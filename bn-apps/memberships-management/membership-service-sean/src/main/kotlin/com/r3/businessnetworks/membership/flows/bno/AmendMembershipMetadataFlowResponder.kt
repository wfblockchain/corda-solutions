package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.NoBNORegisteredException
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorInitiatedFlow
import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataFlow
import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataRequest
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.*

/**
 * BNO's responder to the [AmendMembershipMetadataFlow]. Receives an [AmendMembershipMetadataRequest], issues an amend membership transaction
 * and notifies all business network members via [OnMembershipChanged] when the transaction is finalised. Only ACTIVE members can
 * amend their metadata.
 */
@InitiatedBy(AmendMembershipMetadataFlow::class)
open class AmendMembershipMetadataFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun call(): Unit {
        // await for a message with a proposed metadata change
        val metadataChangeRequest = flowSession.receive<AmendMembershipMetadataRequest>().unwrap{ it }
        /**
         * We must set _id so that superclass function bn() will work correctly.
         */
        this._id = metadataChangeRequest.bn.bnId.id
        val membership = verifyAndGetMembership(flowSession.counterparty)
        return onCounterpartyMembershipVerified(membership, metadataChangeRequest.metadata)
    }

    @Suspendable
    fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>, metadata: Any) {

        // build transaction
        val configuration = confSvc()
        val databaseService = databaseService()
        val bn = bn()
        val notaryParty = configuration.notaryParty()
        /**
         * Get the bno's member state
         */
        val bnoMembership = databaseService.getMembership(ourIdentity, bn) ?: throw NoBNORegisteredException(bn)

        val newMembership = counterpartyMembership.state.data
                .copy(membershipMetadata = metadata, modified = serviceHub.clock.instant())

        // changes to the metadata should be governed by the contract, not flows
        val builder = TransactionBuilder(notaryParty)
                .addInputState(counterpartyMembership)
                .addOutputState(newMembership, MembershipContract.CONTRACT_NAME)
                .addCommand(MembershipContract.Commands.Amend(), flowSession.counterparty.owningKey, ourIdentity.owningKey)
                .addReferenceState(ReferencedStateAndRef(bnoMembership))

        verifyTransaction(builder)

        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(flowSession)))

        if (flowSession.getCounterpartyFlowInfo().flowVersion == 1) {
            subFlow(FinalityFlow(allSignedTx))
        } else {
            subFlow(FinalityFlow(allSignedTx, listOf(flowSession)))
        }

        // notify members about the changes
        val amendedMembership = databaseService.getMembership(flowSession.counterparty, bn())!!
        subFlow(NotifyActiveMembersFlow(this._id, OnMembershipChanged(amendedMembership)))
    }

    /**
     * This method can be overridden to add custom verification membership metadata verifications.
     * See: https://docs.corda.net/head/flow-overriding.html
     */
    @Suspendable
    protected open fun verifyTransaction(builder : TransactionBuilder) {
        builder.verify(serviceHub)
    }
}
