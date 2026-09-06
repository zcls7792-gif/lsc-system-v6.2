# 链盛通 LSC 消费权益凭证平台 · 后端接口文档

> **版本**：V6.2-AI  
> **日期**：2026-09-06  
> **技术栈**：Java 17 + Spring Boot 3 + Spring Cloud + MySQL 8.0 + Redis 7.0 + RabbitMQ + Seata AT  
> **接口前缀**：`/api/v1`  
> **鉴权方式**：JWT Bearer Token（管理端 `Authorization: Bearer <admin_token>`，用户端 `Authorization: Bearer <user_token>`）  
> **数据格式**：JSON（UTF-8）  
> **限流标准**：单 IP 200 QPS；核心账务接口 50 QPS；AI 接口 20 QPS

---

## 目录

1. [通用约定](#1-通用约定)
2. [错误码体系](#2-错误码体系)
3. [用户服务 API](#3-用户服务-api)
4. [LSC 账本服务 API](#4-lsc-账本服务-api)
5. [权益商城服务 API](#5-权益商城服务-api)
6. [订单服务 API](#6-订单服务-api)
7. [核销服务 API](#7-核销服务-api)
8. [B2B 交易服务 API](#8-b2b-交易服务-api)
9. [释放计算服务 API](#9-释放计算服务-api)
10. [风控服务 API](#10-风控服务-api)
11. [管理后台 API](#11-管理后台-api)
12. [AI 网关服务 API](#12-ai-网关服务-api)
13. [存证服务 API](#13-存证服务-api)

---

## 1. 通用约定

### 1.1 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1757126400000,
  "traceId": "a1b2c3d4e5f6"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 0=成功，非0=失败（见错误码） |
| message | string | 提示信息 |
| data | object/array/null | 业务数据 |
| timestamp | long | 服务器时间戳（毫秒） |
| traceId | string | 链路追踪 ID（用于问题排查） |

### 1.2 分页参数

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| pageNo | int | 1 | 页码，从 1 开始 |
| pageSize | int | 20 | 每页条数，最大 100 |

分页响应：
```json
{
  "code": 0,
  "data": {
    "list": [],
    "total": 100,
    "pageNo": 1,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

### 1.3 鉴权说明

| 端 | Token 类型 | 有效期 | 获取方式 |
|---|---|---|---|
| 平台管理后台 | admin_token | 2 小时 | `POST /api/v1/admin/auth/login` |
| 商家管理后台 | merchant_token | 2 小时 | `POST /api/v1/merchant/auth/login` |
| 消费者 App/小程序 | user_token | 7 天 | `POST /api/v1/user/auth/login` |

Token 失效返回 `code=401`，前端需跳转登录页。

### 1.4 幂等键约定

所有写操作（支付、核销、LSC 流转）必须携带 `idempotentKey`（UUID），服务端唯一索引去重，重复请求返回首次结果。

---

## 2. 错误码体系

| code | HTTP | message | 说明 |
|---|---|---|---|
| 0 | 200 | success | 成功 |
| 400 | 400 | 参数错误 | 请求参数校验失败 |
| 401 | 401 | 未登录或 Token 失效 | 需重新登录 |
| 403 | 403 | 无权限 | 角色权限不足 |
| 404 | 404 | 资源不存在 | |
| 429 | 429 | 请求过于频繁 | 触发限流 |
| 500 | 500 | 服务器内部错误 | |
| 1001 | 400 | LSC 余额不足 | 可用 LSC 不够 |
| 1002 | 400 | LSC 流转权限不足 | 违反流转矩阵（如消→消、商→消） |
| 1003 | 400 | 核销资格未满足 | 商家三证不齐 |
| 1004 | 400 | 今日核销额度已用完 | 超过档位日上限 |
| 1005 | 400 | 今日已核销过 | 每日限 1 次 |
| 1006 | 400 | B2B 订单未确认 | 对手方未确认 |
| 1007 | 400 | B2B 贸易背景核验未通过 | AI 判定虚假 |
| 1008 | 400 | 释放速率越界 | rate 不在 0.03%~0.06% |
| 1009 | 400 | 商品价格 1:1 校验失败 | 人民币价 ≠ LSC 价 |
| 1010 | 400 | 核销率 k 参数越界 | 修改参数超出安全范围 |
| 2001 | 400 | 风控拦截 | 命中风控规则 |

---

## 3. 用户服务 API

### 3.1 消费者注册

`POST /api/v1/user/auth/register`

**请求体**：
```json
{
  "mobile": "13800138000",
  "smsCode": "123456",
  "password": "xxx",
  "referrerId": 10001
}
```

**响应 data**：
```json
{
  "userId": 20001,
  "userType": 0,
  "token": "eyJhbGciOi..."
}
```

> userType: 0=消费者会员, 1=商家会员

### 3.2 消费者登录

`POST /api/v1/user/auth/login`

**请求体**：
```json
{
  "mobile": "13800138000",
  "password": "xxx",
  "loginType": "password"
}
```

**响应 data**：
```json
{
  "userId": 20001,
  "userType": 0,
  "token": "eyJhbGciOi..."
}
```

### 3.3 商家注册（资质提交）

`POST /api/v1/merchant/auth/register`

**请求体**（multipart/form-data）：
```
mobile: 13900139000
smsCode: 123456
password: xxx
storeName: 优选生鲜
businessLicense: (营业执照图片文件)
corporateAccountNo: 6222021234567890
regulatoryAgreementSigned: true
province: 上海市
city: 上海市
district: 浦东新区
addressDetail: 世纪大道 100 号
longitude: 121.506377
latitude: 31.245105
contactPhone: 021-12345678
businessHours: 08:00-22:00
```

**响应 data**：
```json
{
  "merchantId": 30001,
  "userType": 1,
  "auditStatus": 0,
  "token": "eyJhbGciOi..."
}
```

> auditStatus: 0=审核中, 1=通过, 2=驳回

### 3.4 实名认证

`POST /api/v1/user/verify`

**请求体**：
```json
{
  "realName": "张三",
  "idCard": "310115199001011234"
}
```

### 3.5 推荐关系绑定

`POST /api/v1/user/bind-referrer`

**请求体**：
```json
{
  "referrerId": 10001
}
```

> 推荐关系严格限定为一级直推，不可更改。

---

## 4. LSC 账本服务 API

### 4.1 查询 LSC 账户

`GET /api/v1/lsc/account`

**响应 data**：
```json
{
  "userId": 20001,
  "totalLocked": 1256800,
  "totalAvailable": 86420,
  "todayReleased": 565,
  "releaseRate": "0.045%",
  "expiringCount": 1280
}
```

### 4.2 LSC 流水查询

`GET /api/v1/lsc/transactions?pageNo=1&pageSize=20&type=1`

**参数**：
| 参数 | 类型 | 说明 |
|---|---|---|
| type | int | 流水类型：1消费发行 2每日释放 3推广奖励 4商城消费 5线下消费 6过期转回 7商家核销 8B2B流转 9退款退回 |

**响应 data**：
```json
{
  "list": [
    {
      "id": 9001,
      "type": 1,
      "typeName": "消费发行",
      "amount": 98,
      "beforeLocked": 1256702,
      "afterLocked": 1256800,
      "beforeAvailable": 86420,
      "afterAvailable": 86420,
      "counterpartyId": 30001,
      "orderNo": "ORD202609060001",
      "createdAt": "2026-09-06 10:30:00"
    }
  ],
  "total": 156
}
```

### 4.3 可用 LSC 明细（带过期时间）

`GET /api/v1/lsc/available-details`

**响应 data**：
```json
{
  "list": [
    {
      "id": 7001,
      "amount": 500,
      "sourceType": "DAILY_RELEASE",
      "expireDate": "2027-09-05",
      "daysLeft": 364
    }
  ]
}
```

> 可用 LSC 有效期 365 天，到期自动转回锁定池。

---

## 5. 权益商城服务 API

### 5.1 商品列表

`GET /api/v1/mall/products?pageNo=1&pageSize=20&categoryId=1&keyword=车厘子`

**响应 data**：
```json
{
  "list": [
    {
      "id": 5001,
      "productName": "智利车厘子 JJ级 2斤装",
      "price": 98.00,
      "lscPrice": 98,
      "stock": 580,
      "sales": 23000,
      "productImages": ["https://cdn.lsc.com/p1.jpg"],
      "merchantId": 30001,
      "storeName": "优选生鲜",
      "status": 1
    }
  ],
  "total": 8642
}
```

> price（人民币）与 lscPrice 数值严格一致，由系统强制校验。

### 5.2 商品详情

`GET /api/v1/mall/products/{id}`

**响应 data**：
```json
{
  "id": 5001,
  "productName": "智利车厘子 JJ级 2斤装",
  "productDesc": "智利进口 JJ级 2斤装 顺丰冷链包邮",
  "price": 98.00,
  "lscPrice": 98,
  "stock": 580,
  "sales": 23000,
  "productImages": ["https://cdn.lsc.com/p1.jpg", "https://cdn.lsc.com/p2.jpg"],
  "videoUrl": "https://cdn.lsc.com/v1.mp4",
  "videoCoverUrl": "https://cdn.lsc.com/v1-cover.jpg",
  "merchantId": 30001,
  "storeName": "优选生鲜",
  "storeRating": 4.8,
  "address": "上海市浦东新区世纪大道100号",
  "longitude": 121.506377,
  "latitude": 31.245105
}
```

### 5.3 商家发布商品

`POST /api/v1/mall/products`（商家端）

**请求体**（multipart/form-data）：
```
productName: 智利车厘子 JJ级 2斤装
productDesc: 智利进口 JJ级
price: 98.00
stock: 580
categoryId: 1
productImages: (图片文件1, 图片文件2)
video: (视频文件，选填)
```

> AI 审核模块自动初审，可疑内容推送人工复核。

### 5.4 AI 商品审核结果回调（内部）

`POST /api/v1/internal/mall/ai-review-callback`

**请求体**：
```json
{
  "productId": 5001,
  "aiResult": 1,
  "aiTags": "清晰图片,无违规",
  "aiScore": 0.95
}
```

> aiResult: 0=AI通过, 1=AI可疑, 2=人工通过, 3=人工拒绝

### 5.5 商品上下架

`PUT /api/v1/mall/products/{id}/status`（商家端）

**请求体**：
```json
{
  "status": 0
}
```

> status: 0=下架, 1=上架, 2=审核中

---

## 6. 订单服务 API

### 6.1 创建商城订单（支持混合支付）

`POST /api/v1/orders`

**请求体**：
```json
{
  "items": [
    {
      "productId": 5001,
      "quantity": 1
    }
  ],
  "lscAmount": 50,
  "addressId": 8001,
  "idempotentKey": "uuid-xxx"
}
```

**响应 data**：
```json
{
  "orderNo": "ORD202609060001",
  "totalPrice": 98.00,
  "lscAmount": 50,
  "rmbAmount": 48.00,
  "status": 0,
  "payUrl": "https://pay.lsc.com/cashier?orderNo=ORD202609060001"
}
```

> 系统校验可用 LSC 余额，LSC 按 1:1 抵扣，人民币差额唤起支付机构收银台。

### 6.2 线下扫码消费

`POST /api/v1/orders/offline`

**请求体**：
```json
{
  "merchantId": 30001,
  "amount": 98.00,
  "lscAmount": 50,
  "idempotentKey": "uuid-xxx"
}
```

### 6.3 订单列表

`GET /api/v1/orders?pageNo=1&pageSize=20&status=0`

**响应 data**：
```json
{
  "list": [
    {
      "orderNo": "ORD202609060001",
      "orderType": 0,
      "productName": "智利车厘子 JJ级 2斤装",
      "totalPrice": 98.00,
      "lscAmount": 50,
      "rmbAmount": 48.00,
      "status": 1,
      "statusName": "已支付",
      "createdAt": "2026-09-06 10:30:00"
    }
  ],
  "total": 28
}
```

> status: 0=待支付, 1=已支付, 2=已完成, 3=已取消, 4=已退款, 5=部分退款

### 6.4 订单详情

`GET /api/v1/orders/{orderNo}`

### 6.5 申请退款

`POST /api/v1/orders/{orderNo}/refund`

**请求体**：
```json
{
  "refundType": "full",
  "reason": "商品质量问题",
  "idempotentKey": "uuid-xxx"
}
```

> 全额退款：人民币原路退回，LSC 退回消费者可用余额，触发 LSC 发行回滚。  
> 部分退款：LSC 和人民币按比例退回。

---

## 7. 核销服务 API

### 7.1 查询核销资格与额度

`GET /api/v1/nh/quota`（商家端）

**响应 data**：
```json
{
  "merchantId": 30001,
  "qualified": true,
  "businessLicenseVerified": true,
  "corporateAccountBound": true,
  "regulatoryAgreementSigned": true,
  "level": "F",
  "monthlyRevenue": 1280000.00,
  "dailyLimit": 2750,
  "usedToday": 1730,
  "remainingToday": 1020,
  "lastNhDate": "2026-09-06",
  "creditScore": 100
}
```

> 额度档位（A-Z）根据上月营业额每月 1 日自动更新。

### 7.2 发起核销

`POST /api/v1/nh/apply`（商家端）

**请求体**：
```json
{
  "lscAmount": 1020,
  "idempotentKey": "uuid-xxx"
}
```

**响应 data**：
```json
{
  "orderNo": "NH202609060001",
  "lscAmount": 1020,
  "cashAmount": 887.40,
  "status": 2
}
```

> 校验流程：资格 → 限额 → 余额 → 调用支付机构划拨 N×0.87 元至主账户 → 原子化扣减并销毁 LSC。  
> 采用唯一订单号 + 版本号乐观锁双重幂等校验。

### 7.3 核销记录

`GET /api/v1/nh/records?pageNo=1&pageSize=20`

**响应 data**：
```json
{
  "list": [
    {
      "id": 6001,
      "orderNo": "NH202609060001",
      "lscAmount": 1020,
      "cashAmount": 887.40,
      "status": 2,
      "statusName": "成功",
      "createdAt": "2026-09-06 09:15:00",
      "completedAt": "2026-09-06 09:15:01"
    }
  ],
  "total": 86
}
```

---

## 8. B2B 交易服务 API

### 8.1 创建 B2B 订单（商家间 LSC 流转）

`POST /api/v1/b2b/orders`（商家端）

**请求体**：
```json
{
  "counterpartyId": 30002,
  "tradeDescription": "采购有机蔬菜 100 箱",
  "totalAmountRmb": 12800.00,
  "lscAmount": 12800,
  "contractNo": "HT20260906001",
  "tradeEvidenceUrls": ["https://cdn.lsc.com/contract1.jpg", "https://cdn.lsc.com/invoice1.jpg"],
  "idempotentKey": "uuid-xxx"
}
```

> 必须绑定真实贸易订单，严禁空流转。

### 8.2 B2B 订单确认（对手方）

`PUT /api/v1/b2b/orders/{orderNo}/confirm`

**请求体**：
```json
{
  "confirmed": true,
  "confirmedBy": "user-30002"
}
```

> 确认后 LSC 账本服务在原子事务中执行流转，按 1:1 价值锚定，转入方 LSC 有效期重置为 365 天。

### 8.3 B2B AI 核验结果回调（内部）

`POST /api/v1/internal/b2b/ai-verify-callback`

**请求体**：
```json
{
  "orderId": 9001,
  "aiResult": 0,
  "aiScore": 0.92,
  "extractedInfo": {
    "contractAmount": 12800.00,
    "invoiceMatch": true,
    "logisticsMatch": true
  }
}
```

> OCR Agent 自动提取合同/送货单/物流/发票信息与订单字段比对。  
> aiResult: 0=AI判定真实, 1=AI判定可疑, 2=人工确认真实, 3=人工确认虚假

### 8.4 B2B 订单列表

`GET /api/v1/b2b/orders?pageNo=1&pageSize=20&status=1`

**响应 data**：
```json
{
  "list": [
    {
      "id": 9001,
      "orderNo": "B2B20260906001",
      "counterpartyName": "绿叶批发",
      "tradeDescription": "采购有机蔬菜 100 箱",
      "lscAmount": 12800,
      "aiVerificationResult": 0,
      "status": 2,
      "statusName": "已流转",
      "createdAt": "2026-09-06 09:00:00"
    }
  ],
  "total": 42
}
```

> status: 0=待确认, 1=已确认, 2=已流转, 3=已完成, 4=已取消, 5=已作废

---

## 9. 释放计算服务 API

### 9.1 每日释放汇总（管理后台）

`GET /api/v1/release/summary?date=2026-09-06`

**响应 data**：
```json
{
  "date": "2026-09-06",
  "mTotal": 1800000.00,
  "nTotal": 7560.00,
  "k": 0.0042,
  "rate": 0.00045,
  "rateDisplay": "0.045%",
  "lLocked": 1256800000,
  "tRelease": 565560,
  "batchCount": 13,
  "failedBatchCount": 0,
  "aiPredictedK7d": 0.0041,
  "aiPredictedK30d": 0.0043,
  "status": 1,
  "statusName": "成功"
}
```

### 9.2 释放参数配置（管理后台）

`GET /api/v1/release/config`

**响应 data**：
```json
{
  "rateMax": "0.06%",
  "rateMaxEditable": false,
  "rateMin": "0.03%",
  "rateMinEditable": false,
  "kMin": "0.50%",
  "kMinEditable": true,
  "kMax": "1.0%",
  "kMaxEditable": true,
  "alpha": "0.06",
  "alphaEditable": true
}
```

> rate_max 和 rate_min 为硬常量，编译后不可修改（editable=false）。

### 9.3 修改释放参数（双重签名审批）

`PUT /api/v1/release/config`（超级管理员）

**请求体**：
```json
{
  "kMin": "0.45%",
  "kMax": "0.95%",
  "alpha": "0.065",
  "approver1": "admin-001",
  "approver2": "admin-002",
  "signature1": "sig-xxx",
  "signature2": "sig-yyy"
}
```

> 修改需双重管理员签名审批 + 链上存证。rate_max/rate_min 不允许修改。

### 9.4 AI 释放趋势预测

`GET /api/v1/release/ai-prediction?days=7`

**响应 data**：
```json
{
  "predictedK": [0.0041, 0.0042, 0.0043, 0.0041, 0.0040, 0.0042, 0.0043],
  "predictedRate": ["0.046%", "0.045%", "0.044%", "0.046%", "0.046%", "0.045%", "0.044%"],
  "confidence": 0.87
}
```

> AI 仅在 0.03%~0.06% 硬约束区间内输出趋势预判。

---

## 10. 风控服务 API

### 10.1 风控规则检测（内部中间件）

`POST /api/v1/internal/risk/check`

**请求体**：
```json
{
  "userId": 20001,
  "scene": "CREATE_ORDER",
  "context": {
    "orderAmount": 98.00,
    "lscRatio": 0.51,
    "ip": "203.0.113.1",
    "deviceId": "device-xxx"
  }
}
```

**响应 data**：
```json
{
  "passed": true,
  "riskLevel": "LOW",
  "riskScore": 12,
  "triggeredRules": []
}
```

> 固定规则：1小时下单>10笔、连续3笔LSC比例>90%、同商品买超5次、1小时3+城市IP登录等。

### 10.2 风控预警列表（管理后台）

`GET /api/v1/admin/risk/alerts?pageNo=1&pageSize=20`

**响应 data**：
```json
{
  "list": [
    {
      "id": 11001,
      "userId": 10086,
      "riskType": "BATCH_ORDER",
      "riskDetail": "1小时内下单12笔",
      "aiRiskLevel": "HIGH",
      "aiRiskScore": 88,
      "actionTaken": "TEMP_LIMIT",
      "createdAt": "2026-09-06 14:20:00"
    }
  ],
  "total": 35
}
```

### 10.3 商家风控评分

`GET /api/v1/admin/risk/merchant-score/{merchantId}`

**响应 data**：
```json
{
  "merchantId": 30001,
  "creditScore": 100,
  "aiRiskScore": 15,
  "riskLevel": "LOW",
  "nhLimitMultiplier": 1.0
}
```

> 信用分 80-100 核销限额 100%；60-79 降至 50%；40-59 暂停核销；<20 永久关闭。

---

## 11. 管理后台 API

### 11.1 管理员登录

`POST /api/v1/admin/auth/login`

**请求体**：
```json
{
  "username": "admin",
  "password": "xxx",
  "captcha": "ab12"
}
```

### 11.2 数据总览

`GET /api/v1/admin/dashboard`

**响应 data**：
```json
{
  "totalLsc": 12860000,
  "regulatoryPool": 1800000.00,
  "todayReleaseRate": "0.045%",
  "platformFeeRate": "2%",
  "todayGmv": 1286000.00,
  "todayOrders": 8642,
  "totalMerchants": 1256,
  "todayNhCount": 2860,
  "todayNhAmount": 248000.00,
  "kValue": 0.0042
}
```

### 11.3 商家审核列表

`GET /api/v1/admin/merchants?auditStatus=0&pageNo=1&pageSize=20`

### 11.4 商家审核通过/驳回

`PUT /api/v1/admin/merchants/{merchantId}/audit`

**请求体**：
```json
{
  "auditStatus": 1,
  "rejectReason": ""
}
```

### 11.5 商品审核列表

`GET /api/v1/admin/products?aiReviewResult=1&pageNo=1&pageSize=20`

### 11.6 商品人工审核

`PUT /api/v1/admin/products/{id}/review`

**请求体**：
```json
{
  "aiReviewResult": 2,
  "rejectReason": ""
}
```

> aiReviewResult: 2=人工通过, 3=人工拒绝

### 11.7 B2B 订单 AI 核验复核

`GET /api/v1/admin/b2b/orders?aiVerificationResult=1`

`PUT /api/v1/admin/b2b/orders/{orderNo}/verify`

**请求体**：
```json
{
  "aiVerificationResult": 2
}
```

### 11.8 核销档位管理

`GET /api/v1/admin/nh/levels`

**响应 data**：
```json
{
  "levels": [
    {"level": "A", "minRevenue": 100000, "dailyLimit": 275},
    {"level": "F", "minRevenue": 1000000, "dailyLimit": 2750},
    {"level": "K", "minRevenue": 2000000, "dailyLimit": 5500},
    {"level": "Z", "minRevenue": 20000000, "dailyLimit": 55000}
  ]
}
```

### 11.9 违规处罚执行

`POST /api/v1/admin/merchants/{merchantId}/penalty`

**请求体**：
```json
{
  "violationType": "FALSE_ADDRESS",
  "violationDesc": "虚假填写线下经营地址",
  "creditDeduct": 20,
  "penaltyAction": "LEVEL_1"
}
```

> penaltyAction: NORMAL, LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4（清退）

### 11.10 管理员操作审计日志

`GET /api/v1/admin/audit-logs?pageNo=1&pageSize=20`

---

## 12. AI 网关服务 API

### 12.1 商品图片审核

`POST /api/v1/ai/review/product-image`

**请求体**：
```json
{
  "imageUrl": "https://cdn.lsc.com/p1.jpg",
  "productId": 5001
}
```

**响应 data**：
```json
{
  "aiResult": 0,
  "aiScore": 0.95,
  "tags": ["清晰", "真实产品图", "无违规"],
  "suggestion": ""
}
```

### 12.2 商品文本审核（NLP）

`POST /api/v1/ai/review/product-text`

**请求体**：
```json
{
  "text": "有机蔬菜礼盒 8种时令蔬菜",
  "productId": 5001
}
```

> 禁止金融投资类字眼。

### 12.3 B2B 贸易背景 OCR 核验

`POST /api/v1/ai/verify/b2b-trade`

**请求体**：
```json
{
  "orderId": 9001,
  "evidenceUrls": ["https://cdn.lsc.com/contract1.jpg"],
  "orderFields": {
    "totalAmountRmb": 12800.00,
    "tradeDescription": "采购有机蔬菜 100 箱"
  }
}
```

### 12.4 AI 客服（消费者 RAG）

`POST /api/v1/ai/cs/consumer`

**请求体**：
```json
{
  "userId": 20001,
  "question": "LSC 怎么核销？"
}
```

**响应 data**：
```json
{
  "answer": "核销仅限商家会员操作，消费者会员无核销资格...",
  "sources": ["用户手册-第3章", "FAQ-LSC003"]
}
```

### 12.5 管理员 NL2SQL 查询

`POST /api/v1/ai/admin/nl2sql`

**请求体**：
```json
{
  "adminId": 1,
  "question": "上月核销金额最高的 10 个商家"
}
```

---

## 13. 存证服务 API

### 13.1 查询每日快照存证

`GET /api/v1/evidence/daily-snapshot?date=2026-09-06`

**响应 data**：
```json
{
  "snapshotDate": "2026-09-06",
  "dataHash": "0xabc123...",
  "txId": "0xtx456...",
  "createdAt": "2026-09-06 02:00:00",
  "chainStatus": "CONFIRMED"
}
```

### 13.2 查询操作存证

`GET /api/v1/evidence/operations?batchNo=BATCH20260906001`

### 13.3 参数变更存证

`GET /api/v1/evidence/param-changes?configKey=k_min`

---

## 附录 A：LSC 流水类型枚举

| type | 名称 | 方向 |
|---|---|---|
| 1 | 消费发行 | 锁定 + |
| 2 | 每日释放 | 锁定 - → 可用 + |
| 3 | 推广奖励释放 | 可用 + |
| 4 | 权益商城消费 | 可用 - |
| 5 | 线下消费 | 可用 - |
| 6 | 过期转回 | 可用 - → 锁定 + |
| 7 | 商家核销 | 可用 -（销毁） |
| 8 | B2B 流转支付 | 可用 - / 对手可用 + |
| 9 | 退款退回 | 可用 + |

## 附录 B：分账规则速查

```
消费 ¥100
├── ¥84 → 商家主账户
├── ¥14 → 商家专属监管账户（唯一出口：商家核销）
└── ¥2  → 平台技术服务费（唯一营收）

商家核销 100 LSC
├── ¥87 → 商家主账户（落袋）
└── ¥13 → 监管账户留存（继续锚定其他可用 LSC）
```

## 附录 C：接口总览

| 服务 | 接口数 | 核心能力 |
|---|---|---|
| 用户服务 | 5 | 注册、登录、实名、推荐绑定 |
| LSC 账本 | 3 | 账户、流水、可用明细 |
| 权益商城 | 5 | 商品 CRUD、AI 审核回调 |
| 订单服务 | 5 | 商城订单、线下消费、退款 |
| 核销服务 | 3 | 额度、核销、记录 |
| B2B 交易 | 4 | 创建、确认、AI 核验、列表 |
| 释放计算 | 4 | 汇总、配置、修改、AI 预测 |
| 风控服务 | 3 | 检测、预警、商家评分 |
| 管理后台 | 10 | 总览、审核、档位、处罚、审计 |
| AI 网关 | 5 | 图/文审核、OCR、客服、NL2SQL |
| 存证服务 | 3 | 快照、操作、参数变更 |
| **合计** | **50** | |

---

*文档结束 · 链盛通 LSC 平台研发团队*
