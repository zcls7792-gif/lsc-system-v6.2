# 链盛通LSC消费权益凭证循环系统V6.2(AI增强版)
# 代码质量检查与BUG修复报告 (2026-08-19)

---

## 一、概述

本次代码质量审计覆盖系统全部17个微服务模块，重点检查ServiceImpl实现类、工具类、安全组件、分库分表路由、网关过滤器等核心环节。累计发现并修复 20 个问题，其中 HIGH 风险 11 个、MEDIUM 风险 7 个、LOW 风险 2 个。修复后关键模块单元测试累计 1441 个全部通过，含证据服务全链路压力测试 600 条记录成功率 100%。

---

## 二、修复清单 (按风险等级)

### ▶ HIGH 风险修复 (11)

| # | 模块 | 文件 | 问题描述 | 修复方案 |
|---|------|------|---------|---------|
| 1 | lsc-map-service | MapServiceImpl.java | `volatile boolean amapDown` 并发更新不安全，高并发下状态不一致 | 重构为 `AtomicBoolean amapDown = new AtomicBoolean(false)`，全部访问使用 `get()/set()` |
| 2 | lsc-map-service | MapServiceImpl.java | 高德地理编码 `location` 字段未校验格式，可能越界/空指针 | 增加 `location==null` 与 `!contains(",")` 判定，`split(",").length < 2` 时抛 BizException |
| 3 | lsc-map-service | MapServiceImpl.java | IP定位 `province/city` 为 null 时字符串拼接 NPE | 空值前先判定 `== null`，兜底返回"未知" |
| 4 | lsc-b2b-service | B2bOrderServiceImpl.java | `createOrder/executeTransfer` 未校验 `lscAmount/totalAmountRmb/operatorId/expireAt` 非空，直接 NPE | 新增参数非空+正数校验，不满足抛 BizException |
| 5 | lsc-b2b-service | B2bOrderServiceImpl.java | AI评分/核验结果 `Long.parseLong` 遇非数字直接抛，阻断订单流程 | 外层捕获 `NumberFormatException`，记录 warn 日志并降级跳过该分支 |
| 6 | lsc-risk-service | RiskControlServiceImpl.java | `incrWindow` Redis 异常直接抛出→风控中断 | 整段 try-catch RuntimeException，降级返回 0L 计数并输出 warn 日志 |
| 7 | lsc-risk-service | RiskControlServiceImpl.java | `selectCount(null)` MyBatis-Plus 语义不稳定，部分版本抛异常 | 改为显式 `new LambdaQueryWrapper<>()` 实例传入 |
| 8 | lsc-common | ShardingRouter.java | `userId % 32` 取模导致用户分布不均；负数ID直接 `-1%8=-1` 越界 | 改为按块分配: `block = userId/32`，库号 `((block%8)+8)%8`；负数ID正向取模 |
| 9 | lsc-common | XssProtectionFilter.java | 无参构造器未给 `enabled` 赋值→过滤器默认禁用，XSS防护失效 | 显式无参构造: `this.enabled = true` |
| 10 | lsc-gateway | JwtAuthFilter.java | `token.isBlank()` 空字符串进入 JJWT，抛 `IllegalArgumentException` 未捕获→500 | 提取 token 后加 `isBlank()` 前置校验；catch 块增加 `IllegalArgumentException` 并返回 401 |
| 11 | lsc-gateway | JwtAuthFilter / RateLimitConfig | `request.getRemoteAddress().getAddress()` IPv6/unresolved 场景为 null，直接 NPE | 双重判空 `!= null && getAddress() != null`，兜底用 `getHostString()` 再兜底 "unknown" |

### ▶ MEDIUM 风险修复 (7)

