function renderIcons(scope=document) {
  scope.querySelectorAll('.icon[data-i]').forEach(el=>{
    const key = el.getAttribute('data-i');
    if (ICONS[key]) el.innerHTML = ICONS[key];
  });
}
renderIcons();

const subScreens = ['orders','promo','product'];
const navTitles = {
  home:'链盛通LSC', mall:'权益商城', scan:'扫一扫', wallet:'LSC钱包', me:'我的',
  orders:'我的订单', promo:'推广中心', product:'商品详情'
};
const darkNavScreens = ['home','scan','wallet','me','promo'];

function showScreen(name) {
  document.querySelectorAll('.screen').forEach(s=>s.classList.remove('active'));
  const el = document.getElementById('screen-'+name);
  if (el) { el.classList.add('active'); document.getElementById('wx-content').scrollTop=0; }
  // tab 高亮
  document.querySelectorAll('.wx-tab').forEach(t=>t.classList.toggle('active', t.dataset.screen===name));
  // 导航栏
  document.getElementById('wx-nav-title').textContent = navTitles[name] || '';
  const dark = darkNavScreens.includes(name);
  document.getElementById('wx-navbar').classList.toggle('dark', dark);
  document.getElementById('wx-statusbar').classList.toggle('dark', dark);
  // tabbar 显示
  document.getElementById('wx-tabbar').style.display = subScreens.includes(name) ? 'none' : 'flex';
  // 子页面显示返回按钮
  const navbar = document.getElementById('wx-navbar');
  let back = navbar.querySelector('.wx-back');
  if (subScreens.includes(name)) {
    if (!back) {
      back = document.createElement('div');
      back.className = 'wx-back';
      back.innerHTML = '<span class="icon" data-i="back"></span>';
      back.onclick = ()=>showScreen('me');
      navbar.appendChild(back);
      renderIcons(back);
    }
    back.style.display = 'flex';
  } else if (back) {
    back.style.display = 'none';
  }
}

document.getElementById('wx-tabbar').addEventListener('click', e=>{
  const t = e.target.closest('.wx-tab');
  if (t) showScreen(t.dataset.screen);
});

