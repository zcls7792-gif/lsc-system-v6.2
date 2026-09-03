/**
 * LSC 系统 Service Worker — shared/sw.js
 * 策略：
 * 1) Install 阶段：预缓存 shared/ 静态资源（跨端通用），保证无网络下四端基础样式与工具函数仍可用
 * 2) Runtime 拦截：
 *      - Navigation 请求（HTML 页面）→ Network-First，失败则回退到当前端的 index.html (离线 shell)
 *      - Same-origin static (css/js/svg/webmanifest/fonts/data:) → Stale-While-Revalidate
 *      - Cross-origin (fonts.googleapis 等) → Cache-First
 * 3) Activate 阶段：清理旧版本缓存 (VERSION 变化)
 *
 * 注意：使用 data: URI 的图标/字体/内联图片不会走 fetch，无需额外处理；
 *       preload/preconnect 资源会在主线程优先加载，SW 作为二级加速与离线兜底。
 */
const VERSION = 'lsc-v6.2.0-20260904';
const CACHE_PREFETCH = `prefetch-${VERSION}`;
const CACHE_RUNTIME  = `runtime-${VERSION}`;
const CACHE_THIRDPARTY = `thirdparty-${VERSION}`;

const SHARED_BASE = (self.registration && self.registration.scope) ? new URL('../shared/', self.registration.scope).pathname : '/shared/';

// 预缓存：跨端公共资源 + 四端 HTML shell (离线 fallback)
const PRECACHE_URLS = [
  `${SHARED_BASE}design-system.css`,
  `${SHARED_BASE}app-utils.js`,
  `${SHARED_BASE}keyboard-a11y.js`,
  '/platform-admin/index.html',
  '/merchant-admin/index.html',
  '/mobile-app/index.html',
  '/mini-program/index.html',
  '/',
];

/* ---------- Install ---------- */
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_PREFETCH).then((cache) => {
      // cache.addAll 会拒绝任何一个 404，为了健壮性手动 catch 单个失败
      return Promise.all(PRECACHE_URLS.map((u) =>
        fetch(u, { credentials: 'same-origin', cache: 'no-cache' })
          .then((resp) => {
            if (resp && resp.ok) return cache.put(u, resp.clone());
            return null;
          })
          .catch(() => null)
      ));
    }).then(() => {
      // 强制激活，便于升级后立即接管旧 tab
      try { self.skipWaiting(); } catch (_) {}
    })
  );
});

/* ---------- Activate: 清理旧缓存 ---------- */
self.addEventListener('activate', (event) => {
  const keep = new Set([CACHE_PREFETCH, CACHE_RUNTIME, CACHE_THIRDPARTY]);
  event.waitUntil(
    caches.keys().then((names) =>
      Promise.all(names.filter((n) => !keep.has(n)).map((n) => caches.delete(n)))
    ).then(() => {
      try { self.clients.claim(); } catch (_) {}
    })
  );
});

/* ---------- Helpers ---------- */
function _isNavigation(req) {
  return req.mode === 'navigate' && (req.method === 'GET' || req.method === 'HEAD');
}
function _isSameOrigin(url) {
  try {
    return new URL(url, self.location.href).origin === self.location.origin;
  } catch (_) {
    return false;
  }
}
function _isStaticAsset(urlPath) {
  return /\.(css|js|mjs|json|webmanifest|svg|png|jpg|jpeg|gif|ico|webp|woff2?|ttf|otf|eot|map)(\?|#|$)/i.test(urlPath);
}
function _fallbackFor(reqURL) {
  const p = reqURL.pathname;
  if (p.startsWith('/platform-admin/')) return '/platform-admin/index.html';
  if (p.startsWith('/merchant-admin/'))  return '/merchant-admin/index.html';
  if (p.startsWith('/mobile-app/'))      return '/mobile-app/index.html';
  if (p.startsWith('/mini-program/'))    return '/mini-program/index.html';
  if (p === '/' || p === '' || p === '/index.html') return '/index.html';
  return null;
}

/* ---------- Fetch ---------- */
self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (!req || req.method !== 'GET') return; // 只处理 GET (HEAD 不重要)
  const url = new URL(req.url, self.location.href);

  // 1) Navigation: Network-First，失败回退离线 shell
  if (_isNavigation(req)) {
    event.respondWith(
      fetch(req).then((resp) => {
        const copy = resp.clone();
        caches.open(CACHE_RUNTIME).then((c) => { try { c.put(req, copy); } catch(_) {} });
        return resp;
      }).catch(() => {
        return caches.match(req).then((cached) => {
          if (cached) return cached;
          const fb = _fallbackFor(url);
          if (!fb) return caches.match('/index.html').then((r) => r || Response.error());
          return caches.match(fb).then((cachedFB) => cachedFB || caches.match('/index.html').then((r) => r || Response.error()));
        });
      })
    );
    return;
  }

  // 2) Data URI / blob: passthrough (fetch(data:) 原生支持即可，但有些浏览器不允许 SW 拦截 data:，保险起见直接 return)
  if (url.protocol === 'data:' || url.protocol === 'blob:') {
    return;
  }

  const sameOrigin = _isSameOrigin(req.url);
  const pathname = url.pathname;

  // 3) Third-party (跨源字体/CDN 等): Cache-First
  if (!sameOrigin) {
    event.respondWith(
      caches.open(CACHE_THIRDPARTY).then((c) =>
        c.match(req).then((cached) => {
          if (cached) return cached;
          return fetch(req).then((resp) => {
            if (resp && resp.ok) { try { c.put(req, resp.clone()); } catch(_) {} }
            return resp;
          }).catch(() => cached || Response.error());
        })
      )
    );
    return;
  }

  // 4) Same-origin 静态资源 (css/js/manifest/图片/字体/svg) → Stale-While-Revalidate
  if (_isStaticAsset(pathname)) {
    event.respondWith(
      caches.open(CACHE_RUNTIME).then((cache) =>
        cache.match(req).then((cached) => {
          const fetcher = fetch(req).then((resp) => {
            if (resp && resp.ok) { try { cache.put(req, resp.clone()); } catch(_) {} }
            return resp;
          }).catch(() => cached || Response.error());
          return cached || fetcher;
        })
      )
    );
    return;
  }

  // 5) 其他 same-origin (API 调用等) → 纯 Network，不缓存 (保证业务实时性)
});

/* ---------- Message: 触发手动更新版本缓存 ---------- */
self.addEventListener('message', (event) => {
  if (!event || !event.data) return;
  const type = event.data && event.data.type;
  if (type === 'LSC_SW_SKIP_WAITING') {
    try { self.skipWaiting(); } catch (_) {}
    if (event.source && event.source.postMessage) {
      try { event.source.postMessage({ type: 'LSC_SW_SKIPPED', version: VERSION }); } catch(_) {}
    }
  } else if (type === 'LSC_SW_VERSION') {
    if (event.source && event.source.postMessage) {
      try { event.source.postMessage({ type: 'LSC_SW_VERSION', version: VERSION, precache: PRECACHE_URLS.length }); } catch(_) {}
    }
  } else if (type === 'LSC_SW_CLEAR_CACHE') {
    caches.keys().then((names) => Promise.all(names.map((n) => caches.delete(n)))).then(() => {
      if (event.source && event.source.postMessage) {
        try { event.source.postMessage({ type: 'LSC_SW_CLEARED', version: VERSION }); } catch(_) {}
      }
    });
  }
});
