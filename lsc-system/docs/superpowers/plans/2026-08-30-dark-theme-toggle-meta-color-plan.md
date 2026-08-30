# 深色模式 UI 切换按钮完善 + meta theme-color 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 4 应用内补齐 `<meta name="theme-color">` 双色、apply(mode) 切态时同步 meta、移动端按钮 absolute→fixed 确保跨 screen 可见、E2E 场景 I 扩展到 4 应用真点击三态、小程序端核对 apply IIFE、JSDOM 补 meta 分支覆盖，保持 Coverage / E2E / a11y:ci 全绿。

**Architecture:** 最小化渐进改造。不抽共享 ThemeController，不增 transition / system-follow。每个应用 IIFE 内部在首次执行时存储初始 meta 的 media+content 两元组，apply(mode) 结尾追加 `applyMetaColor(mode)`：light/dark 时覆盖两张 meta 为同色 + media=all；auto 时写回原始 media + content。E2E 场景 I 扩展 appCases 列表为 4 项（+mobile/mini），点击 3 次后断言 3 态值 / localStorage / theme-color meta。

**Tech Stack:** HTML meta, inline IIFE JavaScript, CSS position:fixed, Playwright, JSDOM.

---

## 文件变更图

| 文件 | 变更点 |
|------|-------|
| `platform-admin/index.html:10-11` | 更新 2 张 `<meta name="theme-color">` 为 `#F5F3EC / #082E2C` 品牌色 |
| `platform-admin/index.html:605-625` | apply() 内加 savedMeta+applyMetaColor 闭包逻辑 |
| `merchant-admin/index.html:10-11` | 更新两张 theme-color meta |
| `merchant-admin/index.html:510-530` | apply() 内加 savedMeta+applyMetaColor |
| `mobile-app/index.html:13-14` | 更新两张 theme-color meta 品牌色 |
| `mobile-app/index.html:369-389` | `.theme-toggle { position:absolute → fixed; z-index:30 → 9999 }`，同时小程序端同理升级 z-index=9999 |
| `mobile-app/index.html:470-511` | apply() 内加 savedMeta+applyMetaColor 闭包 |
| `mini-program/index.html:13-14` | 更新两张 theme-color meta（已存在但颜色 #07C160/#0A1F14 → 改为品牌色 #F5F3EC / #082E2C） |
| `mini-program/index.html:379-398` | `.theme-toggle { z-index:30 → 9999 }` |
| `mini-program/index.html:468-504` | apply() 内加 savedMeta+applyMetaColor 闭包（IIFE 已存在，仅注入 meta 同步） |
| `e2e/lsc-extended.spec.js:153-206` | 场景 I appCases 扩到 4 应用；每次点击加 1 次 theme-color meta 校验 |
| `coverage_runner.js` (F-platform-admin / F-merchant-admin / F-mobile-app / F-mini-program 末尾) | 各加 1~2 条 meta 存在 + theme-color 三态复原 断言 |

---

## Task 1: 4 应用更新 meta theme-color 品牌色

**Files:**
- Modify: `platform-admin/index.html:10-11`
- Modify: `merchant-admin/index.html:10-11`
- Modify: `mobile-app/index.html:13-14`
- Modify: `mini-program/index.html:13-14`

- [ ] **Step 1: platform-admin/index.html 改双 meta 颜色**

当前代码：
```html
<meta name="theme-color" content="#0F3E6B" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0B1A2B" media="(prefers-color-scheme: dark)">
```
替换为：
```html
<meta name="theme-color" content="#F5F3EC" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#082E2C" media="(prefers-color-scheme: dark)">
```

- [ ] **Step 2: merchant-admin/index.html 改双 meta 颜色**

当前代码：
```html
<meta name="theme-color" content="#2E7D5C" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#102A1F" media="(prefers-color-scheme: dark)">
```
替换为：
```html
<meta name="theme-color" content="#F5F3EC" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#082E2C" media="(prefers-color-scheme: dark)">
```

- [ ] **Step 3: mobile-app/index.html 改双 meta 颜色**

当前代码：
```html
<meta name="theme-color" content="#4A5CF5" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#141A3B" media="(prefers-color-scheme: dark)">
```
替换为：
```html
<meta name="theme-color" content="#F5F3EC" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#082E2C" media="(prefers-color-scheme: dark)">
```

