package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.entity.Merchant;
import com.lianshengtong.lsc.entity.NhLevel;
import com.lianshengtong.lsc.entity.Product;
import com.lianshengtong.lsc.mapper.MerchantMapper;
import com.lianshengtong.lsc.mapper.NhLevelMapper;
import com.lianshengtong.lsc.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final NhLevelMapper nhLevelMapper;

    public Map<String, Object> getDashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalLsc", 12860000L);
        result.put("regulatoryPool", 1800000.00);
        result.put("todayReleaseRate", "0.045%");
        result.put("platformFeeRate", "2%");
        result.put("todayGmv", 1286000.00);
        result.put("todayOrders", 8642);
        result.put("totalMerchants", merchantMapper.selectCount(null));
        result.put("todayNhCount", 2860);
        result.put("todayNhAmount", 248000.00);
        result.put("kValue", 0.0042);
        return result;
    }

    public Page<Merchant> getMerchants(Integer auditStatus, int pageNo, int pageSize) {
        LambdaQueryWrapper<Merchant> qw = new LambdaQueryWrapper<>();
        if (auditStatus != null) qw.eq(Merchant::getAuditStatus, auditStatus);
        qw.orderByDesc(Merchant::getCreatedAt);
        return merchantMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    public void auditMerchant(Long merchantId, Integer auditStatus, String rejectReason) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m != null) {
            m.setAuditStatus(auditStatus);
            merchantMapper.updateById(m);
        }
    }

    public Page<Product> getProducts(Integer aiReviewResult, int pageNo, int pageSize) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (aiReviewResult != null) qw.eq(Product::getAiReviewResult, aiReviewResult);
        qw.orderByDesc(Product::getCreatedAt);
        return productMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    public void reviewProduct(Long productId, Integer aiReviewResult, String rejectReason) {
        Product p = productMapper.selectById(productId);
        if (p != null) {
            p.setAiReviewResult(aiReviewResult);
            if (aiReviewResult == 3) p.setStatus(0); // 拒绝则下架
            productMapper.updateById(p);
        }
    }

    public List<NhLevel> getNhLevels() {
        return nhLevelMapper.selectList(new LambdaQueryWrapper<NhLevel>().orderByAsc(NhLevel::getLevel));
    }
}
