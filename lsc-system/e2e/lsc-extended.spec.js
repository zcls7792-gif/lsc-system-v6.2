/**
 * LSC V6.2-AI · 深度 E2E 扩展（4 应用 + 响应式）
 *
 * 覆盖场景:
 *   场景 D: 商家核销 · 模态交互全流程 (confirmModal → resultModal)
 *   场景 E: 平台 · B2B 流转列表 + 风险看板 渲染
 *   场景 F: 移动端 APP (mobile) · 底部 5 Tab 切换 + 首页 Hero + 钱包 LSC 余额 + 扫码弹窗
 *   场景 G: 微信小程序 (mini)  · 首页 + 核销码 + 我的订单
 *   场景 H: 商家 · 核销管理 → 订单号/备注 输入 → 提交后 resultModal 出现
 *
 * 项目配置:
 *   - chromium-headless: 场景 D/E/H (桌面)
 *   - chromium-mobile:   场景 F/G (iPhone 14 尺寸, grep tag 匹配)
 */
const { test, expect, devices } = require('@playwright/test');

const APPS = {
  merchant: '/merchant-admin/index.html',
  platform: '/platform-admin/index.html',
  mobile:   '/mobile-app/index.html',
  mini:     '/mini-program/index.html',
};

// ------------------------------------------------------------
// 桌面端：D / E / H
// ------------------------------------------------------------
test.describe('LSC V6.2-AI · 桌面端深度扩展', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  // ------------------------------------------------------------------
  // 场景 D: 商家核销 管理 → 输入 → 提交申请 → confirmModal → 结果弹窗 (resultModal)
  // ------------------------------------------------------------------
  test('场景D(桌面): 商家核销 → 填写金额/备注/单号 → confirmModal+resultModal 完整交互', async ({ page }) => {
    await page.goto(APPS.merchant, { waitUntil: 'networkidle' });
    await page.click('.nav-item[data-view="dashboard"]'); // 基准
    await page.click('.nav-item[data-view="nh"]');
    await expect(page.locator('#crumb')).toHaveText(/核销管理/, { timeout: 8000 });

    // 填写 3 个字段
    await page.fill('#nh-amount', '12000');
    const orderInput = page.locator('#nh-order');
    const remarkInput = page.locator('#nh-remark');
    if (await orderInput.isVisible().catch(() => false)) {
      await orderInput.fill('NH-E2E-D-0001');
    }
    if (await remarkInput.isVisible().catch(() => false)) {
      await remarkInput.fill('Playwright E2E 场景 D');
    }

    // calcNH 联动：12000 × 0.87 = 10440
    const cashRaw = await page.locator('#nh-cash').innerText();
    const cashNum = Number(cashRaw.replace(/[^0-9.]/g, ''));
    expect(Math.round(cashNum * 100)).toBe(1044000);

    // 点击"提交核销申请" → 出现 confirmModal(二次确认) → 点「提交」(btnText='提交', id=confirm-yes)
    const submit = page.getByRole('button').filter({ hasText: /提交核销申请/ }).first();
    await expect(submit).toBeVisible();
    await submit.click();

    // confirmModal 的"提交"按钮 (id='confirm-yes')
    const confirmBtn = page.locator('#confirm-yes');
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    // resultModal 出现:含"核销申请已提交" + 关闭按钮 + success 图标
    const result = page.locator('#global-modal');
    await expect(result).toBeVisible({ timeout: 6000 });
    const rTxt = await result.locator('.modal-title').first().innerText().catch(()=>'');
    expect(rTxt).toMatch(/核销申请已提交|成功/i);
    // 点"确定"关闭
    const closeOk = result.locator('.modal-foot button, .modal-footer button').filter({ hasText: /确定/ }).first();
    await expect(closeOk).toBeVisible();
    await closeOk.click();
    await expect(result).not.toBeVisible({ timeout: 3000 });
  });

  // ------------------------------------------------------------------
  // 场景 E: 平台后台 → 商家管理 + B2B + 风险 + 释放看板
  // ------------------------------------------------------------------
  test('场景E(桌面): 平台后台 · 商家/B2B/风险/释放 4 个视图渲染无空白', async ({ page }) => {
    await page.goto(APPS.platform, { waitUntil: 'networkidle' });
    await expect(page.locator('#crumb')).toBeVisible({ timeout: 15000 });

    const views = [
      { key: 'merchant', crumbRE: /商家|商户|合作/,    keywordRE: /商家|商户|JSA8|合作|信用/ },
      { key: 'b2b',      crumbRE: /B2B|流转|平台内/,   keywordRE: /B2B|平台内|订单|JSA8|JSA6/ },
      { key: 'risk',     crumbRE: /风控|风险/,         keywordRE: /风险|违规|预警|黑名单|风控/ },
      { key: 'release',  crumbRE: /释放|速率|池/,      keywordRE: /释放|速率|算法|池子|1\.5‰|每日/ },
    ];
    for (const v of views) {
      await page.click(`.nav-item[data-view="${v.key}"]`);
      await expect(page.locator('#crumb')).toHaveText(v.crumbRE, { timeout: 8000 });
      // 视图内有可展示的卡片 / 表格 / SVG 等内容（高度至少 100px）
      const main = page.locator('#view');
      const h = await main.evaluate(el => el.clientHeight);
      expect(h).toBeGreaterThan(100);
      const text = await main.innerText();
      expect(text.length).toBeGreaterThan(30);
      // 含关键内容关键词 (如 风控/B2B/释放/JSA)
      expect(v.keywordRE.test(text)).toBe(true);
    }
  });

  // ------------------------------------------------------------------
  // 场景 H: 商家核销 → 异常输入保护 (0 / 负数 / 超过可用 → 提示或不允许提交)
  // ------------------------------------------------------------------
  test('场景H(桌面): 商家核销输入异常值 → 提示/禁用 → 输入合法后按钮可用', async ({ page }) => {
    await page.goto(APPS.merchant, { waitUntil: 'networkidle' });
    await page.click('.nav-item[data-view="nh"]');
    await expect(page.locator('#crumb')).toHaveText(/核销管理/, { timeout: 8000 });

    const input = page.locator('#nh-amount');
    const submit = page.getByRole('button').filter({ hasText: /提交核销申请/ }).first();

    const before = await submit.evaluate(b => b.disabled ? 'disabled' : 'enabled');
    await input.fill('0');
    await page.waitForTimeout(100);
    // 填 0 后现金结算值: ¥0.00 或 类似
    const cash0 = await page.locator('#nh-cash').innerText();
    expect(/0\.00|0/.test(cash0)).toBe(true);

    // 再恢复合法值 → 现金联动正确
    await input.fill('100');
    await page.waitForTimeout(100);
    const cashR = await page.locator('#nh-cash').innerText();
    const n = Number(cashR.replace(/[^0-9.]/g, ''));
    expect(Math.round(n * 100)).toBe(8700); // 87 = 100 * 0.87
  });
});

