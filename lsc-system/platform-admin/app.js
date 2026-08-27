/* ============== 图标渲染 ============== */
document.querySelectorAll('.icon[data-i]').forEach(el=>{
  const key = el.getAttribute('data-i');
  if (ICONS[key]) el.innerHTML = ICONS[key];
});
document.getElementById('search-icon').innerHTML = ICONS.search;

/* ============== 视图路由 ============== */
const views = {
  dashboard: renderDashboard,
  merchant: renderMerchant,
  product: renderProduct,
  b2b: renderB2B,
  risk: renderRisk,
  credit: renderCredit,
  release: renderRelease,
  reconcile: renderReconcile,
  system: renderSystem,
  ai: renderAI,
};
const crumbMap = { dashboard:'仪表盘',merchant:'商家管理',product:'商品审核',b2b:'B2B订单管理',risk:'风控管理',credit:'信用管理',release:'释放管理',reconcile:'对账管理',system:'系统管理',ai:'AI中心' };

function navTo(view) {
  document.querySelectorAll('.nav-item').forEach(n=>n.classList.toggle('active', n.dataset.view===view));
  document.getElementById('crumb').textContent = crumbMap[view] || view;
  views[view]();
}
document.getElementById('nav').addEventListener('click', e=>{
  const item = e.target.closest('.nav-item');
  if (item) navTo(item.dataset.view);
});

/* ============== 通用 HTML 片段 ============== */
function pageHead(title, desc, extra='') {
  return `<div class="page-head flex items-center justify-between">
    <div><div class="page-title">${title}</div><div class="page-desc">${desc}</div></div>
    <div class="flex gap-3">${extra}</div>
  </div>`;
}

/* ============== 图表：折线/区域 ============== */
function lineChart(opts) {
  // opts: { w, h, series:[{data:[],color,name,area?}], labels:[], forecastFrom?:idx }
  // data 支持 null/undefined 跳过(断线)
  const { w=600, h=240, series, labels, forecastFrom } = opts;
  const pad = { l: 44, r: 16, t: 16, b: 28 };
  const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const allVals = series.flatMap(s=>s.data).filter(v=>v!=null && !isNaN(v));
  const min = Math.min(...allVals), max = Math.max(...allVals);
  const range = max - min || 1;
  const n = labels.length;
  const xStep = iw / Math.max(1, n-1);
  const yScale = v => pad.t + ih - ((v - min)/range)*ih;
  const xAt = i => pad.l + i * xStep;

  const yTicks = 4;
  let grid = '';
  for (let i=0;i<=yTicks;i++){
    const v = min + (range*i/yTicks);
    const y = yScale(v);
    grid += `<line x1="${pad.l}" y1="${y}" x2="${w-pad.r}" y2="${y}" stroke="var(--c-divider)" stroke-width="1" stroke-dasharray="3 3"/>`;
    grid += `<text x="${pad.l-6}" y="${y+3}" font-size="10" fill="var(--c-text-3)" text-anchor="end">${v.toFixed(4)}</text>`;
  }
  let xLabels = '';
  const labelStep = Math.ceil(n/8);
  labels.forEach((lb,i)=>{ if(i%labelStep===0) xLabels += `<text x="${xAt(i)}" y="${h-pad.b+16}" font-size="10" fill="var(--c-text-3)" text-anchor="middle">${lb}</text>`; });

  let paths = '';
  series.forEach(s=>{
    // 构建连续段(遇到null断开)
    const segs = [];
    let cur = [];
    s.data.forEach((v,i)=>{
      if (v==null || isNaN(v)) { if(cur.length){segs.push(cur);cur=[];} }
      else cur.push({i,v});
    });
    if (cur.length) segs.push(cur);

    segs.forEach(seg=>{
      const pts = seg.map(p=>`${xAt(p.i)},${yScale(p.v)}`).join(' ');
      if (s.area && seg===segs[0]) {
        const areaPts = `${xAt(seg[0].i)},${pad.t+ih} `+pts+` ${xAt(seg[seg.length-1].i)},${pad.t+ih}`;
        paths += `<polygon points="${areaPts}" fill="${s.color}" opacity="0.12"/>`;
      }
      paths += `<polyline points="${pts}" fill="none" stroke="${s.color}" stroke-width="${s.dash?2:2.2}" stroke-linejoin="round" ${s.dash?'stroke-dasharray="5 4"':''}/>`;
      // 末端点
      const last = seg[seg.length-1];
      if (seg===segs[segs.length-1] && s.lastDot!==false) paths += `<circle cx="${xAt(last.i)}" cy="${yScale(last.v)}" r="3" fill="${s.color}"/>`;
    });
  });
  // 预测分界线
  let fcLine = '';
  if (forecastFrom!=null) {
    const x = xAt(forecastFrom);
    fcLine = `<line x1="${x}" y1="${pad.t}" x2="${x}" y2="${pad.t+ih}" stroke="var(--c-accent)" stroke-width="1" stroke-dasharray="4 4"/>
    <text x="${x+4}" y="${pad.t+12}" font-size="10" fill="var(--c-accent-deep)" font-weight="600">AI预测</text>`;
  }
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">
    ${grid}${paths}${fcLine}${xLabels}
  </svg>`;
}

/* 柱状图 */
function barChart(opts) {
  const { w=600, h=240, data, labels, color='var(--c-primary)' } = opts;
  const pad = { l: 44, r: 12, t: 16, b: 28 };
  const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const max = Math.max(...data);
  const n = data.length;
  const bw = iw / n * 0.6;
  const gap = iw / n;
  let bars = '', xLabels = '';
  data.forEach((v,i)=>{
    const bh = (v/max)*ih;
    const x = pad.l + i*gap + (gap-bw)/2;
    const y = pad.t + ih - bh;
    bars += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" rx="3" fill="${color}" opacity="${0.6+0.4*(i/n)}"/>`;
    if (i%Math.ceil(n/8)===0) xLabels += `<text x="${x+bw/2}" y="${h-pad.b+16}" font-size="10" fill="var(--c-text-3)" text-anchor="middle">${labels[i]}</text>`;
  });
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${bars}${xLabels}</svg>`;
}

/* 进度环 */
function ringChart(pct, color='var(--c-primary)') {
  const r = 44, c = 2*Math.PI*r, off = c*(1-pct);
  return `<svg width="110" height="110"><circle cx="55" cy="55" r="${r}" fill="none" stroke="var(--c-border-soft)" stroke-width="8"/>
  <circle cx="55" cy="55" r="${r}" fill="none" stroke="${color}" stroke-width="8" stroke-linecap="round" stroke-dasharray="${c}" stroke-dashoffset="${off}" transform="rotate(-90 55 55)"/></svg>`;
}

/* ============== 图表：环形/饼图 ============== */
function donutChart(opts) {
  // opts: { w, h, data:[{label,value,color}], inner=0.55, unit='' }
  const { w=240, h=240, data, inner=0.55, unit='' } = opts;
  const cx = w/2, cy = h/2, r = Math.min(w,h)/2 - 8, ir = r*inner;
  const total = data.reduce((s,d)=>s+d.value, 0) || 1;
  let acc = -Math.PI/2, arcs = '', labels = '';
  data.forEach((d,i)=>{
    const ang = (d.value/total) * Math.PI * 2;
    const a0 = acc, a1 = acc + ang;
    acc = a1;
    const x0 = cx + r*Math.cos(a0), y0 = cy + r*Math.sin(a0);
    const x1 = cx + r*Math.cos(a1), y1 = cy + r*Math.sin(a1);
    const xi0 = cx + ir*Math.cos(a0), yi0 = cy + ir*Math.sin(a0);
    const xi1 = cx + ir*Math.cos(a1), yi1 = cy + ir*Math.sin(a1);
    const large = ang > Math.PI ? 1 : 0;
    arcs += `<path d="M ${x0} ${y0} A ${r} ${r} 0 ${large} 1 ${x1} ${y1} L ${xi1} ${yi1} A ${ir} ${ir} 0 ${large} 0 ${xi0} ${yi0} Z" fill="${d.color}" opacity="0.92"/>`;
    // 引线标签 (仅对占比>5%的扇区)
    const pct = d.value/total;
    if (pct > 0.05) {
      const mid = (a0+a1)/2;
      const lx = cx + (r+10)*Math.cos(mid), ly = cy + (r+10)*Math.sin(mid);
      labels += `<text x="${lx}" y="${ly+3}" font-size="10" fill="var(--c-text-2)" text-anchor="${Math.cos(mid)<0?'end':'start'}" font-weight="600">${(pct*100).toFixed(1)}%</text>`;
    }
  });
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${arcs}${labels}
    <text x="${cx}" y="${cy-4}" font-size="11" fill="var(--c-text-3)" text-anchor="middle">总计</text>
    <text x="${cx}" y="${cy+14}" font-size="16" font-weight="700" fill="var(--c-text-1)" text-anchor="middle" font-family="var(--ff-mono)">${total>=10000?(total/10000).toFixed(1)+'万':total}${unit}</text>
  </svg>`;
}

/* ============== 图表：热力图 ============== */
function heatmap(opts) {
  // opts: { w, h, rows:[labels], cols:[labels], data:[[val,...],...], colorBase }
  const { w=680, h=200, rows, cols, data, colorBase='var(--c-primary)' } = opts;
  const padL = 40, padT = 14, padB = 4, padR = 8;
  const cw = (w - padL - padR) / cols.length;
  const ch = (h - padT - padB) / rows.length;
  const max = Math.max(...data.flat()) || 1;
  let cells = '', xLabs = '', yLabs = '';
  data.forEach((row, ri)=>{
    row.forEach((v, ci)=>{
      const x = padL + ci*cw, y = padT + ri*ch;
      const op = v/max;
      cells += `<rect x="${x+1}" y="${y+1}" width="${cw-2}" height="${ch-2}" rx="2" fill="${colorBase}" opacity="${0.08+op*0.92}"><title>${rows[ri]} ${cols[ci]}: ${v}</title></rect>`;
      if (op > 0.5) cells += `<text x="${x+cw/2}" y="${y+ch/2+3}" font-size="9" fill="#fff" text-anchor="middle" font-weight="600">${v}</text>`;
    });
  });
  rows.forEach((lb,i)=>{ yLabs += `<text x="${padL-6}" y="${padT+i*ch+ch/2+3}" font-size="10" fill="var(--c-text-3)" text-anchor="end">${lb}</text>`; });
  cols.forEach((lb,i)=>{ if(i%3===0) xLabs += `<text x="${padL+i*cw+cw/2}" y="${h-padB+2}" font-size="9" fill="var(--c-text-3)" text-anchor="middle">${lb}</text>`; });
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${yLabs}${cells}${xLabs}</svg>`;
}

/* ============== 图表：堆叠柱状图 ============== */
function stackedBar(opts) {
  // opts: { w, h, labels, stacks:[{name,color,data:[]}], unit }
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

/* ============== 图表：雷达图 ============== */
function radarChart(opts) {
  // opts: { w, h, labels:[], series:[{name,color,data:[0-1,...]}], max=1 }
  const { w=280, h=280, labels, series, max=1 } = opts;
  const cx = w/2, cy = h/2, r = Math.min(w,h)/2 - 36;
  const n = labels.length;
  const ang = i => -Math.PI/2 + (i/n) * Math.PI * 2;
  const pt = (val, i) => [cx + (val/max)*r*Math.cos(ang(i)), cy + (val/max)*r*Math.sin(ang(i))];
  // 网格圈 (4层)
  let grid = '', axis = '', axisLabels = '';
  for(let g=1; g<=4; g++){
    const rr = r*g/4;
    let pts = '';
    for(let i=0;i<n;i++) pts += `${cx+rr*Math.cos(ang(i))},${cy+rr*Math.sin(ang(i))} `;
    grid += `<polygon points="${pts}" fill="none" stroke="var(--c-divider)" stroke-width="1" opacity="${0.3+g*0.15}"/>`;
  }
  labels.forEach((lb,i)=>{
    const [x,y] = pt(max*1.12, i);
    axis += `<line x1="${cx}" y1="${cy}" x2="${cx+r*Math.cos(ang(i))}" y2="${cy+r*Math.sin(ang(i))}" stroke="var(--c-divider)" stroke-width="1"/>`;
    axisLabels += `<text x="${x}" y="${y+3}" font-size="10" fill="var(--c-text-2)" text-anchor="middle" font-weight="600">${lb}</text>`;
  });
  // 数据多边形
  let polys = '';
  series.forEach(s=>{
    let pts = s.data.map((v,i)=>pt(v,i).join(',')).join(' ');
    polys += `<polygon points="${pts}" fill="${s.color}" fill-opacity="0.12" stroke="${s.color}" stroke-width="2"/>`;
    s.data.forEach((v,i)=>{
      const [x,y] = pt(v,i);
      polys += `<circle cx="${x}" cy="${y}" r="3" fill="${s.color}"/>`;
    });
  });
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:100%;">${grid}${axis}${polys}${axisLabels}</svg>`;
}

