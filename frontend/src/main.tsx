import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { auth } from './auth'
import './styles.css'

const root = createRoot(document.getElementById('root')!)
root.render(<div className="auth-loading"><strong>MyRAG</strong><span>正在验证登录状态…</span></div>)

auth.initialize()
  .then((session) => root.render(
    <StrictMode>
      <App session={session} />
    </StrictMode>,
  ))
  .catch((reason: Error) => root.render(
    <div className="auth-loading auth-error">
      <strong>无法完成登录</strong>
      <span>{reason.message}</span>
      <button onClick={() => auth.login()}>重新登录</button>
    </div>,
  ))
