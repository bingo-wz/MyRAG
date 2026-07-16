# 系统架构

## 生产组件关系

```mermaid
flowchart TB
    UI["React 知识运营工作台"] --> API["Spring Boot REST API"]
    API --> KM["KnowledgeService"]
    API --> IM["ImportBatchService"]
    API --> QA["QaService"]
    API --> AN["AnalyticsService"]

    IM --> MINIO[("MinIO")]
    IM --> PG[("PostgreSQL")]
    PG --> OUTBOX["Outbox Relay"]
    OUTBOX --> KAFKA["Kafka"]
    KAFKA --> LEASE["租约 Import Worker"]
    LEASE --> MINIO
    LEASE --> TIKA["Tika / Tesseract"]
    TIKA --> CHUNK["结构化 Token Chunk"]
    CHUNK --> EMB["OpenAI-compatible Embedding"]
    EMB --> MILVUS[("Milvus")]
    CHUNK --> PG

    QA --> RET["Hybrid Retrieval"]
    RET --> EMB
    RET --> MILVUS
    RET --> PG
    AN --> PG
```

## 组件职责与存储边界

| 组件 | 保存内容 | 不保存内容 | 设计原因 |
| --- | --- | --- | --- |
| MinIO | 原始文件，以 SHA-256 内容寻址 | Chunk、向量 | 相同文件物理去重，可独立做生命周期和备份 |
| PostgreSQL | 文档、Chunk 正文、状态、任务、Outbox、问答日志 | 稠密向量 | 强事务和管理查询；`pg_trgm` 提供轻量词法召回 |
| Kafka | 导入批次 ID 事件 | 原文件、解析正文 | 消息小、可重放，不重复保存大内容 |
| Milvus | `chunk_id`、`document_id`、领域、状态、内容哈希、向量 | Chunk 正文 | 避免正文双份存储，结果回 PostgreSQL 补全 |
| etcd | Milvus 元数据 | 业务数据 | 由 Milvus 管理 |

## 领域模型

- `KnowledgeDocument`：知识正文、领域、来源、审核状态、索引版本和源文件哈希。
- `KnowledgeChunk`：Chunk 正文、页码、标题路径、定位符、Token 数、内容哈希和 Embedding 模型。
- `ImportBatch`：批次进度、领取 Worker、租约、下次重试时间和失败原因。
- `ImportFileTask`：对象键、文件哈希、MIME、处理阶段、目标知识 ID 和错误。
- `OutboxEvent`：待投递 Kafka 的事件、投递租约、退避时间和保留状态。
- `QuestionLog`：问题、回答、置信度、延迟、反馈、Bad Case 原因和来源快照。

## 一致性模型

### 上传与消息

1. API 将原文件写入 MinIO，使用内容哈希生成对象键。
2. 同一数据库事务保存 `ImportBatch`、`ImportFileTask` 和 `OutboxEvent`。
3. Outbox Relay 短事务领取事件，事务外发送 Kafka，成功后单独标记 `PUBLISHED`。
4. Relay 崩溃时投递租约过期，事件重新进入 `PENDING`；因此语义是至少一次。

MinIO 写入发生在数据库提交之前。极端回滚可能留下未引用对象，但不会覆盖有效对象；生产环境应定期按数据库引用集合执行对象盘点和延迟清理。

### 消费与任务租约

- Kafka Consumer 用数据库悲观锁领取批次。
- `worker_id + lease_until` 防止同一批次并发处理。
- Worker 每处理一个文件续租；进程崩溃后，兜底扫描器在租约过期后重新领取。
- 批次消息重复消费时，已完成或仍持有有效租约的任务不会再次执行。
- Outbox 已发布记录默认 7 天后删除，Kafka 单机日志默认保留 24 小时。

### PostgreSQL 与 Milvus

- Chunk ID 由 PostgreSQL 生成，Milvus 使用同一 ID 作为主键并执行 Upsert。
- 文档重建索引时先按 `document_id` 删除旧向量，再分批写入新向量。
- 状态流转同步更新 Milvus 的 `status`，Dense 与 Lexical 两路都只召回 `APPROVED`。
- Milvus 成功而数据库事务最终失败时，可能暂时存在孤儿向量；检索补全阶段会因 PostgreSQL 无对应 Chunk 自动丢弃。生产运维需定期执行按 `document_id/index_version` 的索引对账与重建。

## 知识状态机

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PENDING_REVIEW: 提交审核
    REJECTED --> PENDING_REVIEW: 修改后重提
    PENDING_REVIEW --> APPROVED: 审核通过
    PENDING_REVIEW --> REJECTED: 审核驳回
    APPROVED --> OFFLINE: 下线
    OFFLINE --> PENDING_REVIEW: 重新提交
```

只有 `APPROVED` 知识参与问答召回。

## 检索链路

1. 问题调用与入库相同的 Embedding 模型生成向量。
2. Milvus 以 HNSW/COSINE 召回 Dense 候选，并按 `status/domain` 过滤。
3. PostgreSQL 用 `pg_trgm` 从标题和 Chunk 正文召回 Lexical 候选。
4. 合并候选 ID，再从 PostgreSQL 批量读取正文与文档元数据。
5. 使用 `0.78 × semantic + 0.22 × lexical` 重排，应用最小分数和 Top-K。
6. 回答保存引用、置信度、延迟与 Trace ID，负反馈进入 Bad Case。

当前回答层是可运行的抽取式实现。接入真实 LLM 时应增加 Prompt 版本、引用一致性检查、敏感信息过滤、Token 成本、超时与熔断。

## 开发与生产实现映射

| 抽象 | 开发实现 | 生产实现 |
| --- | --- | --- |
| `ObjectStorageService` | 内容寻址文件系统 | MinIO |
| `ImportDispatch` | 数据库兜底扫描 | Kafka Transactional Outbox |
| `EmbeddingService` | Hash Embedding | OpenAI-compatible Embedding |
| `ChunkVectorIndex` | Chunk 内联向量 | Milvus |
| `RetrievalBackend` | Java 内存扫描 | Milvus + PostgreSQL 混合召回 |
| 数据库 | H2 自动建表 | PostgreSQL + Flyway |

## 可观测性

- `myrag.import.files{result=success|failure}`：单文件导入结果计数。
- `myrag.import.file.duration`：解析到索引的文件耗时。
- `myrag.embedding.request.duration{model=...}`：Embedding 请求耗时。
- `myrag.embedding.requests{result=failure}`：Embedding 请求失败数。
- `/actuator/health`：基础健康状态。
- `/actuator/health/readiness`：生产就绪状态；Hash Embedding 会使生产就绪检查失败。
- `/actuator/prometheus`：Prometheus 拉取入口。

建议告警：Kafka 消费延迟、Outbox PENDING 数、租约超时数、导入失败率、Embedding P95、Milvus 搜索 P95、PostgreSQL 连接池和磁盘使用率。
