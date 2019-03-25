package com.r3.businessnetworks.membership.flows

import com.r3.businessnetworks.membership.states.BusinessNetwork
import com.r3.businessnetworks.membership.states.MembershipState
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import java.util.*

sealed class BNMSException(message : String) : FlowException(message)
class NotAMemberException(val counterparty : Party) : BNMSException("Counterparty $counterparty is not a member of this business network")
class MembershipNotActiveException(val counterparty : Party) : BNMSException("Counterparty's $counterparty membership in this business network is not active")
class NotBNOException(val membership : MembershipState<Any>) : BNMSException("This node is not the business network operator for this membership")
class BNONotWhitelisted(val bno : Party) : BNMSException("BNO $bno is not whitelisted as BNO")
class BNNotWhitelisted(val id : UUID) : BNMSException("BusinessNetwork $id is not whitelisted as BN")
class MembershipNotFound(val party : Party) : BNMSException("Membership for party $party not found")
class BusinessNetworkNotFound(val id: UUID? = null, val bno: Party) : BNMSException("BusinessNetwork does not exist for ${id ?: bno}.")
class BNBNOMismatchException(val id: UUID, val bno: Party) : BNMSException("$bno is not BNO for BusinessNetwork $id.")
class BNOMismatchException(val bno1: Party, val bno2: Party) : BNMSException("$bno1 is not the same as $bno2.")
class BNMismatchException(val id1: UUID, val id2: UUID) : BNMSException("$id1 is not the same as $id2.")
class NonEmptyBNException(val id: UUID?) : BNMSException("BusinessNetwork $id is not empty.")
class NoBNORegisteredException(val bn: BusinessNetwork) : BNMSException("BusinessNetwork $bn does not have an registered BNO.")
class DebugOnlyException(message: String) : BNMSException(message)
