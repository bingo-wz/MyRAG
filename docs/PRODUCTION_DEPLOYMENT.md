# 生产部署与运维

## 定位

`docker-compose.production.yml` 用于在资源有限的开发机上验证 Kafka、Milvus、MinIO、PostgreSQL 和完整应用链路。它具备生产组件和可靠性机制，但所有基础设施都是单节点，因此不是高可用集群。

## 前置条件

- Docker Desktop 已启动。
- 建议 Docker 分配至少 6 GB 内存；Milvus 构建较大索引时建议 8 GB 以上。
- Docker Disk image 上限至少 32 GB，并保持 20% 以上空闲。
- 已创建 SiliconFlow API Key；Chat 与 Embedding 默认共用该 Key。
- 已创建 Authing 免费版自建 SPA 应用，并启用 Authorization Code + PKCE。
- 首次拉取镜像前确认有足够磁盘，避免在构建中途耗尽空间。

## 配置

```bash
cp .env.example .env
```

必须修改：

- `DATABASE_PASSWORD`
- `MINIO_SECRET_KEY`
- `SILICONFLOW_API_KEY`
- `OAUTH2_ISSUER_URI`、`OAUTH2_AUDIENCE`
- `OIDC_BROWSER_AUTHORITY`、`OIDC_CLIENT_ID`、`OIDC_SCOPES`、`OIDC_API_TOKEN_SOURCE`
- `OIDC_PRINCIPAL_CLAIM`、`OIDC_ROLES_CLAIM`、`OIDC_DOMAINS_CLAIM`（若 Authing 中采用不同名称）

默认模型配置已经指向 SiliconFlow：

| 配置 | 默认值 |
| --- | --- |
| `EMBEDDING_BASE_URL` | `https://api.siliconflow.cn/v1` |
| `EMBEDDING_MODEL` | `BAAI/bge-m3` |
| `EMBEDDING_DIMENSIONS` | `1024` |
| `CHAT_BASE_URL` | `https://api.siliconflow.cn/v1` |
| `CHAT_MODEL` | `Qwen/Qwen3-8B` |
| `CHAT_ENABLE_THINKING` | `false` |

两类服务通过 OpenAI-compatible 接口接入，可分别用 `EMBEDDING_API_KEY`、`CHAT_API_KEY` 覆盖共用 Key。RAG 默认关闭 Qwen3 思考模式，以缩短时延并把输出预算留给带引用的最终回答。免费模型适合开发和小流量演示，但供应商可能调整价格、限流和模型清单；上线前应调用 `/v1/models` 核对可用性并做限流降级演练。

Authing Issuer 与浏览器 Authority 都使用 `https://<应用域名>.authing.cn/oidc`。SPA Client 必须允许回调 `https://<MyRAG域名>/auth/callback` 和登出回跳 `https://<MyRAG域名>/`，启用 Authorization Code + PKCE，禁止 Implicit Flow，并使用 RS256 签发 ID Token。Authing 默认配置 `OIDC_API_TOKEN_SOURCE=id_token`，`OAUTH2_AUDIENCE` 使用 App ID；如切换到能在 Access Token 中提供业务 Claim 的 IdP，则改为 `access_token` 并使用实际 Access Token Audience。详见 [安全配置](SECURITY.md)。

Apple Silicon 在中国大陆网络下载依赖较慢时，可在 `.env` 设置 `UBUNTU_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports` 和 `MAVEN_MIRROR=https://maven.aliyun.com/repository/public`。两个参数只影响后端镜像构建，默认仍使用官方源；Dockerfile 使用 BuildKit Maven 缓存，重复构建不会反复下载全部依赖。

单机 Kafka 的 `KAFKA_REPLICATION_FACTOR=1` 仅用于本机；连接三节点以上生产集群时应设置为 3。Embedding 维度改变时必须使用新的 Milvus Collection 名称或重建旧 Collection。

## 启停与检查

```bash
docker compose --env-file .env -f docker-compose.production.yml config --quiet
docker compose --env-file .env -f docker-compose.production.yml up --build -d
docker compose --env-file .env -f docker-compose.production.yml ps
docker compose --env-file .env -f docker-compose.production.yml logs -f backend
docker compose --env-file .env -f docker-compose.production.yml logs -f worker
```

