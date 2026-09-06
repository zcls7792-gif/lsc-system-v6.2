// LSC 全栈原型 Mock API 服务器
// 统一响应: { code: 0, message: 'success', data }  (admin/merchant/mobile 三端通吃)
// 端口: 8000

import express from 'express'
import cors from 'cors'
import * as D from './data.js'

const app = express()
app.use(cors())
app.use(express.json({ limit: '10mb' }))

const ok = (data, message = 'success') => ({ code: 0, message, data })
const fail = (code, message) => ({ code, message, data: null })

// 分页辅助: 同时返回 records 和 list, 兼容 admin/merchant/mobile
const page = (arr, req) => {
  const pageNum = Math.max(1, parseInt(req.query.page) || 1)
  const size = Math.max(1, parseInt(req.query.size) || 10)
  const start = (pageNum - 1) * size
  const list = arr.slice(start, start + size)
  return {
    records: list,
    list,
    total: arr.length,
    current: pageNum,
    size,
    pages: Math.ceil(arr.length / size)
  }
}

// ==================== 登录 / 鉴权 ====================
// 管理员登录 (admin-web)
app.post('/api/admin/login', (req, res) => {
  const { username, password } = req.body || {}
  const admin = D.admins.find(a => a.username === username) || D.admins[0]
  res.json(ok({
    token: `mock-admin-token-${admin.id}`,
    admin: { id: admin.id, username: admin.username, realName: admin.realName, role: admin.role }
  }))
})
app.get('/api/admin/info', (req, res) => res.json(ok(D.admins[0])))
app.post('/api/admin/logout', (req, res) => res.json(ok(null)))
app.get('/api/admin/list', (req, res) => res.json(ok(page(D.admins, req))))
app.post('/api/admin', (req, res) => res.json(ok({ id: Date.now(), ...req.body })))
app.put('/api/admin/:id', (req, res) => res.json(ok({ id: Number(req.params.id), ...req.body })))
app.delete('/api/admin/:id', (req, res) => res.json(ok(null)))
app.get('/api/admin/audit/logs', (req, res) => res.json(ok(page(D.auditLogs, req))))

// 商家登录 (merchant-web) - 后端 /user/login 仅返回 token 字符串
app.post('/api/user/login', (req, res) => {
  // merchant-web auth.ts: post('/user/login', {account, password}) → 返回 token 字符串
  // 然后从 JWT 第二段 base64 解析 userId
  const payload = Buffer.from(JSON.stringify({ userId: 10001, sub: 10001 })).toString('base64')
  const token = `mock.${payload}.sig`
  res.json(ok(token))
})
app.post('/api/user/logout', (req, res) => res.json(ok(null)))
app.post('/api/user/change-password', (req, res) => res.json(ok(null)))

// 移动端登录 (mobile-app)
app.post('/api/user/login', (req, res) => { /* 覆盖上面, 已统一 */ })
// 统一处理: merchant-web 和 mobile 都调 /api/user/login, 但返回结构不同
// 用 middleware 区分: merchant-web 发 {account,password}, mobile 发 {account,password,loginType?}
// 实际上面的 ok(token) 对 merchant 正确 (它 await post<string>), 对 mobile 也能解析 (取 data.token)
// mobile 的 LoginResult 期望 {token, userInfo}, 所以返回 token 字符串时 mobile 会取 res.data.token = undefined
// 解决方案: 让登录返回 { token, userInfo } 形式, merchant-web 里 post<string> 实际拿到的是对象但被当 string
// 看 merchant auth.ts: const token = await post<string>(...) 然后 atob(token.split('.')[1])
// 如果返回对象 token.split 会报错 → catch → 仅返回基础信息. 仍可登录.
// 但更稳妥: 返回字符串 token. mobile 那边 http.post<LoginResult> 取 data.token.
// 折中: 返回字符串, mobile 登录会失败但可手动. 为兼顾, 返回对象并让 merchant catch 兜底.
// 重新设计: 统一返回 { token, userInfo }
app._router.stack = app._router.stack.filter(r => !(r.route && r.route.path === '/api/user/login' && r.route.methods.post))
app.post('/api/user/login', (req, res) => {
  const payload = Buffer.from(JSON.stringify({ userId: 10001, sub: 10001 })).toString('base64')
  const token = `mock.${payload}.sig`
  res.json(ok({
    token,
    userInfo: {
      id: 10001, phone: '13800001001', nickname: '链盛通用户', verifyStatus: 1,
      realName: '张三', userType: 1, createTime: D.dayjs()
    }
  }))
})

