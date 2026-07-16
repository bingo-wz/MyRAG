import { useEffect, useState } from 'react'
import { Check, ChevronRight, CircleX, FileText, Quote, ShieldCheck } from 'lucide-react'
import { api } from '../api'
import { EmptyState, ErrorState, LoadingState, Toast } from '../components/Ui'
import { StatusBadge } from '../components/StatusBadge'
import type { Knowledge } from '../types'

export function ReviewPage() {
  const [items, setItems] = useState<Knowledge[]>([])
  const [selected, setSelected] = useState<Knowledge | null>(null)
  const [comment, setComment] = useState('内容与现行规则一致，可以生效')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const load = () => { setLoading(true); api.knowledge({ status: 'PENDING_REVIEW' }).then((result) => { setItems(result.items); setSelected((current) => result.items.find((item) => item.id === current?.id) ?? result.items[0] ?? null) }).catch((reason: Error) => setError(reason.message)).finally(() => setLoading(false)) }
  useEffect(load, [])
  const review = async (approved: boolean) => { if (!selected) return; try { await api.reviewKnowledge(selected.id, approved, comment); setToast(approved ? '审核通过，知识已生效' : '知识已驳回'); load() } catch (reason) { setToast((reason as Error).message) } }
  if (loading) return <LoadingState />
  if (error) return <ErrorState message={error} retry={load} />
  if (!items.length) return <EmptyState title="审核队列已清空" detail="所有待审核知识均已处理" />
  return <div className="review-layout"><aside className="review-queue"><div className="review-caption"><span>等待处理</span><strong>{items.length}</strong></div>{items.map((item) => <button key={item.id} className={selected?.id === item.id ? 'active' : ''} onClick={() => setSelected(item)}><span className="queue-icon"><FileText size={18} /></span><div><strong>{item.title}</strong><small>{item.domain} · {item.createdBy}</small></div><ChevronRight size={16} /></button>)}</aside>{selected && <section className="review-document"><header><div><StatusBadge status={selected.status} /><h2>{selected.title}</h2><p>{selected.domain} · 来源：{selected.source} · 版本 v{selected.version}</p></div><span className="review-safety"><ShieldCheck size={18} />内容安全检测通过</span></header><div className="document-meta"><span>创建人<strong>{selected.createdBy}</strong></span><span>切片数量<strong>{selected.chunkCount} 段</strong></span><span>更新时间<strong>{new Date(selected.updatedAt).toLocaleString('zh-CN')}</strong></span><span>标签<strong>{selected.tags.join(' · ')}</strong></span></div><article><Quote size={24} /><p>{selected.content}</p></article><div className="review-decision"><label><span>审核意见</span><textarea rows={3} value={comment} onChange={(event) => setComment(event.target.value)} /></label><div><button className="reject-button" onClick={() => review(false)}><CircleX size={17} />驳回修改</button><button className="primary-button" onClick={() => review(true)}><Check size={17} />通过并生效</button></div></div></section>}{toast && <Toast message={toast} />}</div>
}
