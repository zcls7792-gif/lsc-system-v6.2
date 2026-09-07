package com.lianshengtong.api.data;

import com.lianshengtong.api.entity.Merchant;
import com.lianshengtong.api.entity.Order;
import com.lianshengtong.api.entity.Product;
import com.lianshengtong.api.repository.MerchantRepository;
import com.lianshengtong.api.repository.OrderRepository;
import com.lianshengtong.api.repository.ProductRepository;
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

    public DataInit(MerchantRepository merchantRepo,
                    ProductRepository productRepo,
                    OrderRepository orderRepo) {
        this.merchantRepo = merchantRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
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
