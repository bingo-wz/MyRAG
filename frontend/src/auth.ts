import { UserManager, type User, type UserManagerSettings } from 'oidc-client-ts'

interface RuntimeConfig {
  oidcAuthority?: string
  oidcClientId?: string
  oidcScopes?: string
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
  const displayName = String(profile.name ?? profile.preferred_username ?? profile.email ?? '已登录用户')
  const roles = Array.isArray(profile.roles) ? profile.roles.map(String) : []
  return {
    authenticated: true,
    displayName,
    roleLabel: roles.includes('ADMIN') ? '系统管理员' : roles.includes('REVIEWER') ? '知识审核员' : '知识用户',
  }
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
  await manager.signinRedirect({ state: window.location.href })
}

async function logout(): Promise<void> {
  if (!manager) return
  await manager.signoutRedirect()
}

async function accessToken(): Promise<string | undefined> {
  if (!manager) return undefined
  const user = await manager.getUser()
  return user && !user.expired ? user.access_token : undefined
}

export const auth = { configured, initialize, login, logout, accessToken }
