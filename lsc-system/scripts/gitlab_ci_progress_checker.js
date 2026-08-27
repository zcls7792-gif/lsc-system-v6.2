#!/usr/bin/env node
/* ============================================================================
 * 链盛通 LSC · GitLab CI 真实进度只读检查器 v1
 * ----------------------------------------------------------------------------
 * 语义化 API (对应 Jenkins 1217250 经验)：
 *   GET /personal_access_tokens/self          → Token 有效性 & scope 审计
 *   GET /projects/:id                         → 项目基本信息
 *   GET /projects/:id/pipelines/latest        → lastBuild 概览 (building/result)
 *   GET /projects/:id/pipelines/:pid/jobs     → stage 列表 (wfapi/describe 等价)
 *   GET /projects/:id/jobs/:jid/trace         → 失败 job 关键日志 (最后 N 行+脱敏)
 *
 * 凭据优先级 (高→低):
 *   1) 当前进程 env        : GITLAB_TOKEN / GITLAB_HOST / (GITLAB_PROJECT_ID or GITLAB_PROJECT)
 *   2) 项目根 .gitlab-ci.env: (source 后的等效值)
 *   3) ~/.config/glab-cli/config.yml  (glab 登录态)
 *   4) ~/.netrc            (machine 域名 login USER password TOKEN)
 *
 * 安全红线（严格遵守 1217250/F3 状态检查硬分流）：
 *   - 全程仅使用 GET；绝不触发 POST/PUT/DELETE
 *   - 绝不打印完整 Token/Authorization/密码/PRIVATE-TOKEN；统一脱敏为 ****
 *   - 发现 scope 越权(非最小 read_api) → 红色告警 + 建议重建 PAT，不影响只读查询本身
 * ========================================================================== */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
let YAML = null;  // 可选依赖：没装 js-yaml 时跳过 glab config.yml 解析，不影响主流程
try { YAML = require('js-yaml'); } catch (_) { YAML = null; }

const ROOT = path.resolve(__dirname, '..');
const PAD = (s, n) => (s + ' '.repeat(n)).slice(0, n);
const NOW = new Date();

