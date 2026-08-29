// Playwright E2E 配置 - LSC V6.2-AI
// 纯静态HTML应用：默认使用 http://127.0.0.1:8765 作为 baseURL
const { defineConfig, devices } = require('@playwright/test');

const BASE_URL = process.env.LSC_E2E_BASE_URL || 'http://127.0.0.1:8765';
const PORT = process.env.LSC_E2E_PORT ? Number(process.env.LSC_E2E_PORT) : 8765;

// 自动检测可用 Chromium 二进制（Playwright 内置 / puppeteer / 系统），无匹配返回 undefined
function resolveChromiumExec() {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH) return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  try {
    const out = require('child_process')
      .execSync('ls /root/.cache/puppeteer/chrome/linux-*/chrome-linux64/chrome 2>/dev/null | head -1')
      .toString().trim();
    return out || undefined;
  } catch (_) { return undefined; }
}
const CHROMIUM_EXEC = resolveChromiumExec();

module.exports = defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.js',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : 1,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'e2e-report' }],
    ['json', { outputFile: 'e2e-report/results.json' }],
  ],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    locale: 'zh-CN',
    ...(CHROMIUM_EXEC ? { launchOptions: { executablePath: CHROMIUM_EXEC, args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'] } } : {}),
  },
  projects: [
    {
      name: 'chromium-headless',
      use: {
        ...devices['Desktop Chrome'],
        headless: true,
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      // 用于移动端 / 小程序响应式模拟（iPhone 14 尺寸）
      name: 'chromium-mobile',
      grep: /移动端|小程序|\(mobile\)|\(mini\)/i,
      use: {
        ...devices['iPhone 14'],
        headless: true,
        defaultBrowserType: 'chromium',
        isMobile: true,
        hasTouch: true,
        colorScheme: 'dark',
      },
    },
  ],
  // 当静态服务未启动时，Playwright可自行启动
  webServer: {
    command: `python3 -m http.server ${PORT} --bind 0.0.0.0`,
    url: `${BASE_URL}/`,
    reuseExistingServer: true,
    cwd: __dirname,
    timeout: 20_000,
  },
});
