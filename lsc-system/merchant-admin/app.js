/* 渲染图标 */
document.querySelectorAll('.icon[data-i]').forEach(el=>{
  const key = el.getAttribute('data-i');
  if (ICONS[key]) el.innerHTML = ICONS[key];
});

const crumbMap = { dashboard:'经营总览', shop:'店铺管理', product:'商品管理', wallet:'LSC账户', nh:'核销管理', b2b:'B2B交易', promotion:'推广管理', credit:'信用中心', ai:'AI助手' };
const views = { dashboard: renderDashboard, shop: renderShop, product: renderProduct, wallet: renderWallet, nh: renderNH, b2b: renderB2B, promotion: renderPromotion, credit: renderCredit, ai: renderAI };

function navTo(view) {
  document.querySelectorAll('.nav-item').forEach(n=>n.classList.toggle('active', n.dataset.view===view));
  document.getElementById('crumb').textContent = crumbMap[view];
  views[view]();
}
document.getElementById('nav').addEventListener('click', e=>{
  const item = e.target.closest('.nav-item');
  if (item) navTo(item.dataset.view);
});

function pageHead(title, desc, extra='') {
  return `<div class="flex items-center justify-between mb-5">
    <div><div style="font-size:20px;font-weight:700;">${title}</div><div style="font-size:13px;color:var(--c-text-3);margin-top:4px;">${desc}</div></div>
    <div class="flex gap-3">${extra}</div>
  </div>`;
}
function setView(html) {
  const v = document.getElementById('view');
  v.innerHTML = html;
  v.querySelectorAll('.icon[data-i]').forEach(el=>{
    const key = el.getAttribute('data-i');
    if (ICONS[key]) el.innerHTML = ICONS[key];
  });
  // A11y: 为滚动容器补 tabindex=0 / role=region / aria-label, 解决 axe-core scrollable-region-focusable
  if (typeof LSC !== 'undefined' && LSC.a11yEnhance) LSC.a11yEnhance(v);
}

/* 环形进度 */
function ringChart(pct, color='var(--c-accent)', size=140, stroke=12) {
  const r = (size-stroke)/2, c = 2*Math.PI*r, off = c*(1-pct);
  return `<svg width="${size}" height="${size}"><circle cx="${size/2}" cy="${size/2}" r="${r}" fill="none" stroke="var(--c-border-soft)" stroke-width="${stroke}"/>
  <circle cx="${size/2}" cy="${size/2}" r="${r}" fill="none" stroke="${color}" stroke-width="${stroke}" stroke-linecap="round" stroke-dasharray="${c}" stroke-dashoffset="${off}" transform="rotate(-90 ${size/2} ${size/2})"/></svg>`;
}

/* ============== 图表函数 ============== */
/* 折线/区域图 */
function lineChart(opts) {
  const { w=680, h=220, labels=[], series=[], yTicks=5 } = opts;
  const pad={l:44,r:14,t:18,b:28};
  const iw=w-pad.l-pad.r, ih=h-pad.t-pad.b;
  const all = series.flatMap(s=>s.data.filter(v=>v!=null&&!isNaN(v)));
  const mx = Math.max(...all, 1), mn = Math.min(...all, 0);
  const span = mx - mn || 1;
  const sx = iw/Math.max(labels.length-1,1);
  const sy = ih/span;
  const px = i => pad.l + i*sx;
  const py = v => pad.t + ih - (v-mn)*sy;
  const T = d => Array.isArray(d) ? d : [d];
  // Y轴刻度
  let axes='';
  for(let i=0;i<=yTicks;i++){
    const yv = mn + (span/yTicks*i);
    const yy = py(yv);
    axes += `<line x1="${pad.l}" y1="${yy}" x2="${w-pad.r}" y2="${yy}" stroke="var(--c-divider)" stroke-dasharray="${i==0?'0':'3 3'}"/>`;
    axes += `<text x="${pad.l-6}" y="${yy+3}" font-size="10" fill="var(--c-text-3)" text-anchor="end">${(yv).toFixed(3)}</text>`;
  }
  labels.forEach((lb,i)=>{ if(i%Math.ceil(labels.length/8)===0) axes += `<text x="${px(i)}" y="${h-8}" font-size="10" fill="var(--c-text-3)" text-anchor="middle">${lb}</text>`; });
  // 系列
  let paths='', legends='';
  series.forEach((s,si)=>{
    const data = s.data;
    const validIdx = data.map((v,i)=>[v,i]).filter(([v])=>v!=null&&!isNaN(v));
    const pts = validIdx.map(([v,i])=>[px(i),py(v)]);
    const poly = pts.map(p=>p.join(',')).join(' ');
    if(s.area && pts.length){
      const area = `${pad.l},${pad.t+ih} ${poly} ${px(validIdx[validIdx.length-1][1])},${pad.t+ih}`;
      paths += `<polygon points="${area}" fill="${s.color}" opacity="0.12"/>`;
    }
    paths += `<polyline points="${poly}" fill="none" stroke="${s.color}" stroke-width="${s.width||2}" stroke-linecap="round" stroke-linejoin="round" ${s.dash?'stroke-dasharray="6 3"':''}/>`;
    pts.forEach(([x,y])=>{ paths += `<circle cx="${x}" cy="${y}" r="${s.radius||2.5}" fill="#fff" stroke="${s.color}" stroke-width="2"/>`; });
    legends += `<span class="tag tag-dot" style="background:${s.color};color:#fff;font-size:10px;">${s.name}</span> `;
  });
  return { svg: `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${axes}${paths}</svg>`, legend: legends };
}
/* 环形/饼图 */
function donutChart(opts) {
  const { w=240, h=240, data, inner=0.55, unit='' } = opts;
  const cx = w/2, cy = h/2, r = Math.min(w,h)/2 - 8, ir = r*inner;
  const total = data.reduce((s,d)=>s+d.value,0)||1;
  let acc=-Math.PI/2, arcs='', labels='';
  data.forEach((d)=>{
    const ang=(d.value/total)*Math.PI*2;
    const a0=acc,a1=acc+ang; acc=a1;
    const x0=cx+r*Math.cos(a0),y0=cy+r*Math.sin(a0);
    const x1=cx+r*Math.cos(a1),y1=cy+r*Math.sin(a1);
    const xi0=cx+ir*Math.cos(a0),yi0=cy+ir*Math.sin(a0);
    const xi1=cx+ir*Math.cos(a1),yi1=cy+ir*Math.sin(a1);
    const large=ang>Math.PI?1:0;
    arcs+=`<path d="M ${x0} ${y0} A ${r} ${r} 0 ${large} 1 ${x1} ${y1} L ${xi1} ${yi1} A ${ir} ${ir} 0 ${large} 0 ${xi0} ${yi0} Z" fill="${d.color}" opacity="0.92"/>`;
    const pct=d.value/total;
    if(pct>0.05){
      const mid=(a0+a1)/2;
      const lx=cx+(r+10)*Math.cos(mid), ly=cy+(r+10)*Math.sin(mid);
      labels+=`<text x="${lx}" y="${ly+3}" font-size="10" fill="var(--c-text-2)" text-anchor="${Math.cos(mid)<0?'end':'start'}" font-weight="600">${(pct*100).toFixed(1)}%</text>`;
    }
  });
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${arcs}${labels}
    <text x="${cx}" y="${cy-4}" font-size="11" fill="var(--c-text-3)" text-anchor="middle">总计</text>
    <text x="${cx}" y="${cy+14}" font-size="15" font-weight="700" fill="var(--c-text-1)" text-anchor="middle" font-family="var(--ff-mono)">${total>=10000?(total/10000).toFixed(1)+'万':total}${unit}</text>
  </svg>`;
}
/* 堆叠柱状图 */
function stackedBar(opts) {
  const { w=560, h=240, labels, stacks, unit='' } = opts;
  const pad = { l: 44, r: 12, t: 16, b: 28 };
  const iw = w-pad.l-pad.r, ih = h-pad.t-pad.b;
  const n = labels.length;
  const sums = labels.map((_,i)=>stacks.reduce((s,st)=>s+(st.data[i]||0),0));
  const max = Math.max(...sums) || 1;
  const gap = iw/n, bw = gap*0.6;
  let bars = '', xLabs = '', legend = '';
  labels.forEach((lb,i)=>{
    const x = pad.l + i*gap + (gap-bw)/2;
    let yBase = pad.t + ih;
    stacks.forEach(st=>{
      const v = st.data[i] || 0;
      const bh = (v/max)*ih;
      yBase -= bh;
      bars += `<rect x="${x}" y="${yBase}" width="${bw}" height="${bh}" fill="${st.color}" opacity="0.88"><title>${st.name}: ${v}${unit}</title></rect>`;
    });
    if(i%Math.ceil(n/8)===0) xLabs += `<text x="${x+bw/2}" y="${h-pad.b+16}" font-size="10" fill="var(--c-text-3)" text-anchor="middle">${lb}</text>`;
  });
  stacks.forEach((st,i)=>{ legend += `<span class="tag tag-dot" style="background:${st.color};color:#fff;font-size:10px;">${st.name}</span> `; });
  return { svg: `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${bars}${xLabs}</svg>`, legend };
}

/* ============== 商家经营总览 ============== */
function renderDashboard() {
  const m = MOCK.merchants[0]; // 锦华餐饮
  const products = MOCK.products.filter(p => p.merchant === m.name);
  const NH = [
    { date: 'D-6', pay: 28500, lsc: 27300 },
    { date: 'D-5', pay: 31200, lsc: 30100 },
    { date: 'D-4', pay: 29800, lsc: 28600 },
    { date: 'D-3', pay: 33400, lsc: 32100 },
    { date: 'D-2', pay: 32100, lsc: 30800 },
    { date: 'D-1', pay: 35600, lsc: 34200 },
    { date: '今日', pay: 18200, lsc: 17500 },
  ];
  const html = pageHead('经营总览', '核心指标 · 核销趋势 · 商品销售分布 · LSC流水构成', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="export"></span>导出报表</button>
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="qr"></span>收款码</button>
  `) + `
  <!-- 核心指标 -->
  <div class="stat-grid mb-5 stagger">
    <div class="stat-card"><div class="stat-label">今日营收</div><div class="stat-value">¥${LSC.fmtNum(18200)}</div><div class="stat-trend up"><span class="icon icon-sm" data-i="arrow-up"></span>12.4% vs 昨日</div></div>
    <div class="stat-card"><div class="stat-label">今日核销LSC</div><div class="stat-value" style="color:var(--c-available);">${LSC.fmtNum(17500)}</div><div class="stat-trend up"><span class="icon icon-sm" data-i="arrow-up"></span>9.8%</div></div>
    <div class="stat-card"><div class="stat-label">核销订单数</div><div class="stat-value">${LSC.fmtNum(86)}</div><div class="stat-trend up"><span class="icon icon-sm" data-i="arrow-up"></span>6.2%</div></div>
    <div class="stat-card"><div class="stat-label">B2B采购(30日)</div><div class="stat-value" style="color:var(--c-accent-deep);">¥${LSC.fmtNum(286000)}</div><div class="stat-trend down"><span class="icon icon-sm" data-i="arrow-down"></span>3.1%</div></div>
  </div>

  <!-- 核销趋势折线图 -->
  <div class="card chart-card mb-5">
    <div class="card-head" style="border:none;padding:0 0 12px;">
      <div><div class="card-title">近7日核销趋势</div><div class="card-sub">人民币支付金额 vs LSC核销数量</div></div>
      <span class="tag tag-dot tag-success">实时</span>
    </div>
    <div class="chart-area">${(() => {
      const lc = lineChart({
        w: 680, h: 220,
        labels: NH.map(d=>d.date),
        series: [
          { name:'支付金额(¥×1000)', color:'var(--c-accent-deep)', data: NH.map(d=>d.pay/1000), area:true, width:2.5 },
          { name:'核销LSC(×1000)', color:'var(--c-available)', data: NH.map(d=>d.lsc/1000), dash:true },
        ]
      });
      return lc.svg + `<div style="margin-top:8px;">${lc.legend}</div>`;
    })()}</div>
  </div>

  <!-- 分布图表 2列 -->
  <div class="grid-2-eq mb-5 stagger">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">商品销售分布</div><div class="card-sub">本店铺商品LSC销售额占比</div></div>
      </div>
      ${(() => {
        const colors = ['var(--c-primary)','var(--c-accent)','var(--c-available)','var(--c-warning)','var(--c-info)','var(--c-danger)','var(--c-primary-soft)'];
        const data = products.map((p,i)=>({label:p.name.length>8?p.name.slice(0,7)+'…':p.name,value:Math.round(p.price*(80+Math.random()*300)),color:colors[i%colors.length]}));
        return `<div style="height:200px;">${donutChart({w:220,h:220,data,unit:' LSC'})}</div>
        <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">${data.map(d=>`<span class="tag tag-dot" style="background:${d.color};color:#fff;font-size:10px;">${d.label}</span>`).join('')}</div>`;
      })()}
    </div>

    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">LSC流水构成</div><div class="card-sub">近7日: 收款核销/B2B获得/推广奖励</div></div>
      </div>
      <div class="chart-area">${(() => {
        const sb = stackedBar({
          w: 540, h: 240, unit:' LSC',
          labels: NH.map(d=>d.date),
          stacks: [
            { name:'核销收款', color:'var(--c-available)', data: NH.map(d=>d.lsc) },
            { name:'B2B获得', color:'var(--c-accent)', data: [2800,3200,0,4500,0,3800,0] },
            { name:'推广奖励', color:'var(--c-info)', data: [120,80,150,200,180,220,90] },
          ]
        });
        return sb.svg + `<div style="margin-top:8px;">${sb.legend}</div>`;
      })()}</div>
    </div>
  </div>

  <!-- 今日核销记录 -->
  <div class="card">
    <div class="card-head"><div class="card-title">今日核销订单 (最近8笔)</div><span class="tag tag-primary">86 笔</span></div>
    <div class="card-body" style="padding:0;">
      <table class="table">
        <thead><tr><th>时间</th><th>订单号</th><th>用户</th><th>商品</th><th>支付金额</th><th>核销LSC</th><th>AI核验</th><th>状态</th></tr></thead>
        <tbody>
        ${[
          ['14:22','260827142201','U10092','招牌全家福套餐','¥588.00','588.00',97,'已完成'],
          ['14:08','260827140816','U10155','精品双人套餐','¥399.00','399.00',96,'已完成'],
          ['13:45','260827134508','U10042','工作日单人餐','¥158.00','158.00',99,'已完成'],
          ['13:12','260827131223','U10211','商务宴请6人餐','¥1288.00','1288.00',95,'已完成'],
          ['12:48','260827124831','U10087','工作日单人餐','¥158.00','158.00',99,'已完成'],
          ['12:30','260827123005','U10189','精品双人套餐','¥399.00','399.00',94,'已完成'],
          ['11:56','260827115644','U10012','家庭欢乐套餐','¥788.00','788.00',98,'已完成'],
          ['11:22','260827112218','U10176','精品双人套餐','¥399.00','399.00',92,'已完成'],
        ].map(r=>`<tr>
          <td style="font-family:var(--ff-mono);font-size:11px;color:var(--c-text-3);">${r[0]}</td>
          <td style="font-family:var(--ff-mono);font-size:11px;">${r[1]}</td>
          <td style="font-size:11px;">${r[2]}</td>
          <td>${r[3]}</td>
          <td style="font-family:var(--ff-mono);font-weight:600;">${r[4]}</td>
          <td style="font-family:var(--ff-mono);color:var(--c-available);font-weight:600;">${r[5]}</td>
          <td><span class="tag tag-dot tag-success">${r[6]}%</span></td>
          <td><span class="tag tag-success">${r[7]}</span></td>
        </tr>`).join('')}
        </tbody>
      </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 店铺管理 ============== */