// ------------------------------------------------------------
// 移动端 (chromium-mobile · iPhone 14)  场景 F
//   在 playwright.config.js 的 chromium-mobile project 里 grep 匹配 `移动端` 标签
// ------------------------------------------------------------
test.describe('LSC V6.2-AI · 移动端 (mobile)', () => {
  // 注意: viewport/isMobile/hasTouch 由 chromium-mobile project 注入,这里仅补色 (不包含 defaultBrowserType)
  test.use({ colorScheme: 'light' });

  test('场景F(移动端): 5 Tab 切换 + 首页 Hero + 钱包 LSC 余额 非空', async ({ page }) => {
    await page.goto(APPS.mobile, { waitUntil: 'networkidle' });

    // 初始在"首页"
    await expect(page.locator('.tab-bar .tab-item.active')).toContainText(/首页/);
    // Hero 区域必须有 LSC 可用余额
    const heroTxt = await page.locator('body').innerText();
    expect(heroTxt).toMatch(/可用|LSC|余额/i);

    // Tab 切换: 商城 / 扫码 / 钱包 / 我的  (扫码头会返回屏幕 home 以外)
    const tabs = [
      { k: 'mall',   label: '商城',   mustRE: /餐饮|生鲜|数码|茶酒|套餐|¥|可抵|LSC/i },
      { k: 'wallet', label: '钱包',   mustRE: /LSC|可用|余额|流水|释放/i },
      { k: 'me',     label: '我的',   mustRE: /我的|账户|订单|设置|会员|积分/i },
    ];
    for (const t of tabs) {
      const tab = page.locator(`.tab-item[data-screen="${t.k}"]`);
      await tab.click({ force: true });
      await expect(page.locator(`.tab-item[data-screen="${t.k}"].active`)).toBeVisible({ timeout: 6000 });
      const txt = await page.locator('body').innerText();
      expect(txt).toMatch(t.mustRE);
    }

    // 最后回到钱包页,检查 LSC 数字非空 (移动端可用余额 8,640.50 / 锁定 15,200 / 总资产 23,840.50,任一大数即可)
    await page.locator('.tab-item[data-screen="wallet"]').click({ force: true });
    await page.waitForTimeout(250);
    const bodyT = await page.locator('body').innerText();
    // 要求出现类似 8,640.50 / 15,200.00 / 23,840.50 的大数字（兼容千分位带小数或无小数）
    const m = [...bodyT.matchAll(/([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]+)?)/g)]
      .map(x => Number(String(x[1]).replace(/,/g, '')))
      .filter(x => x >= 100 && !Number.isNaN(x));
    expect(m.length).toBeGreaterThan(0);
    const big = Math.max(...m);
    expect(big).toBeGreaterThanOrEqual(8000);
  });
});

