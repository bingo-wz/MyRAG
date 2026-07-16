# 生产部署与运维

## 定位

`docker-compose.production.yml` 用于在资源有限的开发机上验证 Kafka、Milvus、MinIO、PostgreSQL 和完整应用链路。它具备生产组件和可靠性机制，但所有基础设施都是单节点，因此不是高可用集群。

## 前置条件

- Docker Desktop 已启动。
- 建议 Docker 分配至少 6 GB 内存；Milvus 构建较大索引时建议 8 GB 以上。
- Docker Disk image 上限至少 32 GB，并保持 20% 以上空闲。
- 提供远程 OpenAI-compatible Embedding 服务，`/embeddings` 返回固定维度向量。
- 首次拉取镜像前确认有足够磁盘，避免在构建中途耗尽空间。

## 配置

```bash
cp .env.example .env
```

必须修改：

- `DATABASE_PASSWORD`
- `MINIO_SECRET_KEY`
- `EMBEDDING_BASE_URL`
- `EMBEDDING_MODEL`
- `EMBEDDING_DIMENSIONS`

`EMBEDDING_BASE_URL` 应包含 OpenAI-compatible API 前缀。例如服务端端点为 `/v1/embeddings`，则配置为 `https://embedding.example.com/v1`。

Apple Silicon 在中国大陆网络下载依赖较慢时，可在 `.env` 设置 `UBUNTU_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports` 和 `MAVEN_MIRROR=https://maven.aliyun.com/repository/public`。两个参数只影响后端镜像构建，默认仍使用官方源；Dockerfile 使用 BuildKit Maven 缓存，重复构建不会反复下载全部依赖。

单机 Kafka 的 `KAFKA_REPLICATION_FACTOR=1` 仅用于本机；连接三节点以上生产集群时应设置为 3。Embedding 维度改变时必须使用新的 Milvus Collection 名称或重建旧 Collection。

## 启停与检查

```bash
docker compose --env-file .env -f docker-compose.production.yml config --quiet
docker compose --env-file .env -f docker-compose.production.yml up --build -d
docker compose --env-file .env -f docker-compose.production.yml ps
docker compose --env-file .env -f docker-compose.production.yml logs -f backend
```

健康检查：

```bash
curl http://localhost:3000/api/actuator/health
curl http://localhost:3000/api/actuator/health/readiness
curl http://localhost:3000/api/actuator/prometheus
```

停止但保留数据：

```bash
docker compose --env-file .env -f docker-compose.production.yml down
```

不要在未备份时执行 `down -v`，它会删除 PostgreSQL、MinIO、Milvus、etcd 和 Kafka Volume。

## 当前轻量化参数

| 项目 | 单机默认 | 作用 |
| --- | --- | --- |
| PostgreSQL 内存上限 | 512 MB | 元数据与词法索引验证 |
| MinIO 内存上限 | 384 MB | 原文件与 Milvus 对象存储 |
| etcd 内存上限 | 256 MB | Milvus 元数据 |
| Milvus 内存上限 | 2.5 GB | 单机向量检索 |
| Kafka 内存上限 | 768 MB | KRaft 单节点，JVM 最大 512 MB |
| Backend 内存上限 | 768 MB | JVM 最大 512 MB |
| Kafka 日志 | 24 小时 / 512 MB | 避免本机消息日志持续增长 |
| 容器日志 | 每容器 3 × 10 MB | 限制 Docker JSON 日志 |
| Outbox | 7 天 | 每日清理已发布事件 |

内存上限适合小批次集成验证。生产吞吐提高后，应根据 Chunk 数、并发解析数和检索 QPS 单独压测，不应继续沿用这些上限。

## MinIO 版本说明

Compose 固定了可复现的 MinIO Community 镜像用于本地集成。MinIO Community 的发行和支持策略已发生变化，真正生产环境应选择组织已评审且有安全更新来源的 MinIO/AIStor 版本，或维护内部构建与补丁流程，不能长期依赖未更新的历史镜像。

应用只依赖标准 MinIO Java API，替换服务端版本不需要修改业务代码。

## 备份

- PostgreSQL：每日逻辑备份 + 周期性物理备份，必须包含 `flyway_schema_history`。
- MinIO：对象版本控制或跨站复制；应用原文件 Bucket 和 Milvus Bucket 都要纳入策略。
- Milvus：向量可由 PostgreSQL Chunk + Embedding 模型重建，但大规模重算成本高，仍建议使用官方备份工具。
- Kafka：导入事件属于短期任务消息，不作为业务事实唯一副本；事实状态保存在 PostgreSQL。
- 配置：备份 Collection 名称、Embedding 模型版本、维度和 Chunk 参数。

至少每季度做一次恢复演练，并验证 PostgreSQL 文档数、Chunk 数和 Milvus向量数对账。

## 扩展到真实高可用生产

1. PostgreSQL 使用主备或云数据库，连接池前置 PgBouncer。
2. Kafka 至少 3 Broker、3 Controller，Topic 副本 3，设置最小同步副本。
3. Milvus 使用 Cluster 模式，对象存储和 etcd 独立高可用。
4. MinIO 使用受支持的分布式部署、TLS、KMS 和最小权限 Service Account。
5. API 与 Import Worker 拆为独立 Deployment；解析 Worker 禁止访问公网并设置硬资源限制。
6. 接入 Prometheus/Grafana、集中日志、Trace、告警和值班流程。
7. 网关启用 SSO/RBAC、请求限流、上传鉴权和审计。

## 上线门禁

- `mvn test` 与 `pnpm build` 全部通过。
- Flyway 在与生产相同大版本的 PostgreSQL 空库和升级库上验证。
- 真实 Word/PDF/Excel/图片样本回归，包含扫描件、加密件、损坏件和超限件。
- Kafka 重复消息、Worker 崩溃、Embedding 超时、Milvus 重启演练通过。
- 完成病毒扫描、解析沙箱、权限模型、TLS 和备份恢复。
- 压测给出单文件 P95、导入吞吐、检索 P95、峰值内存和磁盘增长率。
