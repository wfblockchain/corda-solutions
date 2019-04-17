package com.r3.businessnetworks.membership.flows

import net.corda.core.utilities.getOrThrow
import org.junit.Test

class BusinessNetworkAwareInitiatedFlowTest : AbstractFlowTest(numberOfParticipants = 2) {

    @Test(expected = NotAMemberException::class)
    fun `pending members should not be able to transact on the business network`() {
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
