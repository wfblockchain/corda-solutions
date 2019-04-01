package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.flows.bno.OnMembershipChanged
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.SimpleMembershipMetadata
import com.r3.businessnetworks.membership.testextensions.AmendMembershipMetadataFlowResponderWithCustomVerification
import net.corda.core.flows.FlowException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AmendMembershipMetadataFlowTest : AbstractFlowTest(
        numberOfParticipants = 2,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    @Test
    fun `amend metadata happy path`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        val participantNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)

        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)

        // cleaning up the received notifications as we are interested in the notifications related to metadata amendment only
        NotificationsCounterFlow.NOTIFICATIONS.clear()

        val existingMembership = getMembership(participantNode, participantNode.identity(), bn)
        val newMetadata = (existingMembership.state.data.membershipMetadata as SimpleMembershipMetadata).copy(role = "Some other role")

        val partiallySignedTx = runAmendMetadataFlow(id, bnoNode, participantNode, newMetadata)
        val allSignedTx = allTransactions(participantNode).single { it.id == partiallySignedTx.id }
        allSignedTx.verifyRequiredSignatures()

        val outputWithContract = allSignedTx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = allSignedTx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.Amend)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.membershipMetadata == newMetadata)
        assert(allSignedTx.inputs.single() == existingMembership.ref)

        // all members should have received the same notification
        val amendedMembership = getMembership(bnoNode, participantNode.identity(), bn)
        val expectedNotifications = participantsNodes.map { NotificationHolder(it.identity(), bnoNode.identity(), OnMembershipChanged(amendedMembership)) }.toSet()
        assertEquals(expectedNotifications, NotificationsCounterFlow.NOTIFICATIONS)
    }

    @Test
    fun `non members should not be able to amend their metadata`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        val memberNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)
        runRequestMembershipFlow(id, bnoNode, memberNode)
        runActivateMembershipFlow(id, memberNode.identity())

        try {
            runAmendMetadataFlow(id, bnoNode, memberNode, SimpleMembershipMetadata(role = "Some role"))
        } catch (e : FlowException) {
            assert("${memberNode.identity()} is not a member" == e.message)
        }
    }

    @Test
    fun `should be able to perform custom metadata verification`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        bnoNode.registerInitiatedFlow(AmendMembershipMetadataFlowResponderWithCustomVerification::class.java)

        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)

        val participantNode = participantsNodes.first()
        val existingMembership = getMembership(participantNode, participantNode.identity(), bn)
        val newMetadata = (existingMembership.state.data.membershipMetadata as SimpleMembershipMetadata).copy(role = "Some other role")

        try {
            runAmendMetadataFlow(id, bnoNode, participantNode, newMetadata)
            fail()
        } catch (ex : FlowException) {
            assertEquals("Invalid metadata", ex.message)
        }
    }
}