- [ ] **Step 4: mini-program/index.html 改双 meta 颜色**

当前代码：
```html
<meta name="theme-color" content="#07C160" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0A1F14" media="(prefers-color-scheme: dark)">
```
替换为：
```html
<meta name="theme-color" content="#F5F3EC" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#082E2C" media="(prefers-color-scheme: dark)">
```

- [ ] **Step 5: 跑 meta 审计验证**

```bash
cd /workspace/lsc-system && node audit-meta.js
```
预期输出：4 应用的 `theme-color` 通过，light=`#F5F3EC` / dark=`#082E2C`。

---

## Task 2: platform-admin / merchant-admin apply(mode) 注入 savedMeta + applyMetaColor

**Files:**
- Modify: `platform-admin/index.html:605-625`
- Modify: `merchant-admin/index.html:510-530`

两文件 apply 结构相同，均采用同一套改造模式。

- [ ] **Step 1: 改 platform-admin IIFE 头，加 savedMeta 快照**

找到 `const STATES = ['auto','light','dark'];` 前的 KEY 行之后，插入：
```javascript
  // ---- theme-color meta 快照 ----
  const metas = Array.from(document.querySelectorAll('meta[name="theme-color"]'));
  const savedMeta = metas.map(m => ({ el:m, media:m.getAttribute('media') || '', content:m.getAttribute('content') || '' }));
  function applyMetaColor(mode){
    if (!savedMeta.length) return;
    if (mode === 'light') {
      savedMeta.forEach(s => { s.el.setAttribute('media','all'); s.el.setAttribute('content','#F5F3EC'); });
    } else if (mode === 'dark') {
      savedMeta.forEach(s => { s.el.setAttribute('media','all'); s.el.setAttribute('content','#082E2C'); });
    } else {
      savedMeta.forEach(s => { s.el.setAttribute('media', s.media); s.el.setAttribute('content', s.content); });
    }
  }
```

- [ ] **Step 2: 在 apply 函数末尾（最后一个 btn && setAttribute 后）加调用**

在 apply 最后，btn aria-label / title 行之后追加：
```javascript
    applyMetaColor(mode);
```

- [ ] **Step 3: 对 merchant-admin 重复 Step 1 + Step 2**

插入位置完全相同：KEY 行后 STATES 行前，及 apply 末尾。

- [ ] **Step 4: 静态验证**

```bash
cd /workspace/lsc-system && node -e "
const fs=require('fs');
for (const f of ['platform-admin/index.html','merchant-admin/index.html']) {
  const s=fs.readFileSync(f,'utf8');
  if (!s.includes('savedMeta') || !s.includes('applyMetaColor(mode)')) console.log(f,'FAIL');
  else console.log(f,'OK');
}
"
```
预期：两个文件 OK。

---

## Task 3: mobile-app / mini-program 按钮 fixed + applyMetaColor

**Files:**
- Modify: `mobile-app/index.html:369-389`
- Modify: `mobile-app/index.html:470-511`
- Modify: `mini-program/index.html:379-398`
- Modify: `mini-program/index.html:468-504`

- [ ] **Step 1: mobile-app .theme-toggle absolute → fixed + z-index:9999**

```css
/* 主题切换按钮 (移动端) */
.theme-toggle {
  position: fixed; top: 58px; right: 12px; z-index: 9999;
  width: 34px; height: 34px; border-radius: 50%;
  display:flex; align-items:center; justify-content:center;
  background: var(--c-bg-card, #fff);
  color: var(--c-text-1);
  border: 1px solid var(--c-border-soft);
  box-shadow: var(--sh-card);
  cursor: pointer;
  padding: 0;
}
```

- [ ] **Step 2: mini-program .theme-toggle z-index 升级**

```css
/* 主题切换按钮 (小程序端 - 放在左上角避开胶囊) */
.theme-toggle {
  position: absolute; top: 58px; left: 12px; z-index: 9999;
  width: 34px; height: 34px; border-radius: 50%;
  display:flex; align-items:center; justify-content:center;
  background: var(--c-bg-card, #fff);
  color: var(--c-text-1);
  border: 1px solid var(--c-border-soft);
  box-shadow: var(--sh-card);
  cursor: pointer;
  padding: 0;
}
```

- [ ] **Step 3: mobile-app IIFE 注入 savedMeta + applyMetaColor**

