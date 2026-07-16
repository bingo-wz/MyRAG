import { useCallback, useEffect, useRef, useState } from 'react'
import {
  AlertCircle, ArrowRight, Check, ChevronRight, Download, FileArchive, FileImage,
  FileSpreadsheet, FileText, LoaderCircle, RefreshCw, Send, UploadCloud, X,
} from 'lucide-react'
import { api } from '../api'
import { StatusBadge } from '../components/StatusBadge'
import { EmptyState, ErrorState, LoadingState, Toast } from '../components/Ui'
import type { ImportBatch, ImportFileTask } from '../types'

const processingStatuses = new Set(['QUEUED', 'PROCESSING'])
const fileSteps = ['QUEUED', 'DETECTING', 'EXTRACTING', 'VALIDATING', 'INDEXING', 'READY']

export function ImportPage() {
  const [batches, setBatches] = useState<ImportBatch[]>([])
  const [selected, setSelected] = useState<ImportBatch | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showUploader, setShowUploader] = useState(true)
  const [toast, setToast] = useState('')

  const load = useCallback(async () => {
    try {
      const result = await api.batches()
      setBatches(result)
      if (selected) {
        const next = result.find((item) => item.id === selected.id)
        if (next) setSelected(next)
      }
      setError('')
    } catch (reason) { setError((reason as Error).message) } finally { setLoading(false) }
  }, [selected?.id])

  useEffect(() => { load() }, [])
  useEffect(() => {
    if (!batches.some((batch) => processingStatuses.has(batch.status))) return
    const timer = window.setInterval(load, 1200)
    return () => window.clearInterval(timer)
  }, [batches, load])

  const created = (batch: ImportBatch) => {
    setSelected(batch); setShowUploader(false); setToast('文件已接收，解析任务开始执行'); load()
  }
  const retry = async (id: string) => {
    try { setSelected(await api.retryBatch(id)); setToast('失败文件已重新加入处理队列'); load() } catch (reason) { setToast((reason as Error).message) }
  }
  const submit = async (id: string) => {
    try { setSelected(await api.submitBatch(id)); setToast('本批知识已提交审核'); load() } catch (reason) { setToast((reason as Error).message) }
  }

  return (
    <div className="import-page">
      <section className="pipeline-ribbon" aria-label="导入流程">
        {['文件上传', '智能解析 / OCR', '清洗与校验', '切片向量化', '人工确认', '提交审核'].map((step, index) => <div key={step}><span>{index + 1}</span><strong>{step}</strong>{index < 5 && <ChevronRight size={16} />}</div>)}
      </section>

      <div className="import-layout">
        <section className="import-main">
          <div className="section-head compact"><div><span className="eyebrow">INGESTION CENTER</span><h2>{showUploader ? '创建导入批次' : selected ? `批次 ${selected.id}` : '导入任务'}</h2><p>{showUploader ? '文件独立解析，单个失败不会影响同批其他任务' : '可查看每个文件的解析阶段与结果'}</p></div>{!showUploader && <button className="secondary-button" onClick={() => setShowUploader(true)}><UploadCloud size={16} />新建批次</button>}</div>
          {showUploader ? <Uploader onCreated={created} /> : selected ? <BatchDetail batch={selected} onRetry={retry} onSubmit={submit} /> : <EmptyState title="选择一个导入批次" detail="从右侧任务记录中查看处理详情" />}
        </section>

        <aside className="batch-history">
          <div className="section-head compact"><div><h2>最近批次</h2><p>保留最近 20 条任务记录</p></div><button onClick={load} aria-label="刷新"><RefreshCw size={16} /></button></div>
          {loading ? <LoadingState /> : error ? <ErrorState message={error} retry={load} /> : batches.length === 0 ? <EmptyState title="暂无导入任务" detail="上传文件后将在这里显示" /> : batches.map((batch) => (
            <button key={batch.id} className={selected?.id === batch.id ? 'batch-item active' : 'batch-item'} onClick={() => { setSelected(batch); setShowUploader(false) }}>
              <div><strong>{batch.id}</strong><StatusBadge status={batch.status} /></div>
              <span>{batch.domain} · {batch.totalFiles} 个文件</span>
              <div className="mini-progress"><i style={{ width: `${batch.progress}%` }} /></div>
              <small>{formatDate(batch.createdAt)}<em>{batch.succeededFiles} 成功 / {batch.failedFiles} 失败</em></small>
            </button>
          ))}
        </aside>
      </div>
      {toast && <Toast message={toast} tone={toast.includes('不能') || toast.includes('失败') ? 'error' : 'success'} />}
    </div>
  )
}

