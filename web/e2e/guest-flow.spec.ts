import { test, expect } from '@playwright/test'

test('guest can view the landing page and the read-only sandbox dashboard', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: /acme bank/i })).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toContainText(/banking with/i)

  await page.goto('/sandbox')
  await expect(page.getByText(/welcome, jordan/i)).toBeVisible()
  await expect(page.getByText(/read-only demo/i)).toBeVisible()
})
