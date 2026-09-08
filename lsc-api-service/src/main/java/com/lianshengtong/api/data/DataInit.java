package com.lianshengtong.api.data;

import com.lianshengtong.api.entity.*;
import com.lianshengtong.api.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动数据初始化（V6.2 更新版）
 * 1. 调用 MockData.init() 初始化内存数据
 * 2. 若表为空，从 MockData 同步到 H2（仅首次启动）
 * 3. 后续重启直接从 H2 读取已持久化数据
 * 4. V6.2 新增：LscAccount、DailyReleaseSummary、ReleaseConfig 初始化
 */
@Component
public class DataInit implements CommandLineRunner {

    private final MerchantRepository merchantRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final B2BOrderRepository b2bRepo;
    private final WriteoffRepository writeoffRepo;
    private final EvidenceRepository evidenceRepo;
    private final LedgerTxnRepository ledgerRepo;
    private final RiskLogRepository riskRepo;
    private final LscAccountRepository lscAccountRepo;
    private final DailyReleaseSummaryRepository dailyReleaseRepo;
    private final ReleaseConfigRepository releaseConfigRepo;
    private final UserRepository userRepo;
    private final PromotionPendingRepository promotionPendingRepo;

    public DataInit(MerchantRepository merchantRepo,
                    ProductRepository productRepo,
                    OrderRepository orderRepo,
                    B2BOrderRepository b2bRepo,
                    WriteoffRepository writeoffRepo,
                    EvidenceRepository evidenceRepo,
                    LedgerTxnRepository ledgerRepo,
                    RiskLogRepository riskRepo,
                    LscAccountRepository lscAccountRepo,
                    DailyReleaseSummaryRepository dailyReleaseRepo,
                    ReleaseConfigRepository releaseConfigRepo,
                    UserRepository userRepo,
                    PromotionPendingRepository promotionPendingRepo) {
        this.merchantRepo = merchantRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.b2bRepo = b2bRepo;
        this.writeoffRepo = writeoffRepo;
        this.evidenceRepo = evidenceRepo;
        this.ledgerRepo = ledgerRepo;
        this.riskRepo = riskRepo;
        this.lscAccountRepo = lscAccountRepo;
        this.dailyReleaseRepo = dailyReleaseRepo;
        this.releaseConfigRepo = releaseConfigRepo;
        this.userRepo = userRepo;
        this.promotionPendingRepo = promotionPendingRepo;
    }

    @Override
    public void run(String... args) {
        MockData.init();
        System.out.println("[LSC API] 内存数据初始化完成: 商家" + MockData.merchants.size()
                + " 商品" + MockData.products.size()
                + " 订单" + MockData.orders.size());

        seedUsers();
        seedMerchants();
        seedProducts();
        seedOrders();
        seedB2BOrders();
        seedWriteoffs();
        seedEvidence();
        seedLedgerTxns();
        seedRiskLogs();
        seedLscAccounts();
        seedReleaseConfigs();
        seedDailyReleaseSummary();
        // V6.2 推广奖励挂账表（默认空，由 PromotionController 运行时生成）
        if (promotionPendingRepo.count() == 0) {
            System.out.println("[LSC DB] promotion_pending 表为空，运行时由 PromotionController 生成");
        }
    }

