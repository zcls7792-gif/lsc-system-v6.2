# 链盛通 LSC 平台 · 前端对接示例代码

> **版本**：V6.2-AI  
> **配套文档**：[链盛通LSC平台-后端接口文档.md](./链盛通LSC平台-后端接口文档.md)  
> **Base URL**：`https://api.lsc.com/api/v1`（生产）/ `https://api-test.lsc.com/api/v1`（测试）  
> **技术示例**：Axios + TypeScript（Web/App）、wx.request（小程序）

---

## 目录

1. [通用 HTTP 客户端封装](#1-通用-http-客户端封装)
2. [鉴权与 Token 管理](#2-鉴权与-token-管理)
3. [平台管理后台示例](#3-平台管理后台示例)
4. [商家管理后台示例](#4-商家管理后台示例)
5. [消费者 App / 小程序示例](#5-消费者-app--小程序示例)
6. [TypeScript 类型定义](#6-typescript-类型定义)
7. [错误处理与重试](#7-错误处理与重试)
8. [文件上传示例](#8-文件上传示例)
9. [小程序特有适配](#9-小程序特有适配)
10. [联调注意事项](#10-联调注意事项)

---

## 1. 通用 HTTP 客户端封装

### 1.1 Axios 实例（Web / App）

```typescript
// src/api/request.ts
import axios, { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';

// 接口前缀统一加 /api/v1
const BASE_URL = import.meta.env.VITE_API_BASE || 'https://api.lsc.com/api/v1';

// 三端区分：admin / merchant / user
type TokenType = 'admin' | 'merchant' | 'user';

const tokenKeyMap: Record<TokenType, string> = {
  admin: 'lsc_admin_token',
  merchant: 'lsc_merchant_token',
  user: 'lsc_user_token',
};

function createClient(tokenType: TokenType): AxiosInstance {
  const client = axios.create({
    baseURL: BASE_URL,
    timeout: 15000,
    headers: { 'Content-Type': 'application/json' },
  });

  // 请求拦截：注入 Token
  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(tokenKeyMap[tokenType]);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // 幂等键：写操作自动生成
    if (config.method?.toUpperCase() !== 'GET') {
      config.headers['X-Idempotent-Key'] =
        (config.data as any)?.idempotentKey || crypto.randomUUID();
    }
    return config;
  });

  // 响应拦截：统一解构 + 错误处理
  client.interceptors.response.use(
    (resp) => {
      const { code, message, data } = resp.data;
      if (code === 0) return data;
      // 业务错误
      throw new ApiError(code, message);
    },
    (error) => {
      if (error.response?.status === 401) {
        localStorage.removeItem(tokenKeyMap[tokenType]);
        window.location.href = '/login';
        return Promise.reject(new ApiError(401, '登录已失效，请重新登录'));
      }
      if (error.code === 'ECONNABORTED') {
        return Promise.reject(new ApiError(-1, '请求超时，请稍后重试'));
      }
      return Promise.reject(error);
    }
  );

  return client;
}

export const adminClient = createClient('admin');
export const merchantClient = createClient('merchant');
export const userClient = createClient('user');

// 统一错误类
export class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}
```

### 1.2 封装后的请求方法

```typescript
// src/api/index.ts
import { adminClient, merchantClient, userClient } from './request';

export const adminApi = {
  get: <T = any>(url: string, params?: any) => adminClient.get<any, T>(url, { params }),
  post: <T = any>(url: string, data?: any) => adminClient.post<any, T>(url, data),
  put: <T = any>(url: string, data?: any) => adminClient.put<any, T>(url, data),
  delete: <T = any>(url: string) => adminClient.delete<any, T>(url),
};

export const merchantApi = {
  get: <T = any>(url: string, params?: any) => merchantClient.get<any, T>(url, { params }),
  post: <T = any>(url: string, data?: any) => merchantClient.post<any, T>(url, data),
  put: <T = any>(url: string, data?: any) => merchantClient.put<any, T>(url, data),
};

export const userApi = {
  get: <T = any>(url: string, params?: any) => userClient.get<any, T>(url, { params }),
  post: <T = any>(url: string, data?: any) => userClient.post<any, T>(url, data),
};
```

---

## 2. 鉴权与 Token 管理

### 2.1 登录流程（消费者）

```typescript
// src/api/auth.ts
import { userApi } from './index';

interface LoginReq {
  mobile: string;
  password: string;
  loginType: 'password' | 'sms';
  smsCode?: string;
}

interface LoginResp {
  userId: number;
  userType: number;  // 0=消费者, 1=商家
  token: string;
}

export async function login(req: LoginReq): Promise<LoginResp> {
  const data = await userApi.post<LoginResp>('/user/auth/login', req);
  // 存储 token 和用户信息
  localStorage.setItem('lsc_user_token', data.token);
  localStorage.setItem('lsc_user_id', String(data.userId));
  localStorage.setItem('lsc_user_type', String(data.userType));
  return data;
}

export function logout() {
  localStorage.removeItem('lsc_user_token');
  localStorage.removeItem('lsc_user_id');
  localStorage.removeItem('lsc_user_type');
  window.location.href = '/login';
}
```

### 2.2 商家登录

```typescript
import { merchantApi } from './index';

export async function merchantLogin(mobile: string, password: string) {
  const data = await merchantApi.post<{ merchantId: number; token: string; auditStatus: number }>(
    '/merchant/auth/login',
    { mobile, password, loginType: 'password' }
  );
  localStorage.setItem('lsc_merchant_token', data.token);
  localStorage.setItem('lsc_merchant_id', String(data.merchantId));
  // 审核中需跳转资质补充页
  if (data.auditStatus === 0) {
    window.location.href = '/merchant/qualification';
  }
  return data;
}
```

---

## 3. 平台管理后台示例

### 3.1 数据总览仪表盘

```typescript
// src/api/admin/dashboard.ts
import { adminApi } from '../index';

interface DashboardVO {
  totalLsc: number;
  regulatoryPool: number;
  todayReleaseRate: string;
  platformFeeRate: string;
  todayGmv: number;
  todayOrders: number;
  totalMerchants: number;
  todayNhCount: number;
  todayNhAmount: number;
  kValue: number;
}

export function getDashboard(): Promise<DashboardVO> {
  return adminApi.get<DashboardVO>('/admin/dashboard');
}
```

```vue
<!-- Dashboard.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getDashboard } from '@/api/admin/dashboard';

const data = ref<DashboardVO>();
const loading = ref(true);

onMounted(async () => {
  try {
    data.value = await getDashboard();
  } finally {
    loading.value = false;
  }
});

function formatMoney(n: number) {
  return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2 });
}
</script>

<template>
  <div v-if="data">
    <div class="stat-card">
      <div class="label">全网 LSC 总量</div>
      <div class="value text-primary">
        <i class="fas fa-coins text-gold"></i>
        {{ data.totalLsc.toLocaleString() }} LSC
      </div>
    </div>
    <div class="stat-card">
      <div class="label">监管账户资金池</div>
      <div class="value text-emerald">{{ formatMoney(data.regulatoryPool) }}</div>
    </div>
    <div class="stat-card">
      <div class="label">今日释放速率</div>
      <div class="value text-gold">{{ data.todayReleaseRate }}</div>
      <div class="sub">k = {{ (data.kValue * 100).toFixed(2) }}%</div>
    </div>
  </div>
</template>
```

### 3.2 商家审核列表 + 通过/驳回

```typescript
// src/api/admin/merchant.ts
import { adminApi } from '../index';

interface MerchantAuditVO {
  merchantId: number;
  storeName: string;
  mobile: string;
  businessLicenseUrl: string;
  corporateAccountNo: string;
  regulatoryAgreementSigned: boolean;
  auditStatus: number;  // 0=审核中, 1=通过, 2=驳回
  createdAt: string;
}

export function getMerchantAuditList(params: {
  auditStatus: number;
  pageNo: number;
  pageSize: number;
}) {
  return adminApi.get<{ list: MerchantAuditVO[]; total: number }>(
    '/admin/merchants',
    params
  );
}

export function auditMerchant(merchantId: number, auditStatus: number, rejectReason?: string) {
  return adminApi.put(`/admin/merchants/${merchantId}/audit`, { auditStatus, rejectReason });
}
```

```vue
<!-- MerchantAudit.vue -->
<script setup lang="ts">
import { ref } from 'vue';
import { getMerchantAuditList, auditMerchant } from '@/api/admin/merchant';

const list = ref<MerchantAuditVO[]>([]);

async function loadData() {
  const res = await getMerchantAuditList({ auditStatus: 0, pageNo: 1, pageSize: 20 });
  list.value = res.list;
}

async function approve(id: number) {
  await auditMerchant(id, 1);
  loadData();
}

async function reject(id: number) {
  const reason = prompt('请输入驳回原因');
  if (!reason) return;
  await auditMerchant(id, 2, reason);
  loadData();
}
</script>
```

### 3.3 商品人工审核

```typescript
// src/api/admin/product.ts
export function reviewProduct(productId: number, aiReviewResult: number, rejectReason?: string) {
  // aiReviewResult: 2=人工通过, 3=人工拒绝
  return adminApi.put(`/admin/products/${productId}/review`, { aiReviewResult, rejectReason });
}
```

### 3.4 释放参数修改（双重签名）

```typescript
// src/api/admin/release.ts
export function updateReleaseConfig(params: {
  kMin: string;
  kMax: string;
  alpha: string;
  approver1: string;
  approver2: string;
  signature1: string;
  signature2: string;
}) {
  return adminApi.put('/release/config', params);
}
```

---

## 4. 商家管理后台示例

### 4.1 查询 LSC 账户余额

```typescript
// src/api/merchant/lsc.ts
import { merchantApi } from '../index';

interface LscAccountVO {
  merchantId: number;
  totalLocked: number;
  totalAvailable: number;
  todayReleased: number;
  releaseRate: string;
  expiringCount: number;
}

export function getLscAccount(): Promise<LscAccountVO> {
  return merchantApi.get<LscAccountVO>('/lsc/account');
}
```

### 4.2 核销中心（核心业务）

```typescript
// src/api/merchant/nh.ts
import { merchantApi } from '../index';

interface NhQuotaVO {
  merchantId: number;
  qualified: boolean;
  businessLicenseVerified: boolean;
  corporateAccountBound: boolean;
  regulatoryAgreementSigned: boolean;
  level: string;
  monthlyRevenue: number;
  dailyLimit: number;
  usedToday: number;
  remainingToday: number;
  lastNhDate: string;
  creditScore: number;
}

export function getNhQuota(): Promise<NhQuotaVO> {
  return merchantApi.get<NhQuotaVO>('/nh/quota');
}

interface NhApplyResp {
  orderNo: string;
  lscAmount: number;
  cashAmount: number;
  status: number;
}

export function applyNh(lscAmount: number): Promise<NhApplyResp> {
  return merchantApi.post<NhApplyResp>('/nh/apply', {
    lscAmount,
    idempotentKey: crypto.randomUUID(),
  });
}
```

```vue
<!-- NhCenter.vue 核销中心 -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { getNhQuota, applyNh } from '@/api/merchant/nh';

const quota = ref<NhQuotaVO>();
const inputAmount = ref(0);
const submitting = ref(false);

// 实时计算可到账金额：100 LSC = ¥87
const cashAmount = computed(() => {
  return inputAmount.value * 0.87;
});

onMounted(() => {
  getNhQuota().then((d) => (quota.value = d));
});

async function submitNh() {
  if (!quota.value?.qualified) {
    alert('核销资格未满足，请先完成三证核验');
    return;
  }
  if (inputAmount.value > quota.value.remainingToday) {
    alert(`超过今日可用额度 ${quota.value.remainingToday} LSC`);
    return;
  }
  submitting.value = true;
  try {
    const res = await applyNh(inputAmount.value);
    alert(`核销成功！${res.lscAmount} LSC → ¥${res.cashAmount.toFixed(2)}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="nh-center">
    <div v-if="quota" class="quota-card">
      <div class="quota-header">
        <h3>核销额度（档位 {{ quota.level }}）</h3>
        <span class="badge" :class="quota.qualified ? 'bg-success' : 'bg-warning'">
          {{ quota.qualified ? '资格已满足' : '资格待完善' }}
        </span>
      </div>
      <div class="quota-row">
        <div><strong>日上限：</strong>{{ quota.dailyLimit }} LSC</div>
        <div><strong>已用：</strong>{{ quota.usedToday }} LSC</div>
        <div><strong class="text-gold">可用：{{ quota.remainingToday }} LSC</strong></div>
      </div>
      <div class="qualification">
        <span :class="quota.businessLicenseVerified ? 'ok' : 'no'">营业执照</span>
        <span :class="quota.corporateAccountBound ? 'ok' : 'no'">对公账户</span>
        <span :class="quota.regulatoryAgreementSigned ? 'ok' : 'no'">监管协议</span>
      </div>
    </div>

    <div class="apply-form">
      <label>核销 LSC 数量</label>
      <input type="number" v-model="inputAmount" :max="quota?.remainingToday" />
      <div class="preview">
        预计到账：<strong class="text-emerald">¥{{ cashAmount.toFixed(2) }}</strong>
        <small>（100 LSC = ¥87）</small>
      </div>
      <button class="btn btn-primary" @click="submitNh" :disabled="submitting">
        {{ submitting ? '核销中...' : '确认核销' }}
      </button>
    </div>
  </div>
</template>
```

### 4.3 商家发布商品（含 1:1 价格校验提示）

```typescript
// src/api/merchant/product.ts
import { merchantApi } from '../index';

export function createProduct(form: FormData) {
  return merchantApi.post('/mall/products', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  } as any);
}
```

```vue
<!-- ProductEdit.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { createProduct } from '@/api/merchant/product';

const price = ref(0);
const lscPrice = ref(0);
const productName = ref('');

// 1:1 价格校验：人民币价必须等于 LSC 价
const priceValid = computed(() => Number(price.value) === Number(lscPrice.value));

async function submit() {
  if (!priceValid.value) {
    alert('人民币价格必须与 LSC 价格严格一致（1 LSC = ¥1）');
    return;
  }
  const form = new FormData();
  form.append('productName', productName.value);
  form.append('price', String(price.value));
  // lscPrice 由后端强制等于 price，前端不传或传相同值
  form.append('stock', '100');
  // 图片文件
  // form.append('productImages', fileList.value[0]);
  await createProduct(form);
  alert('商品已提交，等待 AI 审核');
}
</script>
```

### 4.4 B2B 订单创建

```typescript
// src/api/merchant/b2b.ts
import { merchantApi } from '../index';

export function createB2BOrder(params: {
  counterpartyId: number;
  tradeDescription: string;
  totalAmountRmb: number;
  lscAmount: number;
  contractNo: string;
  tradeEvidenceUrls: string[];
}) {
  return merchantApi.post('/b2b/orders', {
    ...params,
    idempotentKey: crypto.randomUUID(),
  });
}
```

---

## 5. 消费者 App / 小程序示例

### 5.1 LSC 钱包首页

```typescript
// src/api/user/lsc.ts
import { userApi } from '../index';

export function getLscAccount() {
  return userApi.get('/lsc/account');
}

export function getLscTransactions(params: { pageNo: number; pageSize: number; type?: number }) {
  return userApi.get('/lsc/transactions', params);
}
```

```vue
<!-- WalletHome.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getLscAccount } from '@/api/user/lsc';

const account = ref<any>();

onMounted(async () => {
  account.value = await getLscAccount();
});
</script>

<template>
  <div class="wallet-card" v-if="account">
    <div class="balance">
      <span class="label">LSC 可用余额</span>
      <span class="amount">
        <i class="fas fa-gem text-gold"></i>
        {{ account.totalAvailable.toLocaleString() }}
      </span>
    </div>
    <div class="sub-info">
      <div>锁定：{{ account.totalLocked.toLocaleString() }} LSC</div>
      <div>今日释放：+{{ account.todayReleased }} LSC</div>
      <div>速率：{{ account.releaseRate }}</div>
    </div>
  </div>
</template>
```

### 5.2 商城混合支付下单

```typescript
// src/api/user/order.ts
import { userApi } from '../index';

interface CreateOrderReq {
  items: { productId: number; quantity: number }[];
  lscAmount: number;
  addressId: number;
}

interface CreateOrderResp {
  orderNo: string;
  totalPrice: number;
  lscAmount: number;
  rmbAmount: number;
  status: number;
  payUrl: string;
}

export function createOrder(req: CreateOrderReq): Promise<CreateOrderResp> {
  return userApi.post<CreateOrderResp>('/orders', {
    ...req,
    idempotentKey: crypto.randomUUID(),
  });
}
```

```vue
<!-- OrderConfirm.vue 混合支付确认页 -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { createOrder } from '@/api/user/order';

const product = ref({ id: 5001, price: 98, name: '智利车厘子' });
const lscPay = ref(0);  // 用户选择用多少 LSC 支付
const availableLsc = ref(86420);

// 人民币差额 = 总价 - LSC 抵扣（1:1）
const rmbPay = computed(() => Math.max(0, product.value.price - lscPay.value));

async function pay() {
  const res = await createOrder({
    items: [{ productId: product.value.id, quantity: 1 }],
    lscAmount: lscPay.value,
    addressId: 8001,
  });
  // 跳转支付机构收银台支付人民币差额
  if (rmbPay.value > 0) {
    window.location.href = res.payUrl;
  } else {
    alert('LSC 全额支付成功');
  }
}
</script>

<template>
  <div class="pay-confirm">
    <div class="product">{{ product.name }} - ¥{{ product.price }}</div>
    <div class="pay-row">
      <span>LSC 支付</span>
      <input type="number" v-model="lscPay" :max="availableLsc" />
      <small>可用 {{ availableLsc }} LSC（1 LSC = ¥1）</small>
    </div>
    <div class="pay-row">
      <span>人民币支付</span>
      <span class="text-danger">¥{{ rmbPay.toFixed(2) }}</span>
    </div>
    <button @click="pay">确认支付</button>
  </div>
</template>
```

### 5.3 线下扫码消费

```typescript
export function payOffline(merchantId: number, amount: number, lscAmount: number) {
  return userApi.post('/orders/offline', {
    merchantId,
    amount,
    lscAmount,
    idempotentKey: crypto.randomUUID(),
  });
}
```

---

## 6. TypeScript 类型定义

```typescript
// src/types/api.ts

// 统一响应
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
  traceId: string;
}

// 分页
export interface PageResult<T> {
  list: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  totalPages: number;
}

// LSC 账户
export interface LscAccount {
  userId: number;
  totalLocked: number;
  totalAvailable: number;
  todayReleased: number;
  releaseRate: string;
  expiringCount: number;
}

// LSC 流水
export interface LscTransaction {
  id: number;
  type: number;
  typeName: string;
  amount: number;
  beforeLocked: number;
  afterLocked: number;
  beforeAvailable: number;
  afterAvailable: number;
  counterpartyId: number;
  orderNo: string;
  createdAt: string;
}

// 商品
export interface Product {
  id: number;
  productName: string;
  price: number;
  lscPrice: number;
  stock: number;
  sales: number;
  productImages: string[];
  merchantId: number;
  storeName: string;
  status: number;
}

// 订单
export interface Order {
  orderNo: string;
  orderType: number;
  productName: string;
  totalPrice: number;
  lscAmount: number;
  rmbAmount: number;
  status: number;
  statusName: string;
  createdAt: string;
}

// 核销记录
export interface NhRecord {
  id: number;
  orderNo: string;
  lscAmount: number;
  cashAmount: number;
  status: number;
  statusName: string;
  createdAt: string;
  completedAt: string;
}

// B2B 订单
export interface B2BOrder {
  id: number;
  orderNo: string;
  counterpartyName: string;
  tradeDescription: string;
  lscAmount: number;
  aiVerificationResult: number;
  status: number;
  statusName: string;
  createdAt: string;
}

// 释放汇总
export interface ReleaseSummary {
  date: string;
  mTotal: number;
  nTotal: number;
  k: number;
  rate: number;
  rateDisplay: string;
  lLocked: number;
  tRelease: number;
  status: number;
}
```

---

## 7. 错误处理与重试

### 7.1 全局错误提示

```typescript
// src/utils/errorHandler.ts
import { ApiError } from '@/api/request';

const ERROR_MESSAGES: Record<number, string> = {
  1001: 'LSC 余额不足，请检查可用余额',
  1002: 'LSC 流转权限不足（消费者不可转给消费者，商家不可转给消费者）',
  1003: '核销资格未满足，请先完成营业执照、对公账户、监管协议三项核验',
  1004: '今日核销额度已用完，请明日再试',
  1005: '今日已核销过，每日限核销 1 次',
  1006: 'B2B 订单对手方尚未确认',
  1007: 'B2B 贸易背景核验未通过',
  1009: '商品价格校验失败：人民币价必须等于 LSC 价（1 LSC = ¥1）',
  1010: '释放参数修改超出安全范围',
  2001: '操作被风控拦截，请稍后重试或联系客服',
};

export function handleApiError(error: unknown): string {
  if (error instanceof ApiError) {
    return ERROR_MESSAGES[error.code] || error.message;
  }
  return '网络异常，请稍后重试';
}
```

### 7.2 带重试的请求（针对网络抖动）

```typescript
// src/api/withRetry.ts
export async function withRetry<T>(
  fn: () => Promise<T>,
  retries = 2,
  delay = 1000
): Promise<T> {
  try {
    return await fn();
  } catch (err: any) {
    if (retries > 0 && (err.code === 'ECONNABORTED' || err.response?.status >= 500)) {
      await new Promise((r) => setTimeout(r, delay));
      return withRetry(fn, retries - 1, delay * 2);
    }
    throw err;
  }
}

// 使用
const data = await withRetry(() => getLscAccount());
```

---

## 8. 文件上传示例

### 8.1 商家资质上传（营业执照等）

```typescript
// src/api/merchant/auth.ts
import { merchantApi } from '../index';

export function registerMerchant(form: {
  mobile: string;
  smsCode: string;
  password: string;
  storeName: string;
  businessLicense: File;
  corporateAccountNo: string;
  regulatoryAgreementSigned: boolean;
  address: {
    province: string;
    city: string;
    district: string;
    detail: string;
    longitude: number;
    latitude: number;
  };
}) {
  const fd = new FormData();
  fd.append('mobile', form.mobile);
  fd.append('smsCode', form.smsCode);
  fd.append('password', form.password);
  fd.append('storeName', form.storeName);
  fd.append('businessLicense', form.businessLicense);
  fd.append('corporateAccountNo', form.corporateAccountNo);
  fd.append('regulatoryAgreementSigned', String(form.regulatoryAgreementSigned));
  fd.append('province', form.address.province);
  fd.append('city', form.address.city);
  fd.append('district', form.address.district);
  fd.append('addressDetail', form.address.detail);
  fd.append('longitude', String(form.address.longitude));
  fd.append('latitude', String(form.address.latitude));

  return merchantApi.post('/merchant/auth/register', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  } as any);
}
```

### 8.2 商品图片上传

```typescript
export function uploadProductImages(files: File[]) {
  const fd = new FormData();
  files.forEach((f) => fd.append('productImages', f));
  return merchantApi.post('/mall/products/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  } as any);
}
```

---

## 9. 小程序特有适配

### 9.1 wx.request 封装

```javascript
// miniprogram/utils/request.js
const BASE_URL = 'https://api.lsc.com/api/v1';

function request(options) {
  const token = wx.getStorageSync('lsc_user_token');
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        Authorization: token ? 'Bearer ' + token : '',
      },
      success(res) {
        const { code, message, data } = res.data;
        if (code === 0) {
          resolve(data);
        } else if (code === 401) {
          wx.removeStorageSync('lsc_user_token');
          wx.redirectTo({ url: '/pages/login/login' });
          reject(new Error('登录已失效'));
        } else {
          wx.showToast({ title: message, icon: 'none' });
          reject(new Error(message));
        }
      },
      fail(err) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(err);
      },
    });
  });
}

module.exports = { request };
```

### 9.2 小程序 LSC 钱包接口

```javascript
// miniprogram/api/lsc.js
const { request } = require('../utils/request');

function getAccount() {
  return request({ url: '/lsc/account' });
}

function getTransactions(pageNo, pageSize) {
  return request({
    url: '/lsc/transactions',
    data: { pageNo, pageSize },
  });
}

module.exports = { getAccount, getTransactions };
```

### 9.3 小程序扫码支付

```javascript
// miniprogram/pages/scanpay/scanpay.js
const { request } = require('../../utils/request');

Page({
  data: { merchantId: null, amount: 0, lscAmount: 0, availableLsc: 0 },

  onLoad() {
    // 扫码获取商家 ID
    wx.scanCode({
      success: (res) => {
        const merchantId = parseInt(res.result);
        this.setData({ merchantId });
        this.loadAccount();
      },
    });
  },

  loadAccount() {
    request({ url: '/lsc/account' }).then((data) => {
      this.setData({ availableLsc: data.totalAvailable });
    });
  },

  onLscInput(e) {
    const lscAmount = parseInt(e.detail.value) || 0;
    this.setData({ lscAmount });
  },

  submitPay() {
    const { merchantId, amount, lscAmount } = this.data;
    request({
      url: '/orders/offline',
      method: 'POST',
      data: {
        merchantId,
        amount,
        lscAmount,
        idempotentKey: Date.now().toString(),
      },
    }).then(() => {
      wx.showToast({ title: '支付成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1500);
    });
  },
});
```

### 9.4 小程序登录（微信授权）

```javascript
// miniprogram/utils/auth.js
function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => {
        // res.code 换取后端 token
        request({
          url: '/user/auth/wx-login',
          method: 'POST',
          data: { code: res.code },
        })
          .then((data) => {
            wx.setStorageSync('lsc_user_token', data.token);
            wx.setStorageSync('lsc_user_id', data.userId);
            resolve(data);
          })
          .catch(reject);
      },
      fail: reject,
    });
  });
}

