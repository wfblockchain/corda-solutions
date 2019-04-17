package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import org.junit.Test

class RegisterBNOFlowTest : AbstractFlowTest(numberOfParticipants = 2) {
    @Test(expected = BNBNOMismatchException::class)
    fun `Only BNO can call RegisterBNOFlow`() {
        runRegisterBNOFlow(id, participantNode)
    }
    @Test
    fun `Register BNO happy path`() {
        val stx = runRegisterBNOFlow(id, bnoNode)
        assert(stx.tx.inputs.isEmpty())
        assert(stx.notary!!.name == notaryName)

        val outputWithContract = stx.tx.outputs.single()
        val outputMembership = outputWithContract.data as MembershipState<*>
        val command = stx.tx.commands.single()

        assert(command.value is MembershipContract.Commands.RegisterBNO)
        assert(outputWithContract.contract == MembershipContract.CONTRACT_NAME)
        assert(outputMembership.bn.bnId.id == id)
        assert(outputMembership.bn.bno == bnoNode.identity())
        stx.verifyRequiredSignatures()
    }
    @Test(expected = NonEmptyBNException::class)
    fun `Register BNO can only happen with a fresh network`() {
        runRegisterBNOFlow(id, bnoNode)
        /**
         * Running the second time will fail
         */
        runRegisterBNOFlow(id, bnoNode)
    }

//    @Test
//    fun `Only BNO can call RegisterBNOFlow`() {
//        val participantNode = participantsNodes.first()
//        try {
//            val id = bnAndNodePairs.toList().first().first.bnId.id
//            runRegisterBNOFlow(id, participantNode)
//            fail()
//        } catch(e: FlowException) {
//            assert(e.message == "O=Participant 0, L=London, C=GB is not BNO for BusinessNetwork a590dd5a-9610-4cea-8f91-8085a8f5d5a2.")
//        }
//    }


//    @Test
//    fun `Register BNO can only happen with a fresh network`() {
//        val id = bnAndNodePairs.toList().first().first.bnId.id
//        val bnoNode = bnAndNodePairs.toList().first().second
//        runRegisterBNOFlow(id, bnoNode)
//        try {
//            runRegisterBNOFlow(id, bnoNode)
//            fail()
//        } catch (e: FlowException) {
//            assert(e.message == "RegisterBNOFlow must start with an empty business network.")
//        }
//    }
}