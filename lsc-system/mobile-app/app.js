/* 渲染图标 */
function renderIcons(scope=document) {
  scope.querySelectorAll('.icon[data-i]').forEach(el=>{
    const key = el.getAttribute('data-i');
    if (ICONS[key]) el.innerHTML = ICONS[key];
  });
}
renderIcons();

/* 子页面栈 */
const subScreens = ['orders','promo','ai','paycode','product'];
var curTab = 'home';

function showScreen(name) {
  document.querySelectorAll('.screen').forEach(s=>{
    s.classList.remove('active');
    s.setAttribute('aria-hidden', 'true');
  });
  const el = document.getElementById('screen-'+name);
  if (el) { el.classList.add('active'); el.setAttribute('aria-hidden', 'false'); document.getElementById('content').scrollTop=0; }
  // tab 高亮 + aria-current
  document.querySelectorAll('.tab-item').forEach(t=>{
    const is = t.dataset.screen===name;
    t.classList.toggle('active', is);
    if (is) t.setAttribute('aria-current', 'page'); else t.removeAttribute('aria-current');
  });
  // 状态栏颜色
  const dark = ['home','scan','paycode','wallet','me','promo'].includes(name);
  document.getElementById('statusbar').classList.toggle('dark', dark);
  // tabbar 显示控制
  document.getElementById('tabbar').style.display = subScreens.includes(name) ? 'none' : 'flex';
  if (!subScreens.includes(name)) curTab = name;
}

/* Tab 点击/键盘 */
document.getElementById('tabbar').addEventListener('click', e=>{
  const item = e.target.closest('.tab-item');
  if (item) { showScreen(item.dataset.screen); }
});
document.getElementById('tabbar').addEventListener('keydown', e=>{
  if ((e.key==='Enter'||e.key===' ') && e.target.classList?.contains('tab-item')) {
    e.preventDefault();
    showScreen(e.target.dataset.screen);
  }
});

/* ===== 首页 · 商家档位+信用分卡片辅助 ===== */
function _getMerchantByName(name) {
  if (typeof MOCK === 'undefined' || !MOCK || !MOCK.merchants) return null;
  return MOCK.merchants.find(m => m.name === name) || null;
}
function _creditTagClass(color) {
  const m = { success: 'tag-success', warning: 'tag-warning', danger: 'tag-danger' };
  return m[color] || 'tag-default';
}
function _tierTagClass(level) {
  if (level === '初始') return 'tag tag-default';
  const order = 'ABCDEFGHIJKLMNOPQ'.indexOf(level);
  if (order >= 12) return 'tag tag-primary';   // M-Q 头部
  if (order >= 7)  return 'tag tag-accent';    // H-L 中高
  if (order >= 3)  return 'tag tag-available'; // D-G 中
  return 'tag tag-info';                        // A-C / 未知
}
function renderMerchantCard(m, opts = {}) {
  const name = m.name;
  const logo = (name || '商').trim()[0];
  const distance = opts.distance || '500m';
  const rating = opts.rating != null ? opts.rating : 4.8;
  const credit = m.credit != null ? m.credit : null;
  const tier = m.nhLevel || '初始';
  const color = m.creditColor || 'success';
  const label = m.statusLabel || '';
  // 暂停/关闭态：整卡置灰 + disabled
  const disabled = (m.nhStatus === 'suspended' || m.nhStatus === 'closed_perm') ? 'true' : null;
  const dimCls = disabled ? ' merchant-m-disabled' : '';
  const tierCls = _tierTagClass(tier);
  const creditCls = 'tag ' + _creditTagClass(color);
  const creditLine = credit != null
    ? `<span class="${creditCls}" role="img" aria-label="信用分${credit}分，${label}" title="信用分 ${credit} 分">信用 ${credit}·${label}</span>`
    : '';
  const tierLine = `<span class="${tierCls}" role="img" aria-label="档位${tier}" title="档位 ${tier} · 月营业额 ${m.minRevenue != null ? '≥' + (m.minRevenue / 10000).toFixed(0) + '万' : '未满2万'}">档位 ${tier}</span>`;
  return `<div class="merchant-m${dimCls}" role="link" aria-label="${name}${disabled ? ' 核销权限受限' : ''}"${disabled ? ' aria-disabled="true"' : ''} onclick="${disabled ? '' : "showScreen('scan')"}">
    <div class="merchant-m-logo">${logo}</div>
    <div class="merchant-m-info">
      <div class="merchant-m-name">${name}${tierLine}</div>
      <div class="merchant-m-meta">${m.type || '零售'} · 评分${rating} · 营业中${creditLine}</div>
      <div class="merchant-m-dist"><span class="icon" data-i="location"></span>距您 ${distance} · 支持LSC消费</div>
    </div>
  </div>`;
}