| # | 模块 | 文件 | 问题描述 | 修复方案 |
|---|------|------|---------|---------|
| 12 | lsc-writeoff-service | WriteOffServiceImpl.java | `toLong` 直接 `Long.parseLong`，非数字→核销中断 | 先判断 `instanceof Number`；外层 catch NumberFormatException 返回 0L |
| 13 | lsc-writeoff-service | WriteOffServiceImpl.java | `applyWriteOff` 未校验 `merchantId/lscAmount` 非空正数 | 新增基础参数校验，非法抛 BizException |
| 14 | lsc-user-service | MerchantServiceImpl.java | `getCreditDetail` 中 `creditScore=null` 直接 put→前端 null，违背契约（默认值 0） | `detail.put("creditScore", ext.getCreditScore()==null?0:ext.getCreditScore())` |
| 15 | lsc-user-service | UserEdgeCaseTest.java | 导入 `com.baomidou.mybatisplus.session.Configuration`（包路径错误，MP版本差异） | 改为 `org.apache.ibatis.session.Configuration` 与 `org.apache.ibatis.builder.MapperBuilderAssistant` |
| 16 | lsc-gateway | GatewayEdgeCaseTest.java | 测试类缺失 JwtAuthFilter、RateLimitConfig 导入→编译失败 | 新增 `import com.lianshengtong.gateway.filter.JwtAuthFilter` 和 `.config.RateLimitConfig` |
| 17 | lsc-gateway | GatewayEdgeCaseTest.java | `ReflectionTestUtils.getField` 返回 Object 直接赋给 `List<String>`→Java 8+ 编译不通过 | 显式强转 `(List<String>)` 并加 `@SuppressWarnings("unchecked")` |
| 18 | lsc-common | 3 个测试文件 | ShardingRouter 算法重构后，旧断言 `userId=4→db=1` `userId=-1→table=-1` 全部失败 | 按新算法(块分配+正向取模)更新 13 处断言和 DisplayName |

### ▶ LOW 风险修复 (2)

| # | 模块 | 文件 | 问题描述 | 修复方案 |
|---|------|------|---------|---------|
| 19 | lsc-common | CommonP2Test.java | XssProtectionFilter 无参构造测试断言 false（与生产修复相反） | 断言改为 `assertTrue`，DisplayName 同步变更为 "默认启用" |
| 20 | lsc-user-service | UserEdgeCaseTest.java | `saveStoreAddress` 测试中 `updateById` stub 未使用，Mockito STRICT 模式抛 `UnnecessaryStubbingException` | 未使用的 stub 改为 `lenient().when(...)` 保留可扩展语义 |

---

## 三、测试验证结果

### 3.1 模块级单元测试

| 模块 | 测试数 | Failures | Errors | Skipped | 结果 | 备注 |
|------|-------:|---------:|-------:|--------:|:----:|------|
| lsc-common | 728 | 0 | 0 | 0 | ✅ | 含异常、安全、工具、AOP、MQ 多维测试 |
| lsc-evidence-service | 471 | 0 | 0 | 0 | ✅ | 含 ComprehensiveStressTest 压力测试 |
| lsc-user-service | 127 | 0 | 0 | 0 | ✅ | 含 User/Merchant 边界条件测试 |
| lsc-admin-service | — | 0 | 0 | — | ✅ | 编译+测试通过 |
| lsc-ai-gateway | — | 0 | 0 | — | ✅ | 编译+测试通过 |
| lsc-gateway | 115 | 0 | 0 | 0 | ✅ | 含限流、JWT解析、IP解析边界 |
| **小计** | **1441+** | **0** | **0** | **0** | **✅** | |

### 3.2 证据服务全链路压力测试 (内嵌 ComprehensiveStressTest)

```
████████████████████████████████████████████████████████████
  📋 全链路压力测试汇总报告
████████████████████████████████████████████████████████████
   总记录数: 600          总耗时: 30064ms
   平均每条: 50.11ms      吞吐:   20 records/s
   成功率: 100%           已失败: 0
   平均处理延迟: 50ms     缓存大小: 598
   ✅ 全链路压力测试通过!
```

### 3.3 编译验证

- 全量 `mvn clean compile` 成功：无编译错误、无警告
- 父 POM + 17 模块构建树解析正确

---

## 四、本轮新增/修改文件清单

