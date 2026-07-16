import { useEffect, useState } from 'react'
import { Shell, type PageKey } from './components/Shell'
import { DashboardPage } from './pages/DashboardPage'
import { KnowledgePage } from './pages/KnowledgePage'
import { ImportPage } from './pages/ImportPage'
import { ReviewPage } from './pages/ReviewPage'
import { PlaygroundPage } from './pages/PlaygroundPage'
import { BadCasesPage } from './pages/BadCasesPage'

const pageTitles: Record<PageKey, { title: string; subtitle: string }> = {
  dashboard: { title: '运营概览', subtitle: '过去 7 天 · 实时数据' },
  knowledge: { title: '知识管理', subtitle: '统一维护知识内容与生效状态' },
  imports: { title: '批量导入', subtitle: '多格式解析、校验与入库任务' },
  review: { title: '审核工作台', subtitle: '仅展示等待人工确认的知识' },
  playground: { title: '问答调试', subtitle: '验证召回、答案与引用来源' },
  badcases: { title: 'Bad Case 追溯', subtitle: '从用户反馈回溯回答链路' },
}

function App() {
  const [page, setPage] = useState<PageKey>(() => {
    const hash = window.location.hash.slice(1) as PageKey
    return hash in pageTitles ? hash : 'dashboard'
  })

  useEffect(() => {
    window.location.hash = page
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [page])

  const content = {
    dashboard: <DashboardPage navigate={setPage} />,
    knowledge: <KnowledgePage />,
    imports: <ImportPage />,
    review: <ReviewPage />,
    playground: <PlaygroundPage />,
    badcases: <BadCasesPage />,
  }[page]

  return (
    <Shell page={page} onNavigate={setPage} meta={pageTitles[page]}>
      <div className="page-enter" key={page}>{content}</div>
    </Shell>
  )
}

export default App
