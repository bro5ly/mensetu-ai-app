import { test, expect } from '@playwright/test';

test('homepage has title', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Mensetu AI/);
});

test('homepage has heading', async ({ page }) => {
  await page.goto('/');
  const heading = page.locator('h1');
  await expect(heading).toHaveText('Mensetu AI');
});
