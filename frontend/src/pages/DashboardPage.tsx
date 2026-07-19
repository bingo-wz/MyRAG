import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, BookOpenCheck, CircleGauge, Clock3, MessageSquareMore, ShieldAlert, TrendingUp } from 'lucide-react'
import { api } from '../api'
import type { PageKey } from '../components/Shell'
import { ErrorState, LoadingState } from '../components/Ui'
import type { Overview } from '../types'

export function DashboardPage({ navigate }: { navigate: (page: PageKey) => void }) {
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState('')
  const load = () => {
    setError('')
    api.overview().then(setData).catch((reason: Error) => setError(reason.message))
  }
  useEffect(load, [])

  if (error) return <ErrorState message={error} retry={load} />
  if (!data) return <LoadingState />

  const metrics = [
    { label: '已生效知识', value: data.approvedKnowledge.toLocaleString(), note: `共 ${data.totalKnowledge} 条`, icon: BookOpenCheck, accent: true },
    { label: '问答采纳率', value: `${data.acceptanceRate}%`, note: `较前 7 天 ${signed(data.acceptanceRateDelta)} 个百分点`, icon: TrendingUp },
    { label: '平均置信度', value: `${data.averageConfidence}%`, note: '目标 ≥ 80%', icon: CircleGauge },
    { label: '平均响应时间', value: `${data.averageLatencyMs}ms`, note: `P95 ${data.latencyP95Ms}ms`, icon: Clock3 },
  ]

  return (
    <div className="dashboard-page">
      <section className="metrics-strip">
        {metrics.map((metric) => {
          const Icon = metric.icon
          return <div className={metric.accent ? 'metric accent' : 'metric'} key={metric.label}><span><Icon size={18} /></span><div><small>{metric.label}</small><strong>{metric.value}</strong><em>{metric.note}</em></div></div>
        })}
      </section>

      <section className="dashboard-grid">
        <div className="trend-panel">
          <div className="section-head">
            <div><span className="eyebrow">QUALITY PULSE</span><h2>问答质量趋势</h2><p>采纳率与每日问题量，按自然日聚合</p></div>
            <div className="legend"><span><i className="line-dot" />问题量</span><span><i className="area-dot" />采纳率</span></div>
          </div>
          <TrendChart data={data.trend} />
        </div>

        <aside className="attention-panel">
          <span className="eyebrow">NEEDS ATTENTION</span>
          <h2>今日待处理</h2>
          <button onClick={() => navigate('review')}><div className="attention-icon amber"><MessageSquareMore size={20} /></div><div><strong>{data.pendingReview} 条知识待审核</strong><span>{data.oldestPendingMinutes === undefined || data.oldestPendingMinutes === null ? '当前没有等待中的知识' : `最长已等待 ${duration(data.oldestPendingMinutes)}`}</span></div><ArrowRight size={17} /></button>
          <button onClick={() => navigate('badcases')}><div className="attention-icon red"><ShieldAlert size={20} /></div><div><strong>{data.badCaseCount} 个 Bad Case</strong><span>需要补充知识或修正召回</span></div><ArrowRight size={17} /></button>
          <button onClick={() => navigate('imports')}><div className="attention-icon teal"><Clock3 size={20} /></div><div><strong>{data.activeImportBatches} 个导入批次处理中</strong><span>查看解析、重试与入库进度</span></div><ArrowRight size={17} /></button>
        </aside>
      </section>

      <section className="domain-section">
        <div className="section-head"><div><span className="eyebrow">KNOWLEDGE COVERAGE</span><h2>领域知识分布</h2><p>当前知识规模与各业务领域覆盖情况</p></div><button onClick={() => navigate('knowledge')}>管理全部知识 <ArrowRight size={16} /></button></div>
        <DomainDistribution data={data.domainDistribution} />
      </section>
    </div>
  )
}

function TrendChart({ data }: { data: Overview['trend'] }) {
  const max = Math.max(...data.map((item) => item.questions), 1)
  const points = useMemo(() => data.map((item, index) => {
    const x = 35 + index * (610 / Math.max(data.length - 1, 1))
    const y = 180 - (item.questions / max) * 130
    const acceptanceY = 180 - (item.acceptanceRate / 100) * 130
    return { x, y, acceptanceY, ...item }
  }), [data, max])
  const path = points.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.y}`).join(' ')
  const acceptancePath = points.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.acceptanceY}`).join(' ')
  const area = `${path} L ${points.at(-1)?.x ?? 645} 190 L 35 190 Z`
  return (
    <div className="chart-wrap">
      <svg className="trend-chart" viewBox="0 0 680 230" role="img" aria-label="最近七天问答趋势">
        {[50, 95, 140, 185].map((y) => <line key={y} x1="35" x2="650" y1={y} y2={y} className="grid-line" />)}
        <defs><linearGradient id="chartArea" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#3d9f89" stopOpacity=".28" /><stop offset="1" stopColor="#3d9f89" stopOpacity="0" /></linearGradient></defs>
        <path d={area} fill="url(#chartArea)" />
        <path d={path} className="chart-line" />
        <path d={acceptancePath} className="acceptance-line" />
        {points.map((point) => <g key={point.date}><circle cx={point.x} cy={point.y} r="4" className="chart-point" /><text x={point.x} y="217" textAnchor="middle">{point.date.slice(5)}</text><text x={point.x} y={point.y - 12} textAnchor="middle" className="chart-value">{point.questions}</text></g>)}
      </svg>
    </div>
  )
}

function signed(value: number) {
  return `${value > 0 ? '+' : ''}${value}`
}

function duration(minutes: number) {
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  return hours < 24 ? `${hours} 小时` : `${Math.floor(hours / 24)} 天`
}

function DomainDistribution({ data }: { data: Record<string, number> }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1])
  const max = Math.max(...entries.map(([, value]) => value), 1)
  return <div className="domain-bars">{entries.map(([name, value], index) => <div className="domain-row" key={name}><span className="domain-index">{String(index + 1).padStart(2, '0')}</span><strong>{name}</strong><div><i style={{ width: `${Math.max(8, value / max * 100)}%` }} /></div><em>{value} 条</em></div>)}</div>
}
