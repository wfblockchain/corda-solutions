package com.r3.businessnetworks.cordaupdates.app.bno

import co.paralleluniverse.fibers.Suspendable
import com.r3.businessnetworks.cordaupdates.app.member.CordappVersionInfo
import com.r3.businessnetworks.cordaupdates.app.member.ReportCordappVersionFlow
import com.r3.businessnetworks.cordaupdates.transport.flows.AbstractRepositoryHosterResponder
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Persists the reported cordapp version to the database via [DatabaseService].
 * Supports [SessionFilter]s to prevent unauthorised access.
 */
@InitiatedBy(ReportCordappVersionFlow::class)
class ReportCordappVersionFlowResponder(session : FlowSession) : AbstractRepositoryHosterResponder<Unit>(session) {

    @Suspendable
    override fun postPermissionCheck() {
        val cordappVersionInfo : CordappVersionInfo = session.receive<CordappVersionInfo>().unwrap {
            // we don't verify the payload here, as we know that the request is coming from a known participant,
            // who has passed through the session filter
            it
        }
        val dbService : DatabaseService = serviceHub.cordaService(DatabaseService::class.java)
        dbService.updateCordappVersionInfo(session.counterparty, cordappVersionInfo)
    }
}

/**
 * Retrieves all reported cordapps versions from the database
 */
@StartableByRPC
class GetCordappVersionsFlow : FlowLogic<Map<String, List<CordappVersionInfo>>>() {

    // TODO: remove the progress tracker once Corda v4 is released
    companion object {
        val QUERYING_FROM_DATABASE = ProgressTracker.Step("Querying data from the database")

        fun tracker() = ProgressTracker(QUERYING_FROM_DATABASE)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : Map<String, List<CordappVersionInfo>> {
        progressTracker.currentStep = QUERYING_FROM_DATABASE
        val dbService : DatabaseService = serviceHub.cordaService(DatabaseService::class.java)
        return dbService.getCordappVersionInfos()
    }
}

/**
 * Retrieves all reported cordapps versions from the database for the specified party
 *
 * @param party party to lookup versions for
 */
@StartableByRPC
// when invoked from shell, strings are resolved to parties automatically
class GetCordappVersionsForPartyFlow(private val party : Party) : FlowLogic<List<CordappVersionInfo>>() {

    // TODO: remove the progress tracker once Corda v4 is released
    companion object {
        val QUERYING_FROM_DATABASE = ProgressTracker.Step("Querying data from the database")

        fun tracker() = ProgressTracker(QUERYING_FROM_DATABASE)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : List<CordappVersionInfo> {
        progressTracker.currentStep = QUERYING_FROM_DATABASE
        val dbService : DatabaseService = serviceHub.cordaService(DatabaseService::class.java)
        return dbService.getCordappVersionInfos(party)
    }
}