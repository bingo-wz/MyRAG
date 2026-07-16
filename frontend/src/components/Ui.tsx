import type { ReactNode } from 'react'
import { AlertTriangle, Inbox, LoaderCircle, X } from 'lucide-react'

export function LoadingState({ label = '正在加载数据' }: { label?: string }) {
  return <div className="state-block"><LoaderCircle className="spin" size={22} /><span>{label}</span></div>
}

export function EmptyState({ title, detail }: { title: string; detail: string }) {
  return <div className="state-block"><Inbox size={24} /><strong>{title}</strong><span>{detail}</span></div>
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="state-block error"><AlertTriangle size={24} /><strong>加载失败</strong><span>{message}</span>{retry && <button onClick={retry}>重新加载</button>}</div>
}

export function Modal({ title, subtitle, onClose, children }: { title: string; subtitle?: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="modal-panel" role="dialog" aria-modal="true">
        <header><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button onClick={onClose}><X size={19} /></button></header>
        {children}
      </section>
    </div>
  )
}

export function Toast({ message, tone = 'success' }: { message: string; tone?: 'success' | 'error' }) {
  return <div className={`toast toast-${tone}`}>{message}</div>
}
