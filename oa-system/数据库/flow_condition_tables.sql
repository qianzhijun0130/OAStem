-- 流程模板分支条件配置（阶段三：条件网关）
-- 设计原则：
--   1. 不新建表，在 oa_flow_definition 上增加 flow_config 列（LONGTEXT），
--      与 node_config「一列一职责」的风格一致：node_config 回答「谁审批」，
--      flow_config 回答「网关往哪条路走」。两者都是 JSON 字符串，前端保存时整体回传。
--   2. flow_config 是「结构化条件」的唯一事实源；部署时由 BpmnXmlNormalizer#injectConditions
--      把它注入 BPMN 的 <conditionExpression>，Flowable 运行期只认注入后的表达式。
--      这样画布重新打开时条件可回显（读 JSON），不必从 JUEL 文本逆向解析。
--   3. 历史模板该列为 NULL = 没有任何可视化条件，注入器直接跳过，行为与升级前完全一致。
USE oa_system;

ALTER TABLE oa_flow_definition
  ADD COLUMN flow_config LONGTEXT NULL COMMENT '网关分支条件配置 JSON：[{flowId,gatewayId,gatewayName,targetNodeId,targetNodeName,logic,and|or,isDefault,conditions:[{field,op,valueType,number|string,value}]}]'
  AFTER node_config;