function renderShop() {
  const html = pageHead('店铺管理', '门店信息维护 · 线下地址管理(高德/百度地图选点) · AI地址核验', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="export"></span>保存修改</button>
  `) + `
  <div class="grid-2-eq mb-5">
    <div class="card">
      <div class="card-head"><div class="card-title">门店基本信息</div><span class="tag tag-success tag-dot">营业中</span></div>
      <div class="card-body" style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
        <div><label class="field-label">门店名称</label><input class="input" value="锦华餐饮连锁·总店"></div>
        <div><label class="field-label">联系电话</label><input class="input" value="021-58881234"></div>
        <div><label class="field-label">商家类型</label><input class="input" value="餐饮" readonly></div>
        <div><label class="field-label">营业时间</label><input class="input" value="09:00 - 22:00"></div>
        <div style="grid-column:1/3;"><label class="field-label">营业执照号</label><input class="input" value="91310115MA1K3P2X8Q" readonly></div>
        <div style="grid-column:1/3;"><label class="field-label">监管账户号</label><input class="input" value="CUST-62284804029876" readonly></div>
      </div>
    </div>
    <div class="card">
      <div class="card-head"><div class="card-title">经营资质</div><span class="tag tag-success">已核验</span></div>
      <div class="card-body">
        <div class="flex items-center justify-between" style="padding:10px 0;border-bottom:1px solid var(--c-divider);">
          <div class="flex items-center gap-3"><span class="icon" data-i="doc" style="color:var(--c-available);"></span><div><div style="font-size:13px;font-weight:500;">营业执照</div><div class="text-xs text-muted">已上传 · 已核验</div></div></div>
          <span class="tag tag-success">有效</span>
        </div>
        <div class="flex items-center justify-between" style="padding:10px 0;border-bottom:1px solid var(--c-divider);">
          <div class="flex items-center gap-3"><span class="icon" data-i="doc" style="color:var(--c-available);"></span><div><div style="font-size:13px;font-weight:500;">食品经营许可证</div><div class="text-xs text-muted">已上传 · 已核验</div></div></div>
          <span class="tag tag-success">有效</span>
        </div>
        <div class="flex items-center justify-between" style="padding:10px 0;border-bottom:1px solid var(--c-divider);">
          <div class="flex items-center gap-3"><span class="icon" data-i="doc" style="color:var(--c-available);"></span><div><div style="font-size:13px;font-weight:500;">监管协议</div><div class="text-xs text-muted">已签署</div></div></div>
          <span class="tag tag-success">已签</span>
        </div>
        <div class="flex items-center justify-between" style="padding:10px 0;">
          <div class="flex items-center gap-3"><span class="icon" data-i="doc" style="color:var(--c-warning);"></span><div><div style="font-size:13px;font-weight:500;">消防合格证</div><div class="text-xs text-muted">即将到期 30天</div></div></div>
          <span class="row-btn warn">更新</span>
        </div>
      </div>
    </div>
  </div>

  <div class="card mb-5">
    <div class="card-head"><div class="card-title">线下门店地址 · AI地址核验</div><span class="tag tag-success tag-dot">核验通过</span></div>
    <div class="card-body">
      <div class="grid-2-eq" style="gap:20px;">
        <div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px;">
            <div><label class="field-label">省/市/区</label><input class="input" value="上海市/浦东新区/世纪大道"></div>
            <div><label class="field-label">详细地址</label><input class="input" value="世纪大道100号环陆家嘴中心"></div>
          </div>
          <div style="margin-bottom:12px;"><label class="field-label">经纬度</label><input class="input" value="121.5066, 31.2399" readonly></div>
          <div class="alert alert-info" style="font-size:12px;">
            <span class="icon icon-sm" data-i="ai"></span>
            <div><b>AI 地址核验结果:</b> 注册地址与工商信息一致，实景地图比对匹配度 96%，核验通过。今日地址修改次数: 0/3。</div>
          </div>
          <div class="flex gap-2 mt-3">
            <button class="btn btn-soft btn-sm">高德地图选点</button>
            <button class="btn btn-outline btn-sm">百度地图选点</button>
          </div>
        </div>
        <div class="map-box" id="shop-map-box">
          <svg class="map-svg" viewBox="0 0 720 300" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern id="waterPattern" width="40" height="40" patternUnits="userSpaceOnUse">
                <rect width="40" height="40" fill="#d9e6f2"/>
                <path d="M0 20 Q 10 15, 20 20 T 40 20" stroke="#c0d4e8" stroke-width="1" fill="none" opacity="0.6"/>
              </pattern>
              <linearGradient id="parkGrad" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#a5d6a7"/>
                <stop offset="100%" stop-color="#81c784"/>
              </linearGradient>
              <radialGradient id="heatGrad" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stop-color="var(--c-danger)" stop-opacity="0.55"/>
                <stop offset="40%" stop-color="var(--c-warning)" stop-opacity="0.35"/>
                <stop offset="100%" stop-color="var(--c-accent)" stop-opacity="0.08"/>
              </radialGradient>
              <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
                <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
                <feOffset dx="0" dy="2" result="off"/>
                <feComponentTransfer><feFuncA type="linear" slope="0.35"/></feComponentTransfer>
                <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
              </filter>
            </defs>
            <!-- 地面背景 -->
            <rect width="720" height="300" fill="#f4f6f8"/>
            <!-- 黄浦江/水域 -->
            <path d="M0,80 C120,60 240,100 360,88 C500,74 620,120 720,96 L720,170 C620,186 480,142 360,154 C240,166 120,200 0,170 Z" fill="url(#waterPattern)" stroke="#a8c5e0" stroke-width="1"/>
            <!-- 陆家嘴中心公园 -->
            <ellipse cx="420" cy="210" rx="68" ry="40" fill="url(#parkGrad)" opacity="0.9"/>
            <circle cx="395" cy="200" r="5" fill="#4e8f51" opacity="0.7"/>
            <circle cx="438" cy="218" r="4" fill="#4e8f51" opacity="0.7"/>
            <circle cx="410" cy="225" r="3" fill="#4e8f51" opacity="0.7"/>
            <!-- 主干道路（世纪大道方向） -->
            <rect x="0" y="140" width="720" height="14" fill="#ffffff" stroke="#d9dde3" stroke-width="0.5"/>
            <rect x="330" y="0" width="18" height="300" fill="#ffffff" stroke="#d9dde3" stroke-width="0.5"/>
            <!-- 道路中心虚线 -->
            <line x1="0" y1="147" x2="720" y2="147" stroke="#f5b301" stroke-width="1.5" stroke-dasharray="10 8"/>
            <line x1="339" y1="0" x2="339" y2="300" stroke="#f5b301" stroke-width="1.5" stroke-dasharray="10 8"/>
            <!-- 次干道 -->
            <rect x="0" y="230" width="720" height="9" fill="#ffffff" stroke="#e6eaef" stroke-width="0.5"/>
            <rect x="140" y="0" width="11" height="300" fill="#ffffff" stroke="#e6eaef" stroke-width="0.5"/>
            <rect x="560" y="0" width="11" height="300" fill="#ffffff" stroke="#e6eaef" stroke-width="0.5"/>
            <!-- 小区块（街区） -->
            <rect x="12"  y="28"  width="110" height="94" fill="#eef0f3" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="160" y="28"  width="154" height="94" fill="#edeef2" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="360" y="28"  width="182" height="94" fill="#eef0f3" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="580" y="28"  width="128" height="94" fill="#edeef2" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="12"  y="164" width="110" height="54" fill="#eef0f3" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="160" y="164" width="154" height="54" fill="#edeef2" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="360" y="164" width="182" height="54" fill="#eef0f3" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="580" y="164" width="128" height="54" fill="#edeef2" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="12"  y="248" width="110" height="40" fill="#eef0f3" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="160" y="248" width="154" height="40" fill="#edeef2" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="360" y="248" width="182" height="40" fill="#eef0f3" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <rect x="580" y="248" width="128" height="40" fill="#edeef2" stroke="#dce0e6" stroke-width="0.5" rx="3"/>
            <!-- 商业建筑/POI（不同高度长方体示意） -->
            <g filter="url(#softShadow)">
              <rect x="38"  y="52" width="54"  height="62" fill="#ffffff" stroke="#c5cbd4" rx="2"/>
              <rect x="62"  y="40" width="28"  height="14" fill="#3f7ca1" rx="2"/>
              <rect x="178" y="46" width="82"  height="68" fill="#ffffff" stroke="#c5cbd4" rx="2"/>
              <rect x="204" y="32" width="32"  height="16" fill="#2e6b9e" rx="2"/>
              <rect x="382" y="50" width="102" height="64" fill="#ffffff" stroke="#c5cbd4" rx="2"/>
              <rect x="414" y="30" width="40"  height="22" fill="#1d5a90" rx="2"/>
              <rect x="596" y="46" width="94"  height="68" fill="#ffffff" stroke="#c5cbd4" rx="2"/>
              <rect x="622" y="34" width="40"  height="14" fill="#447eaa" rx="2"/>
            </g>
            <!-- 行人热力（核销密集区域） -->
            <circle cx="340" cy="148" r="80" fill="url(#heatGrad)"/>
            <circle cx="340" cy="148" r="42" fill="var(--c-danger)" opacity="0.18"/>
            <!-- POI 商家/地铁站/银行图标 -->
            <g font-family="var(--ff-mono)" font-size="10" fill="var(--c-text-2)" text-anchor="middle">
              <circle cx="86"   cy="150" r="7" fill="var(--c-warning)" opacity="0.9"/>
              <text x="86" y="153" fill="#fff" font-size="8" font-weight="700">M</text>
              <text x="86" y="168">陆家嘴站</text>
              <circle cx="618"  cy="150" r="7" fill="var(--c-warning)" opacity="0.9"/>
              <text x="618" y="153" fill="#fff" font-size="8" font-weight="700">M</text>
              <text x="618" y="168">世纪大道站</text>
              <circle cx="640" cy="72"  r="6" fill="var(--c-accent-deep)" opacity="0.95"/>
              <text x="640" y="75" fill="#fff" font-size="8" font-weight="700">¥</text>
              <text x="640" y="86">银行</text>
              <circle cx="72"  cy="282" r="6" fill="var(--c-info)" opacity="0.95"/>
              <text x="72"  y="285" fill="#fff" font-size="8" font-weight="700">购</text>
              <text x="72"  y="297">商场</text>
            </g>
            <!-- 目标门店（锦华餐饮 · 总店）锚点 -->
            <g transform="translate(339,147)">
              <!-- 呼吸脉冲外圈 -->
              <circle class="map-pin-circle" cx="0" cy="0" r="22" fill="none" stroke="var(--c-danger)" stroke-width="2" opacity="0.45">
                <animate attributeName="r" values="12;28;12" dur="2.2s" repeatCount="indefinite"/>
                <animate attributeName="opacity" values="0.55;0.05;0.55" dur="2.2s" repeatCount="indefinite"/>
              </circle>
              <circle r="8" fill="#fff" stroke="var(--c-danger)" stroke-width="2"/>
              <path class="map-pin" d="M0,-4 C-8,-4 -13,-12 -13,-22 C-13,-32 -5,-40 0,-44 C5,-40 13,-32 13,-22 C13,-12 8,-4 0,-4 Z" fill="var(--c-danger)"/>
              <circle cx="0" cy="-22" r="5" fill="#fff"/>
              <text x="0" y="-20" text-anchor="middle" font-size="8" font-weight="800" fill="var(--c-danger)" font-family="var(--ff-mono)">L</text>
            </g>
            <!-- 距离参考刻度 -->
            <line x1="590" y1="290" x2="670" y2="290" stroke="var(--c-text-2)" stroke-width="1.5"/>
            <line x1="590" y1="286" x2="590" y2="294" stroke="var(--c-text-2)" stroke-width="1.5"/>
            <line x1="670" y1="286" x2="670" y2="294" stroke="var(--c-text-2)" stroke-width="1.5"/>
            <text x="630" y="285" font-size="9" fill="var(--c-text-2)" font-family="var(--ff-mono)" text-anchor="middle">500 m</text>
          </svg>
          <div class="map-ctrl" role="group" aria-label="地图缩放控件">
            <button type="button" id="map-zoom-in" title="放大">+</button>
            <button type="button" id="map-zoom-out" title="缩小">−</button>
            <button type="button" id="map-reset" title="重置" style="font-size:11px;">⟳</button>
          </div>
          <div class="map-scale">比例尺 1:5000</div>
          <div style="position:absolute;bottom:12px;left:12px;background:#fff;padding:6px 12px;border-radius:8px;font-size:12px;box-shadow:var(--sh-sm);z-index:2;">
            <span class="text-available font-bold">●</span> 锦华餐饮·总店<br><span class="text-muted">世纪大道100号（121.5066, 31.2399）</span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div class="stat-grid">
    <div class="stat-card gold"><div class="sc-icon"><span class="icon" data-i="release"></span></div><div class="stat-label">今日核销额度</div><div class="stat-value">¥50,000</div><div class="text-xs text-muted mt-2">A档 · 已用 ¥18,200</div></div>
    <div class="stat-card green"><div class="sc-icon"><span class="icon" data-i="unlock"></span></div><div class="stat-label">监管账户余额</div><div class="stat-value">¥86,400</div><div class="text-xs text-muted mt-2">14% 暂扣 · 可核销释放</div></div>
    <div class="stat-card"><div class="sc-icon"><span class="icon" data-i="merchant"></span></div><div class="stat-label">本月营业额</div><div class="stat-value">¥385,000</div><div class="text-xs text-available mt-2">+12.4% 环比</div></div>
    <div class="stat-card"><div class="sc-icon"><span class="icon" data-i="promotion"></span></div><div class="stat-label">推荐用户数</div><div class="stat-value">248</div><div class="text-xs text-muted mt-2">活跃 186</div></div>
  </div>
  `;
  setView(html);
  bindMapControls();
}

/* 地图缩放/重置控件 */
function bindMapControls() {
  const svg = document.querySelector('#shop-map-box svg.map-svg');
  if (!svg) return;
  let scale = 1, tx = 0, ty = 0;
  function apply() {
    svg.style.transformOrigin = 'center center';
    svg.style.transition = 'transform 0.2s ease';
    svg.style.transform = `translate(${tx}px, ${ty}px) scale(${scale})`;
    const scaleEl = document.querySelector('.map-scale');
    if (scaleEl) scaleEl.textContent = `比例尺 1:${Math.round(5000/scale)}`;
  }
  const bIn = document.getElementById('map-zoom-in');
  const bOut = document.getElementById('map-zoom-out');
  const bReset = document.getElementById('map-reset');
  if (bIn) bIn.addEventListener('click', () => { scale = Math.min(scale * 1.25, 4); apply(); });
  if (bOut) bOut.addEventListener('click', () => { scale = Math.max(scale / 1.25, 0.5); apply(); });
  if (bReset) bReset.addEventListener('click', () => { scale = 1; tx = 0; ty = 0; apply(); });
}

/* ============== 商品管理 ============== */
function renderProduct() {
  const products = [
    { name:'精品双人套餐·周末限定', price:399, stock:500, status:'on', sales:1280, ai:'pass' },
    { name:'招牌全家福套餐', price:588, stock:300, status:'on', sales:860, ai:'pass' },
    { name:'下午茶双人组合', price:168, stock:200, status:'review', sales:0, ai:'review' },
    { name:'季节限定·蟹粉小笼礼盒', price:288, stock:150, status:'on', sales:540, ai:'pass' },
    { name:'商务宴请8人桌', price:2680, stock:50, status:'off', sales:42, ai:'pass' },
  ];
  const cards = products.map(p=>`
    <div class="product-card">
      <div class="product-img">
        <span class="icon icon-xl" data-i="product" style="color:var(--c-primary);opacity:0.5;width:48px;height:48px;"></span>
        <span class="tag ${p.status==='on'?'tag-success':p.status==='review'?'tag-warning':'tag-info'}" style="position:absolute;top:10px;left:10px;">${p.status==='on'?'上架中':p.status==='review'?'AI审核中':'已下架'}</span>
      </div>
      <div class="product-body">
        <div style="font-weight:600;font-size:14px;margin-bottom:6px;line-height:1.4;">${p.name}</div>
        <div class="flex items-center justify-between mb-2">
          <span style="font-size:18px;font-weight:700;color:var(--c-accent-deep);font-family:var(--ff-mono);">¥${p.price}</span>
          <span class="text-xs text-muted">= ${p.price} LSC</span>
        </div>
        <div class="flex items-center justify-between text-xs text-muted mb-3">
          <span>库存 ${p.stock}</span><span>已售 ${p.sales}</span>
        </div>
        <div class="flex gap-2">
          ${p.status==='review'
            ? '<span class="row-btn warn" style="flex:1;justify-content:center;">审核中</span>'
            : `<span class="row-btn" style="flex:1;justify-content:center;">编辑</span><span class="row-btn ${p.status==='on'?'gold':''}" style="flex:1;justify-content:center;">${p.status==='on'?'下架':'上架'}</span>`}
        </div>
      </div>
    </div>`).join('');

  const html = pageHead('商品管理', '商品发布/编辑/上下架 · 强制1:1定价校验 · AI图片视频审核', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="product"></span>发布商品</button>
  `) + `
  <div class="card mb-5">
    <div class="card-body">
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
        <div>
          <label class="field-label field-required">商品名称</label>
          <input class="input" placeholder="例: 精品双人套餐">
        </div>
        <div>
          <label class="field-label field-required">商品类目</label>
          <select class="select"><option>餐饮·套餐</option><option>餐饮·单品</option><option>餐饮·礼盒</option></select>
        </div>
        <div>
          <label class="field-label field-required">人民币价格 (元)</label>
          <div class="input-group"><span style="color:var(--c-accent-deep);font-weight:700;">¥</span><input class="input" id="rmb-price" placeholder="输入人民币金额" oninput="document.getElementById('lsc-price').value=this.value"></div>
          <div class="text-xs text-muted mt-1">1元 = 1 LSC · 强制1:1对应</div>
        </div>
        <div>
          <label class="field-label">LSC 价格 (自动填充)</label>
          <div class="input-group"><span style="color:var(--c-primary);font-weight:700;">L</span><input class="input" id="lsc-price" placeholder="自动同步" readonly style="background:var(--c-bg-soft);"></div>
        </div>
        <div>
          <label class="field-label">库存数量</label>
          <input class="input" type="number" placeholder="例: 500">
        </div>
        <div>
          <label class="field-label">商品视频 (选填)</label>
          <div style="border:1px dashed var(--c-border);border-radius:8px;padding:18px;text-align:center;color:var(--c-text-3);font-size:12px;cursor:pointer;" onclick="resultModal('视频上传','已调用视频上传接口, AI将自动审核视频内容真实性与版权。','info')">
            <span class="icon" data-i="eye"></span> 点击上传视频 · AI自动审核
          </div>
        </div>
      </div>
      <div class="alert alert-info mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="ai"></span>商品发布后, AI将自动审核图片/视频/文本, 审核结果实时推送给您。违规内容将自动驳回。</div>
    </div>
  </div>

  <div class="toolbar">
    <div class="seg">
      <span class="seg-item active">全部 ${products.length}</span>
      <span class="seg-item">上架中 3</span>
      <span class="seg-item">审核中 1</span>
      <span class="seg-item">已下架 1</span>
    </div>
  </div>
  <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px;">${cards}</div>
  `;
  setView(html);
}

