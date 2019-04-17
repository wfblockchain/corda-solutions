package com.r3.businessnetworks.membership.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.GetMembershipsFlowResponder
import com.r3.businessnetworks.membership.flows.member.service.MembershipsCacheHolder
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import com.r3.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GetMembershipsFlowTest : AbstractFlowTest(
        numberOfParticipants = 6,
        participantRespondingFlows = listOf(GetMembershipsFlowResponder::class.java)) {
    @Test
    fun `GetMembershipsFlow should return all active memberships to all business network members`() {
        runRegisterBNOFlow(id, bnoNode)

        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)
        val allParties = participantsNodes.identities()

        // verifying memberships list for each party
        participantsNodes.forEach { participantNode ->
            val memberships = runGetMembershipsListFlow(id, bnoNode, participantNode, true)
            assertEquals(allParties.toSet(), memberships.map { it.value.state.data.member }.filter { it.name != bnoNode.identity().name }.toSet())
            val party = participantNode.identity()
            assertEquals(getMembership(participantNode, party, bn), memberships[party])
        }
    }

    @Test
    fun `GetMembershipsFlow should not return suspended memberships`() {
        val suspendedNode = participantsNodes[0]
        val okNode = participantsNodes[2]

        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)
        runSuspendMembershipFlow(id, suspendedNode.identity())

        assertNull(runGetMembershipsListFlow(id, bnoNode, okNode)[suspendedNode.identity()])
    }

    @Test
    fun `GetMembershipsFlow should not return pending memberships`() {
        val pendingNode = participantsNodes[1]
        val okNode = participantsNodes[2]

        runRegisterBNOFlow(id, bnoNode)
        runRequestMembershipFlow(id, bnoNode, participantsNodes)
        runActivateMembershipFlow(id, (participantsNodes - pendingNode).identities())

        assertNull(runGetMembershipsListFlow(id, bnoNode, okNode, true)[pendingNode.identity()])
    }

    @Test
    fun `only active members should be able to use this flow`() {
        val suspendedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val notMember = participantsNodes[3]

        runRegisterBNOFlow(id, bnoNode)
        runRequestMembershipFlow(id, bnoNode, listOf(suspendedNode, pendingNode))
        runSuspendMembershipFlow(id, suspendedNode.identity())

        try {
            runGetMembershipsListFlow(id, bnoNode, notMember, true)
            fail()
        } catch (e : NotAMemberException) {
            assertEquals("Counterparty ${notMember.identity()} is not a member of this business network", e.message)
        }
        try {
            runGetMembershipsListFlow(id, bnoNode, pendingNode, true)
            fail()
        } catch (e : MembershipNotActiveException) {
            assertEquals("Counterparty's ${pendingNode.identity()} membership in this business network is not active", e.message)
        }
        try {
            runGetMembershipsListFlow(id, bnoNode, suspendedNode, true)
            fail()
        } catch (e : MembershipNotActiveException) {
            assertEquals("Counterparty's ${suspendedNode.identity()} membership in this business network is not active", e.message)
        }
    }

    @Test
    fun `nodes that are not in the Network Map should be filtered out from the list`() {
        runRegisterBNOFlow(id, bnoNode)
        // requesting memberships
        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)

        val participant = participantsNodes.first()
        runGetMembershipsListFlow(id, bnoNode, participant, true)

        // adding not existing party to the cache
        val notExistingParty = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
        val future = participant.startFlow(AddNotExistingPartyToMembershipsCache(bnoNode.identity(), MembershipState(notExistingParty, bn, SimpleMembershipMetadata("DEFAULT"), status = MembershipStatus.ACTIVE)))
        mockNetwork.runNetwork()
        future.getOrThrow()

        // not existing parties shouldn't appear on the result list
        val membersWithoutNotExisting = runGetMembershipsListFlow(id, bnoNode, participant, false, true)
        assertFalse(membersWithoutNotExisting.map { it.value.state.data.member }.contains(notExistingParty))

        // not existing parties should appear on the result list is filterOutNotExisting flag has been explicitly set to false
        val membersWithNotExisting = runGetMembershipsListFlow(id, bnoNode, participant, false, false)
        assertTrue(membersWithNotExisting.map { it.value.state.data.member }.contains(notExistingParty))
    }

    @Test
    fun `node should maintain separate lists of memberships per business network`() {
        val bn1 = bnAndNodePairs.toList().first().first
        val id1 = bn1.bnId.id
        val bno1Node = bnAndNodePairs.toList().first().second

        val bn2 = bnAndNodePairs.toList().last().first
        val id2 = bn2.bnId.id
        val bno2Node = bnAndNodePairs.toList().last().second

        val multiBnParticipant = participantsNodes[0]
        val bn1Participants = participantsNodes.subList(1, 3)
        val bn2Participants = participantsNodes.subList(3, 6)

        runRegisterBNOFlow(id1, bno1Node)
        runRegisterBNOFlow(id2, bno2Node)
        // activating multiBnParticipant's node in both of the business networks
        runRequestAndActivateMembershipFlow(id1, bno1Node, multiBnParticipant)
        runRequestAndActivateMembershipFlow(id2, bno2Node, multiBnParticipant)

        // activating bn1 and bn2 members
        runRequestAndActivateMembershipFlow(id1, bno1Node, bn1Participants)
        runRequestAndActivateMembershipFlow(id2, bno2Node, bn2Participants)

        // membership lists received from BNOs
        val bn1MembershipsList = runGetMembershipsListFlow(id1, bno1Node, multiBnParticipant)
        val bn2MembershipsList = runGetMembershipsListFlow(id2, bno2Node, multiBnParticipant)

        // membership states from BNOs vaults
        val bn1MembershipStates = getAllMemberships(bno1Node, bn1)
        val bn2MembershipStates = getAllMemberships(bno2Node, bn2)

        assertEquals(bn1MembershipStates.map { it.state.data.member }.filter { it.name != bno1Node.identity().name }.toSet(), (bn1Participants + multiBnParticipant).map { it.identity() }.toSet())
        assertEquals(bn2MembershipStates.map { it.state.data.member }.filter { it.name != bno2Node.identity().name }.toSet(), (bn2Participants + multiBnParticipant).map { it.identity() }.toSet())
        assertEquals(bn1MembershipStates.toSet(), bn1MembershipsList.values.toSet())
        assertEquals(bn2MembershipStates.toSet(), bn2MembershipsList.values.toSet())
    }

    @Test
    fun `nodes should be able to associate different metadata with different business networks`() {
        val bn1 = bnAndNodePairs.toList().first().first
        val id1 = bn1.bnId.id
        val bno1Node = bnAndNodePairs.toList().first().second

        val bn2 = bnAndNodePairs.toList().last().first
        val id2 = bn2.bnId.id
        val bno2Node = bnAndNodePairs.toList().last().second

        val multiBnParticipant = participantsNodes[0]

        val bn1Metadata = SomeCustomMembershipMetadata("Hello")
        val bn2Metadata = SimpleMembershipMetadata(role = "BANK", displayedName = "RBS")

        runRegisterBNOFlow(id1, bno1Node)
        runRegisterBNOFlow(id2, bno2Node)
        // activating multiBnParticipant's node in both of the business networks
        runRequestAndActivateMembershipFlow(id1, bno1Node, multiBnParticipant, bn1Metadata)
        runRequestAndActivateMembershipFlow(id2, bno2Node, multiBnParticipant, bn2Metadata)

        val bn1MembershipsList = runGetMembershipsListFlow(id1, bno1Node, multiBnParticipant)
        val bn2MembershipsList = runGetMembershipsListFlow(id2, bno2Node, multiBnParticipant)

        val bn1MembershipsListWithoutBNO = bn1MembershipsList.filter { !it.value.state.data.isBNO }
        val bn2MembershipsListWithoutBNO = bn2MembershipsList.filter { !it.value.state.data.isBNO }

        // verifying that different business networks can have different metadata associated with the membership states
        assertEquals(bn1Metadata, bn1MembershipsListWithoutBNO.values.single().state.data.membershipMetadata)
        assertEquals(bn2Metadata, bn2MembershipsListWithoutBNO.values.single().state.data.membershipMetadata)
    }

    @Test(expected = BNNotWhitelisted::class)
    fun `the flow can be run only against whitelisted BNs`() {
        participantNode.services.cordaService(MembershipConfigurationService::class.java).reloadConfigurationFromFile(fileFromClasspath("membership-service-without-bno-whitelist.conf"))

        runRegisterBNOFlow(id, bnoNode)
        runGetMembershipsListFlow(id, bnoNode, participantNode)
    }


    @Test
    fun `when membership gets activated after suspension the membership cache should be repopulated with the list of current members`() {
        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)

        val suspendedMember = participantsNodes.first()
        // populating the memberships cache
        runGetMembershipsListFlow(id, bnoNode, suspendedMember)

        // suspending membership
        runSuspendMembershipFlow(id, suspendedMember.identity())

        // the cache now should be empty
        val cache = suspendedMember.services.cordaService(MembershipsCacheHolder::class.java).cache
        assertTrue(cache.getMemberships(bn).isEmpty())
        assertNull(cache.getLastRefreshedTime(bn))

        // activating membership
        runActivateMembershipFlow(id, suspendedMember.identity())

        // making sure that the cache gets repopulated again
        val memberships = runGetMembershipsListFlow(id, bnoNode, suspendedMember, true)
        assertEquals(participantsNodes.identities().toSet(), memberships.map { it.value.state.data.member }.filter{ it.name != bnoNode.identity().name }.toSet())
    }

}

class AddNotExistingPartyToMembershipsCache(val bno : Party, val membership : MembershipState<SimpleMembershipMetadata>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val cacheHolder = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val stateAndRef = StateAndRef(TransactionState(membership, MembershipContract.CONTRACT_NAME, serviceHub.networkMapCache.notaryIdentities.single()), StateRef(SecureHash.zeroHash, 0))
        cacheHolder.cache.updateMembership(stateAndRef)
    }
}

@CordaSerializable
data class SomeCustomMembershipMetadata(val someCustomField : String)