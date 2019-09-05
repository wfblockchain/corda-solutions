package com.r3.businessnetworks.membership.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@CordaSerializable
object MembershipStateSchemaV1 : MappedSchema(
        schemaFamily = MembershipState::class.java,
        version = 1,
        mappedTypes = listOf(PersistentMembershipState::class.java)
) {
    override val migrationResource = "membership-schema.changelog-master"

    @Entity
    @Table(name = "membership_states")
    class PersistentMembershipState (
            @Column(name = "member_name")
            var member: Party,
            @Column(name = "bn_id")
            var bnId: String,
            @Column(name = "bn_name")
            var bnName: String,
            @Column(name = "bno_name")
            var bno: Party,
            @Column(name = "membership_metadata")
            var membershipMetadata: String,
            @Column(name = "status")
            var status: MembershipStatus) : PersistentState()
}