/* ===== 首页 ===== */
function renderHome() {
  // 首页附近商家（按距离 / 信用分排序：信用分高优先）
  const jh = _getMerchantByName('锦华餐饮连锁·总店') || { name:'锦华餐饮连锁·总店', type:'餐饮', credit:92, nhLevel:'D', creditColor:'success', statusLabel:'100%标准执行', minRevenue:200000 };
  const yp = _getMerchantByName('御品茶业工坊')   || { name:'御品茶业工坊',   type:'零售', credit:96, nhLevel:'B', creditColor:'success', statusLabel:'100%标准执行', minRevenue:50000 };
  const xz = _getMerchantByName('鲜之源生鲜超市') || { name:'鲜之源生鲜超市', type:'零售', credit:78, nhLevel:'D', creditColor:'warning', statusLabel:'50%限额执行', minRevenue:200000 };
  const ys = _getMerchantByName('云裳服饰有限公司') || { name:'云裳服饰有限公司', type:'服装', credit:55, nhLevel:'D', creditColor:'warning', statusLabel:'暂停核销权限', minRevenue:200000, nhStatus:'suspended' };

  const jhCard = renderMerchantCard(jh, { distance: '280m', rating: 4.9 });
  const ypCard = renderMerchantCard(yp, { distance: '650m', rating: 4.8 });
  const xzCard = renderMerchantCard(xz, { distance: '1.2km', rating: 4.6 });
  const ysCard = renderMerchantCard(ys, { distance: '2.1km', rating: 4.2 });

  document.getElementById('screen-home').innerHTML = `
  <div class="home-hero">
    <div class="home-top">
      <div class="home-loc"><span class="icon" data-i="location"></span><span>上海·浦东新区</span></div>
      <div class="home-icons"><span class="icon" data-i="scan" onclick="showScreen('scan')"></span><span class="icon" data-i="bell" onclick="showTip('暂无新消息')"></span></div>
    </div>
    <div style="font-size:13px;opacity:0.8;">您好, 李先生</div>
    <div style="font-size:22px;font-weight:700;margin-top:2px;">链盛通 · 让消费创造权益</div>
  </div>

  <div class="wallet-float">
    <div class="lsc-card-mobile">
      <div class="wm-label">我的 LSC 可用余额</div>
      <div><span class="wm-val">8,640.50</span><span class="wm-unit">LSC</span></div>
      <div class="wm-foot">
        <span>锁定池<b>15,200.00</b></span>
        <span style="text-align:center;">今日释放<b style="color:var(--c-accent-soft);">+38.50</b></span>
        <span style="text-align:right;">总资产<b>23,840.50</b></span>
      </div>
    </div>
  </div>

  <div class="pg">
    <div class="quick-grid">
      <div class="quick-item" onclick="showScreen('scan')"><div class="quick-icon" style="background:var(--c-primary);"><span class="icon" data-i="scan"></span></div><span>扫一扫</span></div>
      <div class="quick-item" onclick="showScreen('paycode')"><div class="quick-icon" style="background:var(--c-available);"><span class="icon" data-i="qr"></span></div><span>付款码</span></div>
      <div class="quick-item" onclick="showScreen('orders')"><div class="quick-icon" style="background:var(--c-accent);"><span class="icon" data-i="order"></span></div><span>订单</span></div>
      <div class="quick-item" onclick="showScreen('promo')"><div class="quick-icon" style="background:var(--c-warning);"><span class="icon" data-i="promotion"></span></div><span>推广</span></div>
    </div>

    <div class="section-head">
      <div class="section-title">权益商城推荐</div>
      <div class="section-more" onclick="showScreen('mall')">更多 <span class="icon icon-sm" data-i="arrowRight"></span></div>
    </div>
    <div class="h-scroll">
      <div class="product-m" onclick="showScreen('product')">
        <div class="product-m-img"><span class="icon" data-i="product"></span></div>
        <div class="product-m-body">
          <div class="product-m-name">精品双人套餐·周末限定</div>
          <div class="product-m-price">¥399</div>
          <div class="product-m-lsc">可抵 399 LSC</div>
        </div>
      </div>
      <div class="product-m" onclick="showScreen('product')">
        <div class="product-m-img"><span class="icon" data-i="product"></span></div>
        <div class="product-m-body">
          <div class="product-m-name">明前龙井·礼盒装250g</div>
          <div class="product-m-price">¥888</div>
          <div class="product-m-lsc">可抵 888 LSC</div>
        </div>
      </div>
      <div class="product-m" onclick="showScreen('product')">
        <div class="product-m-img"><span class="icon" data-i="product"></span></div>
        <div class="product-m-body">
          <div class="product-m-name">智能蓝牙耳机Pro3</div>
          <div class="product-m-price">¥499</div>
          <div class="product-m-lsc">可抵 499 LSC</div>
        </div>
      </div>
    </div>

    <div class="section-head">
      <div class="section-title">附近商家</div>
      <div class="section-more">全部 <span class="icon icon-sm" data-i="arrowRight"></span></div>
    </div>
    ${jhCard}
    ${ypCard}
    ${xzCard}
    ${ysCard}

    <div style="text-align:center;margin:18px 0 6px;font-size:11px;color:var(--c-text-3);">— 链盛通LSC · 消费创造权益 —</div>
  </div>`;
  renderIcons(document.getElementById('screen-home'));
}

/* ===== 商城 ===== */
const PRODUCT_LIST = [
  {name:'精品双人套餐·周末限定', price:399, tag:'热销', merchant:'锦华餐饮连锁·总店', sales:1280, cat:'餐饮', aiScore:96, stock:50,
   desc:'周末限定双人精选套餐, 包含招牌菜4道+主食2份+甜品2份。精选当季食材, 由主厨匠心烹制。仅限周末堂食享用。'},
  {name:'招牌全家福套餐', price:588, tag:'', merchant:'锦华餐饮连锁·总店', sales:856, cat:'餐饮', aiScore:94, stock:30,
   desc:'全家福套餐含招牌菜8道, 适合4-6人享用, 含招牌烤鸭、松鼠桂鱼等经典菜式。节假日家庭聚餐首选。'},
  {name:'明前龙井·礼盒装250g', price:888, tag:'新品', merchant:'御品茶业工坊', sales:234, cat:'茶酒', aiScore:98, stock:100,
   desc:'2026明前龙井, 杭州西湖核心产区, 礼盒装250g。茶汤嫩绿明亮, 栗香持久, 回甘绵长。附防伪溯源码。'},
  {name:'智能蓝牙耳机Pro3', price:499, tag:'', merchant:'数码优选旗舰店', sales:3420, cat:'数码', aiScore:92, stock:200,
   desc:'主动降噪, 蓝牙5.3, 续航36小时, IPX5防水, 含Type-C快充。搭配LSC可享额外9折。'},
  {name:'进口澳洲和牛M9·500g', price:698, tag:'生鲜', merchant:'环球生鲜直送', sales:612, cat:'生鲜', aiScore:95, stock:80,
   desc:'澳洲进口和牛M9级, 500g真空冷链包装, 雪花纹理丰富, 适合煎烤。顺丰冷链次日达, 签收保持0-4℃。'},
  {name:'季节限定·蟹粉小笼礼盒', price:288, tag:'', merchant:'老字号糕点坊', sales:1560, cat:'餐饮', aiScore:93, stock:120,
   desc:'当季蟹粉手工小笼, 礼盒装12只装, 含秘制姜醋。老字号手工包制, 顺丰冷链次日达。'},
];

function renderMall() {
  const products = PRODUCT_LIST;
  const cards = products.map((p,i)=>`
    <div class="mall-card" onclick="openProduct(${i})">
      <div class="mall-card-img"><span class="icon" data-i="product"></span>${p.tag?`<span class="tag tag-accent" style="position:absolute;top:8px;left:8px;">${p.tag}</span>`:''}</div>
      <div style="padding:10px;">
        <div style="font-size:13px;font-weight:600;line-height:1.3;height:34px;overflow:hidden;">${p.name}</div>
        <div class="flex items-center justify-between mt-2">
          <span style="font-size:17px;font-weight:700;color:var(--c-accent-deep);font-family:var(--ff-mono);">¥${p.price}</span>
          <span class="tag tag-available">可抵${p.price}LSC</span>
        </div>
      </div>
    </div>`).join('');
  document.getElementById('screen-mall').innerHTML = `
  <div class="mall-search">
    <div class="flex items-center gap-2" style="background:var(--c-bg-soft);border-radius:18px;padding:8px 14px;">
      <span class="icon icon-sm" data-i="search" style="color:var(--c-text-3);"></span>
      <input placeholder="搜索商品/商家" style="flex:1;border:none;background:transparent;outline:none;font-size:13px;">
    </div>
  </div>
  <div class="mall-cats">
    <span class="cat-chip active">全部</span>
    <span class="cat-chip">餐饮</span>
    <span class="cat-chip">生鲜</span>
    <span class="cat-chip">数码</span>
    <span class="cat-chip">茶酒</span>
    <span class="cat-chip">服饰</span>
  </div>
  <div class="mall-grid">${cards}</div>
  <div style="text-align:center;padding:20px;font-size:11px;color:var(--c-text-3);">— 已经到底啦 —</div>`;
  renderIcons(document.getElementById('screen-mall'));
}

