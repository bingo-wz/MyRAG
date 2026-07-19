import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import { DashboardPage } from './DashboardPage'
import type { Overview } from '../types'

vi.mock('../api', () => ({ api: { overview: vi.fn() } }))

const overview: Overview = {
  totalKnowledge: 12,
  approvedKnowledge: 8,
  pendingReview: 2,
  questionCount: 6,
  acceptanceRate: 75,
  acceptanceRateDelta: 5.2,
  averageConfidence: 82.5,
  averageLatencyMs: 640,
  latencyP95Ms: 980,
  badCaseCount: 1,
  oldestPendingMinutes: 95,
  activeImportBatches: 2,
  trend: [{ date: '2026-07-19', questions: 6, acceptanceRate: 75 }],
  domainDistribution: { 售后服务: 8 },
}

describe('DashboardPage', () => {
  beforeEach(() => vi.mocked(api.overview).mockResolvedValue(overview))

  it('展示后端返回的真实趋势和待处理指标', async () => {
    render(<DashboardPage navigate={vi.fn()} />)

    expect(await screen.findByText('75%')).toBeInTheDocument()
    expect(screen.getByText('较前 7 天 +5.2 个百分点')).toBeInTheDocument()
    expect(screen.getByText('P95 980ms')).toBeInTheDocument()
    expect(screen.getByText('最长已等待 1 小时')).toBeInTheDocument()
    expect(screen.getByText('2 个导入批次处理中')).toBeInTheDocument()
  })
})
