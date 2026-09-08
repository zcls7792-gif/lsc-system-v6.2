package com.lianshengtong.api.data;

import com.lianshengtong.api.entity.B2BOrder;
import com.lianshengtong.api.entity.Evidence;
import com.lianshengtong.api.entity.LedgerTxn;
import com.lianshengtong.api.entity.Merchant;
import com.lianshengtong.api.entity.Order;
import com.lianshengtong.api.entity.Product;
import com.lianshengtong.api.entity.RiskLog;
import com.lianshengtong.api.entity.Writeoff;
import com.lianshengtong.api.repository.B2BOrderRepository;
import com.lianshengtong.api.repository.EvidenceRepository;
import com.lianshengtong.api.repository.LedgerTxnRepository;
import com.lianshengtong.api.repository.MerchantRepository;
import com.lianshengtong.api.repository.OrderRepository;
import com.lianshengtong.api.repository.ProductRepository;
import com.lianshengtong.api.repository.RiskLogRepository;
import com.lianshengtong.api.repository.WriteoffRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动数据初始化：
 * 1. 调用 MockData.init() 初始化内存数据（为未持久化的表提供数据，也为 Seeder 提供源数据）
 * 2. 若 Merchant/Product/Order 三张表为空，从 MockData 同步到 H2（仅首次启动）
 * 3. 后续重启直接从 H2 读取已持久化数据，MockData 仅作为其他表的内存源
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

    public DataInit(MerchantRepository merchantRepo,
                    ProductRepository productRepo,
                    OrderRepository orderRepo,
                    B2BOrderRepository b2bRepo,
                    WriteoffRepository writeoffRepo,
                    EvidenceRepository evidenceRepo,
                    LedgerTxnRepository ledgerRepo,
                    RiskLogRepository riskRepo) {
        this.merchantRepo = merchantRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.b2bRepo = b2bRepo;
        this.writeoffRepo = writeoffRepo;
        this.evidenceRepo = evidenceRepo;
        this.ledgerRepo = ledgerRepo;
        this.riskRepo = riskRepo;
    }

    @Override
    public void run(String... args) {
        MockData.init();
        System.out.println("[LSC API] 内存数据初始化完成: 商家" + MockData.merchants.size()
                + " 商品" + MockData.products.size()
                + " 订单" + MockData.orders.size());

        seedMerchants();
        seedProducts();
        seedOrders();
        seedB2BOrders();
        seedWriteoffs();
        seedEvidence();
        seedLedgerTxns();
        seedRiskLogs();
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
            e.setPaymentType(toInt(o.get("paymentType")));
            e.setStatus(toInt(o.get("status")));
            e.setStatusDesc((String) o.get("statusDesc"));
            e.setCreatedAt((String) o.get("createdAt"));
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
            e.setLscAmount(toDouble(o.get("lscAmount")));
            e.setRmbAmount(toDouble(o.get("rmbAmount")));
            e.setStatus(toInt(o.get("status")));
            e.setStatusDesc((String) o.get("statusDesc"));
            e.setCreatedAt((String) o.get("createdAt"));
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
            e.setStatus(toInt(w.get("status")));
            e.setStatusDesc((String) w.get("statusDesc"));
            e.setCreatedAt((String) w.get("createdAt"));
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
            e.setType((String) t.get("type"));
            e.setTypeCode(toInt(t.get("typeCode")));
            e.setAmount(toDouble(t.get("amount")));
            e.setBalance(toDouble(t.get("balance")));
            e.setRemark((String) t.get("remark"));
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
            e.setMerchantId(toLong(r.get("merchantId")));
            e.setMerchantName((String) r.get("merchantName"));
            e.setLevel((String) r.get("level"));
            e.setLevelCode(toInt(r.get("levelCode")));
            e.setContent((String) r.get("content"));
            e.setCreatedAt((String) r.get("createdAt"));
            list.add(e);
        }
        riskRepo.saveAll(list);
        System.out.println("[LSC DB] risk_logs 表 seed 完成: " + list.size() + " 条");
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