app.post('/api/user/login/sms', (req, res) => {
  const payload = Buffer.from(JSON.stringify({ userId: 10001 })).toString('base64')
  res.json(ok({ token: `mock.${payload}.sig`, userInfo: { id: 10001, phone: req.body?.phone || '13800001001', nickname: '链盛通用户', verifyStatus: 1, userType: 1 } }))
})
app.post('/api/user/sms/send', (req, res) => res.json(ok(null)))
app.post('/api/user/register', (req, res) => {
  const payload = Buffer.from(JSON.stringify({ userId: 10099 })).toString('base64')
  res.json(ok({ token: `mock.${payload}.sig`, userInfo: { id: 10099, phone: req.body?.phone, nickname: '新用户', verifyStatus: 0, userType: 1 } }))
})
app.get('/api/user/profile', (req, res) => res.json(ok({
  id: 10001, phone: '13800001001', nickname: '链盛通用户', avatar: '', verifyStatus: 1,
  realName: '张三', idCard: '330***********1234', referrerPhone: '138****0000', userType: 1, createTime: D.dayjs()
})))
app.post('/api/user/verify', (req, res) => res.json(ok({ id: 10001, verifyStatus: 1, realName: req.body?.realName })))
app.put('/api/user/profile', (req, res) => res.json(ok({ id: 10001, ...req.body })))
app.post('/api/user/password/change', (req, res) => res.json(ok(null)))
app.post('/api/user/password/reset', (req, res) => res.json(ok(null)))

// 地址管理 (mobile)
app.get('/api/user/address/list', (req, res) => res.json(ok(D.addresses)))
app.get('/api/user/address/detail', (req, res) => res.json(ok(D.addresses[0])))
app.post('/api/user/address/save', (req, res) => res.json(ok({ id: Date.now(), ...req.body })))
app.delete('/api/user/address/delete', (req, res) => res.json(ok(null)))
app.post('/api/user/address/default', (req, res) => res.json(ok(null)))

// ==================== 商家 ====================
app.get('/api/merchant/list', (req, res) => res.json(ok(page(D.merchants, req))))
app.get('/api/merchant/audit/list', (req, res) => res.json(ok(page(D.merchants.filter(m => m.auditStatus === 0), req))))
app.post('/api/merchant/audit/:id', (req, res) => res.json(ok(null)))
app.get('/api/merchant/:id', (req, res) => {
  const id = Number(req.params.id)
  res.json(ok(D.merchants.find(m => m.id === id || m.userId === id) || D.merchants[0]))
})
app.get('/api/merchant/:id/credit', (req, res) => {
  const id = Number(req.params.id)
  const m = D.merchants.find(x => x.id === id || x.userId === id) || D.merchants[0]
  res.json(ok({
    merchantId: m.id, creditScore: m.creditScore, aiRiskScore: m.aiRiskScore,
    nhLimitLevel: m.nhLimitLevel, dailyNhLimit: m.dailyNhLimit, penaltyStatus: m.penaltyStatus,
    violations: D.auditLogs.slice(0, 5).map(l => ({ ...l, violationType: '地址虚假', creditDeduct: 20, penaltyAction: '一级处罚' }))
  }))
})
app.get('/api/merchant/violation/logs', (req, res) => res.json(ok(page(D.auditLogs.slice(0, 10).map(l => ({
  id: l.id, merchantId: 10001, violationType: '地址虚假', violationDesc: '线下经营地址与工商注册地址不符',
  creditDeduct: 20, penaltyAction: '一级处罚', aiDetected: 1, operator: l.adminName, created_at: l.created_at
})), req))))
app.post('/api/merchant/:id/penalty', (req, res) => res.json(ok(null)))
app.post('/api/merchant/:id/credit/adjust', (req, res) => res.json(ok(null)))

// 商家扩展信息 (merchant-web)
app.get('/api/merchant/info', (req, res) => {
  const m = D.merchants[0]
  res.json(ok({ ...m, storeName: m.storeName || m.merchantName, isSignedSupervision: m.regulatoryAgreementSigned }))
})
app.post('/api/merchant/store/info', (req, res) => res.json(ok({ ...D.merchants[0], ...req.body })))
app.get('/api/merchant/store/addresses', (req, res) => res.json(ok(D.addresses.map(a => ({ ...a, label: a.isDefault ? '默认地址' : '门店' })))))
app.post('/api/merchant/store/addresses', (req, res) => res.json(ok({ id: Date.now(), ...req.body })))
app.put('/api/merchant/store/addresses/:id', (req, res) => res.json(ok({ id: Number(req.params.id), ...req.body })))
app.delete('/api/merchant/store/addresses/:id', (req, res) => res.json(ok(null)))
app.post('/api/merchant/store/addresses/:id/primary', (req, res) => res.json(ok(null)))
app.get('/api/merchant/store/addresses/update-state', (req, res) => res.json(ok({ todayUpdatedCount: 1, dailyLimit: 3, remaining: 2 })))

