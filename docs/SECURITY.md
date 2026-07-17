# 身份、权限与文件防护

## Authing OIDC 登录与 JWT 校验

生产身份服务选择 Authing 免费版。前端使用 OIDC Authorization Code + PKCE，浏览器只保存 OIDC Client 维护的用户会话，不保存 Client Secret。Nginx 启动时把公开的 Authority、Client ID、Scope、API Token 来源和 Claim 映射写入 `config.js`；所有 `fetch` 和 CSV 下载自动附加 `Authorization: Bearer <token>`。

Authing 的普通 Access Token 只保证携带 `sub` 和 `scope`，而 `roles`、`extended_fields` 等 OIDC Scope Claim 位于 ID Token。MyRAG 因此默认设置 `OIDC_API_TOKEN_SOURCE=id_token`，并要求 Authing 使用 RS256 签发 ID Token；后端仍通过 Issuer、JWKS、有效期和 Audience 完整验签。切换到能在 Access Token 中签发业务 Claim 的 IdP 时，将该变量改为 `access_token` 即可，不需要修改业务代码。

后端是无状态 OAuth2 Resource Server，通过 `issuer-uri` 验证签名、签发方、有效期，并通过 `audiences` 验证目标受众。生产 Token 至少需要：

```json
{
  "aud": ["AUTHING_APP_ID"],
  "preferred_username": "zhangsan",
  "roles": ["KNOWLEDGE_OPERATOR"],
  "domains": ["售后服务", "产品知识"]
}
```

`roles` 会转换为 Spring Security 的 `ROLE_*`；`domains` 支持字符串数组或逗号分隔字符串。`ADMIN` 可跨领域，其他角色查询时必须指定领域，且只能访问 Token 声明的领域。`domains: ["*"]` 只应授予受控的跨领域服务账号。

Claim 名不写死在业务代码中：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `OIDC_API_TOKEN_SOURCE` | `id_token` | API Bearer Token 来源，可选 `id_token` 或 `access_token` |
| `OIDC_PRINCIPAL_CLAIM` | `preferred_username` | 后端审计身份和前端显示名 |
| `OIDC_ROLES_CLAIM` | `roles` | RBAC 角色列表或逗号/空格分隔字符串 |
| `OIDC_DOMAINS_CLAIM` | `domains` | 可访问知识领域列表或逗号分隔字符串 |

映射支持 `extended_fields.domains` 这样的点分隔嵌套路径；如果 Token 本身存在完全同名的 Claim，则优先按完整名称读取，因此也兼容 URL 命名空间 Claim。角色数组元素既可以是字符串，也可以是带 `code` 或 `name` 的对象。Principal 缺失时安全回退到标准 `sub`。

## Authing 免费版配置

1. 在 Authing 创建自建 SPA 应用，Issuer 使用 `https://<应用域名>.authing.cn/oidc`。
2. 启用 Authorization Code + PKCE，Token Endpoint 身份验证方式选择 `none`，ID Token 签名算法选择 `RS256`，不要把 App Secret 放进前端。
3. 登记 `https://<MyRAG域名>/auth/callback`，本机验证时为 `http://localhost:3000/auth/callback`；同时登记登出回跳地址。
4. 创建 `ADMIN`、`KNOWLEDGE_OPERATOR`、`REVIEWER`、`USER` 角色并分配用户；请求 Scope 配置为 `roles extended_fields`。
5. 建立用户扩展字段 `domains`，并在 OIDC Scope/Claim 配置中将其加入 ID Token；`roles` 使用 Authing 内置 Scope。
6. `OIDC_CLIENT_ID` 与 `OAUTH2_AUDIENCE` 均使用 Authing App ID；前后端 Authority/Issuer 必须完全一致。

Authing 的 `roles` 是内置 OIDC Scope；自定义字段可以通过 `extended_fields` 或自定义 Claim 暴露。选择 ID Token 作为 API 凭证是 Authing 的兼容模式；其他 IdP 应优先使用包含业务 Claim 的 Access Token。

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

## 文件防护

当前项目不集成独立杀毒服务，文件导入依靠扩展名白名单、实际 MIME 校验、单文件/单批容量限制、最大提取字符数、正文质量校验和默认 120 秒解析硬超时降低风险。

解析运行在独立 Worker 进程和可取消任务中。正式集群还应启用只读根文件系统、非 root、seccomp/AppArmor、出站白名单、临时目录配额和 Pod 级 CPU/内存限制。对来源不受信任的公网文件，仍应在网关或独立沙箱层增加组织认可的恶意文件检测能力。

## 上线核对

- IdP 只允许已登记的 HTTPS Redirect URI，SPA Client 不配置 Secret。
- 解码一枚真实 Authing ID Token，确认 `alg=RS256`，且 `iss`、`aud`、Principal、角色和领域均与映射配置一致。
- 网关和 MinIO 全部使用 TLS，密钥进入 Secret Manager，不写入 `.env` 或 Git。
- 用 `USER`、`REVIEWER`、`KNOWLEDGE_OPERATOR`、`ADMIN` 四类 Token 做正反向权限回归。
- 用未授权 `domains` 验证列表、详情、问答、导入批次均返回 403。
- 验证超限、伪造扩展名、损坏件、加密件和超时文件均被拒绝或进入可追踪失败状态。
- 验证审计日志不包含 Token、请求正文和知识正文。
