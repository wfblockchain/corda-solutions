package com.r3.businessnetworks.membership.bno.support

import com.r3.businessnetworks.membership.MembershipNotFound
import com.r3.businessnetworks.membership.NotBNOException
import com.r3.businessnetworks.membership.bno.service.BNOConfigurationService
import com.r3.businessnetworks.membership.bno.service.DatabaseService
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

/**
 * Extend from this class if you are a business network operator and you want to make your life easier when writing
 * flows by getting access to the useful methods in this class.
 */
abstract class BusinessNetworkOperatorFlowLogic<out T> : FlowLogic<T>() {
    protected fun verifyThatWeAreBNO(membership : MembershipState<Any>) {
        if(ourIdentity != membership.bno) {
            throw NotBNOException(membership)
        }
    }

    protected fun findMembershipStateForParty(party : Party) : StateAndRef<MembershipState<Any>> {
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        return databaseService.getMembership(party, ourIdentity, configuration.membershipContractName()) ?: throw MembershipNotFound(party)
    }

    protected fun getActiveMembershipStates() : List<StateAndRef<MembershipState<Any>>> {
        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        return databaseService.getActiveMemberships(ourIdentity, configuration.membershipContractName())
    }
}