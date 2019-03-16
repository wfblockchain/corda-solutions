package com.r3.businessnetworks.membership.states

import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant
import java.util.*

class MembershipContractTest {
    private val member = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB"))
    private val anotherMember = TestIdentity(CordaX500Name.parse("O=Another Member,L=New York,C=US"))
    private val bno = TestIdentity(CordaX500Name.parse("O=BNO,L=London,C=GB"))
    private val memberParty = member.party
    private val anotherMemberParty = anotherMember.party
    private val bnoParty = bno.party
    private val bnId = UniqueIdentifier("Aurum BN", UUID.fromString("7ebcc7ca-1e5b-4631-855e-a2f5b7096cd5"))
    private val bn = BusinessNetwork(bnId, bnoParty)

//    private fun membershipState(status : MembershipStatus = MembershipStatus.PENDING, member : Party = memberParty, bno : Party = bnoParty, issued : Instant = Instant.now())
//            = MembershipState(member, bno, issued = issued, status = status, membershipMetadata = SimpleMembershipMetadata("test"))
    private fun membershipState(status : MembershipStatus = MembershipStatus.PENDING, member : Party = memberParty, bn : BusinessNetwork = this.bn, issued : Instant = Instant.now())
            = MembershipState(member, bn, issued = issued, status = status, membershipMetadata = SimpleMembershipMetadata("test"))
    private val bnoMemberState = membershipState(MembershipStatus.ACTIVE, bnoParty, bn)

