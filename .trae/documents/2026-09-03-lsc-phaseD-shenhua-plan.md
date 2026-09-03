# LSC V6.2 Phase D · 后端服务深度补测 + 新双端 Vue 脚手架加固 实施计划

> **阶段定位**：在前序四阶段（覆盖率追击 / 深色模式 / CI 强化 / E2E 扩展）与 Phase A/B/C（data-testid 契约 / 键盘可达性 / PWA 性能）均已交付、CI 双流水线 100% 绿灯的基础上，本轮 **"继续深化"** 聚焦两条主线：
>
> 1. **D1（后端深度补测）**：后端 13 个微服务中存在 7 个核心业务服务"主类 1 个 / 分支 0 个"测试文件 → 主源码:测试比 11:1 的薄覆盖，易造成回归在合并数天后才暴露。
> 2. **D2（新双端 Vue 脚手架加固）**：`lsc-system/lsc-admin-web` 与 `lsc-system/lsc-merchant-web` 是 Vue3 + TypeScript + Vite 新结构（非 Phase A/B/C 改造过的原生 HTML 四端），目前 0 单元测试 / 0 data-testid 契约 / 0 a11y 基础设施注入，存在测试/质量完全缺位的风险。

**目标**：D1 覆盖 7 薄服务的边缘分支并本地 surefire + 最终 CI 全过；D2 注入 vitest 基础脚手架 + 2 关键业务 view 单元测试 + data-testid 静态锚点 + a11y 焦点环。

---

## 仓库调研结论

### 已完成基线

| 模块 | 状态 | 最后验证 |
|---|---|---|
| 原生 4 端（merchant / platform / mobile / mini）data-testid 契约 | ✅ 177 钩子，c8 F_A1~F_A4 断言 | CI Run 33719902664 |
| 原生 4 端 keyboard-a11y（skip-links nav / roving / 6 快捷键） | ✅ axe 48 violations→0，B4 6 Playwright + B5 JSDOM | CI Run 33719902664 |
| 原生 4 端 PWA（manifest / shared SW / preload / preconnect） | ✅ 4 manifests，audit-meta/audit-assets 门控通过 | CI Run 33719902664 |
| a11y-audit.yml：Node 24 + actions v5 | ✅ 无 deprecation annotations | CI Run 33719902664 |
| lsc-media-service ComprehensiveStressTest：500MB 阈值稳定化 | ✅ 49/49 passed | CI Run 33722812299 ✅ |

### 后端薄覆盖服务（7 个）

| Service | main:java count | test count | 现况缺口 |
|---|---|---|---|
| `lsc-order-service` | 11 (controller / service / DTO / mapper / Feign) | 1 (`OrderServiceImplTest`) | 缺 OrderController MockMvc、Refund 分支、乐观锁重试、Feign fallback、DTO 校验 |
| `lsc-risk-service` | 8 | 1 (`RiskControlServiceImplTest`) | 缺 Controller 参数校验、AiGatewayFeign 熔断降级、RiskLogMapper 持久化 |
| `lsc-reconciliation-service` | 9 | 1 (`ReconciliationServiceImplTest`) | 缺 Controller、跨 3 Feign（ledger / order / evidence）聚合分支与失败 fallback |
| `lsc-map-service` | 6 | 2 | ExtendedTest 覆盖面有限；缺 Controller 层、导航路径枚举、GeoResult 边界 |
| `lsc-promotion-service` | 11 | 2 | 缺 PromotionController、FirstOrderCheckDTO 校验、Ledger/User Feign 断链回退 |
| `lsc-writeoff-service` | 10 | 2 | 缺 Controller 403、MerchantFeign 不存在商家、LedgerFeign 余额不足 + 并发核销 |
| `lsc-gateway` | 3 | 3 | 缺 JwtAuthFilter 过期 Token / 伪造签名 / 白名单路径 3 条缺失分支 |

每个服务的补测规模控制在 **1 个扩展测试文件 + 1 个 Controller 测试文件**（不拆分过小），避免新文件过多导致 CI 时间膨胀。

### 新双端 Vue3 脚手架现状