/* ===== 扫码页 ===== */
function renderScan() {
  document.getElementById('screen-scan').innerHTML = `
  <div class="scan-screen">
    <div style="padding:16px 20px;display:flex;align-items:center;gap:12px;">
      <span class="icon" data-i="back" onclick="showScreen('home')" style="color:#fff;width:22px;height:22px;"></span>
      <span style="font-size:16px;font-weight:600;">扫商家收款码</span>
      <span style="margin-left:auto;font-size:12px;opacity:0.7;">相册</span>
    </div>
    <div class="scan-area">
      <div class="scan-frame">
        <div class="scan-corner scan-tl"></div>
        <div class="scan-corner scan-tr"></div>
        <div class="scan-corner scan-bl"></div>
        <div class="scan-corner scan-br"></div>
        <div class="scan-line"></div>
      </div>
    </div>
    <div style="text-align:center;font-size:12px;opacity:0.7;margin-bottom:16px;">将商家收款码放入框内即可自动扫描</div>
    <div class="scan-actions">
      <div class="scan-btn"><span class="icon" data-i="location"></span>附近商家</div>
      <div class="scan-btn primary" onclick="simulateScan()"><span class="icon" data-i="qr"></span>模拟扫码支付</div>
    </div>
  </div>`;
  renderIcons(document.getElementById('screen-scan'));
}
function simulateScan() {
  // 弹出混合支付弹窗
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.style.background = 'rgba(0,0,0,0.6)';
  mask.innerHTML = `<div class="modal" style="width:90%;max-width:340px;border-radius:18px;">
    <div style="padding:20px 16px 8px;text-align:center;background:linear-gradient(135deg,var(--c-primary-tint),#fff);border-radius:18px 18px 0 0;">
      <div style="width:50px;height:50px;border-radius:12px;background:linear-gradient(135deg,var(--c-accent),var(--c-accent-deep));margin:0 auto 10px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700;font-size:20px;">锦</div>
      <div style="font-weight:600;">锦华餐饮连锁·总店</div>
      <div class="text-xs text-muted">世纪大道100号</div>
    </div>
    <div class="modal-body">
      <label class="field-label">消费金额 (元)</label>
      <div class="input-group mb-3"><span style="color:var(--c-accent-deep);font-weight:700;font-size:20px;">¥</span><input class="input" id="scan-amount" value="100" style="font-size:20px;font-weight:700;" oninput="calcHybrid()"></div>
      <div class="alert alert-info" style="font-size:12px;"><span class="icon icon-sm" data-i="unlock"></span>仅<strong>人民币实付部分</strong>按¥1=1LSC等量发行进入锁定池, LSC消费抵扣部分<strong>不</strong>触发发行</div>
      <div class="hybrid-pay" style="margin:14px 0 0;">
        <div class="flex justify-between text-sm"><span>LSC 抵扣 (不发行)</span><b id="hybrid-lsc" style="color:var(--c-available);">0 LSC</b></div>
        <div class="hybrid-bar">
          <div class="hybrid-fill" id="hybrid-fill" style="width:0%;"></div>
          <div class="hybrid-knob" id="hybrid-knob" style="left:0%;"></div>
        </div>
        <div class="pay-summary"><span>人民币实付 (发行依据)</span><b id="hybrid-rmb" style="color:var(--c-accent-deep);">¥100.00</b></div>
        <div class="pay-summary"><span>发行 LSC (=人民币实付)</span><b id="hybrid-get" style="color:var(--c-locked);">+100.00</b></div>
      </div>
    </div>
    <div class="modal-foot">
      <button class="btn btn-outline btn-sm" onclick="this.closest('.modal-mask').remove()">取消</button>
      <button class="btn btn-primary btn-sm" onclick="paySuccess(this)">确认支付 ¥<span id="pay-final">100.00</span></button>
    </div>
  </div>`;
  document.body.appendChild(mask);
  renderIcons(mask);
  // 滑块交互
  setupHybridSlider();
  if (!window.calcHybrid) {
    window.calcHybrid = function() {
      const total = parseFloat(document.getElementById('scan-amount').value) || 0;
      const lscUse = window._hybridPct * Math.min(total, 8640.5);
      const rmbPay = Math.max(0, total - lscUse);
      document.getElementById('hybrid-lsc').textContent = lscUse.toFixed(2) + ' LSC';
      document.getElementById('hybrid-rmb').textContent = '¥' + rmbPay.toFixed(2);
      // 发行规则严格化: 仅人民币实付部分 1:1 发行 LSC, LSC抵扣部分永不发行
      document.getElementById('hybrid-get').textContent = '+' + rmbPay.toFixed(2);
      document.getElementById('pay-final').textContent = rmbPay.toFixed(2);
      // 将本次结算参数存入最近 modal, 供 paySuccess 使用, 避免硬编码 100
      const mask = document.body.querySelector('.modal-mask:last-of-type');
      if (mask) {
        mask.dataset.settleTotal = total.toFixed(2);
        mask.dataset.settleLscUse = lscUse.toFixed(2);
        mask.dataset.settleRmb = rmbPay.toFixed(2);
        mask.dataset.settleIssue = rmbPay.toFixed(2);
      }
    };
  }
  if (!window._hybridPct) window._hybridPct = 0;
  // 初始化一次, 同步结算参数 (使用setTimeout会在modal销毁后触发，导致元素不存在报错)
  if (typeof calcHybrid === 'function') {
    try { calcHybrid(); } catch(_e) { /* 元素尚不存在则跳过, 下一次 input/滑块 触发时会再算 */ }
  }
}
function setupHybridSlider() {
  const bar = document.querySelector('.hybrid-bar');
  if (!bar) return;
  let dragging = false;
  const update = (e) => {
    const rect = bar.getBoundingClientRect();
    const x = (e.touches?e.touches[0].clientX:e.clientX) - rect.left;
    let pct = Math.max(0, Math.min(1, x/rect.width));
    window._hybridPct = pct;
    document.getElementById('hybrid-fill').style.width = (pct*100)+'%';
    document.getElementById('hybrid-knob').style.left = (pct*100)+'%';
    if (window.calcHybrid) window.calcHybrid();
  };
  bar.addEventListener('mousedown', e=>{dragging=true;update(e);});
  document.addEventListener('mousemove', e=>{if(dragging)update(e);});
  document.addEventListener('mouseup', ()=>dragging=false);
}
function paySuccess(btn) {
  const mask = btn.closest('.modal-mask');
  const total   = parseFloat(mask?.dataset?.settleTotal) || 100;
  const lscUse  = parseFloat(mask?.dataset?.settleLscUse) || 0;
  const rmbPay  = parseFloat(mask?.dataset?.settleRmb)   || (total - lscUse);
  const issue   = parseFloat(mask?.dataset?.settleIssue) || rmbPay;
  const totalFmt  = total.toFixed(2);
  const lscUFmt   = lscUse.toFixed(2);
  const rmbFmt    = rmbPay.toFixed(2);
  const issueFmt  = issue.toFixed(2);
  const payMode   = (rmbPay > 0 && lscUse > 0) ? '混合支付'
                  : (rmbPay > 0) ? '人民币支付'
                  : 'LSC全额抵扣';
  // 严格规则表述:
  //   若 rmbPay>0 → 按人民币实付额发行 LSC, 锁定池 +issue
  //   若 rmbPay=0 → LSC抵扣, 无发行, 无锁定池入账
  const issueBlock = rmbPay > 0
    ? `<div class="alert alert-success mt-4" style="font-size:12px;text-align:left;"><span class="icon icon-sm" data-i="unlock"></span><b>${issueFmt} LSC</b> 已进入您的<b>锁定池</b> (来源: 人民币实付 ¥${rmbFmt} 1:1发行), 将按每日动态释放至可用池, 释放速率约0.0468%。</div>`
    : `<div class="alert alert-warning mt-4" style="font-size:12px;text-align:left;"><span class="icon icon-sm" data-i="scan"></span>本次为<b>LSC消费抵扣(${lscUFmt} LSC)</b>, 不触发 LSC 发行, 无锁定池入账。发行仅在人民币实际支付时按 ¥1=1LSC 产生。</div>`;
  const payBreak = lscUse > 0
    ? `订单 ¥${totalFmt} · 抵扣 ${lscUFmt} LSC · 人民币实付 ¥${rmbFmt} · <span style="color:var(--c-locked);">发行 +${issueFmt} LSC</span>`
    : `人民币支付 ¥${rmbFmt} · <span style="color:var(--c-locked);">发行 +${issueFmt} LSC</span>`;
  mask.querySelector('.modal').innerHTML = `<div style="padding:42px 24px 28px;text-align:center;">
    <div style="width:72px;height:72px;border-radius:50%;background:var(--c-available);margin:0 auto 16px;display:flex;align-items:center;justify-content:center;">
      <span class="icon icon-xl" data-i="check" style="width:36px;height:36px;color:#fff;"></span>
    </div>
    <div style="font-size:18px;font-weight:700;">支付成功 · ${payMode}</div>
    <div class="text-muted text-sm mt-2" style="line-height:1.7;">${payBreak}</div>
    ${issueBlock}
    <button class="btn btn-primary btn-block mt-4" onclick="this.closest('.modal-mask').remove();showScreen('wallet');">查看我的钱包</button>
  </div>`;
  renderIcons(mask);
}

