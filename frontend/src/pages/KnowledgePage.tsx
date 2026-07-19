import { useEffect, useState } from 'react'
import {
  ChevronLeft, ChevronRight, Download, MoreHorizontal, Pencil, Plus, Power,
  RotateCcw, Search, Send, Trash2,
} from 'lucide-react'
import { api } from '../api'
import { StatusBadge } from '../components/StatusBadge'
import { EmptyState, ErrorState, LoadingState, Modal, Toast } from '../components/Ui'
import type { Knowledge, KnowledgeDraftSeed, KnowledgeForm, KnowledgePage as KnowledgePageData, KnowledgeStatus } from '../types'

const statuses: Array<{ value: KnowledgeStatus | ''; label: string }> = [
  { value: '', label: '全部状态' }, { value: 'APPROVED', label: '已生效' }, { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'DRAFT', label: '草稿' }, { value: 'REJECTED', label: '已驳回' }, { value: 'OFFLINE', label: '已下线' },
]

interface Props {
  seed: KnowledgeDraftSeed | null
  onSeedConsumed: () => void
}

interface Confirmation {
  title: string
  detail: string
  label: string
  action: () => Promise<void>
}

export function KnowledgePage({ seed, onSeedConsumed }: Props) {
  const [data, setData] = useState<KnowledgePageData | null>(null)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<KnowledgeStatus | ''>('')
  const [domain, setDomain] = useState('')
  const [domains, setDomains] = useState<string[]>([])
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState<Knowledge | 'new' | null>(null)
  const [editorSeed, setEditorSeed] = useState<KnowledgeDraftSeed | null>(null)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [toast, setToast] = useState('')
  const [acting, setActing] = useState<number | null>(null)

  const load = () => {
    setLoading(true); setError('')
    api.knowledge({ keyword, status, domain, page, size: 12 })
      .then(setData)
      .catch((reason: Error) => setError(reason.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    const timer = window.setTimeout(load, 220)
    return () => window.clearTimeout(timer)
  }, [keyword, status, domain, page])

  useEffect(() => {
    api.domains().then(setDomains).catch(() => setDomains([]))
  }, [data?.totalElements])

  useEffect(() => {
    if (!seed) return
    setEditorSeed(seed)
    setEditor('new')
    onSeedConsumed()
  }, [seed, onSeedConsumed])

  const mutate = async (id: number, operation: () => Promise<unknown>, success: string) => {
    setActing(id)
    try {
      await operation()
      setToast(success)
      setConfirmation(null)
      load()
    } catch (reason) {
      setToast((reason as Error).message)
    } finally {
      setActing(null)
    }
  }

  const confirm = (title: string, detail: string, label: string, action: () => Promise<void>) => {
    setConfirmation({ title, detail, label, action })
  }

  const exportKnowledge = async () => {
    try { await api.exportKnowledge(); setToast('知识导出已开始') } catch (reason) { setToast((reason as Error).message) }
  }

  const items = data?.items ?? []

  return (
    <div className="knowledge-page">
      <div className="toolbar">
        <div className="segmented">{statuses.map((item) => <button key={item.value} className={status === item.value ? 'active' : ''} onClick={() => { setStatus(item.value); setPage(0) }}>{item.label}</button>)}</div>
        <div className="toolbar-actions"><button className="secondary-button" onClick={exportKnowledge}><Download size={16} />导出</button><button className="primary-button" onClick={() => { setEditorSeed(null); setEditor('new') }}><Plus size={17} />新建知识</button></div>
      </div>

      <div className="list-controls">
        <label className="search-input"><Search size={17} /><input value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(0) }} placeholder="搜索标题或正文内容" /></label>
        <label className="compact-filter"><span>领域</span><select aria-label="业务领域筛选" value={domain} onChange={(event) => { setDomain(event.target.value); setPage(0) }}><option value="">全部领域</option>{domains.map((item) => <option key={item}>{item}</option>)}</select></label>
        <span>共 {data?.totalElements ?? 0} 条</span>
      </div>

      <div className="table-shell">
        <div className="knowledge-table table-header"><span>知识内容</span><span>业务领域</span><span>状态</span><span>切片</span><span>更新人 / 时间</span><span /></div>
        {loading ? <LoadingState /> : error ? <ErrorState message={error} retry={load} /> : items.length === 0 ? <EmptyState title="没有匹配的知识" detail="调整关键词、状态或领域筛选后再试" /> : items.map((item) => (
          <div className="knowledge-table table-row" key={item.id}>
            <div className="knowledge-cell"><span className="doc-symbol">{item.title.slice(0, 1)}</span><div><strong>{item.title}</strong><p>{item.content}</p><small>{item.tags.map((tag) => <em key={tag}>{tag}</em>)}</small></div></div>
            <span>{item.domain}</span><StatusBadge status={item.status} /><span>{item.chunkCount} 段</span><div className="owner-time"><strong>{item.createdBy}</strong><small>{formatTime(item.updatedAt)}</small></div>
            <KnowledgeActions item={item} disabled={acting === item.id} onEdit={() => { setEditorSeed(null); setEditor(item) }} onSubmit={() => mutate(item.id, () => api.submitKnowledge(item.id), '已提交审核')} onOffline={() => confirm('下线知识', `“${item.title}”下线后将立即停止参与问答召回。`, '确认下线', () => mutate(item.id, () => api.offlineKnowledge(item.id), '知识已下线'))} onReactivate={() => confirm('重新提交审核', `“${item.title}”将进入待审核队列。`, '重新提交', () => mutate(item.id, () => api.reactivateKnowledge(item.id), '知识已重新提交审核'))} onDelete={() => confirm('删除知识', `将永久删除“${item.title}”及其切片和向量索引，此操作无法撤销。`, '永久删除', () => mutate(item.id, () => api.deleteKnowledge(item.id), '知识已删除'))} />
          </div>
        ))}
        {data && data.totalPages > 1 && <footer className="table-pagination"><span>第 {data.page + 1} / {data.totalPages} 页</span><div><button disabled={data.page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}><ChevronLeft size={15} />上一页</button><button disabled={data.page + 1 >= data.totalPages} onClick={() => setPage((current) => current + 1)}>下一页<ChevronRight size={15} /></button></div></footer>}
      </div>

      {editor && <KnowledgeEditor knowledge={editor === 'new' ? undefined : editor} seed={editorSeed} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); setToast(editor === 'new' ? '知识草稿已创建' : '知识内容已更新'); load() }} />}
      {confirmation && <Modal title={confirmation.title} subtitle={confirmation.detail} onClose={() => setConfirmation(null)}><footer className="modal-actions confirm-actions"><button className="secondary-button" onClick={() => setConfirmation(null)}>取消</button><button className="reject-button" onClick={confirmation.action}>{confirmation.label}</button></footer></Modal>}
      {toast && <Toast message={toast} tone={isError(toast) ? 'error' : 'success'} />}
    </div>
  )
}

