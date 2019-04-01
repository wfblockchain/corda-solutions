package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.flows.bno.OnMembershipChanged
import com.r3.businessnetworks.membership.flows.bno.SuspendMembershipFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.fail

class SuspendMembershipFlowTest : AbstractFlowTest(
        numberOfParticipants = 3,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    private fun testMembershipSuspension(suspender : (id: UUID, /*bnoNode : StartedMockNode,*/ participantNode : StartedMockNode) -> SignedTransaction) {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        val suspendedMemberNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)

        runRequestAndActivateMembershipFlow(id, bnoNode, participantsNodes)

        // cleaning up notifications as we are interested in SUSPENDs only
        NotificationsCounterFlow.NOTIFICATIONS.clear()

        val inputMembership = getMembership(suspendedMemberNode, suspendedMemberNode.identity(), bn)
        val stx = suspender(id, suspendedMemberNode)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Suspend)
        assert(stx.inputs.single() == inputMembership.ref)

        // both the active and the suspended member should have received the same notification
        val suspendedMembership = getMembership(bnoNode, suspendedMemberNode.identity(), bn)
        val expectedNotifications = participantsNodes.map { NotificationHolder(it.identity(), bnoNode.identity(), OnMembershipChanged(suspendedMembership)) }.toSet()
        assertEquals(expectedNotifications, NotificationsCounterFlow.NOTIFICATIONS)
    }

    @Test
    fun `membership suspension happy path`() = testMembershipSuspension { id, participantNode -> runSuspendMembershipFlow(id, participantNode.identity()) }

    @Test
    fun `membership suspension should succeed when using a convenience flow`() = testMembershipSuspension { id, participantNode -> runSuspendMembershipForPartyFlow(id, participantNode.identity()) }

    @Test(expected = BNBNOMismatchException::class)
    fun `only BNO should be able to start the flow`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        val memberNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)

        runRequestMembershipFlow(id, bnoNode, memberNode)
        val membership = getMembership(memberNode, memberNode.identity(), bn)
        val future = memberNode.startFlow(SuspendMembershipFlow(id, membership))
        mockNetwork.runNetwork()
        future.getOrThrow()
    }
}