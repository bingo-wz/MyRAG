# 身份、权限与文件安全

## OIDC 登录与 JWT 校验

生产前端使用 OIDC Authorization Code + PKCE，浏览器只保存 OIDC Client 维护的用户会话，不保存 Client Secret。Nginx 启动时把公开的 Authority、Client ID 和 Scope 写入 `config.js`；所有 `fetch` 和 CSV 下载自动附加 `Authorization: Bearer <token>`。

后端是无状态 OAuth2 Resource Server，通过 `issuer-uri` 验证签名、签发方、有效期，并通过 `audiences` 验证目标受众。生产 Token 至少需要：

```json
{
  "aud": ["myrag-api"],
  "preferred_username": "zhangsan",
  "roles": ["KNOWLEDGE_OPERATOR"],
  "domains": ["售后服务", "产品知识"]
}
```

`roles` 会转换为 Spring Security 的 `ROLE_*`；`domains` 支持字符串数组或逗号分隔字符串。`ADMIN` 可跨领域，其他角色查询时必须指定领域，且只能访问 Token 声明的领域。`domains: ["*"]` 只应授予受控的跨领域服务账号。

## 角色矩阵

| 能力 | `ADMIN` | `KNOWLEDGE_OPERATOR` | `REVIEWER` | `USER` |
| --- | --- | --- | --- | --- |
| 知识查询 / RAG 问答 | 全领域 | 授权领域 | 授权领域 | 授权领域 |
| 新建、修改、下线知识 | 是 | 是 | 否 | 否 |
| 批量导入、重试、提交 | 是 | 是 | 否 | 否 |
| 审核知识 | 是 | 否 | 是 | 否 |
| 质量看板 / Bad Case | 是 | 否 | 是 | 否 |
| 导出全部知识 | 是 | 否 | 否 | 否 |
| Milvus 手工对账 | 是 | 否 | 否 | 否 |

用户提交的 `createdBy`、`reviewer` 等身份字段在生产模式不会被信任，服务端统一使用 JWT Principal。

## 审计

所有 `/api/**` 下的 `POST`、`PUT`、`PATCH`、`DELETE` 请求都会记录操作者、方法、路径、响应状态、来源地址、User-Agent 和时间。审计表不记录请求体、Authorization Header、文件内容或模型密钥，避免二次泄露。

生产应把审计表同步到不可变日志平台，设置独立保留周期和访问角色。应用数据库管理员仍可能修改本地审计表，因此本表不是最终的防抵赖存储。

## 文件安全

导入 Worker 在解析前通过 clamd `INSTREAM` 协议流式扫描对象：

- `FOUND`：文件立即失败，不进入 Tika/Tesseract。
- ClamAV 超时、断开或异常响应：失败关闭，不绕过扫描。
- 文件超过扫描上限：立即失败。
- `FILE_SCAN_PROVIDER=noop`：仅用于开发或拓扑验证，readiness 必定失败。

readiness 会对 clamd 执行 `PING/PONG` 连通性探测；只配置 `clamav` 但扫描服务不可达时同样保持 `OUT_OF_SERVICE`。

解析运行在独立 Worker 进程和可取消任务中，默认 120 秒超时。正式集群还应启用只读根文件系统、非 root、seccomp/AppArmor、出站白名单、临时目录配额和 Pod 级 CPU/内存限制。

## 上线核对

- IdP 只允许已登记的 HTTPS Redirect URI，SPA Client 不配置 Secret。
- 网关和 MinIO 全部使用 TLS，密钥进入 Secret Manager，不写入 `.env` 或 Git。
- 用 `USER`、`REVIEWER`、`KNOWLEDGE_OPERATOR`、`ADMIN` 四类 Token 做正反向权限回归。
- 用未授权 `domains` 验证列表、详情、问答、导入批次均返回 403。
- 用 EICAR 测试文件验证 ClamAV；停止 ClamAV 后确认导入失败而非跳过。
- 验证审计日志不包含 Token、请求正文和知识正文。
