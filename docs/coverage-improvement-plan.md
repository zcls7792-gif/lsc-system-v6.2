# 代码覆盖率提升改进计划

> 生成时间：2026-08-07
> 基础数据：JaCoCo 覆盖率报告（355个测试用例，11个模块）

---

## 一、当前覆盖率全景

### 1.1 总体数据

| 指标 | 当前值 | 目标值 | 差距 |
|------|--------|--------|------|
| **行覆盖率** | 44.5% | ≥ 70% | -25.5% |
| **分支覆盖率** | 36.8% | ≥ 60% | -23.2% |
| **指令覆盖率** | 45.9% | ≥ 70% | -24.1% |
| **方法覆盖率** | 34.0% | ≥ 60% | -26.0% |

### 1.2 模块覆盖率排行

```
 lsc-risk-service      98.6% ████████████████████████████
 lsc-writeoff-service  85.6% ██████████████████████░░░░░░
 lsc-promotion-service 82.6% █████████████████████░░░░░░░
 lsc-ledger-service    75.1% ████████████████████░░░░░░░░
 lsc-b2b-service       70.5% ███████████████████░░░░░░░░░
 lsc-mall-service      68.2% ██████████████████░░░░░░░░░░
 lsc-ai-gateway        59.0% ████████████████░░░░░░░░░░░░
 lsc-order-service     31.5% ██████░░░░░░░░░░░░░░░░░░░░
 lsc-user-service      20.7% ████░░░░░░░░░░░░░░░░░░░░░░
 lsc-release-service   11.1% ██░░░░░░░░░░░░░░░░░░░░░░░░
 lsc-common            10.2% ██░░░░░░░░░░░░░░░░░░░░░░░░
```

### 1.3 覆盖率分级

| 等级 | 模块 | 行覆盖率 | 说明 |
|------|------|---------|------|
| 🟢 优秀 | lsc-risk-service | 98.6% | 核心风控逻辑完善 |
| 🟢 优秀 | lsc-writeoff-service | 85.6% | 核销核心覆盖充分 |
| 🟢 优秀 | lsc-promotion-service | 82.6% | 促销逻辑覆盖充分 |
| 🟡 良好 | lsc-ledger-service | 75.1% | 账本核心需补边界 |
| 🟡 良好 | lsc-b2b-service | 70.5% | B2B订单需补异常路径 |
| 🟡 良好 | lsc-mall-service | 68.2% | 商品服务需扩展场景 |
| 🟡 良好 | lsc-ai-gateway | 59.0% | AI子服务需全面补测 |
| 🔴 待提升 | lsc-order-service | 31.5% | 订单核心业务分支缺失 |
| 🔴 待提升 | lsc-user-service | 20.7% | 商家服务完全未覆盖 |
| 🔴 待提升 | lsc-release-service | 11.1% | 释放配置/批量释放未覆盖 |
| 🔴 待提升 | lsc-common | 10.2% | 基础设施类普遍未覆盖 |

---

## 二、低覆盖率模块深度分析

### 2.1 lsc-common（行覆盖率 10.2%）

**问题概述**：30个类中，25个类覆盖率为 0%。这些类多为基础设施类，部分已在 JaCoCo exclude 列表中。

#### 0% 覆盖类清单