    private var ledgerServices = MockServices(
            cordappPackages = listOf("com.r3.businessnetworks.membership.states"),
            firstIdentity = bno,
            networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4
            )
    )

    @Test
    fun `test common assertions`() {
        ledgerServices.ledger {
            val output = membershipState()
            transaction {
                output(MembershipContract.CONTRACT_NAME,  output.copy(modified = Instant.now().minusSeconds(100)))
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Modified date has to be greater or equal to the issued date")
            }
            transaction {
                val input = output.copy(member = anotherMemberParty)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  output)
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Participants of input and output states should be the same")
            }
            transaction {
                val input = output.copy()
                input(MyDummyContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME, output )
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Input state has to be validated with ${MembershipContract.CONTRACT_NAME}")
            }
            transaction {
                val wrongIssuedDate = Instant.now().minusSeconds(100)
                val input = output.copy(issued = wrongIssuedDate, modified = wrongIssuedDate)
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output )
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Input and output states should have the same issued dates")
            }
            transaction {
                val input = output.copy(linearId = UniqueIdentifier())
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Input and output states should have the same linear ids")
            }
            transaction {
                val input = output.copy()
                input(MembershipContract.CONTRACT_NAME, input)
                output(MyDummyContract.CONTRACT_NAME, output)
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Output state has to be validated with ${MembershipContract.CONTRACT_NAME}")
            }
            transaction {
                val issuedDate = Instant.now()
                val inputModifiedDate = issuedDate.plusSeconds(10)
                val outputModifiedDate = issuedDate.plusSeconds(5)
                input(MembershipContract.CONTRACT_NAME, output.copy(issued = issuedDate, modified = inputModifiedDate))
                output(MembershipContract.CONTRACT_NAME, output.copy(issued = issuedDate, modified = outputModifiedDate))
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Output state's modified timestamp should be greater than input's")
            }
        }
    }

    @Test
    fun `test common assertions related to BNO`() {
        ledgerServices.ledger {
            val output = membershipState()
            transaction {
                output(MembershipContract.CONTRACT_NAME,  output)
                command(listOf(member.publicKey, bno.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("All transactions except for BNO registration should contain the active BNO MemberState as reference")
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                verifies()
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME,  bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Request())
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                this.`fails with`("All transactions except for BNO registration should contain a non-BNO output state")
            }
        }
    }

    @Test
    fun `test register BNO`() {
        ledgerServices.ledger {
            transaction {
                val output = bnoMemberState.copy(member = memberParty)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bno.publicKey), MembershipContract.Commands.RegisterBNO())
                this.`fails with`("Registering member should be BNO")
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(member.publicKey), MembershipContract.Commands.RegisterBNO())
                this.`fails with`("Only BNO should sign a BNO registration transaction")
            }
            transaction {
                val input = bnoMemberState.copy(modified = bnoMemberState.issued.minusMillis(2))
                input(MembershipContract.CONTRACT_NAME, input)
                output(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.RegisterBNO())
                this.`fails with`("BNO registration transaction shouldn't contain any input")
            }
            transaction {
                val output = bnoMemberState.copy(status = MembershipStatus.PENDING)
                output(MembershipContract.CONTRACT_NAME, output)
                command(listOf(bno.publicKey), MembershipContract.Commands.RegisterBNO())
                this.`fails with`("BNO registration transaction should contain an output state in ACTIVE status")
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.RegisterBNO())
                verifies()
            }
        }
    }

    @Test
    fun `test request membership`() {
        ledgerServices.ledger {
            transaction {
                output(MembershipContract.CONTRACT_NAME,  membershipState())
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(member.publicKey, bno.publicKey), MembershipContract.Commands.Request())
                this.verifies()
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME,  membershipState())
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(member.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Both BNO and member have to sign a membership request transaction")
            }
            transaction {
                output(MembershipContract.CONTRACT_NAME,  membershipState(MembershipStatus.SUSPENDED))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(member.publicKey, bno.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Membership request transaction should contain an output state in PENDING status")
            }
            transaction {
                val state  = membershipState()
                input(MembershipContract.CONTRACT_NAME,  state)
                output(MembershipContract.CONTRACT_NAME,  state.copy(modified = state.modified.plusSeconds(10)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(member.publicKey, bno.publicKey), MembershipContract.Commands.Request())
                this.`fails with`("Membership request transaction shouldn't contain any inputs")
            }
        }
    }

    @Test
    fun `test suspend membership`() {
        ledgerServices.ledger {
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.SUSPENDED, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Suspend())
                this.verifies()
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.SUSPENDED, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Suspend())
                this.`fails with`("Only BNO should sign a suspension transaction")
            }
            transaction {
                val input = membershipState(MembershipStatus.SUSPENDED)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.SUSPENDED, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Suspend())
                this.`fails with`("Input state of a suspension transaction shouldn't be already suspended")
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.PENDING, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Suspend())
                this.`fails with`( "Output state of a suspension transaction should be suspended")
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.SUSPENDED, modified = input.modified.plusMillis(1), membershipMetadata = SimpleMembershipMetadata(role = "Another role")))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Suspend())
                this.`fails with`("Input and output states of a suspension transaction should have the same metadata")
            }
        }
    }

    @Test
    fun `test activate membership`() {
        ledgerServices.ledger {
            transaction {
                val input = membershipState(MembershipStatus.SUSPENDED)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Activate())
                this.verifies()
            }
            transaction {
                val input = membershipState(MembershipStatus.SUSPENDED)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Activate())
                this.`fails with`("Only BNO should sign a membership activation transaction")
            }
            transaction {
                val input = membershipState(MembershipStatus.ACTIVE)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Activate())
                this.`fails with`("Input state of a membership activation transaction shouldn't be already active")
            }
            transaction {
                val input = membershipState(MembershipStatus.SUSPENDED)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.PENDING, modified = input.modified.plusMillis(1)))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Activate())
                this.`fails with`("Output state of a membership activation transaction should be active")
            }
            transaction {
                val input = membershipState(MembershipStatus.SUSPENDED)
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1), membershipMetadata = SimpleMembershipMetadata(role = "Another metadata")))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Activate())
                this.`fails with`("Input and output states of a membership activation transaction should have the same metadata")
            }
        }
    }


    @Test
    fun `test amend member metadata`() {
        val input = membershipState(MembershipStatus.ACTIVE)
        val output = input.copy(status = MembershipStatus.ACTIVE, modified = input.modified.plusMillis(1), membershipMetadata = SimpleMembershipMetadata(role = "New metadata"))
        ledgerServices.ledger {
            transaction {
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  output)
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Amend())
                this.verifies()
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  output)
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey), MembershipContract.Commands.Amend())
                this.`fails with`("Both BNO and member have to sign a metadata amendment transaction")
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME,  input.copy(status = MembershipStatus.PENDING))
                output(MembershipContract.CONTRACT_NAME,  output)
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Amend())
                this.`fails with`("Both input and output states of a metadata amendment transaction should be active")
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  output.copy(status = MembershipStatus.PENDING))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Amend())
                this.`fails with`("Both input and output states of a metadata amendment transaction should be active")
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME,  input)
                output(MembershipContract.CONTRACT_NAME,  output.copy(membershipMetadata = input.membershipMetadata))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Amend())
                this.`fails with`("Input and output states of an amendment transaction should have different membership metadata")
            }
            transaction {
                input(MembershipContract.CONTRACT_NAME,  input)
                // changing metadata to an object of a different type
                output(MembershipContract.CONTRACT_NAME,  (output as MembershipState<Any>).copy(membershipMetadata = 1))
                reference(MembershipContract.CONTRACT_NAME, bnoMemberState)
                command(listOf(bno.publicKey, member.publicKey), MembershipContract.Commands.Amend())
                this.`fails with`("Input and output states's metadata of an amendment transaction should be of the same type")
            }
        }
    }
}

class MyDummyContract : Contract {
    companion object {
        const val CONTRACT_NAME = "com.r3.businessnetworks.membership.states.MyDummyContract"
    }

    override fun verify(tx : LedgerTransaction) {
    }
}