module.exports = { wxLogin };
```

---

## 10. 联调注意事项

### 10.1 字段命名规范

> **重要**：后端所有字段统一使用 **camelCase**（小驼峰），如 `totalAvailable`、`lscAmount`、`orderNo`。  
> 前端请勿自行转换为 snake_case，直接使用返回字段即可。

### 10.2 幂等键

所有写操作（POST/PUT）必须携带 `idempotentKey`（UUID v4）。前端可通过 `crypto.randomUUID()` 生成，服务端会去重，重复请求返回首次结果。

```typescript
// 浏览器
const key = crypto.randomUUID();

// 小程序（无 crypto API）
const key = Date.now().toString(36) + Math.random().toString(36).slice(2);
```

### 10.3 金额与 LSC 数值

- 所有金额单位为 **元**（人民币），保留 2 位小数
- LSC 数量为 **整数**
- 前端展示时使用 `toLocaleString('zh-CN')` 格式化

### 10.4 时间格式

后端返回 `yyyy-MM-dd HH:mm:ss` 字符串，前端直接展示即可，无需时间戳转换。

### 10.5 Token 过期处理

- `code=401` 时清空本地 Token 并跳转登录页
- Token 有效期：管理端/商家端 2 小时，用户端 7 天
- 建议在请求拦截器中统一处理

### 10.6 环境变量配置

```bash
# .env.development
VITE_API_BASE=https://api-test.lsc.com/api/v1

# .env.production
VITE_API_BASE=https://api.lsc.com/api/v1
```

### 10.7 跨域配置（开发环境）

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'https://api-test.lsc.com',
        changeOrigin: true,
      },
    },
  },
});
```

---

*文档结束 · 链盛通 LSC 平台前端团队*
