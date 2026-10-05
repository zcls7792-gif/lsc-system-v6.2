package com.zcls.lsc.product.enums;

/**
 * 第12.3章 商品主数据枚举。
 */
public final class ProductEnums {

    private ProductEnums() {}

    /** 商品状态。 */
    public enum ProductStatus {
        /** 草稿 */
        DRAFT,
        /** 审核中 */
        REVIEWING,
        /** 在售 */
        ON_SALE,
        /** 下架 */
        OFF_SALE,
        /** 售罄 */
        SOLD_OUT
    }

    /** SKU 状态。 */
    public enum SkuStatus { ACTIVE, INACTIVE }

    /** 销售单位。 */
    public enum SaleUnit {
        /** 件 */
        PIECE,
        /** 盒 */
        BOX,
        /** 瓶 */
        BOTTLE,
        /** 袋 */
        BAG,
        /** 箱 */
        CARTON,
        /** 千克 */
        KG,
        /** 克 */
        G
    }

    /** 成本口径。 */
    public enum CostBasisCode {
        /** 含税成本 */
        TAX_INCLUDED,
        /** 不含税成本 */
        TAX_EXCLUDED
    }

    /** 审核决定。 */
    public enum AuditDecision {
        /** 通过 */
        APPROVED,
        /** 驳回 */
        REJECTED,
        /** 需修改 */
        NEEDS_REVISION
    }
}
