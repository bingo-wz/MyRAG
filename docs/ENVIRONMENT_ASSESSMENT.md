# 当前电脑环境评估

检查日期：2026-07-17。

## 检查结果

| 项目 | 当前情况 | 结论 |
| --- | --- | --- |
| 设备 | MacBook Air，Apple M4，10 核 CPU、8 核 GPU | Java/React 开发和单机集成足够 |
| 内存 | 16 GB | 可运行轻量完整拓扑；不适合模拟多节点生产集群 |
| 可用磁盘 | 拉取完整生产镜像后约 33 GB | 能进行单节点集成，但需限制镜像、Kafka、日志和模型占用 |
| Docker Desktop | 已安装并完成实际启动验证 | 可运行 Compose，建议 6 GB 最低、8 GB 更稳 |
| Docker 配置 | 2 CPU、6 GB RAM、32 GB Disk、512 MB Swap | CPU 和内存勉强满足轻量基线，Milvus 大索引会受限 |
| Java | JDK 21 已安装；系统默认 `JAVA_HOME` 仍指向 JDK 8 | 项目必须显式切到 JDK 21 |
| Maven | 3.9.12 已安装；默认 PATH 曾命中 3.5.4 | 应把 Homebrew Maven 放到 PATH 前部 |
| Node.js / pnpm | Node.js 22、pnpm 10 | 满足前端构建 |
| Python | 3.11 与 3.14 | 可用于运维脚本，不是主应用依赖 |
| PostgreSQL | 17 已安装 | 已用临时实例验证 Flyway 与 Hibernate Schema |
| Tesseract | Host 未安装 | Docker 后端镜像已经内置，不要求 Host 安装 |
| Kafka / MinIO / Milvus | Host 未安装 | 统一通过 Docker Compose，避免污染 Host |
| 本地 AI 模型 | 未安装 | 生产 Profile 采用远程 Embedding 与 Chat Completion，节约磁盘 |
| OIDC / ClamAV | Host 未安装 | OIDC 使用外部 IdP；ClamAV 建议外部部署，避免挤占 6 GB Docker 内存 |

## 已采用的适配方案

- 不安装本地 Milvus/Kafka/MinIO 二进制，全部使用容器并固定镜像。
- 不下载 BGE-M3 等本地模型权重，改为 OpenAI-compatible 远程 Embedding。
- MinIO 内容寻址去重，同一原文件不重复占空间。
- Milvus 只存向量和筛选字段，正文不重复存储。
- Kafka 日志、Outbox 和 Docker JSON 日志都有保留上限。
- API 与 Worker 已拆成独立进程，主拓扑容器内存上限合计约 5.8 GB。
- ClamAV 官方建议约 4 GB RAM，因此不计入本机默认主拓扑；本机应连接外部 ClamAV。
- 保留零依赖开发 Profile，日常改 UI/业务逻辑时无需启动完整基础设施。

## 实机联调结果

已在本机实际启动 PostgreSQL 17、Kafka 4.1、MinIO、etcd 和 Milvus 2.6，并完成以下完整链路：

1. 上传 `README.md`，MinIO 按 SHA-256 内容寻址落盘。
2. PostgreSQL Outbox 发布导入事件，Kafka Consumer 成功消费。
3. Tika 结构化解析出正文并按 Token 预算生成 5 个 Chunk。
4. OpenAI-compatible Embedding 批量向量化，Milvus HNSW Collection 成功写入。
5. 知识提交、审核后，Milvus 状态字段局部更新成功。
6. 通过 Milvus + PostgreSQL 混合检索完成 RAG 问答，返回来源和置信度。
7. 重复上传同一文件时 `storageDeduplicated=true`，确认 MinIO 未重复存储对象。

历史联调中，五个基础设施容器空载约 0.9 GB；本次生产改造后增加独立 API/Worker JVM，预计空载约 1.8～2.2 GB。配置的 5.8 GB 是故障隔离上限，不代表常驻占用；较大 PDF、OCR 和索引构建仍需逐步压测。

本次改造还完成了以下实机验证：

- Java 21 下执行 `mvn clean verify`，21 个测试全部通过；Testcontainers 在 PostgreSQL 17.10 上真实执行 Flyway V1 到 V2，并覆盖 PostgreSQL 动态知识搜索，未跳过测试。
- 前端 TypeScript 检查与 Vite 生产构建通过，产物 JavaScript gzip 后约 93 KB。
- 生产 Compose 主配置与 `security` Profile 均通过解析校验。
- 后端镜像以 UID/GID `10001`、只读根文件系统、`cap_drop=ALL` 和 640 MB 上限启动，健康检查与知识 API 均正常。
- 当前后端镜像约 286 MB、前端镜像约 26 MB；后端演示数据启动后实测内存约 419 MiB。
- PostgreSQL、Kafka、MinIO、etcd、Milvus 五个基础设施容器全部达到 `healthy`；Milvus 无凭据访问返回 401，应用凭据可正常建集合和执行混合检索问答。

## 推荐的本机使用方式

日常开发只启动默认 Compose：

```bash
docker compose up -d
```

验证生产链路时再启动主拓扑，并连接远程模型、OIDC 和外部 ClamAV：

```bash
docker compose down
docker compose --env-file .env -f docker-compose.production.yml up -d
```

不要同时运行两套 Compose。运行完整拓扑时关闭大型 IDE 工程、浏览器重负载页面和其他数据库服务，避免 16 GB 统一内存压力。

## 容量结论

这台电脑可以完成：

- 生产组件连通性验证
- 小到中等批次的 Word/PDF/Excel/图片导入
- Kafka 故障恢复、Milvus 检索和 MinIO 去重验证
- 前后端开发、测试和面试演示
- OIDC/JWT 权限、ClamAV 协议、API/Worker 隔离和索引对账功能验证

这台电脑不适合承担：

- 千万级 Chunk 的全量索引
- 多副本 Kafka/Milvus/MinIO 高可用模拟
- 本地大模型与完整基础设施同时推理
- 本地 ClamAV 4 GB Profile 与 6 GB 主拓扑同时运行
- 长时间生产流量或高并发压测

真正的千万级生产应使用独立服务器或云服务；本机只作为开发与单节点集成环境。