| 项 | `lsc-admin-web` | `lsc-merchant-web` |
|---|---|---|
| 源码 | 21 views / 10 api / 4 layout / stores | 16 views / 11 api / 4 components / stores |
| 构建 | Vite + TypeScript + Vue3 | Vite + TypeScript + Vue3 |
| 单元测试框架 | ❌ 未安装 | ❌ 未安装 |
| data-testid 锚点 | ❌ 0 | ❌ 0 |
| 键盘可达性 / 焦点环 | ❌ 无 keyboard-a11y.js | ❌ 无 keyboard-a11y.js |
| PWA manifest / preload | ❌ 0 | ❌ 0 |

与原生 4 端的 Phase A/B/C 对齐，但使用 **Vue3 生态工具（vitest + vue-utils + 组件级 `data-testid` 注入）**，不强制复写原生结构。

---

## 文件与模块变更地图

### Group D1 · 后端 7 服务深度补测

| 模块 | 新增 / 修改文件 | 预期变更 |
|---|---|---|
| `lsc-order-service` | 新增 `OrderControllerMockMvcTest.java` + 修改 `pom.xml`（若无 `spring-boot-starter-test` 版本声明，已存在则不改） | MockMvc：创建/支付/退款 3 接口 + DTO 校验 8 场景，~140 行 |
| `lsc-order-service` | 新增 `OrderServiceBranchTest.java` | Feign fallback / 乐观锁重试 / 幂等重复请求，~120 行 |
| `lsc-risk-service` | 新增 `RiskControllerTest.java` | MockMvc 参数校验 + 非法场景，~90 行 |
| `lsc-risk-service` | 修改 `RiskControlServiceImplTest.java`（在文件末尾追加） | AiGateway 熔断降级（短路返回低风险）+ RiskLogMapper 持久化断言，+60 行 |
| `lsc-reconciliation-service` | 新增 `ReconciliationBranchTest.java` | 3 Feign 任一失败 fallback，对账差异生成报告分支，~130 行 |
| `lsc-reconciliation-service` | 新增 `ReconciliationControllerTest.java` | MockMvc 查询/生成报告 2 接口，~70 行 |
| `lsc-map-service` | 修改 `MapServiceImplExtendedTest.java` | 追加 4 场景：导航路径枚举 / GeoResult 空 POI / 坐标越界 / 结果降序排序，+80 行 |
| `lsc-promotion-service` | 新增 `PromotionControllerTest.java` + `PromotionFallbackBranchTest.java` | Controller 参数校验 + Ledger 失败回退到 Pending，~150 行（参考 `PromotionServiceImplTest#testCalcReward_LedgerFail_FallsBackToPending` 结构） |
| `lsc-writeoff-service` | 新增 `WriteOffServiceConcurrencyTest.java` + `WriteOffControllerSecurityTest.java` | 并发 8 线程核销（不允许负数余额）+ Controller 403 拦截，~140 行 |
| `lsc-gateway` | 修改 `filter/JwtAuthFilterTest.java` 末尾追加 3 节 | 过期 Token / 伪造签名 / 白名单路径（actuator/*）放行，+70 行 |

### Group D2 · 新双端 Vue 脚手架加固

| 模块 | 新增 / 修改 | 预期变更 |
|---|---|---|
| `lsc-admin-web` | 修改 `package.json` — 追加 devDeps `vitest @vue/test-utils happy-dom typescript`，scripts 增加 `test`, `test:ci` | 4 行 dependencies + 2 行 scripts |
| `lsc-admin-web` | 新建 `vitest.config.ts` | 配置 `environment: happy-dom`，TS include，~30 行 |
| `lsc-admin-web` | 新建 `src/views/login/__tests__/Login.spec.ts` | 用户名/密码空校验、登录成功、JWT 存储、data-testid 存在，~80 行 |
| `lsc-admin-web` | 新建 `src/views/merchant/__tests__/List.spec.ts` | 商家列渲染、`audit` 按钮触发事件、分页 change，~90 行 |
| `lsc-admin-web` | 修改所有主要 view：login / dashboard / merchant-(List|Audit|Credit) / product-(List|Audit) / b2b-(List|Verify) / order-List / evidence-(List|Verify) / risk-(Dashboard|Logs) / param-Approval / release-(Config|Predict|Simulation|Summary) / reconcile-Report / admin-(Audit|List) / writeoff-List | 追加 `data-testid`：`<section data-testid="admin-view-xxx">`，顶部按钮加 `data-testid="admin-btn-<action>"`，搜索框加 `data-testid="admin-search-xxx"`。~24 个锚点，每文件改 2~4 行 |
| `lsc-admin-web` | 修改 `src/styles/index.css` 末尾追加 焦点环 + skip-link 基础样式（参考 lsc-system shared/design-system.css 但仅 20 行） | `:focus-visible` outline / `.skip-link:focus-visible` 显示 |
| `lsc-merchant-web` | 修改 `package.json` — 追加 vitest，scripts 增加 test | 同 admin-web |
| `lsc-merchant-web` | 新建 `vitest.config.ts` | ~30 行 |
| `lsc-merchant-web` | 新建 `src/views/login/__tests__/Login.spec.ts` + `src/views/dashboard/__tests__/Dashboard.spec.ts` | 登录场景 + Dashboard 卡渲染，~160 行 |
| `lsc-merchant-web` | 修改主要 view：login / dashboard / b2b-(Create|List) / credit-Info / lsc-(Account|Transactions) / order-(List|Refund) / product-(List|Publish|Category) / store-(Address|Info) / writeoff-(Apply|Records) | 注入 merchant 前缀 `data-testid="merchant-..."`，~22 个锚点 |
| `lsc-merchant-web` | 修改 `src/styles/index.css` 追加焦点环样式 | ~20 行 |

### Group D3 · CI 门控（无新增 yml，仅扩已存在 build.yml）

- **不新增** workflow 文件。
- 在 `.github/workflows/build.yml` 的 `mvn test` 步骤之后追加 1 个 step：`D1 surefire summary`（`gh api .../logs | strings | grep Tests run:` 汇总），用于失败时快速定位。
- 追加 1 个 step（并行 2 个 job 内部，串行）：`D2 vitest ci` — 在 Node 24 环境中进入 lsc-system/lsc-admin-web 与 lsc-merchant-web 各跑一次 `npm ci && npm run test:ci`。不破坏现有 Quality Gate 结构。

---

## 依赖顺序的实施步骤

### Step 0 — 启动前检查（仅验证，不改代码）

```bash
cd /workspace
# 确认当前 HEAD 无本地变更、CI 绿
git status --short
gh run list --limit 2  # 应 2 success
```

### Step 1 — D1 后端补测：按服务逐个实现 + 单元验证

顺序：order → risk → reconciliation → map → promotion → writeoff → gateway

每个服务执行：
1. 读对应现有 Test 文件结构（Mock 约定、@ExtendWith、Mockito 风格）
2. 新建或追加扩展测试，完全复用现有 Mock 风格（不引入 PowerMock 等新依赖）
3. `mvn -pl <svc> -am -DskipTests=false -Dtest=<NewTestName> -Dsurefire.failIfNoSpecifiedTests=false test` 验证
4. 提交该服务独立 commit

### Step 2 — D2 新双端：vitest 脚手架 + data-testid + 焦点环

1. admin-web：写入 package.json 追加 → `npm install --include=dev`（仅首次）→ vitest.config.ts → 2 spec 跑通 → data-testid 注入 → CSS 焦点环 → 提交
2. merchant-web：完全镜像 admin-web 步骤 → 提交
3. D2 整体验证：admin-web `npm run test:ci` exit=0；merchant-web 同样；手动抽样 3 个 data-testid 存在。

### Step 3 — D3 扩 build.yml：追加 surefire 汇总 + 双端 vitest ci step

1. 在 `build.yml` 的 `Build, Test & Coverage (JDK 17)` job 内：
   - 在 `mvn test` step 之后，新增 step 名 `Post-mortem: aggregate surefire summaries`
   - 在该 job 结尾 `Quality Gate Summary` 之前，新增 2 个 `run:` 行调用 `cd lsc-system/lsc-admin-web && ... test:ci`。
2. （可选）若 `package.json` scripts 区缺少，补 `test:ci` 到两 package.json。

### Step 4 — 本地端到端回归

```bash
# D1 汇总：7 个服务全量 sure-fire
cd /workspace
mvn -B -ntp -pl \
  lsc-order-service,lsc-risk-service,lsc-reconciliation-service,lsc-map-service,lsc-promotion-service,lsc-writeoff-service,lsc-gateway \
  -am -DskipTests=false test 2>&1 | tail -50
# Expected: BUILD SUCCESS，每个服务 BUILD SUCCESS 分别出现

# D2：新双端 vitest
cd /workspace/lsc-system/lsc-admin-web && npm run test:ci
cd /workspace/lsc-system/lsc-merchant-web && npm run test:ci
```

### Step 5 — 提交 + 推送，触发 GitHub CI

一轮 commit、push。等 7-8 min 核对双工作流结论。

---

## 依赖与注意事项

- **依赖前提**：D1 所有扩展测试都基于 `spring-boot-starter-test` 带有的 Mockito / JUnit5；若某个服务 pom 未声明 starter（全局 parent 已声明则不用再写），会在执行中通过 `mvn dependency:resolve` 先校验，失败才加 pom。
- **代理**：sandbox 内 Maven 需要 `~/.m2/settings.xml` proxy（已存在于上一轮修复）。Node 包可能也需 HTTP(S)_PROXY，默认环境变量已设置。
- **兼容性**：vitest 版本选 `^2.1.0`（与 Node 24 兼容），`@vue/test-utils` 选 `^2.4.0`（Vue 3.x）。
- **命名规范**：新 data-testid 沿用现有 `<scope>-<component>-<action>` 约定：admin-web → `admin-`，merchant-web → `merchant-`（与原生 merchant-admin 前缀一致避免冲突，但组件名区分，不会覆盖 Phase A 既有契约）。
- **不越界**：D2 不包含 a11y Playwright 快照（会显著增加 LSC V6.2-Quality Gate 的 16 snap 执行时间），只做 `:focus-visible` 焦点环与 skip-link CSS 基础。若本轮通过后续想追 a11y，可在未来 Plan 中单独扩展。

---

## 验证

| Step | 验证命令 / 检查 | 通过标准 |
|---|---|---|
| D1 每个服务 | `mvn -pl <svc> -am -Dtest=<Test> test` | BUILD SUCCESS，新增测试 100% 通过 |
| D1 汇总 | 7 服务聚合 sure-fire | `Tests run: N, Failures: 0` |
| D2 admin-web | `npm run test:ci` | vitest exit 0，至少 4 个用例通过 |
| D2 merchant-web | `npm run test:ci` | vitest exit 0，至少 4 个用例通过 |
| D2 data-testid | `node -e` 扫描 `admin-` 锚点 ≥24，`merchant-` ≥22 | 计数达成 |
| D2 focus-visible | `grep -c :focus-visible src/styles/index.css` | ≥2 matches 每端 |
| D3 build.yml | `yamllint` / 自检查 | 语法合法，双 step 存在 |
| 最终 CI | `gh run view <QG_RUN>` / `<BT_RUN>` | 均 `conclusion: success` |

---

## 风险

| 风险 | 等级 | 处理 |
|---|---|---|
| 某些 Service 测试 @Mock 依赖链过深（嵌套 3 层 Feign + mapper），写分支失败 | 中 | 回退策略：对该服务只做 Controller MockMvc 参数层覆盖（不进 service）；保证新增文件至少 8 场景的参数/边界分支 |
| npm 在沙箱内下载 vitest 包超时 | 低 | 使用 `-registry https://registry.npmmirror.com` 镜像；或 `npm install --prefer-offline` |
| 扩 build.yml 触发 indentation 错误 | 低 | 提交前用 `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"` 语法校验 |
| 7 服务新增测试拖慢 Build CI 超过 30 min | 中 | surefire 用 `--fail-at-end`（整批结束后再统一抛失败）避免中间单点 fail 早退出；若超过 30 分钟在下轮把 D1 拆到独立轻量 job（Java 17，仅这 7 服务），与原 Build CI 并行 |
