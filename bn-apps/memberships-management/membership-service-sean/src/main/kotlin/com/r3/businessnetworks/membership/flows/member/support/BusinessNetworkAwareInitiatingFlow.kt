package com.r3.businessnetworks.membership.flows.member.support

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.service.MembershipConfigurationService
import com.r3.businessnetworks.membership.states.BusinessNetwork
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import java.util.*

/**
 * This flow is supposed to be extended by Business Network members for any member->BNO communications.
 * The flow verifies that the BNO is whitelisted in member's cordapp configuration.
 */
abstract class BusinessNetworkAwareInitiatingFlow<out T>(val id: UUID? = null, val bno : Party) : FlowLogic<T>() {
    @Suspendable
    override fun call() : T {
        val bn = bn()
        return afterBNOIdentityVerified(bn)
    }

    @Suspendable
    abstract fun afterBNOIdentityVerified(bn: BusinessNetwork) : T

    protected fun confSvc() = serviceHub.cordaService(MembershipConfigurationService::class.java)
    protected fun databaseService() = serviceHub.cordaService(DatabaseService::class.java)

    protected fun bn() = confSvc().bn(id, bno)

}