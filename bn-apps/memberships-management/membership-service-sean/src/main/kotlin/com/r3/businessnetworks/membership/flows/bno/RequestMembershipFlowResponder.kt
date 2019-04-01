package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.NoBNORegisteredException
import com.r3.businessnetworks.membership.flows.member.OnBoardingRequest
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorFlowLogic
import com.r3.businessnetworks.membership.states.MembershipContract
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.sql.SQLException
import javax.persistence.PersistenceException

/**
 * The flow issues a PENDING membership state onto the ledger. After the state is issued, the BNO is supposed to perform
 * required governance / KYC checks / paperwork and etc. After all of the required activities are completed, the BNO can activate membership
 * via [ActivateMembershipFlow].
 *
 * The flow supports automatic membership activation via [MembershipAutoAcceptor].
 *
 * TODO: remove MembershipAutoAcceptor in favour of flow overrides when Corda 4 is released
 */
@InitiatedBy(RequestMembershipFlow::class)
open class RequestMembershipFlowResponder(val session : FlowSession) : BusinessNetworkOperatorFlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        /**
         * Get the bn and metadata sent from RequestMembershipFlow
         * Note: we don't use bn() from BusinessNetworkOperatorFlowLogic because we don't have the BN's UUID.
         */
        val request = session.receive<OnBoardingRequest>().unwrap { it }
        /**
         * Note: Superclass BusinessNetworkOperatorFlowLogic needs the BN.bnId.id to be defined.
         * We only get it after receiving OnBoardingRequest which has bn in it.
         */
        this._id = request.bn.bnId.id
        val bn = bn()
        val metadata = request.metadata
        /**
         * Get the services
         */
        val databaseService = databaseService()
        val configuration = confSvc()
        /**
         * Get the bno's member state
         */
        val bnoMembership = databaseService.getMembership(ourIdentity, bn) ?: throw NoBNORegisteredException(bn)

        // checking that there is no existing membership state
        val counterparty = session.counterparty
        val counterpartMembership = databaseService.getMembership(counterparty, bn)
        if (counterpartMembership != null) {
            throw FlowException("Membership already exists")
        }

        // creating a pending request to make sure that no multiple on-boarding request can be in-flight in the same time
        try {
            databaseService.createPendingMembershipRequest(session.counterparty)
        } catch (e : PersistenceException) {
            logger.warn("Error when trying to create a pending membership request", e)
            throw FlowException("Membership request already exists")
        }

        val membership : MembershipState<Any>
        // Issuing PENDING membership state onto the ledger
        try {
            // receive an on-boarding request
            //val request = session.receive<OnBoardingRequest>().unwrap { it }

            val notary = configuration.notaryParty()

            // issue pending membership state on the ledger
            membership = MembershipState(counterparty, bn, metadata)
            val builder = TransactionBuilder(notary)
                    .addOutputState(membership, MembershipContract.CONTRACT_NAME)
                    .addCommand(MembershipContract.Commands.Request(), counterparty.owningKey, ourIdentity.owningKey)
                    .addReferenceState(ReferencedStateAndRef(bnoMembership))

            verifyTransaction(builder)

            val selfSignedTx = serviceHub.signInitialTransaction(builder)
            val allSignedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(session)))

            if (session.getCounterpartyFlowInfo().flowVersion == 1) {
                subFlow(FinalityFlow(allSignedTx))
            } else {
                subFlow(FinalityFlow(allSignedTx, listOf(session)))
            }
        } finally {
            try {
                logger.info("Removing the pending request from the database")
                databaseService.deletePendingMembershipRequest(session.counterparty)
            } catch (e : PersistenceException) {
                logger.warn("Error when trying to delete pending membership request", e)
            }
        }

        /**
         * TODO: Temporary comment out because we do ActivateMembershipFlow later
         */
        if (activateRightAway(membership, configuration)) {
            logger.info("Auto-activating membership for party ${membership.member}")
            val stateToActivate = findMembershipStateForParty(membership.member)
            subFlow(ActivateMembershipFlow(bn.bnId.id, stateToActivate))
        }
    }

    /**
     * Override this method to automatically accept memberships
     * See: https://docs.corda.net/head/flow-overriding.html
     */
    @Suspendable
    protected open fun activateRightAway(membershipState : MembershipState<Any>, configuration : MembershipConfigurationService) : Boolean {
        return false
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