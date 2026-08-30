/* ============================================================
   链盛通LSC系统 - 共享工具与模拟数据 V6.2
   ============================================================ */

/* ---------- 工具函数 ---------- */
const LSC = {
  // 格式化数字 - 千分位
  fmtNum(n, digits = 0) {
    if (n == null || isNaN(n)) return '-';
    return Number(n).toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  },
  // 货币格式化
  fmtMoney(n, prefix = '¥') {
    if (n == null || isNaN(n)) return '-';
    return prefix + Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },
  // LSC 格式化
  fmtLSC(n) {
    if (n == null || isNaN(n)) return '-';
    return Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' LSC';
  },
  // 百分比
  fmtPct(n, digits = 2) {
    if (n == null || isNaN(n)) return '-';
    return (Number(n) * 100).toFixed(digits) + '%';
  },
  // 简化时间
  fmtTime(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  },
  fmtDate(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  },
  // 生成幂等键 业务类型:用户ID:时间戳:4位随机
  genIdempotentKey(bizType, userId) {
    const ts = Date.now();
    const rand = Math.floor(Math.random() * 10000).toString().padStart(4, '0');
    return `${bizType}:${userId}:${ts}:${rand}`;
  },
  // 释放速率计算 (rate ∈ [0.03%,0.06%] · 2026-08-29 重大调整)
  calcRate(k) {
    if (k <= 0.005) return 0.0006;   // k≤0.50% → rate_max = 0.06%
    if (k >= 0.01)  return 0.0003;   // k≥1.0%  → rate_min = 0.03%
    return 0.0009 - 0.06 * k;        // 线性插值: rate = 0.09% - 0.06×k
  },
  /* ---- 十七档核销限额配置 (唯一权威来源 · 按 minRevenue 从高到低排列) ---- */
  NH_TIERS: [
    { minRevenue: 50000000, level: 'Q', dailyLsc: 115000 }, // ≥5000万
    { minRevenue: 45000000, level: 'P', dailyLsc: 100000 }, // ≥4500万
    { minRevenue: 40000000, level: 'O', dailyLsc:  90000 }, // ≥4000万
    { minRevenue: 35000000, level: 'N', dailyLsc:  80000 }, // ≥3500万
    { minRevenue: 30000000, level: 'M', dailyLsc:  69000 }, // ≥3000万
    { minRevenue: 25000000, level: 'L', dailyLsc:  57000 }, // ≥2500万
    { minRevenue: 20000000, level: 'K', dailyLsc:  46000 }, // ≥2000万
    { minRevenue: 12000000, level: 'J', dailyLsc:  29000 }, // ≥1200万
    { minRevenue:  6000000, level: 'I', dailyLsc:  15000 }, // ≥600万
    { minRevenue:  3200000, level: 'H', dailyLsc:   7000 }, // ≥320万
    { minRevenue:  1600000, level: 'G', dailyLsc:   3600 }, // ≥160万
    { minRevenue:   800000, level: 'F', dailyLsc:   1800 }, // ≥80万
    { minRevenue:   400000, level: 'E', dailyLsc:    900 }, // ≥40万
    { minRevenue:   200000, level: 'D', dailyLsc:    450 }, // ≥20万
    { minRevenue:   100000, level: 'C', dailyLsc:    200 }, // ≥10万
    { minRevenue:    50000, level: 'B', dailyLsc:    115 }, // ≥5万
    { minRevenue:    20000, level: 'A', dailyLsc:     50 }, // ≥2万
  ],
  NH_INITIAL_TIER: { minRevenue: 0, level: '初始', dailyLsc: 30 }, // 新入驻未满2万
  // 根据月营业额(元)匹配档位
  getNhTierByRevenue(monthRevenue) {
    const rev = Number(monthRevenue) || 0;
    for (const t of LSC.NH_TIERS) if (rev >= t.minRevenue) return { ...t };
    return { ...LSC.NH_INITIAL_TIER };
  },
  // 信用分 → 执行系数 + 权限（统一5档：<20 永久关闭 / 20–39 暂停核销+B2B / 40–59 暂停核销 / 60–79 ×50% / 80–100 ×100%）
  getCreditEffect(credit) {
    const c = Number(credit);
    if (!(c >= 0) || c < 20)  return { factor: 0,   nh: 'closed_perm', b2b: 'closed_perm', label: '永久关闭核销与B2B流转', color: 'danger' };
    if (c < 40)               return { factor: 0,   nh: 'suspended',   b2b: 'suspended',   label: '暂停核销及B2B流转',       color: 'danger' };
    if (c < 60)               return { factor: 0,   nh: 'suspended',   b2b: 'allowed',     label: '暂停核销权限',              color: 'warning' };
    if (c < 80)               return { factor: 0.5, nh: 'allowed_half',b2b: 'allowed',     label: '50%限额执行',              color: 'warning' };
    return                            { factor: 1.0, nh: 'allowed',     b2b: 'allowed',     label: '100%标准执行',             color: 'success' };
  },
  // 组合: 给定商家对象 → 档位+信用分合成结果 (同时回填兼容字段 nhLevel/nhLimitDaily)
  getEffectiveNhLimit(merchant) {
    const m = merchant || {};
    const tier = LSC.getNhTierByRevenue(m.monthRevenue);
    const eff  = LSC.getCreditEffect(m.credit);
    const finalDailyLsc = Math.max(0, Math.floor(tier.dailyLsc * eff.factor));
    return {
      // 语义字段
      baseLevel: tier.level,
      baseDailyLsc: tier.dailyLsc,
      minRevenue: tier.minRevenue,
      creditFactor: eff.factor,
      finalDailyLsc,
      nhStatus: eff.nh,
      b2bStatus: eff.b2b,
      statusLabel: eff.label,
      creditColor: eff.color,
      // 兼容字段: 保证现有 m.nhLevel / m.nhLimitDaily 模板不改即生效
      nhLevel: tier.level,
      nhLimitDaily: finalDailyLsc,
    };
  },
  // 对商家数组批量派生 nhLevel / nhLimitDaily / status 等字段(覆盖旧硬编码值)
  applyTierAndCredit(merchants) {
    if (!Array.isArray(merchants)) return merchants;
    for (const m of merchants) {
      const eff = LSC.getEffectiveNhLimit(m);
      m.nhLevel = eff.nhLevel;
      m.nhLimitDaily = eff.nhLimitDaily;
      m.nhStatus = eff.nhStatus;
      m.b2bStatus = eff.b2bStatus;
      m.creditColor = eff.creditColor;
      m.creditFactor = eff.creditFactor;
      m.statusLabel = eff.statusLabel;
      // 若处罚/永久关闭态，反映到 status 字段供 UI 打标
      if (eff.nhStatus === 'closed_perm' && m.status !== 'closed_perm') {
        // 不强制覆盖已有的 penalty/warning，仅当没有更细粒度态时兜底
        if (!m.status || m.status === 'normal') m.status = 'closed_perm';
      }
    }
    return merchants;
  },
  // 简易 SPA 路由
  router(routes, defaultRoute) {
    const go = (name, params) => {
      const r = routes[name] || routes[defaultRoute];
      window.scrollTo(0, 0);
      r(params);
    };
    return { go };
  },
  // 防抖
  debounce(fn, wait = 300) {
    let t;
    return function (...a) {
      clearTimeout(t);
      t = setTimeout(() => fn.apply(this, a), wait);
    };
  },
  // A11y 增强: 扫描所有 overflow:auto/scroll 元素,补 tabindex=0 + role=region
  // 解决 axe-core scrollable-region-focusable 违规
  a11yEnhance(root = document) {
    const els = root.querySelectorAll('*');
    let n = 0;
    for (const el of els) {
      if (el.hasAttribute('tabindex')) continue;
      const cs = getComputedStyle(el);
      const ov = (cs.overflowX + ' ' + cs.overflowY).toLowerCase();
      if (ov.includes('auto') || ov.includes('scroll')) {
        el.setAttribute('tabindex', '0');
        if (!el.getAttribute('role')) el.setAttribute('role', 'region');
        if (!el.getAttribute('aria-label')) {
          const t = (el.textContent || '').trim().slice(0, 30);
          el.setAttribute('aria-label', t ? `可滚动区域: ${t}` : '可滚动区域');
        }
        n++;
      }
    }
    return n;
  }
};