健康检查：

```bash
curl http://localhost:3000/actuator/health/liveness
curl http://localhost:3000/actuator/health/readiness
curl http://localhost:3000/actuator/prometheus
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
| API 内存上限 | 640 MB | JVM 最大 384 MB，不执行文件解析 |
| Worker 内存上限 | 704 MB | JVM 最大 448 MB，单并发解析 |
| Frontend 内存上限 | 96 MB | Nginx 与静态资源 |
| Kafka 日志 | 24 小时 / 512 MB | 避免本机消息日志持续增长 |
| 容器日志 | 每容器 3 × 10 MB | 限制 Docker JSON 日志 |
| Outbox | 7 天 | 每日清理已发布事件 |

主拓扑内存上限合计约 5.8 GB，适合当前电脑的小批次集成验证。生产吞吐提高后，应根据 Chunk 数、并发解析数和检索 QPS 单独压测，不应继续沿用这些上限。

## API 与 Worker 拆分

`backend` 和 `worker` 使用同一镜像、不同运行参数：

- API：`API_ENABLED=true`、`APP_IMPORT_WORKER_ENABLED=false`，提供 REST、Outbox Relay、问答和索引对账。
- Worker：`API_ENABLED=false`、`APP_IMPORT_WORKER_ENABLED=true`、`SPRING_MAIN_WEB_APPLICATION_TYPE=none`，消费 Kafka、解析、切片和向量化，不开放端口。

扩容时 API 与 Worker 应分别设置副本数。Worker 的 Kafka Consumer Group 和数据库租约共同保证重复消息不会并发处理同一批次；不要让 API 同时开启 Worker。

## Milvus 自动对账

- 默认每天 03:30 逐页比较 PostgreSQL 与 Milvus。
- 自动重建缺失或内容哈希漂移的向量，修复状态并删除孤儿向量。
- 管理员可 `POST /api/admin/index/reconcile` 手工触发，`GET` 查询最近报告。
- 大库执行前需评估 Embedding 费用和 Milvus I/O，可用 `VECTOR_RECONCILIATION_ENABLED=false` 暂停定时任务并在维护窗口手工执行。

## MinIO 版本说明

Compose 固定了可复现的 MinIO Community 镜像用于本地集成。MinIO Community 的发行和支持策略已发生变化，真正生产环境应选择组织已评审且有安全更新来源的 MinIO/AIStor 版本，或维护内部构建与补丁流程，不能长期依赖未更新的历史镜像。

应用只依赖标准 MinIO Java API，替换服务端版本不需要修改业务代码。

Milvus 已启用用户认证，Compose 仅把 Milvus 和 MinIO Console 端口绑定到本机回环地址。首次启动使用 Milvus 默认管理员凭据完成初始化后，应立即创建最小权限应用用户、修改默认 `root` 密码，并同步更新 `MILVUS_TOKEN`；跨主机访问必须再配置 TLS 或由受控网关终止 TLS。

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
5. API 与 Import Worker 分别部署和伸缩；解析 Worker 只允许访问 MinIO、Kafka、PostgreSQL、Milvus 和模型网关。
6. 接入 Prometheus/Grafana、集中日志、Trace、告警和值班流程。
7. 网关启用 TLS、请求限流和 WAF；应用层继续执行 JWT/RBAC、领域隔离和审计，不能只依赖网关。

## 上线门禁

- `mvn test` 与 `pnpm build` 全部通过。
- Flyway 在与生产相同大版本的 PostgreSQL 空库和升级库上验证。
- 真实 Word/PDF/Excel/图片样本回归，包含扫描件、加密件、损坏件和超限件。
- Kafka 重复消息、Worker 崩溃、Embedding 超时、Milvus 重启演练通过。
- OIDC 登录、Token 过期、角色矩阵、领域越权和审计查询演练通过。
- 解析超时、伪造扩展名、加密件、损坏件和压缩炸弹演练通过。
- 压测给出单文件 P95、导入吞吐、检索 P95、峰值内存和磁盘增长率。
