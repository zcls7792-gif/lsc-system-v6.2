package com.lianshengtong.api.data;

import java.util.*;

/**
 * 内存数据层 - 与 lsc-mock-server/data.js 保持一致的业务快照
 */
public class MockData {

    public static final List<Map<String, Object>> merchants = new ArrayList<>();
    public static final List<Map<String, Object>> products = new ArrayList<>();
    public static final List<Map<String, Object>> categories = new ArrayList<>();
    public static final List<Map<String, Object>> orders = new ArrayList<>();
    public static final List<Map<String, Object>> b2bOrders = new ArrayList<>();
    public static final List<Map<String, Object>> writeoffs = new ArrayList<>();
    public static final List<Map<String, Object>> ledgerTxns = new ArrayList<>();
    public static final List<Map<String, Object>> admins = new ArrayList<>();
    public static final List<Map<String, Object>> riskLogs = new ArrayList<>();
    public static final List<Map<String, Object>> evidenceRecords = new ArrayList<>();

    private static final Random R = new Random(42);
    private static int seq = 1;

    private static String now() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static void init() {
        initAdmins();
        initMerchants();
        initCategories();
        initProducts();
        initOrders();
        initB2bOrders();
        initWriteoffs();
        initLedgerTxns();
        initRiskLogs();
        initEvidence();
    }

    private static void initAdmins() {
        admins.add(map("id", 1, "username", "admin", "password", "123456",
                "name", "超级管理员", "role", "超级管理员", "status", 1, "createdAt", now()));
        admins.add(map("id", 2, "username", "operator", "password", "123456",
                "name", "运营专员", "role", "运营", "status", 1, "createdAt", now()));
    }

    private static void initMerchants() {
        String[] names = {"盛源百货商行", "优选生鲜超市", "华夏餐饮连锁", "云裳服饰工厂", "金石建材批发",
                "绿野农产品合作社", "尚品家电卖场", "瑞康医药连锁", "锦程物流供应链", "鼎盛五金机电",
                "明珠家居广场", "佳味食品加工厂"};
        String[] contacts = {"李经理", "王主管", "张总", "陈店长", "刘老板"};
        String[] provinces = {"浙江省", "广东省", "江苏省", "四川省", "北京市", "上海市"};
        String[] cities = {"杭州市", "深圳市", "南京市", "成都市", "北京市", "上海市"};
        String[] districts = {"西湖区", "南山区", "玄武区", "武侯区", "朝阳区", "浦东新区"};
        int[] credits = {100, 95, 88, 75, 60, 40};
        for (int i = 0; i < 12; i++) {
            int status = i < 3 ? 0 : i < 5 ? 1 : i == 5 ? 2 : 1;
            int credit = credits[i % 6];
            merchants.add(map(
                    "id", 10001 + i, "userId", 10001 + i,
                    "merchantName", names[i], "name", names[i],
                    "contact", contacts[i % 5], "storeName", names[i] + "(总店)",
                    "mobile", "1380000" + String.format("%04d", 1000 + i),
                    "phone", "1380000" + String.format("%04d", 1000 + i),
                    "auditStatus", status, "status", status,
                    "creditScore", credit, "aiRiskScore", 20 + R.nextInt(60),
                    "monthlyRevenue", new int[]{0, 120000, 350000, 80000, 1500000, 5000000}[i % 6],
                    "nhLimitLevel", "ABCDEFGHIJKL".charAt(i % 12) + "",
                    "dailyNhLimit", new int[]{80, 275, 550, 1100, 1650, 2200, 2750, 3300, 3850, 4400, 4950, 5500}[i],
                    "penaltyStatus", credit < 40 ? 3 : credit < 60 ? 2 : credit < 80 ? 1 : 0,
                    "province", provinces[i % 6], "city", cities[i % 6], "district", districts[i % 6],
                    "addressDetail", "文三路" + (1 + R.nextInt(200)) + "号",
                    "aiAddressVerified", i % 3 == 0 ? 0 : 1,
                    "longitude", 120.0 + R.nextInt(200) / 100.0,
                    "latitude", 30.0 + R.nextInt(100) / 100.0,
                    "businessHours", "09:00-22:00",
                    "createdAt", now(), "created_at", now()
            ));
        }
    }

    private static void initCategories() {
        String[][] cats = {
                {"1", "食品生鲜", "🍎"}, {"2", "服饰鞋包", "👕"}, {"3", "家居家纺", "🛋️"},
                {"4", "数码电器", "📱"}, {"5", "美妆个护", "💄"}, {"6", "母婴玩具", "🧸"},
                {"7", "酒水茶饮", "🍵"}, {"8", "工业品", "🔧"}
        };
        for (String[] c : cats) {
            categories.add(map("id", Integer.parseInt(c[0]), "name", c[1], "icon", c[2], "parentId", 0));
        }
    }

