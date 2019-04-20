package com.r3.businessnetworks.membership.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.membership.flows.bno.*
import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataFlow
import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.flows.member.NotifyMemberFlowResponder
import com.r3.businessnetworks.membership.flows.member.RequestMembershipFlow
import com.r3.businessnetworks.membership.flows.member.support.BusinessNetworkAwareInitiatedFlow
import com.r3.businessnetworks.membership.states.BusinessNetwork
import com.r3.businessnetworks.membership.states.MembershipMetadata
import com.r3.businessnetworks.membership.states.MembershipState
//import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipFlow
//import com.r3.businessnetworks.membership.flows.bno.ActivateMembershipForPartyFlow
//import com.r3.businessnetworks.membership.flows.bno.SuspendMembershipFlow
//import com.r3.businessnetworks.membership.flows.bno.SuspendMembershipForPartyFlow
//import com.r3.businessnetworks.membership.flows.bno.service.DatabaseService
//import com.r3.businessnetworks.membership.flows.member.AmendMembershipMetadataFlow
//import com.r3.businessnetworks.membership.flows.member.GetMembershipsFlow
import com.r3.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.*

/**
 * @param numberOfBusinessNetworks number of BNOs to create. BNOs name is represented with O=BNO_{index of bno},L=New York,C=US, starting from 0
 *      i.e. O=BNO_0,L=New York,C=US, O=BNO_1,L=New York,C=US .... O=BNO_N,L=New York,C=US
 * @param numberOfParticipants *total* number of participants to create. Participants don't have business network membership initially
 * @param participantRespondingFlows responding flows to register for participant nodes
 * @param bnoRespondingFlows responding flows to register for BNO nodes
 */