    /**
     * V6.2 第十四章 14.1 用户表初始化
     * 消费者会员(0) 和 商家会员(1)
     */
    private void seedUsers() {
        if (userRepo.count() > 0) {
            System.out.println("[LSC DB] users 表已存在 " + userRepo.count() + " 条，跳过 seed");
            return;
        }
        List<User> list = new ArrayList<>();
        // 商家会员（与 merchants 表对齐）
        for (Map<String, Object> m : MockData.merchants) {
            User u = new User();
            u.setUserId(toLong(m.get("userId")));
            u.setUserType(1); // 商家会员
            u.setMobile((String) m.get("mobile"));
            u.setIsVerified(1); // 商家已实名认证
            u.setReferrerId(null);
            u.setFirstOrderCompleted(1);
            u.setUserName((String) m.get("merchantName"));
            u.setCreatedAt((String) m.get("createdAt"));
            list.add(u);
        }
        // 消费者会员（与订单中 userId 对齐）
        int consumerStart = 20001;
        for (int i = 0; i < 30; i++) {
            User u = new User();
            u.setUserId((long) (consumerStart + i));
            u.setUserType(0); // 消费者会员
            u.setMobile("1390000" + String.format("%05d", i));
            u.setIsVerified(1);
            // 第一个消费者无推荐人，后续以前一个为推荐人（演示一级直推）
            u.setReferrerId(i == 0 ? null : (long) (consumerStart + i - 1));
            u.setFirstOrderCompleted(i < 10 ? 1 : 0);
            u.setUserName("消费者" + i);
            u.setCreatedAt(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            list.add(u);
        }
        userRepo.saveAll(list);
        System.out.println("[LSC DB] users 表 seed 完成: " + list.size() + " 条");
    }

    private void seedMerchants() {
        if (merchantRepo.count() > 0) {
            System.out.println("[LSC DB] merchants 表已存在 " + merchantRepo.count() + " 条，跳过 seed");
            return;
        }
        List<Merchant> list = new ArrayList<>();
        for (Map<String, Object> m : MockData.merchants) {
            Merchant e = new Merchant();
            e.setId(toLong(m.get("id")));
            e.setUserId(toLong(m.get("userId")));
            e.setMerchantName((String) m.get("merchantName"));
            e.setName((String) m.get("name"));
            e.setContact((String) m.get("contact"));
            e.setStoreName((String) m.get("storeName"));
            e.setMobile((String) m.get("mobile"));
            e.setPhone((String) m.get("phone"));
            e.setAuditStatus(toInt(m.get("auditStatus")));
            e.setStatus(toInt(m.get("status")));
            e.setCreditScore(toInt(m.get("creditScore")));
            e.setAiRiskScore(toInt(m.get("aiRiskScore")));
            e.setMonthlyRevenue(toInt(m.get("monthlyRevenue")));
            e.setNhLimitLevel((String) m.get("nhLimitLevel"));
            e.setDailyNhLimit(toInt(m.get("dailyNhLimit")));
            e.setPenaltyStatus(toInt(m.get("penaltyStatus")));
            e.setProvince((String) m.get("province"));
            e.setCity((String) m.get("city"));
            e.setDistrict((String) m.get("district"));
            e.setAddressDetail((String) m.get("addressDetail"));
            e.setAiAddressVerified(toInt(m.get("aiAddressVerified")));
            e.setLongitude(toDouble(m.get("longitude")));
            e.setLatitude(toDouble(m.get("latitude")));
            e.setBusinessHours((String) m.get("businessHours"));
            // V6.2 第十四章 14.2 商家合规准入三要件
            e.setBusinessLicense((String) m.get("businessLicense"));
            e.setCorporateAccountNo((String) m.get("corporateAccountNo"));
            e.setRegulatoryAgreementSigned(toInt(m.get("regulatoryAgreementSigned")));
            e.setRegulatoryAccountNo((String) m.get("regulatoryAccountNo"));
            e.setMainAccountNo((String) m.get("mainAccountNo"));
            e.setLastNhDate((String) m.get("lastNhDate"));
            e.setAddressUpdateCount(toInt(m.get("addressUpdateCount")));
            e.setCreatedAt((String) m.get("createdAt"));
            list.add(e);
        }
        merchantRepo.saveAll(list);
        System.out.println("[LSC DB] merchants 表 seed 完成: " + list.size() + " 条");
    }

    private void seedProducts() {
        if (productRepo.count() > 0) {
            System.out.println("[LSC DB] products 表已存在 " + productRepo.count() + " 条，跳过 seed");
            return;
        }
        List<Product> list = new ArrayList<>();
        for (Map<String, Object> p : MockData.products) {
            Product e = new Product();
            e.setId(toLong(p.get("id")));
            e.setMerchantId(toLong(p.get("merchantId")));
            e.setMerchantName((String) p.get("merchantName"));
            e.setProductName((String) p.get("productName"));
            e.setName((String) p.get("name"));
            e.setProductDesc((String) p.get("productDesc"));
            e.setDescription((String) p.get("description"));
            Object imgs = p.get("productImages");
            if (imgs instanceof List) {
                e.setProductImages((List<String>) imgs);
            }
            e.setCover((String) p.get("cover"));
            Object images = p.get("images");
            if (images instanceof List) {
                e.setImages((List<String>) images);
            }
            e.setPrice(toDouble(p.get("price")));
            e.setLscPrice(toDouble(p.get("lscPrice")));
            e.setStock(toInt(p.get("stock")));
            e.setSales(toInt(p.get("sales")));
            e.setCategoryId(toInt(p.get("categoryId")));
            e.setStatus(toInt(p.get("status")));
            e.setAiReviewResult(toInt(p.get("aiReviewResult")));
            e.setCreatedAt((String) p.get("createdAt"));
            list.add(e);
        }
        productRepo.saveAll(list);
        System.out.println("[LSC DB] products 表 seed 完成: " + list.size() + " 条");
    }

    private void seedOrders() {
        if (orderRepo.count() > 0) {
            System.out.println("[LSC DB] orders 表已存在 " + orderRepo.count() + " 条，跳过 seed");
            return;
        }
        List<Order> list = new ArrayList<>();
        for (Map<String, Object> o : MockData.orders) {
            Order e = new Order();
            e.setId(toLong(o.get("id")));
            e.setOrderNo((String) o.get("orderNo"));
            e.setUserId(toLong(o.get("userId")));
            e.setUserName((String) o.get("userName"));
            e.setMerchantId(toLong(o.get("merchantId")));
            e.setMerchantName((String) o.get("merchantName"));
            e.setProductId(toLong(o.get("productId")));
            e.setProductName((String) o.get("productName"));
            e.setProductImage((String) o.get("productImage"));
            e.setQuantity(toInt(o.get("quantity")));
            e.setPrice(toDouble(o.get("price")));
            e.setTotalAmount(toDouble(o.get("totalAmount")));
            e.setLscAmount(toDouble(o.get("lscAmount")));
            e.setRmbAmount(toDouble(o.get("rmbAmount")));
            // V6.2 新增字段
            e.setOrderType(toInt(o.get("orderType")));
            e.setPaymentType(toInt(o.get("paymentType")));
            e.setIsFirstOrder(toInt(o.get("isFirstOrder")));
            e.setStatus(toInt(o.get("status")));
            e.setStatusDesc((String) o.get("statusDesc"));
            e.setRefundLscAmount(toDouble(o.get("refundLscAmount")));
            e.setRefundRmbAmount(toDouble(o.get("refundRmbAmount")));
            e.setCreatedAt((String) o.get("createdAt"));
            e.setCompletedAt((String) o.get("completedAt"));
            list.add(e);
        }
        orderRepo.saveAll(list);
        System.out.println("[LSC DB] orders 表 seed 完成: " + list.size() + " 条");
    }

    private void seedB2BOrders() {
        if (b2bRepo.count() > 0) {
            System.out.println("[LSC DB] b2b_orders 表已存在 " + b2bRepo.count() + " 条，跳过 seed");
            return;
        }
        List<B2BOrder> list = new ArrayList<>();
        for (Map<String, Object> o : MockData.b2bOrders) {
            B2BOrder e = new B2BOrder();
            e.setId(toLong(o.get("id")));
            e.setOrderNo((String) o.get("orderNo"));
            e.setInitiatorId(toLong(o.get("initiatorId")));
            e.setInitiatorName((String) o.get("initiatorName"));
            e.setCounterpartyId(toLong(o.get("counterpartyId")));
            e.setCounterpartyName((String) o.get("counterpartyName"));
            // V6.2 新增字段
            e.setTradeDescription((String) o.get("tradeDescription"));
            e.setTotalAmountRmb(toDouble(o.get("totalAmountRmb")));
            e.setLscAmount(toDouble(o.get("lscAmount")));
            e.setRmbAmount(toDouble(o.get("rmbAmount")));
            e.setContractNo((String) o.get("contractNo"));
            e.setTradeEvidenceUrls((String) o.get("tradeEvidenceUrls"));
            e.setAiVerificationResult(toInt(o.get("aiVerificationResult")));
            e.setAiVerificationScore(toDouble(o.get("aiVerificationScore")));
            e.setCounterpartyConfirmed(toInt(o.get("counterpartyConfirmed")));
            e.setConfirmedBy((String) o.get("confirmedBy"));
            e.setConfirmedAt((String) o.get("confirmedAt"));
            e.setLscTransferred(toInt(o.get("lscTransferred")));
            e.setExpireAt((String) o.get("expireAt"));
            e.setIdempotentKey((String) o.get("idempotentKey"));
            e.setVersion(toInt(o.get("version")));
            e.setStatus(toInt(o.get("status")));
            e.setStatusDesc((String) o.get("statusDesc"));
            e.setCreatedAt((String) o.get("createdAt"));
            e.setCompletedAt((String) o.get("completedAt"));
            list.add(e);
        }
        b2bRepo.saveAll(list);
        System.out.println("[LSC DB] b2b_orders 表 seed 完成: " + list.size() + " 条");
    }

    private void seedWriteoffs() {
        if (writeoffRepo.count() > 0) {
            System.out.println("[LSC DB] writeoffs 表已存在 " + writeoffRepo.count() + " 条，跳过 seed");
            return;
        }
        List<Writeoff> list = new ArrayList<>();
        for (Map<String, Object> w : MockData.writeoffs) {
            Writeoff e = new Writeoff();
            e.setId(toLong(w.get("id")));
            e.setOrderNo((String) w.get("orderNo"));
            e.setMerchantId(toLong(w.get("merchantId")));
            e.setMerchantName((String) w.get("merchantName"));
            e.setLscAmount(toDouble(w.get("lscAmount")));
            // V6.2 三笔划拨
            e.setCashAmount(toDouble(w.get("cashAmount")));
            e.setPlatformFeeAmount(toDouble(w.get("platformFeeAmount")));
            e.setRetainedAmount(toDouble(w.get("retainedAmount")));
            e.setAvailableBefore(toDouble(w.get("availableBefore")));
            e.setAvailableAfter(toDouble(w.get("availableAfter")));
            e.setFundBefore(toDouble(w.get("fundBefore")));
            e.setFundAfter(toDouble(w.get("fundAfter")));
            e.setIdempotentKey((String) w.get("idempotentKey"));
            e.setVersion(toInt(w.get("version")));
            e.setStatus(toInt(w.get("status")));
            e.setStatusDesc((String) w.get("statusDesc"));
            e.setCreatedAt((String) w.get("createdAt"));
            e.setCompletedAt((String) w.get("completedAt"));
            list.add(e);
        }
        writeoffRepo.saveAll(list);
        System.out.println("[LSC DB] writeoffs 表 seed 完成: " + list.size() + " 条");
    }

    private void seedEvidence() {
        if (evidenceRepo.count() > 0) {
            System.out.println("[LSC DB] evidence_records 表已存在 " + evidenceRepo.count() + " 条，跳过 seed");
            return;
        }
        List<Evidence> list = new ArrayList<>();
        for (Map<String, Object> r : MockData.evidenceRecords) {
            Evidence e = new Evidence();
            e.setId(toLong(r.get("id")));
            e.setMerchantId(toLong(r.get("merchantId")));
            e.setMerchantName((String) r.get("merchantName"));
            e.setEvidenceHash((String) r.get("evidenceHash"));
            e.setBlockHeight(toLong(r.get("blockHeight")));
            e.setTxId((String) r.get("txId"));
            e.setStatus(toInt(r.get("status")));
            e.setCreatedAt((String) r.get("createdAt"));
            list.add(e);
        }
        evidenceRepo.saveAll(list);
        System.out.println("[LSC DB] evidence_records 表 seed 完成: " + list.size() + " 条");
    }

    private void seedLedgerTxns() {
        if (ledgerRepo.count() > 0) {
            System.out.println("[LSC DB] ledger_txns 表已存在 " + ledgerRepo.count() + " 条，跳过 seed");
            return;
        }
        List<LedgerTxn> list = new ArrayList<>();
        for (Map<String, Object> t : MockData.ledgerTxns) {
            LedgerTxn e = new LedgerTxn();
            e.setId(toLong(t.get("id")));
            e.setUserId(toLong(t.get("userId")));
            e.setType(toInt(t.get("type")));
            e.setTypeStr((String) t.get("typeStr"));
            e.setAmount(toLong(t.get("amount")));
            e.setBeforeLocked(toLong(t.get("beforeLocked")));
            e.setAfterLocked(toLong(t.get("afterLocked")));
            e.setBeforeAvailable(toLong(t.get("beforeAvailable")));
            e.setAfterAvailable(toLong(t.get("afterAvailable")));
            e.setCounterpartyId(toLong(t.get("counterpartyId")));
            e.setOrderNo((String) t.get("orderNo"));
            e.setIdempotentKey((String) t.get("idempotentKey"));
            e.setBalance(toDouble(t.get("balance")));
            e.setRemark((String) t.get("remark"));
            e.setEvidenceHash((String) t.get("evidenceHash"));
            e.setCreatedAt((String) t.get("createdAt"));
            list.add(e);
        }
        ledgerRepo.saveAll(list);
        System.out.println("[LSC DB] ledger_txns 表 seed 完成: " + list.size() + " 条");
    }

    private void seedRiskLogs() {
        if (riskRepo.count() > 0) {
            System.out.println("[LSC DB] risk_logs 表已存在 " + riskRepo.count() + " 条，跳过 seed");
            return;
        }
        List<RiskLog> list = new ArrayList<>();
        for (Map<String, Object> r : MockData.riskLogs) {
            RiskLog e = new RiskLog();
            e.setId(toLong(r.get("id")));
            e.setUserId(toLong(r.get("userId")));
            e.setMerchantId(toLong(r.get("merchantId")));
            e.setMerchantName((String) r.get("merchantName"));
            e.setLevel((String) r.get("level"));
            e.setLevelCode(toInt(r.get("levelCode")));
            e.setType((String) r.get("type"));
            e.setRemark((String) r.get("remark"));
            e.setStatus(toInt(r.get("status")));
            e.setContent((String) r.get("content"));
            e.setCreatedAt((String) r.get("createdAt"));
            list.add(e);
        }
        riskRepo.saveAll(list);
        System.out.println("[LSC DB] risk_logs 表 seed 完成: " + list.size() + " 条");
    }

    /**
     * V6.2 LSC 账户初始化（lsc_accounts）
     * 为商家(10001-10012)和消费者(20001-20030)创建LSC账户
     */
    private void seedLscAccounts() {
        if (lscAccountRepo.count() > 0) {
            System.out.println("[LSC DB] lsc_accounts 表已存在 " + lscAccountRepo.count() + " 条，跳过 seed");
            return;
        }
        List<LscAccount> list = new ArrayList<>();
        // 商家LSC账户
        for (int i = 0; i < 12; i++) {
            LscAccount a = new LscAccount();
            a.setUserId(10001L + i);
            a.setTotalLocked(50000L + (long)(Math.random() * 100000));
            a.setTotalAvailable(5000L + (long)(Math.random() * 20000));
            a.setVersion(1);
            a.setUpdatedAt(java.time.LocalDateTime.now().toString());
            list.add(a);
        }
        // 消费者LSC账户
        for (int i = 0; i < 30; i++) {
            LscAccount a = new LscAccount();
            a.setUserId((long)(20001 + i));
            a.setTotalLocked(20000L + (long)(Math.random() * 50000));
            a.setTotalAvailable(3000L + (long)(Math.random() * 10000));
            a.setVersion(1);
            a.setUpdatedAt(java.time.LocalDateTime.now().toString());
            list.add(a);
        }
        lscAccountRepo.saveAll(list);
        System.out.println("[LSC DB] lsc_accounts 表 seed 完成: " + list.size() + " 条");
    }

    /**
     * V6.2 释放比例配置初始化（release_config）
     * 预置：rate_max=0.06%不可编辑, rate_min=0.03%不可编辑, k_min=0.50%可配置, k_max=1.0%可配置, alpha=0.06可配置
     */
    private void seedReleaseConfigs() {
        if (releaseConfigRepo.count() > 0) {
            System.out.println("[LSC DB] release_config 表已存在 " + releaseConfigRepo.count() + " 条，跳过 seed");
            return;
        }
        List<ReleaseConfig> list = new ArrayList<>();
        String now = java.time.LocalDateTime.now().toString();

        ReleaseConfig rateMax = new ReleaseConfig();
        rateMax.setConfigKey("rate_max");
        rateMax.setConfigValue("0.0006");
        rateMax.setEditable(0);
        rateMax.setDescription("释放速率上限0.06%（硬常量，不可修改）");
        rateMax.setUpdatedAt(now);
        list.add(rateMax);

        ReleaseConfig rateMin = new ReleaseConfig();
        rateMin.setConfigKey("rate_min");
        rateMin.setConfigValue("0.0003");
        rateMin.setEditable(0);
        rateMin.setDescription("释放速率下限0.03%（硬常量，不可修改）");
        rateMin.setUpdatedAt(now);
        list.add(rateMin);

        ReleaseConfig kMin = new ReleaseConfig();
        kMin.setConfigKey("k_min");
        kMin.setConfigValue("0.005");
        kMin.setEditable(1);
        kMin.setDescription("调节起点k_min=0.50%（可配置，需双重管理员审批）");
        kMin.setUpdatedAt(now);
        list.add(kMin);

        ReleaseConfig kMax = new ReleaseConfig();
        kMax.setConfigKey("k_max");
        kMax.setConfigValue("0.01");
        kMax.setEditable(1);
        kMax.setDescription("调节终点k_max=1.0%（可配置，需双重管理员审批）");
        kMax.setUpdatedAt(now);
        list.add(kMax);

        ReleaseConfig alpha = new ReleaseConfig();
        alpha.setConfigKey("alpha");
        alpha.setConfigValue("0.06");
        alpha.setEditable(1);
        alpha.setDescription("调节因子alpha=0.06（可配置，需双重管理员审批）");
        alpha.setUpdatedAt(now);
        list.add(alpha);

        releaseConfigRepo.saveAll(list);
        System.out.println("[LSC DB] release_config 表 seed 完成: " + list.size() + " 条");
    }

    /**
     * V6.2 每日释放汇总初始化（daily_release_summary）
     * 生成最近7天的释放记录
     */
    private void seedDailyReleaseSummary() {
        if (dailyReleaseRepo.count() > 0) {
            System.out.println("[LSC DB] daily_release_summary 表已存在 " + dailyReleaseRepo.count() + " 条，跳过 seed");
            return;
        }
        List<DailyReleaseSummary> list = new ArrayList<>();
        java.util.Random r = new java.util.Random(42);
        for (int i = 7; i > 0; i--) {
            java.time.LocalDate date = java.time.LocalDate.now().minusDays(i);
            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setId((long)(8 - i));
            s.setDate(date.toString());
            double mTotal = 9800000 + r.nextInt(200000);
            double nTotal = r.nextInt(50000);
            double k = nTotal / mTotal;
            // V6.2 动态释放算法：k<=0.5% → rate=0.06%, k>=1.0% → rate=0.03%, 中间 → 0.09% - 0.06*k
            double rate;
            if (k <= 0.005) {
                rate = 0.0006;
            } else if (k >= 0.01) {
                rate = 0.0003;
            } else {
                rate = 0.0009 - 0.06 * k;
            }
            long lLocked = 8000000 + r.nextInt(2000000);
            long tRelease = (long)(lLocked * rate);
            s.setMTotal(mTotal);
            s.setNTotal(nTotal);
            s.setK(k);
            s.setRate(rate);
            s.setLLocked(lLocked);
            s.setTRelease(tRelease);
            s.setBatchCount(10);
            s.setFailedBatchCount(0);
            s.setAiPredictedK7d(k + (r.nextDouble() - 0.5) * 0.002);
            s.setAiPredictedK30d(k + (r.nextDouble() - 0.5) * 0.004);
            s.setStatus(2); // 完成
            s.setCreatedAt(date.atStartOfDay().toString());
            s.setUpdatedAt(java.time.LocalDateTime.now().toString());
            list.add(s);
        }
        dailyReleaseRepo.saveAll(list);
        System.out.println("[LSC DB] daily_release_summary 表 seed 完成: " + list.size() + " 条");
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.valueOf(v.toString());
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.valueOf(v.toString());
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return Double.valueOf(v.toString());
    }
}