/* ===== 首页 ===== */
function renderHome() {
  document.getElementById('screen-home').innerHTML = `
  <div class="wx-hero">
    <div class="wx-hero-top">
      <div class="wx-hero-loc"><span class="icon" data-i="location"></span><span>上海·浦东</span></div>
      <div style="display:flex;gap:14px;"><span class="icon" data-i="bell" style="width:20px;height:20px;opacity:0.9;" onclick="showTip('订阅消息已开启 · 释放/核销通知将推送')"></span></div>
    </div>
    <div style="font-size:13px;opacity:0.9;">您好, 李先生</div>
    <div style="font-size:20px;font-weight:700;margin-top:2px;">消费即创造权益</div>
  </div>

  <div class="lsc-float">
    <div class="lf-label">LSC 可用余额</div>
    <div><span class="lf-val">8,640.50</span><span class="lf-unit">LSC</span></div>
    <div class="lf-foot">
      <span>锁定池<b>15,200.00</b></span>
      <span style="text-align:center;">今日释放<b style="color:#ffe9a8;">+38.50</b></span>
      <span style="text-align:right;">总资产<b>23,840.50</b></span>
    </div>
  </div>

  <div class="wx-card">
    <div class="wx-grid">
      <div class="wx-grid-item" onclick="showScreen('scan')"><div class="wx-grid-icon" style="background:var(--c-wx-green);"><span class="icon" data-i="scan"></span></div><span>扫一扫</span></div>
      <div class="wx-grid-item" onclick="wxPayCode()"><div class="wx-grid-icon" style="background:#576b95;"><span class="icon" data-i="qr"></span></div><span>付款码</span></div>
      <div class="wx-grid-item" onclick="showScreen('orders')"><div class="wx-grid-icon" style="background:var(--c-accent);"><span class="icon" data-i="order"></span></div><span>订单</span></div>
      <div class="wx-grid-item" onclick="showScreen('promo')"><div class="wx-grid-icon" style="background:#ff5a3c;"><span class="icon" data-i="promotion"></span></div><span>推广</span></div>
      <div class="wx-grid-item" onclick="showScreen('wallet')"><div class="wx-grid-icon" style="background:#576b95;"><span class="icon" data-i="wallet"></span></div><span>钱包</span></div>
      <div class="wx-grid-item" onclick="showTip('微信支付 · 支持混合支付')"><div class="wx-grid-icon" style="background:#09bb07;"><span class="icon" data-i="wallet"></span></div><span>微信支付</span></div>
      <div class="wx-grid-item" onclick="showTip('地理位置已授权 · 显示附近商家')"><div class="wx-grid-icon" style="background:#ff9500;"><span class="icon" data-i="location"></span></div><span>附近</span></div>
      <div class="wx-grid-item" onclick="showTip('AI智能客服')"><div class="wx-grid-icon" style="background:var(--c-primary);"><span class="icon" data-i="ai"></span></div><span>AI客服</span></div>
    </div>
  </div>

  <div class="wx-notice">
    <span class="wx-notice-tag">公告</span>
    <span>每日凌晨2点动态释放LSC, 当前核销率k=0.72%, 释放速率0.0385%</span>
  </div>

  <div class="wx-section-title"><h2>权益商城推荐</h2><span class="more" onclick="showScreen('mall')">更多 ›</span></div>
  <div class="wx-h-scroll">
    <div class="wx-product" onclick="showScreen('product')">
      <div class="wx-product-img"><span class="icon" data-i="product"></span></div>
      <div class="wx-product-body"><div class="wx-product-name">精品双人套餐·周末限定</div><div class="wx-product-price">¥<small>399</small></div><div class="wx-product-lsc">可抵399 LSC</div></div>
    </div>
    <div class="wx-product" onclick="showScreen('product')">
      <div class="wx-product-img"><span class="icon" data-i="product"></span></div>
      <div class="wx-product-body"><div class="wx-product-name">明前龙井·礼盒装250g</div><div class="wx-product-price">¥<small>888</small></div><div class="wx-product-lsc">可抵888 LSC</div></div>
    </div>
    <div class="wx-product" onclick="showScreen('product')">
      <div class="wx-product-img"><span class="icon" data-i="product"></span></div>
      <div class="wx-product-body"><div class="wx-product-name">智能蓝牙耳机Pro3</div><div class="wx-product-price">¥<small>499</small></div><div class="wx-product-lsc">可抵499 LSC</div></div>
    </div>
  </div>

  <div class="wx-section-title"><h2>附近商家</h2><span class="more">全部 ›</span></div>
  <div class="wx-merchant" onclick="wxScanPay()">
    <div class="wx-merchant-logo">锦</div>
    <div class="wx-merchant-info">
      <div class="wx-merchant-name">锦华餐饮连锁·总店</div>
      <div class="wx-merchant-meta">餐饮 · 评分4.9 · 营业中</div>
      <div class="wx-merchant-dist"><span class="icon" data-i="location"></span>距您 280m · 支持LSC消费</div>
    </div>
  </div>
  <div class="wx-merchant" onclick="wxScanPay()">
    <div class="wx-merchant-logo" style="background:linear-gradient(135deg,#576b95,#3a4d75);">御</div>
    <div class="wx-merchant-info">
      <div class="wx-merchant-name">御品茶业工坊</div>
      <div class="wx-merchant-meta">零售 · 评分4.8 · 营业中</div>
      <div class="wx-merchant-dist"><span class="icon" data-i="location"></span>距您 650m · 支持LSC消费</div>
    </div>
  </div>
  <div style="text-align:center;padding:16px;font-size:11px;color:#999;">— 链盛通LSC · 消费创造权益 —</div>`;
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
  const ps = PRODUCT_LIST;
  document.getElementById('screen-mall').innerHTML = `
  <div class="wx-search-bar"><div class="wx-search-inner"><span class="icon" data-i="search"></span><input placeholder="搜索商品/商家"></div></div>
  <div class="wx-cats"><span class="wx-cat active">全部</span><span class="wx-cat">餐饮</span><span class="wx-cat">生鲜</span><span class="wx-cat">数码</span><span class="wx-cat">茶酒</span><span class="wx-cat">服饰</span></div>
  <div class="wx-mall-grid">
    ${ps.map((p,i)=>`<div class="wx-mall-card" onclick="openProduct(${i})">
      <div class="wx-mall-img"><span class="icon" data-i="product"></span>${p.tag?`<span class="tag tag-accent" style="position:absolute;top:6px;left:6px;font-size:10px;">${p.tag}</span>`:''}</div>
      <div style="padding:8px;"><div style="font-size:12px;font-weight:500;line-height:1.3;height:32px;overflow:hidden;color:#1a1a1a;">${p.name}</div>
      <div class="flex items-center justify-between mt-1"><span style="font-size:16px;font-weight:700;color:#ff5a3c;font-family:var(--ff-mono);">¥${p.price}</span><span style="font-size:10px;color:var(--c-wx-green-deep);">抵${p.price}LSC</span></div></div>
    </div>`).join('')}
  </div>
  <div style="text-align:center;padding:16px;font-size:11px;color:#999;">— 上滑加载更多 —</div>`;
  renderIcons(document.getElementById('screen-mall'));
}