| 类名 | 未覆盖行 | 未覆盖方法 | 优先级 | 分类 |
|------|---------|-----------|--------|------|
| RabbitMQConfig | 56 | 24 | P3 | 配置类 |
| CsrfTokenManager | 37 | 10 | P2 | 安全 |
| R | 34 | 15 | P3 | 通用返回 |
| AesEncryptUtil | 30 | 8 | **P1** | 加密工具 |
| XssRequestWrapper | 28 | 9 | P2 | 安全 |
| DistributedLock | 26 | 10 | **P1** | 分布式锁 |
| MessageProducer | 24 | 10 | P2 | MQ消息 |
| GlobalExceptionHandler | 23 | 8 | P3 | 异常处理 |
| AdminRoleAspect | 29 | 6 | **P1** | AOP切面 |
| MessageProducer.EvidenceMessage | 18 | 12 | P2 | MQ消息 |
| MessageProducer.RiskAlertMessage | 18 | 12 | P2 | MQ消息 |
| ReleaseCalcUtil | 18 | 5 | **P1** | 释放计算 |
| LogSanitizer (security) | 44 | 6 | P2 | 日志脱敏 |
| IdempotentAspect | 39 | 8 | P2 | 幂等切面 |
| XssProtectionFilter | 18 | 6 | P2 | XSS防护 |
| MessageProducer.FirstOrderMessage | 15 | 10 | P2 | MQ消息 |
| IdempotentKeyGenerator | 14 | 4 | **P1** | 幂等生成 |
| LogSanitizer (utils) | 345 | 6 | P2 | 日志脱敏 |
| BizException | 13 | 5 | P3 | 异常类 |
| TracingConfig | 14 | 8 | P3 | 链路追踪 |
| ResultCode | 33 | 4 | P3 | 返回码 |
| ShardingRouter | 5 | 5 | P3 | 分片路由 |

#### 已排除类（pom.xml 配置）
`**/config/**`, `**/entity/**`, `**/dto/**`, `**/vo/**`, `**/enums/**`, `**/mapper/**`, `**/feign/**`, `**/controller/**`, `**/*Application.class`

#### 改进建议

| 优先级 | 类 | 建议测试场景 | 预估提升 |
|--------|-----|-------------|---------|
| **P1** | AesEncryptUtil | 加密/解密正常流程、密钥派生、异常输入 | +1.0% |
| **P1** | DistributedLock | 加锁/解锁、重试逻辑、超时处理 | +0.8% |
| **P1** | AdminRoleAspect | 管理员校验、角色匹配、权限拒绝 | +0.8% |
| **P1** | ReleaseCalcUtil | 释放率计算、边界值、精度校验 | +0.6% |
| **P1** | IdempotentKeyGenerator | 键生成、重复检测、格式校验 | +0.4% |
| P2 | CsrfTokenManager | Token生成/校验/过期 | +0.8% |
| P2 | MessageProducer | 各消息类型发送、异常处理 | +1.5% |
| P2 | XssProtectionFilter | XSS过滤、编码、白名单 | +0.8% |
| P2 | LogSanitizer | 日志脱敏规则、敏感字段处理 | +1.0% |
| P2 | IdempotentAspect | 幂等拦截、重复请求处理 | +0.6% |

### 2.2 lsc-release-service（行覆盖率 11.1%）

**问题概述**：7个类中，4个类覆盖率为 0%，仅 ReleaseCalcServiceImpl 达到 93.3%。

| 类名 | 覆盖率 | 未覆盖行 | 业务重要性 |
|------|--------|---------|-----------|
| ReleaseCalcServiceImpl | 93.3% | 3/45 | ✅ 已覆盖 |
| **ReleaseConfigServiceImpl** | **0.0%** | **94/94** | 🔴 配置管理核心 |
| **BatchReleaseServiceImpl** | **0.0%** | **144/144** | 🔴 批量释放核心 |
| **ReleaseJobHandler** | **0.0%** | **87/87** | 🟡 定时任务 |
| LoggingAlertChannel | 0.0% | 5/5 | 🟢 告警通道 |
| AlertChannel (interface) | 0.0% | 1/1 | 🟢 接口定义 |

#### 改进建议

**P0 - ReleaseConfigServiceImpl（94行待覆盖）**
- 配置读取：getRateMax/getRateMin/getKMin/getKMax/getAlpha
- 配置修改：updateConfig 双管理员签名校验
- 参数变更：applyParamChange 审批流程
- 审批通过：approveParamChange 审批通过/拒绝
- 缓存刷新：refresh/getByKey 缓存机制
- 可编辑校验：isEditable 硬常量vs可配置

**P0 - BatchReleaseServiceImpl（144行待覆盖）**
- 批量释放：createBatchReleaseOrder 批量创建
- 释放执行：executeReleaseOrder 释放执行
- 异常处理：部分失败回滚、重试机制
- 结果查询：getReleaseResult 状态查询

**P1 - ReleaseJobHandler（87行待覆盖）**
- 定时释放：scheduledReleaseTask 定时触发
- 对账校验：reconcileRelease 对账逻辑
- 异常告警：释放异常告警发送

