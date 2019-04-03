package com.r3.businessnetworks.membership.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
import com.r3.businessnetworks.membership.flows.bno.OnMembershipChanged
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.testextensions.AutoApprovingMembershipFlow
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatedFlow
import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Run tests individually to avoid conflicts in NOTIFICATIONS.
 * Otherwise, NotificationsCounterFlow.NOTIFICATIONS.single() may fail.
 */
class ActivateMembershipFlowTest : AbstractFlowTest(
        numberOfParticipants = 4,
        participantRespondingFlows = listOf(NotificationsCounterFlow::class.java)) {

    private fun testMembershipActivation(activateCallback : (id: UUID, /*bnoNode : StartedMockNode,*/ participantNode : StartedMockNode) -> SignedTransaction) {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        val participantNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)

        runRequestMembershipFlow(id, bnoNode, participantNode)

        // membership state before activation
        val inputMembership = getMembership(participantNode, participantNode.identity(), bn)

        val stx = activateCallback(id, /*bnoNode,*/ participantNode)
        stx.verifyRequiredSignatures()

        val outputTxState = stx.tx.outputs.single()
        val command = stx.tx.commands.single()

        assert(MembershipContract.CONTRACT_NAME == outputTxState.contract)
        assert(command.value is MembershipContract.Commands.Activate)
        assert(stx.tx.inputs.single() == inputMembership.ref)

        // making sure that a correct notification has been send
        val membershipStateAndRef = getMembership(bnoNode, participantNode.identity(), bn)

        val notification = NotificationsCounterFlow.NOTIFICATIONS.single()
        assertEquals(NotificationHolder(participantNode.identity(), bnoNode.identity(), OnMembershipChanged(membershipStateAndRef)), notification)
    }

    @Test
    fun `membership activation happy path`() = testMembershipActivation { id, participantNode ->
        runActivateMembershipFlow(id, participantNode.identity())
    }

    @Test
    fun `membership activation should succeed when using a convenience flow`() = testMembershipActivation { id, participantNode ->
        runActivateMembershipForPartyFlow(id, participantNode.identity())
    }

    @Test(expected = BNBNOMismatchException::class)
    fun `only BNO should be able to start the flow`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        val participantNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)

        runRequestMembershipFlow(id, bnoNode, participantNode)
        val membership = getMembership(participantNode, participantNode.identity(), bn)
        val future = participantNode.startFlow(ActivateMembershipFlow(id, membership))
        mockNetwork.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `membership can be auto activated`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second
        bnoNode.registerInitiatedFlow(AutoApprovingMembershipFlow::class.java)
        val bnoConfiguration = bnoNode.services.cordaService(MembershipConfigurationService::class.java)
        bnoConfiguration.reloadConfigurationFromFile(fileFromClasspath("membership-service.conf"))
        val participantNode = participantsNodes.first()

        runRegisterBNOFlow(id, bnoNode)

        runRequestMembershipFlow(id, bnoNode, participantNode)

        // membership state before activation
        val inputMembership = getMembership(participantNode, participantNode.identity(), bn)
        assertTrue(inputMembership.state.data.isActive())

        val updatedMembership = getMembership(bnoNode, participantNode.identity(), bn)
        assertEquals(NotificationHolder(participantNode.identity(), bnoNode.identity(), OnMembershipChanged(updatedMembership)), NotificationsCounterFlow.NOTIFICATIONS.single())
    }
}
