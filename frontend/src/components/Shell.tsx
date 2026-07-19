import { useEffect, useState, type ReactNode } from 'react'
import {
  Activity, BookOpenText, Boxes, FileCheck2,
  LayoutDashboard, MessageSquareText, Search, Sparkles, UploadCloud,
} from 'lucide-react'
import { api } from '../api'
import type { Overview } from '../types'

export type PageKey = 'dashboard' | 'knowledge' | 'imports' | 'review' | 'playground' | 'badcases'

const nav: Array<{ key: PageKey; label: string; icon: typeof Activity }> = [
  { key: 'dashboard', label: '运营概览', icon: LayoutDashboard },
  { key: 'knowledge', label: '知识管理', icon: BookOpenText },
  { key: 'imports', label: '批量导入', icon: UploadCloud },
  { key: 'review', label: '审核工作台', icon: FileCheck2 },
  { key: 'playground', label: '问答调试', icon: MessageSquareText },
  { key: 'badcases', label: 'Bad Case', icon: Activity },
]

interface Props {
  page: PageKey
  onNavigate: (page: PageKey) => void
  meta: { title: string; subtitle: string }
  children: ReactNode
}

export function Shell({ page, onNavigate, meta, children }: Props) {
  const [overview, setOverview] = useState<Overview | null>(null)
  useEffect(() => { api.overview().then(setOverview).catch(() => setOverview(null)) }, [page])
  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        onNavigate('knowledge')
      }
    }
    window.addEventListener('keydown', shortcut)
    return () => window.removeEventListener('keydown', shortcut)
  }, [onNavigate])
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" onClick={() => onNavigate('dashboard')}>
          <span className="brand-mark"><Sparkles size={20} strokeWidth={1.8} /></span>
          <span><strong>MyRAG</strong><small>Knowledge OS</small></span>
        </button>

        <nav className="primary-nav" aria-label="主导航">
          <span className="nav-caption">工作空间</span>
          {nav.map((item) => {
            const Icon = item.icon
            return (
              <button key={item.key} className={page === item.key ? 'active' : ''} onClick={() => onNavigate(item.key)}>
                <Icon size={18} strokeWidth={1.7} />
                <span>{item.label}</span>
                {item.key === 'review' && overview && overview.pendingReview > 0 && <em>{overview.pendingReview}</em>}
              </button>
            )
          })}
        </nav>

        <div className="sidebar-foot">
          <div className="index-health">
            <div><Boxes size={17} /><span>向量索引</span><i /></div>
            <strong>{overview ? `${overview.approvedKnowledge} 个知识文档已生效` : '正在读取索引状态'}</strong>
            <small>{overview ? `知识总量：${overview.totalKnowledge}` : '请稍候'}</small>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="mobile-brand">MyRAG</div>
          <div className="page-heading">
            <h1>{meta.title}</h1>
            <span>{meta.subtitle}</span>
          </div>
          <div className="top-actions">
            <button className="global-search" onClick={() => onNavigate('knowledge')}><Search size={17} /><span>搜索知识内容</span><kbd>⌘ K</kbd></button>
          </div>
        </header>
        <div className="content-area">{children}</div>
      </main>
    </div>
  )
}
