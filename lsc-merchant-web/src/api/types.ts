/**
 * 商家端共享实体类型 (与后端 lsc_common 数据库字段对齐)
 */

/** LSC 账户 */
export interface LscAccount {
  userId: number
  totalLocked: number
  totalAvailable: number
  version: number
  updatedAt: string
}

/** LSC 流水 */
export interface LscTransaction {
  id: number
  userId: number
  /** 1消费发行 2每日释放 3推广奖励 4商城消费 5线下消费 6过期转回 7商家核销 8B2B流转 9退款退回 */
  type: number
  amount: number
  beforeLocked: number
  afterLocked: number
  beforeAvailable: number
  afterAvailable: number
  counterpartyId?: number
  orderNo: string
  remark?: string
  createdAt: string
}

/** 可用 LSC 明细 */
export interface AvailableLscDetail {
  id: number
  userId: number
  amount: number
  sourceType: string
  sourceId?: number
  originalExpireDate: string
  expireDate: string
  /** 1有效 2过期转回 3已使用 4已核销 5退款退回 */
  status: number
  createdAt: string
}

/** 商家扩展信息 */
export interface MerchantExtension {
  merchantId: number
  businessLicense: string
  businessLicenseImg?: string
  creditScore: number
  aiRiskScore?: number
  monthlyRevenue: number
  /** 核销限额档位 1-16 0初始 */
  nhLimitLevel: number
  /** 每日核销限额 */
  dailyNhLimit: number
  regulatoryAccountNo?: string
  mainAccountNo?: string
  lastNhDate?: string
  /** 0正常 1一级 2二级 3三级 4清退 */
  penaltyStatus: number
  storeName?: string
  province?: string
  city?: string
  district?: string
  addressDetail?: string
  /** 0未核验 1AI通过 2AI可疑 3人工确认 */
  aiAddressVerified: number
  longitude?: number
  latitude?: number
  contactPhone?: string
  businessHours?: string
  /** 当日地址修改次数 */
  addressUpdateCount: number
  isSignedSupervision: number
  /** 0待审核 1通过 2拒绝 */
  auditStatus: number
}

/** 商品 */
export interface Product {
  id: number
  merchantId: number
  productName: string
  productDesc?: string
  /** 图片 JSON 数组 (前端解析为 string[]) */
  productImages: string
  /** 价格 (人民币 = LSC 价格 1:1) */
  price: number
  stock: number
  categoryId: number
  videoUrl?: string
  videoCoverUrl?: string
  videoDuration?: number
  /** 视频审核状态 0待审核 1通过 2拒绝 */
  videoStatus: number
  /** AI审核 0未审 1AI通过 2AI可疑 3人工通过 4人工拒绝 */
  aiReviewResult: number
  aiReviewTags?: string
  videoRejectReason?: string
  salesCount: number
  /** 0下架 1上架 2审核中 */
  status: number
  createdAt: string
  updatedAt: string
}

/** 商品类目 */
export interface ProductCategory {
  id: number
  parentId: number
  name: string
  iconUrl?: string
  sortOrder: number
  status: number
}

/** 订单 */
export interface Order {
  id: number
  orderNo: string
  /** 0线上商城 1线下消费 */
  orderType: number
  consumerId: number
  merchantId: number
  productId: number
  productName?: string
  quantity: number
  totalPrice: number
  lscAmount: number
  rmbAmount: number
  /** 0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款 */
  status: number
  refundLscAmount: number
  refundRmbAmount: number
  payTime?: string
  createdAt: string
  completedAt?: string
}

/** B2B 订单 */
export interface B2BOrder {
  id: number
  orderNo: string
  initiatorId: number
  counterpartyId: number
  tradeDescription: string
  totalAmountRmb: number
  /** LSC 流转数量 (1:1) */
  lscAmount: number
  contractNo?: string
  tradeEvidenceUrls?: string
  aiVerificationResult: number
  aiVerificationScore?: number
  aiRiskTags?: string
  counterpartyConfirmed: number
  confirmedBy?: string
  confirmedAt?: string
  lscTransferred: number
  expireAt: string
  /** 0待确认 1已确认 2已流转 3已完成 4已取消 5已作废 */
  status: number
  createdAt: string
  completedAt?: string
}

/** 商家核销记录 */
export interface MerchantNhRecord {
  id: number
  merchantId: number
  lscAmount: number
  cashAmount: number
  availableBefore: number
  availableAfter: number
  fundBefore: number
  fundAfter: number
  orderNo: string
  /** 0待处理 1处理中 2成功 3失败 */
  status: number
  failReason?: string
  createdAt: string
  completedAt?: string
}

/** 退款申请 */
export interface RefundRequest {
  id: number
  orderNo: string
  orderId: number
  consumerId: number
  merchantId: number
  productName?: string
  refundLscAmount: number
  refundRmbAmount: number
  reason: string
  /** 0待处理 1已同意 2已拒绝 */
  status: number
  createdAt: string
  handledAt?: string
}

/** 商家违规记录 */
export interface MerchantViolation {
  id: number
  merchantId: number
  violationType: string
  violationDesc: string
  creditDeduct: number
  penaltyAction: string
  aiDetected: number
  penaltyStart?: string
  penaltyEnd?: string
  operator: string
  createdAt: string
}

/** 线下地址 (商家门店地址条目) */
export interface StoreAddress {
  id: number
  merchantId: number
  label?: string
  province: string
  city: string
  district: string
  addressDetail: string
  longitude: number
  latitude: number
  contactPhone?: string
  isPrimary: number
}

/** 上传结果 */
export interface UploadResult {
  url: string
  /** 视频转码相关 */
  status?: 'uploaded' | 'transcoding' | 'ready' | 'failed'
  coverUrl?: string
  duration?: number
  width?: number
  height?: number
  size?: number
}

/** 高德地图 POI */
export interface AmapPoi {
  id?: string
  name: string
  address: string
  longitude: number
  latitude: number
  pname?: string
  cityname?: string
  adname?: string
}