/* ---------- 模拟数据 ---------- */
const MOCK = {
  // 平台概览
  dashboard: {
    lockedTotal: 86542000.50,    // 全网锁定总量
    availableTotal: 42318000.00, // 全网可用总量
    todayRelease: 468230.00,      // 当日释放 (rate=0.0468% · 上调 21.6%)
    todayNHTotal: 1240000.00,     // 当日核销
    todayB2B: 856000.00,          // 当日B2B流转
    todayConsume: 2340000.00,     // 当日消费发行
    userCount: 486320,            // 用户总数
    merchantCount: 12856,         // 商家总数
    todayNewUser: 2840,
    todayNewMerchant: 56,
    currentK: 0.0072,             // 当前核销率
    currentRate: 0.000468,        // 当前释放速率 (k=0.72% → 0.0468%)
  },
  // k值历史 (近30天)
  kHistory: [0.0062,0.0065,0.0068,0.0071,0.0069,0.0072,0.0074,0.0073,0.0071,0.0068,
             0.0066,0.0064,0.0067,0.0069,0.0071,0.0072,0.0073,0.0072,0.0071,0.0070,
             0.0068,0.0069,0.0071,0.0072,0.0074,0.0073,0.0072,0.0072,0.0072,0.0072],
  // k值预测 (未来7天)
  kForecast7: [0.0073,0.0075,0.0076,0.0078,0.0079,0.0081,0.0082],
  // k值预测 (未来30天)
  kForecast30: Array.from({length:30}, (_,i) => 0.0072 + i*0.00018 + (Math.sin(i/3)*0.0003)),
  // 释放总量趋势 (近30天)
  releaseTrend: [320000,335000,348000,360000,355000,370000,385000,380000,372000,368000,
                 360000,355000,365000,372000,380000,385000,392000,388000,382000,378000,
                 372000,375000,380000,385000,392000,388000,385000,385000,385200,385200],
  // 商家列表
  merchants: [
    { id:'M20001', name:'锦华餐饮连锁·总店', type:'餐饮', credit:92, aiRisk:18, monthRevenue:385000, nhLevel:'A', nhLimitDaily:50000, status:'normal', addr:'上海市浦东新区世纪大道100号', aiAddr:'pass' },
    { id:'M20002', name:'恒通建材批发中心', type:'建材', credit:85, aiRisk:35, monthRevenue:1240000, nhLevel:'B', nhLimitDaily:30000, status:'normal', addr:'杭州市余杭区五常街道168号', aiAddr:'pass' },
    { id:'M20003', name:'鲜之源生鲜超市', type:'零售', credit:78, aiRisk:42, monthRevenue:286000, nhLevel:'B', nhLimitDaily:30000, status:'warning', addr:'苏州市姑苏区干将路88号', aiAddr:'suspect' },
    { id:'M20004', name:'鼎盛物流仓储', type:'物流', credit:64, aiRisk:68, monthRevenue:890000, nhLevel:'C', nhLimitDaily:10000, status:'penalty', addr:'深圳市龙岗区中心城26号', aiAddr:'fail' },
    { id:'M20005', name:'御品茶业工坊', type:'零售', credit:96, aiRisk:8, monthRevenue:96000, nhLevel:'A', nhLimitDaily:50000, status:'normal', addr:'福州市鼓楼区八一七中路12号', aiAddr:'pass' },
    { id:'M20006', name:'金泰百货商行', type:'百货', credit:71, aiRisk:52, monthRevenue:542000, nhLevel:'B', nhLimitDaily:30000, status:'warning', addr:'南京市玄武区中山路200号', aiAddr:'pass' },
    { id:'M20007', name:'海纳科技公司', type:'数码', credit:88, aiRisk:25, monthRevenue:2160000, nhLevel:'A', nhLimitDaily:50000, status:'normal', addr:'北京市海淀区中关村大街1号', aiAddr:'pass' },
    { id:'M20008', name:'云裳服饰有限公司', type:'服装', credit:55, aiRisk:75, monthRevenue:428000, nhLevel:'C', nhLimitDaily:10000, status:'penalty', addr:'广州市天河区天河北路90号', aiAddr:'suspect' },
    // 边界样本1: 新入驻未满2万 → 初始档 30 LSC/日
    { id:'M20009', name:'阳光社区便利铺', type:'零售', credit:85, aiRisk:12, monthRevenue:12800, nhLevel:'初始', nhLimitDaily:30, status:'normal', addr:'成都市锦江区春熙路18号', aiAddr:'pass' },
    // 边界样本2: 信用分<20 → 永久关闭核销+B2B (无论营业额多少)
    { id:'M20010', name:'星耀数码(已永久关停)', type:'数码', credit:15, aiRisk:96, monthRevenue:2800000, nhLevel:'H', nhLimitDaily:0, status:'normal', addr:'武汉市洪山区珞喻路88号', aiAddr:'fail' },
  ],
  // 商品审核列表
  products: [
    { id:'P5001', merchant:'锦华餐饮连锁·总店', name:'精品双人套餐·周末限定', price:399.00, stock:500, status:'ai_pass', aiTags:['真实','高清','合规'], aiScore:0.92, video:'ok' },
    { id:'P5002', merchant:'御品茶业工坊', name:'明前龙井·礼盒装250g', price:888.00, stock:120, status:'ai_suspect', aiTags:['疑似过度修图'], aiScore:0.61, video:'none' },
    { id:'P5003', merchant:'恒通建材批发中心', name:'环保竹地板·工程批量', price:128.00, stock:5000, status:'ai_pass', aiTags:['真实','合规'], aiScore:0.88, video:'ok' },
    { id:'P5004', merchant:'鲜之源生鲜超市', name:'进口澳洲和牛M9·500g', price:698.00, stock:80, status:'manual_review', aiTags:['需人工复核'], aiScore:0.55, video:'ok' },
    { id:'P5005', merchant:'云裳服饰有限公司', name:'高仿奢侈品包·A货', price:299.00, stock:200, status:'ai_reject', aiTags:['涉嫌假冒','违规'], aiScore:0.18, video:'reject' },
    { id:'P5006', merchant:'海纳科技公司', name:'智能蓝牙耳机Pro3', price:499.00, stock:2000, status:'ai_pass', aiTags:['真实','高清','合规'], aiScore:0.95, video:'ok' },
  ],
  // B2B订单列表
  b2bOrders: [
    { id:'B2B20260824001', from:'海纳科技公司', to:'恒通建材批发中心', desc:'智能门禁系统采购', rmb:128000, lsc:128000, contract:'HT-2026-0824', aiVerify:1, aiMatch:0.94, status:'confirmed' },
    { id:'B2B20260824002', from:'御品茶业工坊', to:'锦华餐饮连锁·总店', desc:'高端茶叶季度供应', rmb:96000, lsc:96000, contract:'HT-2026-0824-2', aiVerify:3, aiMatch:0.98, status:'completed' },
    { id:'B2B20260824003', from:'鲜之源生鲜超市', to:'鼎盛物流仓储', desc:'冷链运输服务', rmb:38000, lsc:38000, contract:'HT-2026-0823', aiVerify:2, aiMatch:0.42, status:'pending' },
    { id:'B2B20260824004', from:'金泰百货商行', to:'云裳服饰有限公司', desc:'夏装批采订单', rmb:86000, lsc:86000, contract:'HT-2026-0824-3', aiVerify:0, aiMatch:0, status:'await_verify' },
    { id:'B2B20260824005', from:'恒通建材批发中心', to:'海纳科技公司', desc:'机房装修材料', rmb:215000, lsc:215000, contract:'HT-2026-0822', aiVerify:4, aiMatch:0.12, status:'rejected' },
  ],
  // 用户风控日志
  riskLogs: [
    { id:'RL26001', user:'U10086', type:'批量注册', detail:'同IP 24小时内注册5个账号', level:'high', score:0.86, action:'限制登录', op:'AI Agent', ts:Date.now()-3600000 },
    { id:'RL26002', user:'U10092', type:'异常核销', detail:'凌晨3点连续核销8笔', level:'high', score:0.79, action:'冻结核销', op:'AI Agent', ts:Date.now()-7200000 },
    { id:'RL26003', user:'U10102', type:'转账聚集', detail:'4个账户LSC集中转给1商家', level:'medium', score:0.62, action:'人工复核', op:'AI Agent', ts:Date.now()-10800000 },
    { id:'RL26004', user:'U10115', type:'设备指纹', detail:'同设备登录12个账号', level:'high', score:0.91, action:'设备封禁', op:'运营·张明', ts:Date.now()-86400000 },
    { id:'RL26005', user:'U10118', type:'位置异常', detail:'下单与收货地址距离>800km', level:'low', score:0.31, action:'放行', op:'AI Agent', ts:Date.now()-172800000 },
  ],
  // 商家违规记录
  violations: [
    { id:'V26001', merchant:'M20008', type:'虚假地址', detail:'注册地址与实际经营地址不符', deduct:20, measure:'暂停核销30天', aiFound:true, start:Date.now()-86400000*5, end:Date.now()+86400000*25, op:'运营·李娜' },
    { id:'V26002', merchant:'M20004', type:'高核销率', detail:'单日核销率达8.2%触发风控', deduct:15, measure:'核销限额降至1万/日', aiFound:true, start:Date.now()-86400000*3, end:Date.now()+86400000*27, op:'AI Agent' },
    { id:'V26003', merchant:'M20003', type:'信用异常', detail:'AI风险评分连续3天>40', deduct:10, measure:'加强审核', aiFound:true, start:Date.now()-86400000*2, end:Date.now()+86400000*7, op:'运营·王强' },
  ],
  // 管理员审计日志
  auditLogs: [
    { id:'A26001', admin:'admin·林总', role:'超级管理员', op:'参数修改', detail:'将k_min由0.50%调整为0.45%', ip:'116.62.105.32', device:'MacOS·Chrome', aiFlag:false, ts:Date.now()-1800000 },
    { id:'A26002', admin:'运营·张明', role:'运营管理员', op:'商家处罚', detail:'对M20004执行核销限额处罚', ip:'117.84.21.18', device:'Win11·Edge', aiFlag:false, ts:Date.now()-3600000 },
    { id:'A26003', admin:'运营·李娜', role:'运营管理员', op:'商品下架', detail:'下架P5005(涉嫌假冒)', ip:'121.40.88.99', device:'iOS·Safari', aiFlag:false, ts:Date.now()-5400000 },
    { id:'A26004', admin:'admin·林总', role:'超级管理员', op:'释放熔断', detail:'触发当日释放熔断暂停', ip:'116.62.105.32', device:'MacOS·Chrome', aiFlag:true, ts:Date.now()-86400000 },
    { id:'A26005', admin:'财务·陈工', role:'运营管理员', op:'对账核查', detail:'查询8月23日对账报告', ip:'114.55.12.74', device:'Win10·Chrome', aiFlag:false, ts:Date.now()-90000000 },
  ],
};