abstract class AbstractFlowTest(private val numberOfBusinessNetworks : Int = 2,
                                private val numberOfParticipants : Int,
                                private val participantRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf(),
                                private val bnoRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf()) {
    val notaryName = CordaX500Name.parse("O=Notary,L=London,C=GB")
    lateinit var bnoNodes : List<StartedMockNode>
    lateinit var bnAndNodePairs: List<Pair<BusinessNetwork, StartedMockNode>>
    lateinit var bn: BusinessNetwork
    lateinit var id: UUID
    lateinit var bnoNode: StartedMockNode
    lateinit var participantsNodes : List<StartedMockNode>
    lateinit var participantNode: StartedMockNode
    lateinit var mockNetwork : MockNetwork
    /**
     * Note: these should be consistent with membership-service.conf
     */
    val rawBusinessNetworks = setOf(
            Triple("a590dd5a-9610-4cea-8f91-8085a8f5d5a2", "Aurum", "CN=WFBN,OU=Finance,O=WFC,L=San Francisco,C=US"),
            Triple("88579a8c-deba-48e7-be98-164b8135388f", "Janus", "CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")
    )

    @Before
    open fun setup() {
        mockNetwork = MockNetwork(
                // legacy API is used on purpose as otherwise flows defined in tests are not picked up by the framework
                cordappPackages = listOf("com.r3.businessnetworks.membership.flows", "com.r3.businessnetworks.membership.states"),
                notarySpecs = listOf(MockNetworkNotarySpec(notaryName)),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
                /**
                 * set threadPerNode = false to enable debugging into flows.
                 */
                ,threadPerNode = false
        )

        bnAndNodePairs = rawBusinessNetworks.map {
            val bnoName =  CordaX500Name.parse(it.third)
            val bnoNode = createNode(bnoName)
            val bnId = UniqueIdentifier(id = UUID.fromString(it.first), externalId = it.second)
            val bn = BusinessNetwork(bnId = bnId, bno = bnoNode.info.legalIdentities.first())
            bnoRespondingFlows.forEach { bnoNode.registerInitiatedFlow(it) }
            Pair(bn, bnoNode)
        }
        bn = bnAndNodePairs.toList().first().first
        id = bn.bnId.id
        bnoNode = bnAndNodePairs.toList().first().second

        bnoNodes = bnAndNodePairs.map {
            it.second
        }

        participantsNodes = (0..numberOfParticipants).map { indexOfParticipant ->
            val node = createNode(CordaX500Name.parse("O=Participant $indexOfParticipant,L=London,C=GB"))
            participantRespondingFlows.forEach { node.registerInitiatedFlow(it) }
            node
        }
        participantNode = participantsNodes.first()

        mockNetwork.runNetwork()
    }

    private fun createNode(name : CordaX500Name) =
            mockNetwork.createNode(MockNodeParameters(legalName = name))

    @After
    open fun tearDown() {
        mockNetwork.stopNodes()
        NotificationsCounterFlow.NOTIFICATIONS.clear()
    }

    /**
     * MembershipMetadata(setOf()) will fail GetMembershipsFlowTest `node should maintain separate lists of memberships per business network`()
     * because MembershipeMetadata is not a data class.
     */
    fun runRegisterBNOFlow(id: UUID, node: StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT") /*MembershipMetadata(setOf())*/): SignedTransaction {
        val future = node.startFlow(RegisterBNOFlow(id, membershipMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()

    }

    fun runRequestAndActivateMembershipFlow(id: UUID, bnoNode : StartedMockNode, participantNode : StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) {
        runRequestMembershipFlow(id, bnoNode, participantNode, membershipMetadata)
        runActivateMembershipFlow(id, /*bnoNode,*/ participantNode.identity())
    }

    fun runRequestAndActivateMembershipFlow(id: UUID, bnoNode : StartedMockNode, participantNodes : List<StartedMockNode>, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) {
        runRequestMembershipFlow(id, bnoNode, participantNodes, membershipMetadata)
        runActivateMembershipFlow(id, /*bnoNode,*/ participantNodes.map { it.identity() })
    }

    fun runRequestMembershipFlow(id: UUID, bnoNode : StartedMockNode, participantNode : StartedMockNode, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) : SignedTransaction {
        val future = participantNode.startFlow(RequestMembershipFlow(id = id, bno = bnoNode.identity(), membershipMetadata = membershipMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runRequestMembershipFlow(id: UUID, bnoNode : StartedMockNode, participantNodes : List<StartedMockNode>, membershipMetadata : Any = SimpleMembershipMetadata(role = "DEFAULT")) : List<SignedTransaction> {
        return participantNodes.map { runRequestMembershipFlow(id, bnoNode, it, membershipMetadata) }
    }

    fun runSuspendMembershipFlow(id: UUID, /*bnoNode : StartedMockNode,*/ participant : Party) : SignedTransaction {
        val bn: BusinessNetwork = bnAndNodePairs.toList().filter { it.first.bnId.id == id }.first().first
        val bnoNode: StartedMockNode = bnAndNodePairs.toList().filter { it.first.bnId.id == id }.first().second
        val membership = getMembership(bnoNode, participant, bn)
        val future = bnoNode.startFlow(SuspendMembershipFlow(id, membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runSuspendMembershipForPartyFlow(id: UUID, /*bnoNode : StartedMockNode,*/ participant : Party) : SignedTransaction {
        val bnoNode: StartedMockNode = bnAndNodePairs.toList().filter { it.first.bnId.id == id }.first().second
        val future = bnoNode.startFlow(SuspendMembershipForPartyFlow(id, participant))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipFlow(id: UUID, /*bnoNode : StartedMockNode,*/ participant : Party) : SignedTransaction {
        val bn: BusinessNetwork = bnAndNodePairs.toList().filter { it.first.bnId.id == id }.first().first
        val bnoNode: StartedMockNode = bnAndNodePairs.toList().filter { it.first.bnId.id == id }.first().second
        val membership = getMembership(bnoNode, participant, bn)
        val future = bnoNode.startFlow(ActivateMembershipFlow(id, membership))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runActivateMembershipFlow(id: UUID, /*bnoNode : StartedMockNode,*/ participants : List<Party>) : List<SignedTransaction> {
        return participants.map { runActivateMembershipFlow(id, it) }
    }

    fun runActivateMembershipForPartyFlow(id: UUID, /*bnoNode : StartedMockNode,*/ participant : Party) : SignedTransaction {
        val bnoNode: StartedMockNode = bnAndNodePairs.toList().filter { it.first.bnId.id == id }.first().second
        val future = bnoNode.startFlow(ActivateMembershipForPartyFlow(id, participant))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runAmendMetadataFlow(id: UUID?, bnoNode : StartedMockNode, participantNode : StartedMockNode, newMetadata : Any) : SignedTransaction {
        val future = participantNode.startFlow(AmendMembershipMetadataFlow(id, bnoNode.identity(), newMetadata))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun runGetMembershipsListFlow(id: UUID, bnoNode : StartedMockNode, participantNode : StartedMockNode, force : Boolean = false, filterOutNotExisting : Boolean = true) : Map<Party, StateAndRef<MembershipState<Any>>> {
        val future = participantNode.startFlow(GetMembershipsFlow(id, bnoNode.identity(), force, filterOutNotExisting))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun getMembership(nodeToGetFrom : StartedMockNode, member : Party, bn : BusinessNetwork) : StateAndRef<MembershipState<Any>> = nodeToGetFrom.transaction {
        val dbService = nodeToGetFrom.services.cordaService(DatabaseService::class.java)
        dbService.getMembership(member, bn)!!
    }

    fun getAllMemberships(nodeToGetFrom : StartedMockNode, bn : BusinessNetwork) : List<StateAndRef<MembershipState<Any>>> = nodeToGetFrom.transaction {
        val dbService = nodeToGetFrom.services.cordaService(DatabaseService::class.java)
        dbService.getAllMemberships(bn)
    }

    fun allTransactions(node : StartedMockNode) = node.transaction {
        node.services.validatedTransactions.track().snapshot
    }

    fun fileFromClasspath(fileName : String) = File(AbstractFlowTest::class.java.classLoader.getResource(fileName).toURI())
}

fun StartedMockNode.identity() = info.legalIdentities.single()
fun List<StartedMockNode>.identities() = map { it.identity() }

@InitiatedBy(NotifyMemberFlow::class)
class NotificationsCounterFlow(session : FlowSession) : NotifyMemberFlowResponder(session) {
    companion object {
        val NOTIFICATIONS : MutableSet<NotificationHolder> = mutableSetOf()
    }

    @Suspendable
    override fun call() {
        val notification = super.call()
        NOTIFICATIONS.add(NotificationHolder(ourIdentity, session.counterparty, notification))
    }
}

data class NotificationHolder(val member : Party, val bno : Party, val notification : Any)

open class AbstractDummyInitiatingFlow(private val counterparty : Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        initiateFlow(counterparty).sendAndReceive<String>("Hello")
    }
}

open class AbstractBNAwareRespondingFlow(session : FlowSession, private val id: UUID?, private val bnoName : String) : BusinessNetworkAwareInitiatedFlow<Unit>(session)  {
    //    override fun bnoIdentity()  = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(bnoName))!!
    override fun bn() = confSvc().bn(id, serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(bnoName))!!)
    override fun bnoIdentity() = bn().bno

    @Suspendable
    override fun onOtherPartyMembershipVerified() {
        flowSession.receive<String>().unwrap { it }
        flowSession.send("Hello")
    }
}

@InitiatingFlow
class BN_1_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_2_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_3_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_4_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatingFlow
class BN_5_InitiatingFlow(counterparty : Party) : AbstractDummyInitiatingFlow(counterparty)

@InitiatedBy(BN_1_InitiatingFlow::class)
class BN_1_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"CN=WFBN,OU=Finance,O=WFC,L=San Francisco,C=US")

@InitiatedBy(BN_2_InitiatingFlow::class)
class BN_2_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")

@InitiatedBy(BN_3_InitiatingFlow::class)
class BN_3_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")

@InitiatedBy(BN_4_InitiatingFlow::class)
class BN_4_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")

@InitiatedBy(BN_5_InitiatingFlow::class)
class BN_5_RespondingFlow(session : FlowSession) : AbstractBNAwareRespondingFlow(session, null,"CN=Servicer,OU=Lending,O=WFC,L=San Francisco,C=US")