/* ===== 扫码 ===== */
function renderScan() {
  document.getElementById('screen-scan').innerHTML = `
  <div class="wx-scan">
    <div class="wx-scan-nav">
      <span style="font-size:15px;font-weight:600;">扫商家收款码</span>
      <span style="margin-left:auto;font-size:12px;opacity:0.7;" onclick="showScreen('home')">关闭</span>
    </div>
    <div class="wx-scan-area">
      <div class="wx-scan-frame">
        <div class="wx-scan-corner wx-sc-tl"></div>
        <div class="wx-scan-corner wx-sc-tr"></div>
        <div class="wx-scan-corner wx-sc-bl"></div>
        <div class="wx-scan-corner wx-sc-br"></div>
        <div class="wx-scan-line"></div>
      </div>
    </div>
    <div class="wx-scan-hint">将商家收款码放入框内即可自动扫描</div>
    <div class="wx-scan-actions">
      <div class="wx-scan-btn"><span class="icon" data-i="location"></span>附近商家</div>
      <div class="wx-scan-btn primary" onclick="wxScanPay()"><span class="icon" data-i="qr"></span>模拟扫码</div>
    </div>
  </div>`;
  renderIcons(document.getElementById('screen-scan'));
}

/* 微信支付弹窗 */
function wxScanPay() {
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.style.background = 'rgba(0,0,0,0.5)';
  mask.innerHTML = `<div class="modal" style="width:90%;max-width:340px;border-radius:14px;overflow:hidden;">
    <div style="padding:16px;text-align:center;border-bottom:1px solid #f0f0f0;">
      <div style="font-weight:600;font-size:15px;">锦华餐饮连锁·总店</div>
      <div class="text-xs text-muted mt-1">世纪大道100号</div>
      <div style="margin-top:14px;"><span style="font-size:13px;color:#999;">支付金额</span><div style="font-size:32px;font-weight:700;color:#1a1a1a;font-family:var(--ff-mono);">¥<span id="wx-pay-amt">100.00</span></div></div>
    </div>
    <div style="padding:14px;">
      <div style="padding:10px 0;border-bottom:1px solid #f0f0f0;display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:8px;"><div style="width:28px;height:28px;border-radius:6px;background:#09bb07;display:flex;align-items:center;justify-content:center;color:#fff;font-size:12px;font-weight:700;">微</div><span style="font-size:14px;">微信支付</span></div>
        <div style="width:20px;height:20px;border-radius:50%;border:2px solid var(--c-wx-green);background:var(--c-wx-green);display:flex;align-items:center;justify-content:center;"><span class="icon" data-i="check" style="width:12px;height:12px;color:#fff;"></span></div>
      </div>
      <div style="padding:10px 0;display:flex;justify-content:space-between;font-size:13px;"><span style="color:#666;">LSC抵扣</span><b style="color:var(--c-wx-green-deep);font-family:var(--ff-mono);" id="wx-lsc-deduct">0.00 LSC</b></div>
      <div class="alert" style="background:var(--c-wx-green-tint);color:var(--c-wx-green-deep);padding:8px 10px;border-radius:6px;font-size:11px;margin-top:6px;"><span class="icon icon-sm" data-i="unlock"></span>支付后获得100 LSC进入锁定池, 每日动态释放</div>
    </div>
    <div style="padding:12px;border-top:1px solid #f0f0f0;display:flex;gap:8px;">
      <button class="wx-btn wx-btn-outline" style="flex:1;padding:10px;" onclick="this.closest('.modal-mask').remove()">取消</button>
      <button class="wx-btn wx-btn-green" style="flex:1;padding:10px;" onclick="wxPaySuccess(this)">确认支付</button>
    </div>
  </div>`;
  document.body.appendChild(mask);
  renderIcons(mask);
}
function wxPaySuccess(btn) {
  const mask = btn.closest('.modal-mask');
  mask.querySelector('.modal').innerHTML = `<div style="padding:40px 24px;text-align:center;">
    <div style="width:60px;height:60px;border-radius:50%;background:var(--c-wx-green);margin:0 auto 14px;display:flex;align-items:center;justify-content:center;"><span class="icon icon-xl" data-i="check" style="width:32px;height:32px;color:#fff;"></span></div>
    <div style="font-size:17px;font-weight:700;">支付成功</div>
    <div class="text-muted text-sm mt-2">消费¥100 · 获得100.00 LSC</div>
    <div style="background:var(--c-wx-green-tint);color:var(--c-wx-green-deep);padding:10px;border-radius:8px;font-size:12px;margin-top:14px;text-align:left;"><span class="icon icon-sm" data-i="bell"></span> 已为您订阅消息: LSC释放/核销通知将通过微信推送</div>
    <button class="wx-btn wx-btn-green" style="margin-top:18px;" onclick="this.closest('.modal-mask').remove();showScreen('wallet');">查看钱包</button>
  </div>`;
  renderIcons(mask);
}
function wxPayCode() {
  showTip('LSC付款码已生成 · 30秒自动刷新 · 含防伪签名');
}