/* ---- 十七档+信用分联动 (唯一权威派生: 覆盖 MOCK.merchants 中所有硬编码的 nhLevel/nhLimitDaily/statusLabel) ---- */
LSC.applyTierAndCredit(MOCK.merchants);

/* ---------- 简易 SVG 图标库 (线性) ---------- */
const ICONS = {
  dashboard: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>',
  merchant: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l1.5-5h15L21 9"/><path d="M4 9v11h16V9"/><path d="M9 14h6"/></svg>',
  product: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 8l-9-5-9 5 9 5 9-5z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/></svg>',
  b2b: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M7 10l-4 4 4 4"/><path d="M17 14l4-4-4-4"/><path d="M3 14h7"/><path d="M14 10h7"/><path d="M10 10l4 4"/></svg>',
  risk: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2L3 7v6c0 5 4 9 9 9s9-4 9-9V7l-9-5z"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>',
  credit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v10"/><path d="M9.5 9.5c0-1.4 1.1-2 2.5-2s2.5.6 2.5 2-1.1 2-2.5 2v2"/></svg>',
  release: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2v6"/><path d="M9 5l3-3 3 3"/><path d="M5 12c0 3.9 3.1 7 7 7s7-3.1 7-7"/><path d="M5 12h14"/><path d="M19 12c0 3.9-3.1 7-7 7"/></svg>',
  reconcile: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18"/><path d="M3 12h18"/><path d="M3 18h18"/><path d="M7 3v18"/><path d="M17 3v18"/></svg>',
  system: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/></svg>',
  ai: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="6" width="16" height="13" rx="2"/><path d="M9 2v4M15 2v4"/><circle cx="9" cy="12" r="1.2"/><circle cx="15" cy="12" r="1.2"/><path d="M9 16h6"/><path d="M2 12H4M20 12h2"/></svg>',
  wallet: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7h15a3 3 0 013 3v6a3 3 0 01-3 3H4a1 1 0 01-1-1V7z"/><path d="M3 7V5a1 1 0 011-1h11"/><circle cx="16" cy="13" r="1.5"/></svg>',
  shop: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l2-5h14l2 5"/><path d="M4 9v11h16V9"/><path d="M4 9h16"/><path d="M9 20v-6h6v6"/></svg>',
  mall: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l1.5-5h15L21 9"/><path d="M4 9v11h16V9"/><path d="M9 14h6"/></svg>',
  promotion: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="9" r="2.5"/><circle cx="17" cy="15" r="2.5"/><path d="M11.5 9.5L14.5 14"/></svg>',
  order: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-7 8-7s8 3 8 7"/></svg>',
  home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/></svg>',
  scan: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7V5a2 2 0 012-2h2M17 3h2a2 2 0 012 2v2M21 17v2a2 2 0 01-2 2h-2M7 21H5a2 2 0 01-2-2v-2"/><path d="M3 12h18"/></svg>',
  chat: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12c0 4.4-4 8-9 8-1.4 0-2.7-.2-3.9-.7L3 21l1.7-5.1C3.6 14.7 3 13.4 3 12c0-4.4 4-8 9-8s9 3.6 9 8z"/></svg>',
  bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M18 8a6 6 0 00-12 0c0 7-3 9-3 9h18s-3-2-3-9z"/><path d="M10 21a2 2 0 004 0"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4-4"/></svg>',
  logout: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 4H5a2 2 0 00-2 2v12a2 2 0 002 2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/></svg>',
  menu: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18M3 12h18M3 18h18"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M15 18l-6-6 6-6"/></svg>',
  qr: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><path d="M14 14h3v3M21 14v7M14 21h7"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12l5 5 9-9"/></svg>',
  warning: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2L2 21h20L12 2z"/><path d="M12 9v5"/><path d="M12 18h.01"/></svg>',
  chart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 3v18h18"/><path d="M7 14l3-3 4 4 5-7"/></svg>',
  chain: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M10 14a5 5 0 007 0l3-3a5 5 0 00-7-7l-1 1"/><path d="M14 10a5 5 0 00-7 0l-3 3a5 5 0 007 7l1-1"/></svg>',
  doc: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V9z"/><path d="M14 3v6h6"/></svg>',
  lock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 018 0v4"/></svg>',
  unlock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 018-1"/></svg>',
  flow: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 5h6l4 4h8"/><path d="M3 19h18"/><circle cx="13" cy="9" r="2"/></svg>',
  location: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 22s8-7 8-13a8 8 0 00-16 0c0 6 8 13 8 13z"/><circle cx="12" cy="9" r="3"/></svg>',
  more: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/></svg>',
  filter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 4h18l-7 9v7l-4-2v-5L3 4z"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12a9 9 0 11-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>',
  export: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2v-7"/><path d="M12 3v12"/><path d="M8 7l4-4 4 4"/></svg>',
  eye: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>',
  arrowDown: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M6 13l6 6 6-6"/></svg>',
  arrowUp: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 19V5M6 11l6-6 6 6"/></svg>',
  arrowRight: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 12h14M13 6l6 6-6 6"/></svg>',
};