**生产代码修复 (9 files)**
1. [MapServiceImpl.java](file:///workspace/lsc-map-service/src/main/java/com/lianshengtong/map/service/impl/MapServiceImpl.java) — AtomicBoolean + 格式越界 + IP定位NPE
2. [B2bOrderServiceImpl.java](file:///workspace/lsc-b2b-service/src/main/java/com/lianshengtong/b2b/service/impl/B2bOrderServiceImpl.java) — 参数校验 + NumberFormatException 兜底
3. [RiskControlServiceImpl.java](file:///workspace/lsc-risk-service/src/main/java/com/lianshengtong/risk/service/impl/RiskControlServiceImpl.java) — Redis异常降级 + LambdaQueryWrapper 显式化
4. [WriteOffServiceImpl.java](file:///workspace/lsc-writeoff-service/src/main/java/com/lianshengtong/writeoff/service/impl/WriteOffServiceImpl.java) — toLong安全转换 + applyWriteOff参数校验
5. [ShardingRouter.java](file:///workspace/lsc-common/src/main/java/com/lianshengtong/common/sharding/ShardingRouter.java) — 分库算法(按块分配+正向取模)重构
6. [XssProtectionFilter.java](file:///workspace/lsc-common/src/main/java/com/lianshengtong/common/security/XssProtectionFilter.java) — 无参构造 enabled=true
7. [MerchantServiceImpl.java](file:///workspace/lsc-user-service/src/main/java/com/lianshengtong/user/service/impl/MerchantServiceImpl.java) — creditScore null→0 默认值
8. [JwtAuthFilter.java](file:///workspace/lsc-gateway/src/main/java/com/lianshengtong/gateway/filter/JwtAuthFilter.java) — 空token前置校验 + resolveClientIp NPE 双重保护
9. [RateLimitConfig.java](file:///workspace/lsc-gateway/src/main/java/com/lianshengtong/gateway/config/RateLimitConfig.java) — resolveIp NPE 双重保护

**测试代码修复 (7 files)**
10. CommonP2Test.java — XssProtectionFilter 断言 true
11. CommonP3Test.java — ShardingRouter 7 处断言更新
12. CommonP6Test.java — ShardingRouter 4 处断言更新
13. UserEdgeCaseTest.java — import修复 + creditScore默认值对齐 + lenient() stub
14. MerchantServiceImplTest.java — (已验证正确的 ibatis import)
15. GatewayEdgeCaseTest.java — 缺失import、泛型强转、@Test 校验
16. UtilsEdgeCaseTest.java — 幂等键段数 5 段(此前为4段)

---

## 五、遗留与后续建议

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P1 | 剩余微服务补充 ServiceImpl 单测 | 当前 b2b/risk/writeoff/release/ledger/media 等核心服务测试覆盖率仍可提升，建议按 AdminServiceImpl 补充模式逐个补齐 |
| P1 | SonarQube / SpotBugs 集成 | 把 SpotBugs、PMD、Checkstyle 纳入 CI 构建门禁，质量阈值设为 BLOCKER=0、CRITICAL≤1、覆盖率≥80% |
| P2 | 数据库集成测试引入 Testcontainers | 当前 ORM/Mapper 层以 Mock 为主，建议用 MySQL testcontainer 跑一次真实分库分表路由 + 事务验证 |
| P2 | 性能回归基准建立 | 将 evidence-service 的压力测试抽取成独立 profile，每次发版跑基线对比，吞吐下降≥5%阻断合并 |
| P3 | 统一 Mockito strictness | 在父 pom 的 surefire argLine 中全局指定 `-Dmockito.strictness=LENIENT` 或彻底清理 UnnecessaryStubbing |

---

## 六、Git 推送说明

- 提交策略：所有修复以一个 or 多个 fixup commit 推送至主分支，保留逐文件修复历史
- 包含本报告文档 `LSC_V6.2_Code_Quality_Fix_Report_20260819.md` 以便审计追踪
- 推送完成后可在 GitHub Actions 中查看 CI 构建日志（若已配置 workflow）

---

**报告生成时间**: 2026-08-19  
**审核**: LSC 技术委员会  
**签字**: ___________________  