/* ===== 钱包 ===== */
function renderWallet() {
  const txs = [
    {t:'每日释放',i:'unlock',c:'var(--c-wx-green)',a:'+38.50',time:'今日 02:03'},
    {t:'消费发行',i:'mall',c:'#576b95',a:'+100.00',time:'昨日 18:24'},
    {t:'线下扫码消费',i:'scan',c:'#ff9500',a:'-50.00',time:'昨日 12:30'},
    {t:'推广奖励',i:'promotion',c:'var(--c-accent)',a:'+10.00',time:'前天 15:20'},
    {t:'每日释放',i:'unlock',c:'var(--c-wx-green)',a:'+37.80',time:'前天 02:03'},
  ];
  document.getElementById('screen-wallet').innerHTML = `
  <div class="wx-wallet-top">
    <div style="font-size:13px;opacity:0.85;">我的 LSC 钱包</div>
    <div class="wx-wallet-bal">
      <div class="wx-wb-label">可用余额</div>
      <div class="wx-wb-val">8,640.50</div>
      <div class="wx-wb-sub"><span>锁定池<b style="color:#a8e6c4;">15,200.00</b></span><span>总资产<b>23,840.50</b></span></div>
    </div>
  </div>
  <div class="wx-wallet-tabs">
    <div class="wx-wallet-tab active">全部</div>
    <div class="wx-wallet-tab">释放</div>
    <div class="wx-wallet-tab">消费</div>
    <div class="wx-wallet-tab">推广</div>
  </div>
  <!-- 个人LSC流转链路图 -->
  <div class="wx-card" style="margin-top:10px;padding:10px 12px;">
    <div style="font-size:12px;font-weight:600;color:var(--c-wx-green-deep);margin-bottom:6px;display:flex;align-items:center;gap:5px;">
      <span class="icon icon-sm" data-i="flow" style="color:var(--c-wx-green-deep);"></span>我的LSC流转链路
    </div>
    <div style="overflow-x:auto;">
    <svg viewBox="0 0 520 160" style="width:100%;min-width:480px;height:130px;">
      <defs>
        <marker id="wx-a1" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="#576b95"/></marker>
        <marker id="wx-a2" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="var(--c-wx-green-deep)"/></marker>
        <marker id="wx-a3" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="#ff9500"/></marker>
      </defs>
      <rect x="4" y="54" width="86" height="52" rx="10" fill="rgba(87,107,149,0.08)" stroke="#576b95" stroke-width="1.5"/>
      <text x="47" y="76" font-size="10" font-weight="700" fill="#576b95" text-anchor="middle">消费发行</text>
      <text x="47" y="92" font-size="9" fill="#666" text-anchor="middle">+24,800</text>
      <rect x="140" y="46" width="86" height="68" rx="10" fill="rgba(255,177,61,0.10)" stroke="#ff9500" stroke-width="2"/>
      <text x="183" y="70" font-size="11" font-weight="700" fill="#ff9500" text-anchor="middle">🔒 锁定池</text>
      <text x="183" y="88" font-size="10" fill="#1a1a1a" text-anchor="middle">15,200</text>
      <text x="183" y="102" font-size="8" fill="#999" text-anchor="middle">日释 0.038%</text>
      <rect x="276" y="46" width="86" height="68" rx="10" fill="rgba(7,193,96,0.10)" stroke="var(--c-wx-green-deep)" stroke-width="2"/>
      <text x="319" y="70" font-size="11" font-weight="700" fill="var(--c-wx-green-deep)" text-anchor="middle">✓ 可用池</text>
      <text x="319" y="88" font-size="10" fill="#1a1a1a" text-anchor="middle">8,640</text>
      <text x="319" y="102" font-size="8" fill="#999" text-anchor="middle">可消费/转账</text>
      <rect x="412" y="12" width="90" height="46" rx="10" fill="rgba(255,149,0,0.10)" stroke="#ff9500" stroke-width="1.5"/>
      <text x="457" y="32" font-size="10" font-weight="700" fill="#ff9500" text-anchor="middle">扫码消费</text>
      <text x="457" y="48" font-size="9" fill="#999" text-anchor="middle">-6,400</text>
      <rect x="412" y="96" width="90" height="46" rx="10" fill="rgba(200,162,75,0.14)" stroke="var(--c-accent-deep)" stroke-width="1.5"/>
      <text x="457" y="116" font-size="10" font-weight="700" fill="var(--c-accent-deep)" text-anchor="middle">推广奖励</text>
      <text x="457" y="132" font-size="9" fill="#999" text-anchor="middle">+320</text>
      <line x1="90" y1="80" x2="138" y2="80" stroke="#576b95" stroke-width="2" marker-end="url(#wx-a1)"/>
      <text x="114" y="75" font-size="8" fill="#576b95" font-weight="600" text-anchor="middle">1:1</text>
      <line x1="226" y1="80" x2="274" y2="80" stroke="var(--c-wx-green-deep)" stroke-width="2.5" marker-end="url(#wx-a2)"/>
      <text x="250" y="75" font-size="8" fill="var(--c-wx-green-deep)" font-weight="600" text-anchor="middle">缓释</text>
      <path d="M 340 66 Q 400 40 410 34" fill="none" stroke="#ff9500" stroke-width="2" marker-end="url(#wx-a3)"/>
      <text x="375" y="40" font-size="8" fill="#ff9500" font-weight="600" text-anchor="middle">抵扣</text>
      <path d="M 457 96 Q 457 84 360 84 Q 330 84 330 80" fill="none" stroke="var(--c-accent-deep)" stroke-width="1.5" stroke-dasharray="3 2" marker-end="url(#wx-a2)"/>
    </svg>
    </div>
  </div>
  <div class="wx-card" style="margin-top:10px;">
    <div style="padding:0 14px;">${txs.map(t=>`
      <div class="wx-tx">
        <div class="wx-tx-icon" style="background:${t.c}22;color:${t.c};"><span class="icon" data-i="${t.i}"></span></div>
        <div class="wx-tx-info"><div class="wx-tx-title">${t.t}</div><div class="wx-tx-time">${t.time}</div></div>
        <div class="wx-tx-amt" style="color:${t.a.startsWith('+')?'var(--c-wx-green-deep)':'#1a1a1a'};">${t.a}</div>
      </div>`).join('')}
    </div>
  </div>
  <div style="text-align:center;padding:14px;font-size:11px;color:#999;">锁定池LSC按每日动态速率缓释</div>`;
  renderIcons(document.getElementById('screen-wallet'));
}

