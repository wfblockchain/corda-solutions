package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.NonEmptyBNException
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@StartableByRPC
@InitiatingFlow(version = 2)
open class RegisterBNOFlow(private val id: UUID?, private val membershipMetadata : Any) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {
    @Suspendable
    override fun call(): SignedTransaction {
        if (!isEmptyBusinessNetwork()) throw NonEmptyBNException(id) //FlowException("RegisterBNOFlow must start with an empty business network.")

        val notary = confSvc().notaryParty()
        val state = MembershipState(member = ourIdentity, bn = bn(), membershipMetadata = membershipMetadata, status = MembershipStatus.ACTIVE)
        val builder = TransactionBuilder(notary)
                .addOutputState(state, MembershipContract.CONTRACT_NAME)
                .addCommand(MembershipContract.Commands.RegisterBNO(), ourIdentity.owningKey)
        verifyTransaction(builder)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(selfSignedTx, listOf()))
        return selfSignedTx
    }

    /**
     * Override this method to add custom verification membership metadata verifications.
     * See: https://docs.corda.net/head/flow-overriding.html
     */
    @Suspendable
    protected open fun verifyTransaction(builder : TransactionBuilder) {
        builder.verify(serviceHub)
    }

}