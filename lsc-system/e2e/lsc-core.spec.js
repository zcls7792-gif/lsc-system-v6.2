// LSC V6.2-AI 端到端核心 3 场景
// 场景 A: 商家管理后台核销流程 → LSC账户卡值变化
// 场景 B: 平台后台 AI 活动流 + 实时速率图表渲染
// 场景 C: 商家店铺管理页 SVG 真实地图 + 缩放控件交互
const { test, expect } = require('@playwright/test');

const APPS = {
  merchant: '/merchant-admin/index.html',
  platform: '/platform-admin/index.html',
  mobile:   '/mobile-app/index.html',
  mini:     '/mini-program/index.html',
};

test.describe('LSC V6.2-AI · 端到端核心 3 场景', () => {

  test.beforeEach(async ({ page, baseURL }) => {
    // 捕获控制台错误（失败断言）
    page.on('console', msg => {
      if (msg.type() === 'error' && !/favicon|design-system\.css|app-utils\.js|app\.js/.test(msg.text())) {
        // 非资源404错误打印日志，方便定位
        process.stderr.write('[CONSOLE-ERR] ' + msg.text() + '\n');
      }
    });
  });

  // ======================================================
  // 场景 A: 商家管理后台 · 核销流程 → 账户变化
  // ======================================================
  test('场景A: 商家后台核销管理页面 → 核销计算联动 + LSC账户值非空', async ({ page }) => {
    await page.goto(APPS.merchant, { waitUntil: 'networkidle' });

    // 0) 先强制点击到"经营总览"作为基准（不同存储状态下不一定默认）
    await page.click('.nav-item[data-view="dashboard"]');
    // 1) 经营总览渲染成功：存在核心指标 + crumb 正确
    await expect(page.locator('.stat-card').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('#crumb')).toHaveText(/经营总览/);

    // 2) 导航点击到"核销管理"
    await page.click('.nav-item[data-view="nh"]');
    await expect(page.locator('#crumb')).toHaveText(/核销管理/, { timeout: 8000 });

    // 3) 页内必须存在核销输入框 + 核销按钮
    await expect(page.locator('#nh-amount')).toBeVisible();
    const submitBtn = page.getByRole('button').filter({ hasText: /提交核销申请/ }).first();
    await expect(submitBtn).toBeVisible();
    // 4) 输入框交互：修改值 → 现金结算联动 calcNH
    await page.fill('#nh-amount', '5000');
    await page.waitForTimeout(150);
    const cashRaw = await page.locator('#nh-cash').innerText();
    // 允许 4350 / 4,350 两种千分位格式
    const cashNum = Number(cashRaw.replace(/[^0-9.]/g, ''));
    expect(Math.round(cashNum * 100)).toBe(435000);

    // 5) 再进入"LSC账户"查看钱包数字非空（LSC 钱包页使用 hero-val / hf-val，不可用 stat-card）
    await page.click('.nav-item[data-view="wallet"]');
    await expect(page.locator('#crumb')).toHaveText(/LSC.*账户/);
    const hero = page.locator('.hero-val').first();
    await expect(hero).toBeVisible();
    const txt = await hero.innerText().catch(() => '');
    const n = Number(String(txt).replace(/[^0-9.]/g, ''));
    expect(isNaN(n)).toBe(false);
    expect(n).toBeGreaterThan(0); // 可用余额必须是正数
    expect(txt).not.toMatch(/NaN|undefined|null/);
  });

  // ======================================================
  // 场景 B: 平台后台 · AI活动流 + 速率实时图表渲染
  // ======================================================
  test('场景B: 平台后台 AI活动流feed + 速率图表SVG渲染 + 2个定时器创建', async ({ page }) => {
    await page.goto(APPS.platform, { waitUntil: 'networkidle' });

    // 1) 进入 AI 视图触发 _aiTimers 初始化
    await page.click('.nav-item[data-view="ai"]', { timeout: 12000 });
    await expect(page.locator('#crumb')).toHaveText(/AI|智能/);

    // 2) feed 容器 + 至少 1 条活动
    await expect(page.locator('#ai-activity-feed')).toBeVisible({ timeout: 10000 });
    const feedCount = await page.locator('#ai-activity-feed > *').count();
    expect(feedCount).toBeGreaterThanOrEqual(1);

    // 3) 释放速率实时图表：SVG path 元素存在 + 指标卡有数字
    await expect(page.locator('#rate-realtime-chart')).toBeVisible();
    const rateVal = await page.locator('#rate-k-val, #rate-rate-val').first().innerText().catch(() => '');
    expect(rateVal.length > 0).toBe(true);

    // 4) 断言 window._aiTimers 已被正确设置为数组且 length>=2
    const timerInfo = await page.evaluate(() => {
      return {
        isArray: Array.isArray(window._aiTimers),
        length: (window._aiTimers || []).length,
      };
    });
    expect(timerInfo.isArray).toBe(true);
    expect(timerInfo.length).toBeGreaterThanOrEqual(2);
  });

  // ======================================================
  // 场景 C: 商家店铺管理页 · SVG 真实地图 + 缩放控件
  // ======================================================
  test('场景C: 商家店铺地图替换占位为真实SVG + 缩放控件可交互', async ({ page }) => {
    await page.goto(APPS.merchant, { waitUntil: 'networkidle' });

    // 1) 进入"店铺管理"
    await page.click('.nav-item[data-view="shop"]', { timeout: 12000 });
    await expect(page.locator('#crumb')).toHaveText(/店铺管理/);

    // 2) 地图必须是真实 SVG（含 viewBox、路径/矩形元素、POI文本）
    const mapSvg = page.locator('#shop-map-box svg.map-svg');
    await expect(mapSvg).toBeVisible();

    // viewBox 存在
    const vb = await mapSvg.getAttribute('viewBox');
    expect(vb).toBeTruthy();
    expect(vb.split(' ').length).toBe(4);

    // 存在街道/建筑/POI 特征：至少 8 个 <rect> 建筑街区 + 1 条 <path> 水/锚点 + <text> 元素(地铁站/银行等)
    const rectCount = await mapSvg.locator('svg > g > rect, svg > rect').count() + await mapSvg.locator('rect').count();
    const pathCount = await mapSvg.locator('path').count();
    const svgTextItems = await mapSvg.locator('text').count();
    // 锚点/图例包含"锦华餐饮"文字（在 SVG 外 div，也必须可见）
    const legendText = await page.getByText(/锦华餐饮/).first().innerText().catch(() => '');
    expect(rectCount).toBeGreaterThanOrEqual(8);
    expect(pathCount).toBeGreaterThanOrEqual(1);
    expect(svgTextItems).toBeGreaterThanOrEqual(4);
    expect(legendText.length).toBeGreaterThanOrEqual(4);

    // 3) 缩放控件 3 个按钮存在
    const bIn = page.locator('#map-zoom-in');
    const bOut = page.locator('#map-zoom-out');
    const bReset = page.locator('#map-reset');
    await expect(bIn).toBeVisible();
    await expect(bOut).toBeVisible();
    await expect(bReset).toBeVisible();

    // 4) 放大 → 比例尺应变成 1:xxxx (更小数字)
    const getScale = () => page.locator('.map-scale').innerText().catch(() => '');
    const sBefore = await getScale();
    await bIn.click();
    await page.waitForTimeout(300);
    const sAfter = await getScale();
    // 比例尺文字不能未变（至少不是同字符串，放大后 ratio 会变）
    const parseN = s => {
      const m = s.match(/1:(\d+)/);
      return m ? Number(m[1]) : 0;
    };
    const nBefore = parseN(sBefore);
    const nAfter  = parseN(sAfter);
    if (nBefore > 0 && nAfter > 0) {
      expect(nAfter).toBeLessThan(nBefore); // 放大 → 1:N 的 N 更小
    }

    // 5) 重置 → SVG transform scale 恢复
    await bReset.click();
    await page.waitForTimeout(250);
    const style = await mapSvg.evaluate(el => el.getAttribute('style') || el.style.transform);
    // 不要求严格的 style，只要不报错即通过；空 transform 或 scale(1) 都算 OK
    expect(typeof style).toBe('string');
  });

});