    private static void initProducts() {
        String[] names = {"有机五常大米 5kg", "新疆和田大枣 500g", "澳洲进口牛排套装", "纯棉四件套 1.8m",
                "智能扫地机器人", "304不锈钢保温杯", "云南小粒咖啡豆 1kg", "手工竹纤维毛巾 10条装",
                "景德镇青花瓷餐具套装", "云南普洱茶饼 357g", "儿童益智积木玩具", "运动跑步鞋 男款",
                "无线蓝牙耳机", "天然乳胶枕头", "东北黑木耳 250g", "阳澄湖大闸蟹礼盒",
                "法国进口红酒 750ml", "泰国天然乳胶床垫", "华为 Mate 手机壳", "小米空气净化器滤芯"};
        double[] prices = {59.9, 89, 268, 199, 1299, 69, 128, 49, 358, 168, 159, 399, 199, 129, 78, 588, 328, 2999, 49, 189};
        for (int i = 0; i < 20; i++) {
            int status = i < 4 ? 2 : i < 16 ? 1 : 0;
            String img = "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=" +
                    java.net.URLEncoder.encode(names[i] + " 商品主图 白底 电商摄影") + "&image_size=square";
            products.add(map(
                    "id", 2001 + i, "merchantId", 10001 + (i % 12),
                    "merchantName", merchants.get(i % 12).get("merchantName"),
                    "productName", names[i], "name", names[i],
                    "productDesc", "精选优质" + names[i] + "，产地直供，品质保障。支持人民币与LSC 1:1混合支付。",
                    "description", "精选优质" + names[i],
                    "productImages", List.of(img), "cover", img, "images", List.of(img),
                    "price", prices[i], "lscPrice", prices[i],
                    "stock", 50 + R.nextInt(949), "sales", 10 + R.nextInt(4990),
                    "categoryId", 1 + (i % 8), "status", status,
                    "aiReviewResult", i < 4 ? (i % 2 == 0 ? 1 : 0) : 0,
                    "createdAt", now(), "created_at", now()
            ));
        }
    }

    private static void initOrders() {
        String[] statusDesc = {"待支付", "已支付", "已完成", "已取消", "已退款", "部分退款"};
        for (int i = 0; i < 30; i++) {
            Map<String, Object> prod = products.get(i % products.size());
            int qty = 1 + R.nextInt(3);
            double total = Math.round(((Number) prod.get("price")).doubleValue() * qty * 100) / 100.0;
            int payType = i % 3;
            double lscAmt = payType == 0 ? 0 : payType == 1 ? Math.floor(total) : Math.floor(total * 0.5);
            double rmbAmt = Math.round((total - lscAmt) * 100) / 100.0;
            int status = i % 6;
            // V6.2 订单类型：0纯RMB 1 LSC全额 2混合
            int orderType = payType == 0 ? 0 : payType == 1 ? 1 : 2;
            // V6.2 首单标记：第一个用户的订单为首单
            int isFirstOrder = (i == 0) ? 1 : 0;
            orders.add(map(
                    "id", i + 1, "orderNo", "LS" + System.currentTimeMillis() + i,
                    "userId", 10001 + (i % 12), "userName", "用户" + (i % 12),
                    "merchantId", prod.get("merchantId"), "merchantName", prod.get("merchantName"),
                    "productId", prod.get("id"), "productName", prod.get("name"),
                    "productImage", prod.get("cover"),
                    "quantity", qty, "price", prod.get("price"),
                    "totalAmount", total, "lscAmount", lscAmt, "rmbAmount", rmbAmt,
                    "orderType", orderType, "paymentType", payType,
                    "isFirstOrder", isFirstOrder,
                    "status", status, "statusDesc", statusDesc[status],
                    "refundLscAmount", 0.0, "refundRmbAmount", 0.0,
                    "createdAt", now(), "created_at", now(),
                    "completedAt", status == 2 ? now() : null
            ));
        }
    }

