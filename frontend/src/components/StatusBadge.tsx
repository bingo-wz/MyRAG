import type { ImportBatchStatus, ImportFileStatus, KnowledgeStatus } from '../types'

const labels: Record<string, string> = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已生效', REJECTED: '已驳回', OFFLINE: '已下线',
  QUEUED: '排队中', PROCESSING: '处理中', READY: '待确认', PARTIAL_READY: '部分完成', SUBMITTED: '已提交', FAILED: '失败',
  DETECTING: '格式检测', EXTRACTING: '内容提取', VALIDATING: '质量校验', INDEXING: '切片入库',
}

export function StatusBadge({ status }: { status: KnowledgeStatus | ImportBatchStatus | ImportFileStatus }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}><i />{labels[status] ?? status}</span>
}