// ==================== 商品 ====================
app.get('/api/product/list', (req, res) => {
  let list = [...D.products]
  if (req.query.status !== undefined) list = list.filter(p => p.status === Number(req.query.status))
  if (req.query.keyword) list = list.filter(p => p.productName.includes(req.query.keyword))
  res.json(ok(page(list, req)))
})
app.get('/api/product/categories', (req, res) => res.json(ok(D.categories)))
app.get('/api/product/categories/:parentId', (req, res) => res.json(ok([])))
app.get('/api/product/banners', (req, res) => res.json(ok(D.banners)))
app.get('/api/product/recommend', (req, res) => res.json(ok(D.products.slice(0, 10))))
app.get('/api/product/hot', (req, res) => res.json(ok(D.products.slice(0, 8).sort((a, b) => b.sales - a.sales))))
app.get('/api/product/search', (req, res) => res.json(ok(page(D.products.filter(p => p.productName.includes(req.query.keyword || '')), req))))
app.get('/api/product/stores/nearby', (req, res) => res.json(ok(D.stores)))

app.get('/api/product/detail', (req, res) => {
  const id = Number(req.query.id)
  const p = D.products.find(x => x.id === id) || D.products[0]
  res.json(ok({ ...p, store: D.stores[0], specs: [{ id: 1, name: '规格', values: ['标准', '加大'] }] }))
})
app.get('/api/product/:id', (req, res) => {
  const id = Number(req.params.id)
  if (isNaN(id)) return next()
  const p = D.products.find(x => x.id === id) || D.products[0]
  res.json(ok(p))
})
app.post('/api/product/publish', (req, res) => res.json(ok({ id: Date.now(), ...req.body, status: 2 })))
app.put('/api/product/update', (req, res) => res.json(ok({ ...req.body })))
app.delete('/api/product/:id', (req, res) => res.json(ok(null)))
app.post('/api/product/on-shelf', (req, res) => res.json(ok(null)))
app.post('/api/product/off-shelf', (req, res) => res.json(ok(null)))
app.post('/api/product/:id/status', (req, res) => res.json(ok(null)))

app.get('/api/product/audit/list', (req, res) => res.json(ok(page(D.products.filter(p => p.status === 2), req))))
app.get('/api/product/:id/ai-review', (req, res) => res.json(ok({
  productId: Number(req.params.id), aiReviewResult: 1, aiReviewTags: ['图片合规', '文本无敏感词', '价格1:1一致'],
  riskPoints: [], suggestion: 'AI初审通过，建议上架'
})))
app.post('/api/product/audit/:id', (req, res) => res.json(ok(null)))

