import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import { KnowledgePage } from './KnowledgePage'
import type { Knowledge, KnowledgePage as KnowledgePageData } from '../types'

vi.mock('../api', () => ({
  api: {
    knowledge: vi.fn(), domains: vi.fn(), updateKnowledge: vi.fn(), createKnowledge: vi.fn(),
    submitKnowledge: vi.fn(), offlineKnowledge: vi.fn(), reactivateKnowledge: vi.fn(),
    deleteKnowledge: vi.fn(), exportKnowledge: vi.fn(),
  },
}))

const knowledge: Knowledge = {
  id: 1,
  title: '测试知识',
  content: '这是一条用于验证编辑流程的知识内容。',
  domain: '售后服务',
  source: '测试来源',
  tags: ['测试'],
  status: 'DRAFT',
  chunkCount: 1,
  createdBy: '本地用户',
  createdAt: '2026-07-19T10:00:00',
  updatedAt: '2026-07-19T10:00:00',
  version: 0,
}

const pageData: KnowledgePageData = {
  items: [knowledge], page: 0, size: 12, totalElements: 25, totalPages: 3,
}

describe('KnowledgePage', () => {
  beforeEach(() => {
    vi.mocked(api.knowledge).mockResolvedValue(pageData)
    vi.mocked(api.domains).mockResolvedValue(['售后服务', '会员权益'])
    vi.mocked(api.updateKnowledge).mockResolvedValue({ ...knowledge, title: '更新后的知识' })
  })

  it('使用真实总数、领域筛选和分页参数加载知识', async () => {
    const user = userEvent.setup()
    render(<KnowledgePage seed={null} onSeedConsumed={vi.fn()} />)

    expect(await screen.findByText('测试知识')).toBeInTheDocument()
    expect(screen.getByText('共 25 条')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('业务领域筛选'), '售后服务')

    await waitFor(() => expect(api.knowledge).toHaveBeenLastCalledWith(expect.objectContaining({
      domain: '售后服务', page: 0, size: 12,
    })))
  })

  it('通过行菜单编辑知识并调用更新接口', async () => {
    const user = userEvent.setup()
    render(<KnowledgePage seed={null} onSeedConsumed={vi.fn()} />)
    await screen.findByText('测试知识')

    await user.click(screen.getByLabelText('操作 测试知识'))
    await user.click(screen.getByRole('button', { name: '编辑内容' }))
    const title = screen.getByLabelText('知识标题')
    await user.clear(title)
    await user.type(title, '更新后的知识')
    await user.click(screen.getByRole('button', { name: '保存修改' }))

    await waitFor(() => expect(api.updateKnowledge).toHaveBeenCalledWith(1,
      expect.objectContaining({ title: '更新后的知识', domain: '售后服务' })))
  })
})
