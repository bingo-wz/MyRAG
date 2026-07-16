import { useEffect, useState } from 'react'
import { AlertTriangle, ArrowUpRight, ChevronRight, Clock3, Copy, GitBranch, Search, ShieldAlert } from 'lucide-react'
import { api } from '../api'
import { EmptyState, ErrorState, LoadingState, Toast } from '../components/Ui'
import type { BadCase } from '../types'

export function BadCasesPage() {
  const [items, setItems] = useState<BadCase[]>([])
  const [selected, setSelected] = useState<BadCase | null>(null)
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const load = () => { api.badCases().then((result) => { setItems(result); setSelected((current) => result.find((item) => item.traceId === current?.traceId) ?? result[0] ?? null) }).catch((reason: Error) => setError(reason.message)).finally(() => setLoading(false)) }
  useEffect(load, [])
  if (loading) return <LoadingState />
  if (error) return <ErrorState message={error} retry={load} />
  const filtered = items.filter((item) => item.question.includes(query) || item.traceId.includes(query))
  if (!items.length) return <EmptyState title="没有 Bad Case" detail="低置信度或负反馈问题会自动进入这里" />
  return <div className="badcase-page"><section className="badcase-list"><div className="case-toolbar"><label><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索问题或 Trace ID" /></label><span>{filtered.length} 个待处理</span></div><div className="case-table case-head"><span>用户问题</span><span>异常原因</span><span>置信度</span><span>时间</span><span /></div>{filtered.map((item) => <button className={selected?.traceId === item.traceId ? 'case-table case-row active' : 'case-table case-row'} key={item.traceId} onClick={() => setSelected(item)}><div><span className="case-severity"><AlertTriangle size={15} /></span><div><strong>{item.question}</strong><code>{item.traceId}</code></div></div><span>{item.reason || '待分析'}</span><em className={item.confidence < 50 ? 'low' : ''}>{item.confidence}%</em><span>{formatTime(item.createdAt)}</span><ChevronRight size={16} /></button>)}</section>{selected && <aside className="trace-inspector"><header><div><span className="eyebrow">TRACE INSPECTOR</span><h2>回答链路</h2></div><span className="severity-label"><ShieldAlert size={16} />需处理</span></header><div className="trace-id"><span>Trace ID</span><code>{selected.traceId}</code><button onClick={() => { navigator.clipboard.writeText(selected.traceId); setToast('Trace ID 已复制') }}><Copy size={14} /></button></div><div className="trace-section"><span className="trace-number">01</span><div><small>用户问题</small><p>{selected.question}</p></div></div><div className="trace-line" /><div className="trace-section"><span className="trace-number warning">02</span><div><small>异常定位</small><strong>{selected.reason || '等待人工分析'}</strong><p>置信度 {selected.confidence}% · 响应 {selected.latencyMs}ms</p></div></div><div className="trace-line" /><div className="trace-section"><span className="trace-number">03</span><div><small>模型回答</small><p className="answer-snapshot">{selected.answer}</p></div></div><div className="trace-meta"><span><GitBranch size={15} />召回快照<code>{parseSources(selected.sourceSnapshot).length} 条来源</code></span><span><Clock3 size={15} />发生时间<time>{new Date(selected.createdAt).toLocaleString('zh-CN')}</time></span></div><button className="primary-button case-action" onClick={() => setToast('已创建知识补充任务')}><ArrowUpRight size={16} />创建知识补充任务</button></aside>}{toast && <Toast message={toast} />}</div>
}

function parseSources(value: string) { try { return JSON.parse(value) as unknown[] } catch { return [] } }
function formatTime(value: string) { return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
