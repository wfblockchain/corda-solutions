package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.DebugOnlyException
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatingFlow
import com.r3.businessnetworks.membership.states.BusinessNetwork
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

@CordaSerializable
data class OnBoardingRequest(val bn: BusinessNetwork, val metadata : Any)

/**
 * The flow requests BNO to kick-off the on-boarding procedure.
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class RequestMembershipFlow(id: UUID? = null, bno: Party, private val membershipMetadata: Any) : BusinessNetworkAwareInitiatingFlow<SignedTransaction>(id = id, bno = bno) {
    companion object {
        object SENDING_MEMBERSHIP_DATA_TO_BNO : ProgressTracker.Step("Sending membership data to BNO")
        object ACCEPTING_INCOMING_PENDING_MEMBERSHIP : ProgressTracker.Step("Accepting incoming pending membership")

        fun tracker() = ProgressTracker(
                SENDING_MEMBERSHIP_DATA_TO_BNO,
                ACCEPTING_INCOMING_PENDING_MEMBERSHIP
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun afterBNOIdentityVerified(bn: BusinessNetwork) : SignedTransaction {
        progressTracker.currentStep = SENDING_MEMBERSHIP_DATA_TO_BNO

        val bnoSession = initiateFlow(bno)
        bnoSession.send(OnBoardingRequest(bn, membershipMetadata))

        val signResponder = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val output = stx.tx.outputs.single()

                val membershipState = output.data as MembershipState<*>
                if (bno != membershipState.bn.bno) {
                    throw IllegalArgumentException("Wrong BNO identity")
                }
                if (ourIdentity != membershipState.member) {
                    throw IllegalArgumentException("We have to be the member")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        progressTracker.currentStep = ACCEPTING_INCOMING_PENDING_MEMBERSHIP

        val selfSignedTx = subFlow(signResponder)

        return if (bnoSession.getCounterpartyFlowInfo().flowVersion == 1) {
            selfSignedTx
        } else {
            subFlow(ReceiveFinalityFlow(bnoSession, selfSignedTx.id))
        }
    }
}