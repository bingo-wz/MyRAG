import { useEffect, useState } from 'react'
import { Download, Filter, MoreHorizontal, Plus, Search, Send, SlidersHorizontal } from 'lucide-react'
import { api } from '../api'
import { StatusBadge } from '../components/StatusBadge'
import { EmptyState, ErrorState, LoadingState, Modal, Toast } from '../components/Ui'
import type { Knowledge, KnowledgeStatus } from '../types'

const statuses: Array<{ value: KnowledgeStatus | ''; label: string }> = [
  { value: '', label: '全部状态' }, { value: 'APPROVED', label: '已生效' }, { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'DRAFT', label: '草稿' }, { value: 'REJECTED', label: '已驳回' }, { value: 'OFFLINE', label: '已下线' },
]

export function KnowledgePage() {
  const [items, setItems] = useState<Knowledge[]>([])
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<KnowledgeStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [toast, setToast] = useState('')

  const load = () => {
    setLoading(true); setError('')
    api.knowledge({ keyword, status }).then((result) => setItems(result.items)).catch((reason: Error) => setError(reason.message)).finally(() => setLoading(false))
  }
  useEffect(() => { const timer = window.setTimeout(load, 220); return () => window.clearTimeout(timer) }, [keyword, status])

  const submit = async (id: number) => {
    try { await api.submitKnowledge(id); setToast('已提交审核'); load() } catch (reason) { setToast((reason as Error).message) }
  }

  return (
    <div className="knowledge-page">
      <div className="toolbar">
        <div className="segmented">{statuses.map((item) => <button key={item.value} className={status === item.value ? 'active' : ''} onClick={() => setStatus(item.value)}>{item.label}</button>)}</div>
        <div className="toolbar-actions"><a className="secondary-button" href="/api/knowledge/export"><Download size={16} />导出</a><button className="primary-button" onClick={() => setShowCreate(true)}><Plus size={17} />新建知识</button></div>
      </div>
      <div className="list-controls"><label className="search-input"><Search size={17} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索标题或正文内容" /></label><button><Filter size={16} />领域</button><button><SlidersHorizontal size={16} />更多筛选</button><span>共 {items.length} 条</span></div>

      <div className="table-shell">
        <div className="knowledge-table table-header"><span>知识内容</span><span>业务领域</span><span>状态</span><span>切片</span><span>更新人 / 时间</span><span /></div>
        {loading ? <LoadingState /> : error ? <ErrorState message={error} retry={load} /> : items.length === 0 ? <EmptyState title="没有匹配的知识" detail="调整关键词或状态筛选后再试" /> : items.map((item) => (
          <div className="knowledge-table table-row" key={item.id}>
            <div className="knowledge-cell"><span className="doc-symbol">{item.title.slice(0, 1)}</span><div><strong>{item.title}</strong><p>{item.content}</p><small>{item.tags.map((tag) => <em key={tag}>{tag}</em>)}</small></div></div>
            <span>{item.domain}</span><StatusBadge status={item.status} /><span>{item.chunkCount} 段</span><div className="owner-time"><strong>{item.createdBy}</strong><small>{formatTime(item.updatedAt)}</small></div>
            <div className="row-actions">{(item.status === 'DRAFT' || item.status === 'REJECTED') && <button title="提交审核" onClick={() => submit(item.id)}><Send size={16} /></button>}<button><MoreHorizontal size={18} /></button></div>
          </div>
        ))}
      </div>

      {showCreate && <CreateKnowledge onClose={() => setShowCreate(false)} onCreated={() => { setShowCreate(false); setToast('知识草稿已创建'); load() }} />}
      {toast && <Toast message={toast} tone={toast.includes('失败') || toast.includes('不能') ? 'error' : 'success'} />}
    </div>
  )
}

function CreateKnowledge({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState({ title: '', content: '', domain: '售后服务', source: '运营录入', tags: '', createdBy: '知识运营' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const change = (key: string, value: string) => setForm((current) => ({ ...current, [key]: value }))
  const save = async () => {
    setSaving(true); setError('')
    try { await api.createKnowledge(form); onCreated() } catch (reason) { setError((reason as Error).message) } finally { setSaving(false) }
  }
  return <Modal title="新建知识" subtitle="保存后进入草稿状态，可检查内容再提交审核" onClose={onClose}><div className="form-grid"><label className="full"><span>知识标题</span><input value={form.title} onChange={(event) => change('title', event.target.value)} placeholder="例如：手机退换货服务规则" /></label><label><span>业务领域</span><input value={form.domain} onChange={(event) => change('domain', event.target.value)} /></label><label><span>知识来源</span><input value={form.source} onChange={(event) => change('source', event.target.value)} /></label><label className="full"><span>正文内容</span><textarea rows={8} value={form.content} onChange={(event) => change('content', event.target.value)} placeholder="输入可被 AI 检索和引用的完整内容" /></label><label className="full"><span>标签 <small>使用逗号分隔</small></span><input value={form.tags} onChange={(event) => change('tags', event.target.value)} placeholder="售后, 退货, 服务规则" /></label>{error && <p className="form-error">{error}</p>}</div><footer className="modal-actions"><button className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving || !form.title || !form.content} onClick={save}>{saving ? '保存中…' : '保存草稿'}</button></footer></Modal>
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