// ==================== 订单 ====================
app.get('/api/order/list', (req, res) => {
  let list = [...D.orders]
  if (req.query.status !== undefined && req.query.status !== '' && req.query.status !== '-1') list = list.filter(o => o.status === Number(req.query.status))
  res.json(ok(page(list, req)))
})
app.get('/api/order/detail', (req, res) => res.json(ok(D.orders.find(o => o.id === Number(req.query.id)) || D.orders[0])))
app.get('/api/order/:orderNo', (req, res) => res.json(ok(D.orders.find(o => o.orderNo === req.params.orderNo) || D.orders[0])))
app.post('/api/order/create', (req, res) => {
  const total = req.body?.items?.reduce((s, it) => s + (D.products.find(p => p.id === it.productId)?.price || 0) * it.quantity, 0) || 0
  res.json(ok({ orderId: Date.now(), orderNo: `OD${Date.now()}`, payAmount: total - (req.body?.lscAmount || 0), lscAmount: req.body?.lscAmount || 0, expireTime: Date.now() + 900000 }))
})
app.post('/api/order/pay', (req, res) => res.json(ok({ orderId: req.body?.orderId, status: 1 })))
app.post('/api/order/cancel', (req, res) => res.json(ok(null)))
app.post('/api/order/confirm', (req, res) => res.json(ok(null)))
app.post('/api/order/refund/apply', (req, res) => res.json(ok(null)))
app.post('/api/order/preview', (req, res) => {
  const total = req.body?.items?.reduce((s, it) => s + (D.products.find(p => p.id === it.productId)?.price || 0) * it.quantity, 0) || 0
  res.json(ok({ totalAmount: total, lscAmount: req.body?.lscAmount || 0, rmbAmount: total - (req.body?.lscAmount || 0), items: req.body?.items || [], address: D.addresses[0] }))
})
app.post('/api/order/ship', (req, res) => res.json(ok(null)))
app.post('/api/order/refund/agree', (req, res) => res.json(ok(null)))
app.post('/api/order/refund/reject', (req, res) => res.json(ok(null)))
app.get('/api/order/refund/list', (req, res) => res.json(ok(page(D.orders.filter(o => o.status === 4 || o.status === 5).map(o => ({ ...o, refundStatus: 1, refundReason: '商品质量问题' })), req))))
app.get('/api/order/stats-today', (req, res) => res.json(ok({ todayOrderCount: 28, todayRevenue: 15680.5, pendingShipCount: 5, pendingRefundCount: 2 })))
app.get('/api/order/export', (req, res) => res.json(ok(D.orders.slice(0, 100))))

// ==================== B2B ====================
app.get('/api/b2b/list', (req, res) => {
  let list = [...D.b2bOrders]
  if (req.query.status !== undefined && req.query.status !== '') list = list.filter(b => b.status === Number(req.query.status))
  res.json(ok(page(list, req)))
})
app.get('/api/b2b/:orderNo', (req, res) => res.json(ok(D.b2bOrders.find(b => b.orderNo === req.params.orderNo) || D.b2bOrders[0])))
app.get('/api/b2b/:orderNo/verify-result', (req, res) => res.json(ok({
  orderNo: req.params.orderNo, aiVerificationResult: 0, aiVerificationScore: 92.5,
  matchedFields: ['合同编号', '发票金额', '物流单号'], riskPoints: []
})))
app.get('/api/b2b/:orderNo/documents', (req, res) => res.json(ok([
  { type: '采购合同', url: 'https://example.com/contract.jpg' },
  { type: '送货单', url: 'https://example.com/delivery.jpg' },
  { type: '发票', url: 'https://example.com/invoice.jpg' }
])))
app.post('/api/b2b/:orderNo/verify-confirm', (req, res) => res.json(ok(null)))
app.post('/api/b2b/create', (req, res) => res.json(ok({ id: Date.now(), orderNo: `B2B${Date.now()}`, ...req.body, status: 0 })))
app.post('/api/b2b/confirm', (req, res) => res.json(ok(null)))
app.post('/api/b2b/cancel', (req, res) => res.json(ok(null)))
app.post('/api/b2b/complete', (req, res) => res.json(ok(null)))

// ==================== 核销 ====================
app.get('/api/writeoff/list', (req, res) => {
  let list = [...D.writeoffRecords]
  if (req.query.status !== undefined && req.query.status !== '') list = list.filter(w => w.status === Number(req.query.status))
  res.json(ok(page(list, req)))
})
app.get('/api/writeoff/:orderNo', (req, res) => res.json(ok(D.writeoffRecords.find(w => w.orderNo === req.params.orderNo) || D.writeoffRecords[0])))
app.get('/api/writeoff/by-id/:id', (req, res) => res.json(ok(D.writeoffRecords.find(w => w.id === Number(req.params.id)) || D.writeoffRecords[0])))
app.get('/api/writeoff/stats', (req, res) => res.json(ok({ totalCount: D.writeoffRecords.length, totalLsc: D.writeoffRecords.reduce((s, w) => s + w.lscAmount, 0), totalCash: D.writeoffRecords.reduce((s, w) => s + w.cashAmount, 0) })))
app.get('/api/writeoff/quota', (req, res) => res.json(ok({
  dailyLimit: 2750, todayUsed: 0, todayRemaining: 2750, nhLimitLevel: 6,
  cashRate: 0.87, regulatoryBalance: 156800.00, lastNhDate: null
})))
app.post('/api/writeoff/apply', (req, res) => {
  const lsc = req.body?.lscAmount || 1000
  res.json(ok({ id: Date.now(), merchantId: 10001, lscAmount: lsc, cashAmount: +(lsc * 0.87).toFixed(2), orderNo: `NH${Date.now()}`, status: 1, created_at: D.dayjs() }))
})

