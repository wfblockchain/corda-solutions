package com.r3.businessnetworks.membership.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.states.BusinessNetwork
//import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
//import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipForPartyFlow
//import com.r3.businessnetworks.membership.flows.bno.NotifyMemberFlow
//import com.r3.businessnetworks.membership.flows.bno.SuspendMembershipFlow
//import com.r3.businessnetworks.membership.flows.bno.SuspendMembershipForPartyFlow
//import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
//import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataFlow
//import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
//import com.r3.businessnetworks.membership.flows.member.NotifyMembersFlowResponder
//import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.findCordapp
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.*

/**
 * @param numberOfBusinessNetworks number of BNOs to create. BNOs name is represented with O=BNO_{index of bno},L=New York,C=US, starting from 0
 *      i.e. O=BNO_0,L=New York,C=US, O=BNO_1,L=New York,C=US .... O=BNO_N,L=New York,C=US
 * @param numberOfParticipants *total* number of participants to create. Participants don't have business network membership initially
 * @param participantRespondingFlows responding flows to register for participant nodes
 * @param bnoRespondingFlows responding flows to register for BNO nodes
 */
abstract class AbstractFlowTest(private val numberOfBusinessNetworks : Int = 2,
                                private val numberOfParticipants : Int,
                                private val participantRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf(),
                                private val bnoRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf()) {
    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    lateinit var bnoNodes : List<StartedMockNode>
    lateinit var bnAndNodePairs: List<Pair<BusinessNetwork, StartedMockNode>>
    lateinit var participantsNodes : List<StartedMockNode>
    lateinit var mockNetwork : MockNetwork

    val rawBusinessNetworks = setOf(
            Triple("a590dd5a-9610-4cea-8f91-8085a8f5d5a2", "Aurum", "CN=WFBN,OU=Finance,O=WFC,L=San Francisco,C=US"),
            Triple("88579a8c-deba-48e7-be98-164b8135388f", "Janus", "CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")
    )

    @Before
    open fun setup() {
        mockNetwork = MockNetwork(
                // legacy API is used on purpose as otherwise flows defined in tests are not picked up by the framework
                cordappPackages = listOf("com.r3.businessnetworks.membership.flows", "com.r3.businessnetworks.membership.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName))
        )
//        bnoNodes = rawBusinessNetworks.map {
//            val bnoName =  CordaX500Name.parse(it.third)
//            val bnoNode = createNode(bnoName)
//            bnoRespondingFlows.forEach { bnoNode.registerInitiatedFlow(it) }
//            bnoNode
//        }

        bnAndNodePairs = rawBusinessNetworks.map {
            val bnoName =  CordaX500Name.parse(it.third)
            val bnoNode = createNode(bnoName)
            val bnId = UniqueIdentifier(id = UUID.fromString(it.first), externalId = it.second)
            val bn = BusinessNetwork(id = bnId, bno = bnoNode.info.legalIdentities.first())
            bnoRespondingFlows.forEach { bnoNode.registerInitiatedFlow(it) }
            Pair(bn, bnoNode)
        }

        bnoNodes = bnAndNodePairs.map {
            it.second
        }

        participantsNodes = (0..numberOfParticipants).map { indexOfParticipant ->
            val node = createNode(CordaX500Name.parse("O=Participant $indexOfParticipant,L=London,C=GB"))
            participantRespondingFlows.forEach { node.registerInitiatedFlow(it) }
            node
        }

        mockNetwork.runNetwork()
    }

    private fun createNode(name : CordaX500Name) =
            mockNetwork.createNode(MockNodeParameters(legalName = name))

    @After
    open fun tearDown() {
        mockNetwork.stopNodes()
//        NotificationsCounterFlow.NOTIFICATIONS.clear()
    }

    fun runRequestAndActivateMembershipFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) {
//        runRequestMembershipFlow(bnoNode, participantNode, membershipMetadata)
//        runActivateMembershipFlow(bnoNode, participantNode.identity())
    }

//    fun runRequestAndActivateMembershipFlow(bnoNode : StartedMockNode, participantNodes : List<StartedMockNode>, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) {
//        runRequestMembershipFlow(bnoNode, participantNodes, membershipMetadata)
//        runActivateMembershipFlow(bnoNode, participantNodes.map { it.identity() })
//    }

//    fun runRequestMembershipFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) : SignedTransaction {
//        val future = participantNode.startFlow(RequestMembershipFlow(bnoNode.identity(), membershipMetadata))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }

//    fun runRequestMembershipFlow(bnoNode : StartedMockNode, participantNodes : List<StartedMockNode>, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) : List<SignedTransaction> {
//        return participantNodes.map { runRequestMembershipFlow(bnoNode, it, membershipMetadata) }
//    }

//    fun runSuspendMembershipFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
//        val membership = getMembership(bnoNode, participant, bnoNode.identity())
//        val future = bnoNode.startFlow(SuspendMembershipFlow(membership))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }

//    fun runSuspendMembershipForPartyFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
//        val future = bnoNode.startFlow(SuspendMembershipForPartyFlow(participant))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }
//
//    fun runActivateMembershipFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
//        val membership = getMembership(bnoNode, participant, bnoNode.identity())
//        val future = bnoNode.startFlow(ActivateMembershipFlow(membership))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }

//    fun runActivateMembershipFlow(bnoNode : StartedMockNode, participants : List<Party>) : List<SignedTransaction> {
//        return participants.map { runActivateMembershipFlow(bnoNode, it) }
//    }
//
//    fun runActivateMembershipForPartyFlow(bnoNode : StartedMockNode, participant : Party) : SignedTransaction {
//        val future = bnoNode.startFlow(ActivateMembershipForPartyFlow(participant))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }
//
//    fun runAmendMetadataFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, newMetadata : Any) : SignedTransaction {
//        val future = participantNode.startFlow(AmendMembershipMetadataFlow(bnoNode.identity(), newMetadata))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }
//
//    fun runGetMembershipsListFlow(bnoNode : StartedMockNode, participantNode : StartedMockNode, force : Boolean = false, filterOutNotExisting : Boolean = true) : Map<Party, StateAndRef<MembershipState<Any>>> {
//        val future = participantNode.startFlow(GetMembershipsFlow(bnoNode.identity(), force, filterOutNotExisting))
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }
//
//    fun getMembership(nodeToGetFrom : StartedMockNode, member : Party, bno : Party) : StateAndRef<MembershipState<Any>> = nodeToGetFrom.transaction {
//        val dbService = nodeToGetFrom.services.cordaService(DatabaseService::class.java)
//        dbService.getMembership(member, bno)!!
//    }
//
//    fun getAllMemberships(nodeToGetFrom : StartedMockNode, bno : Party) : List<StateAndRef<MembershipState<Any>>> = nodeToGetFrom.transaction {
//        val dbService = nodeToGetFrom.services.cordaService(DatabaseService::class.java)
//        dbService.getAllMemberships(bno)
//    }

    fun allTransactions(node : StartedMockNode) = node.transaction {
        node.services.validatedTransactions.track().snapshot
    }

    fun fileFromClasspath(fileName : String) = File(AbstractFlowTest::class.java.classLoader.getResource(fileName).toURI())
}

fun StartedMockNode.identity() = info.legalIdentities.single()
fun List<StartedMockNode>.identities() = map { it.identity() }

//@InitiatedBy(NotifyMemberFlow::class)
//class NotificationsCounterFlow(session : FlowSession) : NotifyMembersFlowResponder(session) {
//    companion object {
//        val NOTIFICATIONS : MutableSet<NotificationHolder> = mutableSetOf()
//    }
//
//    @Suspendable
//    override fun call() {
//        val notification = super.call()
//        NOTIFICATIONS.add(NotificationHolder(ourIdentity, session.counterparty, notification))
//    }
//}

data class NotificationHolder(val member : Party, val bno : Party, val notification : Any)

