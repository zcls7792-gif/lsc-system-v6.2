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

// CI 环境下: logs/ 独立落盘,并按 $LSC_REPORT_SUBDIR(=CI_COMMIT_SHORT_SHA)归档 c8
const IS_CI = process.env.GITLAB_CI === 'true' || process.env.CI === 'true';
const LOG_DIR = path.join(ROOT, 'logs');
const CI_SUBDIR = process.env.LSC_REPORT_SUBDIR || process.env.CI_COMMIT_SHORT_SHA || null;
if (IS_CI) {
  fs.mkdirSync(LOG_DIR, { recursive: true });
  if (CI_SUBDIR) {
    const dir = path.join(ROOT, 'coverage', CI_SUBDIR);
    fs.mkdirSync(dir, { recursive: true });
    // 给 npm run coverage 注入 c8 --report-dir (再覆盖一次 package.json 的默认 coverage/)
    // 方式:通过环境变量读给后续 shell
    process.env.C8_REPORT_DIR = dir;
    console.log(`${DIM}[CI] 覆盖率归档目录: coverage/${CI_SUBDIR}/${RESET}`);
  }
}

// 小工具:把子进程输出同步捕获后"同时写终端 + 写 logs/",不依赖 bash/PIPESTATUS
// (alpine /bin/sh = ash,不认 PIPESTATUS,会 Bad substitution;GitLab 的 shell executor 默认 bash,
// 但 node:alpine 镜像没有 bash,为两套环境都稳,用 Node 原生 pipe+捕获最稳)
function spawnWithTee(label, stepIndex, cmd, args, opts) {
  const useShell = process.platform === 'win32' || opts.useShell;
  // 非 CI: 直接 inherit,省内存/IO,与历史行为一致
  if (!IS_CI) {
    return spawnSync(cmd, args, {
      cwd: ROOT, encoding: 'utf8',
      stdio: ['ignore', 'inherit', 'inherit'],
      shell: useShell,
      env: process.env,
    });
  }
  const safeName = label.replace(/[^\w\u4e00-\u9fa5.-]+/g,'_').slice(0,80);
  const logPath = path.join(LOG_DIR, `step-${String(stepIndex).padStart(2,'0')}-${safeName}.log`);
  const header = [
    `# Step ${stepIndex}: ${label}`,
    `# Command: ${cmd} ${args.map(a=>/[\s"]/.test(a)?JSON.stringify(a):a).join(' ')}`,
    `# Started: ${new Date().toISOString()}`,
    `# PWD: ${ROOT}`,
    `# CI_JOB_ID=${process.env.CI_JOB_ID||''}  CI_COMMIT_SHORT_SHA=${process.env.CI_COMMIT_SHORT_SHA||''}`,
    `# useShell=${useShell}`,
    '',
  ].join('\n');
  let res;
  try {
    res = spawnSync(cmd, args, {
      cwd: ROOT, encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],    // 同时 pipe,拿到完整 stdout/stderr
      shell: useShell,
      env: process.env,
      maxBuffer: 50 * 1024 * 1024,          // 50MB,够 coverage 文本
    });
  } catch (err) {
    const text = header + '\n# SPAWN ERROR: ' + (err && err.stack || err) + '\n';
    fs.writeFileSync(logPath, text);
    return { status: 127, error: err, pid: 0, stdout: '', stderr: '' };
  }
  // 写终端 + 落日志(带分割线清晰分开 stdout/stderr)
  if (res.stdout) process.stdout.write(res.stdout);
  if (res.stderr) process.stderr.write(res.stderr);
  const body =
      (res.stdout || '') +
      (res.stderr ? (res.stdout && !res.stdout.endsWith('\n') ? '\n' : '') + '----- STDERR -----\n' + res.stderr : '') +
      `\n\n# Step finished: status=${res.status} ok=${res.status===0} error=${res.error?res.error.message:''}\n# Ended: ${new Date().toISOString()}\n`;
  fs.writeFileSync(logPath, header + body);
  return res;
}

function runStep(label, cmd, args, opts={}) {
  const idx = runStep._i = (runStep._i||0) + 1;
  const t0 = Date.now();
  console.log(`\n${BOLD}${CYAN}▶▶ STEP ${label}${RESET}${DIM}  ${cmd} ${args.join(' ')}${RESET}\n${hr(60,'·')}`);
  const res = spawnWithTee(label, idx, cmd, args, opts);
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
