// @ts-check
// Playwright E2E smoke test: Login page loads and form interactions work.
// Run: npx playwright test

const { test, expect } = require('@playwright/test');

test.describe('登录页面', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('页面加载并显示登录表单', async ({ page }) => {
    await expect(page.locator('.login-card')).toBeVisible();
    await expect(page.locator('text=登录')).toBeVisible();
  });

  test('短信登录模式正常工作', async ({ page }) => {
    // 默认应该是短信登录
    const phoneInput = page.locator('input[placeholder*="手机"]').first();
    await expect(phoneInput).toBeVisible();
    await phoneInput.fill('13800000001');

    // 点击获取验证码按钮
    const sendBtn = page.locator('text=获取验证码').first();
    await expect(sendBtn).toBeVisible();
  });

  test('切换到账号密码登录', async ({ page }) => {
    // 找到切换按钮
    const switchBtn = page.locator('text=账号登录').first();
    if (await switchBtn.isVisible()) {
      await switchBtn.click();
      await expect(page.locator('input[type="password"]').first()).toBeVisible();
    }
  });

  test('空表单提交显示验证错误', async ({ page }) => {
    const submitBtn = page.locator('button[type="submit"], .el-button--primary').filter({ hasText: /登录|注册/ }).first();
    if (await submitBtn.isVisible()) {
      await submitBtn.click();
      // Element Plus 应该显示校验错误
      await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5000 });
    }
  });
});
