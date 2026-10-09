package com.zcls.lsc.product;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.product.enums.ProductEnums.ProductStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 第12.3章 商品服务。
 *
 *  - 商品状态机：DRAFT -> REVIEWING -> ON_SALE / OFF_SALE / SOLD_OUT
 *  - 上架必须经过审核（audit_version 递增）；下架可直接操作
 *  - 在售商品不可删除（软删除用 OFF_SALE）；删除仅允许 DRAFT/REVIEWING 状态
 *  - 商品绑定 seller_entity_id（法律主体），用于开票与结算
 */
@Service
public class ProductService {

    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建商品（草稿）。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createProduct(long sellerEntityId, String name, long categoryId,
                              String returnPolicyVersion, String descriptionRef) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "product name is required");
        }
        long productId = nextId();
        jdbc.update(
                "INSERT INTO product(product_id, seller_entity_id, name, category_id, status, "
                        + "audit_version, description_ref, return_policy_version) "
                        + "VALUES(?,?,?,?,'DRAFT',0,?,?)",
                productId, sellerEntityId, name, categoryId, descriptionRef, returnPolicyVersion);
        return productId;
    }

    /**
     * 更新商品基本信息（仅 DRAFT/REVIEWING 可编辑核心字段）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(long productId, String name, String descriptionRef) {
        String status = jdbc.queryForObject(
                "SELECT status FROM product WHERE product_id=?", String.class, productId);
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "product not found: " + productId);
        }
        if (ProductStatus.ON_SALE.name().equals(status) || ProductStatus.SOLD_OUT.name().equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "cannot edit product in status " + status);
        }
        jdbc.update(
                "UPDATE product SET name=COALESCE(?,name), description_ref=COALESCE(?,description_ref) "
                        + "WHERE product_id=?",
                name, descriptionRef, productId);
    }

    /**
     * 提交审核（DRAFT -> REVIEWING，audit_version+1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(long productId) {
        int updated = jdbc.update(
                "UPDATE product SET status='REVIEWING', audit_version=audit_version+1 "
                        + "WHERE product_id=? AND status='DRAFT'",
                productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "product not in DRAFT status or not found: " + productId);
        }
    }

    /**
     * 审核通过并上架（REVIEWING -> ON_SALE）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveAndOnSale(long productId) {
        int updated = jdbc.update(
                "UPDATE product SET status='ON_SALE' WHERE product_id=? AND status='REVIEWING'",
                productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "product not in REVIEWING status or not found: " + productId);
        }
    }

    /**
     * 审核驳回（REVIEWING -> DRAFT）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectToDraft(long productId) {
        int updated = jdbc.update(
                "UPDATE product SET status='DRAFT' WHERE product_id=? AND status='REVIEWING'",
                productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "product not in REVIEWING status or not found: " + productId);
        }
    }

    /**
     * 下架（ON_SALE -> OFF_SALE）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void offSale(long productId) {
        int updated = jdbc.update(
                "UPDATE product SET status='OFF_SALE' WHERE product_id=? AND status='ON_SALE'",
                productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "product not in ON_SALE status or not found: " + productId);
        }
    }

    /**
     * 重新上架（OFF_SALE -> ON_SALE）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onSale(long productId) {
        int updated = jdbc.update(
                "UPDATE product SET status='ON_SALE' WHERE product_id=? AND status='OFF_SALE'",
                productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "product not in OFF_SALE status or not found: " + productId);
        }
    }

    /**
     * 标记售罄（ON_SALE -> SOLD_OUT）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSoldOut(long productId) {
        int updated = jdbc.update(
                "UPDATE product SET status='SOLD_OUT' WHERE product_id=? AND status='ON_SALE'",
                productId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "product not in ON_SALE status or not found: " + productId);
        }
    }

    /**
     * 查询商品。
     */
    public ProductRow getProduct(long productId) {
        List<ProductRow> rows = jdbc.query(
                "SELECT product_id, seller_entity_id, name, category_id, status, audit_version, "
                        + "description_ref, return_policy_version FROM product WHERE product_id=?",
                (rs, rowNum) -> new ProductRow(
                        rs.getLong("product_id"),
                        rs.getLong("seller_entity_id"),
                        rs.getString("name"),
                        rs.getLong("category_id"),
                        rs.getString("status"),
                        rs.getLong("audit_version"),
                        rs.getString("description_ref"),
                        rs.getString("return_policy_version")),
                productId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "product not found: " + productId);
        }
        return rows.get(0);
    }

    /**
     * 按状态分页查询商品。
     */
    public List<ProductRow> listByStatus(ProductStatus status, int limit, long offset) {
        return jdbc.query(
                "SELECT product_id, seller_entity_id, name, category_id, status, audit_version, "
                        + "description_ref, return_policy_version FROM product "
                        + "WHERE status=? ORDER BY product_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new ProductRow(
                        rs.getLong("product_id"),
                        rs.getLong("seller_entity_id"),
                        rs.getString("name"),
                        rs.getLong("category_id"),
                        rs.getString("status"),
                        rs.getLong("audit_version"),
                        rs.getString("description_ref"),
                        rs.getString("return_policy_version")),
                status.name(), limit, offset);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record ProductRow(long productId, long sellerEntityId, String name, long categoryId,
                             String status, long auditVersion, String descriptionRef,
                             String returnPolicyVersion) {}
}