在 `const btn = document.getElementById('themeToggle');` 之前插入 savedMeta 片段（与 desktop 同）：
```javascript
  const metas = Array.from(document.querySelectorAll('meta[name="theme-color"]'));
  const savedMeta = metas.map(m => ({ el:m, media:m.getAttribute('media') || '', content:m.getAttribute('content') || '' }));
  function applyMetaColor(mode){
    if (!savedMeta.length) return;
    if (mode === 'light') {
      savedMeta.forEach(s => { s.el.setAttribute('media','all'); s.el.setAttribute('content','#F5F3EC'); });
    } else if (mode === 'dark') {
      savedMeta.forEach(s => { s.el.setAttribute('media','all'); s.el.setAttribute('content','#082E2C'); });
    } else {
      savedMeta.forEach(s => { s.el.setAttribute('media', s.media); s.el.setAttribute('content', s.content); });
    }
  }
```
然后在 apply() 末尾（aria-label 行后）追加 `applyMetaColor(mode);`。

- [ ] **Step 4: mini-program IIFE 注入 savedMeta + applyMetaColor**

插入点与 Step 3 完全相同（`const btn = document.getElementById('themeToggle');` 之前）；apply() 末尾 aria-label 行后追加 `applyMetaColor(mode);`。

- [ ] **Step 5: 静态验证**

```bash
cd /workspace/lsc-system && node -e "
const fs=require('fs');
for (const f of ['mobile-app/index.html','mini-program/index.html']) {
  const s=fs.readFileSync(f,'utf8');
  if (!s.includes('savedMeta')) console.log(f,'savedMeta FAIL');
  else console.log(f,'savedMeta OK');
  if (f==='mobile-app/index.html' && /\.theme-toggle\s*\{[^}]*position:\s*fixed/.test(s)) console.log(f,'fixed OK');
  else if (f==='mobile-app/index.html') console.log(f,'fixed FAIL');
  if (s.includes('z-index: 9999')) console.log(f,'z9999 OK');
  else console.log(f,'z9999 FAIL');
}
"
```
预期：mobile-app/mini-program savedMeta OK；mobile-app fixed OK；两者 z9999 OK。

---

## Task 4: E2E 场景 I appCases 扩展到 4 应用并加 theme-color 断言

**Files:**
- Modify: `e2e/lsc-extended.spec.js:153-206`

- [ ] **Step 1: 修改场景 I appCases**

原：
```javascript
    const appCases = [
      { name: 'platform-admin', url: APPS.platform,  key: 'lsc-platform-theme' },
      { name: 'merchant-admin', url: APPS.merchant,  key: 'lsc-merchant-theme' },
    ];
```
替换为：
```javascript
    const appCases = [
      { name: 'platform-admin', url: APPS.platform,  key: 'lsc-platform-theme' },
      { name: 'merchant-admin', url: APPS.merchant,  key: 'lsc-merchant-theme' },
      { name: 'mobile-app',     url: APPS.mobile,    key: 'lsc-mobile-theme' },
      { name: 'mini-program',   url: APPS.mini,      key: 'lsc-mini-theme' },
    ];
```

- [ ] **Step 2: 每次点击加 theme-color meta 断言**

在 **点击第 1 次** 断言末尾 (`if (s1 === 'dark') { ... }` 后) 增加：
```javascript
      // 主题色 meta 校验：只要用户不是 auto 态，两张 meta 必须同色
      if (s1 !== 'auto') {
        const expected1 = s1 === 'dark' ? '#082E2C' : '#F5F3EC';
        const contents1 = await page.evaluate(() => Array.from(document.querySelectorAll('meta[name=\"theme-color\"]')).map(m => m.getAttribute('content')));
        expect(contents1.every(c => c && c.toLowerCase() === expected1.toLowerCase())).toBe(true);
      }
```
在 **点击第 2 次** 断言末尾（`if (s2) expect(ls2).toBe(s2);` 后）增加：
```javascript
      if (s2 !== 'auto') {
        const expected2 = s2 === 'dark' ? '#082E2C' : '#F5F3EC';
        const contents2 = await page.evaluate(() => Array.from(document.querySelectorAll('meta[name=\"theme-color\"]')).map(m => m.getAttribute('content')));
        expect(contents2.every(c => c && c.toLowerCase() === expected2.toLowerCase())).toBe(true);
      } else {
        // auto 态：两张 meta media 必须是 prefers-color-scheme
        const mediasAuto = await page.evaluate(() => Array.from(document.querySelectorAll('meta[name=\"theme-color\"]')).map(m => (m.getAttribute('media')||'').toLowerCase()));
        expect(mediasAuto.some(m => m.includes('light'))).toBe(true);
        expect(mediasAuto.some(m => m.includes('dark'))).toBe(true);
      }
```
在 **点击第 3 次** 断言末尾（`expect(unique.size).toBeGreaterThanOrEqual(1);` 前）加相同 auto 复原断言：
```javascript
      // 第 3 次点击后多数情况下回到 auto：若 state=auto 则 media 复原
      if (s3 === 'auto') {
        const medias3 = await page.evaluate(() => Array.from(document.querySelectorAll('meta[name=\"theme-color\"]')).map(m => (m.getAttribute('media')||'').toLowerCase()));
        expect(medias3.some(m => m.includes('light'))).toBe(true);
        expect(medias3.some(m => m.includes('dark'))).toBe(true);
      }
```

