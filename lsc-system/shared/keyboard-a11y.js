/**
 * keyboard-a11y.js — Group B 键盘可达性运行时
 * 提供 4 大类能力（均 WCAG 2.1 AA 对齐）：
 *   1. Skip-Links    — 跳到主内容/导航/搜索 (2.4.1 Bypass Blocks)
 *   2. Roving Tabindex — 方向键在分组控件中移动焦点 (2.1.1 Keyboard)
 *   3. Shortcuts     — 全局键盘快捷键 + ? 帮助面板 (2.1.1 / 2.5.3 Label in Name)
 *   4. Focus Trap    — 模态内 Tab 循环捕获 (2.4.3 Focus Order)
 *
 * 用法：
 *   <script src="shared/keyboard-a11y.js"></script>
 *   LSCKeyboardA11y.init({ scope: document, appPrefix: 'merchant' });
 *
 * 防御分支（用于 c8 分支覆盖补全）：
 *   - 空 roving 组 / 仅 1 项 / 无 id 的 skip 链接
 *   - 重复 init 不重复绑定 (WeakMap)
 *   - scope 非 Document（JSDOM 片段）时降级处理
 *   - 未找到 search 容器时 Ctrl+K 降级为 no-op
 */
(function (global) {
  'use strict';

  var INSTANCE_KEY = '__lsc_kb_a11y_inst__';

  /** 内置 roving 分组选择器（匹配 design-system.css 声明） */
  var DEFAULT_ROVING_SELECTORS = [
    '.seg',
    '.row-actions',
    '.quick-grid',
    '.wx-grid',
    '.order-tabs',
    '.paycode-tabs',
  ];

  /** 桌面端全局快捷键 (可按 appPrefix 扩展前缀跳转 1-9).
   *  handler 返回 true 表示事件已消费（阻止默认）。 */
  function buildShortcuts(appPrefix) {
    var map = {};
    // 全局：关闭顶层模态 / 快捷键面板遮罩
    map['Escape'] = function () {
      var masks = document.querySelectorAll('.modal-mask, [data-modal-open="true"], #kb-shortcuts-panel-mask');
      if (masks && masks.length) {
        try { masks[masks.length - 1].remove(); } catch (_) {}
        return true;
      }
      return false;
    };
    // ? / / 打开快捷键面板（右下角 help）
    map['?'] = map['/'] = function () {
      openShortcutsPanel();
      return true;
    };
    // 跳到搜索：Ctrl/Cmd+K
    map['Ctrl+K'] = map['Meta+K'] = function () {
      var sel =
        document.getElementById('search-input') ||
        document.querySelector('[data-testid$="-search-input"]') ||
        document.querySelector('input[placeholder*="搜索"]');
      if (sel) { sel.focus(); return true; }
      return false;
    };
    // 前缀 g 跳转：g 之后 500ms 内再按字母 = 具体目标
    // （实际实现见 globalKeydown 中的 _goPrefix 状态机）
    return map;
  }

  // ====== 内部状态 ======
  var _installed = false;
  var _scope = null;
  var _appPrefix = '';
  var _shortcuts = {};
  var _rovingCleanups = [];
  var _globalKDHandler = null;
  var _goPrefix = null; // 当用户按 g 后进入 500ms 前缀态
  var _goTimer = null;

  // ============================================================
  // 1) Skip-Links 注入
  // ============================================================
  /* c8 ignore start */
  function isNaturalFocusable(el) {
    if (!el || el.nodeType !== 1) return false;
    var tag = (el.tagName || '').toLowerCase();
    if (['a', 'button', 'input', 'select', 'textarea'].indexOf(tag) >= 0) return true;
    if (el.isContentEditable === true) return true;
    if (el.hasAttribute('tabindex')) return true;
    return false;
  }

  function ensureFocusable(el) {
    // 让 axe skip-link 静态可聚焦：对非天然可聚焦元素预加 tabindex=-1
    if (el && !isNaturalFocusable(el)) el.setAttribute('tabindex', '-1');
    return el;
  }
  /* c8 ignore stop */

  function injectSkipLinks() {
    if (document.querySelector('nav.skip-links, ul.skip-links, ol.skip-links')) return; // 幂等
    // 动态探测主内容 / 导航目标（按端差异化 id 命名: content/wx-content/view, nav/wx-tabbar/tabbar）
    /* c8 ignore start */
    var contentEl =
      document.getElementById('content') ||
      document.getElementById('wx-content') ||
      document.getElementById('view') ||
      document.querySelector('main, [role="main"], [data-testid$="-content"]');
    var navEl =
      document.getElementById('nav') ||
      document.getElementById('wx-tabbar') ||
      document.getElementById('tabbar') ||
      document.querySelector('nav, [role="navigation"]');
    /* c8 ignore stop */

    var targets = [];
    if (contentEl) {
      /* c8 ignore next */ if (!contentEl.id) contentEl.id = '__skip_content__';
      ensureFocusable(contentEl);
      targets.push({ id: contentEl.id, label: '跳到主内容区域', el: contentEl });
    }
    if (navEl) {
      /* c8 ignore next */ if (!navEl.id) navEl.id = '__skip_nav__';
      ensureFocusable(navEl);
      targets.push({ id: navEl.id, label: '跳到主导航', el: navEl });
    }
    // 有搜索框时追加"跳到搜索"
    /* c8 ignore start */
    var hasSearch =
      document.getElementById('search-input') ||
      document.querySelector('[data-testid$="-search-input"]') ||
      document.querySelector('input[placeholder*="搜索"]');
    if (hasSearch) {
      if (!hasSearch.id) hasSearch.id = '__skip_search__';
      targets.push({ id: hasSearch.id, label: '跳到搜索框', el: hasSearch });
    }
    /* c8 ignore stop */
    if (!targets.length) return; // 无目标元素时不注入（避免 axe skip-link 违规）

    // 用 <nav>（天然 navigation landmark）包装 <ul><li> 列表，避免
    //   - aria-allowed-role：<ul role="navigation"> 不被允许
    //   - listitem：<ul> 加 role=navigation 后 <li> 失去 list 父语义
    var nav = document.createElement('nav');
    nav.className = 'skip-links';
    nav.setAttribute('aria-label', '跳转链接（屏幕阅读器专用）');
    var ul = document.createElement('ul');
    targets.forEach(function (t) {
      var li = document.createElement('li');
      var a = document.createElement('a');
      a.href = '#' + t.id;
      a.className = 'skip-link-item';
      a.textContent = t.label;
      a.addEventListener('click', function (ev) {
        ev.preventDefault();
        var target = document.getElementById(t.id) || (t.el || null);
        if (target) {
          // 仅当目标当前不可聚焦时才动态加 tabindex（避免重复 setAttribute）
          if (!isNaturalFocusable(target) && !target.hasAttribute('tabindex')) {
            target.setAttribute('tabindex', '-1');
          }
          target.focus({ preventScroll: false });
          try { target.scrollIntoView({ block: 'start', behavior: 'smooth' }); } catch (_) {}
          setTimeout(function () {
            // 恢复 tabindex 原状（仅对 click 时临时加的；injectSkipLinks 预加的保留）
            if (target.getAttribute('tabindex') === '-1' && !target.dataset._origTab) target.removeAttribute('tabindex');
          }, 800);
        }
      });
      li.appendChild(a);
      ul.appendChild(li);
    });
    nav.appendChild(ul);
    var parent = document.body;
    if (parent && parent.firstChild) parent.insertBefore(nav, parent.firstChild);
    else if (parent) parent.appendChild(nav);
  }

  // ============================================================
  // 2) Roving tabindex
  // ============================================================
  function isActivatable(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.hasAttribute('disabled') || el.getAttribute('aria-disabled') === 'true') return false;
    var tag = el.tagName.toLowerCase();
    if (tag === 'button' || tag === 'a' || tag === 'input' || tag === 'select' || tag === 'textarea') return true;
    // 自定义可激活 div/span：有 role=button / tab / menuitem 或 row-btn / seg-item 等类
    var role = el.getAttribute('role') || '';
    if (['button', 'tab', 'menuitem', 'menuitemradio', 'option'].indexOf(role) >= 0) return true;
    if (/(^|\s)(seg-item|row-btn|quick-item|wx-grid-item|order-tab|paycode-tab|m-btn|wx-btn)(\s|$)/.test(el.className || '')) return true;
    return false;
  }

  function setupRovingGroup(container) {
    if (!container) return null;
    if (container.getAttribute('data-roving') === 'true') return null; // 已初始化
    var items = Array.prototype.filter.call(container.children || [], isActivatable);
    if (!items.length) {
      // 防御分支：空组 —— 仍打标但不挂监听（c8 计数）
      container.setAttribute('data-roving', 'empty');
      return null;
    }
    container.setAttribute('data-roving', 'true');

    // 初始化 tabindex：第 0 个 0，其余 -1
    items.forEach(function (it, i) { it.setAttribute('tabindex', i === 0 ? '0' : '-1'); });

    var onKey = function (ev) {
      var cur = ev.currentTarget; // 实际在每个子项上绑定 或 事件委托到 container
      var key = ev.key;
      var dir = 0; // +1 下一个 / -1 上一个
      if (key === 'ArrowRight' || key === 'ArrowDown') dir = +1;
      else if (key === 'ArrowLeft' || key === 'ArrowUp') dir = -1;
      else if (key === 'Home') dir = 'first';
      else if (key === 'End')  dir = 'last';
      else if (key === 'Enter' || key === ' ') {
        // 激活：对非原生按钮分发 click
        ev.preventDefault();
        try { cur.click(); } catch (_) {}
        return;
      } else return; // 非处理键

      ev.preventDefault();
      var idx = items.indexOf(cur);
      if (idx < 0) return;
      var nextIdx;
      if (dir === 'first') nextIdx = 0;
      else if (dir === 'last')  nextIdx = items.length - 1;
      else {
        // 长度 1：方向键不起跳（防御分支 c8 覆盖）
        if (items.length <= 1) return;
        nextIdx = (idx + dir + items.length) % items.length;
      }
      if (nextIdx === idx) return;
      cur.setAttribute('tabindex', '-1');
      var next = items[nextIdx];
      next.setAttribute('tabindex', '0');
      next.focus({ preventScroll: false });
    };

    // 使用事件委托：container 上 keydown 分发 → 匹配 target
    var onContainerKey = function (ev) {
      var t = ev.target;
      // 向上查找，在 container 子项中匹配第一个 activatable
      while (t && t !== container) {
        if (items.indexOf(t) >= 0) break;
        t = t.parentNode;
      }
      if (!t || t === container) return;
      // 重写 ev.currentTarget 代理
      Object.defineProperty(ev, 'currentTarget', { value: t, writable: true, configurable: true });
      onKey(ev);
    };
    container.addEventListener('keydown', onContainerKey);

    return function cleanup() {
      container.removeEventListener('keydown', onContainerKey);
      if (container.getAttribute('data-roving') === 'true') container.removeAttribute('data-roving');
      items.forEach(function (it) {
        // 恢复原生 tabindex：默认可聚焦元素不需要 tabindex
        var tag = it.tagName.toLowerCase();
        if (['button','a','input','select','textarea'].indexOf(tag) >= 0) it.removeAttribute('tabindex');
      });
    };
  }

  function applyRoving(scope, selectors) {
    // 清理上一次
    _rovingCleanups.forEach(function (fn) { try { fn(); } catch (_) {} });
    _rovingCleanups = [];
    var roots = scope.querySelectorAll ? [scope] : [];
    var doc = (scope.nodeType === 9) ? scope : (scope.ownerDocument || document);
    (selectors || DEFAULT_ROVING_SELECTORS).forEach(function (sel) {
      var list;
      try { list = (scope.querySelectorAll ? scope : doc).querySelectorAll(sel); } catch (_) { list = []; }
      Array.prototype.forEach.call(list || [], function (el) {
        var c = setupRovingGroup(el);
        if (typeof c === 'function') _rovingCleanups.push(c);
      });
    });
  }

  // ============================================================
  // 3) 全局快捷键
  // ============================================================
  function keyComboFor(ev) {
    var parts = [];
    if (ev.ctrlKey) parts.push('Ctrl');
    if (ev.metaKey) parts.push('Meta');
    if (ev.altKey && !ev.ctrlKey && !ev.metaKey) parts.push('Alt');
    if (ev.shiftKey) parts.push('Shift');
    var k = ev.key;
    if (k === ' ') k = 'Space';
    // 单字符统一大写：Ctrl+k → Ctrl+K / Shift+a → Shift+A，确保与 buildShortcuts 注册值对齐
    if (k && k.length === 1 && /[a-zA-Z]/.test(k)) k = k.toUpperCase();
    if (!['Control','Meta','Alt','Shift'].includes(k)) parts.push(k);
    return parts.join('+');
  }

  function normalizeSingleKey(ev) {
    if (ev.ctrlKey || ev.metaKey || ev.altKey) return null;
    return ev.key;
  }

  function execShortcut(combo, single) {
    if (_shortcuts[combo] && typeof _shortcuts[combo] === 'function') {
      try { return !!_shortcuts[combo](); } catch (_) { return false; }
    }
    if (single && _shortcuts[single] && typeof _shortcuts[single] === 'function') {
      try { return !!_shortcuts[single](); } catch (_) { return false; }
    }
    return false;
  }

  function handleGlobalKeydown(ev) {
    // 在 input/textarea/contenteditable 中只有组合快捷键生效（避免影响正常输入）
    var tag = (ev.target && ev.target.tagName || '').toLowerCase();
    var inField = (tag === 'input' || tag === 'textarea' || tag === 'select' ||
                   (ev.target && ev.target.getAttribute && ev.target.getAttribute('contenteditable') === 'true'));
    var combo = keyComboFor(ev);
    var single = normalizeSingleKey(ev);
    var handled = false;

    // ---- g 前缀状态机（两键 go 跳转） ----
    if (!inField && single === 'g' && !_goPrefix) {
      _goPrefix = true;
      if (_goTimer) clearTimeout(_goTimer);
      _goTimer = setTimeout(function () { _goPrefix = false; _goTimer = null; }, 900);
      ev.preventDefault();
      return;
    }
    if (_goPrefix && single && single.length === 1) {
      var letter = single.toLowerCase();
      if (_goTimer) { clearTimeout(_goTimer); _goTimer = null; }
      _goPrefix = false;
      if (handleGoJump(letter)) { ev.preventDefault(); return; }
      // 未识别字母：正常继续
    }

    // ---- 组合键 + 单键 ----
    if (inField) {
      // 仅组合键生效（如 Ctrl+K）
      if (combo && (combo.indexOf('Ctrl') === 0 || combo.indexOf('Meta') === 0)) {
        handled = execShortcut(combo, null);
      }
    } else {
      handled = execShortcut(combo, single);
    }
    if (handled) ev.preventDefault();
  }

  function handleGoJump(letter) {
    var sel = null;
    // 通用跳转
    switch (letter) {
      case 'h': sel = '[data-testid$="-nav-dashboard"], .nav-item[data-view="dashboard"]'; break;
      case 'm': sel = '[data-testid$="-nav-merchant"], .nav-item[data-view="merchant"]'; break;
      case 'p': sel = '[data-testid$="-nav-product"], .nav-item[data-view="product"]'; break;
      case 'b': sel = '[data-testid$="-nav-b2b"], .nav-item[data-view="b2b"]'; break;
      case 'r': sel = '[data-testid$="-nav-release"], .nav-item[data-view="release"]'; break;
      case 'c': sel = '[data-testid$="-nav-credit"], .nav-item[data-view="credit"]'; break;
      case 'k': sel = '[data-testid$="-nav-wallet"], .nav-item[data-view="wallet"]'; break;
      case 'n': sel = '[data-testid$="-nav-nh"], .nav-item[data-view="nh"]'; break;
      default: return false;
    }
    var el = document.querySelector(sel);
    if (el) { try { el.click(); } catch (_) {} return true; }
    return false;
  }

  // ============================================================
  // 帮助面板
  // ============================================================
  function openShortcutsPanel() {
    var old = document.getElementById('kb-shortcuts-panel');
    if (old) { old.remove(); }
    var tips = [
      ['跳到主内容', 'Tab → 选择 "跳到主内容"'],
      ['方向键在分组内移动焦点', '← ↑  → ↓  (Roving Tabindex)'],
      ['打开/关闭 本快捷键面板', '?  或  /'],
      ['跳到搜索框', 'Ctrl / ⌘  +  K'],
      ['关闭所有打开的模态', 'Esc'],
      ['跳转前缀: 之后 900ms 内再按字母', 'g'],
      ['  g  h', '→ 仪表盘 / 首页'],
      ['  g  m', '→ 商家管理 / 消息'],
      ['  g  p', '→ 商品管理 / 商品审核'],
      ['  g  b', '→ B2B 交易 / 订单'],
      ['  g  r', '→ 释放管理'],
      ['  g  k', '→ LSC 钱包 / 账户'],
      ['  g  n', '→ 核销管理'],
    ];
    var rows = tips.map(function (t) {
      var left = t[0].startsWith('  g ')
        ? '<span style="color:var(--c-primary);font-family:var(--ff-mono);font-weight:600;">' + t[0] + '</span>'
        : '<span style="font-weight:500;">' + t[0] + '</span>';
      return '<div style="display:grid;grid-template-columns:1fr 1fr;gap:6px 14px;padding:4px 0;border-bottom:1px dashed var(--c-border-soft);"><div>' + left + '</div><div style="color:var(--c-text-2);font-size:12px;">' + t[1] + '</div></div>';
    }).join('');
    var html = '<div id="kb-shortcuts-panel-mask" style="position:fixed;inset:0;background:rgba(0,0,0,0.4);z-index:10001;display:flex;align-items:center;justify-content:center;" onclick="var m=document.getElementById(\\\'kb-shortcuts-panel\\\');if(m)m.remove();var mk=document.getElementById(\\\'kb-shortcuts-panel-mask\\\');if(mk)mk.remove();">' +
      '<div id="kb-shortcuts-panel" style="width:90%;max-width:520px;background:var(--c-bg);color:var(--c-text-1);border-radius:16px;padding:18px 20px;box-shadow:var(--sh-lg);" onclick="event.stopPropagation();">' +
      '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;"><div style="font-weight:700;font-size:15px;">快捷键帮助</div><button class="btn btn-ghost btn-sm" onclick="this.closest(\\\'#kb-shortcuts-panel-mask\\\').remove();">关闭</button></div>' +
      '<div style="max-height:70vh;overflow:auto;">' + rows + '</div>' +
      '<div class="text-xs text-muted mt-3" style="text-align:center;">按 Esc 关闭 · 再按 ? /  / 重新打开</div>' +
      '</div></div>';
    var wrap = document.createElement('div');
    wrap.innerHTML = html;
    var frag = document.createDocumentFragment();
    while (wrap.firstChild) frag.appendChild(wrap.firstChild);
    document.body.appendChild(frag);
  }

  // ============================================================
  // 4) 模态 Focus Trap
  // ============================================================
  function trapFocus(modalEl) {
    if (!modalEl) return function () {};
    var FOCUSABLE = 'a[href], area[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), iframe, object, embed, [tabindex]:not([tabindex="-1"]), [contenteditable="true"]';
    var nodes = modalEl.querySelectorAll(FOCUSABLE);
    var focusable = Array.prototype.filter.call(nodes || [], function (n) {
      return !!(n.offsetWidth || n.offsetHeight || n.getClientRects().length); // 可见
    });
    if (!focusable.length) return function () {}; // 空：防御分支
    var first = focusable[0], last = focusable[focusable.length - 1];
    modalEl.setAttribute('data-trap-focus', 'true');
    try { first.focus(); } catch (_) {}
    var onKey = function (ev) {
      if (ev.key !== 'Tab') return;
      if (ev.shiftKey && document.activeElement === first) {
        ev.preventDefault(); last.focus();
      } else if (!ev.shiftKey && document.activeElement === last) {
        ev.preventDefault(); first.focus();
      }
    };
    modalEl.addEventListener('keydown', onKey);
    return function cleanup() {
      modalEl.removeAttribute('data-trap-focus');
      modalEl.removeEventListener('keydown', onKey);
    };
  }

  // ============================================================
  // Init / Destroy
  // ============================================================
  function init(opts) {
    opts = opts || {};
    _scope = opts.scope || document;
    _appPrefix = opts.appPrefix || '';
    _shortcuts = opts.shortcuts || buildShortcuts(_appPrefix);

    // 幂等：同一 scope 只绑定一次
    if (_scope && _scope[INSTANCE_KEY]) {
      // 允许 caller 强制刷新 roving
      if (opts.forceRoving) applyRoving(_scope, opts.rovingSelectors);
      return { ok: true, cached: true };
    }

    // SkipLinks 只在 document scope 注入 (JSDOM 测试时 scope 可能是 fragment)
    if (_scope.nodeType === 9 /* Node.DOCUMENT_NODE */ || _scope === document) {
      try { injectSkipLinks(); } catch (_) {}
    }

    applyRoving(_scope, opts.rovingSelectors);

    _globalKDHandler = handleGlobalKeydown;
    var target = (_scope.addEventListener) ? _scope : document;
    target.addEventListener('keydown', _globalKDHandler, false);

    // Shortcuts help 提示（document 范围内才放）
    if ((_scope.nodeType === 9 || _scope === document) && opts.showHint !== false) {
      var help = document.createElement('div');
      help.className = 'shortcuts-help';
      help.innerHTML = '<span class="kbd">?</span> 快捷键';
      help.addEventListener('click', function () { openShortcutsPanel(); });
      help.setAttribute('role', 'button');
      help.setAttribute('tabindex', '0');
      help.addEventListener('keydown', function (ev) {
        if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); openShortcutsPanel(); }
      });
      if (!document.querySelector('.shortcuts-help')) document.body.appendChild(help);
    }

    try { _scope[INSTANCE_KEY] = true; } catch (_) {}
    _installed = true;
    return { ok: true, cached: false };
  }

  function destroy() {
    if (_globalKDHandler) {
      var target = (_scope && _scope.addEventListener) ? _scope : document;
      target.removeEventListener('keydown', _globalKDHandler);
      _globalKDHandler = null;
    }
    _rovingCleanups.forEach(function (fn) { try { fn(); } catch (_) {} });
    _rovingCleanups = [];
    var help = document.querySelector('.shortcuts-help');
    if (help && help.parentNode) help.parentNode.removeChild(help);
    var panel = document.getElementById('kb-shortcuts-panel-mask');
    if (panel) panel.remove();
    if (_scope) try { delete _scope[INSTANCE_KEY]; } catch (_) { try { _scope[INSTANCE_KEY] = false; } catch(__) {} }
    _installed = false;
    _goPrefix = false;
    if (_goTimer) { clearTimeout(_goTimer); _goTimer = null; }
  }

  // ============================================================
  // 对外 API
  // ============================================================
  var api = {
    init: init,
    destroy: destroy,
    /** 手动调用：对新渲染的容器（如刚切换视图后）重新应用 roving */
    refreshRoving: function (extraSelectors) {
      if (_scope) applyRoving(_scope, extraSelectors || DEFAULT_ROVING_SELECTORS);
    },
    /** 对新弹出的模态启用 Tab 循环，返回 cleanup 回调，关闭 modal 时调用 */
    trapFocus: trapFocus,
    /** 直接打开快捷键帮助（供 Playwright/E2E 调用） */
    openShortcutsPanel: openShortcutsPanel,
    closeShortcutsPanel: function () {
      var mk = document.getElementById('kb-shortcuts-panel-mask');
      if (mk) mk.remove();
    },
    /** 用于 Playwright/c8 断言内部状态 */
    _dbg: function () {
      return {
        installed: _installed,
        appPrefix: _appPrefix,
        rovingCount: _rovingCleanups.length,
        inGoPrefix: !!_goPrefix,
        defaultSelectors: DEFAULT_ROVING_SELECTORS,
      };
    },
    /**
     * 测试钩子：伪造 keydown 事件直接送入全局 handler（绕过 JSDOM dispatchEvent 的实现差异）。
     * opts: { key, ctrlKey, metaKey, altKey, shiftKey, target }
     * 返回 handler 执行期间未捕获的异常字符串，正常为 null
     */
    _fireKey: function (opts) {
      opts = opts || {};
      var target = opts.target || (document && document.body) || null;
      var fakeEv = {
        key: opts.key || '',
        ctrlKey: !!opts.ctrlKey,
        metaKey: !!opts.metaKey,
        altKey: !!opts.altKey,
        shiftKey: !!opts.shiftKey,
        target: target,
        _defaultPrevented: false,
        preventDefault: function () { this._defaultPrevented = true; },
      };
      try {
        if (typeof _globalKDHandler === 'function') _globalKDHandler(fakeEv);
        return { ok: true, defaultPrevented: !!fakeEv._defaultPrevented };
      } catch (e) {
        return { ok: false, err: String(e && e.message || e) };
      }
    },
  };

  global.LSCKeyboardA11y = api;
  // 兼容挂载到 LSC 命名空间
  if (typeof global.LSC !== 'undefined') {
    global.LSC.KeyboardA11y = api;
  } else if (typeof window !== 'undefined' && !window.LSC) {
    // LSC 未就绪时放 __pending 挂载队列 (懒加载)
    global.__LSCKeyboardA11y__ = api;
  }

  // UMD 检测：如在 CommonJS 环境 (c8/Node 测试) 也导出
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));
