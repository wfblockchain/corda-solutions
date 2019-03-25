package com.r3.businessnetworks.membership.flows.service

import com.r3.businessnetworks.membership.flows.BNNotWhitelisted
import com.r3.businessnetworks.membership.flows.BNBNOMismatchException
import com.r3.businessnetworks.membership.flows.BusinessNetworkNotFound
import com.typesafe.config.ConfigFactory
import com.r3.businessnetworks.membership.flows.ConfigUtils.loadConfig
import com.r3.businessnetworks.membership.states.BusinessNetwork
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
 * Configuration that is used by both bno and member apps.
 */
@CordaService
class MembershipConfigurationService(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // X500 name of the BNO
        const val BUSINESS_NETWORKS = "business-networks"
        const val BN_ID = "id"
        const val BN_NAME = "name"
        const val BN_BNO_NAME = "bnoName"
        const val NOTARY_NAME = "notaryName"

        val logger = loggerFor<MembershipConfigurationService>()
    }

    private var _config = loadConfig()

    /**
     * Get the raw business networks in Triple(bnId, name, bnoName)
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
                        BusinessNetwork(bnId = bnId, bno = it)
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

    /**
     * For CorDapps flow APIs, we will have the UUID of the BN as a parameter to identify the BN the CorDapp operates in.
     */
    fun bnFromId(id: UUID): BusinessNetwork? = bns().singleOrNull { it.bnId.id == id }

    fun bnoFromId(id: UUID): Party? = bnFromId(id)?.bno

    /**
     * For a legacy CorDapp which does not have the desired UUID in the flow APIs, we use this to identify the BN from a given BNO.
     * In this model, we cannot have more than one BN using the same BNO.
     */
    fun bnFromBNO(bno: Party): BusinessNetwork? = bns().singleOrNull { it.bno == bno }

    fun bnsFromBNO(bno: Party): Set<BusinessNetwork> = bns().filter { it.bno == bno }.toSet()

    /**
     * This is the comprehensive validation logic to ensure id and bno are all matching for both the legacy and the new configurations.
     */
    fun bn(id: UUID?, bno: Party): BusinessNetwork = (id?.let {
        val bn = bnFromId(id)
        bn?.let { if (it.bno == bno) it else throw BNBNOMismatchException(it.bnId.id, bno) } ?: throw BNNotWhitelisted(it)
    } ?: bnFromBNO(bno)) ?: throw BusinessNetworkNotFound(id, bno)

    private fun notaryName() : CordaX500Name = CordaX500Name.parse(_config.getString(NOTARY_NAME))
    fun notaryParty() = serviceHub.networkMapCache.getNotary(notaryName())
            ?: throw IllegalArgumentException("Notary ${notaryName()} has not been found on the network")

    fun reloadConfigurationFromFile(file : File) {
        _config = ConfigFactory.parseFile(file)
    }
}
