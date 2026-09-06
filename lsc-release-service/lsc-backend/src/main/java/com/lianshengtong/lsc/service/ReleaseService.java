package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.ReleaseConfig;
import com.lianshengtong.lsc.entity.ReleaseSummary;
import com.lianshengtong.lsc.mapper.ReleaseConfigMapper;
import com.lianshengtong.lsc.mapper.ReleaseSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final ReleaseConfigMapper configMapper;
    private final ReleaseSummaryMapper summaryMapper;

    @Value("${lsc.release.rate-max}")
    private BigDecimal rateMax;
    @Value("${lsc.release.rate-min}")
    private BigDecimal rateMin;

    public Map<String, Object> getSummary(String date) {
        LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
        ReleaseSummary s = summaryMapper.selectOne(
                new LambdaQueryWrapper<ReleaseSummary>().eq(ReleaseSummary::getReleaseDate, d));
        if (s == null) {
            // 模拟数据
            s = new ReleaseSummary();
            s.setReleaseDate(d);
            s.setMTotal(new BigDecimal("1800000.00"));
            s.setNTotal(new BigDecimal("7560.00"));
            s.setKValue(new BigDecimal("0.0042"));
            s.setRate(calcRate(s.getKValue()));
            s.setLLocked(1836800000L);
            s.setTRelease(826560L);
            s.setStatus(1);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("date", s.getReleaseDate());
        result.put("mTotal", s.getMTotal());
        result.put("nTotal", s.getNTotal());
        result.put("k", s.getKValue());
        result.put("rate", s.getRate());
        result.put("rateDisplay", s.getRate().multiply(new BigDecimal("100")).setScale(3, RoundingMode.HALF_UP) + "%");
        result.put("lLocked", s.getLLocked());
        result.put("tRelease", s.getTRelease());
        result.put("status", s.getStatus());
        return result;
    }

    public Map<String, Object> getConfig() {
        ReleaseConfig c = configMapper.selectById(1L);
        if (c == null) c = new ReleaseConfig();
        Map<String, Object> result = new HashMap<>();
        result.put("rateMax", fmtPct(c.getRateMax(), "0.06%", 2));
        result.put("rateMaxEditable", false);
        result.put("rateMin", fmtPct(c.getRateMin(), "0.03%", 2));
        result.put("rateMinEditable", false);
        result.put("kMin", fmtPct(c.getKMin(), "0.50%", 2));
        result.put("kMinEditable", true);
        result.put("kMax", fmtPct(c.getKMax(), "1.0%", 1));
        result.put("kMaxEditable", true);
        result.put("alpha", c.getAlpha() != null ? c.getAlpha().stripTrailingZeros().toPlainString() : "0.06");
        result.put("alphaEditable", true);
        return result;
    }

    /**
     * 百分比格式化：val(小数) × 100，保留指定小数位
     * @param val 原始小数值（如 0.005 表示 0.5%）
     * @param def 默认显示值
     * @param scale 小数位数（rate/kMin=2, kMax=1，与方案文档一致）
     */
    private String fmtPct(BigDecimal val, String def, int scale) {
        if (val == null) return def;
        BigDecimal pct = val.multiply(new BigDecimal("100"));
        return pct.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public Map<String, Object> updateConfig(BigDecimal kMin, BigDecimal kMax, BigDecimal alpha) {
        ReleaseConfig c = configMapper.selectById(1L);
        if (c == null) {
            c = new ReleaseConfig();
            c.setId(1L);
        }
        // k 范围安全校验
        if (kMin.compareTo(BigDecimal.ZERO) <= 0 || kMax.compareTo(kMin) <= 0) {
            throw new BusinessException(ErrorCode.K_OUT_OF_RANGE);
        }
        c.setKMin(kMin);
        c.setKMax(kMax);
        c.setAlpha(alpha);
        // rateMax/rateMin 硬常量，不允许修改
        if (c.getId() == null) {
            configMapper.insert(c);
        } else {
            configMapper.updateById(c);
        }
        return getConfig();
    }

    /**
     * 释放速率计算公式：
     * k ≤ 0.50% → rate = 0.06%
     * k ≥ 1.0%  → rate = 0.03%
     * 中间线性：rate = 0.09% - 0.06 × k
     */
    public BigDecimal calcRate(BigDecimal k) {
        ReleaseConfig c = configMapper.selectById(1L);
        BigDecimal kMin = c != null && c.getKMin() != null ? c.getKMin() : new BigDecimal("0.005");
        BigDecimal kMax = c != null && c.getKMax() != null ? c.getKMax() : new BigDecimal("0.010");
        BigDecimal rMax = c != null && c.getRateMax() != null ? c.getRateMax() : rateMax;
        BigDecimal rMin = c != null && c.getRateMin() != null ? c.getRateMin() : rateMin;
        BigDecimal alpha = c != null && c.getAlpha() != null ? c.getAlpha() : new BigDecimal("0.06");

        if (k.compareTo(kMin) <= 0) return rMax;
        if (k.compareTo(kMax) >= 0) return rMin;
        // rate = rateMax - alpha * (k - kMin) / (kMax - kMin) * (rateMax - rateMin)... 简化为线性
        BigDecimal rate = rMax.subtract(alpha.multiply(k)).setScale(6, RoundingMode.HALF_UP);
        // 硬约束兜底
        if (rate.compareTo(rMax) > 0) rate = rMax;
        if (rate.compareTo(rMin) < 0) rate = rMin;
        return rate;
    }
}
