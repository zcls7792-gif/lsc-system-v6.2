package com.zcls.lsc.product;

import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.product.enums.ProductEnums.CostBasisCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 第12.3章 商品价格版本服务。
 *
 *  - 价格版本不可修改：新建版本覆盖旧版本，effective_at 控制生效时间
 *  - 成本价 cost_price_enc 使用 AES 加密存储（KMS 抽象，此处用对称密钥占位）
 *  - 赠送系数 grant_coefficient_ppm 范围 [0, 1_000_000]，DB CHECK 兜底
 *  - 价格版本需双签：approved_by 非空且不等于创建者（由调用方保证）
 *  - grant_c_unit / grant_b_unit 为展示用赠送基准，实际按公式计算
 */
@Service
public class PriceVersionService {

    /** AES 密钥版本（生产环境应从 KMS 获取）。 */
    public static final String COST_KEY_VERSION = "cost-key-v1";

    private final JdbcTemplate jdbc;

    public PriceVersionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建价格版本。
     *
     * @param skuId              SKU ID
     * @param retailPriceCent    C 端零售价（分）
     * @param bPriceCent         B 端采购价（分）
     * @param costPriceCent      成本价（分，明文，会被加密存储）
     * @param grantCoefPpm       赠送系数 ppm（0-1000000）
     * @param grantCUnit         C 端赠送基准 unit（展示用）
     * @param grantBUnit         B 端赠送基准 unit（展示用）
     * @param effectiveAt        生效时间
     * @param approvedBy         审批人 ID
     * @param requesterId        发起人 ID（用于双签校验）
     * @return price_version
     */
    @Transactional(rollbackFor = Exception.class)
    public long createPriceVersion(long skuId, long retailPriceCent, long bPriceCent,
                                   long costPriceCent, long grantCoefPpm,
                                   long grantCUnit, long grantBUnit,
                                   LocalDateTime effectiveAt, long approvedBy, long requesterId) {
        // 双签校验：审批人必须不同于发起人
        if (approvedBy == requesterId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "price version requires dual-sign: approver must differ from requester");
        }
        if (retailPriceCent <= 0 || bPriceCent <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "retail_price_cent and b_price_cent must be positive");
        }
        if (costPriceCent < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "cost_price_cent cannot be negative");
        }
        if (grantCoefPpm < 0 || grantCoefPpm > LscConstants.GRANT_COEF_MAX_PPM) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "grant_coefficient_ppm out of range: " + grantCoefPpm);
        }

        // 加密成本价
        byte[] costEnc = encryptCost(costPriceCent);

        long priceVersion = nextId();
        jdbc.update(
                "INSERT INTO product_price_version(price_version, sku_id, retail_price_cent, "
                        + "b_price_cent, cost_price_enc, cost_key_version, cost_basis_code, "
                        + "grant_coefficient_ppm, grant_c_unit, grant_b_unit, effective_at, "
                        + "approved_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                priceVersion, skuId, retailPriceCent, bPriceCent, costEnc,
                COST_KEY_VERSION, CostBasisCode.TAX_INCLUDED.name(),
                grantCoefPpm, grantCUnit, grantBUnit,
                Timestamp.valueOf(effectiveAt), approvedBy);
        return priceVersion;
    }

    /**
     * 按 price_version 主键查询价格版本。
     */
    public PriceVersionRow getPriceVersion(long priceVersion) {
        List<PriceVersionRow> rows = jdbc.query(
                "SELECT price_version, sku_id, retail_price_cent, b_price_cent, cost_price_enc, "
                        + "cost_key_version, cost_basis_code, grant_coefficient_ppm, "
                        + "grant_c_unit, grant_b_unit, effective_at, approved_by "
                        + "FROM product_price_version WHERE price_version=?",
                (rs, rowNum) -> new PriceVersionRow(
                        rs.getLong("price_version"),
                        rs.getLong("sku_id"),
                        rs.getLong("retail_price_cent"),
                        rs.getLong("b_price_cent"),
                        rs.getBytes("cost_price_enc"),
                        rs.getString("cost_key_version"),
                        rs.getString("cost_basis_code"),
                        rs.getLong("grant_coefficient_ppm"),
                        rs.getLong("grant_c_unit"),
                        rs.getLong("grant_b_unit"),
                        rs.getTimestamp("effective_at").toLocalDateTime(),
                        rs.getLong("approved_by")),
                priceVersion);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询 SKU 当前生效的价格版本。
     */
    public PriceVersionRow getActivePriceVersion(long skuId) {
        List<PriceVersionRow> rows = jdbc.query(
                "SELECT price_version, sku_id, retail_price_cent, b_price_cent, cost_price_enc, "
                        + "cost_key_version, cost_basis_code, grant_coefficient_ppm, "
                        + "grant_c_unit, grant_b_unit, effective_at, approved_by "
                        + "FROM product_price_version WHERE sku_id=? AND effective_at <= ? "
                        + "ORDER BY effective_at DESC LIMIT 1",
                (rs, rowNum) -> new PriceVersionRow(
                        rs.getLong("price_version"),
                        rs.getLong("sku_id"),
                        rs.getLong("retail_price_cent"),
                        rs.getLong("b_price_cent"),
                        rs.getBytes("cost_price_enc"),
                        rs.getString("cost_key_version"),
                        rs.getString("cost_basis_code"),
                        rs.getLong("grant_coefficient_ppm"),
                        rs.getLong("grant_c_unit"),
                        rs.getLong("grant_b_unit"),
                        rs.getTimestamp("effective_at").toLocalDateTime(),
                        rs.getLong("approved_by")),
                skuId, Timestamp.valueOf(LocalDateTime.now()));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询 SKU 的所有价格版本（历史）。
     */
    public List<PriceVersionRow> listPriceVersions(long skuId) {
        return jdbc.query(
                "SELECT price_version, sku_id, retail_price_cent, b_price_cent, cost_price_enc, "
                        + "cost_key_version, cost_basis_code, grant_coefficient_ppm, "
                        + "grant_c_unit, grant_b_unit, effective_at, approved_by "
                        + "FROM product_price_version WHERE sku_id=? ORDER BY effective_at DESC",
                (rs, rowNum) -> new PriceVersionRow(
                        rs.getLong("price_version"),
                        rs.getLong("sku_id"),
                        rs.getLong("retail_price_cent"),
                        rs.getLong("b_price_cent"),
                        rs.getBytes("cost_price_enc"),
                        rs.getString("cost_key_version"),
                        rs.getString("cost_basis_code"),
                        rs.getLong("grant_coefficient_ppm"),
                        rs.getLong("grant_c_unit"),
                        rs.getLong("grant_b_unit"),
                        rs.getTimestamp("effective_at").toLocalDateTime(),
                        rs.getLong("approved_by")),
                skuId);
    }

    /**
     * 解密成本价（管理端展示用，需权限控制）。
     */
    public long decryptCost(byte[] costEnc, String keyVersion) {
        if (costEnc == null || costEnc.length == 0) return 0L;
        if (!COST_KEY_VERSION.equals(keyVersion)) {
            // 不同密钥版本应从 KMS 取对应密钥，此处仅支持当前版本
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "unsupported cost key version: " + keyVersion);
        }
        return decryptCost(costEnc);
    }

    // ===== 成本加解密（KMS 抽象占位，生产环境替换为 KMS SDK 调用） =====

    private static final String AES_KEY = "LscCostKey2024!!!"; // 16 bytes AES-128

    private byte[] encryptCost(long costCent) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            String plain = String.valueOf(costCent);
            return cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("cost encryption failed", e);
        }
    }

    private long decryptCost(byte[] encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(encrypted);
            return Long.parseLong(new String(decrypted, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("cost decryption failed", e);
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record PriceVersionRow(long priceVersion, long skuId, long retailPriceCent,
                                  long bPriceCent, byte[] costPriceEnc, String costKeyVersion,
                                  String costBasisCode, long grantCoefPpm, long grantCUnit,
                                  long grantBUnit, LocalDateTime effectiveAt, long approvedBy) {}
}
