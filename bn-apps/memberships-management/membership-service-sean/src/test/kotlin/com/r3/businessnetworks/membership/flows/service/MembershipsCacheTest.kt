package com.r3.businessnetworks.membership.flows.service

import com.r3.businessnetworks.membership.flows.member.service.MembershipsCache
import com.r3.businessnetworks.membership.states.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MembershipsCacheTest {
    private val bno1 = TestIdentity(CordaX500Name.parse("CN=WFBN,OU=Finance,O=WFC,L=San Francisco,C=US")).party
    private val bno2 = TestIdentity(CordaX500Name.parse("CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")).party
//    private val bno1 = TestIdentity(CordaX500Name.parse("O=BNO1,L=New York,C=US")).party
//    private val bno2 = TestIdentity(CordaX500Name.parse("O=BNO2,L=New York,C=US")).party
    private val member1 = TestIdentity(CordaX500Name.parse("O=Member1,L=London,C=GB")).party
    private val member2 = TestIdentity(CordaX500Name.parse("O=Member2,L=London,C=GB")).party
    private val member3 = TestIdentity(CordaX500Name.parse("O=Member3,L=London,C=GB")).party
    private val notary = TestIdentity(CordaX500Name.parse("O=Notary,L=London,C=GB")).party

    private val businessNetworks = setOf(
            BusinessNetwork(UniqueIdentifier(id = UUID.fromString("a590dd5a-9610-4cea-8f91-8085a8f5d5a2"), externalId = "Aurum"), bno1),
            BusinessNetwork(UniqueIdentifier(id = UUID.fromString("88579a8c-deba-48e7-be98-164b8135388f"), externalId = "Janus"), bno2)
    )
    private val bn1 = businessNetworks.toList().first()
    private val bn2 = businessNetworks.toList().last()

    private val membership1 = membershipStateFor(bn1, member1)
    private val membership2 = membershipStateFor(bn1, member2)
    private val membership3 = membershipStateFor(bn2, member3)

    private val cache = MembershipsCache()

    @Test
    fun `getMembership for not existing BNO or member returns null`() {
        assertNull(cache.getMembership(bn1, member1))
    }

    @Test
    fun `getMembership happy path`() {
        cache.updateMembership(membership1.toStateAndRef())
        cache.updateMembership(membership2.toStateAndRef())
        cache.updateMembership(membership3.toStateAndRef())

        assertEquals(membership1.toStateAndRef(), cache.getMembership(bn1, member1))
        assertEquals(membership2.toStateAndRef(), cache.getMembership(bn1, member2))
        assertEquals(membership3.toStateAndRef(), cache.getMembership(bn2, member3))
    }

    @Test
    fun `getMemberships for not existing BNO returns an empty map`() {
        assertTrue(cache.getMemberships(bn1).isEmpty())
    }

    @Test
    fun `getMemberships happy path`() {
        cache.updateMembership(membership1.toStateAndRef())
        cache.updateMembership(membership2.toStateAndRef())
        cache.updateMembership(membership3.toStateAndRef())

        assertEquals(mapOf(Pair(member1, membership1.toStateAndRef()), Pair(member2, membership2.toStateAndRef())), cache.getMemberships(bn1))
        assertEquals(mapOf(Pair(member3, membership3.toStateAndRef())), cache.getMemberships(bn2))
    }

    @Test
    fun `updateMembership happy path`() {
        cache.updateMembership(membership1.toStateAndRef())
        val updatedMembership = membership1.copy(modified = membership1.modified.plusSeconds(10))
        cache.updateMembership(updatedMembership.toStateAndRef())
        assertEquals(updatedMembership.toStateAndRef(), cache.getMembership(bn1, member1))
        // single membership updates shouldn't update last refresh time
        assertNull(cache.getLastRefreshedTime(bn1))
    }

    @Test
    fun `updateMembership membership which modified time is later should be taken`() {
        cache.updateMembership(membership1.toStateAndRef())
        val updatedMembership = membership1.copy(modified = membership1.modified.minusSeconds(10))
        cache.updateMembership(updatedMembership.toStateAndRef())
        assertEquals(membership1.toStateAndRef(), cache.getMembership(bn1, member1))
    }

    @Test
    fun `applyMembershipsSnapshot happy path`() {
        cache.updateMembership(membership1.toStateAndRef())
        cache.updateMembership(membership2.toStateAndRef())

        val updatedMembership1 = membership1.copy(modified = membership1.modified.minusSeconds(10))
        val updatedMembership2 = membership2.copy(modified = membership1.modified.plusSeconds(10))

        cache.applyMembershipsSnapshot(listOf(updatedMembership1.toStateAndRef(), updatedMembership2.toStateAndRef()))

        // membership1 should still be in the cache as its modified time is after updatedMembership1's
        assertEquals(membership1.toStateAndRef(), cache.getMembership(bn1, member1))
        assertEquals(updatedMembership2.toStateAndRef(), cache.getMembership(bn1, member2))
        // The last refreshed time for BNO1 should have been updated
        assertNotNull(cache.getLastRefreshedTime(bn1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `applyMembershipsSnapshot memberships from different BNOs can't coexist in the same snapshot`() {
        cache.applyMembershipsSnapshot(listOf(membership1.toStateAndRef(), membership3.toStateAndRef()))
    }

    @Test
    fun testResetCache() {
        cache.applyMembershipsSnapshot(listOf(membership1, membership2).map { it.toStateAndRef() })
        cache.applyMembershipsSnapshot(listOf(membership3.toStateAndRef()))

        cache.reset(bn1)

        // cache reset should remove all memberships as well as the last modified date
        assertNull(cache.getLastRefreshedTime(bn1))
        assertTrue(cache.getMemberships(bn1).isEmpty())
        // memberships from BNO2 should remain untouched
        assertNotNull(cache.getLastRefreshedTime(bn2))
        assertEquals(mapOf(Pair(member3, membership3.toStateAndRef())), cache.getMemberships(bn2))
    }

    private fun membershipStateFor(bn : BusinessNetwork, member : Party) =  MembershipState(member, bn, SimpleMembershipMetadata(), Instant.now(), Instant.now(), MembershipStatus.PENDING)
    private fun MembershipState<Any>.toStateAndRef() = StateAndRef(TransactionState(this, MembershipContract.CONTRACT_NAME, notary), StateRef(SecureHash.zeroHash, 0))
}