package com.r3.businessnetworks.membership.flows.service

import com.r3.businessnetworks.membership.flows.AbstractFlowTest
import kotlin.test.assertTrue
import org.junit.Test

class MembershipConfigurationServiceTest  : AbstractFlowTest(
        numberOfParticipants = 1) {

    @Test
    fun `Test config api - number of raw BNs is expected`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)//.reloadConfigurationFromFile(fileFromClasspath("membership-service-without-bno-whitelist.conf"))
        val bns = confSvc.rawBNs()
        assertTrue( bns.size == 2)
    }
    @Test
    fun `Test config api - raw BNs are the expected triples`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)
        val rawBNs = confSvc.rawBNs()
        assertTrue( rawBNs == rawBusinessNetworks)
    }
    @Test
    fun `Verify the number of BNO parties`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)
        val bnos = confSvc.bnoIdentities()
        assertTrue( bnos.size == 2 )
    }
    @Test
    fun `Verify BNO identities`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)
        val bnos = confSvc.bnoIdentities()
        assertTrue( bnos == bnoNodes.map { it.info.legalIdentities.first() }.toSet() )
    }

    @Test
    fun `Verify BNO parties`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)
        val bnos = confSvc.bnoParties()
        assertTrue( bnos == bnoNodes.map { it.info.legalIdentities.first() }.toSet() )
    }

    @Test
    fun `Verify the number of BNs`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)
        val bns = confSvc.bns()
        assertTrue( bns.size == 2 )
    }
    @Test
    fun `Verify BNs`() {
        val participantNode = participantsNodes[0]
        val confSvc = participantNode.services.cordaService(MembershipConfigurationService::class.java)
        val bns = confSvc.bns()
        assertTrue( bns == bnAndNodePairs.map { it.first }.toSet() )
    }
}