function Uploader({ onCreated }: { onCreated: (batch: ImportBatch) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [files, setFiles] = useState<File[]>([])
  const [domain, setDomain] = useState('售后服务')
  const [tags, setTags] = useState('批量导入')
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const add = (incoming: File[]) => setFiles((current) => [...current, ...incoming].slice(0, 50))
  const upload = async () => {
    setUploading(true); setError('')
    try { onCreated(await api.createBatch(files, domain, tags)) } catch (reason) { setError((reason as Error).message) } finally { setUploading(false) }
  }
  return <div className="uploader-content">
    <div className={dragging ? 'drop-zone dragging' : 'drop-zone'} onDragOver={(event) => { event.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={(event) => { event.preventDefault(); setDragging(false); add(Array.from(event.dataTransfer.files)) }} onClick={() => input.current?.click()}>
      <input ref={input} type="file" multiple accept=".doc,.docx,.pdf,.xls,.xlsx,.csv,.tsv,.txt,.md,.rtf,.ppt,.pptx,.png,.jpg,.jpeg,.tif,.tiff,.bmp,.webp" onChange={(event) => add(Array.from(event.target.files ?? []))} />
      <span><UploadCloud size={28} /></span><h3>拖放文件到这里</h3><p>或点击选择文件，单批最多 50 个</p>
      <div className="format-list"><em>Word</em><em>PDF</em><em>Excel</em><em>图片 OCR</em><em>TXT / Markdown</em></div>
    </div>
    {files.length > 0 && <div className="selected-files"><div className="selected-head"><strong>已选择 {files.length} 个文件</strong><button onClick={() => setFiles([])}>清空</button></div>{files.map((file, index) => <div className="selected-file" key={`${file.name}-${index}`}><FileTypeIcon name={file.name} /><div><strong>{file.name}</strong><span>{formatBytes(file.size)}</span></div><button onClick={() => setFiles((current) => current.filter((_, itemIndex) => index !== itemIndex))}><X size={16} /></button></div>)}</div>}
    <div className="import-metadata"><label><span>业务领域</span><input value={domain} onChange={(event) => setDomain(event.target.value)} /></label><label><span>统一标签</span><input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="逗号分隔" /></label></div>
    <div className="import-notice"><AlertCircle size={17} /><p><strong>图片与扫描件会自动尝试 OCR。</strong> 如未安装中文语言包，文件会保留并标记失败，可在补齐运行环境后重试。</p></div>
    {error && <p className="form-error">{error}</p>}
    <button className="primary-button upload-submit" disabled={!files.length || !domain || uploading} onClick={upload}>{uploading ? <><LoaderCircle className="spin" size={17} />正在上传</> : <>开始导入 <ArrowRight size={17} /></>}</button>
  </div>
}

function BatchDetail({ batch, onRetry, onSubmit }: { batch: ImportBatch; onRetry: (id: string) => void; onSubmit: (id: string) => void }) {
  const canSubmit = batch.status === 'READY' || batch.status === 'PARTIAL_READY'
  return <div className="batch-detail">
    <div className="batch-summary"><div className="radial-progress" style={{ '--progress': `${batch.progress * 3.6}deg` } as React.CSSProperties}><span>{batch.progress}%</span></div><div><StatusBadge status={batch.status} /><h3>{batch.processedFiles} / {batch.totalFiles} 个文件已处理</h3><p>{batch.succeededFiles} 个已生成知识草稿，{batch.failedFiles} 个需要处理</p></div><div className="summary-actions"><a className="secondary-button" href={`/api/imports/${batch.id}/report`}><Download size={16} />结果报告</a>{batch.failedFiles > 0 && <button className="secondary-button" onClick={() => onRetry(batch.id)}><RefreshCw size={16} />重试失败项</button>}<button className="primary-button" disabled={!canSubmit} onClick={() => onSubmit(batch.id)}><Send size={16} />提交审核</button></div></div>
    <div className="file-task-list"><div className="file-task file-task-head"><span>文件</span><span>当前阶段</span><span>提取结果</span><span>重试</span></div>{batch.files.map((file) => <FileTask key={file.id} file={file} />)}</div>
  </div>
}

function FileTask({ file }: { file: ImportFileTask }) {
  const currentIndex = file.status === 'SUBMITTED' ? fileSteps.length : fileSteps.indexOf(file.status)
  return <div className="file-task"><div className="task-file"><FileTypeIcon name={file.originalName} /><div><strong>{file.originalName}</strong><span>{file.detectedType || '等待格式检测'} · {formatBytes(file.sizeBytes)}</span></div></div><div><StatusBadge status={file.status} />{file.status !== 'FAILED' && <div className="step-dots">{fileSteps.map((step, index) => <i key={step} className={index <= currentIndex ? 'done' : ''}>{index < currentIndex && <Check size={9} />}</i>)}</div>}</div><div className={file.errorMessage ? 'task-result error' : 'task-result'}>{file.errorMessage || (file.extractedCharacters ? `${file.extractedCharacters.toLocaleString()} 字符 · 知识 #${file.documentId}` : '处理中…')}</div><span>{file.retryCount}</span></div>
}

function FileTypeIcon({ name }: { name: string }) {
  const extension = name.split('.').pop()?.toLowerCase()
  if (['xls', 'xlsx', 'csv', 'tsv'].includes(extension ?? '')) return <span className="file-icon excel"><FileSpreadsheet size={19} /></span>
  if (['png', 'jpg', 'jpeg', 'tif', 'tiff', 'bmp', 'webp'].includes(extension ?? '')) return <span className="file-icon image"><FileImage size={19} /></span>
  if (['zip', 'rar', '7z'].includes(extension ?? '')) return <span className="file-icon archive"><FileArchive size={19} /></span>
  return <span className="file-icon document"><FileText size={19} /></span>
}

function formatBytes(value: number) { return value < 1024 * 1024 ? `${Math.max(1, Math.round(value / 1024))} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