/* ===== 付款码 ===== */
function renderPaycode() {
  document.getElementById('screen-paycode').innerHTML = `
  <div class="paycode-screen">
    <div style="padding:8px 0;display:flex;align-items:center;gap:12px;">
      <span class="icon" data-i="back" onclick="showScreen('home')" style="width:22px;height:22px;"></span>
      <span style="font-size:16px;font-weight:600;">LSC 付款码</span>
    </div>
    <div class="paycode-tabs">
      <div class="paycode-tab active">LSC付款码 (不发行)</div>
      <div class="paycode-tab">混合付款 (按人民币实付发行)</div>
    </div>
    <div class="paycode-amount">
      <div class="pa-label">LSC 可用余额</div>
      <div class="pa-val">8,640.50</div>
    </div>
    <div class="paycode-qr">
      <div class="qr-grid-m">
        <div class="qr-corner qr-tl"></div>
        <div class="qr-corner qr-tr"></div>
        <div class="qr-corner qr-bl"></div>
        <div class="center-logo">李</div>
      </div>
    </div>
    <div class="paycode-refresh">
      <span class="icon icon-sm" data-i="refresh"></span> 30秒自动刷新 · 含防伪签名
    </div>
    <div style="margin-top:24px;background:rgba(255,255,255,0.1);border-radius:14px;padding:14px;font-size:12px;">
      <div style="font-weight:600;margin-bottom:6px;">使用说明</div>
      <div style="opacity:0.85;line-height:1.6;">向商家出示此付款码, 商家扫描后输入金额即从<b>可用余额</b>中扣减相应 LSC。<br><strong>重要</strong>: 纯 LSC 付款属于消费抵扣,<b>不会</b>触发 LSC 发行。只有人民币支付或混合付款中的人民币实付部分, 才按 ¥1=1LSC 发行进入锁定池。</div>
    </div>
  </div>`;
  renderIcons(document.getElementById('screen-paycode'));
}