/* ============== 仪表盘 ============== */
function renderDashboard() {
  const d = MOCK.dashboard;
  const lockedPct = d.lockedTotal/(d.lockedTotal+d.availableTotal);
  const availPct = 1-lockedPct;
  const html = pageHead('运营仪表盘', '链盛通LSC凭证循环系统 · 全网实时态势', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="refresh"></span>刷新</button>
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="export"></span>导出日报</button>
  `) + `
  <!-- 核心指标 -->
  <div class="stat-grid mb-5 stagger">
    <div class="stat-card locked">
      <div class="stat-icon-bg"><span class="icon" data-i="lock"></span></div>
      <div class="stat-label">全网锁定池总量</div>
      <div><span class="stat-value">${(d.lockedTotal/1e6).toFixed(2)}</span><span class="stat-unit">百万 LSC</span></div>
      <div class="stat-delta stat-delta-up"><span class="icon icon-sm" data-i="arrowUp"></span>较昨日 +0.42%</div>
    </div>
    <div class="stat-card available">
      <div class="stat-icon-bg"><span class="icon" data-i="unlock"></span></div>
      <div class="stat-label">全网可用池总量</div>
      <div><span class="stat-value">${(d.availableTotal/1e6).toFixed(2)}</span><span class="stat-unit">百万 LSC</span></div>
      <div class="stat-delta stat-delta-up"><span class="icon icon-sm" data-i="arrowUp"></span>较昨日 +1.18%</div>
    </div>
    <div class="stat-card accent">
      <div class="stat-icon-bg"><span class="icon" data-i="release"></span></div>
      <div class="stat-label">当日释放总量</div>
      <div><span class="stat-value">${LSC.fmtNum(d.todayRelease)}</span><span class="stat-unit">LSC</span></div>
      <div class="stat-delta stat-delta-up"><span class="icon icon-sm" data-i="arrowUp"></span>批次 86 / 86 成功</div>
    </div>
    <div class="stat-card">
      <div class="stat-icon-bg"><span class="icon" data-i="merchant"></span></div>
      <div class="stat-label">当日核销总额</div>
      <div><span class="stat-value">${LSC.fmtNum(d.todayNHTotal)}</span><span class="stat-unit">LSC</span></div>
      <div class="stat-delta stat-delta-down"><span class="icon icon-sm" data-i="arrowDown"></span>核销率 k=0.72%</div>
    </div>
  </div>

  <!-- 双池可视化 + 核销率环 -->
  <div class="grid-2 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">锁定池 / 可用池 资金分布</div><div class="card-sub">全网LSC凭证双池实时状态</div></div>
      </div>
      <div class="pool-vis">
        <div class="pool-half locked">
          <div><div class="pool-label">LOCKED POOL · 锁定池</div><div class="pool-value">${LSC.fmtNum(d.lockedTotal)}</div></div>
          <div class="pool-pct">占比 ${(lockedPct*100).toFixed(1)}% · 动态缓释中</div>
          <div class="pool-wave"></div>
        </div>
        <div class="pool-half available">
          <div><div class="pool-label">AVAILABLE POOL · 可用池</div><div class="pool-value">${LSC.fmtNum(d.availableTotal)}</div></div>
          <div class="pool-pct">占比 ${(availPct*100).toFixed(1)}% · 可消费/流转/核销</div>
          <div class="pool-wave"></div>
        </div>
      </div>
    </div>
    <div class="card chart-card flex-col items-center" style="text-align:center;">
      <div class="card-title mb-3">当前核销率 k</div>
      <div class="ring" style="margin: 8px 0;">
        ${ringChart(d.currentK/0.01, 'var(--c-accent)')}
        <div class="ring-text">
          <div style="font-size:24px;font-weight:700;font-family:var(--ff-mono);color:var(--c-accent-deep);">${(d.currentK*100).toFixed(2)}%</div>
          <div style="font-size:11px;color:var(--c-text-3);">健康区间</div>
        </div>
      </div>
      <div style="font-size:12px;color:var(--c-text-3);">释放速率 rate = <b style="color:var(--c-primary);font-family:var(--ff-mono);">${(d.currentRate*10000).toFixed(3)}‱</b></div>
      <div class="alert alert-info mt-3" style="width:100%;font-size:12px;">k值处于0.50%-1.0%健康区间，按公式 rate = 0.075% - 0.05×k 动态计算</div>
    </div>
  </div>

  <!-- 趋势图 + AI 预测 -->
  <div class="grid-2-1 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">核销率 k 值趋势与AI预测</div><div class="card-sub">近30天历史 + 未来7天AI预测</div></div>
        <div class="flex gap-3 text-xs">
          <span class="tag tag-dot tag-info">历史</span>
          <span class="tag tag-dot tag-accent">AI预测</span>
        </div>
      </div>
      <div class="chart-area">${lineChart({
        w:760, h:280,
        labels: (() => {
          const labels = [];
          for(let i=29;i>=0;i--) labels.push('D-'+i);
          for(let i=1;i<=7;i++) labels.push('F+'+i);
          return labels;
        })(),
        series: [
          { data: [...MOCK.kHistory, ...Array(7).fill(null)], color: 'var(--c-locked)', name:'历史k值', area:true },
          { data: [...Array(29).fill(null), MOCK.kHistory[MOCK.kHistory.length-1], ...MOCK.kForecast7], color: 'var(--c-accent)', name:'AI预测', dash:true }
        ],
        forecastFrom: 30
      })}</div>
    </div>
    <div class="ai-panel">
      <span class="ai-tag"><span class="ai-dot"></span>AI 智能简报</span>
      <div class="ai-panel-title"><span class="icon icon-sm" data-i="ai"></span>今日运营洞察</div>
      <ul>
        <li><span class="ai-li-dot">▸</span><div>核销率 k=<b>0.72%</b>，处于健康区间，预计7天内升至0.82%，<b style="color:var(--c-available)">无需干预</b>。</div></li>
        <li><span class="ai-li-dot">▸</span><div>B2B流转量较昨日下降8.3%，建议关注恒通建材、海纳科技订单。</div></li>
        <li><span class="ai-li-dot">▸</span><div>检测到 <b style="color:var(--c-danger)">2家</b>商家信用分异常，AI已生成处罚建议。</div></li>
        <li><span class="ai-li-dot">▸</span><div>释放批次执行成功率100%，资金零误差，链上存证已完成。</div></li>
      </ul>
      <div style="margin-top:14px;">
        <div style="font-size:11px;color:var(--c-text-3);margin-bottom:4px;">AI综合健康度</div>
        <div class="ai-score-bar"><div style="width:92%;"></div></div>
        <div style="font-size:11px;color:var(--c-available);font-weight:600;margin-top:4px;">优秀 · 92分</div>
      </div>
    </div>
  </div>

  <!-- 实时交易 + 释放趋势 -->
  <div class="grid-2">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">每日释放总量趋势</div><div class="card-sub">近30天释放量(LSC)</div></div>
      </div>
      <div class="chart-area">${barChart({
        w:560, h:260,
        data: MOCK.releaseTrend,
        labels: MOCK.releaseTrend.map((_,i)=>'D'+(i+1)),
        color: 'var(--c-primary-soft)'
      })}</div>
    </div>
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">核心业务实时数据</div><div class="card-sub">今日累计</div></div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px;">
        <div class="task-card" style="border-left-color:var(--c-locked);"><div class="stat-label">消费发行</div><div class="stat-value" style="font-size:20px;">${LSC.fmtNum(d.todayConsume)}</div><div class="text-xs text-muted">LSC · 流水类型1</div></div>
        <div class="task-card" style="border-left-color:var(--c-available);"><div class="stat-label">商家核销</div><div class="stat-value" style="font-size:20px;">${LSC.fmtNum(d.todayNHTotal)}</div><div class="text-xs text-muted">LSC · 流水类型7</div></div>
        <div class="task-card" style="border-left-color:var(--c-accent);"><div class="stat-label">B2B流转</div><div class="stat-value" style="font-size:20px;">${LSC.fmtNum(d.todayB2B)}</div><div class="text-xs text-muted">LSC · 流水类型8</div></div>
        <div class="task-card" style="border-left-color:var(--c-warning);"><div class="stat-label">新增用户</div><div class="stat-value" style="font-size:20px;">${LSC.fmtNum(d.todayNewUser)}</div><div class="text-xs text-muted">人 · 含商家 ${d.todayNewMerchant}</div></div>
      </div>
      <div class="divider"></div>
      <div style="font-size:12px;color:var(--c-text-3);display:flex;justify-content:space-between;">
        <span>累计用户 ${LSC.fmtNum(d.userCount)}</span>
        <span>累计商家 ${LSC.fmtNum(d.merchantCount)}</span>
        <span>系统在线 99.98%</span>
      </div>
    </div>
  </div>

  <!-- 分布图表：商家类别 / B2B订单状态 / 风险等级 -->
  <div class="grid-3 mb-5 stagger">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 8px;">
        <div><div class="card-title">商家类别分布</div><div class="card-sub">按行业类型统计</div></div>
      </div>
      ${(() => {
        const typeMap = {};
        MOCK.merchants.forEach(m=>{ typeMap[m.type] = (typeMap[m.type]||0)+1; });
        const colors = ['var(--c-primary)','var(--c-accent)','var(--c-available)','var(--c-warning)','var(--c-info)','var(--c-danger)','var(--c-primary-soft)'];
        const data = Object.entries(typeMap).map(([k,v],i)=>({label:k,value:v,color:colors[i%colors.length]}));
        return `<div style="height:180px;">${donutChart({w:240,h:240,data})}</div>
        <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">${data.map(d=>`<span class="tag tag-dot" style="background:${d.color};color:#fff;font-size:10px;">${d.label} ${d.value}</span>`).join('')}</div>`;
      })()}
    </div>
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 8px;">
        <div><div class="card-title">B2B订单状态分布</div><div class="card-sub">今日订单状态</div></div>
      </div>
      ${(() => {
        const sMap = { confirmed:'待确认', completed:'已完成', pending:'进行中', await_verify:'AI核验中', rejected:'已拒绝' };
        const sColor = { confirmed:'var(--c-warning)', completed:'var(--c-available)', pending:'var(--c-info)', await_verify:'var(--c-accent)', rejected:'var(--c-danger)' };
        const cMap = {};
        MOCK.b2bOrders.forEach(o=>{ cMap[o.status] = (cMap[o.status]||0)+1; });
        const data = Object.entries(cMap).map(([k,v])=>({label:sMap[k]||k,value:v,color:sColor[k]||'var(--c-text-3)'}));
        return `<div style="height:180px;">${donutChart({w:240,h:240,data,unit:'单'})}</div>
        <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">${data.map(d=>`<span class="tag tag-dot" style="background:${d.color};color:#fff;font-size:10px;">${d.label} ${d.value}</span>`).join('')}</div>`;
      })()}
    </div>
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 8px;">
        <div><div class="card-title">风控事件等级分布</div><div class="card-sub">AI Agent 自动拦截</div></div>
      </div>
      ${(() => {
        const lMap = {};
        MOCK.riskLogs.forEach(r=>{ lMap[r.level] = (lMap[r.level]||0)+1; });
        const lColor = { high:'var(--c-danger)', medium:'var(--c-warning)', low:'var(--c-available)' };
        const lName = { high:'高危', medium:'中危', low:'低危' };
        const data = Object.entries(lMap).map(([k,v])=>({label:lName[k]||k,value:v,color:lColor[k]||'var(--c-text-3)'}));
        return `<div style="height:180px;">${donutChart({w:240,h:240,data,unit:'件'})}</div>
        <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">${data.map(d=>`<span class="tag tag-dot" style="background:${d.color};color:#fff;font-size:10px;">${d.label} ${d.value}</span>`).join('')}</div>`;
      })()}
    </div>
  </div>

  <!-- 活跃度热力图 + LSC流水构成 -->
  <div class="grid-2 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">消费活跃度热力图</div><div class="card-sub">近7天 × 24小时 · 颜色越深交易越密集</div></div>
      </div>
      <div class="chart-area">${heatmap({
        w: 680, h: 200,
        rows: ['周一','周二','周三','周四','周五','周六','周日'],
        cols: Array.from({length:24},(_,i)=>i+'时'),
        data: (() => {
          const rows = [];
          for(let d=0; d<7; d++){
            const row = [];
            for(let h=0; h<24; h++){
              let v = 0;
              if(h>=11 && h<=13) v += 60+Math.random()*40; // 午餐高峰
              if(h>=17 && h<=20) v += 70+Math.random()*50; // 晚餐高峰
              if(h>=8 && h<=22) v += 10+Math.random()*30;
              if(d>=5) v *= 1.3; // 周末
              row.push(Math.round(v));
            }
            rows.push(row);
          }
          return rows;
        })(),
        colorBase: 'var(--c-primary)'
      })}</div>
      <div style="font-size:11px;color:var(--c-text-3);margin-top:8px;display:flex;align-items:center;gap:8px;">
        <span>低</span>
        <div style="width:120px;height:10px;border-radius:5px;background:linear-gradient(to right,rgba(14,77,74,0.08),rgba(14,77,74,1));"></div>
        <span>高</span>
        <span style="margin-left:auto;color:var(--c-accent-deep);">高峰: 周六 18-20时</span>
      </div>
    </div>
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">LSC流水构成趋势</div><div class="card-sub">近7天 · 消费发行/核销/B2B流转堆叠</div></div>
      </div>
      <div class="chart-area">${(() => {
        const sb = stackedBar({
          w: 560, h: 260, unit: ' LSC',
          labels: ['D-6','D-5','D-4','D-3','D-2','D-1','今日'],
          stacks: [
            { name:'消费发行', color:'var(--c-locked)', data:[2100,2250,2180,2320,2280,2350,2340].map(v=>v*1000) },
            { name:'商家核销', color:'var(--c-available)', data:[1080,1120,1150,1180,1210,1220,1240].map(v=>v*1000) },
            { name:'B2B流转', color:'var(--c-accent)', data:[720,810,780,830,890,910,856].map(v=>v*1000) },
          ]
        });
        return sb.svg + `<div style="margin-top:8px;">${sb.legend}</div>`;
      })()}</div>
    </div>
  </div>

  <!-- LSC 凭证流转链路图 -->
  <div class="card chart-card mb-5">
    <div class="card-head" style="border:none;padding:0 0 12px;">
      <div><div class="card-title">LSC凭证流转链路全景</div><div class="card-sub">双池循环 · 消费→锁定→释放→核销/流转 闭环</div></div>
      <span class="tag tag-dot tag-accent">实时</span>
    </div>
    <div style="overflow-x:auto;">
    <svg viewBox="0 0 960 280" style="width:100%;min-width:720px;height:auto;">
      <defs>
        <marker id="arrow-flow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="var(--c-text-3)"/>
        </marker>
        <marker id="arrow-accent" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="var(--c-accent)"/>
        </marker>
      </defs>
      <!-- 节点: 消费者消费 -->
      <rect x="20" y="110" width="120" height="60" rx="12" fill="var(--c-primary-tint)" stroke="var(--c-primary)" stroke-width="1.5"/>
      <text x="80" y="135" font-size="13" font-weight="700" fill="var(--c-primary)" text-anchor="middle">消费者消费</text>
      <text x="80" y="152" font-size="10" fill="var(--c-text-3)" text-anchor="middle">¥支付 → 发行LSC</text>
      <!-- 节点: 锁定池 -->
      <rect x="220" y="80" width="140" height="70" rx="12" fill="var(--c-locked-tint,rgba(255,177,61,0.12))" stroke="var(--c-warning)" stroke-width="2"/>
      <text x="290" y="108" font-size="14" font-weight="700" fill="var(--c-warning)" text-anchor="middle">🔒 锁定池</text>
      <text x="290" y="128" font-size="11" fill="var(--c-text-2)" text-anchor="middle" font-family="var(--ff-mono)">${LSC.fmtNum(d.lockedTotal)}</text>
      <text x="290" y="142" font-size="9" fill="var(--c-text-3)" text-anchor="middle">每日 ${((d.currentRate)*100).toFixed(3)}% 缓释</text>
      <!-- 节点: 可用池 -->
      <rect x="440" y="80" width="140" height="70" rx="12" fill="var(--c-available-tint,rgba(45,179,128,0.12))" stroke="var(--c-available)" stroke-width="2"/>
      <text x="510" y="108" font-size="14" font-weight="700" fill="var(--c-available)" text-anchor="middle">✓ 可用池</text>
      <text x="510" y="128" font-size="11" fill="var(--c-text-2)" text-anchor="middle" font-family="var(--ff-mono)">${LSC.fmtNum(d.availableTotal)}</text>
      <text x="510" y="142" font-size="9" fill="var(--c-text-3)" text-anchor="middle">可消费/流转/核销</text>
      <!-- 节点: 商家核销 -->
      <rect x="660" y="40" width="120" height="55" rx="12" fill="var(--c-primary-tint)" stroke="var(--c-primary)" stroke-width="1.5"/>
      <text x="720" y="65" font-size="13" font-weight="700" fill="var(--c-primary)" text-anchor="middle">商家核销</text>
      <text x="720" y="82" font-size="10" fill="var(--c-text-3)" text-anchor="middle">14%→监管账户</text>
      <!-- 节点: B2B流转 -->
      <rect x="660" y="130" width="120" height="55" rx="12" fill="var(--c-accent-tint,rgba(200,162,75,0.12))" stroke="var(--c-accent)" stroke-width="1.5"/>
      <text x="720" y="155" font-size="13" font-weight="700" fill="var(--c-accent-deep)" text-anchor="middle">B2B流转</text>
      <text x="720" y="172" font-size="10" fill="var(--c-text-3)" text-anchor="middle">商家间凭证转移</text>
      <!-- 节点: 熔断/风控 -->
      <rect x="660" y="210" width="120" height="50" rx="12" fill="rgba(239,68,68,0.08)" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="4 3"/>
      <text x="720" y="232" font-size="12" font-weight="700" fill="var(--c-danger)" text-anchor="middle">风控拦截</text>
      <text x="720" y="248" font-size="9" fill="var(--c-text-3)" text-anchor="middle">AI实时监控</text>
      <!-- 节点: 释放执行 -->
      <rect x="830" y="80" width="110" height="70" rx="12" fill="var(--c-bg-soft)" stroke="var(--c-text-3)" stroke-width="1"/>
      <text x="885" y="108" font-size="12" font-weight="600" fill="var(--c-text-2)" text-anchor="middle">释放引擎</text>
      <text x="885" y="125" font-size="10" fill="var(--c-text-3)" text-anchor="middle">每日02:00</text>
      <text x="885" y="140" font-size="10" fill="var(--c-available)" text-anchor="middle" font-weight="600">86/86 ✓</text>
      <!-- 箭头连线 -->
      <line x1="140" y1="140" x2="218" y2="115" stroke="var(--c-primary)" stroke-width="2" marker-end="url(#arrow-flow)"/>
      <text x="175" y="120" font-size="9" fill="var(--c-primary)" font-weight="600">1:1发行</text>
      <line x1="360" y1="115" x2="438" y2="115" stroke="var(--c-accent)" stroke-width="2.5" marker-end="url(#arrow-accent)"/>
      <text x="395" y="108" font-size="9" fill="var(--c-accent-deep)" font-weight="600">动态释放</text>
      <line x1="580" y1="100" x2="658" y2="67" stroke="var(--c-primary)" stroke-width="2" marker-end="url(#arrow-flow)"/>
      <text x="615" y="78" font-size="9" fill="var(--c-primary)" font-weight="600">核销</text>
      <line x1="580" y1="130" x2="658" y2="155" stroke="var(--c-accent)" stroke-width="2" marker-end="url(#arrow-accent)"/>
      <text x="615" y="148" font-size="9" fill="var(--c-accent-deep)" font-weight="600">流转</text>
      <line x1="580" y1="150" x2="658" y2="230" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="4 3" marker-end="url(#arrow-flow)"/>
      <text x="600" y="200" font-size="9" fill="var(--c-danger)" font-weight="600">异常拦截</text>
      <line x1="360" y1="150" x2="828" y2="100" stroke="var(--c-text-3)" stroke-width="1" stroke-dasharray="3 3" marker-end="url(#arrow-flow)" opacity="0.5"/>
      <text x="600" y="240" font-size="9" fill="var(--c-text-3)" text-anchor="middle">释放引擎从锁定池批量划拨至可用池</text>
      <!-- 循环回流箭头 -->
      <path d="M 720 95 Q 720 20 290 20 Q 80 20 80 108" fill="none" stroke="var(--c-available)" stroke-width="1.5" stroke-dasharray="5 4" opacity="0.5" marker-end="url(#arrow-flow)"/>
      <text x="400" y="14" font-size="9" fill="var(--c-available)" font-weight="600" opacity="0.7">核销资金回流 → 新消费循环</text>
    </svg>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 商家管理 ============== */
function renderMerchant() {
  const rows = MOCK.merchants.map(m=>`
    <tr>
      <td><div style="font-weight:600;color:var(--c-primary);">${m.id}</div></td>
      <td>
        <div style="font-weight:500;">${m.name}</div>
        <div class="text-xs text-muted">${m.type} · ${m.addr}</div>
      </td>
      <td><span class="status-pill ${m.status==='normal'?'tag-success':m.status==='warning'?'tag-warning':'tag-danger'} tag-dot">${m.status==='normal'?'正常':m.status==='warning'?'预警':'处罚中'}</span></td>
      <td><b style="color:${m.credit>=80?'var(--c-available)':m.credit>=60?'var(--c-warning)':'var(--c-danger)'};font-family:var(--ff-mono);">${m.credit}</b></td>
      <td>
        <div style="display:flex;align-items:center;gap:6px;">
          <div class="ai-score-bar" style="width:60px;margin:0;"><div style="width:${m.aiRisk}%;background:${m.aiRisk>60?'var(--c-danger)':m.aiRisk>30?'var(--c-warning)':'var(--c-available)'};"></div></div>
          <span class="text-xs">${m.aiRisk}</span>
        </div>
      </td>
      <td>${LSC.fmtMoney(m.monthRevenue)}</td>
      <td><span class="tag ${m.nhLevel==='A'?'tag-success':m.nhLevel==='B'?'tag-warning':'tag-danger'}">${m.nhLevel}档</span> <span class="text-xs text-muted">/日${LSC.fmtNum(m.nhLimitDaily)}</span></td>
      <td><span class="tag ${m.aiAddr==='pass'?'tag-success':m.aiAddr==='suspect'?'tag-warning':'tag-danger'} tag-dot">${m.aiAddr==='pass'?'核验通过':'核验可疑'}</span></td>
      <td><div class="row-actions">
        <span class="row-btn" onclick="showMerchantDetail('${m.id}')">资质</span>
        <span class="row-btn warn" onclick="showAdjustLimit('${m.id}')">调额</span>
        <span class="row-btn danger" onclick="showPenalty('${m.id}')">处罚</span>
      </div></td>
    </tr>`).join('');
  const html = pageHead('商家管理', '商家入驻审核、资质管理、核销额度调整与信用监控', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="filter"></span>高级筛选</button>
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="export"></span>导出</button>
  `) + `
  <div class="toolbar">
    <div class="seg">
      <span class="seg-item active">全部 ${MOCK.merchants.length}</span>
      <span class="seg-item">待审核 12</span>
      <span class="seg-item">正常 8</span>
      <span class="seg-item">预警 3</span>
      <span class="seg-item">处罚 2</span>
    </div>
    <div class="input-group" style="margin-left:auto;">
      <input class="input" placeholder="搜索商家ID/名称/手机号" style="width:240px;">
      <button class="btn btn-soft btn-sm">搜索</button>
    </div>
  </div>
  <div class="card">
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr>
        <th>商家ID</th><th>商家信息</th><th>状态</th><th>信用分</th><th>AI风险</th><th>月营业额</th><th>核销档位</th><th>地址核验</th><th>操作</th>
      </tr></thead>
      <tbody>${rows}</tbody>
    </table>
    </div>
    <div style="padding:14px 16px;display:flex;align-items:center;justify-content:space-between;border-top:1px solid var(--c-divider);">
      <span class="text-xs text-muted">共 ${MOCK.merchants.length} 条记录 · 8物理库分片存储</span>
      <div class="flex gap-2 items-center">
        <button class="btn btn-ghost btn-sm">上一页</button>
        <span class="tag tag-info">1 / 12</span>
        <button class="btn btn-soft btn-sm">下一页</button>
      </div>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 商品审核 ============== */
function renderProduct() {
  const statusMap = {
    ai_pass: { tag:'tag-success', label:'AI通过' },
    ai_suspect: { tag:'tag-warning', label:'AI存疑' },
    ai_reject: { tag:'tag-danger', label:'AI驳回' },
    manual_review: { tag:'tag-info', label:'人工复核' },
  };
  const cards = MOCK.products.map(p=>{
    const s = statusMap[p.status];
    return `<div class="card" style="overflow:hidden;">
      <div style="height:140px;background:linear-gradient(135deg,var(--c-primary-tint),var(--c-bg-soft));display:flex;align-items:center;justify-content:center;position:relative;">
        <span class="icon icon-xl" data-i="product" style="color:var(--c-primary);opacity:0.5;width:48px;height:48px;"></span>
        <span class="tag ${s.tag}" style="position:absolute;top:10px;left:10px;">${s.label}</span>
        ${p.video==='ok'?'<span class="tag tag-info" style="position:absolute;top:10px;right:10px;"><span class="icon icon-sm" data-i="eye"></span> 视频</span>':''}
        ${p.video==='reject'?'<span class="tag tag-danger" style="position:absolute;top:10px;right:10px;">视频违规</span>':''}
      </div>
      <div style="padding:14px;">
        <div style="font-weight:600;font-size:14px;margin-bottom:4px;line-height:1.4;">${p.name}</div>
        <div class="text-xs text-muted mb-3">${p.merchant} · ${LSC.fmtMoney(p.price)} · 库存 ${p.stock}</div>
        <div class="mb-3">
          <div class="text-xs text-muted mb-1">AI审核置信度</div>
          <div class="flex items-center gap-2">
            <div class="ai-score-bar" style="flex:1;margin:0;"><div style="width:${p.aiScore*100}%;background:${p.aiScore>0.8?'var(--c-available)':p.aiScore>0.5?'var(--c-warning)':'var(--c-danger)'};"></div></div>
            <span class="text-sm font-bold" style="color:${p.aiScore>0.8?'var(--c-available)':p.aiScore>0.5?'var(--c-warning)':'var(--c-danger)'};">${(p.aiScore*100).toFixed(0)}%</span>
          </div>
        </div>
        <div class="flex gap-2 mb-3 flex-wrap">${p.aiTags.map(t=>`<span class="tag ${t.includes('违规')||t.includes('假冒')||t.includes('疑似')?'tag-danger':t.includes('需')?'tag-warning':'tag-success'}">${t}</span>`).join('')}</div>
        <div class="row-actions">
          ${p.status==='ai_reject'?`<span class="row-btn danger" onclick="showProductDetail('${p.id}')">详情</span>`:''}
          ${p.status==='manual_review'?`<span class="row-btn" onclick="showProductDetail('${p.id}')">详情</span><span class="row-btn" onclick="resultModal('审核通过','商品 ${p.name} 已通过审核并上架。')">通过</span><span class="row-btn danger" onclick="confirmModal('驳回商品','确认驳回商品 ${p.name}？驳回原因: 涉嫌违规内容。',()=>resultModal('已驳回','商品 ${p.name} 已驳回。',{danger:true}))">驳回</span>`:''}
          ${p.status==='ai_pass'?`<span class="row-btn" onclick="showProductDetail('${p.id}')">详情</span><span class="row-btn warn" onclick="confirmModal('下架商品','确认下架商品 ${p.name}？',()=>resultModal('已下架','商品 ${p.name} 已下架。','warning'),{danger:true,btnText:'下架'})">下架</span>`:''}
          ${p.status==='ai_suspect'?`<span class="row-btn" onclick="showProductDetail('${p.id}')">复核</span><span class="row-btn danger" onclick="confirmModal('驳回商品','确认驳回商品 ${p.name}？',()=>resultModal('已驳回','商品 ${p.name} 已驳回。',{danger:true}))">驳回</span>`:''}
        </div>
      </div>
    </div>`;
  }).join('');
  const html = pageHead('商品审核', 'AI智能审核商品图片/视频/文本，人工复核存疑商品，强制校验1:1定价', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="refresh"></span>刷新队列</button>
  `) + `
  <div class="toolbar">
    <div class="seg">
      <span class="seg-item active">全部 ${MOCK.products.length}</span>
      <span class="seg-item tag-success">AI通过 3</span>
      <span class="seg-item tag-warning">AI存疑 1</span>
      <span class="seg-item tag-danger">AI驳回 1</span>
      <span class="seg-item tag-info">待复核 1</span>
    </div>
  </div>
  <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px;">${cards}</div>
  <div class="ai-panel mt-6">
    <span class="ai-tag"><span class="ai-dot"></span>AI 商品审核 Agent</span>
    <div class="ai-panel-title"><span class="icon icon-sm" data-i="ai"></span>审核模型运行状态</div>
    <div class="grid-3" style="margin-top:12px;">
      <div><div class="stat-label">图像审核 ResNet</div><div class="font-bold" style="color:var(--c-available);">准确率 96.2%</div></div>
      <div><div class="stat-label">视频审核 多模态</div><div class="font-bold" style="color:var(--c-available);">准确率 94.8%</div></div>
      <div><div class="stat-label">文本分类 BERT</div><div class="font-bold" style="color:var(--c-available);">准确率 97.1%</div></div>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== B2B订单 ============== */
function renderB2B() {
  const verifyMap = {
    0: { tag:'tag-info', label:'待核验' },
    1: { tag:'tag-success', label:'AI真实' },
    2: { tag:'tag-warning', label:'AI可疑' },
    3: { tag:'tag-success', label:'人工确认' },
    4: { tag:'tag-danger', label:'人工虚假' },
  };
  const statusMap = {
    await_verify: { tag:'tag-info', label:'待AI核验' },
    pending: { tag:'tag-warning', label:'对手方待确认' },
    confirmed: { tag:'tag-success', label:'已确认流转' },
    completed: { tag:'tag-success', label:'已完成' },
    rejected: { tag:'tag-danger', label:'已驳回' },
  };
  const rows = MOCK.b2bOrders.map(o=>{
    const v = verifyMap[o.aiVerify], s = statusMap[o.status];
    return `<tr>
      <td><div style="font-weight:600;font-family:var(--ff-mono);font-size:12px;">${o.id}</div></td>
      <td><div><b>${o.from}</b></div><div class="text-xs text-muted">→ ${o.to}</div></td>
      <td><div style="font-size:13px;">${o.desc}</div><div class="text-xs text-muted">合同: ${o.contract}</div></td>
      <td><div style="font-family:var(--ff-mono);font-weight:600;">${LSC.fmtMoney(o.rmb)}</div><div class="text-xs text-available">≈ ${LSC.fmtNum(o.lsc)} LSC</div></td>
      <td><span class="tag ${v.tag} tag-dot">${v.label}</span><div class="text-xs text-muted mt-1">匹配度 ${(o.aiMatch*100).toFixed(0)}%</div></td>
      <td><span class="tag ${s.tag}">${s.label}</span></td>
      <td><div class="row-actions">
        ${o.status==='await_verify'?`<span class="row-btn" onclick="confirmModal('触发AI核验','确认触发AI核验订单 ${o.id}？',()=>resultModal('AI核验已触发','订单 ${o.id} 已提交至B2B OCR核验Agent，预计5秒内完成。'))">核验</span>`:''}
        ${o.status==='pending'?`<span class="row-btn" onclick="showB2BDetail('${o.id}')">凭证</span>`:''}
        ${o.status==='rejected'?`<span class="row-btn danger" onclick="showB2BDetail('${o.id}')">原因</span>`:''}
        ${o.status==='confirmed'||o.status==='completed'?`<span class="row-btn" onclick="showB2BDetail('${o.id}')">链上</span>`:''}
        <span class="row-btn warn" onclick="showB2BDetail('${o.id}')">详情</span>
      </div></td>
    </tr>`;
  }).join('');
  const html = pageHead('B2B订单管理', '商家间LSC流转订单全生命周期管理 · AI核验贸易凭证 · 1:1金额对应校验', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="export"></span>导出</button>
  `) + `
  <div class="stat-grid mb-5" style="grid-template-columns:repeat(4,1fr);">
    <div class="stat-card"><div class="stat-label">今日B2B流转</div><div class="stat-value" style="font-size:20px;">${LSC.fmtNum(MOCK.dashboard.todayB2B)}</div><div class="text-xs text-muted">LSC</div></div>
    <div class="stat-card"><div class="stat-label">待核验订单</div><div class="stat-value" style="font-size:20px;color:var(--c-warning);">1</div><div class="text-xs text-muted">笔</div></div>
    <div class="stat-card"><div class="stat-label">AI核验通过率</div><div class="stat-value" style="font-size:20px;color:var(--c-available);">94.2%</div><div class="text-xs text-muted">本月</div></div>
    <div class="stat-card"><div class="stat-label">异常订单</div><div class="stat-value" style="font-size:20px;color:var(--c-danger);">1</div><div class="text-xs text-muted">人工虚假</div></div>
  </div>

  <!-- B2B 订单状态机 -->
  <div class="card mb-5">
    <div class="card-head"><div class="card-title">B2B订单生命周期状态机</div><div class="card-sub">全状态流转路径 · 绿色=正常 · 红色=异常终止</div></div>
    <div style="overflow-x:auto;">
    <svg viewBox="0 0 920 160" style="width:100%;min-width:680px;height:auto;">
      <defs>
        <marker id="sm-arrow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="var(--c-text-3)"/></marker>
        <marker id="sm-arrow-ok" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="var(--c-available)"/></marker>
        <marker id="sm-arrow-dng" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="var(--c-danger)"/></marker>
      </defs>
      <!-- 状态节点 -->
      <rect x="10" y="50" width="110" height="50" rx="8" fill="var(--c-bg-soft)" stroke="var(--c-text-3)" stroke-width="1.5"/>
      <text x="65" y="72" font-size="12" font-weight="700" fill="var(--c-text-2)" text-anchor="middle">创建订单</text>
      <text x="65" y="88" font-size="9" fill="var(--c-text-3)" text-anchor="middle">await_verify</text>

      <rect x="170" y="50" width="110" height="50" rx="8" fill="var(--c-info-tint,rgba(59,130,246,0.1))" stroke="var(--c-info)" stroke-width="1.5"/>
      <text x="225" y="72" font-size="12" font-weight="700" fill="var(--c-info)" text-anchor="middle">AI核验中</text>
      <text x="225" y="88" font-size="9" fill="var(--c-text-3)" text-anchor="middle">OCR+多模态</text>

      <rect x="330" y="50" width="110" height="50" rx="8" fill="var(--c-warning-tint,rgba(255,177,61,0.1))" stroke="var(--c-warning)" stroke-width="1.5"/>
      <text x="385" y="72" font-size="12" font-weight="700" fill="var(--c-warning)" text-anchor="middle">对手方确认</text>
      <text x="385" y="88" font-size="9" fill="var(--c-text-3)" text-anchor="middle">pending</text>

      <rect x="490" y="50" width="110" height="50" rx="8" fill="var(--c-available-tint,rgba(45,179,128,0.1))" stroke="var(--c-available)" stroke-width="1.5"/>
      <text x="545" y="72" font-size="12" font-weight="700" fill="var(--c-available)" text-anchor="middle">LSC流转</text>
      <text x="545" y="88" font-size="9" fill="var(--c-text-3)" text-anchor="middle">confirmed</text>

      <rect x="650" y="50" width="110" height="50" rx="8" fill="var(--c-available-tint,rgba(45,179,128,0.1))" stroke="var(--c-available)" stroke-width="2"/>
      <text x="705" y="72" font-size="12" font-weight="700" fill="var(--c-available)" text-anchor="middle">已完成</text>
      <text x="705" y="88" font-size="9" fill="var(--c-text-3)" text-anchor="middle">completed ✓</text>

      <rect x="330" y="120" width="110" height="36" rx="8" fill="rgba(239,68,68,0.08)" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="4 3"/>
      <text x="385" y="142" font-size="11" font-weight="700" fill="var(--c-danger)" text-anchor="middle">已驳回 rejected</text>

      <rect x="170" y="120" width="110" height="36" rx="8" fill="rgba(239,68,68,0.08)" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="4 3"/>
      <text x="225" y="142" font-size="11" font-weight="700" fill="var(--c-danger)" text-anchor="middle">人工虚假 rejected</text>

      <!-- 正常流转箭头 -->
      <line x1="120" y1="75" x2="168" y2="75" stroke="var(--c-text-3)" stroke-width="1.5" marker-end="url(#sm-arrow)"/>
      <line x1="280" y1="75" x2="328" y2="75" stroke="var(--c-available)" stroke-width="2" marker-end="url(#sm-arrow-ok)"/>
      <text x="304" y="68" font-size="9" fill="var(--c-available)" font-weight="600" text-anchor="middle">核验通过</text>
      <line x1="440" y1="75" x2="488" y2="75" stroke="var(--c-available)" stroke-width="2" marker-end="url(#sm-arrow-ok)"/>
      <text x="464" y="68" font-size="9" fill="var(--c-available)" font-weight="600" text-anchor="middle">确认</text>
      <line x1="600" y1="75" x2="648" y2="75" stroke="var(--c-available)" stroke-width="2" marker-end="url(#sm-arrow-ok)"/>
      <text x="624" y="68" font-size="9" fill="var(--c-available)" font-weight="600" text-anchor="middle">链上存证</text>

      <!-- 异常分支箭头 -->
      <path d="M 225 100 Q 225 120 280 120 L 328 120" fill="none" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="4 3" marker-end="url(#sm-arrow-dng)"/>
      <text x="265" y="114" font-size="9" fill="var(--c-danger)" font-weight="600">匹配度&lt;50%</text>
      <path d="M 385 100 Q 385 130 385 120" fill="none" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="4 3" marker-end="url(#sm-arrow-dng)"/>
      <text x="420" y="114" font-size="9" fill="var(--c-danger)" font-weight="600">对手方拒绝</text>

      <!-- 风控旁路 -->
      <path d="M 545 50 Q 545 20 385 20 Q 225 20 225 50" fill="none" stroke="var(--c-danger)" stroke-width="1.5" stroke-dasharray="3 3" opacity="0.5" marker-end="url(#sm-arrow-dng)"/>
      <text x="385" y="14" font-size="9" fill="var(--c-danger)" font-weight="600" text-anchor="middle">风控熔断 → 回退核验</text>
    </svg>
    </div>
  </div>

  <div class="card">
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr>
        <th>订单号</th><th>发起方 → 对手方</th><th>交易描述</th><th>人民币/LSC</th><th>AI核验</th><th>状态</th><th>操作</th>
      </tr></thead>
      <tbody>${rows}</tbody>
    </table>
    </div>
  </div>
  <div class="ai-panel mt-5">
    <span class="ai-tag"><span class="ai-dot"></span>B2B OCR 核验 Agent</span>
    <div class="ai-panel-title"><span class="icon icon-sm" data-i="ai"></span>智能凭证核验引擎</div>
    <ul>
      <li><span class="ai-li-dot">▸</span>OCR提取合同编号、金额、双方信息，与订单字段自动匹配</li>
      <li><span class="ai-li-dot">▸</span>多模态模型识别贸易凭证真伪，标注可疑区域供人工复核</li>
      <li><span class="ai-li-dot">▸</span>强制校验 LSC数量与人民币金额 1:1 对应，越界自动拦截</li>
    </ul>
  </div>
  `;
  setView(html);
}

/* ============== 风控管理 ============== */
function renderRisk() {
  const levelMap = { high:{tag:'tag-danger',label:'高风险'}, medium:{tag:'tag-warning',label:'中风险'}, low:{tag:'tag-info',label:'低风险'} };
  const rows = MOCK.riskLogs.map(r=>{
    const l = levelMap[r.level];
    return `<tr>
      <td><span style="font-family:var(--ff-mono);font-size:12px;">${r.id}</span></td>
      <td><span style="font-family:var(--ff-mono);">${r.user}</span></td>
      <td><span class="tag tag-info">${r.type}</span></td>
      <td>${r.detail}</td>
      <td><span class="tag ${l.tag} tag-dot">${l.label}</span></td>
      <td><div class="flex items-center gap-2"><div class="ai-score-bar" style="width:50px;margin:0;"><div style="width:${r.score*100}%;background:${r.score>0.7?'var(--c-danger)':r.score>0.4?'var(--c-warning)':'var(--c-available)'};"></div></div><span class="text-xs font-bold">${(r.score*100).toFixed(0)}</span></div></td>
      <td><span class="tag ${r.action.includes('封禁')||r.action.includes('冻结')||r.action.includes('限制')?'tag-danger':r.action.includes('复核')?'tag-warning':'tag-success'}">${r.action}</span></td>
      <td class="text-xs text-muted">${r.op}</td>
      <td class="text-xs text-muted">${LSC.fmtTime(r.ts)}</td>
    </tr>`;
  }).join('');
  const html = pageHead('风控管理', 'AI动态风控 + 固定规则风控双引擎 · 用户行为序列分析 · 实时风险评分', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="system"></span>规则配置</button>
  `) + `
  <div class="grid-2-1 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;"><div class="card-title">风控规则引擎</div><div class="card-sub">固定规则 + AI动态评分双引擎</div></div>
      <div class="param-row"><div><div class="param-key">单IP注册限频</div><div class="param-desc">同IP 24小时内注册不得超过3个账号</div></div><div class="param-val">3 <i>个/日</i></div></div>
      <div class="param-row"><div><div class="param-key">异常核销时段</div><div class="param-desc">凌晨0-6点连续核销≥5笔触发风控</div></div><div class="param-val">5 <i>笔</i></div></div>
      <div class="param-row"><div><div class="param-key">LSC聚集阈值</div><div class="param-desc">4+账户LSC集中转给1商家触发人工复核</div></div><div class="param-val">4 <i>账户</i></div></div>
      <div class="param-row"><div><div class="param-key">AI风险评分阈值</div><div class="param-desc">评分≥0.7自动处置，0.4-0.7人工复核</div></div><div class="param-val">0.70</div></div>
      <div class="param-row"><div><div class="param-key">设备指纹识别</div><div class="param-desc">同设备登录账号不得超过2个</div></div><div class="param-val">2 <i>个</i></div></div>
    </div>
    <div class="ai-panel">
      <span class="ai-tag"><span class="ai-dot"></span>用户风控 Agent</span>
      <div class="ai-panel-title"><span class="icon icon-sm" data-i="ai"></span>实时风控态势</div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:12px;">
        <div class="task-card"><div class="stat-label">今日拦截</div><div class="stat-value" style="font-size:22px;color:var(--c-danger);">28</div><div class="text-xs text-muted">起</div></div>
        <div class="task-card"><div class="stat-label">人工复核</div><div class="stat-value" style="font-size:22px;color:var(--c-warning);">12</div><div class="text-xs text-muted">起</div></div>
        <div class="task-card"><div class="stat-label">误判率</div><div class="stat-value" style="font-size:22px;color:var(--c-available);">2.1%</div><div class="text-xs text-muted">本月</div></div>
        <div class="task-card"><div class="stat-label">模型准确率</div><div class="stat-value" style="font-size:22px;color:var(--c-available);">97.9%</div><div class="text-xs text-muted">XGBoost</div></div>
      </div>
    </div>
  </div>
  <div class="card">
    <div class="card-head"><div class="card-title">用户风控日志</div><div class="card-sub">实时风控处置记录</div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>日志ID</th><th>用户ID</th><th>风控类型</th><th>详情</th><th>风险等级</th><th>AI评分</th><th>处置动作</th><th>操作人</th><th>时间</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 信用管理 ============== */
function renderCredit() {
  const rows = MOCK.violations.map(v=>`
    <tr>
      <td><span style="font-family:var(--ff-mono);font-size:12px;">${v.id}</span></td>
      <td><span style="font-family:var(--ff-mono);">${v.merchant}</span></td>
      <td><span class="tag tag-warning">${v.type}</span></td>
      <td>${v.detail}</td>
      <td><span class="tag tag-danger">-${v.deduct}</span></td>
      <td>${v.measure}</td>
      <td>${v.aiFound?'<span class="tag tag-accent tag-dot">AI发现</span>':'<span class="tag tag-info">人工</span>'}</td>
      <td class="text-xs text-muted">${LSC.fmtDate(v.start)} ~ ${LSC.fmtDate(v.end)}</td>
      <td class="text-xs text-muted">${v.op}</td>
      <td><span class="row-btn danger" onclick="showRevokePenalty('${v.id}')">撤销</span></td>
    </tr>`).join('');
  const html = pageHead('信用管理', '商家信用分查询 · 违规记录追溯 · AI发现的违规自动留痕 · 处罚双人审批', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="export"></span>导出</button>
  `) + `
  <div class="grid-3 mb-5">
    <div class="stat-card"><div class="stat-label">A档信用商家</div><div class="stat-value" style="color:var(--c-available);">3</div><div class="text-xs text-muted">≥80分 · 5万/日核销</div></div>
    <div class="stat-card"><div class="stat-label">B档信用商家</div><div class="stat-value" style="color:var(--c-warning);">3</div><div class="text-xs text-muted">60-79分 · 3万/日核销</div></div>
    <div class="stat-card"><div class="stat-label">C档/处罚中</div><div class="stat-value" style="color:var(--c-danger);">2</div><div class="text-xs text-muted">&lt;60分 · 1万/日或暂停</div></div>
  </div>
  <div class="card">
    <div class="card-head"><div class="card-title">商家违规记录</div><div class="card-sub">违规追溯 · 扣分留痕 · 处罚执行</div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>记录ID</th><th>商家ID</th><th>违规类型</th><th>违规描述</th><th>扣分</th><th>处罚措施</th><th>发现来源</th><th>处罚期间</th><th>操作人</th><th>操作</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    </div>
  </div>
  <div class="alert alert-warning mt-5"><span class="icon icon-sm" data-i="warning"></span>所有处罚执行操作均需<b>双人审批</b>签名验证，自动记录审计日志并上链存证。</div>
  `;
  setView(html);
}

/* ============== 释放管理 ============== */
function renderRelease() {
  const d = MOCK.dashboard;
  const html = pageHead('释放管理', '每日动态释放算法参数配置 · 任务监控 · AI趋势预测 · 人工熔断', `
    <button class="btn btn-danger btn-sm" onclick="showCircuitBreaker()"><span class="icon icon-sm" data-i="warning"></span>人工熔断</button>
  `) + `
  <div class="grid-2-1 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">释放速率 rate 与核销率 k 关系曲线</div><div class="card-sub">rate = 0.075% - 0.05×k (k∈[0.5%,1.0%])</div></div>
      </div>
      <div class="chart-area">${lineChart({
        w:560, h:280,
        labels: Array.from({length:21},(_,i)=>((0.5+i*0.025)*100).toFixed(2)+'%'),
        series: [{
          data: Array.from({length:21},(_,i)=>{ const k=0.005+i*0.00025; return LSC.calcRate(k); }),
          color: 'var(--c-primary)', name:'rate', area:true
        }]
      })}</div>
    </div>
    <div class="ai-panel">
      <span class="ai-tag"><span class="ai-dot"></span>释放趋势预测 Agent</span>
      <div class="ai-panel-title"><span class="icon icon-sm" data-i="ai"></span>LSTM 预测模型</div>
      <div style="margin-top:10px;">
        <div class="text-xs text-muted mb-2">未来7天 k 值预测</div>
        ${MOCK.kForecast7.map((k,i)=>`<div class="flex items-center justify-between text-sm" style="padding:4px 0;border-bottom:1px dashed var(--c-border-soft);"><span class="text-muted">D+${i+1}</span><b style="font-family:var(--ff-mono);color:var(--c-accent-deep);">${(k*100).toFixed(2)}%</b></div>`).join('')}
      </div>
      <div class="alert alert-info mt-3" style="font-size:12px;">预测7天内k值将稳步上升，建议保持当前参数，无需调整 k_min/k_max。</div>
    </div>
  </div>

  <div class="card mb-5">
    <div class="card-head"><div class="card-title">释放算法参数配置</div><div class="card-sub">rate_max/rate_min 不可编辑 · 其余参数可编辑(需双人审批)</div></div>
    <div class="card-body">
      <div class="param-row"><div><div class="param-key">rate_max <span class="tag" style="font-size:10px;">不可编辑</span></div><div class="param-desc">释放速率上限 · k≤0.50%时启用</div></div><div class="param-val locked-param">0.05% <i>‱</i></div></div>
      <div class="param-row"><div><div class="param-key">rate_min <span class="tag" style="font-size:10px;">不可编辑</span></div><div class="param-desc">释放速率下限 · k≥1.0%时启用</div></div><div class="param-val locked-param">0.03% <i>‱</i></div></div>
      <div class="param-row" style="cursor:pointer;" onclick="showParamEdit('k_min')"><div><div class="param-key">k_min <span class="tag tag-info" style="font-size:10px;">可编辑</span></div><div class="param-desc">健康核销率下限 · 低于此值启用rate_max</div></div><div class="param-val">0.50% <i>当前</i></div></div>
      <div class="param-row" style="cursor:pointer;" onclick="showParamEdit('k_max')"><div><div class="param-key">k_max <span class="tag tag-info" style="font-size:10px;">可编辑</span></div><div class="param-desc">健康核销率上限 · 高于此值启用rate_min</div></div><div class="param-val">1.0% <i>当前</i></div></div>
      <div class="param-row" style="cursor:pointer;" onclick="showParamEdit('alpha')"><div><div class="param-key">alpha <span class="tag tag-info" style="font-size:10px;">可编辑</span></div><div class="param-desc">动态加权平滑系数</div></div><div class="param-val">0.05 <i>当前</i></div></div>
      <div class="flex justify-end gap-3 mt-4"><button class="btn btn-outline btn-sm" onclick="showSimulation()"><span class="icon icon-sm" data-i="ai"></span>AI仿真推演</button><button class="btn btn-primary btn-sm" onclick="showParamEdit('k_min')">提交修改</button></div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">今日释放任务监控</div><div class="tag tag-success tag-dot">执行成功</div></div>
    <div class="card-body">
      <div class="grid-3 mb-4">
        <div class="task-card"><div class="stat-label">全网锁定总量</div><div class="stat-value" style="font-size:22px;">${LSC.fmtNum(d.lockedTotal)}</div><div class="text-xs text-muted">LSC</div></div>
        <div class="task-card"><div class="stat-label">当日应释放</div><div class="stat-value" style="font-size:22px;color:var(--c-accent-deep);">${LSC.fmtNum(d.todayRelease)}</div><div class="text-xs text-muted">rate × 锁定总量</div></div>
        <div class="task-card"><div class="stat-label">实际释放</div><div class="stat-value" style="font-size:22px;color:var(--c-available);">${LSC.fmtNum(d.todayRelease)}</div><div class="text-xs text-available">零误差 ✓</div></div>
      </div>
      <div class="task-step done"><span class="step-dot"></span>02:00 任务启动 (XXL-JOB集群单实例)</div>
      <div class="task-step done"><span class="step-dot"></span>02:01 计算 N_total / M_total → k=0.72%</div>
      <div class="task-step done"><span class="step-dot"></span>02:01 分段计算 rate=0.0385% (0.075%-0.05×0.0072)</div>
      <div class="task-step done"><span class="step-dot"></span>02:02 二次校验 rate ∈ [0.03%,0.05%] ✓</div>
      <div class="task-step done"><span class="step-dot"></span>02:03 批量执行 86 批次 (每批10万条) 全部成功</div>
      <div class="task-step done"><span class="step-dot"></span>02:08 汇总校验 实际=应释放 零误差</div>
      <div class="task-step done"><span class="step-dot"></span>02:09 链上存证 SHA-256哈希已上链 Fabric</div>
      <div class="alert alert-success mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="check"></span>任务完成 · 用时 9分12秒 · 链上交易ID: 0x8a3f...e291</div>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 对账管理 ============== */
function renderReconcile() {
  const html = pageHead('对账管理', '支付机构资金流水对账 · 链上存证校验 · 异常自动诊断', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="refresh"></span>生成今日对账</button>
  `) + `
  <div class="grid-2 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;"><div class="card-title">支付机构资金流水对账</div><div class="card-sub">2026-08-27</div></div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px;">
        <div class="task-card"><div class="stat-label">平台流水</div><div class="stat-value" style="font-size:20px;">${LSC.fmtMoney(MOCK.dashboard.todayConsume)}</div></div>
        <div class="task-card"><div class="stat-label">机构流水</div><div class="stat-value" style="font-size:20px;">${LSC.fmtMoney(MOCK.dashboard.todayConsume)}</div></div>
        <div class="task-card" style="border-left-color:var(--c-available);"><div class="stat-label">差异</div><div class="stat-value" style="font-size:20px;color:var(--c-available);">¥0.00</div></div>
        <div class="task-card" style="border-left-color:var(--c-available);"><div class="stat-label">对账状态</div><div class="stat-value" style="font-size:20px;color:var(--c-available);">平账</div></div>
      </div>
      <div class="alert alert-success mt-4" style="font-size:12px;">支付机构流水与平台账务完全一致，平台2%技术服务费已由支付机构直接清分。</div>
    </div>
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;"><div class="card-title">链上存证校验</div><div class="card-sub">Hyperledger Fabric 2.5</div></div>
      <div class="task-step done"><span class="step-dot"></span>关键操作流水哈希存证 (1248条)</div>
      <div class="task-step done"><span class="step-dot"></span>每日快照哈希上链 (D-1)</div>
      <div class="task-step done"><span class="step-dot"></span>链上数据完整性校验 通过</div>
      <div class="task-step done"><span class="step-dot"></span>双人审批操作存证 (8条)</div>
      <div class="task-step done"><span class="step-dot"></span>熔断操作存证 (0条)</div>
      <div class="alert alert-info mt-4" style="font-size:12px;">存证合约仅开放写入/查询方法，禁止修改删除。哈希序列化: 字段升序+SHA-256。</div>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><div class="card-title">分布式事务异常日志</div><div class="card-sub">Seata AT 回滚失败记录 · AI自动诊断</div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>记录ID</th><th>全局事务ID</th><th>业务类型</th><th>业务单号</th><th>异常内容</th><th>AI诊断</th><th>状态</th><th>处理人</th></tr></thead>
      <tbody>
        <tr><td>TX001</td><td style="font-family:var(--ff-mono);font-size:11px;">192.168.1.10-8623</td><td>LSC发行</td><td>ORD20260827001</td><td>分支事务回滚超时</td><td><span class="tag tag-accent">网络抖动</span></td><td><span class="tag tag-success">已处理</span></td><td>运维·自动</td></tr>
        <tr><td>TX002</td><td style="font-family:var(--ff-mono);font-size:11px;">192.168.1.12-9821</td><td>核销</td><td>NH20260827008</td><td>乐观锁冲突3次</td><td><span class="tag tag-accent">并发竞争</span></td><td><span class="tag tag-warning">处理中</span></td><td>运维·张工</td></tr>
      </tbody>
    </table>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== 系统管理 ============== */
function renderSystem() {
  const rows = MOCK.auditLogs.map(a=>`
    <tr>
      <td><span style="font-family:var(--ff-mono);font-size:12px;">${a.id}</span></td>
      <td><div style="font-weight:500;">${a.admin}</div><div class="text-xs text-muted">${a.role}</div></td>
      <td><span class="tag tag-info">${a.op}</span></td>
      <td>${a.detail}</td>
      <td class="text-xs text-muted" style="font-family:var(--ff-mono);">${a.ip}</td>
      <td class="text-xs text-muted">${a.device}</td>
      <td>${a.aiFlag?'<span class="tag tag-accent tag-dot">AI异常</span>':'<span class="tag tag-success">正常</span>'}</td>
      <td class="text-xs text-muted">${LSC.fmtTime(a.ts)}</td>
    </tr>`).join('');
  const html = pageHead('系统管理', '管理员账号 · 角色权限 · 双人审批 · 操作审计日志', `
    <button class="btn btn-primary btn-sm"><span class="icon icon-sm" data-i="user"></span>新增管理员</button>
  `) + `
  <div class="grid-3 mb-5">
    <div class="card chart-card"><div class="stat-label">超级管理员</div><div class="stat-value" style="font-size:24px;">2</div><div class="text-xs text-muted">林总 / 王董</div></div>
    <div class="card chart-card"><div class="stat-label">运营管理员</div><div class="stat-value" style="font-size:24px;">8</div><div class="text-xs text-muted">运营/财务/风控</div></div>
    <div class="card chart-card"><div class="stat-label">今日审计</div><div class="stat-value" style="font-size:24px;color:var(--c-accent-deep);">${MOCK.auditLogs.length}</div><div class="text-xs text-muted">条 · 含1条AI异常</div></div>
  </div>
  <div class="card mb-5">
    <div class="card-head"><div class="card-title">管理员操作审计日志</div><div class="card-sub">所有关键操作 · IP+设备指纹 · AI异常检测</div></div>
    <div style="overflow-x:auto;">
    <table class="table">
      <thead><tr><th>记录ID</th><th>操作人</th><th>操作类型</th><th>操作详情</th><th>IP地址</th><th>设备</th><th>AI标记</th><th>时间</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    </div>
  </div>
  <div class="card">
    <div class="card-head"><div class="card-title">双人审批签名验证</div><div class="card-sub">参数修改 / 处罚执行 必须双人签名</div></div>
    <div class="card-body">
      <div class="dual-approval">
        <div class="field"><label class="field-label field-required">第一管理员签名</label><input class="input" placeholder="输入管理员账号"></div>
        <div class="field"><label class="field-label field-required">第二管理员签名</label><input class="input" placeholder="输入管理员账号"></div>
      </div>
      <div class="alert alert-warning mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="lock"></span>签名验证: verifyDualApproval(sig1, sig2, payload) 需两位管理员<b>不同</b>且各自签名通过。所有签名操作记录审计并上链存证。</div>
      <div class="flex justify-end mt-3"><button class="btn btn-primary btn-sm">验证并执行</button></div>
    </div>
  </div>
  `;
  setView(html);
}

/* ============== AI中心 ============== */
function renderAI() {
  const agents = [
    {name:'商品审核Agent', desc:'图片/视频/文本审核 + AIGC文案生成', icon:'product', gold:false, calls:'12,860', acc:'96.2%'},
    {name:'B2B OCR核验Agent', desc:'提取合同信息并与订单匹配校验', icon:'doc', gold:false, calls:'8,420', acc:'94.8%'},
    {name:'地址核验Agent', desc:'地图实景与工商信息比对', icon:'location', gold:false, calls:'3,210', acc:'92.1%'},
    {name:'用户风控Agent', desc:'行为序列分析输出风险评分', icon:'risk', gold:false, calls:'48,920', acc:'97.9%'},
    {name:'商家风控Agent', desc:'综合指标评分与预警', icon:'merchant', gold:false, calls:'12,856', acc:'95.6%'},
    {name:'异常核销分析Agent', desc:'k值波动分析与异常核销识别', icon:'chart', gold:false, calls:'5,640', acc:'93.4%'},
    {name:'AI客服Agent', desc:'RAG从规则文档检索答案', icon:'chat', gold:false, calls:'86,420', acc:'88.7%'},
    {name:'管理员助手Agent', desc:'NL2SQL自然语言查询数据库', icon:'ai', gold:true, calls:'2,180', acc:'91.2%'},
  ];
  const cards = agents.map(a=>`
    <div class="agent-card ${a.gold?'gold':''}">
      <div class="agent-icon"><span class="icon icon-lg" data-i="${a.icon}"></span></div>
      <div class="agent-name">${a.name}</div>
      <div class="agent-desc">${a.desc}</div>
      <div class="agent-stat"><span>调用次数(今日)<br><span class="v">${a.calls}</span></span><span>准确率<br><span class="v" style="color:var(--c-available);">${a.acc}</span></span></div>
    </div>`).join('');
  const advAgents = [
    {name:'释放趋势预测Agent', desc:'LSTM输入历史k值/B2B/消费趋势，预测7-30天核销率', icon:'release'},
    {name:'参数仿真Agent', desc:'蒙特卡洛模拟修改k_min/k_max/alpha后系统表现', icon:'system'},
    {name:'供应链匹配Agent', desc:'图神经网络推荐上下游商家', icon:'flow'},
  ];
  const advCards = advAgents.map(a=>`
    <div class="agent-card gold">
      <div class="agent-icon"><span class="icon icon-lg" data-i="${a.icon}"></span></div>
      <div class="agent-name">${a.name}</div>
      <div class="agent-desc">${a.desc}</div>
      <div class="agent-stat"><span>输出形式<br><span class="v">仪表盘/报告</span></span><span>权限<br><span class="v" style="color:var(--c-text-3);">仅建议</span></span></div>
    </div>`).join('');

  const html = pageHead('AI中心', '8大运营Agent + 3大高级决策Agent · 统一AI网关 · 限流降级 · 仅建议无写权限', `
    <button class="btn btn-outline btn-sm"><span class="icon icon-sm" data-i="refresh"></span>刷新状态</button>
  `) + `
  <div class="alert alert-info mb-5"><span class="icon icon-sm" data-i="ai"></span><div><b>AI安全准则:</b> 所有AI服务仅具备数据库只读权限，无写权限。所有AIGC内容标注"AI生成"标识。AI接口限流单IP 20请求/秒，推理超时10秒自动降级为人工审核模式。</div></div>

  <div class="mb-3 text-sm font-bold" style="color:var(--c-text-1);">运营层 AI Agent (8个)</div>
  <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px;" class="mb-6 stagger">${cards}</div>

  <div class="mb-3 text-sm font-bold" style="color:var(--c-accent-deep);">高级决策 AI Agent (3个) · 仅展示供参考</div>
  <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px;" class="stagger">${advCards}</div>

  <div class="card mt-6">
    <div class="card-head"><div class="card-title">AI网关服务状态</div><div class="tag tag-success tag-dot">运行中</div></div>
    <div class="card-body">
      <div class="grid-3">
        <div class="task-card"><div class="stat-label">QPS限流</div><div class="stat-value" style="font-size:18px;">20/s</div><div class="text-xs text-muted">单IP</div></div>
        <div class="task-card"><div class="stat-label">推理超时</div><div class="stat-value" style="font-size:18px;">10s</div><div class="text-xs text-muted">自动降级</div></div>
        <div class="task-card"><div class="stat-label">缓存命中率</div><div class="stat-value" style="font-size:18px;color:var(--c-available);">68.4%</div><div class="text-xs text-muted">推理结果缓存</div></div>
        <div class="task-card"><div class="stat-label">模型版本</div><div class="stat-value" style="font-size:18px;">v6.2</div><div class="text-xs text-muted">灰度10%</div></div>
        <div class="task-card"><div class="stat-label">GPU利用率</div><div class="stat-value" style="font-size:18px;color:var(--c-warning);">72%</div><div class="text-xs text-muted">T4 × 2</div></div>
        <div class="task-card"><div class="stat-label">降级触发</div><div class="stat-value" style="font-size:18px;color:var(--c-available);">0</div><div class="text-xs text-muted">本月</div></div>
      </div>
    </div>
  </div>

  <!-- AI 风险雷达 + 实时数据流 -->
  <div class="grid-2 mb-5">
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">商家AI风险雷达图</div><div class="card-sub">多维风险评分对比 · 鼎盛物流(M20004) vs 御品茶业(M20005)</div></div>
      </div>
      <div style="display:flex;gap:12px;align-items:center;">
        <div style="flex:1;height:260px;">${radarChart({
          w: 300, h: 300, max: 100,
          labels: ['信用分','核销异常','地址核验','商品合规','设备风险','流水异常'],
          series: [
            { name:'鼎盛物流', color:'var(--c-danger)', data:[64,72,88,55,68,80] },
            { name:'御品茶业', color:'var(--c-available)', data:[96,8,95,98,5,10] },
          ]
        })}</div>
        <div style="width:140px;flex-shrink:0;">
          <div style="margin-bottom:12px;">
            <div class="text-xs text-muted">鼎盛物流 M20004</div>
            <div style="font-size:24px;font-weight:700;color:var(--c-danger);font-family:var(--ff-mono);">68<span style="font-size:12px;">分</span></div>
            <div class="ai-score-bar" style="margin-top:4px;"><div style="width:68%;background:var(--c-danger);"></div></div>
            <span class="tag tag-danger tag-dot" style="margin-top:4px;">处罚中</span>
          </div>
          <div>
            <div class="text-xs text-muted">御品茶业 M20005</div>
            <div style="font-size:24px;font-weight:700;color:var(--c-available);font-family:var(--ff-mono);">8<span style="font-size:12px;">分</span></div>
            <div class="ai-score-bar" style="margin-top:4px;"><div style="width:8%;background:var(--c-available);"></div></div>
            <span class="tag tag-success tag-dot" style="margin-top:4px;">正常</span>
          </div>
        </div>
      </div>
      <div class="alert alert-warning mt-3" style="font-size:12px;"><span class="icon icon-sm" data-i="warning"></span>AI建议: 鼎盛物流"地址核验"与"流水异常"维度得分过低,建议继续限制核销额度并加强审核。</div>
    </div>
    <div class="card chart-card">
      <div class="card-head" style="border:none;padding:0 0 12px;">
        <div><div class="card-title">释放速率实时数据流</div><div class="card-sub">k值 × rate 联动 · 近24小时</div></div>
        <span class="tag tag-dot tag-accent" id="ai-stream-live">● LIVE</span>
      </div>
      <div class="chart-area" id="rate-realtime-chart">${(()=>{
        const _svg = lineChart({
          w: 560, h: 220,
          labels: Array.from({length:24},(_,i)=>(i+1)+':00'),
          series: [
            { data: Array.from({length:24},(_,i)=>0.0060+Math.sin(i/4)*0.0008+(Math.random()-0.5)*0.0004), color:'var(--c-locked)', name:'k值(×100)', area:true, width:2.5 },
            { data: Array.from({length:24},(_,i)=>0.0035+Math.sin(i/4+1)*0.0005+(Math.random()-0.5)*0.00025), color:'var(--c-accent)', name:'rate(×100)', dash:true },
          ],
        });
        return _svg + '<div id="rate-realtime-legend" style="margin-top:8px;">'+
          '<span class="tag tag-dot" style="background:var(--c-locked);color:#fff;font-size:10px;">k值(×100)</span> '+
          '<span class="tag tag-dot" style="background:var(--c-accent);color:#fff;font-size:10px;">rate(×100)</span>'+
          '</div>';
      })()}</div>
      <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin-top:10px;">
        <div style="background:var(--c-bg-soft);border-radius:8px;padding:8px;text-align:center;">
          <div class="text-xs text-muted">当前 k 值</div>
          <div id="rate-k-val" style="font-size:16px;font-weight:700;color:var(--c-warning);font-family:var(--ff-mono);">0.72%</div>
        </div>
        <div style="background:var(--c-bg-soft);border-radius:8px;padding:8px;text-align:center;">
          <div class="text-xs text-muted">当前 rate</div>
          <div id="rate-rate-val" style="font-size:16px;font-weight:700;color:var(--c-accent-deep);font-family:var(--ff-mono);">0.0385%</div>
        </div>
        <div style="background:var(--c-bg-soft);border-radius:8px;padding:8px;text-align:center;">
          <div class="text-xs text-muted">预测趋势</div>
          <div id="rate-trend" style="font-size:16px;font-weight:700;color:var(--c-available);font-family:var(--ff-mono);">↗ 上升</div>
        </div>
      </div>
    </div>
  </div>

  <!-- AI Agent 实时活动流 -->
  <div class="card chart-card">
    <div class="card-head" style="border:none;padding:0 0 12px;">
      <div><div class="card-title">AI Agent 实时活动流</div><div class="card-sub">各Agent最近执行任务 · 自动刷新</div></div>
      <span class="tag tag-success tag-dot">运行中</span>
    </div>
    <div id="ai-activity-feed" style="max-height:280px;overflow-y:auto;">
      ${[
        { agent:'用户风控Agent', task:'U10086 批量注册检测 → 限制登录', time:'刚刚', level:'danger' },
        { agent:'商品审核Agent', task:'P5006 智能蓝牙耳机Pro3 → 通过(95%)', time:'12秒前', level:'success' },
        { agent:'B2B OCR核验Agent', task:'B2B20260824002 OCR提取完成 → 匹配度98%', time:'35秒前', level:'success' },
        { agent:'异常核销分析Agent', task:'M20004 核销率8.2% → 触发风控阈值', time:'1分钟前', level:'warning' },
        { agent:'商家风控Agent', task:'M20008 AI风险评分75 → 生成处罚建议', time:'2分钟前', level:'danger' },
        { agent:'地址核验Agent', task:'M20005 御品茶业 → 地址匹配通过', time:'3分钟前', level:'success' },
        { agent:'AI客服Agent', task:'用户咨询"LSC释放规则" → RAG检索回答', time:'4分钟前', level:'info' },
        { agent:'管理员助手Agent', task:'林总 NL2SQL查询 → 返回信用分<60商家列表', time:'6分钟前', level:'info' },
      ].map(a=>`
        <div style="display:flex;align-items:flex-start;gap:10px;padding:10px 0;border-bottom:1px solid var(--c-divider);">
          <div style="width:8px;height:8px;border-radius:50%;margin-top:5px;flex-shrink:0;background:${a.level==='danger'?'var(--c-danger)':a.level==='warning'?'var(--c-warning)':a.level==='success'?'var(--c-available)':'var(--c-info)'};${a.level==='danger'||a.level==='warning'?'box-shadow:0 0 6px currentColor;':''}"></div>
          <div style="flex:1;">
            <div style="font-size:13px;"><b style="color:var(--c-primary);">${a.agent}</b> <span style="color:var(--c-text-2);">${a.task}</span></div>
          </div>
          <div class="text-xs text-muted" style="flex-shrink:0;">${a.time}</div>
        </div>
      `).join('')}
    </div>
    <div style="text-align:center;padding:10px;font-size:11px;color:var(--c-text-3);">— 实时流由AI网关推送 · 仅展示最近20条 —</div>
  </div>
  `;
  setView(html);
  // ===== P0: AI 中心实时刷新 (清除旧定时器后重新创建) =====
  if (window._aiTimers) { window._aiTimers.forEach(t=>clearInterval(t)); window._aiTimers = []; }
  window._aiTimers = [];

  // P0-3: 活动流每10秒插入一条新记录 (slide-down 动画)
  const ACT_AGENTS = [
    { agent:'用户风控Agent', tasks:['可疑设备指纹检测 → 临时冻结','批量注册拦截 → 限制登录','同IP多号 → 触发二次验证','异常时段登录 → 邮件通知','异地登录 → 强制改密'] },
    { agent:'商品审核Agent', tasks:['图片AI鉴黄 → 拒绝','AI生成标题 → 人工复核','类目匹配 → 自动修正','防伪码校验 → 已通过','禁售关键词检测 → 拦截'] },
    { agent:'B2B OCR核验Agent', tasks:['凭证OCR提取完成 → 匹配97%','手写合同识别 → 待复核','发票核验 → 税号一致','印章真伪 → 已通过','合同一致性对比 → 差异3处'] },
    { agent:'异常核销分析Agent', tasks:['核销率0.3% → 告警','短时高并发核销 → 限流','凌晨批量核销 → 待人工','同款高频核销 → 触发熔断'] },
    { agent:'商家风控Agent', tasks:['风险评分82 → 生成处罚建议','流水突增50% → 核查','连续3日低核销 → 提醒','执照到期提醒 → 已推送'] },
    { agent:'地址核验Agent', tasks:['门店坐标比对 → 通过','经营地址搬迁 → 更新','虚拟地址检测 → 拒绝','地图POI匹配 → 一致'] },
    { agent:'AI客服Agent', tasks:['用户咨询"LSC释放规则" → 已回答','投诉工单分类 → 售后组','退款政策解读 → 已推送','常见问题Q&A → 推荐'] },
    { agent:'管理员助手Agent', tasks:['NL2SQL查询 → 信用分<60商家','运营周报摘要 → 已发送','参数变更审计 → 已归档','告警趋势分析 → 5项下降'] },
  ];
  const LEVEL_COLORS = { danger:'var(--c-danger)', warning:'var(--c-warning)', success:'var(--c-available)', info:'var(--c-info)' };
  function pushActivity() {
    const feed = document.getElementById('ai-activity-feed');
    if (!feed) return;
    const a = ACT_AGENTS[Math.floor(Math.random()*ACT_AGENTS.length)];
    const taskText = a.tasks[Math.floor(Math.random()*a.tasks.length)];
    const now = new Date();
    const time = '刚刚';
    const level = Math.random()<0.2 ? 'danger' : Math.random()<0.35 ? 'warning' : Math.random()<0.7 ? 'success' : 'info';
    const dotGlow = (level==='danger'||level==='warning') ? 'box-shadow:0 0 6px currentColor;' : '';
    const entry = document.createElement('div');
    entry.style.cssText = 'display:flex;align-items:flex-start;gap:10px;padding:10px 0;border-bottom:1px solid var(--c-divider);opacity:0;transform:translateY(-8px);transition:opacity 0.35s ease,transform 0.35s ease;';
    entry.innerHTML = `
      <div style="width:8px;height:8px;border-radius:50%;margin-top:5px;flex-shrink:0;background:${LEVEL_COLORS[level]};color:${LEVEL_COLORS[level]};${dotGlow}"></div>
      <div style="flex:1;"><div style="font-size:13px;"><b style="color:var(--c-primary);">${a.agent}</b> <span style="color:var(--c-text-2);">${taskText}</span></div></div>
      <div class="text-xs text-muted" style="flex-shrink:0;">${time}</div>`;
    feed.insertBefore(entry, feed.firstChild);
    // 只保留最近20条
    while (feed.children.length > 20) { feed.removeChild(feed.lastChild); }
    // 下一帧触发 slideDown / fadeIn 动画
    requestAnimationFrame(()=>{ requestAnimationFrame(()=>{ entry.style.opacity='1'; entry.style.transform='translateY(0)'; }); });
  }
  const tFeed = setInterval(pushActivity, 10000);
  window._aiTimers.push(tFeed);

  // P0-4: 释放速率数据流每3秒追加一个新采样点,旧点左滑 (保持24小时的滚动窗口)
  // 我们为 k / rate 两个系列维护可追加的全局状态
  if (!window._rateSeries) {
    // 初始化 24 个数据点
    window._rateLabels = Array.from({length:24},(_,i)=>(i+1)+':00');
    window._rateSeries = {
      k: Array.from({length:24},(_,i)=>0.0060+Math.sin(i/4)*0.0008+(Math.random()-0.5)*0.0004),
      rate: Array.from({length:24},(_,i)=>0.0035+Math.sin(i/4+1)*0.0005+(Math.random()-0.5)*0.00025),
    };
  }
  function redrawRateChart() {
    const chartEl = document.getElementById('rate-realtime-chart');
    const kLabel = document.getElementById('rate-k-val');
    const rateLabel = document.getElementById('rate-rate-val');
    const trendLabel = document.getElementById('rate-trend');
    if (!chartEl) return;
    const { w=560, h=220, labels, series } = {
      w: 560, h: 220,
      labels: window._rateLabels,
      series: [
        { data: window._rateSeries.k, color:'var(--c-locked)', name:'k值(×100)', area:true, width:2.5 },
        { data: window._rateSeries.rate, color:'var(--c-accent)', name:'rate(×100)', dash:true },
      ],
    };
    chartEl.innerHTML = lineChart({ w, h, labels, series }) +
      '<div id="rate-realtime-legend" style="margin-top:8px;">'+
      series.map(s=>`<span class="tag tag-dot" style="background:${s.color};color:#fff;font-size:10px;">${s.name}</span>`).join(' ')+
      '</div>';
    // 更新指标卡
    const lastK = window._rateSeries.k[window._rateSeries.k.length-1];
    const lastRate = window._rateSeries.rate[window._rateSeries.rate.length-1];
    const prevK = window._rateSeries.k[window._rateSeries.k.length-2];
    const prevRate = window._rateSeries.rate[window._rateSeries.rate.length-2];
    if (kLabel) kLabel.textContent = (lastK*100).toFixed(4)+'%';
    if (rateLabel) rateLabel.textContent = (lastRate*100).toFixed(4)+'%';
    if (trendLabel) {
      const totalUp = (lastRate>prevRate) && (lastK>prevK);
      trendLabel.textContent = totalUp ? '↗ 上升' : (lastRate<prevRate?'↘ 下降':'→ 平稳');
      trendLabel.style.color = totalUp ? 'var(--c-available)' : (lastRate<prevRate?'var(--c-danger)':'var(--c-text-2)');
    }
  }
  function appendRatePoint() {
    if (!window._rateSeries) return;
    // 左滑:弹出最早的标签和数据
    window._rateLabels.shift();
    const last = window._rateLabels[window._rateLabels.length-1];
    const lastHour = parseInt(last, 10) || 0;
    window._rateLabels.push(((lastHour%24)+1)+':00');
    const kArr = window._rateSeries.k;
    const rArr = window._rateSeries.rate;
    const newK = Math.max(0.0045, Math.min(0.0085, kArr[kArr.length-1] + (Math.random()-0.5)*0.0004));
    const newR = Math.max(0.0025, Math.min(0.0055, rArr[rArr.length-1] + (Math.random()-0.5)*0.00025));
    kArr.shift(); rArr.shift();
    kArr.push(newK); rArr.push(newR);
    redrawRateChart();
  }
  // 暴露到 window,便于外部验证
  window.pushActivity = pushActivity;
  window.redrawRateChart = redrawRateChart;
  window.appendRatePoint = appendRatePoint;
  // 初次绘制(为 ID 锚点填充真实内容)
  redrawRateChart();
  const tRate = setInterval(appendRatePoint, 3000);
  window._aiTimers.push(tRate);
}

/* ============== 工具 ============== */
function setView(html) {
  const v = document.getElementById('view');
  v.innerHTML = html;
  // 重新渲染图标
  v.querySelectorAll('.icon[data-i]').forEach(el=>{
    const key = el.getAttribute('data-i');
    if (ICONS[key]) el.innerHTML = ICONS[key];
  });
  v.querySelectorAll('.icon:not([data-i])').forEach(el=>{
    if (!el.innerHTML.trim()) {
      const parent = el.closest('[data-i]') || el.parentElement;
    }
  });
}

/* ============== 通知中心数据 ============== */
const NOTIFS = [
  { id:'N01', title:'AI风控预警', desc:'用户U10086 触发批量注册风控，已自动限制登录', time:'8分钟前', type:'danger', read:false },
  { id:'N02', title:'释放任务完成', desc:'今日释放任务执行成功，86批次零误差，链上存证已完成', time:'5小时前', type:'success', read:false },
  { id:'N03', title:'商家处罚待审批', desc:'M20004 鼎盛物流仓储 核销限额处罚待双人审批', time:'2小时前', type:'warning', read:false },
  { id:'N04', title:'B2B订单AI核验', desc:'B2B20260824003 AI判定可疑(匹配度42%)，需人工复核', time:'3小时前', type:'warning', read:true },
  { id:'N05', title:'对账报告生成', desc:'2026-08-26 对账完成，平台流水与机构流水一致', time:'1天前', type:'info', read:true },
];
const notifColorMap = { danger:'var(--c-danger)', success:'var(--c-success)', warning:'var(--c-warning)', info:'var(--c-info)' };

function renderNotifList() {
  document.getElementById('notif-list').innerHTML = NOTIFS.map(n=>`
    <div class="notif-item ${n.read?'read':'unread'}">
      <div class="notif-dot" style="background:${n.read?'var(--c-border)':notifColorMap[n.type]};${n.read?'':'box-shadow:0 0 6px '+notifColorMap[n.type]}"></div>
      <div style="flex:1;">
        <div class="notif-title">${n.title}</div>
        <div class="notif-desc">${n.desc}</div>
        <div class="notif-time">${n.time}</div>
      </div>
    </div>
  `).join('');
}
renderNotifList();
document.getElementById('notif-toggle').addEventListener('click', e=>{
  e.stopPropagation();
  document.getElementById('notif-panel').classList.toggle('hidden');
});
document.addEventListener('click', e=>{
  const panel = document.getElementById('notif-panel');
  if (!panel.classList.contains('hidden') && !e.target.closest('.notif-wrap')) panel.classList.add('hidden');
});

/* ============== 全局弹窗系统 ============== */
function openModal(opts) {
  // opts: { title, body, footer, danger?, wide?, onClose? }
  closeModal();
  const mask = document.createElement('div');
  mask.className = 'modal-mask' + (opts.danger ? ' danger-mask' : '');
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
  mask.addEventListener('click', e=>{ if (e.target===mask || e.target.id==='gm-close') { closeModal(); opts.onClose && opts.onClose(); } });
}
function closeModal() {
  const m = document.getElementById('global-modal');
  if (m) m.remove();
}

/* 双人审批弹窗(可复用) */
function dualApprovalModal(opts) {
  // opts: { title, summary (html), payload (描述), onApprove() }
  let sig1='', sig2='';
  const body = `
    ${opts.summary || ''}
    <div class="alert alert-warning mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="lock"></span>此操作需双人审批签名验证。verifyDualApproval(sig1, sig2, payload) 要求两位管理员<b>不同</b>且各自签名通过。所有签名操作记录审计并上链存证。</div>
    <div class="dual-sign-grid mt-3">
      <div class="sign-box" id="sig1-box">
        <div class="sign-role">第一管理员签名</div>
        <div class="sign-input"><input class="input" id="sig1-input" placeholder="输入管理员账号" oninput="updateSig('sig1', this.value)"></div>
      </div>
      <div class="sign-box" id="sig2-box">
        <div class="sign-role">第二管理员签名</div>
        <div class="sign-input"><input class="input" id="sig2-input" placeholder="输入管理员账号" oninput="updateSig('sig2', this.value)"></div>
      </div>
    </div>
    <div class="text-xs text-muted mt-3" id="dual-status">等待两位管理员输入账号...</div>
  `;
  const footer = `
    <button class="btn btn-outline btn-sm" onclick="closeModal()">取消</button>
    <button class="btn ${opts.danger?'btn-danger':'btn-primary'} btn-sm" id="dual-confirm" disabled>验证并执行</button>
  `;
  window._dualSig = { s1:'', s2:'', onApprove: opts.onApprove };
  window.updateSig = function(key, val) {
    window._dualSig[key] = val;
    const box1 = document.getElementById('sig1-box');
    const box2 = document.getElementById('sig2-box');
    box1.classList.toggle('verified', window._dualSig.s1.length>=2);
    box2.classList.toggle('verified', window._dualSig.s2.length>=2);
    box1.classList.toggle('active', window._dualSig.s1.length>0 && window._dualSig.s1.length<2);
    box2.classList.toggle('active', window._dualSig.s2.length>0 && window._dualSig.s2.length<2);
    const bothFilled = window._dualSig.s1.length>=2 && window._dualSig.s2.length>=2;
    const same = window._dualSig.s1 === window._dualSig.s2;
    const status = document.getElementById('dual-status');
    const btn = document.getElementById('dual-confirm');
    if (!bothFilled) { status.textContent='等待两位管理员输入账号...'; status.style.color='var(--c-text-3)'; btn.disabled=true; }
    else if (same) { status.textContent='✗ 两位管理员账号不能相同'; status.style.color='var(--c-danger)'; btn.disabled=true; }
    else { status.textContent='✓ 双人签名验证通过，可执行操作'; status.style.color='var(--c-available)'; btn.disabled=false; }
  };
  openModal({
    title: opts.title || '双人审批',
    body, footer,
    danger: opts.danger,
    onClose: ()=>{ delete window._dualSig; delete window.updateSig; }
  });
  document.getElementById('dual-confirm').addEventListener('click', ()=>{
    if (window._dualSig.s1.length>=2 && window._dualSig.s2.length>=2 && window._dualSig.s1!==window._dualSig.s2) {
      closeModal();
      opts.onApprove && opts.onApprove();
    }
  });
}

/* 成功/结果提示弹窗 */
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

/* 确认弹窗 */
function confirmModal(title, body, onConfirm, opts={}) {
  openModal({
    title,
    body: `<div style="padding:8px 4px;">${body}</div>`,
    footer: `
      <button class="btn btn-outline btn-sm" onclick="closeModal()">取消</button>
      <button class="btn ${opts.danger?'btn-danger':'btn-primary'} btn-sm" id="confirm-yes">${opts.btnText||'确认'}</button>
    `,
    danger: opts.danger
  });
  document.getElementById('confirm-yes').addEventListener('click', ()=>{ closeModal(); onConfirm && onConfirm(); });
}

/* ============== 业务弹窗 ============== */

/* 商家详情/资质查看 */
function showMerchantDetail(mid) {
  const m = MOCK.merchants.find(x=>x.id===mid);
  if (!m) return;
  const addrStatus = m.aiAddr==='pass'?'<span class="tag tag-success tag-dot">核验通过</span>':m.aiAddr==='suspect'?'<span class="tag tag-warning tag-dot">核验可疑</span>':'<span class="tag tag-danger tag-dot">核验失败</span>';
  const body = `
    <div style="display:flex;align-items:center;gap:14px;margin-bottom:20px;">
      <div class="avatar avatar-lg avatar-gold">${m.name.charAt(0)}</div>
      <div><div style="font-size:18px;font-weight:700;">${m.name}</div>
      <div class="text-sm text-muted mt-1">${m.id} · ${m.type} · ${m.status==='normal'?'正常':m.status==='warning'?'预警':'处罚中'}</div></div>
    </div>
    <div class="detail-grid">
      <div class="detail-field"><div class="detail-label">信用分</div><div class="detail-value" style="color:${m.credit>=80?'var(--c-available)':m.credit>=60?'var(--c-warning)':'var(--c-danger)'};">${m.credit} / 100</div></div>
      <div class="detail-field"><div class="detail-label">AI风险评分</div><div class="detail-value">${m.aiRisk} <span class="text-xs text-muted">(${m.aiRisk>60?'高风险':m.aiRisk>30?'中风险':'低风险'})</span></div></div>
      <div class="detail-field"><div class="detail-label">月营业额</div><div class="detail-value mono">${LSC.fmtMoney(m.monthRevenue)}</div></div>
      <div class="detail-field"><div class="detail-label">核销档位</div><div class="detail-value">${m.nhLevel}档 · 限额 ${LSC.fmtNum(m.nhLimitDaily)}/日</div></div>
      <div class="detail-field"><div class="detail-label">营业执照号</div><div class="detail-value mono">91310115MA1K${m.id.slice(-4)}X</div></div>
      <div class="detail-field"><div class="detail-label">监管账户号</div><div class="detail-value mono">JG-${m.id}-0001</div></div>
      <div class="detail-field" style="grid-column:1/3;"><div class="detail-label">经营地址</div><div class="detail-value">${m.addr}</div></div>
      <div class="detail-field"><div class="detail-label">AI地址核验</div><div class="detail-value">${addrStatus}</div></div>
      <div class="detail-field"><div class="detail-label">主账户号</div><div class="detail-value mono">MA-${m.id}-0001</div></div>
    </div>
    <div class="divider"></div>
    <div class="text-xs text-muted">分片存储: lsc_db_${parseInt(mid.replace(/\D/g,''))%8} · users表分片键 user_id mod 32</div>
  `;
  openModal({
    title: '商家资质详情',
    body,
    footer: `<button class="btn btn-outline btn-sm" onclick="closeModal()">关闭</button>`
  });
}

/* 商家核销额度调整(双人审批) */
function showAdjustLimit(mid) {
  const m = MOCK.merchants.find(x=>x.id===mid);
  if (!m) return;
  dualApprovalModal({
    title: '调整核销额度 · 双人审批',
    danger: false,
    summary: `<div class="detail-grid">
      <div class="detail-field"><div class="detail-label">商家</div><div class="detail-value">${m.name} (${m.id})</div></div>
      <div class="detail-field"><div class="detail-label">当前核销档位</div><div class="detail-value">${m.nhLevel}档</div></div>
      <div class="detail-field"><div class="detail-label">当前日限额</div><div class="detail-value mono">¥${LSC.fmtNum(m.nhLimitDaily)}</div></div>
      <div class="detail-field"><div class="detail-label">调整后日限额</div><div class="detail-value">
        <select class="select" id="new-limit" style="padding:6px 10px;">
          <option value="10000">¥10,000 (C档)</option>
          <option value="30000">¥30,000 (B档)</option>
          <option value="50000" selected>¥50,000 (A档)</option>
        </select>
      </div></div>
    </div>`,
    onApprove: ()=>{
      const newLimit = document.getElementById('new-limit') ? document.getElementById('new-limit').value : m.nhLimitDaily;
      resultModal('核销额度调整成功', `商家 ${m.name} (${m.id}) 的日核销限额已由 ¥${LSC.fmtNum(m.nhLimitDaily)} 调整为 <b>¥${LSC.fmtNum(newLimit)}</b>。<br>操作已记录审计日志并上链存证。`);
    }
  });
}

/* 商家处罚(双人审批) */
function showPenalty(mid) {
  const m = MOCK.merchants.find(x=>x.id===mid);
  if (!m) return;
  dualApprovalModal({
    title: '执行商家处罚 · 双人审批',
    danger: true,
    summary: `<div class="detail-grid">
      <div class="detail-field"><div class="detail-label">商家</div><div class="detail-value">${m.name} (${m.id})</div></div>
      <div class="detail-field"><div class="detail-label">当前信用分</div><div class="detail-value" style="color:${m.credit>=60?'var(--c-warning)':'var(--c-danger)'};">${m.credit}</div></div>
      <div class="detail-field"><div class="detail-label">违规类型</div><div class="detail-value">
        <select class="select" id="vio-type" style="padding:6px 10px;">
          <option>虚假地址</option><option>高核销率异常</option><option>信用异常</option><option>商品违规</option>
        </select>
      </div></div>
      <div class="detail-field"><div class="detail-label">扣减信用分</div><div class="detail-value">
        <select class="select" id="deduct-score" style="padding:6px 10px;">
          <option value="5">-5 分</option><option value="10">-10 分</option><option value="15" selected>-15 分</option><option value="20">-20 分</option>
        </select>
      </div></div>
      <div class="detail-field" style="grid-column:1/3;"><div class="detail-label">处罚措施</div>
        <div class="detail-value"><select class="select" id="measure" style="padding:6px 10px;">
          <option>暂停核销30天</option><option>核销限额降至1万/日</option><option>加强商品审核</option><option>冻结账户</option>
        </select></div>
      </div>
    </div>`,
    onApprove: ()=>{
      resultModal('处罚执行成功', `已对商家 ${m.name} (${m.id}) 执行处罚：<br>• 扣减信用分 -15 分<br>• 处罚措施: 暂停核销30天<br><br>处罚记录已写入 merchant_violations 表，审计日志已上链存证。`, 'warning');
    }
  });
}

/* 商品审核详情 */
function showProductDetail(pid) {
  const p = MOCK.products.find(x=>x.id===pid);
  if (!p) return;
  const statusMap = {
    ai_pass: { tag:'tag-success', label:'AI通过' },
    ai_suspect: { tag:'tag-warning', label:'AI存疑' },
    ai_reject: { tag:'tag-danger', label:'AI驳回' },
    manual_review: { tag:'tag-info', label:'待人工复核' },
  };
  const s = statusMap[p.status];
  const needAction = p.status==='manual_review' || p.status==='ai_suspect';
  const body = `
    <div class="grid-2" style="grid-template-columns:200px 1fr;gap:16px;">
      <div style="height:160px;border-radius:var(--r-md);background:linear-gradient(135deg,var(--c-primary-tint),var(--c-bg-soft));display:flex;align-items:center;justify-content:center;">
        <span class="icon icon-xl" data-i="product" style="width:56px;height:56px;color:var(--c-primary);opacity:0.5;"></span>
      </div>
      <div>
        <div style="font-size:16px;font-weight:700;">${p.name}</div>
        <div class="text-sm text-muted mt-1">${p.merchant} · ${p.id}</div>
        <div class="flex gap-2 mt-2 flex-wrap">
          <span class="tag ${s.tag} tag-dot">${s.label}</span>
          ${p.video==='ok'?'<span class="tag tag-info">含视频</span>':''}
          ${p.video==='reject'?'<span class="tag tag-danger">视频违规</span>':''}
        </div>
        <div class="mt-3"><span class="text-xs text-muted">售价</span> <b style="font-family:var(--ff-mono);color:var(--c-accent-deep);">${LSC.fmtMoney(p.price)}</b> <span class="text-xs text-available">= ${LSC.fmtNum(p.price)} LSC (1:1)</span></div>
      </div>
    </div>
    <div class="divider"></div>
    <div class="detail-grid">
      <div class="detail-field"><div class="detail-label">库存</div><div class="detail-value mono">${p.stock}</div></div>
      <div class="detail-field"><div class="detail-label">AI置信度</div><div class="detail-value" style="color:${p.aiScore>0.8?'var(--c-available)':p.aiScore>0.5?'var(--c-warning)':'var(--c-danger)'};">${(p.aiScore*100).toFixed(0)}%</div></div>
    </div>
    <div class="mt-3"><div class="detail-label" style="margin-bottom:6px;">AI审核标签</div>
      <div class="flex gap-2 flex-wrap">${p.aiTags.map(t=>`<span class="tag ${t.includes('违规')||t.includes('假冒')||t.includes('疑似')?'tag-danger':t.includes('需')?'tag-warning':'tag-success'}">${t}</span>`).join('')}</div>
    </div>
    <div class="ai-panel mt-4">
      <span class="ai-tag"><span class="ai-dot"></span>商品审核 Agent</span>
      <div class="text-sm mt-2" style="line-height:1.7;">图像审核(ResNet) + 视频审核(多模态) + 文本分类(BERT) 三模型并行。强制校验人民币价格与LSC价格1:1对应。所有审核结果标注"AI生成"。</div>
    </div>
  `;
  openModal({
    title: '商品审核详情',
    body,
    footer: `
      <button class="btn btn-outline btn-sm" onclick="closeModal()">关闭</button>
      ${needAction?`<button class="btn btn-primary btn-sm" onclick="closeModal();resultModal('审核通过','商品 ${p.name} 已通过审核并上架。')">通过</button><button class="btn btn-danger btn-sm" onclick="closeModal();resultModal('已驳回','商品 ${p.name} 已驳回。驳回原因: 涉嫌违规内容。','danger')">驳回</button>`:''}
    `
  });
}

/* B2B订单详情 */
function showB2BDetail(oid) {
  const o = MOCK.b2bOrders.find(x=>x.id===oid);
  if (!o) return;
  const verifyMap = {
    0: { tag:'tag-info', label:'待核验' },
    1: { tag:'tag-success', label:'AI判定真实' },
    2: { tag:'tag-warning', label:'AI判定可疑' },
    3: { tag:'tag-success', label:'人工确认真实' },
    4: { tag:'tag-danger', label:'人工确认虚假' },
  };
  const v = verifyMap[o.aiVerify];
  const body = `
    <div class="detail-grid">
      <div class="detail-field"><div class="detail-label">订单号</div><div class="detail-value mono">${o.id}</div></div>
      <div class="detail-field"><div class="detail-label">合同编号</div><div class="detail-value mono">${o.contract}</div></div>
      <div class="detail-field"><div class="detail-label">发起方</div><div class="detail-value">${o.from}</div></div>
      <div class="detail-field"><div class="detail-label">对手方</div><div class="detail-value">${o.to}</div></div>
      <div class="detail-field" style="grid-column:1/3;"><div class="detail-label">交易描述</div><div class="detail-value">${o.desc}</div></div>
      <div class="detail-field"><div class="detail-label">人民币金额</div><div class="detail-value mono">${LSC.fmtMoney(o.rmb)}</div></div>
      <div class="detail-field"><div class="detail-label">LSC金额 (1:1)</div><div class="detail-value mono" style="color:var(--c-available);">${LSC.fmtNum(o.lsc)} LSC</div></div>
      <div class="detail-field"><div class="detail-label">AI核验结果</div><div class="detail-value"><span class="tag ${v.tag} tag-dot">${v.label}</span> 匹配度 ${(o.aiMatch*100).toFixed(0)}%</div></div>
      <div class="detail-field"><div class="detail-label">当前状态</div><div class="detail-value">${o.status==='completed'?'已完成':o.status==='confirmed'?'已确认流转':o.status==='pending'?'对手方待确认':o.status==='await_verify'?'待AI核验':'已驳回'}</div></div>
    </div>
    <div class="divider"></div>
    <div class="detail-label" style="margin-bottom:8px;">贸易凭证 (AI已核验)</div>
    <div class="voucher-grid">
      <div class="voucher-thumb"><span class="icon icon-lg" data-i="doc"></span><div class="ai-tag-mini">合同 ✓</div></div>
      <div class="voucher-thumb"><span class="icon icon-lg" data-i="doc"></span><div class="ai-tag-mini">发票 ✓</div></div>
      <div class="voucher-thumb"><span class="icon icon-lg" data-i="doc"></span><div class="ai-tag-mini">${o.aiVerify===2?'可疑':'物流 ✓'}</div></div>
    </div>
    <div class="ai-panel mt-4">
      <span class="ai-tag"><span class="ai-dot"></span>B2B OCR 核验 Agent</span>
      <div class="text-sm mt-2" style="line-height:1.7;">OCR提取合同编号/金额/双方信息，与订单字段自动匹配。多模态模型识别凭证真伪。强制校验 LSC数量与人民币金额1:1对应。</div>
    </div>
  `;
  openModal({
    title: 'B2B订单详情',
    body,
    footer: `<button class="btn btn-outline btn-sm" onclick="closeModal()">关闭</button>`
  });
}

/* 释放参数修改(双人审批) */
function showParamEdit(paramKey) {
  const params = {
    k_min: { label:'k_min 健康核销率下限', current:'0.50%', desc:'低于此值启用 rate_max (0.05%)', options:['0.45%','0.50%','0.55%'] },
    k_max: { label:'k_max 健康核销率上限', current:'1.0%', desc:'高于此值启用 rate_min (0.03%)', options:['0.9%','1.0%','1.1%'] },
    alpha: { label:'alpha 动态加权平滑系数', current:'0.05', desc:'用于k值历史加权平滑', options:['0.03','0.05','0.08'] },
  };
  const p = params[paramKey];
  if (!p) return;
  dualApprovalModal({
    title: '修改释放算法参数 · 双人审批',
    summary: `<div class="detail-grid">
      <div class="detail-field" style="grid-column:1/3;"><div class="detail-label">参数</div><div class="detail-value">${p.label}</div></div>
      <div class="detail-field"><div class="detail-label">说明</div><div class="detail-value text-sm">${p.desc}</div></div>
      <div class="detail-field"><div class="detail-label">调整后值</div><div class="detail-value">
        <select class="select" id="new-param" style="padding:6px 10px;">
          ${p.options.map(o=>`<option ${o===p.current?'selected':''}>${o}</option>`).join('')}
        </select>
      </div></div>
    </div>
    <div class="value-diff">
      <span class="text-sm text-muted">原值</span><span class="old-val">${p.current}</span>
      <span class="arrow"><span class="icon icon-sm" data-i="arrowRight"></span></span>
      <span class="text-sm text-muted">新值</span><span class="new-val" id="param-new-val">${p.current}</span>
    </div>`,
    onApprove: ()=>{
      const newVal = document.getElementById('new-param').value;
      resultModal('参数修改成功', `释放算法参数 ${p.label} 已由 ${p.current} 修改为 <b>${newVal}</b>。<br>修改记录已写入 release_config 表，审计日志已上链存证。`);
    }
  });
  // 实时更新对比
  setTimeout(()=>{
    const sel = document.getElementById('new-param');
    if (sel) sel.addEventListener('change', ()=>{
      document.getElementById('param-new-val').textContent = sel.value;
    });
  }, 50);
}

/* AI仿真推演结果 */
function showSimulation() {
  const body = `
    <div class="ai-panel mb-4">
      <span class="ai-tag"><span class="ai-dot"></span>参数仿真 Agent</span>
      <div class="ai-panel-title mt-2"><span class="icon icon-sm" data-i="ai"></span>蒙特卡洛模拟 · 30天推演</div>
      <div class="text-sm text-muted mt-1">输入: k_min 0.45% / k_max 0.95% / alpha 0.05 · 10000次模拟</div>
    </div>
    <div class="sim-result">
      <div class="sim-row"><span class="text-muted">预测7天后核销率 k</span><b style="color:var(--c-accent-deep);">0.82% (↑0.10%)</b></div>
      <div class="sim-row"><span class="text-muted">预测30天后核销率 k</span><b style="color:var(--c-accent-deep);">0.91% (↑0.19%)</b></div>
      <div class="sim-row"><span class="text-muted">预测释放速率 rate</span><b style="color:var(--c-primary);">0.0295% (↓0.009%)</b></div>
      <div class="sim-row"><span class="text-muted">30天累计释放总量</span><b style="color:var(--c-available);">11,856,200 LSC</b></div>
      <div class="sim-row"><span class="text-muted">系统健康度评分</span><b style="color:var(--c-available);">94分 · 优秀</b></div>
      <div class="sim-row"><span class="text-muted">风险等级</span><b style="color:var(--c-available);">低 · 无需干预</b></div>
    </div>
    <div class="chart-area mt-4" style="height:200px;">${lineChart({
      w:560, h:200,
      labels: Array.from({length:30},(_,i)=>'D'+(i+1)),
      series: [
        { data: Array.from({length:30},(_,i)=>0.0072+i*0.0006+Math.sin(i/4)*0.0002), color:'var(--c-accent)', name:'预测k值', area:true }
      ]
    })}</div>
    <div class="alert alert-info mt-4" style="font-size:12px;"><span class="icon icon-sm" data-i="ai"></span>AI仿真结果仅供参考，不修改业务数据。所有高级决策AI仅输出建议，不具备写权限。</div>
  `;
  openModal({
    title: 'AI参数仿真推演',
    body,
    footer: `<button class="btn btn-outline btn-sm" onclick="closeModal()">关闭</button>`
  });
}

/* 释放熔断(双人审批) */
function showCircuitBreaker() {
  dualApprovalModal({
    title: '人工熔断 · 双人审批',
    danger: true,
    summary: `<div class="alert alert-danger"><span class="icon icon-sm" data-i="warning"></span><div><b>高危操作</b>：熔断将立即暂停当日释放任务，所有未释放LSC将延迟处理。此操作仅限极端情况使用。</div></div>
    <div class="detail-grid mt-3">
      <div class="detail-field"><div class="detail-label">当前释放速率</div><div class="detail-value mono">0.0385%‱</div></div>
      <div class="detail-field"><div class="detail-label">当前核销率 k</div><div class="detail-value mono">0.72%</div></div>
      <div class="detail-field"><div class="detail-label">今日已释放</div><div class="detail-value mono">${LSC.fmtNum(MOCK.dashboard.todayRelease)} LSC</div></div>
      <div class="detail-field"><div class="detail-label">待释放</div><div class="detail-value mono" style="color:var(--c-warning);">0 LSC</div></div>
    </div>`,
    onApprove: ()=>{
      resultModal('熔断已执行', '当日释放任务已暂停。<br>熔断操作已记录审计日志并上链存证。<br>已通知两名超级管理员。', 'warning');
    }
  });
}

/* 撤销处罚(双人审批) */
function showRevokePenalty(vid) {
  dualApprovalModal({
    title: '撤销商家处罚 · 双人审批',
    danger: false,
    summary: `<div class="detail-grid">
      <div class="detail-field"><div class="detail-label">违规记录ID</div><div class="detail-value mono">${vid}</div></div>
      <div class="detail-field"><div class="detail-label">操作类型</div><div class="detail-value">撤销处罚</div></div>
    </div>
    <div class="alert alert-warning mt-3" style="font-size:12px;">撤销处罚将恢复商家信用分与核销权限。请确认处罚确实为误判。</div>`,
    onApprove: ()=>{
      resultModal('处罚已撤销', `违规记录 ${vid} 的处罚已撤销。<br>商家信用分与核销权限已恢复。<br>撤销操作已记录审计日志并上链存证。`);
    }
  });
}

/* ============== AI助手浮窗 ============== */
document.getElementById('ai-toggle').addEventListener('click', ()=>{
  document.getElementById('ai-mask').classList.remove('hidden');
});
document.getElementById('ai-close').addEventListener('click', ()=>{
  document.getElementById('ai-mask').classList.add('hidden');
});
document.getElementById('ai-mask').addEventListener('click', e=>{
  if (e.target.id==='ai-mask') e.target.classList.add('hidden');
});

/* ============== 启动 ============== */
renderDashboard();