// ==================== LSC 账本 ====================
app.get('/api/ledger/account', (req, res) => res.json(ok({
  total: 128500, available: 12850, locked: 115650, released: 12850,
  pendingRelease: 115650, releaseProgress: 10, todayRelease: 46
})))
app.get('/api/ledger/account/:userId', (req, res) => res.json(ok({
  user_id: Number(req.params.userId), total_locked: 115650, total_available: 12850, version: 1, updated_at: D.dayjs()
})))
app.get('/api/ledger/transactions', (req, res) => res.json(ok(page(D.lscTransactions, req))))
app.get('/api/ledger/available-details', (req, res) => res.json(ok(page(D.lscTransactions.slice(0, 10).map(t => ({
  id: t.id, user_id: t.user_id, amount: Math.abs(t.amount), source_type: 'release', original_expire_date: '2027-09-06',
  expire_date: '2027-09-06', status: 1, created_at: t.created_at
})), req))))
app.get('/api/ledger/recent-trend', (req, res) => res.json(ok(Array.from({ length: 7 }, (_, i) => ({
  date: D.dateStr(new Date(Date.now() - (6 - i) * 86400000)),
  orderCount: D.rand(5, 30), revenue: D.rand(1000, 8000), lscIn: D.rand(50, 500)
})))))
app.get('/api/ledger/overview/:userId', (req, res) => res.json(ok({
  totalLocked: 115650, totalAvailable: 12850, totalUsed: 3200, totalWrittenOff: 8000, monthlyRevenue: 350000
})))
app.get('/api/ledger/transaction-types', (req, res) => res.json(ok(D.txTypes.map(t => ({ code: t.type, desc: t.desc })))))
app.get('/api/ledger/promotion/summary', (req, res) => res.json(ok({
  totalReward: 1280, invitedCount: 12, activeCount: 8, referrerPhone: '138****0000',
  rules: '被推荐人完成首单（≥10元）后，推荐人获得首单金额10%的LSC奖励，永久有效'
})))

// ==================== 风控 ====================
app.get('/api/risk/logs', (req, res) => res.json(ok(page(D.riskLogs, req))))
app.get('/api/risk/dashboard', (req, res) => res.json(ok({
  totalEvents: D.riskLogs.length, highRisk: D.riskLogs.filter(r => r.aiRiskLevel === 2).length,
  mediumRisk: D.riskLogs.filter(r => r.aiRiskLevel === 1).length, lowRisk: D.riskLogs.filter(r => r.aiRiskLevel === 0).length,
  handled: D.riskLogs.filter(r => r.handleStatus === 1).length,
  trend: Array.from({ length: 7 }, (_, i) => ({ date: D.dateStr(new Date(Date.now() - (6 - i) * 86400000)), count: D.rand(1, 8) }))
})))
app.get('/api/risk/logs/:id', (req, res) => res.json(ok(D.riskLogs.find(r => r.id === Number(req.params.id)) || D.riskLogs[0])))
app.post('/api/risk/logs/:id/handle', (req, res) => res.json(ok(null)))

// ==================== 释放 ====================
app.get('/api/release/summary', (req, res) => res.json(ok(page(D.releaseSummaries, req))))
app.get('/api/release/config', (req, res) => res.json(ok(D.releaseConfig)))
app.post('/api/release/param-approval', (req, res) => res.json(ok({ approvalId: Date.now(), status: 'PENDING' })))
app.post('/api/release/param-approve', (req, res) => res.json(ok(null)))
app.get('/api/release/predict', (req, res) => res.json(ok({
  days: 7, predictedK: D.releaseSummaries.slice(-7).map(s => ({ date: s.date, k: s.k, rate: s.rate })),
  predictedK7d: 0.0052, predictedK30d: 0.0048
})))
app.get('/api/release/trend', (req, res) => res.json(ok(D.releaseSummaries.slice(-30).map(s => ({ date: s.date, k: s.k, rate: s.rate, tRelease: s.tRelease })))))
app.post('/api/release/simulation', (req, res) => res.json(ok({
  scenario: req.body?.scenario || 'default', predictedRate: 0.00045, predictedLockedAfter30d: 1100000000,
  predictedAnchoringRatio: 12.5, suggestion: '参数调整后锚定比率充裕，建议通过'
})))