/* ===== 我的 ===== */
function renderMe() {
  document.getElementById('screen-me').innerHTML = `
  <div class="wx-me-top">
    <div class="wx-me-user">
      <div class="wx-me-avatar">李</div>
      <div><div class="wx-me-name">李先生</div><div class="wx-me-tag">微信用户 · 已实名</div></div>
      <span class="icon" data-i="arrowRight" style="margin-left:auto;opacity:0.7;width:20px;height:20px;"></span>
    </div>
    <div class="wx-me-stats">
      <div class="wx-me-stat"><div class="v">248</div><div class="l">推荐人数</div></div>
      <div class="wx-me-stat"><div class="v" style="color:#ffe9a8;">3,820</div><div class="l">推广奖励</div></div>
      <div class="wx-me-stat"><div class="v">96</div><div class="l">消费订单</div></div>
    </div>
  </div>
  <div class="wx-me-menu">
    <div class="wx-me-cell" onclick="showScreen('orders')"><span class="icon" data-i="order"></span><span class="t">我的订单</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
    <div class="wx-me-cell" onclick="showScreen('promo')"><span class="icon" data-i="promotion"></span><span class="t">推广中心</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
    <div class="wx-me-cell" onclick="showScreen('wallet')"><span class="icon" data-i="wallet"></span><span class="t">LSC钱包</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
    <div class="wx-me-cell" onclick="showTip('AI智能客服为您服务')"><span class="icon" data-i="ai"></span><span class="t">AI客服</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
  </div>
  <div class="wx-me-menu" style="margin-top:10px;">
    <div class="wx-me-cell"><span class="icon" data-i="user"></span><span class="t">实名认证</span><span class="tag tag-success" style="font-size:11px;">已认证</span></div>
    <div class="wx-me-cell"><span class="icon" data-i="location"></span><span class="t">收货地址</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
    <div class="wx-me-cell"><span class="icon" data-i="doc"></span><span class="t">入驻协议 / 隐私政策</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
    <div class="wx-me-cell" onclick="showTip('小程序设置')"><span class="icon" data-i="system"></span><span class="t">设置</span><span class="arrow"><span class="icon" data-i="arrowRight"></span></span></div>
  </div>
  <div style="text-align:center;padding:16px;font-size:11px;color:#999;">链盛通LSC V6.2 · 微信小程序版</div>`;
  renderIcons(document.getElementById('screen-me'));
}

