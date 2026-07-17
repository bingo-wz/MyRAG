import { UserManager, type User, type UserManagerSettings } from 'oidc-client-ts'

interface RuntimeConfig {
  oidcAuthority?: string
  oidcClientId?: string
  oidcScopes?: string
  oidcApiTokenSource?: string
  oidcPrincipalClaim?: string
  oidcRolesClaim?: string
}

declare global {
  interface Window {
    MYRAG_CONFIG?: RuntimeConfig
  }
}

export interface AuthSession {
  authenticated: boolean
  displayName: string
  roleLabel: string
}

const runtime = window.MYRAG_CONFIG ?? {}
const authority = runtime.oidcAuthority?.trim() ?? ''
const clientId = runtime.oidcClientId?.trim() ?? ''
const apiTokenSource = runtime.oidcApiTokenSource?.trim() === 'id_token' ? 'id_token' : 'access_token'
const principalClaim = runtime.oidcPrincipalClaim?.trim() || 'preferred_username'
const rolesClaim = runtime.oidcRolesClaim?.trim() || 'roles'
const configured = Boolean(authority && clientId)

const settings: UserManagerSettings | undefined = configured ? {
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: `openid profile email ${runtime.oidcScopes?.trim() ?? ''}`.trim(),
  loadUserInfo: true,
  monitorSession: false,
} : undefined

const manager = settings ? new UserManager(settings) : undefined

function session(user: User): AuthSession {
  const profile = user.profile as Record<string, unknown>
  const displayName = String(resolveClaim(profile, principalClaim)
    ?? profile.name ?? profile.preferred_username ?? profile.email ?? user.profile.sub ?? '已登录用户')
  const roles = claimValues(resolveClaim(profile, rolesClaim))
    .map(role => role.replace(/^ROLE_/i, '').toUpperCase())
  return {
    authenticated: true,
    displayName,
    roleLabel: roles.includes('ADMIN') ? '系统管理员'
      : roles.includes('REVIEWER') ? '知识审核员'
        : roles.includes('KNOWLEDGE_OPERATOR') ? '知识运营员' : '知识用户',
  }
}

function resolveClaim(profile: Record<string, unknown>, claimPath: string): unknown {
  if (Object.prototype.hasOwnProperty.call(profile, claimPath)) {
    return profile[claimPath]
  }
  let current: unknown = profile
  for (const segment of claimPath.split('.')) {
    if (!current || typeof current !== 'object' || !(segment in current)) return undefined
    current = (current as Record<string, unknown>)[segment]
  }
  return current
}

function claimValues(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map(item => {
      if (item && typeof item === 'object') {
        const role = item as Record<string, unknown>
        return String(role.code ?? role.name ?? '')
      }
      return String(item)
    }).filter(Boolean)
  }
  return typeof value === 'string' ? value.split(/[,\s]+/).filter(Boolean) : []
}

async function initialize(): Promise<AuthSession> {
  if (!manager) {
    return { authenticated: false, displayName: '开发用户', roleLabel: '本地开发模式' }
  }
  if (window.location.pathname === '/auth/callback') {
    const user = await manager.signinRedirectCallback()
    const state = typeof user.state === 'string' ? user.state : window.location.origin
    const target = new URL(state, window.location.origin)
    window.history.replaceState({}, document.title,
      target.origin === window.location.origin ? `${target.pathname}${target.search}${target.hash}` : '/')
  }
  const user = await manager.getUser()
  if (!user || user.expired) {
    await login()
    throw new Error('正在跳转到统一登录')
  }
  return session(user)
}

async function login(): Promise<void> {
  if (!manager) return
  const state = window.location.pathname === '/auth/callback'
    ? `${window.location.origin}/${window.location.hash}`
    : window.location.href
  await manager.signinRedirect({ state })
}

async function logout(): Promise<void> {
  if (!manager) return
  await manager.signoutRedirect()
}

async function apiToken(): Promise<string | undefined> {
  if (!manager) return undefined
  const user = await manager.getUser()
  if (!user || user.expired) return undefined
  return apiTokenSource === 'id_token' ? user.id_token : user.access_token
}

export const auth = { configured, initialize, login, logout, apiToken }