// 灰度审批
app.get('/api/release/gray/approvals', (req, res) => res.json(ok(page(D.grayFlows, req))))
app.get('/api/release/gray/approvals/:id', (req, res) => {
  const flow = D.grayFlows.find(f => f.id === Number(req.params.id)) || D.grayFlows[0]
  res.json(ok({
    flow,
    nodes: [
      { id: 1, flowId: flow.id, nodeOrder: 1, approverRole: 'SUPER_ADMIN', approver: 'superadmin', nodeStatus: 'APPROVED', comment: '同意', signature: 'sig1', decidedAt: D.dayjs() },
      { id: 2, flowId: flow.id, nodeOrder: 2, approverRole: 'SUPER_ADMIN', approver: 'superadmin2', nodeStatus: 'WAITING', comment: null, signature: null, decidedAt: null }
    ],
    audits: [
      { id: 1, flowId: flow.id, flowNo: flow.flowNo, action: 'FLOW_CREATED', operator: flow.applicant, detailJson: null, chainTxHash: `0x${'a'.repeat(64)}`, createdAt: D.dayjs() },
      { id: 2, flowId: flow.id, flowNo: flow.flowNo, action: 'FLOW_SUBMITTED', operator: flow.applicant, detailJson: null, chainTxHash: `0x${'b'.repeat(64)}`, createdAt: D.dayjs() }
    ]
  }))
})
app.post('/api/release/gray/approvals', (req, res) => res.json(ok({ id: Date.now(), flowNo: `GRAY${Date.now()}`, ...req.body, status: 'PENDING_APPROVAL' })))
app.put('/api/release/gray/approvals/action/approve', (req, res) => res.json(ok(null)))
app.put('/api/release/gray/approvals/action/cancel', (req, res) => res.json(ok(null)))
app.put('/api/release/gray/approvals/action/retry-execute', (req, res) => res.json(ok(null)))

// ==================== 存证 ====================
app.get('/api/evidence/list', (req, res) => res.json(ok(page(D.evidenceRecords, req))))
app.get('/api/evidence/:id', (req, res) => res.json(ok(D.evidenceRecords.find(e => e.id === Number(req.params.id)) || D.evidenceRecords[0])))
app.post('/api/evidence/verify', (req, res) => res.json(ok({ date: req.body?.date, matched: true, diffCount: 0, report: '链上哈希与数据库哈希完全一致' })))
app.get('/api/evidence/verify-report', (req, res) => res.json(ok({ date: req.query.date, matched: true, diffCount: 0, aiAnalysis: 'AI校验通过，数据完整可信' })))

// ==================== 对账 ====================
app.get('/api/reconcile/report', (req, res) => res.json(ok(page(D.reconcileReports, req))))
app.get('/api/reconcile/report/:date', (req, res) => res.json(ok(D.reconcileReports.find(r => r.date === req.params.date) || D.reconcileReports[0])))
app.post('/api/reconcile/trigger', (req, res) => res.json(ok({ date: req.body?.date, status: 1 })))

// ==================== AI 客服 ====================
app.post('/api/ai/customer-service', (req, res) => {
  const msg = req.body?.message || ''
  const replies = {
    'LSC': 'LSC是链盛通消费权益凭证，1 LSC = 1元人民币消费权益。通过真实人民币消费获得，每日按0.03%-0.06%速率释放。',
    '核销': '商家核销100个LSC可获得87元现金，剩余13元继续留在监管账户锚定其他可用LSC。每日限核销1次，限额按月营业额档位确定。',
    '退款': '首单不支持退款，使用LSC的订单不支持退款，仅纯人民币支付的非首单订单支持退款。',
    '推广': '被推荐人完成首单（≥10元）后，推荐人获得首单金额10%的LSC奖励，永久有效。'
  }
  let reply = '感谢您的咨询，我是链盛通AI客服。关于LSC权益凭证、核销、退款、推广奖励等问题都可以问我。'
  for (const k in replies) {
    if (msg.includes(k)) { reply = replies[k]; break }
  }
  res.json(ok({ reply, createTime: D.dayjs() }))
})
app.get('/api/ai/quick-questions', (req, res) => res.json(ok(D.aiQuickQuestions)))

// ==================== 兜底 ====================
app.use((req, res) => {
  res.status(404).json(fail(404, `Mock 未实现: ${req.method} ${req.path}`))
})

const PORT = 8000
app.listen(PORT, () => {
  console.log(`[LSC Mock Server] 运行于 http://localhost:${PORT}`)
  console.log(`[LSC Mock Server] 为 admin-web(5173) / merchant-web(5174) / mobile-app(8080) 提供数据`)
})
