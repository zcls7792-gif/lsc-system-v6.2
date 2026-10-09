package com.zcls.lsc.product;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.product.enums.ProductEnums.SaleUnit;
import com.zcls.lsc.product.enums.ProductEnums.SkuStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 第12.3章 SKU 服务。
 *
 *  - SKU 编码全局唯一（uk_product_sku_code）
 *  - spec_json 存储规格（颜色/尺码等），由调用方保证合法 JSON
 *  - pack_qty 箱规（B端整箱采购单位）；b_min_qty B端最小采购量
 *  - sale_unit 销售单位，影响 C 端展示与 B 端起订
 *  - SKU 状态 ACTIVE/INACTIVE；INACTIVE 不参与报价
 */
@Service
public class SkuService {

    private final JdbcTemplate jdbc;

    public SkuService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建 SKU。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createSku(long productId, String skuCode, String specJson,
                          SaleUnit saleUnit, int packQty, int bMinQty) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sku_code is required");
        }
        if (packQty <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "pack_qty must be positive");
        }
        if (bMinQty <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "b_min_qty must be positive");
        }
        long skuId = nextId();
        try {
            jdbc.update(
                    "INSERT INTO product_sku(sku_id, product_id, sku_code, spec_json, sale_unit, "
                            + "pack_qty, b_min_qty, status) VALUES(?,?,?,?,?,?,?,'ACTIVE')",
                    skuId, productId, skuCode, specJson,
                    saleUnit == null ? SaleUnit.PIECE.name() : saleUnit.name(),
                    packQty, bMinQty);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "sku_code already exists: " + skuCode);
        }
        return skuId;
    }

    /**
     * 更新 SKU 规格与箱规。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSku(long skuId, String specJson, SaleUnit saleUnit,
                          Integer packQty, Integer bMinQty) {
        StringBuilder sql = new StringBuilder("UPDATE product_sku SET ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (specJson != null) { sql.append("spec_json=?,"); args.add(specJson); }
        if (saleUnit != null) { sql.append("sale_unit=?,"); args.add(saleUnit.name()); }
        if (packQty != null) {
            if (packQty <= 0) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "pack_qty must be positive");
            sql.append("pack_qty=?,"); args.add(packQty);
        }
        if (bMinQty != null) {
            if (bMinQty <= 0) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "b_min_qty must be positive");
            sql.append("b_min_qty=?,"); args.add(bMinQty);
        }
        if (args.isEmpty()) return; // 无更新
        sql.deleteCharAt(sql.length() - 1); // 去掉末尾逗号
        sql.append(" WHERE sku_id=?");
        args.add(skuId);
        int updated = jdbc.update(sql.toString(), args.toArray());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sku not found: " + skuId);
        }
    }

    /**
     * 切换 SKU 状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(long skuId, SkuStatus status) {
        int updated = jdbc.update(
                "UPDATE product_sku SET status=? WHERE sku_id=?",
                status.name(), skuId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sku not found: " + skuId);
        }
    }

    /**
     * 查询 SKU。
     */
    public SkuRow getSku(long skuId) {
        List<SkuRow> rows = jdbc.query(
                "SELECT sku_id, product_id, sku_code, spec_json, sale_unit, pack_qty, "
                        + "b_min_qty, status FROM product_sku WHERE sku_id=?",
                (rs, rowNum) -> new SkuRow(
                        rs.getLong("sku_id"),
                        rs.getLong("product_id"),
                        rs.getString("sku_code"),
                        rs.getString("spec_json"),
                        rs.getString("sale_unit"),
                        rs.getInt("pack_qty"),
                        rs.getInt("b_min_qty"),
                        rs.getString("status")),
                skuId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sku not found: " + skuId);
        }
        return rows.get(0);
    }

    /**
     * 列出商品下所有 SKU。
     */
    public List<SkuRow> listByProduct(long productId) {
        return jdbc.query(
                "SELECT sku_id, product_id, sku_code, spec_json, sale_unit, pack_qty, "
                        + "b_min_qty, status FROM product_sku WHERE product_id=? ORDER BY sku_id",
                (rs, rowNum) -> new SkuRow(
                        rs.getLong("sku_id"),
                        rs.getLong("product_id"),
                        rs.getString("sku_code"),
                        rs.getString("spec_json"),
                        rs.getString("sale_unit"),
                        rs.getInt("pack_qty"),
                        rs.getInt("b_min_qty"),
                        rs.getString("status")),
                productId);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record SkuRow(long skuId, long productId, String skuCode, String specJson,
                         String saleUnit, int packQty, int bMinQty, String status) {}
}
