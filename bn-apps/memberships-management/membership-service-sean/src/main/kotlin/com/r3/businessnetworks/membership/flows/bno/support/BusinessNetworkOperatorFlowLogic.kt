package com.r3.businessnetworks.membership.flows.bno.support

import com.r3.businessnetworks.membership.flows.*
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.states.BusinessNetwork
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import java.util.*

/**
 * Extend from this class if you are a business network operator and you want to make your life easier when writing
 * flows by getting access to the useful methods in this class.
 * Note: we make _id a protected var so that the subclass will either pass it in via constructor or early in the call()
 * function. See RequestMembershipFlowResponder for an example.
 * But by making it protected (which is not private), subclass cannot use the same variable name id.
 * So we use _id instead of id here so that the subclass can use id that way the user does not have to deal with _.
 */
abstract class BusinessNetworkOperatorFlowLogic<out T>(protected var _id: UUID? = null) : FlowLogic<T>() {
    /**
     * Note: we functions to encapsulate the Corda service retrievals instead of "caching" them in vals
     * because the latter will cause StateMachine initialization exceptions in MockNetwork flow tests.
     * That is fine performance-wise since we don't make these calls ofter during the life of the flow.
     */
    protected fun confSvc() = serviceHub.cordaService(MembershipConfigurationService::class.java)
    protected fun databaseService() = serviceHub.cordaService(DatabaseService::class.java)
    /**
     * Calling bn() is the first thing in function call().
     * How to enforce it?
     * We may model after BusinessNetworkAwareInitiatingFlow.
     * Or do it inside verifyThatWeAreBNO(...)
     */
    protected fun bn(): BusinessNetwork = confSvc().bn(_id, ourIdentity)

    protected fun verifyThatWeAreBNO(membership : MembershipState<Any>) =
        bn().also {
            if(it != membership.bn) {
                throw BNMismatchException(it.bnId.id, membership.bn.bnId.id)
            }
            if(it.bno != membership.bn.bno) {
                throw BNOMismatchException(it.bno, membership.bn.bno)
            }
            if(ourIdentity != membership.bn.bno) {
                throw NotBNOException(membership)
            }
        }

    protected fun findMembershipStateForParty(party : Party) = databaseService().getMembership(party, bn()) ?: throw MembershipNotFound(party)

    protected fun getActiveMembershipStates() = databaseService().getActiveMemberships(bn())

    protected fun getActiveMembershipsExceptForBNO() = databaseService().getActiveMembershipsExceptForBNO(bn())

    protected fun isEmptyBusinessNetwork() = databaseService().getAllMemberships(bn()).isEmpty()

    protected fun verifyAndGetMembership(initiator : Party) : StateAndRef<MembershipState<Any>> {
        logger.info("Verifying membership status of $initiator")
        val membership = databaseService().getMembership(initiator, bn())
        if (membership == null) {
            throw NotAMemberException(initiator)
        } else if (!membership.state.data.isActive()) {
            throw MembershipNotActiveException(initiator)
        }
        return membership
    }


}