/* ============== LSC账户 ============== */
function renderWallet() {
  const txTypes = {
    1:{tag:'tag-locked',label:'消费发行'},
    2:{tag:'tag-available',label:'每日释放'},
    3:{tag:'tag-accent',label:'推广奖励'},
    4:{tag:'tag-info',label:'权益商城'},
    5:{tag:'tag-info',label:'线下消费'},
    6:{tag:'tag-warning',label:'过期转回'},
    7:{tag:'tag-success',label:'商家核销'},
    8:{tag:'tag-info',label:'B2B流转'},
    9:{tag:'tag-warning',label:'退款退回'},
  };
  const txs = [
    {type:2, amount:385.20, lockedBefore:385585, lockedAfter:385200, availBefore:186035, availAfter:186420, orderId:'REL20260827002', ts:Date.now()-3600000},
    {type:7, amount:5000, lockedBefore:385585, lockedAfter:385585, availBefore:191035, availAfter:186035, orderId:'NH20260824001', ts:Date.now()-86400000},
    {type:1, amount:880, lockedBefore:384705, lockedAfter:385585, availBefore:186915, availAfter:186035, orderId:'ORD20260824008', ts:Date.now()-90000000},
    {type:3, amount:88, lockedBefore:384705, lockedAfter:384705, availBefore:186827, availAfter:186915, orderId:'PROMO20260824005', ts:Date.now()-172800000},
    {type:8, amount:12000, lockedBefore:384705, lockedAfter:384705, availBefore:174827, availAfter:186827, orderId:'B2B20260822003', ts:Date.now()-259200000},
  ];
  const rows = txs.map(t=>{
    const ty = txTypes[t.type];
    const isIn = [2,3,8,9].includes(t.type);
    return `<tr>
      <td><span class="tag ${ty.tag}">${ty.label}</span></td>
      <td><span style="font-family:var(--ff-mono);font-weight:600;color:${isIn?'var(--c-available)':'var(--c-danger)'};">${isIn?'+':'-'}${LSC.fmtNum(t.amount,2)}</span> LSC</td>
      <td class="text-xs text-muted">锁定 ${LSC.fmtNum(t.lockedBefore)} → ${LSC.fmtNum(t.lockedAfter)}</td>
      <td class="text-xs text-muted">可用 ${LSC.fmtNum(t.availBefore)} → ${LSC.fmtNum(t.availAfter)}</td>
      <td><span style="font-family:var(--ff-mono);font-size:11px;">${t.orderId}</span></td>
      <td class="text-xs text-muted">${LSC.fmtTime(t.ts)}</td>
    </tr>`;
  }).join('');

  const html = pageHead('LSC 账户', '可用余额 · 锁定余额 · 流水明细 · 释放记录 · 有效期管理', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="export"></span>导出流水</button>
  `) + `
  <div class="lsc-hero mb-5">
    <div class="flex justify-between items-start">
      <div>
        <div class="hero-label">可用 LSC 余额 (AVAILABLE)</div>
        <div><span class="hero-val">186,420.50</span><span class="hero-unit">LSC</span></div>
      </div>
      <span class="tag tag-accent" style="background:rgba(200,162,75,0.25);color:var(--c-accent-soft);">A档商家</span>
    </div>
    <div class="hero-foot">
      <div class="hf-item"><div class="hf-label">锁定池余额</div><div class="hf-val">385,200.00 LSC</div></div>
      <div class="hf-item"><div class="hf-label">总余额</div><div class="hf-val">571,620.50 LSC</div></div>
      <div class="hf-item"><div class="hf-label">今日释放</div><div class="hf-val" style="color:var(--c-accent-soft);">+385.20</div></div>
      <div class="hf-item"><div class="hf-label">监管账户</div><div class="hf-val">¥86,400</div></div>
    </div>
  </div>

  <div class="grid-2 mb-5">
    <div class="card">
      <div class="card-head"><div class="card-title">可用 LSC 明细 · 有效期</div><span class="tag tag-info">8条</span></div>
      <div class="card-body" style="padding:0;">
        <table class="table">
          <thead><tr><th>来源</th><th>金额</th><th>到期日</th><th>状态</th></tr></thead>
          <tbody>
            <tr><td>每日释放</td><td><b>385.20</b></td><td class="text-warning">2027-08-27</td><td><span class="tag tag-success">有效</span></td></tr>
            <tr><td>B2B流转</td><td><b>12,000.00</b></td><td>2027-08-24</td><td><span class="tag tag-success">有效</span></td></tr>
            <tr><td>推广奖励</td><td><b>88.00</b></td><td>2027-08-24</td><td><span class="tag tag-success">有效</span></td></tr>
            <tr><td>每日释放</td><td><b>380.00</b></td><td class="text-danger">2026-08-30</td><td><span class="tag tag-warning">即将过期</span></td></tr>
            <tr><td>消费发行</td><td><b>2,400.00</b></td><td>2027-08-20</td><td><span class="tag tag-success">有效</span></td></tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="card">
      <div class="card-head"><div class="card-title">近7日释放趋势</div></div>
      <div class="card-body">
        <div style="height:200px;">${(()=>{
          const data = [348,360,372,365,380,385,385];
          const w=320,h=200,pad={l:30,r:10,t:10,b:24},iw=w-pad.l-pad.r,ih=h-pad.t-pad.b;
          const max=Math.max(...data),min=Math.min(...data),range=max-min||1;
          const xAt=i=>pad.l+i*iw/(data.length-1);
          const yAt=v=>pad.t+ih-((v-min)/range)*ih;
          const pts=data.map((v,i)=>`${xAt(i)},${yAt(v)}`).join(' ');
          return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">
            <polyline points="${pts}" fill="none" stroke="var(--c-available)" stroke-width="2.5"/>
            <polygon points="${pad.l},${pad.t+ih} ${pts} ${xAt(data.length-1)},${pad.t+ih}" fill="var(--c-available)" opacity="0.12"/>
            ${data.map((v,i)=>`<circle cx="${xAt(i)}" cy="${yAt(v)}" r="3" fill="var(--c-available)"/>`).join('')}
            ${data.map((v,i)=>`<text x="${xAt(i)}" y="${h-pad.b+14}" font-size="9" fill="var(--c-text-3)" text-anchor="middle">D-${data.length-i}</text>`).join('')}
          </svg>`;
        })()}</div>
        <div class="flex justify-between text-xs text-muted mt-3"><span>本周累计释放</span><b style="color:var(--c-available);">+2,595.20 LSC</b></div>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">LSC 流水明细</div><div class="seg"><span class="seg-item active">全部</span><span class="seg-item">释放</span><span class="seg-item">核销</span><span class="seg-item">B2B</span></div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>流水类型</th><th>变动金额</th><th>锁定池变动</th><th>可用池变动</th><th>订单号</th><th>时间</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 核销管理 ============== */
function renderNH() {
  const html = pageHead('核销管理', '核销申请 · 现金结算(×0.87) · 核销记录 · 限额查询 · 收款码生成', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="release"></span>申请核销</button>
  `) + `
  <div class="stat-grid mb-5">
    <div class="stat-card gold"><div class="sc-icon"><span class="icon" data-i="release"></span></div><div class="stat-label">今日已核销</div><div class="stat-value">¥18,200</div><div class="text-xs text-muted mt-1">LSC 20,919 · ×0.87结算</div></div>
    <div class="stat-card"><div class="sc-icon"><span class="icon" data-i="unlock"></span></div><div class="stat-label">剩余额度</div><div class="stat-value">¥31,800</div><div class="progress mt-2"><div class="progress-bar-gold progress-bar" style="width:36.4%;"></div></div></div>
    <div class="stat-card green"><div class="sc-icon"><span class="icon" data-i="wallet"></span></div><div class="stat-label">监管账户余额</div><div class="stat-value">¥86,400</div><div class="text-xs text-muted mt-1">14%暂扣 · 可核销释放</div></div>
    <div class="stat-card"><div class="sc-icon"><span class="icon" data-i="credit"></span></div><div class="stat-label">核销档位</div><div class="stat-value">A档</div><div class="text-xs text-available mt-1">额度 ¥50,000/日</div></div>
  </div>

  <div class="grid-2-eq mb-5">
    <div class="card">
      <div class="card-head"><div class="card-title">商家专属收款码</div><span class="tag tag-success tag-dot">动态刷新</span></div>
      <div class="card-body" style="text-align:center;">
        <div class="qr-display">
          <div class="qr-grid">
            <div class="qr-corner qr-tl"></div>
            <div class="qr-corner qr-tr"></div>
            <div class="qr-corner qr-bl"></div>
            <div style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:40px;height:40px;background:var(--c-accent);border-radius:8px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:18px;">锦</div>
          </div>
        </div>
        <div class="mt-4">
          <div style="font-size:15px;font-weight:600;">锦华餐饮连锁·总店</div>
          <div class="text-xs text-muted mt-1">M20001 · 扫码即付 LSC</div>
          <div class="text-xs text-accent mt-2">⏱ 30秒后自动刷新 · 含防伪签名</div>
        </div>
        <div class="flex gap-2 justify-center mt-4">
          <button class="btn btn-outline btn-sm">下载打印</button>
          <button class="btn btn-soft btn-sm">刷新二维码</button>
        </div>
      </div>
    </div>
    <div class="card">
      <div class="card-head"><div class="card-title">核销申请</div><span class="tag tag-info">需校验资格</span></div>
      <div class="card-body">
        <div class="alert alert-success mb-3" style="font-size:12px;"><span class="icon icon-sm" data-i="check"></span>核销资格校验通过: 监管协议已签 · 信用92分 · 监管账户正常</div>
        <div class="mb-3"><label class="field-label field-required">核销 LSC 金额</label><div class="input-group"><span style="color:var(--c-primary);font-weight:700;">L</span><input class="input" id="nh-amount" placeholder="输入核销LSC数量" value="10000" oninput="calcNH()"></div></div>
        <div class="mb-3" style="padding:14px;background:var(--c-bg-soft);border-radius:10px;">
          <div class="flex justify-between text-sm mb-2"><span class="text-muted">核销 LSC 数量</span><b id="nh-lsc" style="font-family:var(--ff-mono);">10,000.00 LSC</b></div>
          <div class="flex justify-between text-sm mb-2"><span class="text-muted">现金结算 (×0.87)</span><b id="nh-cash" style="font-family:var(--ff-mono);color:var(--c-accent-deep);">¥8,700.00</b></div>
          <div class="flex justify-between text-sm mb-2"><span class="text-muted">扣减可用 LSC</span><b style="color:var(--c-danger);">-10,000.00</b></div>
          <div class="flex justify-between text-sm"><span class="text-muted">监管账户→主账户</span><b style="color:var(--c-available);">+¥8,700.00</b></div>
        </div>
        <div class="alert alert-info mb-3" style="font-size:11px;"><span class="icon icon-sm" data-i="info"></span>核销资金来源: 商家14%监管账户划拨至主账户。平台2%技术服务费已在消费分账时由支付机构直接清分, 与核销资金无关。</div>
        <button class="btn btn-primary btn-block" onclick="confirmModal('提交核销申请','核销资金来源: 商家14%监管账户划拨至主账户。提交后采用订单号+版本号乐观锁双重幂等校验。',()=>resultModal('核销申请已提交','核销申请已提交, 系统将校验幂等性并执行资金划拨。','success'),{btnText:'提交'})">提交核销申请</button>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">核销记录</div><span class="text-xs text-muted">订单号+幂等键双重校验</span></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>核销单号</th><th>核销LSC</th><th>现金结算</th><th>核销前可用</th><th>核销后可用</th><th>状态</th><th>时间</th></tr></thead>
      <tbody>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">NH20260827008</td><td><b>5,000.00</b></td><td style="color:var(--c-accent-deep);font-weight:600;">¥4,350.00</td><td>191,420</td><td>186,420</td><td><span class="tag tag-success">已完成</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-3600000)}</td></tr>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">NH20260824001</td><td><b>5,000.00</b></td><td style="color:var(--c-accent-deep);font-weight:600;">¥4,350.00</td><td>196,035</td><td>191,035</td><td><span class="tag tag-success">已完成</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-86400000)}</td></tr>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">NH20260820003</td><td><b>8,000.00</b></td><td style="color:var(--c-accent-deep);font-weight:600;">¥6,960.00</td><td>204,035</td><td>196,035</td><td><span class="tag tag-success">已完成</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-7*86400000)}</td></tr>
      </tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
  if (!window.calcNH) {
    window.calcNH = function() {
      const v = parseFloat(document.getElementById('nh-amount').value) || 0;
      document.getElementById('nh-lsc').textContent = v.toFixed(2) + ' LSC';
      document.getElementById('nh-cash').textContent = '¥' + (v * 0.87).toFixed(2);
    };
  }
}

/* ============== B2B交易 ============== */
function renderB2B() {
  const html = pageHead('B2B交易', '商家间LSC流转 · 必须绑定真实B2B订单 · AI核验贸易凭证 · 1:1金额对应', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="b2b"></span>创建B2B订单</button>
  `) + `
  <div class="card mb-5">
    <div class="card-head"><div class="card-title">创建 B2B 流转订单</div><span class="tag tag-warning">流转有效期365天重置</span></div>
    <div class="card-body">
      <div class="steps">
        <div class="step done"><div class="step-circle"><span class="icon icon-sm" data-i="check"></span></div><div class="step-label">创建订单</div></div>
        <div class="step active"><div class="step-circle">2</div><div class="step-label">对手方确认</div></div>
        <div class="step"><div class="step-circle">3</div><div class="step-label">AI核验凭证</div></div>
        <div class="step"><div class="step-circle">4</div><div class="step-label">LSC流转</div></div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
        <div><label class="field-label field-required">对手方商家</label>
          <select class="select"><option>御品茶业工坊 (M20005)</option><option>恒通建材批发中心 (M20002)</option><option>海纳科技公司 (M20007)</option></select>
        </div>
        <div><label class="field-label field-required">交易描述</label><input class="input" value="高端茶叶季度供应"></div>
        <div><label class="field-label field-required">人民币金额</label><div class="input-group"><span style="color:var(--c-accent-deep);font-weight:700;">¥</span><input class="input" value="96000" oninput="this.parentElement.parentElement.nextElementSibling.querySelector('input').value=this.value"></div></div>
        <div><label class="field-label">LSC金额 (1:1自动)</label><div class="input-group"><span style="color:var(--c-primary);font-weight:700;">L</span><input class="input" value="96000" readonly style="background:var(--c-bg-soft);"></div></div>
        <div><label class="field-label">合同编号</label><input class="input" value="HT-2026-0824-2"></div>
        <div><label class="field-label">过期时间</label><input class="input" type="date" value="2026-09-24"></div>
        <div style="grid-column:1/3;"><label class="field-label">贸易凭证 (合同/发票/物流单)</label>
          <div style="border:1px dashed var(--c-border);border-radius:8px;padding:20px;text-align:center;cursor:pointer;" onclick="resultModal('凭证上传','已调用凭证上传, AI将自动进行OCR提取、字段匹配与真伪核验。','info')">
            <span class="icon icon-lg" data-i="doc" style="color:var(--c-text-3);"></span>
            <div class="text-sm text-muted mt-2">点击上传贸易凭证图片 (支持多张)</div>
            <div class="text-xs text-accent mt-1">AI将提取合同编号/金额并与订单匹配</div>
          </div>
        </div>
      </div>
      <div class="alert alert-warning mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="warning"></span>消费者会员间禁止LSC流转 · 消费者→商家支付允许 · 商家→消费者反向流转禁止。流转后接收方LSC有效期重置为365天。</div>
      <div class="flex justify-end gap-3 mt-3"><button class="btn btn-outline btn-sm" onclick="resultModal('已保存草稿','B2B订单草稿已保存, 可稍后继续编辑。','info')">保存草稿</button><button class="btn btn-primary btn-sm" onclick="confirmModal('提交B2B订单','确认提交订单? 提交后进入待对手方确认状态, AI将并行核验凭证真伪。',()=>resultModal('订单已提交','B2B订单已提交, 等待对手方确认。AI核验同步进行中。','success'),{btnText:'提交'})">提交并等待确认</button></div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">B2B 订单记录</div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>订单号</th><th>对手方</th><th>描述</th><th>金额</th><th>AI核验</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">B2B20260824002</td><td>御品茶业工坊</td><td>高端茶叶季度供应</td><td><b>¥96,000</b><br><span class="text-xs text-available">96,000 LSC</span></td><td><span class="tag tag-success tag-dot">真实 98%</span></td><td><span class="tag tag-success">已完成</span></td><td><span class="row-btn">详情</span></td></tr>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">B2B20260822003</td><td>恒通建材批发中心</td><td>装修材料采购</td><td><b>¥12,000</b><br><span class="text-xs text-available">12,000 LSC</span></td><td><span class="tag tag-success tag-dot">真实 94%</span></td><td><span class="tag tag-success">已完成</span></td><td><span class="row-btn">详情</span></td></tr>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">B2B20260827009</td><td>海纳科技公司</td><td>智能点餐系统采购</td><td><b>¥45,000</b><br><span class="text-xs text-available">45,000 LSC</span></td><td><span class="tag tag-info">待核验</span></td><td><span class="tag tag-warning">待确认</span></td><td><span class="row-btn gold">催办</span></td></tr>
      </tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 推广管理 ============== */
function renderPromotion() {
  const html = pageHead('推广管理', '推荐关系 · 首单10%奖励 · 推广二维码 · 奖励记录', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="qr"></span>生成推广码</button>
  `) + `
  <div class="stat-grid mb-5">
    <div class="stat-card gold"><div class="sc-icon"><span class="icon" data-i="promotion"></span></div><div class="stat-label">累计推荐用户</div><div class="stat-value">248</div><div class="text-xs text-available mt-1">活跃 186</div></div>
    <div class="stat-card green"><div class="sc-icon"><span class="icon" data-i="wallet"></span></div><div class="stat-label">累计推广奖励</div><div class="stat-value">3,820</div><div class="text-xs text-muted mt-1">LSC · 首单10%</div></div>
    <div class="stat-card"><div class="sc-icon"><span class="icon" data-i="promotion"></span></div><div class="stat-label">本月新增</div><div class="stat-value">28</div><div class="text-xs text-available mt-1">+12.4%</div></div>
    <div class="stat-card"><div class="sc-icon"><span class="icon" data-i="order"></span></div><div class="stat-label">本月奖励</div><div class="stat-value">486</div><div class="text-xs text-muted mt-1">LSC</div></div>
  </div>

  <div class="grid-2-eq mb-5">
    <div class="card" style="text-align:center;">
      <div class="card-head" style="justify-content:center;"><div class="card-title">我的推广二维码</div></div>
      <div class="card-body">
        <div class="qr-display" style="width:180px;height:180px;">
          <div class="qr-grid" style="background-size:6px 6px;">
            <div class="qr-corner qr-tl" style="width:28px;height:28px;"></div>
            <div class="qr-corner qr-tr" style="width:28px;height:28px;"></div>
            <div class="qr-corner qr-bl" style="width:28px;height:28px;"></div>
            <div style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:32px;height:32px;background:var(--c-primary);border-radius:6px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:14px;">推</div>
          </div>
        </div>
        <div class="mt-3"><div style="font-weight:600;">锦华餐饮专属推广码</div><div class="text-xs text-muted mt-1">好友首单消费 · 您获10%奖励</div></div>
        <div class="flex gap-2 justify-center mt-3"><button class="btn btn-outline btn-sm">复制链接</button><button class="btn btn-soft btn-sm">保存图片</button></div>
      </div>
    </div>
    <div class="card">
      <div class="card-head"><div class="card-title">推广规则说明</div></div>
      <div class="card-body">
        <ul style="line-height:2;font-size:13px;color:var(--c-text-2);">
          <li><span style="color:var(--c-accent);">▸</span> 首单定义: 用户实名认证后第一笔有效消费(金额≥1元且未全额退款)</li>
          <li><span style="color:var(--c-accent);">▸</span> 奖励数量 = 首单消费金额 × 10%, 从您的锁定池划转至可用池</li>
          <li><span style="color:var(--c-accent);">▸</span> 写入流水类型3 (推广奖励释放)</li>
          <li><span style="color:var(--c-accent);">▸</span> 首单全额退款时, 已释放奖励从可用余额扣回锁定池</li>
          <li><span style="color:var(--c-accent);">▸</span> 余额不足生成欠款记录, 挂账自动补发</li>
        </ul>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">推广奖励记录</div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>被推荐人</th><th>首单金额</th><th>奖励LSC</th><th>状态</th><th>时间</th></tr></thead>
      <tbody>
        <tr><td>U10386 (李**)</td><td>¥880.00</td><td><b style="color:var(--c-accent-deep);">+88.00</b></td><td><span class="tag tag-success">已发放</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-86400000)}</td></tr>
        <tr><td>U10392 (王**)</td><td>¥560.00</td><td><b style="color:var(--c-accent-deep);">+56.00</b></td><td><span class="tag tag-success">已发放</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-2*86400000)}</td></tr>
        <tr><td>U10401 (张**)</td><td>¥1,280.00</td><td><b style="color:var(--c-accent-deep);">+128.00</b></td><td><span class="tag tag-warning">挂账补发</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-3*86400000)}</td></tr>
        <tr><td>U10415 (陈**)</td><td>¥320.00</td><td><b style="color:var(--c-danger);">-32.00</b></td><td><span class="tag tag-danger">退款扣回</span></td><td class="text-xs text-muted">${LSC.fmtTime(Date.now()-5*86400000)}</td></tr>
      </tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 信用中心 ============== */
function renderCredit() {
  const html = pageHead('信用中心', '商家信用分 · 违规记录 · 处罚状态 · 信用分影响核销档位', '') + `
  <div class="grid-2-eq mb-5">
    <div class="card" style="text-align:center;">
      <div class="card-head" style="justify-content:center;"><div class="card-title">当前信用分</div></div>
      <div class="card-body">
        <div class="credit-ring">
          ${ringChart(0.92, 'var(--c-available)', 140, 12)}
          <div class="cr-text">
            <div style="font-size:36px;font-weight:700;font-family:var(--ff-mono);color:var(--c-available);">92</div>
            <div style="font-size:11px;color:var(--c-text-3);">满分100 · A档</div>
          </div>
        </div>
        <div class="mt-3">
          <span class="tag tag-success tag-dot" style="font-size:13px;padding:5px 14px;">信用优秀</span>
        </div>
        <div class="text-xs text-muted mt-3">信用分≥80 → A档 (5万/日核销) · 60-79 → B档 · &lt;60 → C档</div>
      </div>
    </div>
    <div class="card">
      <div class="card-head"><div class="card-title">信用分构成</div></div>
      <div class="card-body">
        <div style="padding:10px 0;"><div class="flex justify-between mb-1"><span class="text-sm">合规经营</span><b class="text-available">30/30</b></div><div class="progress"><div class="progress-bar progress-bar-available" style="width:100%;"></div></div></div>
        <div style="padding:10px 0;"><div class="flex justify-between mb-1"><span class="text-sm">AI风险评分</span><b class="text-available">26/30</b></div><div class="progress"><div class="progress-bar progress-bar-available" style="width:87%;"></div></div></div>
        <div style="padding:10px 0;"><div class="flex justify-between mb-1"><span class="text-sm">核销健康度</span><b class="text-available">18/20</b></div><div class="progress"><div class="progress-bar progress-bar-available" style="width:90%;"></div></div></div>
        <div style="padding:10px 0;"><div class="flex justify-between mb-1"><span class="text-sm">用户评价</span><b class="text-available">18/20</b></div><div class="progress"><div class="progress-bar progress-bar-available" style="width:90%;"></div></div></div>
        <div class="divider"></div>
        <div class="flex justify-between"><span class="text-sm font-semibold">总分</span><b class="text-available" style="font-size:20px;font-family:var(--ff-mono);">92/100</b></div>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">违规与处罚记录</div><span class="tag tag-success">无在处处罚</span></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>记录ID</th><th>违规类型</th><th>描述</th><th>扣分</th><th>处罚</th><th>期间</th><th>状态</th></tr></thead>
      <tbody>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">V25018</td><td><span class="tag tag-warning">延迟发货</span></td><td>3笔订单超48小时未发货</td><td><span class="tag tag-danger">-5</span></td><td>警告</td><td>2026-05-10 ~ 2026-05-17</td><td><span class="tag tag-info">已解除</span></td></tr>
        <tr><td style="font-family:var(--ff-mono);font-size:11px;">V25002</td><td><span class="tag tag-warning">商品描述不符</span></td><td>1笔订单退款投诉成立</td><td><span class="tag tag-danger">-3</span></td><td>警告</td><td>2026-02-15 ~ 2026-02-22</td><td><span class="tag tag-info">已解除</span></td></tr>
      </tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== AI助手 ============== */
function renderAI() {
  const html = pageHead('AI 助手', '商家智能客服 · RAG规则检索 · 操作指导 · 转人工', '') + `
  <div class="card" style="height:560px;display:flex;flex-direction:column;">
    <div class="card-head" style="background:linear-gradient(90deg,var(--c-primary-tint),#fff);">
      <div class="flex items-center gap-3">
        <div class="avatar avatar-sm avatar-gold">AI</div>
        <div><div style="font-weight:600;">锦华商家智能助手</div><div class="text-xs text-available">● 在线 · RAG + 操作指导</div></div>
      </div>
      <span class="tag tag-info">商家助手Agent</span>
    </div>
    <div style="flex:1;overflow-y:auto;padding:20px;display:flex;flex-direction:column;gap:12px;background:var(--c-bg-soft);">
      <div class="chat-bubble chat-ai">您好, 锦华商家! 我是您的智能助手。可以帮您查询流水、解释规则、指导核销操作。请问有什么可以帮您?</div>
      <div class="chat-bubble chat-me">核销的现金是怎么结算的?</div>
      <div class="chat-bubble chat-ai">核销结算规则如下:<br><br>1️⃣ 现金结算金额 = 核销LSC金额 × <b>0.87</b><br>2️⃣ 资金来源: 从您14%监管账户划拨至主账户<br>3️⃣ 同时扣减您自身可用LSC余额<br>4️⃣ 平台2%技术服务费已在消费分账时由支付机构清分, 与核销资金无关<br><br>例如: 核销 10,000 LSC → 现金结算 ¥8,700 到主账户。</div>
      <div class="chat-bubble chat-me">我的信用分为什么是92?</div>
      <div class="chat-bubble chat-ai">您的信用分92分构成:<br>• 合规经营 30/30<br>• AI风险评分 26/30<br>• 核销健康度 18/20<br>• 用户评价 18/20<br><br>主要扣分项: AI风险评分中, 您上月有2笔B2B订单匹配度略低, 影响了2分。保持稳定交易即可恢复。</div>
      <div class="chat-bubble chat-ai">💡 您可以问我: "今日还能核销多少"、"如何创建B2B订单"、"推广奖励怎么算"</div>
    </div>
    <div style="padding:12px 16px;border-top:1px solid var(--c-divider);display:flex;gap:8px;background:#fff;">
      <input class="input" placeholder="输入您的问题..." style="flex:1;">
      <button class="btn btn-outline btn-sm">转人工</button>
      <button class="btn btn-primary btn-sm">发送</button>
    </div>
  </div>

  <div class="grid-3 mt-5">
    <div class="card chart-card"><div class="sc-icon" style="width:36px;height:36px;border-radius:10px;background:var(--c-accent-soft);color:var(--c-accent-deep);display:flex;align-items:center;justify-content:center;margin-bottom:10px;"><span class="icon" data-i="chat"></span></div><div style="font-weight:600;">智能问答</div><div class="text-xs text-muted mt-1">基于规则文档RAG检索, 准确率88.7%</div></div>
    <div class="card chart-card"><div class="sc-icon" style="width:36px;height:36px;border-radius:10px;background:var(--c-primary-tint);color:var(--c-primary);display:flex;align-items:center;justify-content:center;margin-bottom:10px;"><span class="icon" data-i="doc"></span></div><div style="font-weight:600;">操作指导</div><div class="text-xs text-muted mt-1">商品发布/核销/B2B 流程引导</div></div>
    <div class="card chart-card"><div class="sc-icon" style="width:36px;height:36px;border-radius:10px;background:var(--c-available-tint);color:var(--c-available);display:flex;align-items:center;justify-content:center;margin-bottom:10px;"><span class="icon" data-i="user"></span></div><div style="font-weight:600;">转人工客服</div><div class="text-xs text-muted mt-1">复杂问题无缝转接人工</div></div>
  </div>
  `;
  setView(html);
}

/* ============== 弹窗系统 ============== */
function openModal(opts) {
  closeModal();
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.id = 'global-modal';
  mask.innerHTML = `<div class="modal modal-detail">
    <div class="modal-head">
      <div class="modal-title">${opts.title || '详情'}</div>
      <span class="icon" data-i="close" id="gm-close" style="cursor:pointer;color:var(--c-text-3);"></span>
    </div>
    <div class="modal-body">${opts.body || ''}</div>
    ${opts.footer ? `<div class="modal-foot">${opts.footer}</div>` : ''}
  </div>`;
  document.body.appendChild(mask);
  mask.querySelectorAll('.icon[data-i]').forEach(el=>{ const k=el.getAttribute('data-i'); if(ICONS[k]) el.innerHTML=ICONS[k]; });
  mask.addEventListener('click', e=>{ if (e.target===mask || e.target.id==='gm-close') closeModal(); });
}
function closeModal() {
  const m = document.getElementById('global-modal');
  if (m) m.remove();
}
function resultModal(title, body, type='success') {
  const iconMap = { success:'check', warning:'warning', danger:'warning', info:'ai' };
  const colorMap = { success:'var(--c-available)', warning:'var(--c-warning)', danger:'var(--c-danger)', info:'var(--c-info)' };
  openModal({
    title,
    body: `<div style="text-align:center;padding:20px 10px;">
      <div style="width:64px;height:64px;border-radius:50%;background:${colorMap[type]};margin:0 auto 16px;display:flex;align-items:center;justify-content:center;">
        <span class="icon icon-xl" data-i="${iconMap[type]}" style="width:32px;height:32px;color:#fff;"></span>
      </div>
      <div style="font-size:17px;font-weight:700;">${title}</div>
      <div class="text-sm text-muted mt-2" style="line-height:1.7;">${body}</div>
    </div>`,
    footer: `<button class="btn btn-primary btn-sm" onclick="closeModal()">确定</button>`
  });
}
function confirmModal(title, body, onConfirm, opts={}) {
  openModal({
    title,
    body: `<div style="padding:8px 4px;">${body}</div>`,
    footer: `<button class="btn btn-outline btn-sm" onclick="closeModal()">取消</button><button class="btn ${opts.danger?'btn-danger':'btn-primary'} btn-sm" id="confirm-yes">${opts.btnText||'确认'}</button>`
  });
  document.getElementById('confirm-yes').addEventListener('click', ()=>{ closeModal(); onConfirm && onConfirm(); });
}

/* B2B订单详情 + AI核验进度 */
function showB2BDetail(oid) {
  const orders = {
    'B2B20260824002': { counter:'御品茶业工坊', desc:'高端茶叶季度供应', rmb:96000, lsc:96000, contract:'HT-2026-0824-2', verify:3, match:0.98, status:'completed', time:'2026-08-24 14:32' },
    'B2B20260822003': { counter:'恒通建材批发中心', desc:'装修材料采购', rmb:12000, lsc:12000, contract:'HT-2026-0822', verify:1, match:0.94, status:'completed', time:'2026-08-22 10:15' },
    'B2B20260827009': { counter:'海纳科技公司', desc:'智能点餐系统采购', rmb:45000, lsc:45000, contract:'HT-2026-0827', verify:0, match:0, status:'pending', time:'2026-08-27 09:08' },
  };
  const o = orders[oid];
  if (!o) return;
  const verifyMap = { 0:{tag:'tag-info',label:'待核验'}, 1:{tag:'tag-success',label:'AI判定真实'}, 2:{tag:'tag-warning',label:'AI判定可疑'}, 3:{tag:'tag-success',label:'人工确认真实'}, 4:{tag:'tag-danger',label:'人工确认虚假'} };
  const v = verifyMap[o.verify];
  const isVerifying = o.verify === 0;
  const body = `
    <div class="detail-grid">
      <div class="detail-field"><div class="detail-label">订单号</div><div class="detail-value mono">${oid}</div></div>
      <div class="detail-field"><div class="detail-label">合同编号</div><div class="detail-value mono">${o.contract}</div></div>
      <div class="detail-field"><div class="detail-label">对手方</div><div class="detail-value">${o.counter}</div></div>
      <div class="detail-field"><div class="detail-label">创建时间</div><div class="detail-value">${o.time}</div></div>
      <div class="detail-field" style="grid-column:1/3;"><div class="detail-label">交易描述</div><div class="detail-value">${o.desc}</div></div>
      <div class="detail-field"><div class="detail-label">人民币金额</div><div class="detail-value mono">${LSC.fmtMoney(o.rmb)}</div></div>
      <div class="detail-field"><div class="detail-label">LSC金额 (1:1)</div><div class="detail-value mono" style="color:var(--c-available);">${LSC.fmtNum(o.lsc)} LSC</div></div>
      <div class="detail-field"><div class="detail-label">AI核验结果</div><div class="detail-value"><span class="tag ${v.tag} tag-dot">${v.label}</span> ${o.match>0?`匹配度 ${(o.match*100).toFixed(0)}%`:''}</div></div>
      <div class="detail-field"><div class="detail-label">流转状态</div><div class="detail-value">${o.status==='completed'?'已完成流转':o.status==='pending'?'对手方待确认':'处理中'}</div></div>
    </div>
    <div class="divider"></div>
    <div class="detail-label" style="margin-bottom:8px;">贸易凭证</div>
    <div class="voucher-grid">
      <div class="voucher-thumb"><span class="icon icon-lg" data-i="doc"></span><div class="ai-tag-mini">${o.verify>=1?'合同 ✓':'待核'}</div></div>
      <div class="voucher-thumb"><span class="icon icon-lg" data-i="doc"></span><div class="ai-tag-mini">${o.verify>=1?'发票 ✓':'待核'}</div></div>
      <div class="voucher-thumb"><span class="icon icon-lg" data-i="doc"></span><div class="ai-tag-mini">${o.verify>=1?'物流 ✓':'待核'}</div></div>
    </div>
    ${isVerifying ? `<div class="mt-4" style="padding:14px;background:var(--c-accent-soft);border-radius:var(--r-md);">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;"><span class="icon icon-sm" data-i="ai" style="color:var(--c-accent-deep);"></span><b style="color:var(--c-accent-deep);">B2B OCR核验 Agent 进行中</b></div>
      <div class="ai-verify-progress"><div id="verify-bar" style="width:0%;"></div></div>
      <div class="verify-step done"><span class="vs-dot"></span>对手方已确认订单</div>
      <div class="verify-step active" id="step-ocr"><span class="vs-dot"></span>OCR提取合同编号/金额/双方信息...</div>
      <div class="verify-step" id="step-match"><span class="vs-dot"></span>字段匹配校验 (1:1金额对应)</div>
      <div class="verify-step" id="step-result"><span class="vs-dot"></span>多模态模型识别凭证真伪</div>
    </div>` : `<div class="alert alert-info mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="ai"></span>B2B OCR核验Agent: OCR提取合同信息并与订单匹配，多模态模型识别凭证真伪。强制校验LSC数量与人民币金额1:1对应。流转后接收方LSC有效期重置365天。</div>`}
  `;
  openModal({
    title: 'B2B订单详情',
    body,
    footer: `<button class="btn btn-outline btn-sm" onclick="closeModal()">关闭</button>${isVerifying?'<button class="btn btn-primary btn-sm" onclick="simulateVerify(\''+oid+'\')">模拟AI核验</button>':''}`
  });
  if (isVerifying) {
    let p = 0;
    window._verifyTimer = setInterval(()=>{
      p += 8;
      const bar = document.getElementById('verify-bar');
      if (bar) bar.style.width = Math.min(p, 100) + '%';
      if (p >= 40 && document.getElementById('step-ocr')) { document.getElementById('step-ocr').classList.replace('active','done'); document.getElementById('step-match').classList.add('active'); }
      if (p >= 75 && document.getElementById('step-match')) { document.getElementById('step-match').classList.replace('active','done'); document.getElementById('step-result').classList.add('active'); }
      if (p >= 100) { clearInterval(window._verifyTimer); closeModal(); resultModal('AI核验完成', `订单 ${oid} AI核验完成：<br>• OCR提取: 合同编号 ${o.contract} ✓<br>• 金额匹配: ¥${LSC.fmtNum(o.rmb)} = ${LSC.fmtNum(o.lsc)} LSC ✓<br>• 凭证真伪: 真实 (匹配度 96%)<br><br>等待对手方确认后即可执行LSC流转。`); }
    }, 300);
  }
}
function simulateVerify(oid) {
  // 模拟点击已自动运行的进度条，这里直接触发完成
  if (window._verifyTimer) { clearInterval(window._verifyTimer); }
  const bar = document.getElementById('verify-bar');
  if (bar) bar.style.width = '100%';
  setTimeout(()=>{ closeModal(); resultModal('AI核验完成', `订单 ${oid} AI核验完成：<br>• OCR提取: 合同编号匹配 ✓<br>• 金额匹配: 1:1对应 ✓<br>• 凭证真伪: 真实 (匹配度 96%)<br><br>等待对手方确认后即可执行LSC流转。`); }, 400);
}

/* 商品详情 */
function showProductDetail(pid) {
  const products = {
    'P001': { name:'精品双人套餐·周末限定', price:399, stock:500, status:'on', video:'ok', aiScore:0.92, aiTags:['真实','高清','合规'] },
    'P002': { name:'单人元气午餐套餐', price:58, stock:800, status:'on', video:'none', aiScore:0.88, aiTags:['真实','合规'] },
    'P003': { name:'招牌四菜一汤家宴', price:288, stock:200, status:'review', video:'ok', aiScore:0.65, aiTags:['需人工复核'] },
  };
  const p = products[pid];
  if (!p) return;
  const statusInfo = p.status==='on' ? {tag:'tag-success',label:'已上架'} : p.status==='review' ? {tag:'tag-warning',label:'审核中'} : {tag:'tag-info',label:'已下架'};
  const body = `
    <div class="grid-2" style="grid-template-columns:160px 1fr;gap:16px;">
      <div style="height:140px;border-radius:var(--r-md);background:linear-gradient(135deg,var(--c-primary-tint),var(--c-bg-soft));display:flex;align-items:center;justify-content:center;">
        <span class="icon icon-xl" data-i="product" style="width:48px;height:48px;color:var(--c-primary);opacity:0.5;"></span>
      </div>
      <div>
        <div style="font-size:16px;font-weight:700;">${p.name}</div>
        <div class="text-sm text-muted mt-1">商品ID: ${pid}</div>
        <div class="flex gap-2 mt-2 flex-wrap">
          <span class="tag ${statusInfo.tag} tag-dot">${statusInfo.label}</span>
          ${p.video==='ok'?'<span class="tag tag-info">含视频</span>':''}
        </div>
        <div class="mt-3"><span class="text-xs text-muted">售价</span> <b style="font-family:var(--ff-mono);color:var(--c-accent-deep);">${LSC.fmtMoney(p.price)}</b> <span class="text-xs text-available">= ${p.price} LSC (1:1)</span></div>
      </div>
    </div>
    <div class="divider"></div>
    <div class="detail-grid">
      <div class="detail-field"><div class="detail-label">库存</div><div class="detail-value mono">${p.stock}</div></div>
      <div class="detail-field"><div class="detail-label">AI审核置信度</div><div class="detail-value" style="color:${p.aiScore>0.8?'var(--c-available)':'var(--c-warning)'};">${(p.aiScore*100).toFixed(0)}%</div></div>
    </div>
    <div class="mt-3"><div class="detail-label" style="margin-bottom:6px;">AI审核标签</div>
      <div class="flex gap-2 flex-wrap">${p.aiTags.map(t=>`<span class="tag ${t.includes('需')?'tag-warning':'tag-success'}">${t}</span>`).join('')}</div>
    </div>
    <div class="alert alert-info mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="ai"></span>商品发布强制校验人民币价格与LSC价格1:1一致。视频上传后AI多模态模型自动审核。</div>
  `;
  openModal({
    title: '商品详情',
    body,
    footer: `<button class="btn btn-outline btn-sm" onclick="closeModal()">关闭</button>${p.status==='on'?`<button class="btn btn-danger btn-sm" onclick="closeModal();confirmModal('下架商品','确认下架 ${p.name}？',()=>resultModal('已下架','商品 ${p.name} 已下架。','warning'),{danger:true,btnText:'下架'})">下架</button>`:''}`
  });
}

/* 启动 */
renderDashboard();