/* ===== 订单 ===== */
function renderOrders() {
  document.getElementById('screen-orders').innerHTML = `
  <div class="wx-order-tabs">
    <div class="wx-order-tab active">全部</div><div class="wx-order-tab">消费</div><div class="wx-order-tab">兑换</div><div class="wx-order-tab">退款</div>
  </div>
  <div class="wx-order">
    <div class="wx-order-head"><span class="wx-order-no">ORD20260827001</span><span class="tag tag-success">已完成</span></div>
    <div class="wx-order-body">
      <div class="wx-order-img"><span class="icon" data-i="product"></span></div>
      <div style="flex:1;"><div style="font-size:13px;font-weight:500;">精品双人套餐·周末限定</div><div class="text-xs text-muted mt-1">锦华餐饮连锁</div><div class="text-xs" style="color:var(--c-wx-green-deep);">获399 LSC(锁定池)</div></div>
      <div style="text-align:right;"><div style="font-family:var(--ff-mono);font-weight:700;">¥399</div><div class="text-xs text-muted">×1</div></div>
    </div>
  </div>
  <div class="wx-order">
    <div class="wx-order-head"><span class="wx-order-no">OFF20260826003</span><span class="tag tag-success">已核销</span></div>
    <div class="wx-order-body">
      <div class="wx-order-img" style="background:linear-gradient(135deg,var(--c-accent-soft),#fff);"><span class="icon" data-i="scan" style="color:var(--c-accent-deep);"></span></div>
      <div style="flex:1;"><div style="font-size:13px;font-weight:500;">线下扫码消费</div><div class="text-xs text-muted mt-1">锦华餐饮连锁</div><div class="text-xs text-muted">LSC抵扣50 · 人民币¥50</div></div>
      <div style="text-align:right;"><div style="font-family:var(--ff-mono);font-weight:700;">¥100</div><div class="text-xs text-muted">混合支付</div></div>
    </div>
  </div>
  <div class="wx-order">
    <div class="wx-order-head"><span class="wx-order-no">EXC20260825002</span><span class="tag tag-warning">待核销</span></div>
    <div class="wx-order-body">
      <div class="wx-order-img" style="background:linear-gradient(135deg,var(--c-wx-green-tint),#fff);"><span class="icon" data-i="unlock" style="color:var(--c-wx-green-deep);"></span></div>
      <div style="flex:1;"><div style="font-size:13px;font-weight:500;">权益兑换</div><div class="text-xs text-muted mt-1">御品茶业工坊</div><div class="text-xs text-muted">消费抵扣200 LSC</div></div>
      <div style="text-align:right;"><div style="font-family:var(--ff-mono);font-weight:700;color:var(--c-wx-green-deep);">-200</div><div class="text-xs text-muted">LSC</div></div>
    </div>
  </div>`;
  renderIcons(document.getElementById('screen-orders'));
}

