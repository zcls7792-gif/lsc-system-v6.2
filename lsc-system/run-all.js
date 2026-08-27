#!/usr/bin/env node
/**
 * 全流程一键执行器
 * 顺序:
 *   1) node verify_p0.js           - 4 应用 22 断言 功能回归基线
 *   2) node test_p0_chart_logic.js - 8 条 BUG 回归 TAP 用例
 *   3) npm run coverage            - c8 + coverage_runner 生成覆盖率 (html/text/lcov)
 *   4) node coverage_report.js     - 生成"覆盖率报告_P0-图表逻辑_日期.html"总览
 *
 * 每一步都独立子进程,c8 只能对第 3 步 npm run coverage 生效,不影响 1/2 两步。
 * 输出:
 *   - 控制台彩色/结构化 汇总表
 *   - 全部成功时 exit 0, 任一步失败 exit 非 0
 */
const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const ROOT = __dirname;
const GREEN = '\x1b[32m', RED='\x1b[31m', YELLOW='\x1b[33m', CYAN='\x1b[36m', RESET='\x1b[0m', BOLD='\x1b[1m', DIM='\x1b[2m';
const hr = (n=80,w='─')=> w.repeat(n);

function runStep(label, cmd, args, opts={}) {
  const t0 = Date.now();
  console.log(`\n${BOLD}${CYAN}▶▶ STEP ${label}${RESET}${DIM}  ${cmd} ${args.join(' ')}${RESET}\n${hr(60,'·')}`);
  const res = spawnSync(cmd, args, { cwd: ROOT, encoding:'utf8', stdio:['ignore','inherit','inherit'], shell: process.platform==='win32' || opts.useShell });
  const dt = ((Date.now()-t0)/1000).toFixed(2)+'s';
  const ok = res.status === 0 && (res.error == null);
  console.log(`${hr(60,'·')}\n${ok?GREEN+'✔ PASS':RED+'✗ FAIL'}${RESET}  ${label}  ${BOLD}exit=${res.status}${RESET}  ${DIM}elapsed=${dt}${RESET}`);
  return { label, ok, status: res.status, dt };
}

// 最后打印一张 ASCI 汇总表
function printReport(rows, extraLines) {
  const sep = '+---+---------------------------------------------------------+--------+---------+';
  console.log('\n'+sep);
  console.log('| # | 步骤                                                    | 结果  | 耗时    |');
  console.log(sep);
  rows.forEach((r,i)=>{
    const mark = r.ok ? `${GREEN}✔${RESET}` : `${RED}✗${RESET}`;
    const statusCell = r.ok ? `${GREEN}PASS${RESET} ` : `${RED}FAIL${RESET}`;
    const label = (r.label.length > 55 ? r.label.slice(0,52)+'...' : r.label.padEnd(55));
    console.log(`| ${String(i+1).padStart(2)}| ${label} | ${statusCell} | ${r.dt.padStart(7)} |`);
  });
  console.log(sep);
  if (extraLines) extraLines.forEach(l => console.log('  '+l));
}

function readCoverageSummary() {
  try {
    const summaryPath = path.join(ROOT, 'coverage', 'coverage-summary.json');
    if (fs.existsSync(summaryPath)) {
      const s = JSON.parse(fs.readFileSync(summaryPath,'utf8'));
      const t = s.total || {};
      return {
        stmts: (t.statements && t.statements.pct),
        branch: (t.branches && t.branches.pct),
        funcs: (t.functions && t.functions.pct),
        lines: (t.lines && t.lines.pct),
      };
    }
  } catch(_) {}
  // 回退:从 lcov.info 手算
  try {
    const lcov = fs.readFileSync(path.join(ROOT,'coverage','lcov.info'),'utf8');
    let lf=0,lh=0,bf=0,bh=0,ff=0,fh=0;
    for (const line of lcov.split(/\r?\n/)) {
      if (line.startsWith('LF:')) lf+=+line.slice(3);
      if (line.startsWith('LH:')) lh+=+line.slice(3);
      if (line.startsWith('BRF:')) bf+=+line.slice(4);
      if (line.startsWith('BRH:')) bh+=+line.slice(4);
      if (line.startsWith('FNF:')) ff+=+line.slice(4);
      if (line.startsWith('FNH:')) fh+=+line.slice(4);
    }
    const p = (h,f)=> (f? Math.round(10000*h/f)/100 : 100);
    return { stmts:p(lh,lf), lines:p(lh,lf), branch:p(bh,bf), funcs:p(fh,ff) };
  } catch(_) { return null; }
}

const steps = [
  runStep('1. 回归基线 verify_p0 (4 应用 × 22 断言)',          'node', ['verify_p0.js']),
  runStep('2. TAP BUG 回归 test_p0_chart_logic (8 用例)',       'node', ['test_p0_chart_logic.js']),
  runStep('3. c8 覆盖率采集 + coverage_runner (28 断言)',       'npm',  ['run','coverage'], { useShell: true }),
  runStep('4. 覆盖率总览 HTML 报告 coverage_report.js',         'node', ['coverage_report.js']),
];

const allOk = steps.every(s => s.ok);
const cov = readCoverageSummary();
const extra = [];
extra.push(`${BOLD}最终状态:${RESET} ${allOk ? GREEN+'全流程通过 ✅'+RESET : RED+'存在失败步骤 ❌'+RESET}`);
if (cov) {
  extra.push(`${BOLD}覆盖率汇总:${RESET} 语句 ${cov.stmts?.toFixed(2)}%  分支 ${YELLOW+cov.branch?.toFixed(2)+'%'+RESET}  函数 ${cov.funcs?.toFixed(2)}%  行 ${cov.lines?.toFixed(2)}%`);
  if (cov.branch < 80) extra.push(`${RED}⚠ 分支覆盖率低于 80% (目标 80)${RESET}`);
}
extra.push('报告: 覆盖率报告_P0-图表逻辑_20260827.html  /  coverage/index.html (逐行)');
printReport(steps, extra);
process.exit(allOk ? 0 : 1);
