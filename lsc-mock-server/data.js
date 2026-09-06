// LSC 全栈原型 Mock 数据层
// 所有接口共享这一份内存数据，确保四端看到的是同一套业务快照

const dayjs = (d = new Date()) => {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
const dateStr = (d) => dayjs(d).slice(0, 10)
const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)]

// ---- 商家 ----
const merchantNames = ['盛源百货商行', '优选生鲜超市', '华夏餐饮连锁', '云裳服饰工厂', '金石建材批发',
  '绿野农产品合作社', '尚品家电卖场', '瑞康医药连锁', '锦程物流供应链', '鼎盛五金机电',
  '明珠家居广场', '佳味食品加工厂']
const provinces = [['浙江省', '杭州市', '西湖区'], ['广东省', '深圳市', '南山区'], ['江苏省', '南京市', '玄武区'],
  ['四川省', '成都市', '武侯区'], ['北京市', '北京市', '朝阳区'], ['上海市', '上海市', '浦东新区']]

const merchants = Array.from({ length: 12 }, (_, i) => {
  const [province, city, district] = provinces[i % provinces.length]
  const status = i < 3 ? 0 : i < 5 ? 1 : i === 5 ? 2 : 1 // 0待审核 1已通过 2已拒绝
  const credit = [100, 95, 88, 75, 60, 40][i % 6]
  const levels = ['0', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K']
  const dailyLimits = [80, 275, 550, 1100, 1650, 2200, 2750, 3300, 3850, 4400, 4950, 5500]
  return {
    id: 10001 + i,
    userId: 10001 + i,
    merchantName: merchantNames[i],
    name: merchantNames[i],
    contact: ['李经理', '王主管', '张总', '陈店长', '刘老板'][i % 5],
    storeName: merchantNames[i] + '(总店)',
    mobile: `1380000${String(1000 + i).padStart(4, '0')}`,
    phone: `1380000${String(1000 + i).padStart(4, '0')}`,
    businessLicense: `91330100MA${String(100000 + i * 7).slice(0, 6)}XX`,
    corporateAccountNo: `622202123456${String(1000 + i).padStart(6, '0')}`,
    regulatoryAccountNo: `8888622200${String(1000 + i).padStart(6, '0')}`,
    mainAccountNo: `622202123456${String(2000 + i).padStart(6, '0')}`,
    regulatoryAgreementSigned: status === 1 ? 1 : 0,
    auditStatus: status,
    creditScore: credit,
    aiRiskScore: rand(20, 80),
    monthlyRevenue: [0, 120000, 350000, 80000, 1500000, 5000000][i % 6],
    nhLimitLevel: levels[i % 12],
    dailyNhLimit: dailyLimits[i % 12],
    penaltyStatus: credit < 40 ? 3 : credit < 60 ? 2 : credit < 80 ? 1 : 0,
    storeName2: merchantNames[i],
    province, city, district,
    addressDetail: `${['文三路', '科技园路', '中山路', '人民大道', '解放路'][i % 5]}${rand(1, 200)}号`,
    aiAddressVerified: i % 3 === 0 ? 0 : 1,
    longitude: 120.0 + rand(0, 200) / 100,
    latitude: 30.0 + rand(0, 100) / 100,
    contactPhone: `0571-8${String(rand(1000000, 9999999))}`,
    businessHours: '09:00-22:00',
    addressUpdateCount: rand(0, 3),
    lastNhDate: i % 2 === 0 ? dateStr(new Date()) : null,
    created_at: dayjs(new Date(Date.now() - i * 86400000 * 10)),
    createdAt: dayjs(new Date(Date.now() - i * 86400000 * 10))
  }
})

// ---- 商品 ----
const productNames = ['有机五常大米 5kg', '新疆和田大枣 500g', '澳洲进口牛排套装', '纯棉四件套 1.8m',
  '智能扫地机器人', '304不锈钢保温杯', '云南小粒咖啡豆 1kg', '手工竹纤维毛巾 10条装',
  '景德镇青花瓷餐具套装', '云南普洱茶饼 357g', '儿童益智积木玩具', '运动跑步鞋 男款',
  '无线蓝牙耳机', '天然乳胶枕头', '东北黑木耳 250g', '阳澄湖大闸蟹礼盒',
  '法国进口红酒 750ml', '泰国天然乳胶床垫', '华为 Mate 手机壳', '小米空气净化器滤芯']

const products = Array.from({ length: 20 }, (_, i) => {
  const price = [59.9, 89, 268, 199, 1299, 69, 128, 49, 358, 168, 159, 399, 199, 129, 78, 588, 328, 2999, 49, 189][i]
  const status = i < 4 ? 2 : i < 16 ? 1 : 0 // 2审核中 1上架 0下架
  return {
    id: 2001 + i,
    merchantId: 10001 + (i % 12),
    merchantName: merchantNames[i % 12],
    productName: productNames[i],
    name: productNames[i],
    productDesc: `精选优质${productNames[i]}，产地直供，品质保障。支持人民币与LSC 1:1 混合支付。`,
    description: `精选优质${productNames[i]}，产地直供，品质保障。`,
    productImages: [`https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(productNames[i] + ' 商品主图 白底 电商摄影')}&image_size=square`],
    cover: `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(productNames[i] + ' 商品主图 白底 电商摄影')}&image_size=square`,
    images: [`https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(productNames[i] + ' 商品主图 白底 电商摄影')}&image_size=square`],
    price,
    lscPrice: price,
    stock: rand(50, 999),
    sales: rand(10, 5000),
    categoryId: 1 + (i % 8),
    status,
    videoUrl: i % 5 === 0 ? 'https://example.com/video.mp4' : null,
    videoStatus: i % 5 === 0 ? 1 : 0,
    aiReviewResult: i < 4 ? (i % 2 === 0 ? 1 : 0) : 0,
    aiReviewTags: i < 4 ? ['图片合规', '文本无敏感词'] : [],
    created_at: dayjs(new Date(Date.now() - i * 86400000 * 2))
  }
})

const categories = [
  { id: 1, name: '食品生鲜', icon: '🍎', parentId: 0 },
  { id: 2, name: '服饰鞋包', icon: '👕', parentId: 0 },
  { id: 3, name: '家居家纺', icon: '🛋️', parentId: 0 },
  { id: 4, name: '数码电器', icon: '📱', parentId: 0 },
  { id: 5, name: '美妆个护', icon: '💄', parentId: 0 },
  { id: 6, name: '母婴玩具', icon: '🧸', parentId: 0 },
  { id: 7, name: '酒水茶饮', icon: '🍵', parentId: 0 },
  { id: 8, name: '工业品', icon: '🔧', parentId: 0 }
]

// ---- 订单 ----
const orderStatus = [0, 1, 2, 3, 4, 5]
const orderStatusDesc = ['待支付', '已支付', '已完成', '已取消', '已退款', '部分退款']
const paymentTypes = [0, 1, 2]
const paymentDesc = ['纯人民币', 'LSC全额', '混合支付']

const orders = Array.from({ length: 30 }, (_, i) => {
  const prod = products[i % products.length]
  const qty = rand(1, 3)
  const total = +(prod.price * qty).toFixed(2)
  const payType = i % 3
  const lscAmt = payType === 0 ? 0 : payType === 1 ? Math.floor(total) : Math.floor(total * 0.5)
  const rmbAmt = +(total - lscAmt).toFixed(2)
  const status = orderStatus[i % 6]
  return {
    id: 30001 + i,
    orderNo: `OD${Date.now().toString().slice(-8)}${String(i).padStart(4, '0')}`,
    orderType: i % 2,
    orderTypeDesc: i % 2 === 0 ? '线上商城' : '线下消费',
    paymentType: payType,
    paymentTypeDesc: paymentDesc[payType],
    isFirstOrder: i === 0 ? 1 : 0,
    consumerId: 40001 + (i % 8),
    consumerName: `用户${1000 + (i % 8)}`,
    consumerMobile: `139****${String(1000 + i).padStart(4, '0')}`,
    merchantId: prod.merchantId,
    merchantName: prod.merchantName,
    productId: prod.id,
    productName: prod.productName,
    productImage: prod.cover,
    totalPrice: total,
    totalAmount: total,
    lscAmount: lscAmt,
    rmbAmount: rmbAmt,
    price: prod.price,
    quantity: qty,
    status,
    statusDesc: orderStatusDesc[status],
    refundLscAmount: status === 4 ? lscAmt : 0,
    refundRmbAmount: status === 4 ? rmbAmt : 0,
    items: [{ productId: prod.id, productName: prod.productName, productImage: prod.cover, price: prod.price, lscPrice: prod.price, quantity: qty }],
    address: { name: '张三', phone: '139****1234', province: '浙江省', city: '杭州市', district: '西湖区', detail: '文三路 123 号' },
    created_at: dayjs(new Date(Date.now() - i * 3600000 * 6)),
    completed_at: status >= 2 ? dayjs(new Date(Date.now() - i * 3600000 * 3)) : null
  }
})

// ---- B2B 订单 ----
const b2bStatus = [0, 1, 2, 3, 4, 5]
const b2bStatusDesc = ['待确认', '已确认', '已流转', '已完成', '已取消', '已作废']

const b2bOrders = Array.from({ length: 15 }, (_, i) => {
  const initiator = 10001 + (i % 12)
  const counterparty = 10001 + ((i + 3) % 12)
  const amount = [50000, 120000, 300000, 80000, 1500000, 200000][i % 6]
  const status = b2bStatus[i % 6]
  return {
    id: 40001 + i,
    orderNo: `B2B${String(Date.now()).slice(-8)}${String(i).padStart(3, '0')}`,
    initiatorId: initiator,
    initiatorName: merchantNames[initiator - 10001],
    counterpartyId: counterparty,
    counterpartyName: merchantNames[counterparty - 10001],
    tradeDescription: `采购${pick(['原材料', '半成品', '成品', '包装物料'])}一批，用于生产加工`,
    totalAmountRmb: amount,
    lscAmount: amount,
    contractNo: `HT${2026}${String(1000 + i).padStart(4, '0')}`,
    tradeEvidenceUrls: '["https://example.com/contract.jpg","https://example.com/invoice.jpg"]',
    aiVerificationResult: i % 4,
    aiVerificationScore: +(70 + rand(0, 30)).toFixed(2),
    counterpartyConfirmed: status >= 1 ? 1 : 0,
    confirmedBy: status >= 1 ? merchantNames[counterparty - 10001] : null,
    confirmedAt: status >= 1 ? dayjs(new Date(Date.now() - i * 86400000)) : null,
    lscTransferred: status >= 2 ? 1 : 0,
    expire_at: dayjs(new Date(Date.now() + (7 - i) * 86400000)),
    status,
    statusDesc: b2bStatusDesc[status],
    created_at: dayjs(new Date(Date.now() - i * 86400000)),
    completed_at: status === 3 ? dayjs(new Date(Date.now() - (i - 1) * 86400000)) : null
  }
})

// ---- 核销记录 ----
const nhStatus = [0, 1, 2, 3]
const nhStatusDesc = ['待处理', '处理中', '成功', '失败']

const writeoffRecords = Array.from({ length: 20 }, (_, i) => {
  const lsc = [500, 1000, 2750, 5500, 11000, 80][i % 6]
  const cash = +(lsc * 0.87).toFixed(2)
  const status = i < 16 ? 2 : i < 18 ? 1 : 3
  const merchantId = 10001 + (i % 12)
  return {
    id: 50001 + i,
    merchantId,
    merchantName: merchantNames[merchantId - 10001],
    lscAmount: lsc,
    cashAmount: cash,
    availableBefore: lsc + rand(1000, 50000),
    availableAfter: rand(1000, 50000),
    fundBefore: cash + rand(10000, 200000),
    fundAfter: rand(10000, 200000),
    orderNo: `NH${String(Date.now()).slice(-8)}${String(i).padStart(3, '0')}`,
    status,
    statusDesc: nhStatusDesc[status],
    created_at: dayjs(new Date(Date.now() - i * 86400000)),
    completed_at: status === 2 ? dayjs(new Date(Date.now() - i * 86400000 + 600000)) : null
  }
})

// ---- LSC 流水 ----
const txTypes = [
  { type: 1, desc: '消费发行' },
  { type: 2, desc: '每日释放' },
  { type: 3, desc: '推广奖励释放' },
  { type: 4, desc: '权益商城消费' },
  { type: 5, desc: '线下消费' },
  { type: 6, desc: '过期转回' },
  { type: 7, desc: '商家核销' },
  { type: 8, desc: 'B2B流转支付' },
  { type: 9, desc: '退款发行回滚' }
]

const lscTransactions = Array.from({ length: 25 }, (_, i) => {
  const t = txTypes[i % 9]
  const amount = [500, 12, 30, -200, -88, -15, -1000, -50000, -100][i % 9]
  return {
    id: 60001 + i,
    txNo: `TX${String(Date.now()).slice(-8)}${String(i).padStart(3, '0')}`,
    user_id: 10001 + (i % 12),
    type: t.type,
    typeDesc: t.desc,
    amount,
    before_locked: rand(10000, 100000),
    after_locked: rand(10000, 100000),
    before_available: rand(500, 20000),
    after_available: rand(500, 20000),
    balance: rand(500, 20000),
    counterparty_id: t.type === 8 ? 10001 + ((i + 3) % 12) : null,
    orderNo: `OD${String(Date.now()).slice(-8)}${String(i).padStart(3, '0')}`,
    remark: t.desc,
    createTime: dayjs(new Date(Date.now() - i * 3600000 * 5)),
    created_at: dayjs(new Date(Date.now() - i * 3600000 * 5))
  }
})

// ---- 风控日志 ----
const riskTypes = ['高频下单预警', '高比例LSC支付预警', '跨地区登录预警', '异常核销分析', '批量注册识别', '拆分订单套利']
const riskLevels = [0, 1, 2]
const riskLevelDesc = ['低', '中', '高']

const riskLogs = Array.from({ length: 14 }, (_, i) => {
  const level = riskLevels[i % 3]
  return {
    id: 70001 + i,
    user_id: 40001 + (i % 8),
    userName: `用户${1000 + (i % 8)}`,
    riskType: riskTypes[i % riskTypes.length],
    riskDetail: `${riskTypes[i % riskTypes.length]}：检测到异常行为特征，建议人工复核`,
    aiRiskLevel: level,
    aiRiskLevelDesc: riskLevelDesc[level],
    aiRiskScore: +(60 + rand(0, 40)).toFixed(2),
    actionTaken: level === 2 ? '已临时限制LSC使用' : level === 1 ? '记录日志' : '记录日志',
    handleStatus: level === 2 ? 1 : 0,
    operator: level === 2 ? 'admin001' : null,
    created_at: dayjs(new Date(Date.now() - i * 3600000 * 8))
  }
})

// ---- 释放汇总 ----
const releaseSummaries = Array.from({ length: 30 }, (_, i) => {
  const k = +(0.003 + (Math.sin(i) + 1) * 0.002).toFixed(6)
  const rate = k <= 0.005 ? 0.0006 : k >= 0.01 ? 0.0003 : +(0.0009 - 0.06 * k).toFixed(8)
  const lLocked = 1200000000 - i * 500000
  const tRelease = Math.floor(lLocked * rate)
  return {
    id: 80001 + i,
    date: dateStr(new Date(Date.now() - (29 - i) * 86400000)),
    mTotal: 140000000.00,
    nTotal: +(k * 140000000).toFixed(2),
    k,
    rate,
    lLocked,
    tRelease,
    batchCount: 120,
    failedBatchCount: 0,
    aiPredictedK7d: +(k * 1.02).toFixed(6),
    aiPredictedK30d: +(k * 0.95).toFixed(6),
    status: 1,
    created_at: dayjs(new Date(Date.now() - (29 - i) * 86400000))
  }
})

// ---- 释放配置 ----
const releaseConfig = [
  { id: 1, configKey: 'rate_max', configValue: '0.06%', editable: 0, description: '释放速率上限(硬常量)' },
  { id: 2, configKey: 'rate_min', configValue: '0.03%', editable: 0, description: '释放速率下限(硬常量)' },
  { id: 3, configKey: 'k_min', configValue: '0.50%', editable: 1, description: '调节起点' },
  { id: 4, configKey: 'k_max', configValue: '1.0%', editable: 1, description: '调节终点' },
  { id: 5, configKey: 'alpha', configValue: '0.06', editable: 1, description: '线性调节因子' }
]

// ---- 存证记录 ----
const evidenceRecords = Array.from({ length: 15 }, (_, i) => ({
  id: 90001 + i,
  batchNo: `BATCH${String(Date.now()).slice(-8)}${String(i).padStart(3, '0')}`,
  operationType: pick(['每日快照', '参数变更', '核销记录', 'B2B流转', '管理员操作']),
  businessId: `BIZ${10000 + i}`,
  dataHash: `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[rand(0, 15)]).join('')}`,
  txId: `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[rand(0, 15)]).join('')}`,
  created_at: dayjs(new Date(Date.now() - i * 3600000 * 3))
}))

// ---- 对账报告 ----
const reconcileReports = Array.from({ length: 10 }, (_, i) => ({
  id: 100001 + i,
  date: dateStr(new Date(Date.now() - (9 - i) * 86400000)),
  status: i < 8 ? 1 : 2,
  statusDesc: i < 8 ? '一致' : '有差异',
  totalOrders: rand(8000, 15000),
  totalAmount: +(rand(500000, 2000000)).toFixed(2),
  merchantCount: rand(8, 12),
  diffCount: i < 8 ? 0 : rand(1, 5),
  diffAmount: i < 8 ? 0 : +(rand(100, 5000)).toFixed(2),
  aiDiagnosis: i < 8 ? '全量一致，无异常' : '检测到差异，AI建议核查支付机构流水',
  created_at: dayjs(new Date(Date.now() - (9 - i) * 86400000))
}))

// ---- 管理员 ----
const admins = [
  { id: 1, username: 'superadmin', realName: '超级管理员', role: 'SUPER_ADMIN', roleDesc: '超级管理员', status: 1, lastLoginAt: dayjs() },
  { id: 2, username: 'superadmin2', realName: '超级管理员2', role: 'SUPER_ADMIN', roleDesc: '超级管理员', status: 1, lastLoginAt: dayjs() },
  { id: 3, username: 'ops001', realName: '运营-李明', role: 'OPS_ADMIN', roleDesc: '运营管理员', status: 1, lastLoginAt: dayjs() },
  { id: 4, username: 'ops002', realName: '运营-王芳', role: 'OPS_ADMIN', roleDesc: '运营管理员', status: 1, lastLoginAt: dayjs() },
  { id: 5, username: 'tech001', realName: '技术-张伟', role: 'TECH_ADMIN', roleDesc: '技术管理员', status: 1, lastLoginAt: dayjs() },
  { id: 6, username: 'finance001', realName: '财务-陈静', role: 'FINANCE_ADMIN', roleDesc: '财务管理员', status: 1, lastLoginAt: dayjs() }
]

// ---- 审计日志 ----
const auditLogs = Array.from({ length: 40 }, (_, i) => ({
  id: 110001 + i,
  adminId: admins[i % admins.length].id,
  adminName: admins[i % admins.length].realName,
  adminRole: admins[i % admins.length].roleDesc,
  operation: pick(['商家审核通过', '商品审核通过', '参数变更审批', '信用分调整', '违规处罚执行', '登录系统', '核销额度调整', '风控事件处理']),
  operationDetail: `执行操作：${pick(['商家审核通过', '商品审核通过', '参数变更审批'])}，对象ID：${10000 + i}`,
  ipAddress: `192.168.${rand(1, 10)}.${rand(1, 254)}`,
  aiAnomalyFlag: i % 20 === 0 ? 1 : 0,
  created_at: dayjs(new Date(Date.now() - i * 3600000 * 2))
}))

// ---- 灰度审批流 ----
const grayFlows = Array.from({ length: 6 }, (_, i) => ({
  id: 120001 + i,
  flowNo: `GRAY${String(Date.now()).slice(-8)}${String(i).padStart(3, '0')}`,
  flowType: ['GRADUATE', 'WEIGHT_CHANGE', 'ROLLBACK', 'LAUNCH'][i % 4],
  flowTypeDesc: ['灰度毕业', '灰度放量', '灰度回滚', '首次灰度'][i % 4],
  policyId: `policy-${100 + i}`,
  applicant: admins[i % admins.length].username,
  title: `${['灰度毕业', '灰度放量', '灰度回滚', '首次灰度'][i % 4]} - 释放参数 ${i + 1}`,
  applyReason: '根据AI仿真推演建议，调整参数以优化释放速率',
  status: ['PENDING_APPROVAL', 'APPROVED', 'SUCCEEDED', 'REJECTED', 'EXECUTING', 'DRAFT'][i],
  requiredApprovals: 2,
  approvedCount: i >= 2 ? 2 : 0,
  totalNodes: 2,
  payloadJson: JSON.stringify({ targetWeight: 100 }),
  executeResponse: i >= 2 ? '{"result":"success"}' : null,
  executeCostMs: i >= 2 ? rand(100, 800) : null,
  approvedAt: i >= 2 ? dayjs() : null,
  createdAt: dayjs(new Date(Date.now() - i * 86400000)),
  updatedAt: dayjs(new Date(Date.now() - i * 86400000))
}))

// ---- 移动端专用 ----
const banners = Array.from({ length: 5 }, (_, i) => ({
  id: 1 + i,
  image: `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(['链盛通LSC消费权益平台主视觉', '1比1消费权益商城', '每日释放LSC凭证', '商家核销享87折', '推广奖励永久有效'][i] + ' 横幅海报 渐变背景 现代金融科技风')}&image_size=landscape_16_9`,
  link: `/pages-product/detail/index?id=${products[i].id}`,
  type: 'product'
}))

const stores = Array.from({ length: 6 }, (_, i) => ({
  id: 10001 + i,
  name: merchantNames[i],
  address: `${provinces[i % 6][1]}市${provinces[i % 6][2]}文三路${rand(1, 200)}号`,
  phone: `0571-8${rand(1000000, 9999999)}`,
  latitude: 30.0 + rand(0, 100) / 100,
  longitude: 120.0 + rand(0, 200) / 100,
  distance: +(rand(100, 5000) / 1000).toFixed(1)
}))

const addresses = Array.from({ length: 5 }, (_, i) => ({
  id: 200 + i,
  name: ['张三', '李四', '王五', '赵六', '孙七'][i],
  phone: `139****${String(1000 + i).padStart(4, '0')}`,
  province: provinces[i % 6][0],
  city: provinces[i % 6][1],
  district: provinces[i % 6][2],
  detail: `${['文三路', '科技园路', '中山路'][i % 3]}${rand(1, 200)}号${rand(1, 30)}栋${rand(1, 20)}01`,
  isDefault: i === 0,
  latitude: 30.1 + i * 0.01,
  longitude: 120.1 + i * 0.01
}))

const aiQuickQuestions = [
  'LSC是什么？怎么获得？',
  'LSC和人民币怎么换算？',
  '怎么核销LSC？',
  '首单为什么不能退款？',
  '推广奖励怎么算？'
]

export {
  merchants, products, categories, orders, b2bOrders, writeoffRecords,
  lscTransactions, riskLogs, releaseSummaries, releaseConfig,
  evidenceRecords, reconcileReports, admins, auditLogs, grayFlows,
  banners, stores, addresses, aiQuickQuestions,
  txTypes, orderStatusDesc, paymentDesc, b2bStatusDesc, nhStatusDesc,
  dayjs, dateStr, rand, pick
}
