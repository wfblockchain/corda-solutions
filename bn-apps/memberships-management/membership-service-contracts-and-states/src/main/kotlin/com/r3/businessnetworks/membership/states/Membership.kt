package com.r3.businessnetworks.membership.states

import com.r3.businessnetworks.membership.states.MembershipStatus.ACTIVE
import com.r3.businessnetworks.membership.states.MembershipStatus.PENDING
import com.r3.businessnetworks.membership.states.MembershipStatus.SUSPENDED
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant

/**
 * Contracts that verifies an evolution of [MembershipState]s. Only an evolution of [MembershipState]s is verified, not of their metadata.
 * To verify evolution of a membership metadata, users can:
 * 1. override responding flows at the BNO's side and put a custom verification logic in there (off-ledger verification)
 * 2. override [MembershipContract] and add a custom verification logic into a new contract (on-ledger verification), for example:
 *
 * class MyMembershipContract : MembershipContract {
 *  // ........
 *
 *  override fun verifyAmend(tx : LedgerTransaction, command : CommandWithParties<Commands>, outputMembership : MembershipState<*>, inputMembership : MembershipState<*>) {
 *      super.verifyAmend(tx, command, outputMembership, inputMembership)
 *      // custom logic goes in here
 *  }
 * }
 */
open class MembershipContract : Contract {
    companion object {
        const val CONTRACT_NAME = "com.r3.businessnetworks.membership.states.MembershipContract"
    }

    open class Commands : /*CommandData,*/ TypeOnlyCommandData() {
        class RegisterBNO: Commands()
        class Request : Commands()
        class Amend : Commands()
        class Suspend : Commands()
        class Activate : Commands()
    }