/* ------------------------ 1. 凭据探测 (多优先级) ------------------------ */
function detectCredentials() {
  const found = [];
  const creds = { host: null, project: null, projectId: null, token: null, src: null };

  // 优先级 1：进程 env
  const e = process.env;
  const envTok = e.GITLAB_TOKEN || e.GITLAB_API_TOKEN || e.GITLAB_PRIVATE_TOKEN || e.CI_JOB_TOKEN || null;
  const envHost = e.GITLAB_HOST || e.GITLAB_SERVER || e.GITLAB_URL || null;
  if (envTok || envHost || e.GITLAB_PROJECT || e.GITLAB_PROJECT_ID) {
    creds.token = creds.token || envTok;
    creds.host = creds.host || envHost;
    creds.projectId = creds.projectId || (e.GITLAB_PROJECT_ID && String(e.GITLAB_PROJECT_ID).trim()) || null;
    creds.project = creds.project || (e.GITLAB_PROJECT && e.GITLAB_PROJECT.trim()) || null;
    if (creds.src == null) creds.src = '进程环境变量 env (GITLAB_*)';
    found.push('process.env');
  }

  // 优先级 2：.gitlab-ci.env (bash 兼容语法，极简解析，不执行)
  const envFile = path.join(ROOT, '.gitlab-ci.env');
  if (fs.existsSync(envFile)) {
    const body = fs.readFileSync(envFile, 'utf8');
    const map = {};
    for (const raw of body.split(/\r?\n/)) {
      const line = raw.replace(/^\s+/, '');
      if (!line || line.startsWith('#')) continue;
      const eq = line.indexOf('='); if (eq < 0) continue;
      const k = line.slice(0, eq).trim();
      let v = line.slice(eq + 1).trim();
      if (v.length >= 2 && (v.startsWith('"') && v.endsWith('"') || v.startsWith("'") && v.endsWith("'"))) {
        v = v.slice(1, -1);
      }
      map[k] = v;
    }
    if (map.GITLAB_TOKEN) { creds.token = creds.token || map.GITLAB_TOKEN; creds.src ??= '.gitlab-ci.env'; found.push('.gitlab-ci.env'); }
    if (map.GITLAB_HOST) creds.host = creds.host || map.GITLAB_HOST;
    if (map.GITLAB_PROJECT_ID) creds.projectId = creds.projectId || String(map.GITLAB_PROJECT_ID).trim();
    if (map.GITLAB_PROJECT) creds.project = creds.project || String(map.GITLAB_PROJECT).trim();
  }

  // 优先级 3：~/.config/glab-cli/config.yml
  const glabCfg = path.join(os.homedir(), '.config', 'glab-cli', 'config.yml');
  if (fs.existsSync(glabCfg)) {
    try {
      const cfg = YAML.load(fs.readFileSync(glabCfg, 'utf8')) || {};
      const host = Object.keys(cfg.hosts || {})[0];
      if (host) {
        const hostCfg = cfg.hosts[host] || {};
        if (hostCfg.token) { creds.token = creds.token || hostCfg.token; creds.src ??= 'glab config.yml'; found.push('glab-cli'); }
        creds.host = creds.host || (cfg.protocol && host ? `${cfg.protocol}://${host}` : null);
      }
    } catch (_) { /* ignore parse */ }
  }

  // 优先级 4：~/.netrc
  const netrc = path.join(os.homedir(), '.netrc');
  if (fs.existsSync(netrc)) {
    try {
      const lines = fs.readFileSync(netrc, 'utf8').split(/\r?\n/).join(' ').split(/\s+/);
      let i = 0, mach = null, login = null, pass = null;
      while (i < lines.length) {
        const tok = lines[i++];
        if (tok === 'machine') mach = lines[i++];
        else if (tok === 'login') login = lines[i++];
        else if (tok === 'password') pass = lines[i++];
        if (mach && login && pass) {
          if (/gitlab/i.test(mach) || (creds.host && mach.includes(new URL(creds.host).hostname))) {
            creds.token = creds.token || pass;
            creds.host = creds.host || `https://${mach}`;
            creds.src ??= '~/.netrc';
            found.push('netrc');
            break;
          }
          mach = login = pass = null;
        }
      }
    } catch (_) { /* ignore */ }
  }

  return { creds, found };
}

