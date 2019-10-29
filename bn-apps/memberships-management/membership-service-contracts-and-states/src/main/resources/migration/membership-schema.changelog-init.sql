--liquibase formatted sql

--changeset membership:init dbms:azure
create table dbo.membership_states (
  output_index int not null,
  transaction_id nvarchar(64) not null,
  member_name nvarchar(255) not null,
  bn_id nvarchar(64) not null,
  bn_name nvarchar(255) not null,
  bno_name nvarchar(255) not null,
  membership_metadata nvarchar(MAX) not null,
  status nvarchar(20) not null
)

--rollback drop table dbo.membership_states;
