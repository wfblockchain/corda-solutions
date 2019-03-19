package com.r3.businessnetworks.membership.flows.member.service

import com.r3.businessnetworks.membership.states.BusinessNetwork
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private typealias MembershipByParty = ConcurrentHashMap<Party, StateAndRef<MembershipState<Any>>>
//@Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("MembershipsByBN"))
//private typealias MembershipsByBNO = ConcurrentHashMap<Party, MembershipByParty>

private typealias MembershipsByBN = ConcurrentHashMap<BusinessNetwork, MembershipByParty>

/**
 * A singleton service that holds a cache of memberships
 */
@CordaService
class MembershipsCacheHolder(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    val cache : MembershipsCache = MembershipsCache()
}

/**
 *
 */
class MembershipsCache {
//    @Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("cacheByBN"))
//    private val cache = MembershipsByBNO()
    private val cacheByBN = MembershipsByBN()
//    @Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("lastRefreshedByBN"))
//    private val lastRefreshed = ConcurrentHashMap<Party, Instant>()
    private val lastRefreshedByBN = ConcurrentHashMap<BusinessNetwork, Instant>()

//    @Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("getMembership(bn : BusinessNetwork, party : Party)"))
//    fun getMembership(bno : Party, party : Party) : StateAndRef<MembershipState<Any>>? = cache[bno]?.get(party)
    fun getMembership(bn : BusinessNetwork, party : Party) : StateAndRef<MembershipState<Any>>? = cacheByBN[bn]?.get(party)

//    @Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("getMemberships(bn: BusinessNetwork)"))
//    fun getMemberships(bno : Party) : MembershipByParty = cache[bno] ?: MembershipByParty()
    fun getMemberships(bn: BusinessNetwork): MembershipByParty = cacheByBN[bn] ?: MembershipByParty()

    fun updateMembership(changedMembership : StateAndRef<MembershipState<Any>>) {
        val membershipsByParty = cacheByBN.getOrPut(changedMembership.state.data.bn) {
            MembershipByParty()
        }

        membershipsByParty.merge(changedMembership.state.data.member, changedMembership) { oldState, newState ->
            if (oldState.state.data.modified > newState.state.data.modified) oldState else newState
        }
    }

    fun applyMembershipsSnapshot(membershipByParty : List<StateAndRef<MembershipState<Any>>>) {
        if (membershipByParty.isNotEmpty()) {
            val bns = membershipByParty.asSequence().map { it.state.data.bn }.toSet()
            if (bns.size != 1) {
                throw IllegalArgumentException("All membership states in the snapshot should refer to the same BN!")
            }
            val bn = bns.single()
            membershipByParty.forEach {
                updateMembership(it)
            }
            // don't forget to update the last refreshed time
            lastRefreshedByBN[bn] = Instant.now()
        }
    }

//    @Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("getLastRefreshedTime(bn : BusinessNetwork)"))
//    fun getLastRefreshedTime(bno : Party) = lastRefreshed[bno]
    fun getLastRefreshedTime(bn : BusinessNetwork) = lastRefreshedByBN[bn]

//    @Deprecated("Business Network is not uniquely identified by BNO any more.", ReplaceWith("reset(bn : BusinessNetwork)"))
//    internal fun reset(bno : Party) {
//        cache.remove(bno)
//        lastRefreshed.remove(bno)
//    }
    internal fun reset(bn : BusinessNetwork) {
        cacheByBN.remove(bn)
        lastRefreshedByBN.remove(bn)
    }
}