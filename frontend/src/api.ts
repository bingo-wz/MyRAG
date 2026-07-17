import type { AskResponse, BadCase, ImportBatch, Knowledge, KnowledgePage, KnowledgeStatus, Overview } from './types'
import { auth } from './auth'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await authorizedFetch(path, options)
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function authorizedFetch(path: string, options?: RequestInit): Promise<Response> {
  const headers = new Headers(options?.headers)
  const accessToken = await auth.accessToken()
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(path, { ...options, headers })
  if (response.status === 401 && auth.configured) {
    await auth.login()
    throw new Error('登录状态已失效，正在重新登录')
  }
  return response
}

async function errorMessage(response: Response): Promise<string> {
  let message = `请求失败（${response.status}）`
  try {
    const body = await response.json()
    message = body.message || message
  } catch {
    // 保留默认错误信息
  }
  return message
}

async function download(path: string, filename: string): Promise<void> {
  const response = await authorizedFetch(path)
  if (!response.ok) throw new Error(await errorMessage(response))
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

const jsonOptions = (method: string, body?: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: body === undefined ? undefined : JSON.stringify(body),
})

export const api = {
  overview: () => request<Overview>('/api/analytics/overview'),
  badCases: () => request<BadCase[]>('/api/analytics/bad-cases'),
  knowledge: (params: { keyword?: string; status?: KnowledgeStatus | ''; domain?: string; size?: number } = {}) => {
    const query = new URLSearchParams()
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.status) query.set('status', params.status)
    if (params.domain) query.set('domain', params.domain)
    query.set('size', String(params.size ?? 50))
    return request<KnowledgePage>(`/api/knowledge?${query}`)
  },
  createKnowledge: (body: Record<string, string>) => request<Knowledge>('/api/knowledge', jsonOptions('POST', body)),
  submitKnowledge: (id: number) => request<Knowledge>(`/api/knowledge/${id}/submit`, { method: 'POST' }),
  reviewKnowledge: (id: number, approved: boolean, comment: string) =>
    request<Knowledge>(`/api/knowledge/${id}/review`, jsonOptions('POST', { approved, reviewer: '王敏', comment })),
  offlineKnowledge: (id: number) => request<Knowledge>(`/api/knowledge/${id}/offline`, { method: 'POST' }),
  ask: (question: string, domain?: string) => request<AskResponse>('/api/qa/ask', jsonOptions('POST', { question, domain })),
  feedback: (traceId: string, accepted: boolean, reason?: string) =>
    request<void>(`/api/qa/${traceId}/feedback`, jsonOptions('POST', { accepted, reason })),
  batches: () => request<ImportBatch[]>('/api/imports'),
  batch: (id: string) => request<ImportBatch>(`/api/imports/${id}`),
  createBatch: (files: File[], domain: string, tags: string) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    form.append('domain', domain)
    form.append('createdBy', '知识运营')
    form.append('tags', tags)
    return request<ImportBatch>('/api/imports', { method: 'POST', body: form })
  },
  retryBatch: (id: string) => request<ImportBatch>(`/api/imports/${id}/retry`, { method: 'POST' }),
  submitBatch: (id: string) => request<ImportBatch>(`/api/imports/${id}/submit`, { method: 'POST' }),
  exportKnowledge: () => download('/api/knowledge/export', '知识库导出.csv'),
  downloadBatchReport: (id: string) => download(`/api/imports/${id}/report`, `导入报告-${id}.csv`),
}
