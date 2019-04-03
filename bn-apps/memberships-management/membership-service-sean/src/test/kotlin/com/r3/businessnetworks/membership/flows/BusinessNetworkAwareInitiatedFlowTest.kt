package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import org.junit.Test

class BusinessNetworkAwareInitiatedFlowTest : AbstractFlowTest(numberOfParticipants = 2) {

    @Test(expected = NotAMemberException::class)
    fun `pending members should not be able to transact on the business network`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second

        val participant1 = participantsNodes[0]
        val participant2 = participantsNodes[1]

        participant2.registerInitiatedFlow(BN_1_RespondingFlow::class.java)

        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participant2)
        runRequestMembershipFlow(id, bnoNode, participant1)

        val future = participant1.startFlow(BN_1_InitiatingFlow(participant2.identity()))

        mockNetwork.runNetwork()

        future.getOrThrow()
    }

    @Test(expected = NotAMemberException::class)
    fun `suspended members should not be able to transact on the business network`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second

        val participant1 = participantsNodes[0]
        val participant2 = participantsNodes[1]

        participant2.registerInitiatedFlow(BN_1_RespondingFlow::class.java)

        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participant2)
        runRequestAndActivateMembershipFlow(id, bnoNode, participant1)
        runSuspendMembershipFlow(id, participant1.identity())

        val future = participant1.startFlow(BN_1_InitiatingFlow(participant2.identity()))

        mockNetwork.runNetwork()

        future.getOrThrow()
    }

    @Test
    fun `active members should be able to transact on the business network`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second

        val participant1 = participantsNodes[0]
        val participant2 = participantsNodes[1]

        participant2.registerInitiatedFlow(BN_1_RespondingFlow::class.java)

        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participant2)
        runRequestAndActivateMembershipFlow(id, bnoNode, participant1)

        val future = participant1.startFlow(BN_1_InitiatingFlow(participant2.identity()))

        mockNetwork.runNetwork()

        future.getOrThrow()
    }

    @Test(expected = NotAMemberException::class)
    fun `non-members should be able to transact on the business network`() {
        val bn = bnAndNodePairs.toList().first().first
        val id = bn.bnId.id
        val bnoNode = bnAndNodePairs.toList().first().second

        val participant1 = participantsNodes[0]
        val participant2 = participantsNodes[1]

        participant2.registerInitiatedFlow(BN_1_RespondingFlow::class.java)

        runRegisterBNOFlow(id, bnoNode)
        runRequestAndActivateMembershipFlow(id, bnoNode, participant2)

        val future = participant1.startFlow(BN_1_InitiatingFlow(participant2.identity()))

        mockNetwork.runNetwork()

        future.getOrThrow()
    }
}

@InitiatingFlow
class BN_1_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_2_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_3_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_4_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_5_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatedBy(BN_1_InitiatingFlow::class)
class BN_1_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"O=BNO_0,L=New York,C=US")

@InitiatedBy(BN_2_InitiatingFlow::class)
class BN_2_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"O=BNO_1,L=New York,C=US")

@InitiatedBy(BN_3_InitiatingFlow::class)
class BN_3_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"O=BNO_2,L=New York,C=US")

@InitiatedBy(BN_4_InitiatingFlow::class)
class BN_4_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"O=BNO_3,L=New York,C=US")

@InitiatedBy(BN_5_InitiatingFlow::class)
class BN_5_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"O=BNO_4,L=New York,C=US")