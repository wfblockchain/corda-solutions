package com.r3.businessnetworks.membership.flows.member

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.node.StatesToRecord

@InitiatedBy(ActivateMembershipFlow::class)
open class ActivateMembershipFlowResponder(private val session : FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        /**
         * By using ALL_VISIBLE, we make this party an observer.
         */
        subFlow(ReceiveFinalityFlow(otherSideSession = session, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}