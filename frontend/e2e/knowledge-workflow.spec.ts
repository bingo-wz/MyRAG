import { expect, test } from '@playwright/test'

test('完成知识创建、审核、问答和反馈闭环', async ({ page }) => {
  const title = `退换货规则 ${Date.now()}`
  await page.goto('/#knowledge')
  await page.getByRole('button', { name: '新建知识' }).click()
  await page.getByLabel('知识标题').fill(title)
  await page.getByLabel('业务领域', { exact: true }).fill('售后服务')
  await page.getByLabel('知识来源').fill('E2E 自动化')
  await page.getByLabel('正文内容').fill('用户签收商品七日内，在商品完好且配件齐全时可以申请无理由退货。')
  await page.getByLabel(/标签/).fill('退货,E2E')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText('知识草稿已创建')).toBeVisible()
  await expect(page.getByText(title)).toBeVisible()

  await page.getByLabel(`操作 ${title}`).click()
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByText('已提交审核')).toBeVisible()

  await page.getByRole('button', { name: '审核工作台' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await page.getByLabel('审核意见').fill('E2E 审核通过')
  await page.getByRole('button', { name: '通过并生效' }).click()
  await expect(page.getByText('审核通过，知识已生效')).toBeVisible()

  await page.getByRole('button', { name: '问答调试' }).click()
  await page.getByLabel('限定业务领域').selectOption('售后服务')
  await page.getByPlaceholder('请输入需要验证的问题…').fill('签收六天还能申请退货吗？')
  await page.getByRole('button', { name: '运行问答' }).click()
  await expect(page.getByText(/七日/).first()).toBeVisible()
  await page.getByRole('button', { name: '需改进' }).click()
  await expect(page.getByText('已加入 Bad Case 追溯')).toBeVisible()
})
