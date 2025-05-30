CREATE TABLE ACT_DMN_DEPLOYMENT (ID_ varchar(255) NOT NULL, NAME_ nvarchar(255), CATEGORY_ varchar(255), DEPLOY_TIME_ datetime, TENANT_ID_ varchar(255), PARENT_DEPLOYMENT_ID_ varchar(255), CONSTRAINT PK_ACT_DMN_DEPLOYMENT PRIMARY KEY (ID_));

CREATE TABLE ACT_DMN_DEPLOYMENT_RESOURCE (ID_ varchar(255) NOT NULL, NAME_ nvarchar(255), DEPLOYMENT_ID_ varchar(255), RESOURCE_BYTES_ varbinary(MAX), CONSTRAINT PK_ACT_DMN_DEPLOYMENT_RESOURCE PRIMARY KEY (ID_));

CREATE TABLE ACT_DMN_DECISION (ID_ varchar(255) NOT NULL, NAME_ nvarchar(255), VERSION_ int, KEY_ varchar(255), CATEGORY_ nvarchar(255), DECISION_TYPE_ varchar(255), DEPLOYMENT_ID_ varchar(255), TENANT_ID_ varchar(255), RESOURCE_NAME_ nvarchar(255), DESCRIPTION_ nvarchar(255), CONSTRAINT PK_ACT_DMN_DECISION_TABLE PRIMARY KEY (ID_));

CREATE TABLE ACT_DMN_HI_DECISION_EXECUTION (ID_ varchar(255) NOT NULL, DECISION_DEFINITION_ID_ varchar(255), DEPLOYMENT_ID_ varchar(255), START_TIME_ datetime, END_TIME_ datetime, INSTANCE_ID_ varchar(255), EXECUTION_ID_ varchar(255), ACTIVITY_ID_ varchar(255), SCOPE_TYPE_ varchar(255), FAILED_ bit CONSTRAINT DF_ACT_DMN_HI_DECISION_EXECUTION_FAILED_ DEFAULT 0, TENANT_ID_ varchar(255), EXECUTION_JSON_ varchar(MAX), CONSTRAINT PK_ACT_DMN_HI_DECISION_EXECUTION PRIMARY KEY (ID_));

ALTER TABLE ACT_DMN_DEPLOYMENT_RESOURCE
    ADD CONSTRAINT ACT_FK_DMN_RSRC_DPL FOREIGN KEY (DEPLOYMENT_ID_) REFERENCES ACT_DMN_DEPLOYMENT (ID_);

CREATE NONCLUSTERED INDEX ACT_IDX_DMN_RSRC_DPL ON ACT_DMN_DEPLOYMENT_RESOURCE(DEPLOYMENT_ID_);

CREATE UNIQUE NONCLUSTERED INDEX ACT_IDX_DMN_DEC_UNIQ ON ACT_DMN_DECISION(KEY_, VERSION_, TENANT_ID_);

CREATE NONCLUSTERED INDEX ACT_IDX_DMN_INSTANCE_ID ON ACT_DMN_HI_DECISION_EXECUTION(INSTANCE_ID_);

insert into ACT_GE_PROPERTY
values ('dmn.schema.version', '7.2.0.2', 1);
