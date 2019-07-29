
UPDATE ACT_CMMN_DATABASECHANGELOGLOCK SET LOCKED = 1, LOCKEDBY = '192.168.1.5 (192.168.1.5)', LOCKGRANTED = TIMESTAMP('2019-03-13 21:58:17.195') WHERE ID = 1 AND LOCKED = 0;

ALTER TABLE ACT_CMMN_RU_PLAN_ITEM_INST ADD IS_COMPLETEABLE_ SMALLINT;

CALL SYSPROC.ADMIN_CMD ('REORG TABLE ACT_CMMN_RU_PLAN_ITEM_INST');

ALTER TABLE ACT_CMMN_RU_CASE_INST ADD IS_COMPLETEABLE_ SMALLINT;

CALL SYSPROC.ADMIN_CMD ('REORG TABLE ACT_CMMN_RU_CASE_INST');

CREATE INDEX ACT_IDX_PLAN_ITEM_STAGE_INST ON ACT_CMMN_RU_PLAN_ITEM_INST(STAGE_INST_ID_);

ALTER TABLE ACT_CMMN_RU_PLAN_ITEM_INST ADD IS_COUNT_ENABLED_ SMALLINT;

CALL SYSPROC.ADMIN_CMD ('REORG TABLE ACT_CMMN_RU_PLAN_ITEM_INST');

ALTER TABLE ACT_CMMN_RU_PLAN_ITEM_INST ADD VAR_COUNT_ INTEGER;

CALL SYSPROC.ADMIN_CMD ('REORG TABLE ACT_CMMN_RU_PLAN_ITEM_INST');

ALTER TABLE ACT_CMMN_RU_PLAN_ITEM_INST ADD SENTRY_PART_INST_COUNT_ INTEGER;

CALL SYSPROC.ADMIN_CMD ('REORG TABLE ACT_CMMN_RU_PLAN_ITEM_INST');

INSERT INTO ACT_CMMN_DATABASECHANGELOG (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('3', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', CURRENT TIMESTAMP, 3, '7:1c0c14847bb4a891aaf91668d14240c1', 'addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_RU_CASE_INST; createIndex indexName=ACT_IDX_PLAN_ITEM_STAGE_INST, tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST; addColumn tableNam...', '', 'EXECUTED', NULL, NULL, '3.5.3', '2510697846');

UPDATE ACT_CMMN_DATABASECHANGELOGLOCK SET LOCKED = 0, LOCKEDBY = NULL, LOCKGRANTED = NULL WHERE ID = 1;