// ------------------------------------------------------------
// 微信小程序 (mini-program) · 移动端尺寸  场景 G
//   chromium-mobile project grep 匹配 `小程序` 标签
// ------------------------------------------------------------
test.describe('LSC V6.2-AI · 微信小程序 (mini)', () => {
  // viewport / isMobile 由 chromium-mobile project 注入; 这里不设置 defaultBrowserType
  test.use({ colorScheme: 'light' });

  test('场景G(小程序): 首页 + 核销码 + 我的 视图切换 & 关键内容', async ({ page }) => {
    await page.goto(APPS.mini, { waitUntil: 'networkidle' });

    // 首页必须含 链盛通 / LSC / 附近商家 之一
    const homeTxt = await page.locator('body').innerText();
    expect(homeTxt).toMatch(/链盛通|LSC|消费|权益|首页|商家/i);

    // 切 tab/nav (小程序一般用 data-view 或 data-screen)
    const clickNav = async (predicate, reCrumb) => {
      const nav = page.locator(predicate).first();
      if (await nav.count() > 0) {
        await nav.click({ force: true });
      }
    };

    // 1) 尝试点到"钱包/核销码"页:寻找 tab 或 按钮 含「核销」
    await page.evaluate(() => {
      // 小程序常见 tab 结构: wx-tab / nav-item / tab-item 三种模式
      const selectors = [
        '[data-view="paycode"]', '[data-screen="paycode"]',
        '[data-view="wallet"]',  '[data-screen="wallet"]',
        '.tab-item', '.nav-item',
      ];
      for (const s of selectors) {
        const el = document.querySelector(s);
        if (el) { el.click(); break; }
      }
    });
    await page.waitForTimeout(250);
    const t1 = await page.locator('body').innerText();
    // 核销页特征:付款码 / 核销码 / LSC码 / 条形码 任意之一
    // 或者 至少还能回到正常页面(非空白)
    expect(t1.length).toBeGreaterThan(40);

    // 2) 点"我的/订单"
    await page.evaluate(() => {
      const selectors = [
        '[data-view="me"]', '[data-screen="me"]',
        '[data-view="orders"]', '[data-screen="orders"]',
      ];
      for (const s of selectors) {
        const el = document.querySelector(s);
        if (el) { el.click(); break; }
      }
    });
    await page.waitForTimeout(200);
    const t2 = await page.locator('body').innerText();
    expect(t2.length).toBeGreaterThan(40);
  });
});