    private static void initB2bOrders() {
        // V6.2 状态：0待确认 1已确认 2已流转 3已完成 4已取消 5已作废
        String[] statusDesc = {"待确认", "已确认", "已流转", "已完成", "已取消", "已作废"};
        String[] tradeDescs = {"食品原料采购", "日用品批发", "建材供应链采购", "电子产品批发", "农产品收购"};
        for (int i = 0; i < 15; i++) {
            double amt = Math.round((1000 + R.nextInt(50000)) * 100) / 100.0;
            int status = i % 6;
            b2bOrders.add(map(
                    "id", i + 1, "orderNo", "B2B" + System.currentTimeMillis() + i,
                    "initiatorId", 10001 + (i % 6), "initiatorName", merchants.get(i % 6).get("name"),
                    "counterpartyId", 10001 + ((i + 3) % 6), "counterpartyName", merchants.get((i + 3) % 6).get("name"),
                    "tradeDescription", tradeDescs[i % tradeDescs.length],
                    "totalAmountRmb", amt, "lscAmount", Math.floor(amt), "rmbAmount", 0,
                    "contractNo", "CN-" + (1000 + i),
                    "tradeEvidenceUrls", "/files/contract" + i + ".pdf",
                    "aiVerificationResult", i % 5 == 0 ? 1 : 0, "aiVerificationScore", 80 + R.nextInt(20),
                    "counterpartyConfirmed", status >= 1 ? 1 : 0,
                    "confirmedBy", status >= 1 ? merchants.get((i + 3) % 6).get("name") : null,
                    "confirmedAt", status >= 1 ? now() : null,
                    "lscTransferred", status >= 2 ? 1 : 0,
                    "expireAt", java.time.LocalDateTime.now().plusDays(7).toString(),
                    "idempotentKey", "B2B-" + i, "version", 1,
                    "status", status, "statusDesc", statusDesc[status],
                    "createdAt", now(), "completedAt", status == 3 ? now() : null
            ));
        }
    }

    private static void initWriteoffs() {
        // V6.2 状态：0待处理 1处理中 2成功 3失败
        String[] statusDesc = {"待处理", "处理中", "成功", "失败"};
        for (int i = 0; i < 20; i++) {
            int status = i % 4;
            double lsc = 100 + R.nextInt(9900);
            // V6.2 三笔划拨：87% + 3% + 10%
            double cash = Math.round(lsc * 0.87 * 100) / 100.0;
            double fee = Math.round(lsc * 0.03 * 100) / 100.0;
            double retained = Math.round(lsc * 0.10 * 100) / 100.0;
            writeoffs.add(map(
                    "id", i + 1, "orderNo", "WO" + System.currentTimeMillis() + i,
                    "merchantId", 10001 + (i % 12), "merchantName", merchants.get(i % 12).get("name"),
                    "lscAmount", lsc,
                    "cashAmount", cash, "platformFeeAmount", fee, "retainedAmount", retained,
                    "availableBefore", lsc + 10000, "availableAfter", 10000.0,
                    "fundBefore", 50000.0, "fundAfter", 50000.0 + cash,
                    "idempotentKey", "NH-" + (i + 1), "version", 1,
                    "status", status, "statusDesc", statusDesc[status],
                    "createdAt", now(), "completedAt", status == 2 ? now() : null
            ));
        }
    }

    private static void initLedgerTxns() {
        String[] types = {"释放", "核销", "消费", "退款", "转入", "转出"};
        for (int i = 0; i < 25; i++) {
            int typeIdx = i % 6;
            ledgerTxns.add(map(
                    "id", i + 1, "userId", 10001 + (i % 12),
                    "type", types[typeIdx], "typeCode", typeIdx,
                    "amount", (typeIdx % 2 == 0 ? 1 : -1) * (100 + R.nextInt(5000)),
                    "balance", 10000 + R.nextInt(100000),
                    "remark", types[typeIdx] + "流水",
                    "createdAt", now()
            ));
        }
    }

    private static void initRiskLogs() {
        String[] levels = {"低", "中", "高"};
        for (int i = 0; i < 15; i++) {
            riskLogs.add(map(
                    "id", i + 1, "merchantId", 10001 + (i % 12),
                    "merchantName", merchants.get(i % 12).get("name"),
                    "level", levels[i % 3], "levelCode", i % 3,
                    "content", "风控规则触发：异常交易监测",
                    "createdAt", now()
            ));
        }
    }

    private static void initEvidence() {
        for (int i = 0; i < 12; i++) {
            evidenceRecords.add(map(
                    "id", i + 1, "merchantId", 10001 + (i % 12),
                    "merchantName", merchants.get(i % 12).get("name"),
                    "evidenceHash", "0x" + UUID.randomUUID().toString().replace("-", ""),
                    "blockHeight", 100000 + i, "txId", "TX" + i,
                    "status", i % 2 == 0 ? 1 : 0,
                    "createdAt", now()
            ));
        }
    }

    @SafeVarargs
    private static Map<String, Object> map(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((String) kvs[i], kvs[i + 1]);
        }
        return m;
    }
}
