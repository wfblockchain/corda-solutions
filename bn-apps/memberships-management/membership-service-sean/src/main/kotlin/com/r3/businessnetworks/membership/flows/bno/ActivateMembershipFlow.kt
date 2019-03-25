package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.NoBNORegisteredException
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import com.r3.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * The flow changes status of a PENDING or SUSPENDED membership to ACTIVE. The flow can be started only by BNO. BNO can unilaterally
 * activate memberships and no member's signature is required. After a membership is activated, the flow
 * fires-and-forgets [OnMembershipChanged] notification to all business network members.
 *
 * @param membership membership state to be activated
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class ActivateMembershipFlow(val id: UUID?, val membership : StateAndRef<MembershipState<Any>>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {

    @Suspendable
    override fun call() : SignedTransaction {
        val bn = verifyThatWeAreBNO(membership.state.data)

        val databaseService = databaseService()
        val configuration = confSvc()
        /**
         * Get the bno's member state
         */
        val bnoMembership = databaseService.getMembership(ourIdentity, bn) ?: throw NoBNORegisteredException(bn)

        // create membership activation transaction
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant()), MembershipContract.CONTRACT_NAME)
                .addCommand(MembershipContract.Commands.Activate(), ourIdentity.owningKey)
                .addReferenceState(ReferencedStateAndRef(bnoMembership))
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)

        val memberSession = initiateFlow(membership.state.data.member)
        val stx = if (memberSession.getCounterpartyFlowInfo().flowVersion == 1) {
            subFlow(FinalityFlow(selfSignedTx))
        } else {
            subFlow(FinalityFlow(selfSignedTx, listOf(memberSession)))
        }

        // We should notify members about changes with the ACTIVATED membership
        val activatedMembership = databaseService.getMembership(membership.state.data.member, bn)!!
        subFlow(NotifyActiveMembersFlow(OnMembershipChanged(activatedMembership)))

        return stx
    }
}

/**
 * This is a convenience flow that can be easily used from a command line
 *
 * @param party whose membership state to be activated
 */
@InitiatingFlow
@StartableByRPC
open class ActivateMembershipForPartyFlow(val id: UUID?, val party : Party) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {

    companion object {
        object LOOKING_FOR_MEMBERSHIP_STATE : ProgressTracker.Step("Looking for party's membership state")
        object ACTIVATING_THE_MEMBERSHIP_STATE : ProgressTracker.Step("Activating the membership state")

        fun tracker() = ProgressTracker(
                LOOKING_FOR_MEMBERSHIP_STATE,
                ACTIVATING_THE_MEMBERSHIP_STATE
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party)

        progressTracker.currentStep = ACTIVATING_THE_MEMBERSHIP_STATE
        return subFlow(ActivateMembershipFlow(id, stateToActivate))
    }

}