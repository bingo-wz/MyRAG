# MyRAG

MyRAG 是一个面向企业知识运营的 AI 知识库，依据简历中“AI 知识库”项目经历实现。系统覆盖多格式批量导入、结构化解析、知识审核、混合检索、RAG 问答、质量指标和 Bad Case 追溯。

仓库同时提供两种运行形态：

- 开发演示：H2、本地内容寻址存储、Hash Embedding、进程内向量检索，零外部服务即可运行。
- 生产拓扑：PostgreSQL、Kafka、MinIO、Milvus、OpenAI-compatible Embedding，使用 Outbox、任务租约和可恢复 Worker。

## 已实现能力

- 知识状态机：草稿、提交审核、通过、驳回、下线、重新提交和乐观锁版本控制。
- 批量导入：Word、PDF、Excel、PowerPoint、CSV、TXT、Markdown、RTF 和常见图片。
- OCR：Docker 镜像内置 Tesseract `chi_sim+eng`，支持图片和扫描版 PDF。
- 结构化解析：保留标题层级、段落、列表、表格行、页码与定位信息。
- Token 切片：目标 480 Token、60 Token 重叠，超长块拆分，Chunk 内容哈希稳定。
- 可靠任务：导入批次与 Outbox 同事务写入，Kafka 至少一次投递，数据库租约、心跳、退避重试和兜底扫描。
- 轻量存储：MinIO 原文件按 SHA-256 内容寻址去重；Milvus 只保存向量和过滤字段，不重复保存正文。
- 混合召回：Milvus HNSW/COSINE 稠密召回 + PostgreSQL `pg_trgm` 词法召回 + Java 重排。
- 质量闭环：回答置信度、时延、采纳率、Bad Case 原因和来源快照。
- 可观测性：Actuator、Prometheus 指标、生产就绪检查和受限容器日志。

## 生产数据流

```mermaid
flowchart LR
    UI["React 运营台"] --> API["Spring Boot API"]
    API --> MINIO[("MinIO 原文件")]
    API --> PG[("PostgreSQL 元数据 + 正文")]
    PG --> OUTBOX["Transactional Outbox"]
    OUTBOX --> KAFKA["Kafka"]
    KAFKA --> WORKER["租约 Import Worker"]
    WORKER --> TIKA["Tika + Tesseract"]
    TIKA --> CHUNK["结构化 Token 切片"]
    CHUNK --> EMB["远程 Embedding"]
    EMB --> MILVUS[("Milvus 向量索引")]
    EMB --> PG
    API --> HYBRID["Dense + Lexical 混合检索"]
    HYBRID --> MILVUS
    HYBRID --> PG
```

## 技术栈

- 后端：Java 21、Spring Boot 4.1、Spring Data JPA、Flyway、Micrometer
- 任务：Kafka、Transactional Outbox、数据库租约 Worker
- 存储：PostgreSQL 17、MinIO、Milvus 2.6、etcd
- 解析：Apache Tika 3.3、PDFBox、Apache POI、Tesseract OCR
- 前端：React 19、TypeScript、Vite、Nginx
- 交付：Docker Compose、Maven、pnpm

## 快速启动

### 零依赖开发演示

```bash
docker compose up --build -d
```

访问 [http://localhost:3000](http://localhost:3000)。此模式用于功能演示，不使用 Kafka、Milvus、MinIO。

### Kafka + Milvus + MinIO 生产拓扑

先配置远程 OpenAI-compatible Embedding 服务。为了节省本机磁盘，生产 Compose 不下载本地模型。

```bash
cp .env.example .env
# 修改 .env 中的密码和 EMBEDDING_BASE_URL
docker compose --env-file .env -f docker-compose.production.yml up --build -d
docker compose --env-file .env -f docker-compose.production.yml ps
curl http://localhost:3000/api/actuator/health
```

服务入口：

- 管理端：[http://localhost:3000](http://localhost:3000)
- MinIO Console：[http://localhost:9001](http://localhost:9001)
- Milvus：`localhost:19530`

`docker-compose.production.yml` 是资源受限电脑上的单节点生产拓扑验证基线，不等同于高可用生产集群。对外上线前应改用多节点 Kafka、Milvus 和受支持的 MinIO 部署，并补齐 SSO/RBAC、TLS、病毒扫描、解析沙箱与审计。

## 本地开发与验证

```bash
# 终端 1，需要 JDK 21
make dev-backend

# 终端 2，需要 Node.js 22 与 pnpm
make dev-frontend

# 后端测试 + 前端严格构建
make test
```

本机若默认仍是 JDK 8，请显式设置：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="/opt/homebrew/opt/maven/bin:$JAVA_HOME/bin:$PATH"
```

## 生产轻量化策略

- 原文件以 SHA-256 为对象键，相同内容只在 MinIO 保存一份。
- Milvus 不存 Chunk 正文；正文和元数据仅保存在 PostgreSQL。
- Kafka 单机验证环境保留 24 小时、最大约 512 MB 日志。
- Outbox 已发布事件默认保留 7 天并自动清理。
- Docker 日志单容器最多 `3 × 10 MB`。
- Embedding 使用远程 API，避免本机模型权重与推理运行时占用磁盘。
- PostgreSQL 用 Flyway 管理 Schema，Milvus 使用 HNSW + mmap，索引可按文档重建。

详细部署和容量建议见 [生产部署](docs/PRODUCTION_DEPLOYMENT.md)，当前电脑评估见 [环境评估](docs/ENVIRONMENT_ASSESSMENT.md)。

## 核心 API

| 模块 | 方法 | 接口 | 说明 |
| --- | --- | --- | --- |
| 知识 | GET | `/api/knowledge` | 搜索和分页 |
| 知识 | POST | `/api/knowledge` | 新建草稿并索引 |
| 审核 | POST | `/api/knowledge/{id}/submit` | 提交审核 |
| 审核 | POST | `/api/knowledge/{id}/review` | 通过或驳回 |
| 导入 | POST | `/api/imports` | 创建多文件导入批次 |
| 导入 | GET | `/api/imports/{batchId}` | 查询逐文件进度 |
| 导入 | POST | `/api/imports/{batchId}/retry` | 重试失败文件 |
| 导入 | POST | `/api/imports/{batchId}/submit` | 批量提交审核 |
| 问答 | POST | `/api/qa/ask` | 执行 RAG 问答 |
| 反馈 | POST | `/api/qa/{traceId}/feedback` | 采纳或标记 Bad Case |
| 分析 | GET | `/api/analytics/overview` | 质量看板指标 |

## 文档

- [系统架构](docs/ARCHITECTURE.md)
- [批量导入全流程](docs/IMPORT_PIPELINE.md)
- [生产部署与运维](docs/PRODUCTION_DEPLOYMENT.md)
- [当前电脑环境评估](docs/ENVIRONMENT_ASSESSMENT.md)

## 许可

[MIT](LICENSE)