    override fun verify(tx : LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val output = tx.outputs.single { it.data is MembershipState<*> }
        val outputMembership = output.data as MembershipState<*>
        val bnoRefMembershipState = tx.referenceInputRefsOfType<MembershipState<*>>().singleOrNull()

        requireThat {
            "Modified date has to be greater or equal to the issued date" using (outputMembership.modified >= outputMembership.issued)
            "Both BNO and member have to be participants" using (outputMembership.participants.toSet() == setOf(outputMembership.member, outputMembership.bn.bno))
            "Output state has to be validated with ${contractName()}" using (output.contract == contractName())
            if (!tx.inputs.isEmpty()) {
                val input = tx.inputs.single()
                val inputState = input.state.data as MembershipState<*>
                "Participants of input and output states should be the same" using (outputMembership.participants.toSet() == input.state.data.participants.toSet())
                "Input state has to be validated with ${contractName()}" using (input.state.contract == contractName())
                "Input and output states should have the same issued dates" using (inputState.issued == outputMembership.issued)
                "Input and output states should have the same linear IDs" using (inputState.linearId == outputMembership.linearId)
                "Output state's modified timestamp should be greater than input's" using (outputMembership.modified > inputState.modified)
            }
            if (command.value !is Commands.RegisterBNO) {
                "All transactions except for BNO registration should contain the active BNO MemberState as reference" using (bnoRefMembershipState != null && bnoRefMembershipState.state.data.isBNO && bnoRefMembershipState.state.data.isActive())
                "All transactions except for BNO registration should contain a non-BNO output state" using (!outputMembership.isBNO)
            }
        }

        when (command.value) {
            is Commands.RegisterBNO -> verifyRegisterBNO(tx, command, outputMembership)
            is Commands.Request -> verifyRequest(tx, command, outputMembership)
            is Commands.Suspend -> verifySuspend(tx, command, outputMembership, tx.inputsOfType<MembershipState<*>>().single())
            is Commands.Activate -> verifyActivate(tx, command, outputMembership, tx.inputsOfType<MembershipState<*>>().single())
            is Commands.Amend -> verifyAmend(tx, command, outputMembership, tx.inputsOfType<MembershipState<*>>().single())
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    // custom implementations should be able to specify their own contract names
    open fun contractName() = CONTRACT_NAME

    fun verifyRegisterBNO(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*>) = requireThat {
        "Registering member should be BNO" using (outputMembership.isBNO)
        "Only BNO should sign a BNO registration transaction" using (command.signers.toSet() == setOf(outputMembership.bn.bno.owningKey))
        "BNO registration transaction shouldn't contain any input" using (tx.inputs.isEmpty())
        "BNO registration transaction should contain an output state in ACTIVE status" using (outputMembership.isActive())
    }
    open fun verifyRequest(tx : LedgerTransaction, command : CommandWithParties<Commands>, outputMembership : MembershipState<*>) = requireThat {
        "Both BNO and member have to sign a membership request transaction" using (command.signers.toSet() == outputMembership.participants.map { it.owningKey }.toSet() )
        "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
        "Membership request transaction should contain an output state in PENDING status" using (outputMembership.isPending())
    }
    open fun verifySuspend(tx : LedgerTransaction, command : CommandWithParties<Commands>, outputMembership : MembershipState<*>, inputMembership : MembershipState<*>) {
        "Only BNO should sign a suspension transaction" using (command.signers.toSet() == setOf(outputMembership.bn.bno.owningKey))
        "Input state of a suspension transaction shouldn't be already suspended" using (!inputMembership.isSuspended())
        "Output state of a suspension transaction should be suspended" using (outputMembership.isSuspended())
        "Input and output states of a suspension transaction should have the same metadata" using (inputMembership.membershipMetadata == outputMembership.membershipMetadata)
    }

    open fun verifyActivate(tx : LedgerTransaction, command : CommandWithParties<Commands>, outputMembership : MembershipState<*>, inputMembership : MembershipState<*>) {
        "Only BNO should sign a membership activation transaction" using (command.signers.toSet() == setOf(outputMembership.bn.bno.owningKey))
        "Input state of a membership activation transaction shouldn't be already active" using (!inputMembership.isActive())
        "Output state of a membership activation transaction should be active" using (outputMembership.isActive())
        "Input and output states of a membership activation transaction should have the same metadata" using (inputMembership.membershipMetadata == outputMembership.membershipMetadata)
    }

    open fun verifyAmend(tx : LedgerTransaction, command : CommandWithParties<Commands>, outputMembership : MembershipState<*>, inputMembership : MembershipState<*>) = requireThat {
        "Both BNO and member have to sign a metadata amendment transaction" using (command.signers.toSet() == outputMembership.participants.map { it.owningKey }.toSet() )
        "Both input and output states of a metadata amendment transaction should be active" using (inputMembership.isActive() && outputMembership.isActive())
        "Input and output states of an amendment transaction should have different membership metadata" using (inputMembership.membershipMetadata != outputMembership.membershipMetadata)
        "Input and output states's metadata of an amendment transaction should be of the same type" using (inputMembership.membershipMetadata.javaClass == outputMembership.membershipMetadata.javaClass)
    }
}

/**
 * Represents a membership on the ledger. Supports user defined extensions via [membershipMetadata].
 * Users can associate any custom metadata object with their [MembershipState], which will be recorded on the ledger.
 *
 * @param member identity of a member
 * @param bno identity of the BNO
 * @param issued timestamp when the state has been issued
 * @param modified timestamp when the state has been modified the last time
 * @param status status of the state, i.e. ACTIVE, SUSPENDED, PENDING etc.
 */
@BelongsToContract(MembershipContract::class)
data class MembershipState<out T : Any>(val member : Party,
//                                        val bno : Party,
                                        val bn: BusinessNetwork,
                                        val membershipMetadata : T,
                                        val issued : Instant = Instant.now(),
                                        val modified : Instant = issued,
                                        val status : MembershipStatus = MembershipStatus.PENDING,
                                        override val linearId : UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
    override fun generateMappedObject(schema : MappedSchema) : PersistentState {
        return when (schema) {
            is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                    member = this.member,
                    bnId = this.bn.bnId.id.toString(),
                    bnName = this.bn.bnId.externalId ?: "",
                    bno = this.bn.bno,
                    status = this.status
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
    override fun supportedSchemas() = listOf(MembershipStateSchemaV1)
    override val participants = setOf(bn.bno, member).toList() //if (bn.bno == member) listOf(member) else listOf(bn.bno, member)
    fun isSuspended() = status == MembershipStatus.SUSPENDED
    fun isPending() = status == MembershipStatus.PENDING
    fun isActive() = status == MembershipStatus.ACTIVE
    val isBNO = member == bn.bno
}

/**
 * Statuses that a membership can go through.
 *
 * [PENDING] - newly submitted state, haven't been approved yet. Pending members can't transact on the Business Network
 * [ACTIVE] - active members can transact on the Business Network
 * [SUSPENDED] - suspended members can't transact on the Business Network. Suspended members can be activated back.
 */
@CordaSerializable
enum class MembershipStatus {
    PENDING, ACTIVE, SUSPENDED
}

/**
 * Simple metadata example.
 */
@CordaSerializable
data class SimpleMembershipMetadata(val role : String = "", val displayedName : String = "")

/*
/**
 * This is an example of interface-based enum class.
 * MyRole supplements BaseRole.
 * One problem is name in enum cannot be overriden.
 * Hence roleName.
 */
@CordaSerializable
interface Role1 {
    val roleName: String
    val description: String
}
@CordaSerializable
enum class BaseRole1(override val roleName: String, override val description: String) : Role1 {
    BNO(roleName = "BNO", description = "Business Network Operator")
}
@CordaSerializable
enum class MyRole1(override val roleName: String, override val description: String) : Role1 {
    NB(roleName = "NB", description = "New Bank"),
    OB(roleName = "OB", description = "Old Bank"),
    SA(roleName = "SA", description = "Service Agent")
}
*/

/**
 * This base and BN specific example pair are object-declaration based.
 * There are overrides for equals, hashCode, toString so use the default object's.
 */

@CordaSerializable
interface Role {
    val name: String
    val description: String
}

@CordaSerializable
data class RoleDelegate(override val name: String, override val description: String) : Role

/**
 * Base role definition with the required BNO role.
 * We use object declaration to simulate enum class element which is also a singleton
 * There is an example of how to extend it for a BN
 */
@CordaSerializable
object BaseRole {
    object BNO: Role by RoleDelegate(name = "BNO", description = "Business Network Operator")
    /** example: equivalent object declaration without using delegate */
//    object BNO: Role { override val name = "BNO", override val description = "Business Network Operator" }
}

/*
/** An BN-specific customization */
@CordaSerializable
object MyRole {
//    object BNO: Role by BaseRole.BNO
    object NB: Role by RoleDelegate(name = "NB", description = "New Bank")
    object OB: Role by RoleDelegate(name = "OB", description = "Old Bank")
    object SA: Role by RoleDelegate(name = "SA", description = "Service Agent")
}
*/

/**
 * Base membership metadata which must include roles.
 * There is an example of how to extend it for a BN.
 */
@CordaSerializable
open class MembershipMetadata(open val roles: Set<Role>)

/**
 * This is an example to customize/extend metadata for a CorDapp
 */
/*
data class MyMembershipMetadata(override val roles: Set<Role>): MembershipMetadata(roles) {
    val displayedName : String = ""
}
*/

@CordaSerializable
data class BusinessNetwork(val bnId: UniqueIdentifier, val bno: Party) {
    /**
     * UUID id uniquely identifies the BN
     */
    override fun equals(other: Any?) = (other as? BusinessNetwork)?.let {
        this.bnId.id == other.bnId.id //&& this.bno == other.bno
    } ?: false

    fun hasDifferentBNO(other: BusinessNetwork) = this == other  && this.bno != other.bno
    fun hasDifferentDisplayedName(other: BusinessNetwork) = this == other && this.displayedName != other.displayedName

    val displayedName: String = bnId.externalId ?: bno.toString()
}