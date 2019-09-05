--liquibase formatted sql

--changeset pending_membership_request:init
create table dbo.pending_membership_requests (
  bnId BIGINT NOT NULL /*IDENTITY(1, 1)*/ PRIMARY KEY CLUSTERED,
  pending_member NVARCHAR(255) NOT NULL UNIQUE
)

--rollback drop table dbo.pending_membership_requests;
