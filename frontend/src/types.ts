export type KnowledgeStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'OFFLINE'

export interface Knowledge {
  id: number
  title: string
  content: string
  domain: string
  source: string
  tags: string[]
  status: KnowledgeStatus
  reviewComment?: string
  reviewer?: string
  chunkCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface KnowledgePage {
  items: Knowledge[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DailyPoint {
  date: string
  questions: number
  acceptanceRate: number
}

export interface Overview {
  totalKnowledge: number
  approvedKnowledge: number
  pendingReview: number
  questionCount: number
  acceptanceRate: number
  averageConfidence: number
  averageLatencyMs: number
  badCaseCount: number
  trend: DailyPoint[]
  domainDistribution: Record<string, number>
}

export interface Source {
  documentId: number
  title: string
  domain: string
  excerpt: string
  score: number
}

export interface AskResponse {
  traceId: string
  answer: string
  confidence: number
  latencyMs: number
  sources: Source[]
}

export interface BadCase {
  traceId: string
  question: string
  answer: string
  confidence: number
  latencyMs: number
  reason?: string
  sourceSnapshot: string
  createdAt: string
}

export type ImportBatchStatus = 'QUEUED' | 'PROCESSING' | 'READY' | 'PARTIAL_READY' | 'SUBMITTED' | 'FAILED'
export type ImportFileStatus = 'QUEUED' | 'SCANNING' | 'DETECTING' | 'EXTRACTING' | 'VALIDATING' | 'INDEXING' | 'READY' | 'SUBMITTED' | 'FAILED'

export interface ImportFileTask {
  id: number
  originalName: string
  detectedType?: string
  sizeBytes: number
  status: ImportFileStatus
  extractedCharacters: number
  documentId?: number
  errorMessage?: string
  retryCount: number
  updatedAt: string
}

export interface ImportBatch {
  id: string
  status: ImportBatchStatus
  domain: string
  createdBy: string
  tags: string[]
  totalFiles: number
  processedFiles: number
  succeededFiles: number
  failedFiles: number
  progress: number
  createdAt: string
  updatedAt: string
  files: ImportFileTask[]
}
