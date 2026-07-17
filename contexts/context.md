# 项目上下文

## 项目目标

根据简历中第一个项目“AI 知识库”实现可运行、可演示、可扩展的企业知识运营系统，并落地 Word、PDF、Excel、图片等文件的批量知识导入全流程。

## 已确认的生产组件

- Kafka：导入任务总线，采用 Transactional Outbox 避免数据库提交与消息发送不一致。
- Milvus：生产稠密向量索引与 COSINE/HNSW 检索。
- MinIO：原文件对象存储，SHA-256 内容寻址去重；同时作为单机 Milvus 的对象存储后端。
- PostgreSQL：知识元数据、Chunk 正文、任务状态、Outbox、问答日志和 `pg_trgm` 词法索引。
- OpenAI-compatible Embedding：生产语义向量，优先使用远程服务以减少本机存储。

## 运行边界

- 默认 Profile 保留 H2 + 文件系统 + Hash Embedding + Inline Vector，服务于低成本开发测试。
- `production` Profile 强制启用 PostgreSQL + Kafka + MinIO + Milvus + 语义 Embedding。
- 单机 Compose 用于完整拓扑集成和容量评估，不宣称具备多副本高可用能力。
- 对外生产上线仍需部署级 SSO/RBAC、TLS、解析沙箱、备份恢复和多节点基础设施。

## 开发规范

- 解释、文档和新增注释使用中文。
- Java 21，Spring Boot 4.1，React 19，TypeScript 严格模式。
- 后端变更必须通过 `mvn test`，前端变更必须通过 `pnpm build`。
- 生产 Schema 必须由 Flyway 管理并在真实 PostgreSQL 上校验。
- 不提交构建产物、导入原文件、运行数据库、日志、密钥或个人简历。
