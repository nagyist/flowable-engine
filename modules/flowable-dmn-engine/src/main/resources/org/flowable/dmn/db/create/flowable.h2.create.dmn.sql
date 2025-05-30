CREATE TABLE ACT_DMN_DEPLOYMENT (ID_ VARCHAR(255) NOT NULL, NAME_ VARCHAR(255), CATEGORY_ VARCHAR(255), DEPLOY_TIME_ TIMESTAMP, TENANT_ID_ VARCHAR(255), PARENT_DEPLOYMENT_ID_ VARCHAR(255), CONSTRAINT PK_ACT_DMN_DEPLOYMENT PRIMARY KEY (ID_));

CREATE TABLE ACT_DMN_DEPLOYMENT_RESOURCE (ID_ VARCHAR(255) NOT NULL, NAME_ VARCHAR(255), DEPLOYMENT_ID_ VARCHAR(255), RESOURCE_BYTES_ BLOB, CONSTRAINT PK_ACT_DMN_DEPLOYMENT_RESOURCE PRIMARY KEY (ID_));

CREATE TABLE ACT_DMN_DECISION (ID_ VARCHAR(255) NOT NULL, NAME_ VARCHAR(255), VERSION_ INT, KEY_ VARCHAR(255), CATEGORY_ VARCHAR(255), DECISION_TYPE_ VARCHAR(255), DEPLOYMENT_ID_ VARCHAR(255), TENANT_ID_ VARCHAR(255), RESOURCE_NAME_ VARCHAR(255), DESCRIPTION_ VARCHAR(255), CONSTRAINT PK_ACT_DMN_DECISION_TABLE PRIMARY KEY (ID_));

CREATE TABLE ACT_DMN_HI_DECISION_EXECUTION (ID_ VARCHAR(255) NOT NULL, DECISION_DEFINITION_ID_ VARCHAR(255), DEPLOYMENT_ID_ VARCHAR(255), START_TIME_ TIMESTAMP, END_TIME_ TIMESTAMP, INSTANCE_ID_ VARCHAR(255), EXECUTION_ID_ VARCHAR(255), ACTIVITY_ID_ VARCHAR(255), SCOPE_TYPE_ VARCHAR(255), FAILED_ BOOLEAN DEFAULT FALSE, TENANT_ID_ VARCHAR(255), EXECUTION_JSON_ CLOB, CONSTRAINT PK_ACT_DMN_HI_DECISION_EXECUTION PRIMARY KEY (ID_));

ALTER TABLE ACT_DMN_DEPLOYMENT_RESOURCE
    ADD CONSTRAINT ACT_FK_DMN_RSRC_DPL FOREIGN KEY (DEPLOYMENT_ID_) REFERENCES ACT_DMN_DEPLOYMENT (ID_);

CREATE INDEX ACT_IDX_DMN_RSRC_DPL ON ACT_DMN_DEPLOYMENT_RESOURCE (DEPLOYMENT_ID_);

CREATE UNIQUE INDEX ACT_IDX_DMN_DEC_UNIQ ON ACT_DMN_DECISION(KEY_, VERSION_, TENANT_ID_);

CREATE INDEX ACT_IDX_DMN_INSTANCE_ID ON ACT_DMN_HI_DECISION_EXECUTION(INSTANCE_ID_);

insert into ACT_GE_PROPERTY
values ('dmn.schema.version', '7.2.0.2', 1);