/* ===== 推广(分享裂变) ===== */
function renderPromo() {
  document.getElementById('screen-promo').innerHTML = `
  <div class="wx-share-card">
    <div style="font-size:13px;opacity:0.9;">邀请好友 · 赚10%奖励</div>
    <div style="font-size:22px;font-weight:700;margin-top:4px;">好友首单消费<br>您即获10% LSC</div>
    <div class="wx-share-qr">
      <div class="qg"><div class="qc qtl"></div><div class="qc qtr"></div><div class="qc qbl"></div><div class="qlogo">李</div></div>
    </div>
    <div style="text-align:center;font-size:12px;opacity:0.9;margin-top:10px;">扫码或分享卡片给好友</div>
  </div>
  <div style="padding:0 12px;">
    <button class="wx-btn wx-btn-green" onclick="wxShare()"><span class="icon" data-i="promotion" style="width:16px;height:16px;vertical-align:middle;margin-right:4px;"></span>分享给微信好友</button>
    <button class="wx-btn wx-btn-outline" style="margin-top:8px;" onclick="showTip('已保存推广码到相册')">保存推广码图片</button>
  </div>
  <div class="wx-section-title"><h2>推广战绩</h2></div>
  <div style="display:flex;gap:8px;padding:0 12px 12px;">
    <div style="flex:1;background:#fff;border-radius:10px;padding:12px;text-align:center;"><div style="font-size:20px;font-weight:700;font-family:var(--ff-mono);color:var(--c-wx-green-deep);">248</div><div class="text-xs text-muted">已邀请</div></div>
    <div style="flex:1;background:#fff;border-radius:10px;padding:12px;text-align:center;"><div style="font-size:20px;font-weight:700;font-family:var(--ff-mono);color:var(--c-wx-green-deep);">186</div><div class="text-xs text-muted">活跃</div></div>
    <div style="flex:1;background:#fff;border-radius:10px;padding:12px;text-align:center;"><div style="font-size:20px;font-weight:700;font-family:var(--ff-mono);color:var(--c-accent-deep);">3,820</div><div class="text-xs text-muted">累计LSC</div></div>
  </div>
  <div class="wx-card"><div style="padding:0 14px;">
    <div class="wx-tx"><div class="wx-tx-icon" style="background:var(--c-accent-soft);color:var(--c-accent-deep);"><span class="icon" data-i="promotion"></span></div><div class="wx-tx-info"><div class="wx-tx-title">好友首单消费</div><div class="wx-tx-time">U10386 · ¥880</div></div><div class="wx-tx-amt" style="color:var(--c-wx-green-deep);">+88.00</div></div>
    <div class="wx-tx"><div class="wx-tx-icon" style="background:var(--c-accent-soft);color:var(--c-accent-deep);"><span class="icon" data-i="promotion"></span></div><div class="wx-tx-info"><div class="wx-tx-title">好友首单消费</div><div class="wx-tx-time">U10392 · ¥560</div></div><div class="wx-tx-amt" style="color:var(--c-wx-green-deep);">+56.00</div></div>
    <div class="wx-tx"><div class="wx-tx-icon" style="background:#fce6e4;color:var(--c-danger);"><span class="icon" data-i="promotion"></span></div><div class="wx-tx-info"><div class="wx-tx-title">好友退款扣回</div><div class="wx-tx-time">U10415 · ¥320</div></div><div class="wx-tx-amt" style="color:var(--c-danger);">-32.00</div></div>
  </div></div>`;
  renderIcons(document.getElementById('screen-promo'));
}
function wxShare() {
  // 模拟微信分享卡片
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.style.background = 'rgba(0,0,0,0.6)';
  mask.innerHTML = `<div style="position:absolute;right:16px;top:16px;background:#fff;border-radius:12px;padding:14px;width:200px;animation:slideUp 0.3s ease;">
    <div style="font-size:13px;font-weight:600;margin-bottom:10px;">分享到</div>
    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;text-align:center;">
      <div onclick="showTip('已分享给好友')" style="cursor:pointer;"><div style="width:40px;height:40px;border-radius:50%;background:#09bb07;margin:0 auto 4px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:700;">微</div><div style="font-size:10px;color:#666;">微信好友</div></div>
      <div onclick="showTip('已分享到朋友圈')" style="cursor:pointer;"><div style="width:40px;height:40px;border-radius:50%;background:#ff9500;margin:0 auto 4px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:700;">圈</div><div style="font-size:10px;color:#666;">朋友圈</div></div>
      <div onclick="showTip('已收藏')" style="cursor:pointer;"><div style="width:40px;height:40px;border-radius:50%;background:#576b95;margin:0 auto 4px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:700;">藏</div><div style="font-size:10px;color:#666;">收藏</div></div>
    </div>
    <button class="wx-btn wx-btn-outline" style="margin-top:10px;padding:8px;font-size:12px;" onclick="this.closest('.modal-mask').remove()">取消</button>
  </div>`;
  mask.addEventListener('click', e=>{ if(e.target===mask) mask.remove(); });
  document.body.appendChild(mask);
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
  <div style="height:240px;background:linear-gradient(135deg,var(--c-wx-green-tint),#f7f7f7);display:flex;align-items:center;justify-content:center;position:relative;">
    <span class="icon" data-i="product" style="width:72px;height:72px;color:var(--c-wx-green);opacity:0.4;"></span>
    ${p.tag?`<span class="tag tag-accent" style="position:absolute;top:12px;right:12px;">${p.tag}</span>`:''}
  </div>
  <div style="background:#fff;padding:14px;margin-top:-12px;border-radius:14px 14px 0 0;">
    <div style="font-size:17px;font-weight:700;">${p.name}</div>
    <div class="flex items-center gap-2 mt-2">
      <span style="font-size:24px;font-weight:700;color:#ff5a3c;font-family:var(--ff-mono);">¥${p.price}</span>
      <span style="font-size:11px;color:var(--c-wx-green-deep);">可抵${p.price} LSC</span>
    </div>
    <div class="text-xs text-muted mt-2">${p.merchant} · 月销${p.sales}</div>
    <div style="background:var(--c-wx-green-tint);color:var(--c-wx-green-deep);padding:8px 10px;border-radius:6px;font-size:12px;margin-top:10px;"><span class="icon icon-sm" data-i="unlock"></span> 支付¥${p.price}后获得${p.price} LSC进入锁定池, 每日动态释放</div>
    <!-- 商品属性 -->
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px;">
      <div style="background:#f7f7f7;border-radius:8px;padding:8px 10px;">
        <div style="font-size:10px;color:#999;">AI 真实度评分</div>
        <div style="font-size:15px;font-weight:700;color:var(--c-wx-green-deep);font-family:var(--ff-mono);">${p.aiScore}%</div>
      </div>
      <div style="background:#f7f7f7;border-radius:8px;padding:8px 10px;">
        <div style="font-size:10px;color:#999;">库存</div>
        <div style="font-size:15px;font-weight:700;color:#1a1a1a;font-family:var(--ff-mono);">${p.stock} 件</div>
      </div>
    </div>
    <div class="wx-section-title" style="padding:14px 0 8px;"><h2 style="font-size:14px;">商品详情</h2></div>
    <div class="text-sm" style="line-height:1.7;color:#666;">${p.desc}</div>
    <div style="height:60px;"></div>
  </div>
  <div class="wx-pay-bar" style="position:fixed;left:0;right:0;bottom:0;max-width:366px;margin:0 auto;z-index:20;">
    <button class="wx-btn wx-btn-outline" style="flex:1;padding:10px;margin-right:6px;" onclick="showTip('已加入购物车 · ${p.name}')">加入购物车</button>
    <button class="wx-btn wx-btn-green" style="flex:1;padding:10px;" onclick="wxScanPay()">立即购买</button>
  </div>`;
  renderIcons(document.getElementById('screen-product'));
}

/* 提示 toast */
function showTip(msg) {
  const t = document.createElement('div');
  t.className = 'wx-subscribe-tip';
  t.innerHTML = `<span class="icon" data-i="bell" style="width:16px;height:16px;"></span>${msg}`;
  document.body.appendChild(t);
  renderIcons(t);
  setTimeout(()=>{ t.style.opacity='0'; t.style.transition='opacity 0.3s'; setTimeout(()=>t.remove(),300); }, 2200);
}

/* 启动 */
renderHome(); renderMall(); renderScan(); renderWallet(); renderMe();
renderOrders(); renderPromo(); renderProduct();
showScreen('home');
// A11y: 为滚动容器补 tabindex=0 / role=region / aria-label, 解决 axe-core scrollable-region-focusable
if (typeof LSC !== 'undefined' && LSC.a11yEnhance) LSC.a11yEnhance(document.getElementById('wx-content'));