### 2.3 lsc-user-service（行覆盖率 20.7%）

**问题概述**：3个类中，MerchantServiceImpl 和 JwtUtil 覆盖率为 0%。

| 类名 | 覆盖率 | 未覆盖行 | 业务重要性 |
|------|--------|---------|-----------|
| UserServiceImpl | 86.0% | 13/93 | ✅ 已覆盖 |
| **MerchantServiceImpl** | **0.0%** | **262/262** | 🔴 商家管理核心 |
| JwtUtil (user) | 0.0% | 32/32 | 🟡 JWT工具 |

#### 改进建议

**P0 - MerchantServiceImpl（262行待覆盖）**
- 商家注册：register 入驻流程、重复校验
- 商家审核：audit 通过/拒绝、状态流转
- 信用分管理：updateCredit 扣分逻辑
- 核销档位：updateWriteOffLevel 档位计算
- 地址管理：saveStoreAddress 每日3次限制、主地址逻辑
- 处罚管理：penalize 5级处罚、信用分清零
- 信息查询：getMerchantInfo/listMerchants 分页查询

**P1 - JwtUtil（32行待覆盖）**
- Token生成：generateToken 正常流程
- Token验证：validateToken 过期/篡改
- 密码校验：BCrypt编解码

### 2.4 lsc-order-service（行覆盖率 31.5%）

**问题概述**：OrderServiceImpl 仅 31.5%，187行未覆盖，11个方法未覆盖。

| 方法 | 覆盖状态 | 优先级 |
|------|---------|--------|
| createOrder | ✅ 部分 | - |
| payOrder | ✅ 部分 | - |
| completeOrder | ✅ 部分 | - |
| cancelOrder | ✅ 部分 | - |
| **refundOrder** | 🔴 未覆盖 | **P0** |
| **partialRefund** | 🔴 未覆盖 | **P0** |
| **listOrders(扩展)** | 🔴 未覆盖 | **P1** |
| **statsToday** | 🔴 未覆盖 | **P1** |
| **getByOrderNo** | 🔴 未覆盖 | **P1** |
| **LSC支付分支** | 🔴 未覆盖 | **P0** |
| **分布式锁分支** | 🔴 未覆盖 | **P0** |

#### 改进建议

**P0 - 核心业务分支**
- refundOrder：全额退款流程、LSC退回、异常回滚
- partialRefund：部分退款、累计退金额、边界校验
- payOrder：LSC+人民币混合支付、账本Feign调用
- 分布式锁：lock获取成功/失败/释放、并发支付

**P1 - 查询与统计**
- listOrders：多条件过滤、分页、日期范围
- statsToday：今日订单统计、收入计算、待处理统计
- getByOrderNo：空值容错、历史数据兼容

### 2.5 lsc-ai-gateway（行覆盖率 59.0%）

**问题概述**：7个AI子服务实现类和2个调用器覆盖率为 0%。

| 类名 | 覆盖率 | 未覆盖行 | 业务重要性 |
|------|--------|---------|-----------|
| AiCircuitBreakerManager | 95.0% | 5 | ✅ 已覆盖 |
| LocalRuleEngine | 100.0% | 0 | ✅ 已覆盖 |
| AiProductReviewServiceImpl | 100.0% | 0 | ✅ 已覆盖 |
| AiRecommendServiceImpl | 100.0% | 0 | ✅ 已覆盖 |
| AiRiskControlServiceImpl | 100.0% | 0 | ✅ 已覆盖 |
| **AiB2bVerifyServiceImpl** | **0.0%** | **27** | 🔴 B2B验证 |
| **AiAddressVerifyServiceImpl** | **0.0%** | **26** | 🔴 地址验证 |
| **AiParamSimulationServiceImpl** | **0.0%** | **29** | 🔴 参数仿真 |
| **AiCustomerServiceServiceImpl** | **0.0%** | **26** | 🟡 客服服务 |
| **AiReleasePredictServiceImpl** | **0.0%** | **32** | 🔴 释放预测 |
| **AiMerchantProfileServiceImpl** | **0.0%** | **26** | 🔴 商家画像 |
| AiModelInvoker | 0.0% | 1 | 🟢 接口 |
| StubAiModelInvoker | 0.0% | 6 | 🟢 桩实现 |

