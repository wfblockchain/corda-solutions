package com.r3.businessnetworks.membership.flows.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.bno.support.BusinessNetworkOperatorInitiatedFlow
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.flows.member.MembershipListRequest
import com.r3.businessnetworks.membership.flows.member.MembershipsListResponse
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

/**
 * Responder to the [GetMembershipsFlow]. Only active members can request a membership list.
 */
@InitiatedBy(GetMembershipsFlow::class)
open class GetMembershipsFlowResponder(flowSession : FlowSession) : BusinessNetworkOperatorInitiatedFlow<Unit>(flowSession) {

    @Suspendable
    override fun call(): Unit {
        val request = flowSession.receive<MembershipListRequest>().unwrap { it }
        /**
         * We must set _id so that superclass function bn() will work correctly.
         */
        this._id = request.id
        val membership = verifyAndGetMembership(flowSession.counterparty)
        return onCounterpartyMembershipVerified(membership)
    }
    @Suspendable
    fun onCounterpartyMembershipVerified(counterpartyMembership : StateAndRef<MembershipState<Any>>) {
        logger.info("Sending membership list to ${flowSession.counterparty}")
        //build memberships snapshot
        val databaseService = databaseService()
        val membershipsWhereWeAreBNO = databaseService.getAllMemberships(bn())
        flowSession.send(MembershipsListResponse(membershipsWhereWeAreBNO))
    }
}