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
 */
abstract class BusinessNetworkOperatorFlowLogic<out T>(private val id: UUID? = null) : FlowLogic<T>() {
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
    protected fun bn(): BusinessNetwork = confSvc().bn(id, ourIdentity)
//            (id?.let {
//        val bn = confSvc().bnFromId(id)
//        bn?.let { if (it.bno == ourIdentity) it else throw BNBNOMismatchException(it.bnId.id, ourIdentity) } ?: throw BNNotWhitelisted(it)
//    } ?: confSvc().bnFromBNO(ourIdentity)) ?: throw BusinessNetworkNotFound(id, ourIdentity)

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
//    protected fun findMembershipStateForParty(party : Party) : StateAndRef<MembershipState<Any>> {
////        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
//        return databaseService.getMembership(party, bn) ?: throw MembershipNotFound(party)
//    }

    protected fun getActiveMembershipStates() = databaseService().getActiveMemberships(bn())
//    protected fun getActiveMembershipStates() : List<StateAndRef<MembershipState<Any>>> {
////        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
//        return databaseService.getActiveMemberships(bn)
//    }
    protected fun getActiveMembershipsExceptForBNO() = databaseService().getActiveMembershipsExceptForBNO(bn())

    protected fun isEmptyBusinessNetwork() = databaseService().getAllMemberships(bn()).isEmpty()

}