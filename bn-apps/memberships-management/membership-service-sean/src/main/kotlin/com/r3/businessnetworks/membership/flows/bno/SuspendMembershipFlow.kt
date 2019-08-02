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
 * A flow that is used by BNO to suspend a membership. BNO can unilaterally suspend memberships, for example as a result of the governance
 * action.
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class SuspendMembershipFlow(private val id: UUID?, val membership : StateAndRef<MembershipState<Any>>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {

    @Suspendable
    override fun call() : SignedTransaction {
        val bn = verifyThatWeAreBNO(membership.state.data)
        val configuration = confSvc()
        val databaseService = databaseService()
        /**
         * Get the bno's member state
         */
        val bnoMembership = databaseService.getMembership(ourIdentity, bn) ?: throw NoBNORegisteredException(bn)

        // build suspension transaction
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.SUSPENDED, modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.Suspend(), ourIdentity.owningKey)
                .addReferenceState(ReferencedStateAndRef(bnoMembership))
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)

        val allActiveMembers = getActiveMembershipsExceptForBNO().map { it.state.data.member }.toSet()  + membership.state.data.member
        val memberSession = initiateFlow(membership.state.data.member)
        val memberSessions = allActiveMembers.map {
            initiateFlow(it)
        }

        val finalisedTx = if (memberSession.getCounterpartyFlowInfo().flowVersion == 1) {
            subFlow(FinalityFlow(selfSignedTx))
        } else {
            subFlow(FinalityFlow(selfSignedTx, memberSessions))
        }

        val dbService = serviceHub.cordaService(DatabaseService::class.java)
        val suspendedMembership = dbService.getMembership(membership.state.data.member, bn)!!

        // notify other members about suspension
        subFlow(NotifyActiveMembersFlow(id, OnMembershipChanged(suspendedMembership)))
        // sending notification to the suspended member separately
        val suspendedMember = suspendedMembership.state.data.member
        subFlow(NotifyMemberFlow(OnMembershipChanged(suspendedMembership), suspendedMember))

        return finalisedTx
    }
}

/**
 * This is a convenience flow that can be easily used from a command line
 *
 * @param party whose membership state to be suspended
 */
@InitiatingFlow
@StartableByRPC
open class SuspendMembershipForPartyFlow(private val id: UUID?, val party : Party) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {

    companion object {
        object LOOKING_FOR_MEMBERSHIP_STATE : ProgressTracker.Step("Looking for party's membership state")
        object SUSPENDING_THE_MEMBERSHIP_STATE : ProgressTracker.Step("Suspending the membership state")

        fun tracker() = ProgressTracker(
                LOOKING_FOR_MEMBERSHIP_STATE,
                SUSPENDING_THE_MEMBERSHIP_STATE
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party)

        progressTracker.currentStep = SUSPENDING_THE_MEMBERSHIP_STATE
        return subFlow(SuspendMembershipFlow(id, stateToActivate))
    }

}