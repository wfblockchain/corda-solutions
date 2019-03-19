package com.r3.businessnetworks.membership.flows.member.service

import com.typesafe.config.ConfigFactory
import com.r3.businessnetworks.membership.flows.ConfigUtils.loadConfig
import com.r3.businessnetworks.membership.states.BusinessNetwork
import com.r3.businessnetworks.membership.states.MembershipContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.io.File
import java.util.*

/**
 * Configuration that is used by member app.
 */
@CordaService
class MemberConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // X500 name of the BNO
        const val BUSINESS_NETWORKS = "business-networks"
        const val BN_ID = "id"
        const val BN_NAME = "name"
        const val BN_BNO_NAME = "bnoName"
        val logger = loggerFor<MemberConfigurationService>()
    }

    private var _config = loadConfig()

    /**
     * Get the raw business networks in Triple(id, name, bnoName)
     */
    fun rawBNs(): Set<Triple<String, String, String>> {
        return (if (_config.hasPath(BUSINESS_NETWORKS)) _config.getObjectList(BUSINESS_NETWORKS) else listOf())
                .asSequence().map {
                    val config = it.toConfig()
                    Triple(config.getString(BN_ID), config.getString(BN_NAME), config.getString(BN_BNO_NAME))
                }.filterNotNull().toSet()
    }

    /**
     * BNOs should be explicitly whitelisted. Any attempt to communicate with not whitelisted BNO would fail.
     */
    @Deprecated("This queries configuration service directly.", ReplaceWith("bnoParties()"))
    fun bnoIdentities() : Set<Party> {
        return (if (_config.hasPath(BUSINESS_NETWORKS)) _config.getObjectList(BUSINESS_NETWORKS) else listOf())
                .asSequence().map {
                    val config = it.toConfig()
                    val x500Name = CordaX500Name.parse(config.getString(BN_BNO_NAME))
                    val party = serviceHub.identityService.wellKnownPartyFromX500Name(x500Name)
                    if (party == null) {
                        logger.warn("BNO identity $it can't be resolver on the network")
                    }
                    party
                }.filterNotNull().toSet()
    }

    /**
     * Whitelisted BNs that are live.
     */
    fun bns(): Set<BusinessNetwork> {
        return (if (_config.hasPath(BUSINESS_NETWORKS)) _config.getObjectList(BUSINESS_NETWORKS) else listOf())
                .asSequence().map {
                    val config = it.toConfig()
                    val bnId = UniqueIdentifier(id = UUID.fromString(config.getString(BN_ID)), externalId = config.getString(BN_NAME))
                    val x500Name = CordaX500Name.parse(config.getString(BN_BNO_NAME))
                    val party = serviceHub.identityService.wellKnownPartyFromX500Name(x500Name)
                    party?.let {
                        BusinessNetwork(id = bnId, bno = it)
                    } ?: null

                }.filterNotNull().toSet()
    }

    /**
     * Whitelisted BNOs from whitelisted BNs.
     */
    fun bnoParties(): Set<Party> {
        return bns().map {
            it.bno
        }.toSet()
    }

    fun bnFromBNO(bno: Party): BusinessNetwork? = bns().firstOrNull { it.bno == bno }

    fun reloadConfigurationFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }
}
