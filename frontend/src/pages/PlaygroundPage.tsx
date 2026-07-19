import { useEffect, useState } from 'react'
import { BookMarked, Check, ChevronDown, Clock, Copy, CornerDownLeft, MessageSquareText, RotateCcw, Sparkles, ThumbsDown, ThumbsUp } from 'lucide-react'
import { api } from '../api'
import { Toast } from '../components/Ui'
import type { AskResponse } from '../types'

const examples = ['手机买了六天还能退吗？', '会员积分可以抵扣多少金额？', '怎么预约官方维修网点？']

export function PlaygroundPage() {
  const [question, setQuestion] = useState('手机买了六天还能退吗？')
  const [domain, setDomain] = useState('')
  const [result, setResult] = useState<AskResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState('')
  const [domains, setDomains] = useState<string[]>([])
  useEffect(() => { api.domains().then(setDomains).catch(() => setDomains([])) }, [])
  const ask = async () => { if (!question.trim()) return; setLoading(true); try { setResult(await api.ask(question, domain || undefined)) } catch (reason) { setToast((reason as Error).message) } finally { setLoading(false) } }
  const feedback = async (accepted: boolean) => { if (!result) return; try { await api.feedback(result.traceId, accepted, accepted ? undefined : '回答未完整解决问题'); setToast(accepted ? '已记录为有效回答' : '已加入 Bad Case 追溯') } catch (reason) { setToast((reason as Error).message) } }
  return <div className="playground-layout">
    <aside className="prompt-panel"><div><span className="eyebrow">QUERY</span><h2>输入用户问题</h2><p>系统只会召回已审核生效的知识</p></div><label className="domain-select"><span>限定业务领域</span><div><select value={domain} onChange={(event) => setDomain(event.target.value)}><option value="">全部领域</option>{domains.map((item) => <option key={item}>{item}</option>)}</select><ChevronDown size={16} /></div></label><label className="question-box"><textarea rows={7} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="请输入需要验证的问题…" maxLength={1000} /><span>{question.length} / 1000</span></label><button className="primary-button ask-button" onClick={ask} disabled={loading || !question.trim()}>{loading ? <><span className="typing-dots"><i /><i /><i /></span>生成回答中</> : <><Sparkles size={17} />运行问答<kbd>⌘ ↵</kbd></>}</button><div className="example-queries"><span>试试这些问题</span>{examples.map((example) => <button key={example} onClick={() => setQuestion(example)}>{example}<CornerDownLeft size={14} /></button>)}</div></aside>
    <section className="answer-panel">{!result && !loading ? <div className="answer-placeholder"><span><MessageSquareText size={31} /></span><h2>回答将在这里生成</h2><p>你可以检查答案、引用来源、置信度和完整 Trace ID</p></div> : loading ? <div className="answer-loading"><span><Sparkles size={22} /></span><div><i /><i /><i /></div><p>正在检索相关知识并组织回答…</p></div> : result && <div className="answer-result"><header><div><span className="ai-mark"><Sparkles size={18} /></span><div><strong>MyRAG Assistant</strong><small>基于 {result.sources.length} 条已生效知识回答</small></div></div><div><button title="重新生成" onClick={ask}><RotateCcw size={16} /></button><button title="复制" onClick={() => { navigator.clipboard.writeText(result.answer); setToast('回答已复制') }}><Copy size={16} /></button></div></header><article>{result.answer.split('\n').map((line, index) => line ? <p key={index}>{line}</p> : <br key={index} />)}</article><div className="answer-stats"><span><Check size={15} />置信度 <strong>{Math.round(result.confidence * 100)}%</strong></span><span><Clock size={15} />耗时 <strong>{result.latencyMs}ms</strong></span><span>Trace ID <code>{result.traceId}</code></span></div><div className="source-list"><h3><BookMarked size={17} />引用来源</h3>{result.sources.map((source, index) => <details key={`${source.documentId}-${index}`} open={index === 0}><summary><span>{String(index + 1).padStart(2, '0')}</span><div><strong>{source.title}</strong><small>{source.domain} · 相似度 {Math.round(source.score * 100)}%</small></div><ChevronDown size={16} /></summary><p>{source.excerpt}</p></details>)}</div><footer><span>这个回答解决了问题吗？</span><div><button onClick={() => feedback(true)}><ThumbsUp size={16} />有帮助</button><button onClick={() => feedback(false)}><ThumbsDown size={16} />需改进</button></div></footer></div>}</section>{toast && <Toast message={toast} tone={toast.includes('失败') ? 'error' : 'success'} />}
  </div>
}