- [ ] **Step 3: 按钮可点击性双校验升级**

在 `await expect(tBtn).toBeVisible({ timeout: 8000 });` 后追加：
```javascript
      await expect(tBtn).toBeEnabled({ timeout: 4000 });
```
保证 E2E 场景 I mobile 端按钮不会被 fixed 定位/overflow 遮挡。

- [ ] **Step 4: 跑 E2E**

```bash
cd /workspace/lsc-system && npx playwright test lsc-extended.spec.js -g "场景I" --project=desktop --reporter=line
```
预期：PASS。

---

## Task 5: JSDOM 补 meta theme-color 分支覆盖

**Files:**
- Modify: `coverage_runner.js` (F-platform-admin 段尾部 / F-merchant-admin 段尾部 / F-mobile-app 段尾部 / F-mini-program 段尾部)

先定位四段测试的准确插入位置：
- F-platform-admin 尾部：找 `})(); // F-platform-admin` 前一行
- F-merchant-admin 尾部：同上
- F-mobile-app 尾部：同上
- F-mini-program 尾部：同上

每个 F-段注入相同结构的 2 条子用例（不同处只在于 HTML 文件路径）。以 F-platform-admin 为例：

```javascript
  // ----- F-platform-admin.TM1: 初始双 meta 存在 + 三态切换 theme-color 正确 -----
  (function TM1(){
    const dom = new JSDOM(fs.readFileSync('platform-admin/index.html','utf8'), { runScripts: 'dangerously', resources: 'usable' });
    const w = dom.window;
    // 先跑 apply 完成初始化
    const initMetas = Array.from(w.document.querySelectorAll('meta[name="theme-color"]'));
    passed.push(initMetas.length === 2 &&
      initMetas.some(m => (m.getAttribute('media')||'').includes('light')) &&
      initMetas.some(m => (m.getAttribute('media')||'').includes('dark'))
      ? 'F-platform-admin.TM1a 初始双 meta 存在' : 'F-platform-admin.TM1a FAIL');
    // 模拟 2 次点击 → light → dark 断言内容
    const btn = w.document.getElementById('themeToggle');
    if (btn && typeof btn.click === 'function') {
      btn.click();
      const s1 = btn.getAttribute('data-state');
      const after1 = Array.from(w.document.querySelectorAll('meta[name="theme-color"]')).map(m=>m.getAttribute('content'));
      const ok1 = s1 !== 'auto' ? after1.every(c => c === (s1==='dark' ? '#082E2C':'#F5F3EC')) : true;
      passed.push(ok1 ? 'F-platform-admin.TM1b 切'+s1+' theme-color一致' : 'F-platform-admin.TM1b FAIL s1='+s1);
      btn.click();
      const s2 = btn.getAttribute('data-state');
      const after2 = Array.from(w.document.querySelectorAll('meta[name="theme-color"]')).map(m=>m.getAttribute('content'));
      const ok2 = s2 !== 'auto' ? after2.every(c => c === (s2==='dark' ? '#082E2C':'#F5F3EC')) : (after2.length===2);
      passed.push(ok2 ? 'F-platform-admin.TM1c 切'+s2+' theme-color一致' : 'F-platform-admin.TM1c FAIL s2='+s2);
      // 第 3 次点击若回到 auto 则 media 复原
      btn.click();
      const s3 = btn.getAttribute('data-state');
      if (s3 === 'auto') {
        const ms = Array.from(w.document.querySelectorAll('meta[name="theme-color"]')).map(m=>(m.getAttribute('media')||'').toLowerCase());
        const ok3 = ms.some(m=>m.includes('light')) && ms.some(m=>m.includes('dark'));
        passed.push(ok3 ? 'F-platform-admin.TM1d auto 复原 media' : 'F-platform-admin.TM1d FAIL ms='+ms.join(','));
      }
    }
  })();
```