#### 改进建议

**P0 - 业务核心AI服务**
- AiB2bVerifyServiceImpl：B2B交易验证、风控评分
- AiAddressVerifyServiceImpl：地址真实性验证、多地址检测
- AiReleasePredictServiceImpl：释放率预测、趋势分析
- AiMerchantProfileServiceImpl：商家画像、风险等级

**P1 - 辅助AI服务**
- AiParamSimulationServiceImpl：参数模拟、场景回放
- AiCustomerServiceServiceImpl：客服自动应答、工单分类

**P2 - 基础组件**
- StubAiModelInvoker：桩实现验证

### 2.6 lsc-mall-service（行覆盖率 68.2%）

**问题概述**：ProductServiceImpl 仅 64.2%，HybridPayServiceImpl 达到 90%。

| 类名 | 覆盖率 | 未覆盖行 | 缺失场景 |
|------|--------|---------|---------|
| HybridPayServiceImpl | 90.0% | 2 | LSC不足降级 |
| **ProductServiceImpl** | **64.2%** | **39/109** | 商品管理分支 |

#### ProductServiceImpl 改进建议
- 商品状态变更：上架/下架逻辑
- 库存管理：扣减/回滚、并发库存
- 分类管理：树形结构、级联操作
- 搜索逻辑：关键词、价格区间、排序

---

## 三、分阶段改进计划

### 第一阶段：核心业务补测（目标：行覆盖率 → 60%）

**周期**：1-2周
**重点**：OrderServiceImpl、MerchantServiceImpl、ReleaseConfigServiceImpl

| 序号 | 任务 | 模块 | 预估用例数 | 预估提升 |
|------|------|------|-----------|---------|
| 1 | OrderServiceImpl退款/锁分支补测 | lsc-order-service | 8-10 | +25% |
| 2 | MerchantServiceImpl全方法补测 | lsc-user-service | 12-15 | +65% |
| 3 | ReleaseConfigServiceImpl全方法补测 | lsc-release-service | 10-12 | +40% |
| 4 | BatchReleaseServiceImpl核心流程补测 | lsc-release-service | 8-10 | +35% |

### 第二阶段：AI服务与基础设施（目标：行覆盖率 → 75%）

**周期**：2-3周
**重点**：AI子服务、lsc-common基础设施

| 序号 | 任务 | 模块 | 预估用例数 | 预估提升 |
|------|------|------|-----------|---------|
| 5 | AI核心服务补测（4个ServiceImpl） | lsc-ai-gateway | 20-25 | +30% |
| 6 | AI辅助服务补测（2个ServiceImpl） | lsc-ai-gateway | 10-12 | +15% |
| 7 | lsc-common P1类补测 | lsc-common | 12-15 | +15% |
| 8 | lsc-common P2类补测 | lsc-common | 15-20 | +20% |

### 第三阶段：边界与优化（目标：行覆盖率 → 85%）

**周期**：2-3周
**重点**：边界场景、异常路径、性能优化

| 序号 | 任务 | 模块 | 预估用例数 | 预估提升 |
|------|------|------|-----------|---------|
| 9 | ProductServiceImpl边界补测 | lsc-mall-service | 8-10 | +15% |
| 10 | ReleaseJobHandler定时任务补测 | lsc-release-service | 5-8 | +8% |
| 11 | lsc-common剩余类补测 | lsc-common | 10-15 | +10% |
| 12 | 全模块边界场景覆盖 | 全部 | 20-30 | +5% |

---

## 四、测试用例规范

### 4.1 单元测试模板

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("{模块名} - {类名} 单元测试")
class XxxServiceImplTest {

    @Mock
    private XxxMapper xxxMapper;

    @InjectMocks
    private XxxServiceImpl xxxService;

    @Test
    @DisplayName("{方法名} - 正常场景")
    void methodName_success() {
        // 准备数据
        // 执行方法
        // 验证结果
    }

    @Test
    @DisplayName("{方法名} - 异常场景：{异常描述}")
    void methodName_exception() {
        // 准备异常数据
        assertThrows(BizException.class, () -> service.method(...));
    }