/* ------------------------ 2. 合法性校验 & 脱敏工具 ------------------------ */
function maskSecrets(text) {
  if (text == null) return '';
  let s = String(text);
  s = s.replace(/glpat-[A-Za-z0-9_\-]{10,}/g, 'glpat-********');
  s = s.replace(/(Authorization:\s*Bearer\s+)[A-Za-z0-9._\-+/=]{8,}/gi, (m, p) => (p || '') + '********');
  s = s.replace(/(PRIVATE-TOKEN:\s*)[A-Za-z0-9_\-]{8,}/gi, (m, p) => (p || '') + '********');
  s = s.replace(/(password[=:\s]+["']?)[^"'\s&;]{4,}/gi, (m, p) => (p || '') + '********');
  s = s.replace(/(token[=:\s]+["']?)[^"'\s&;]{4,}/gi, (m, p) => (p || '') + '********');
  return s;
}
function maskTokenVal(t) {
  if (!t) return '(空)';
  return t.length > 6 ? `${t.slice(0, 6)}**** (len=${t.length})` : `**** (len=${t.length})`;
}

function validate(creds) {
  const problems = [];
  if (!creds.host) problems.push('缺少 GITLAB_HOST（例如 https://gitlab.com）');
  else {
    try { const u = new URL(creds.host); if (u.pathname && u.pathname !== '/') problems.push('GITLAB_HOST 不要带 path，需仅为协议+主机名'); }
    catch (_) { problems.push('GITLAB_HOST URL 格式非法'); }
  }
  if (!creds.token) problems.push('缺少 GITLAB_TOKEN（scope 最小 read_api）');
  else if (creds.token.length < 12) problems.push('GITLAB_TOKEN 长度异常（可能复制不全）');
  if (!creds.projectId && !creds.project) problems.push('GITLAB_PROJECT_ID (纯数字，推荐) 或 GITLAB_PROJECT (namespace/path) 至少填一个');
  return problems;
}

function projectUrlSegment(creds) {
  if (creds.projectId) return encodeURIComponent(String(creds.projectId));
  return encodeURIComponent(creds.project);
}

/* ------------------------ 3. GitLab API 封装 (全 GET) ------------------------ */
async function api({ host, token, path: apiPath, params = {}, parseJson = true, accept = 'application/json', raw = false }) {
  const qs = new URLSearchParams(params).toString();
  const url = `${host.replace(/\/$/, '')}/api/v4${apiPath}${qs ? '?' + qs : ''}`;
  const headers = {
    'PRIVATE-TOKEN': token,
    'Accept': accept,
    'User-Agent': 'lsc-ci-progress-checker/1.0',
  };
  const res = await fetch(url, { method: 'GET', headers, redirect: 'follow' });
  const text = await res.text();
  if (res.status === 401) throw new Error(`HTTP 401 Unauthorized → Token 无效/过期/缺失 read_api scope; ${maskSecrets(text).slice(0, 300)}`);
  if (res.status === 403) throw new Error(`HTTP 403 Forbidden → Token 无权限（scope 不足？ IP 封禁？）; ${maskSecrets(text).slice(0, 300)}`);
  if (res.status === 404) throw new Error(`HTTP 404 Not Found → 项目/资源不存在或 Token 看不到该项目 (private/internal 可见性?); path=${apiPath}`);
  if (res.status >= 400) throw new Error(`HTTP ${res.status} on ${apiPath} → ${maskSecrets(text).slice(0, 500)}`);
  if (raw) return { status: res.status, text };
  if (!parseJson) return text;
  if (!text) return null;
  try { return JSON.parse(text); } catch (e) { throw new Error(`JSON parse failed: ${maskSecrets(text).slice(0, 200)}`); }
}

/* ------------------------ 4. 进度报告主流程 ------------------------ */
async function main() {
  console.log('╔══════════════════════════════════════════════════════════════════════╗');
  console.log('║ 📊 链盛通 LSC V6.2 · GitLab 真实 CI 进度只读检查器  v1               ║');
  console.log('║    只读操作 · 5 条 GitLab v4 API · 全程脱敏 · 对应 1217250 结构化    ║');
  console.log('╚══════════════════════════════════════════════════════════════════════╝');
  console.log('执行时间:', NOW.toISOString(), '· Node:', process.version, '\n');

  /* --- 4.1 凭据 --- */
  console.log('① 凭据探测 (多优先级)...');
  const { creds, found } = detectCredentials();
  console.log('   探测命中源:', found.length ? found.join(' → ') : '(空)');
  console.log('   最终使用源:', creds.src || '(无)');
  console.log('   HOST      :', creds.host || '(未设置)');
  console.log('   PROJECT_ID:', creds.projectId || '(未设置)');
  console.log('   PROJECT   :', creds.project || '(未设置)');
  console.log('   TOKEN     :', maskTokenVal(creds.token));

  const problems = validate(creds);
  if (problems.length) {
    console.log('\n✗ 凭据校验未通过：');
    problems.forEach(p => console.log('   · ' + p));
    console.log(`
建议操作（任选其一）：
  A. 推荐：  cp ${ROOT}/.gitlab-ci.env.example ${ROOT}/.gitlab-ci.env
             （编辑 .gitlab-ci.env 填 3 要素）
             然后：source ${ROOT}/scripts/gitlab_ci_auth_setup.sh
             再跑：node ${__filename}

  B. 临时：  export GITLAB_HOST="https://gitlab.com"
             export GITLAB_PROJECT_ID="<数字ID>"  或  export GITLAB_PROJECT="namespace/name"
             export GITLAB_TOKEN="glpat-xxx"
             再跑：node ${__filename}
`);
    process.exit(2);
  }

  /* --- 4.2 Scope 审计 --- */
  console.log('\n② Token 有效性 + Scope 审计 (最小权限 read_api 自查)...');
  let scopeAudit = { ok: false, scopes: [], expires: null, name: null };
  try {
    const self = await api({ host: creds.host, token: creds.token, path: '/personal_access_tokens/self' });
    scopeAudit.scopes = (self.scopes || []);
    scopeAudit.expires = self.expires_at || null;
    scopeAudit.name = self.name || 'N/A';
    const hasReadApi = scopeAudit.scopes.includes('read_api');
    const hasApi = scopeAudit.scopes.includes('api');
    scopeAudit.ok = hasReadApi || hasApi;  // 有任一读/写scope都能跑查询，但会告警
    console.log('   Token 名  :', scopeAudit.name);
    console.log('   scopes     :', scopeAudit.scopes.join(', ') || '(空？异常)');
    console.log('   expires_at :', scopeAudit.expires || '永不到期 (不推荐)');
    if (hasReadApi && scopeAudit.scopes.length === 1) {
      console.log('   ✅ 完美：只有 read_api 一个 scope（最小权限原则）');
    } else if (hasReadApi) {
      console.log('   ⚠  轻微越权：除 read_api 外还有多余 scopes，建议 revoke 后重建仅 read_api 的 PAT');
    } else if (hasApi) {
      console.log('   🚨 严重越权：当前 PAT 含 api scope（等同于全站读写）！强烈建议 revoke 并重建 read_api');
    } else {
      console.log('   ✗ scope 不含 read_api/api，后续查询大概率会 403，请重新生成 PAT');
    }
  } catch (err) {
    if (/404|401|403|not found/i.test(err.message)) {
      // CI_JOB_TOKEN 或 Project Token 可能不支持 /self 端点 → 跳过不致命
      console.log('   ⚠  /personal_access_tokens/self 不支持（这是 CI_JOB_TOKEN / Project Token 的正常情况）');
      console.log('        跳过 scope 审计，直接继续项目查询。具体错误：', maskSecrets(err.message).split('\n')[0]);
      scopeAudit.ok = true; scopeAudit.scopes = ['(端点不可用，跳过)'];
    } else {
      console.log('   ✗ 鉴权失败:', maskSecrets(err.message));
      process.exit(3);
    }
  }

  /* --- 4.3 项目基本信息 --- */
  console.log('\n③ 项目基本信息（对应 Jenkins wfapi/describe）...');
  const seg = projectUrlSegment(creds);
  let projectMeta;
  try {
    projectMeta = await api({ host: creds.host, token: creds.token, path: `/projects/${seg}` });
    console.log('   name         :', projectMeta.path_with_namespace);
    console.log('   web_url      :', projectMeta.web_url);
    console.log('   default_br   :', projectMeta.default_branch);
    console.log('   visibility   :', projectMeta.visibility);
    console.log('   jobs_enabled :', projectMeta.jobs_enabled ?? '(字段未返回)');
    console.log('   last_activity:', projectMeta.last_activity_at || '(空)');
  } catch (err) {
    console.log('   ✗ 获取项目失败:', maskSecrets(err.message));
    process.exit(4);
  }

  /* --- 4.4 lastBuild = latest pipeline --- */
  console.log('\n④ lastBuild = latest pipeline 概览 (对应 Jenkins /lastBuild)...');
  let latest;
  try {
    latest = await api({ host: creds.host, token: creds.token, path: `/projects/${seg}/pipelines/latest` });
  } catch (err) {
    if (/404/i.test(err.message)) {
      console.log('   ∅ 暂无任何 pipeline（项目可能尚未触发 .gitlab-ci.yml 执行）');
      console.log('   可能原因：① Runner 未注册 ② Mirror 未同步 ③ 本分支没推到 GitLab');
      process.exit(0);
    }
    console.log('   ✗ latest pipeline 查询失败:', maskSecrets(err.message));
    process.exit(5);
  }
  console.log('   pipeline # :', latest.id, '   iid:', latest.iid, '   sha:', latest.sha ? latest.sha.slice(0, 8) + '…' : 'N/A');
  console.log('   ref        :', latest.ref, '   source:', latest.source);
  console.log('   status     :', latest.status, '   result/final:', latest.status === 'running' || latest.status === 'pending' ? '(构建中/排队)' : latest.status);
  console.log('   created_at :', latest.created_at);
  console.log('   updated_at :', latest.updated_at);
  console.log('   duration(s):', latest.duration ?? '(空)   (若构建中=累计耗时，finished=总耗时)');
  console.log('   user       :', (latest.user && latest.user.name) || 'N/A');
  console.log('   web_url    :', `${projectMeta.web_url}/-/pipelines/${latest.id}`);

  /* --- 4.5 stages = jobs 列表按 stage 聚合（等价 wfapi/describe） --- */
  console.log('\n⑤ 阶段列表 (等价 wfapi/describe，按 stage 聚合 jobs)...');
  const jobs = await api({
    host: creds.host, token: creds.token,
    path: `/projects/${seg}/pipelines/${latest.id}/jobs`,
    params: { per_page: 100, include_retried: 'true' },
  });
  console.log('   job 总数   :', jobs.length, '(含 retried，最多 100)');
  const byStage = new Map();
  for (const j of jobs) {
    if (!byStage.has(j.stage)) byStage.set(j.stage, []);
    byStage.get(j.stage).push(j);
  }
  const stageNames = [...byStage.keys()];
  console.log('   stages 顺序 (按 first createdAt 升序):');
  stageNames.sort((a, b) => {
    const A = byStage.get(a).reduce((m, j) => Math.min(m, new Date(j.created_at).getTime()), Infinity);
    const B = byStage.get(b).reduce((m, j) => Math.min(m, new Date(j.created_at).getTime()), Infinity);
    return A - B;
  });

  let runningStage = null;
  const failedStages = [];
  const doneStages = [];
  for (const stage of stageNames) {
    const list = byStage.get(stage);
    // 一个 stage 内多个 job：任 failed/canceled/skipped 取最严重；否则优先 running 再 pending 再 success
    const agg = aggregateStageStatus(list);
    const dur = list.reduce((s, j) => s + ((Number(j.duration) || 0)), 0);
    const nRun = list.filter(j => j.status === 'running').length;
    const nFail = list.filter(j => j.status === 'failed' || j.status === 'canceled').length;
    const nOk = list.filter(j => j.status === 'success').length;
    const bar = `[${'█'.repeat(nOk)}${'P'.repeat(nRun)}${'x'.repeat(nFail)}${'·'.repeat(Math.max(0, list.length - nOk - nRun - nFail))}]`;
    console.log(`     · ${PAD(stage, 16)}  ${PAD(agg.status, 10)}  jobs=${list.length}  ${bar}  total=${dur.toFixed(0)}s`);
    list.forEach(j => console.log(`         └ ${PAD(j.name, 36)}  ${PAD(j.status, 10)}  id=${j.id}  dur=${(j.duration ?? 0).toFixed(0)}s  allow_fail=${j.allow_failure}`));
    if (agg.status === 'running' && !runningStage) runningStage = { stage, list };
    if (agg.status === 'failed' || agg.status === 'canceled') failedStages.push({ stage, list });
    if (agg.status === 'success') doneStages.push(stage);
  }

  /* --- 4.6 当前执行 stage + 失败 stage 尾日志 80 行（脱敏） --- */
  console.log('\n⑥ 当前执行中 / 失败阶段关键日志（最后 80 行 + 脱敏）...');
  const targetJobs = [];
  if (runningStage) {
    const runJobs = runningStage.list.filter(j => j.status === 'running').slice(-1);
    targetJobs.push({ label: `[当前执行中 stage=${runningStage.stage}]`, jid: runJobs[0]?.id, jname: runJobs[0]?.name });
  }
  for (const fs of failedStages) {
    const fj = fs.list.filter(j => j.status === 'failed' || j.status === 'canceled').slice(-1)[0];
    targetJobs.push({ label: `[失败 stage=${fs.stage}]`, jid: fj?.id, jname: fj?.name });
  }
  if (targetJobs.length === 0) {
    console.log('   ✓ 无 running / failed stage，无需拉取尾日志');
  }
  for (const t of targetJobs) {
    if (!t.jid) continue;
    console.log(`\n   ═══ ${t.label}  job=${t.jname} (id=${t.jid}) ═══ 最后 80 行：`);
    try {
      const { text } = await api({
        host: creds.host, token: creds.token, raw: true,
        path: `/projects/${seg}/jobs/${t.jid}/trace`, accept: 'text/plain',
      });
      const lines = text.replace(/\r\n?/g, '\n').split('\n');
      const tail = lines.slice(-80);
      const masked = maskSecrets(tail.join('\n'));
      console.log('   ' + masked.split('\n').map(l => (l || ' ')).join('\n   '));
    } catch (e) {
      console.log('   ✗ 拉 trace 失败:', maskSecrets(e.message));
    }
  }

  /* --- 4.7 总结 --- */
  console.log('\n═══════════════════════════════════════════════════════════════ 总结');
  console.log('项目            :', projectMeta.path_with_namespace, '  (id=' + projectMeta.id + ')');
  console.log('latest pipeline : #' + latest.id + '  ref=' + latest.ref + '  sha=' + (latest.sha || '').slice(0, 8));
  console.log('整体状态        :', latest.status,
    latest.status === 'running' ? `（当前阶段：${runningStage ? runningStage.stage : 'N/A'}）` : ''
  );
  console.log('阶段维度        : 完成 ✓ ' + doneStages.length + ' / 失败 ✗ ' + failedStages.length +
    ' / 进行中 ⏱ ' + (runningStage ? 1 : 0) + ' / 总计 ' + stageNames.length);
  if (failedStages.length) {
    console.log('🚨 失败阶段      :', failedStages.map(f => f.stage).join(', '));
    console.log('   （如需进入修复：请在对话框里授权我创建 ISSUE 后再改动，严格遵守 1217250/F3 硬分流）');
  } else if (latest.status === 'success') {
    console.log('✅ 全绿：所有阶段 success');
  } else if (latest.status === 'running' || latest.status === 'pending' || latest.status === 'created') {
    console.log('⏱  构建中… 请稍后再跑本脚本查看最新进度：');
    console.log('       node scripts/gitlab_ci_progress_checker.js');
  } else {
    console.log('ℹ  非典型结果（' + latest.status + '）：请在 GitLab Web 页面打开链接复核');
  }
  console.log('Pipeline 直链  :', `${projectMeta.web_url}/-/pipelines/${latest.id}`);
}

/* ------------------------ 辅助：stage 状态聚合 ------------------------ */
function aggregateStageStatus(list) {
  const set = new Set(list.map(j => j.status));
  const order = ['failed', 'canceled', 'running', 'pending', 'created', 'preparing', 'waiting_for_resource', 'scheduled', 'manual', 'success', 'skipped'];
  for (const s of order) if (set.has(s)) return { status: s };
  return { status: 'unknown' };
}

process.on('unhandledRejection', (err) => {
  console.error('\n💥 Unhandled Rejection (已脱敏):\n', maskSecrets(err && err.stack ? err.stack : String(err)));
  process.exit(99);
});

main().catch(err => {
  console.error('\n💥 顶层错误（已脱敏）:\n', maskSecrets(err && err.stack ? err.stack : String(err)));
  process.exit(99);
});