/* ===== 钱包 ===== */
function renderWallet() {
  const txs = [
    {type:'释放', icon:'unlock', color:'var(--c-available)', amount:'+38.50', time:'今日 02:03', orderId:'REL20260827002'},
    {type:'消费发行(人民币实付)', icon:'mall', color:'var(--c-locked)', amount:'+100.00', time:'昨日 18:24', orderId:'ORD20260826008'},
    {type:'线下消费(LSC抵扣,不发行)', icon:'scan', color:'var(--c-info)', amount:'-50.00', time:'昨日 12:30', orderId:'OFF20260826003'},
    {type:'推广奖励', icon:'promotion', color:'var(--c-accent)', amount:'+10.00', time:'前天 15:20', orderId:'PROMO20260824005'},
    {type:'每日释放', icon:'unlock', color:'var(--c-available)', amount:'+37.80', time:'前天 02:03', orderId:'REL20260825002'},
  ];
  const txHtml = txs.map(t=>`
    <div class="tx-item">
      <div class="tx-icon" style="background:${t.color}22;color:${t.color};"><span class="icon" data-i="${t.icon}"></span></div>
      <div class="tx-info">
        <div class="tx-title">${t.type}</div>
        <div class="tx-time">${t.time} · ${t.orderId}</div>
      </div>
      <div class="tx-amount" style="color:${t.amount.startsWith('+')?'var(--c-available)':'var(--c-text-1)'};">${t.amount}</div>
    </div>`).join('');
  document.getElementById('screen-wallet').innerHTML = `
  <div class="wallet-screen-top">
    <div style="padding:4px 0 0;font-size:13px;opacity:0.8;">我的 LSC 钱包</div>
    <div class="wallet-bal">
      <div class="wb-label">可用余额 (AVAILABLE)</div>
      <div class="wb-val">8,640.50</div>
      <div class="wb-sub">
        <span>锁定池<b style="color:var(--c-locked-soft);">15,200.00</b></span>
        <span>总资产<b>23,840.50</b></span>
      </div>
    </div>
  </div>
  <div class="wallet-tabs">
    <div class="wallet-tab active">全部</div>
    <div class="wallet-tab">释放</div>
    <div class="wallet-tab">消费</div>
    <div class="wallet-tab">推广</div>
  </div>
  <div class="pg" style="padding-top:8px;">
    <!-- 个人LSC流转链路图 -->
    <div style="background:#fff;border-radius:14px;padding:12px;box-shadow:var(--sh-card);margin-bottom:14px;">
      <div style="font-size:12px;font-weight:600;color:var(--c-primary);margin-bottom:8px;display:flex;align-items:center;gap:6px;">
        <span class="icon icon-sm" data-i="flow" style="color:var(--c-primary);"></span>我的LSC流转链路
      </div>
      <div style="overflow-x:auto;">
      <svg viewBox="0 0 560 170" style="width:100%;min-width:520px;height:140px;">
        <defs>
          <marker id="arrow-user-1" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="var(--c-locked)"/></marker>
          <marker id="arrow-user-2" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="var(--c-available)"/></marker>
          <marker id="arrow-user-3" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="var(--c-info)"/></marker>
        </defs>
        <!-- 节点:消费发行 -->
        <rect x="6" y="58" width="94" height="54" rx="10" fill="var(--c-primary-tint,rgba(14,77,74,0.08))" stroke="var(--c-primary)" stroke-width="1.5"/>
        <text x="53" y="82" font-size="11" font-weight="700" fill="var(--c-primary)" text-anchor="middle">人民币消费发行</text>
        <text x="53" y="98" font-size="9" fill="var(--c-text-3)" text-anchor="middle" font-family="var(--ff-mono)">+24,800</text>
        <!-- 节点:锁定池 -->
        <rect x="156" y="50" width="94" height="70" rx="10" fill="rgba(255,177,61,0.10)" stroke="var(--c-warning)" stroke-width="2"/>
        <text x="203" y="76" font-size="12" font-weight="700" fill="var(--c-warning)" text-anchor="middle">🔒 锁定池</text>
        <text x="203" y="94" font-size="11" fill="var(--c-text-2)" text-anchor="middle" font-family="var(--ff-mono)">15,200</text>
        <text x="203" y="108" font-size="8" fill="var(--c-text-3)" text-anchor="middle">日释 0.047%</text>
        <!-- 节点:可用池 -->
        <rect x="306" y="50" width="94" height="70" rx="10" fill="rgba(45,179,128,0.10)" stroke="var(--c-available)" stroke-width="2"/>
        <text x="353" y="76" font-size="12" font-weight="700" fill="var(--c-available)" text-anchor="middle">✓ 可用池</text>
        <text x="353" y="94" font-size="11" fill="var(--c-text-2)" text-anchor="middle" font-family="var(--ff-mono)">8,640</text>
        <text x="353" y="108" font-size="8" fill="var(--c-text-3)" text-anchor="middle">可消费/转账</text>
        <!-- 节点:线下消费 -->
        <rect x="456" y="16" width="94" height="50" rx="10" fill="rgba(59,130,246,0.10)" stroke="var(--c-info)" stroke-width="1.5"/>
        <text x="503" y="38" font-size="11" font-weight="700" fill="var(--c-info)" text-anchor="middle">线下消费</text>
        <text x="503" y="54" font-size="9" fill="var(--c-text-3)" text-anchor="middle" font-family="var(--ff-mono)">-6,400</text>
        <!-- 节点:推广奖励 -->
        <rect x="456" y="102" width="94" height="50" rx="10" fill="rgba(200,162,75,0.12)" stroke="var(--c-accent)" stroke-width="1.5"/>
        <text x="503" y="124" font-size="11" font-weight="700" fill="var(--c-accent-deep)" text-anchor="middle">推广奖励</text>
        <text x="503" y="140" font-size="9" fill="var(--c-text-3)" text-anchor="middle" font-family="var(--ff-mono)">+320</text>
        <!-- 连线 -->
        <line x1="100" y1="85" x2="154" y2="85" stroke="var(--c-locked)" stroke-width="2" marker-end="url(#arrow-user-1)"/>
        <text x="127" y="80" font-size="8" fill="var(--c-locked)" font-weight="600" text-anchor="middle">人民币¥1=1LSC发行</text>
        <line x1="250" y1="85" x2="304" y2="85" stroke="var(--c-available)" stroke-width="2.5" marker-end="url(#arrow-user-2)"/>
        <text x="277" y="80" font-size="8" fill="var(--c-available)" font-weight="600" text-anchor="middle">每日缓释</text>
        <path d="M 374 68 Q 430 40 454 40" fill="none" stroke="var(--c-info)" stroke-width="2" marker-end="url(#arrow-user-3)"/>
        <text x="415" y="44" font-size="8" fill="var(--c-info)" font-weight="600" text-anchor="middle">扫码抵扣·不发行</text>
        <path d="M 503 102 Q 503 90 400 90 Q 360 90 360 86" fill="none" stroke="var(--c-accent)" stroke-width="1.5" stroke-dasharray="3 2" marker-end="url(#arrow-user-2)"/>
        <text x="450" y="94" font-size="7" fill="var(--c-accent-deep)" font-weight="600" text-anchor="middle">直接入账</text>
        <!-- 循环虚线 -->
        <path d="M 40 58 Q 40 10 300 10 Q 500 10 500 16" fill="none" stroke="var(--c-text-3)" stroke-width="1" stroke-dasharray="3 3" opacity="0.4" marker-end="url(#arrow-user-3)"/>
        <text x="280" y="4" font-size="7" fill="var(--c-text-3)" text-anchor="middle" opacity="0.7">消费循环</text>
      </svg>
      </div>
    </div>
    <div style="background:#fff;border-radius:14px;padding:0 16px;box-shadow:var(--sh-card);margin-bottom:16px;">
      ${txHtml}
    </div>
    <div class="card chart-card" style="text-align:center;">
      <div class="card-title" style="font-size:14px;">近7日释放趋势</div>
      <div style="height:120px;margin-top:8px;">${(()=>{
        const data=[32,36,37,36,38,38,38];
        const w=280,h=120,pad={l:10,r:10,t:10,b:20},iw=w-pad.l-pad.r,ih=h-pad.t-pad.b;
        const max=Math.max(...data),min=Math.min(...data),range=max-min||1;
        const xAt=i=>pad.l+i*iw/(data.length-1);
        const yAt=v=>pad.t+ih-((v-min)/range)*ih;
        const pts=data.map((v,i)=>`${xAt(i)},${yAt(v)}`).join(' ');
        return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">
          <polygon points="${pad.l},${pad.t+ih} ${pts} ${xAt(data.length-1)},${pad.t+ih}" fill="var(--c-available)" opacity="0.15"/>
          <polyline points="${pts}" fill="none" stroke="var(--c-available)" stroke-width="2.5"/>
          ${data.map((v,i)=>`<circle cx="${xAt(i)}" cy="${yAt(v)}" r="3" fill="var(--c-available)"/>`).join('')}
        </svg>`;
      })()}</div>
      <div class="text-xs text-available mt-1">本周累计释放 +245.50 LSC</div>
    </div>
    <div style="text-align:center;padding:14px;font-size:11px;color:var(--c-text-3);">锁定池LSC按每日动态释放速率缓释</div>
  </div>`;
  renderIcons(document.getElementById('screen-wallet'));
}

/* ===== 我的 ===== */
function renderMe() {
  document.getElementById('screen-me').innerHTML = `
  <div class="me-top">
    <div class="me-user">
      <div class="me-avatar">李</div>
      <div>
        <div class="me-name">李先生</div>
        <div class="me-tag">VIP会员 · 已实名认证</div>
      </div>
      <span class="icon" data-i="arrowRight" style="margin-left:auto;opacity:0.7;"></span>
    </div>
    <div class="me-stats">
      <div class="me-stat"><div class="ms-val">248</div><div class="ms-label">推荐人数</div></div>
      <div class="me-stat"><div class="ms-val" style="color:var(--c-accent-soft);">3,820</div><div class="ms-label">推广奖励</div></div>
      <div class="me-stat"><div class="ms-val">96</div><div class="ms-label">消费订单</div></div>
    </div>
  </div>
  <div class="me-menu">
    <div class="me-menu-item" onclick="showScreen('orders')"><span class="icon" data-i="order"></span><span class="mm-text">我的订单</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
    <div class="me-menu-item" onclick="showScreen('promo')"><span class="icon" data-i="promotion"></span><span class="mm-text">推广中心</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
    <div class="me-menu-item" onclick="showScreen('wallet')"><span class="icon" data-i="wallet"></span><span class="mm-text">LSC钱包</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
    <div class="me-menu-item" onclick="showScreen('ai')"><span class="icon" data-i="ai"></span><span class="mm-text">AI客服</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
  </div>
  <div class="me-menu" style="margin-top:12px;">
    <div class="me-menu-item"><span class="icon" data-i="user"></span><span class="mm-text">实名认证</span><span class="tag tag-success">已认证</span></div>
    <div class="me-menu-item"><span class="icon" data-i="location"></span><span class="mm-text">收货地址</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
    <div class="me-menu-item"><span class="icon" data-i="system"></span><span class="mm-text">设置</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
    <div class="me-menu-item"><span class="icon" data-i="doc"></span><span class="mm-text">入驻协议 / 隐私政策</span><span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span></div>
  </div>
  <div class="pg">
    <button class="m-btn m-btn-outline m-btn-block" style="color:var(--c-text-3);margin-top:20px;">退出登录</button>
    <div style="text-align:center;margin-top:14px;font-size:11px;color:var(--c-text-3);">链盛通LSC V6.2 · 消费创造权益</div>
  </div>`;
  renderIcons(document.getElementById('screen-me'));
}

/* ===== 订单 ===== */
function renderOrders() {
  document.getElementById('screen-orders').innerHTML = `
  <div style="background:#fff;padding:8px 16px;display:flex;align-items:center;gap:12px;box-shadow:var(--sh-sm);">
    <span class="icon" data-i="back" onclick="showScreen('me')" style="width:22px;height:22px;color:var(--c-text-1);"></span>
    <span style="font-size:16px;font-weight:600;">我的订单</span>
  </div>
  <div class="order-tabs">
    <div class="order-tab active">全部</div>
    <div class="order-tab">消费</div>
    <div class="order-tab">兑换</div>
    <div class="order-tab">退款</div>
  </div>
  <div class="order-card">
    <div class="order-card-head"><span class="order-no">ORD20260827001</span><span class="tag tag-success">已完成</span></div>
    <div class="order-card-body">
      <div class="order-card-img"><span class="icon" data-i="product"></span></div>
      <div style="flex:1;">
        <div style="font-size:14px;font-weight:500;">精品双人套餐·周末限定</div>
        <div class="text-xs text-muted mt-1">锦华餐饮连锁·总店</div>
        <div class="text-xs text-available mt-1">获得 399.00 LSC (锁定池)</div>
      </div>
      <div style="text-align:right;"><div style="font-family:var(--ff-mono);font-weight:700;">¥399</div><div class="text-xs text-muted">×1</div></div>
    </div>
    <div class="order-card-foot">
      <span class="text-xs text-muted">2026-08-27 18:24</span>
      <div class="flex gap-2"><button class="m-btn m-btn-outline" style="padding:6px 14px;font-size:12px;">查看详情</button></div>
    </div>
  </div>
  <div class="order-card">
    <div class="order-card-head"><span class="order-no">OFF20260826003</span><span class="tag tag-success">已核销</span></div>
    <div class="order-card-body">
      <div class="order-card-img" style="background:linear-gradient(135deg,var(--c-accent-soft),#fff);"><span class="icon" data-i="scan" style="color:var(--c-accent-deep);"></span></div>
      <div style="flex:1;">
        <div style="font-size:14px;font-weight:500;">线下扫码消费</div>
        <div class="text-xs text-muted mt-1">锦华餐饮连锁·总店</div>
        <div class="text-xs mt-1" style="color:var(--c-info);">LSC抵扣 50 · 人民币实付 ¥50</div>
        <div class="text-xs mt-0.5" style="color:var(--c-locked);">发行 +50.00 LSC (锁定池, 依据人民币实付 1:1)</div>
      </div>
      <div style="text-align:right;"><div style="font-family:var(--ff-mono);font-weight:700;">¥100</div><div class="text-xs text-muted">混合支付</div></div>
    </div>
    <div class="order-card-foot"><span class="text-xs text-muted">2026-08-26 12:30</span><div class="flex gap-2"><button class="m-btn m-btn-outline" style="padding:6px 14px;font-size:12px;">再次消费</button></div></div>
  </div>
  <div class="order-card">
    <div class="order-card-head"><span class="order-no">EXC20260825002</span><span class="tag tag-warning">待核销</span></div>
    <div class="order-card-body">
      <div class="order-card-img" style="background:linear-gradient(135deg,var(--c-available-tint),#fff);"><span class="icon" data-i="unlock" style="color:var(--c-available);"></span></div>
      <div style="flex:1;">
        <div style="font-size:14px;font-weight:500;">权益兑换</div>
        <div class="text-xs text-muted mt-1">御品茶业工坊</div>
        <div class="text-xs text-muted mt-1">消费抵扣 200 LSC</div>
      </div>
      <div style="text-align:right;"><div style="font-family:var(--ff-mono);font-weight:700;color:var(--c-available);">-200</div><div class="text-xs text-muted">LSC</div></div>
    </div>
    <div class="order-card-foot"><span class="text-xs text-muted">2026-08-25 15:20</span><div class="flex gap-2"><button class="m-btn m-btn-outline" style="padding:6px 14px;font-size:12px;">等待商家核销</button></div></div>
  </div>`;
  renderIcons(document.getElementById('screen-orders'));
}

/* ===== 推广 ===== */
function renderPromo() {
  document.getElementById('screen-promo').innerHTML = `
  <div style="background:#fff;padding:8px 16px;display:flex;align-items:center;gap:12px;box-shadow:var(--sh-sm);">
    <span class="icon" data-i="back" onclick="showScreen('me')" style="width:22px;height:22px;color:var(--c-text-1);"></span>
    <span style="font-size:16px;font-weight:600;">推广中心</span>
  </div>
  <div class="promo-hero" style="margin-top:8px;">
    <h2>邀请好友 · 赚10%奖励</h2>
    <p>好友首单消费, 您即获消费金额10%的LSC奖励</p>
  </div>
  <div class="promo-qr-card">
    <div style="font-size:13px;color:var(--c-text-2);margin-bottom:10px;">我的专属推广码</div>
    <div style="width:160px;height:160px;background:#fff;border-radius:14px;padding:12px;margin:0 auto;border:1px solid var(--c-border-soft);position:relative;">
      <div style="width:100%;height:100%;background-image:linear-gradient(#1a1a1a 1px,transparent 1px),linear-gradient(90deg,#1a1a1a 1px,transparent 1px);background-size:6px 6px;position:relative;">
        <div style="position:absolute;top:4px;left:4px;width:22px;height:22px;background:#1a1a1a;"></div>
        <div style="position:absolute;top:4px;right:4px;width:22px;height:22px;background:#1a1a1a;"></div>
        <div style="position:absolute;bottom:4px;left:4px;width:22px;height:22px;background:#1a1a1a;"></div>
        <div style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:28px;height:28px;background:var(--c-primary);border-radius:6px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700;font-size:12px;">李</div>
      </div>
    </div>
    <div class="flex gap-2 justify-center mt-4">
      <button class="m-btn m-btn-outline">复制链接</button>
      <button class="m-btn m-btn-accent">保存图片</button>
    </div>
  </div>
  <div class="pg">
    <div class="section-head"><div class="section-title">推广战绩</div></div>
    <div class="flex gap-2 mb-4">
      <div style="flex:1;background:#fff;border-radius:12px;padding:14px;text-align:center;box-shadow:var(--sh-card);"><div style="font-size:22px;font-weight:700;font-family:var(--ff-mono);color:var(--c-primary);">248</div><div class="text-xs text-muted mt-1">已邀请</div></div>
      <div style="flex:1;background:#fff;border-radius:12px;padding:14px;text-align:center;box-shadow:var(--sh-card);"><div style="font-size:22px;font-weight:700;font-family:var(--ff-mono);color:var(--c-available);">186</div><div class="text-xs text-muted mt-1">活跃用户</div></div>
      <div style="flex:1;background:#fff;border-radius:12px;padding:14px;text-align:center;box-shadow:var(--sh-card);"><div style="font-size:22px;font-weight:700;font-family:var(--ff-mono);color:var(--c-accent-deep);">3,820</div><div class="text-xs text-muted mt-1">累计LSC</div></div>
    </div>
    <div class="section-head"><div class="section-title">奖励记录</div></div>
    <div style="background:#fff;border-radius:12px;padding:0 14px;box-shadow:var(--sh-card);">
      <div class="tx-item"><div class="tx-icon" style="background:var(--c-accent-soft);color:var(--c-accent-deep);"><span class="icon" data-i="promotion"></span></div><div class="tx-info"><div class="tx-title">好友首单消费</div><div class="tx-time">U10386 · ¥880</div></div><div class="tx-amount" style="color:var(--c-available);">+88.00</div></div>
      <div class="tx-item"><div class="tx-icon" style="background:var(--c-accent-soft);color:var(--c-accent-deep);"><span class="icon" data-i="promotion"></span></div><div class="tx-info"><div class="tx-title">好友首单消费</div><div class="tx-time">U10392 · ¥560</div></div><div class="tx-amount" style="color:var(--c-available);">+56.00</div></div>
      <div class="tx-item"><div class="tx-icon" style="background:var(--c-danger);color:#fff;"><span class="icon" data-i="promotion"></span></div><div class="tx-info"><div class="tx-title">好友退款扣回</div><div class="tx-time">U10415 · ¥320</div></div><div class="tx-amount" style="color:var(--c-danger);">-32.00</div></div>
    </div>
  </div>`;
  renderIcons(document.getElementById('screen-promo'));
}

/* ===== AI客服 ===== */
function renderAI() {
  document.getElementById('screen-ai').innerHTML = `
  <div class="ai-chat-screen">
    <div class="ai-chat-head">
      <span class="icon" data-i="back" onclick="showScreen('me')" style="width:22px;height:22px;color:var(--c-text-1);"></span>
      <div class="avatar avatar-sm avatar-gold">AI</div>
      <div><div style="font-size:14px;font-weight:600;">智能客服</div><div class="text-xs text-available">● 在线</div></div>
      <span style="margin-left:auto;font-size:12px;color:var(--c-primary);">转人工</span>
    </div>
    <div class="ai-chat-body" id="chat-body">
      <div class="chat-bubble chat-ai">您好, 我是链盛通智能客服。可以为您解答LSC规则、消费、释放、核销等问题。请问有什么可以帮您?</div>
      <div class="chat-bubble chat-me">LSC是怎么释放的?</div>
      <div class="chat-bubble chat-ai">LSC释放机制:<br><br>1️⃣ 消费后获得LSC进入<b>锁定池</b><br>2️⃣ 每日凌晨2点按动态速率释放至<b>可用池</b><br>3️⃣ 释放速率 rate 由全网核销率 k 决定:<br>· k≤0.5%: rate=0.06%<br>· k≥1.0%: rate=0.03%<br>· 0.5%&lt;k&lt;1.0%: rate=0.09%-0.06×k<br><br>当前 k=0.72%, rate=0.0468%</div>
      <div class="chat-bubble chat-me">锁定池和可用池有什么区别?</div>
      <div class="chat-bubble chat-ai">🔹 <b>锁定池</b>: 消费获得, 每日动态缓释, 不可直接消费<br>🔹 <b>可用池</b>: 可消费抵扣、线下扫码、推广奖励来源<br><br>简单理解: 锁定池是"正在释放中", 可用池是"可立即使用"。</div>
    </div>
    <div class="ai-chat-input">
      <input class="input" placeholder="输入您的问题..." style="flex:1;">
      <button class="m-btn m-btn-primary" style="padding:8px 14px;"><span class="icon icon-sm" data-i="arrowRight" style="width:18px;height:18px;"></span></button>
    </div>
  </div>`;
  renderIcons(document.getElementById('screen-ai'));
}

/* ===== 商品详情 ===== */
let _curProductIdx = 0;
function openProduct(idx) {
  _curProductIdx = idx;
  renderProduct(idx);
  showScreen('product');
}
function renderProduct(idx) {
  idx = (idx == null) ? _curProductIdx : idx;
  const p = PRODUCT_LIST[idx] || PRODUCT_LIST[0];
  document.getElementById('screen-product').innerHTML = `
  <div style="position:relative;">
    <div style="height:260px;background:linear-gradient(135deg,var(--c-primary-tint),var(--c-bg-soft));display:flex;align-items:center;justify-content:center;position:relative;">
      <span class="icon" data-i="product" style="width:80px;height:80px;color:var(--c-primary);opacity:0.4;"></span>
      <span class="icon" data-i="back" onclick="showScreen('mall')" style="position:absolute;top:14px;left:16px;width:26px;height:26px;color:var(--c-text-1);background:#fff;border-radius:50%;padding:4px;box-shadow:var(--sh-sm);"></span>
      ${p.tag?`<span class="tag tag-accent" style="position:absolute;top:14px;right:16px;">${p.tag}</span>`:''}
    </div>
  </div>
  <div style="background:#fff;padding:16px;margin-top:-16px;border-radius:18px 18px 0 0;position:relative;">
    <div style="font-size:18px;font-weight:700;">${p.name}</div>
    <div class="flex items-center gap-2 mt-2">
      <span style="font-size:24px;font-weight:700;color:var(--c-accent-deep);font-family:var(--ff-mono);">¥${p.price}</span>
      <span class="tag tag-available">可抵 ${p.price} LSC</span>
    </div>
    <div class="text-xs text-muted mt-2">${p.merchant} · 月销 ${p.sales}</div>
    <div class="alert alert-info mt-3" style="font-size:12px;"><span class="icon icon-sm" data-i="unlock"></span>使用<strong>人民币</strong>支付 ¥${p.price} 后, 您将获得 <strong>${p.price} LSC</strong> 进入锁定池 (¥1=1LSC)。<br>如使用可用 LSC 抵扣, 抵扣部分不产生发行, 仅实付人民币部分按 1:1 发行。每日动态释放至可用池后可用于未来消费抵扣。</div>
    <!-- 商品属性 -->
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px;">
      <div style="background:var(--c-bg-soft);border-radius:10px;padding:10px;">
        <div class="text-xs text-muted">AI 真实度评分</div>
        <div style="font-size:16px;font-weight:700;color:var(--c-primary);font-family:var(--ff-mono);">${p.aiScore}%</div>
      </div>
      <div style="background:var(--c-bg-soft);border-radius:10px;padding:10px;">
        <div class="text-xs text-muted">库存</div>
        <div style="font-size:16px;font-weight:700;color:var(--c-text-1);font-family:var(--ff-mono);">${p.stock} 件</div>
      </div>
    </div>
    <div class="section-head"><div class="section-title">商品详情</div></div>
    <div class="text-sm" style="line-height:1.7;color:var(--c-text-2);">${p.desc}</div>
    <div style="height:60px;"></div>
  </div>
  <div style="position:fixed;left:0;right:0;bottom:0;background:#fff;padding:10px 16px;border-top:1px solid var(--c-border-soft);display:flex;gap:10px;align-items:center;z-index:20;width:100%;max-width:366px;margin:0 auto;">
    <div style="text-align:center;font-size:11px;color:var(--c-text-3);"><span class="icon" data-i="chat" style="width:20px;height:20px;display:block;margin:0 auto 2px;"></span>客服</div>
    <button class="m-btn m-btn-outline" style="flex:1;" onclick="addToCart(${idx})">加入购物车</button>
    <button class="m-btn m-btn-accent" style="flex:1;" onclick="simulateScan()">立即购买</button>
  </div>`;
  renderIcons(document.getElementById('screen-product'));
}

/* 购物车 / Toast 提示 */
function addToCart(idx) {
  const p = PRODUCT_LIST[idx];
  showTip(`已加入购物车 · ${p.name}`);
}
function showTip(msg) {
  const old = document.getElementById('app-tip');
  if (old) old.remove();
  const t = document.createElement('div');
  t.id = 'app-tip';
  t.style.cssText = 'position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);background:rgba(8,46,44,0.92);color:#fff;padding:12px 20px;border-radius:12px;font-size:13px;z-index:9999;max-width:300px;text-align:center;box-shadow:0 8px 24px rgba(0,0,0,0.2);animation:fadeIn 0.3s ease;';
  t.innerHTML = `<span class="icon" data-i="check" style="width:16px;height:16px;vertical-align:middle;margin-right:6px;"></span>${msg}`;
  document.body.appendChild(t);
  renderIcons(t);
  setTimeout(()=>{ t.style.opacity='0'; t.style.transition='opacity 0.3s'; setTimeout(()=>t.remove(),300); }, 2200);
}

/* 启动: 渲染所有页面 */
renderHome(); renderMall(); renderScan(); renderWallet(); renderMe();
renderOrders(); renderPromo(); renderAI(); renderPaycode(); renderProduct();
showScreen('home');
// A11y: 为滚动容器补 tabindex=0 / role=region / aria-label, 解决 axe-core scrollable-region-focusable
if (typeof LSC !== 'undefined' && LSC.a11yEnhance) LSC.a11yEnhance(document.getElementById('content'));