    @Test
    @DisplayName("{方法名} - 边界场景：{边界描述}")
    void methodName_boundary() {
        // 准备边界数据
        // 验证边界行为
    }
}
```

### 4.2 覆盖率提升策略

1. **核心方法优先**：先覆盖 public 核心业务方法，再覆盖 private 辅助方法
2. **分支覆盖优先**：优先覆盖 if/else 分支，确保逻辑路径完整
3. **异常路径必测**：每个 public 方法至少覆盖 1 个异常路径
4. **边界值覆盖**：null、空集合、零值、最大值等边界情况
5. **Mock 最小化**：只 Mock 外部依赖的必要行为，避免过度 Mock

### 4.3 质量门禁

| 指标 | 要求 | 实施方式 |
|------|------|---------|
| 行覆盖率 | ≥ 70% | JaCoCo 报告自动检查 |
| 分支覆盖率 | ≥ 50% | JaCoCo 报告自动检查 |
| 测试通过率 | 100% | CI 流水线拦截 |
| 新增用例 | 每个 PR ≥ 5 个 | Code Review 检查 |

---

## 五、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| lsc-common 依赖复杂 | Mock 难度高 | 使用 @Spy 或构造器注入替代 Mock |
| AI 服务依赖外部 | 测试不稳定 | 使用 StubAiModelInvoker 桩实现 |
| 分布式锁测试 | 并发场景难测 | 使用 ShardedLockUtil 进行单元级锁测试 |
| 测试代码膨胀 | 维护成本高 | 遵循单一职责，每个用例只测一个场景 |
| 覆盖率虚高 | 质量不达标 | 关注分支覆盖率和突变测试 |

---

## 六、预期成果

### 6.1 覆盖率提升预测

| 阶段 | 行覆盖率 | 分支覆盖率 | 测试用例总数 |
|------|---------|-----------|-------------|
| 当前 | 44.5% | 36.8% | 355 |
| 第一阶段结束 | ~60% | ~50% | ~420 |
| 第二阶段结束 | ~75% | ~65% | ~480 |
| 第三阶段结束 | ~85% | ~75% | ~530 |

### 6.2 交付物清单

1. ✅ JaCoCo 覆盖率报告（当前）
2. 📋 分阶段测试用例补充
3. 📋 最终覆盖率报告
4. 📋 测试规范文档
5. 📋 CI 流水线覆盖率门禁

---

## 附录：详细覆盖率数据

### A. lsc-common 类级详情

| 类 | 行% | 分支% | 方法% | 未覆盖行 |
|----|-----|-------|-------|---------|
| SnowflakeIdUtil.Holder | 100.0 | - | 100.0 | 0 |
| ShardedLockUtil | 86.4 | 100.0 | 66.7 | 3 |
| OptimisticLockHelper | 82.4 | 100.0 | 100.0 | 3 |
| SnowflakeIdUtil | 58.3 | 50.0 | 40.0 | 15 |
| EvidenceHashUtil | 40.0 | 41.7 | 50.0 | 30 |
| 其余25个类 | 0.0 | 0.0 | 0.0 | 全部 |

### B. lsc-release-service 类级详情

| 类 | 行% | 未覆盖行 |
|----|-----|---------|
| ReleaseCalcServiceImpl | 93.3 | 3 |
| BatchReleaseServiceImpl | 0.0 | 144 |
| ReleaseConfigServiceImpl | 0.0 | 94 |
| ReleaseJobHandler | 0.0 | 87 |
| 其余3个类 | 0.0 | 11 |

### C. lsc-user-service 类级详情

| 类 | 行% | 未覆盖行 |
|----|-----|---------|
| UserServiceImpl | 86.0 | 13 |
| MerchantServiceImpl | 0.0 | 262 |
| JwtUtil | 0.0 | 32 |

### D. lsc-order-service 类级详情

| 类 | 行% | 分支% | 方法% | 未覆盖行 |
|----|-----|-------|-------|---------|
| OrderServiceImpl | 31.5 | 24.0 | 38.9 | 187 |

---

*报告由 JaCoCo 自动生成数据 + 人工分析汇总*