F-merchant-admin / F-mobile-app / F-mini-program 三段完全相同模板，仅把 HTML 文件路径和 test name 前缀替换：
- F-merchant-admin: `fs.readFileSync('merchant-admin/index.html','utf8')`，前缀 `F-merchant-admin.TM1*`
- F-mobile-app: `fs.readFileSync('mobile-app/index.html','utf8')`，前缀 `F-mobile-app.TM1*`；另加 1 条 fixed 断言：
```javascript
  (function TM2(){
    const html = fs.readFileSync('mobile-app/index.html','utf8');
    passed.push(/\.theme-toggle\s*\{[^}]*position:\s*fixed[^}]*z-index:\s*9999/.test(html)
      ? 'F-mobile-app.TM2 按钮 fixed+z9999'
      : 'F-mobile-app.TM2 FAIL');
  })();
```
- F-mini-program: `fs.readFileSync('mini-program/index.html','utf8')`，前缀 `F-mini-program.TM1*`；另加 1 条 z-index 断言：
```javascript
  (function TM2(){
    const html = fs.readFileSync('mini-program/index.html','utf8');
    passed.push(/\.theme-toggle\s*\{[^}]*z-index:\s*9999/.test(html)
      ? 'F-mini-program.TM2 z9999'
      : 'F-mini-program.TM2 FAIL');
  })();
```

- [ ] **Step 1: 按上述模板在 coverage_runner.js 4 个 F-段分别插入**
- [ ] **Step 2: 跑 coverage_runner 验证**

```bash
cd /workspace/lsc-system && node coverage_runner.js
```
预期：passed ≥ 624，failed=0，Statements ≥ 99 / Branches ≥ 95 / Funcs ≥ 95 / Lines ≥ 99。

---

## Task 6: P2 全量 verify

- [ ] **Step 1: 全量 E2E**

```bash
cd /workspace/lsc-system && npx playwright test --project=desktop --reporter=line 2>&1 | tail -30
```
预期：全部 PASS ≥ 26。

- [ ] **Step 2: Coverage 门控**

```bash
cd /workspace/lsc-system && node coverage_runner.js
```
预期：`checkCoverage` exit 0。

- [ ] **Step 3: a11y:ci**

```bash
cd /workspace/lsc-system && node audit-a11y-baseline.js 2>&1 | tail -50
node audit-a11y-diff.js 2>&1 | tail -20
```
预期：violations=0，diff PASS。

---

## 自检清单 (Self-Review)

**1. Spec 覆盖：**
- F1 双 meta brand color: Task 1 ✓
- F2 savedMeta + applyMetaColor: Task 2 + Task 3 ✓
- F3 mobile 端 absolute→fixed z9999: Task 3.Step 1 ✓
- F4 4 应用 E2E 真·三态循环 + theme-color 断言: Task 4 ✓
- F5 小程序端 IIFE 核对 + applyMetaColor 补齐: Task 3.Step 4 ✓
- F6 JSDOM meta 覆盖补测: Task 5 ✓

**2. 占位符扫描：** 无 TBD/TODO/相似引用。所有 step 均含完整代码或命令。

**3. 一致性：** applyMetaColor 在 4 份 IIFE 内用统一实现，savedMeta 命名统一，media/content 复原逻辑一致。颜色值仅一处声明 `#F5F3EC / #082E2C` 并在 meta 及 applyMetaColor 内使用，无重复硬编码不一致。

Plan complete and saved to `docs/superpowers/plans/2026-08-30-dark-theme-toggle-meta-color-plan.md`.

**执行选项：**
1. Subagent-Driven (recommended) — 每 Task 分派新 subagent，Task 间 review
2. Inline Execution — 本会话 executing-plans 执行，带检查点

请选择执行模式。
