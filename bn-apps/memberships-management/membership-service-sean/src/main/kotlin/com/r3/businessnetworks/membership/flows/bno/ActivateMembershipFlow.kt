package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.NoBNORegisteredException
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
 * WFC Note: We make all nodes in networkmap except for BNO and the notary the observers of this transaction so that they can
 * use the active membership states are reference states in their transactions. The notification flows may not serve our purposes
 * because they only update the node's cache instead of storing the state in the vault and we need to research whether or not
 * a StateAndRef<MembershipState<*>> can be used as a reference state even though it is not in our vault.
 * The problem is that we are leaking to nodes which are not part of the BN..
 * Option for future consideration to mitigate the leak:
 * Only make all known members dbSvc.getAllMemberships(...) regardless status as observers.
 * But future members may miss out existing members. Can we refresh our membership cache via GetMembershipsFlow inside
 * our CorDapp flow and use a StateAndRef<MembershipState<*>> as reference state in our CorDapp flow
 * even though it is not in our vault?
 *
 * @param membership membership state to be activated
 */
@StartableByRPC
@InitiatingFlow(version = 2)
open class ActivateMembershipFlow(private val id: UUID?, val membership : StateAndRef<MembershipState<Any>>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {

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

//        val allActiveMembers = getActiveMembershipsExceptForBNO().map { it.state.data.member }.toSet()  + membership.state.data.member
        val allNodesExceptForBNO = serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() }.toSet() - ourIdentity - notary
        val memberSessions = allNodesExceptForBNO.map {
            initiateFlow(it)
        }
      val stx = subFlow(FinalityFlow(selfSignedTx, memberSessions))

        // We should notify members about changes with the ACTIVATED membership
        val activatedMembership = databaseService.getMembership(membership.state.data.member, bn)!!
        subFlow(NotifyActiveMembersFlow(id, OnMembershipChanged(activatedMembership)))

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
open class ActivateMembershipForPartyFlow(private val id: UUID?, val party : Party) : BusinessNetworkOperatorFlowLogic<SignedTransaction>(id) {

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