function KnowledgeActions({ item, disabled, onEdit, onSubmit, onOffline, onReactivate, onDelete }: {
  item: Knowledge
  disabled: boolean
  onEdit: () => void
  onSubmit: () => void
  onOffline: () => void
  onReactivate: () => void
  onDelete: () => void
}) {
  const editable = item.status === 'DRAFT' || item.status === 'REJECTED' || item.status === 'OFFLINE'
  const removable = item.status === 'DRAFT' || item.status === 'REJECTED' || item.status === 'OFFLINE'
  return <div className="row-actions"><details className="row-menu"><summary aria-label={`操作 ${item.title}`}><MoreHorizontal size={18} /></summary><div>{editable && <button onClick={onEdit}><Pencil size={14} />编辑内容</button>}{(item.status === 'DRAFT' || item.status === 'REJECTED') && <button disabled={disabled} onClick={onSubmit}><Send size={14} />提交审核</button>}{item.status === 'APPROVED' && <button disabled={disabled} onClick={onOffline}><Power size={14} />下线知识</button>}{item.status === 'OFFLINE' && <button disabled={disabled} onClick={onReactivate}><RotateCcw size={14} />重新提交</button>}{removable && <button className="danger" disabled={disabled} onClick={onDelete}><Trash2 size={14} />删除知识</button>}{item.status === 'PENDING_REVIEW' && <span>等待审核处理</span>}</div></details></div>
}

function KnowledgeEditor({ knowledge, seed, onClose, onSaved }: {
  knowledge?: Knowledge
  seed: KnowledgeDraftSeed | null
  onClose: () => void
  onSaved: () => void
}) {
  const initial: KnowledgeForm = knowledge ? {
    title: knowledge.title, content: knowledge.content, domain: knowledge.domain,
    source: knowledge.source, tags: knowledge.tags.join(','),
  } : {
    title: seed?.title ?? '', content: seed?.content ?? '', domain: seed?.domain ?? '',
    source: seed?.source ?? '运营录入', tags: seed?.tags ?? '', createdBy: '本地用户',
  }
  const [form, setForm] = useState<KnowledgeForm>(initial)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const change = (key: keyof KnowledgeForm, value: string) => setForm((current) => ({ ...current, [key]: value }))
  const save = async () => {
    setSaving(true); setError('')
    try {
      if (knowledge) await api.updateKnowledge(knowledge.id, form)
      else await api.createKnowledge({ ...form, createdBy: form.createdBy ?? '本地用户' })
      onSaved()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setSaving(false)
    }
  }
  const valid = form.title.trim() && form.content.trim() && form.domain.trim() && form.source.trim()
  return <Modal title={knowledge ? '编辑知识' : '新建知识'} subtitle={knowledge ? '保存后重新生成切片与向量索引' : '保存后进入草稿状态，可检查内容再提交审核'} onClose={onClose}><div className="form-grid"><label className="full"><span>知识标题</span><input value={form.title} onChange={(event) => change('title', event.target.value)} placeholder="例如：手机退换货服务规则" /></label><label><span>业务领域</span><input value={form.domain} onChange={(event) => change('domain', event.target.value)} /></label><label><span>知识来源</span><input value={form.source} onChange={(event) => change('source', event.target.value)} /></label><label className="full"><span>正文内容</span><textarea rows={8} value={form.content} onChange={(event) => change('content', event.target.value)} placeholder="输入可被 AI 检索和引用的完整内容" /></label><label className="full"><span>标签 <small>使用逗号分隔</small></span><input value={form.tags} onChange={(event) => change('tags', event.target.value)} placeholder="售后, 退货, 服务规则" /></label>{error && <p className="form-error">{error}</p>}</div><footer className="modal-actions"><button className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving || !valid} onClick={save}>{saving ? '保存中…' : knowledge ? '保存修改' : '保存草稿'}</button></footer></Modal>
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function isError(message: string) {
  return ['失败', '不能', '错误', '不存在', '冲突'].some((keyword) => message.includes(keyword))
}
