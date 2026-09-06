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

    /** 释放速率上限 0.06% —— 硬常量，编译后不可修改（方案文档18.4） */
    public static final BigDecimal RATE_MAX = new BigDecimal("0.0006");
    /** 释放速率下限 0.03% —— 硬常量，编译后不可修改（方案文档18.4） */
    public static final BigDecimal RATE_MIN = new BigDecimal("0.0003");

    private final ReleaseConfigMapper configMapper;
    private final ReleaseSummaryMapper summaryMapper;

    @Value("${lsc.release.rate-max:0.0006}")
    private BigDecimal rateMax;
    @Value("${lsc.release.rate-min:0.0003}")
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
        Map<String, Object> result = new HashMap<>();
        // rateMax/rateMin 为硬常量，不从数据库读取（方案文档18.4）
        result.put("rateMax", fmtPct(RATE_MAX, "0.06%", 2));
        result.put("rateMaxEditable", false);
        result.put("rateMin", fmtPct(RATE_MIN, "0.03%", 2));
        result.put("rateMinEditable", false);
        result.put("kMin", fmtPct(getConfigDecimal("k_min", new BigDecimal("0.005")), "0.50%", 2));
        result.put("kMinEditable", true);
        result.put("kMax", fmtPct(getConfigDecimal("k_max", new BigDecimal("0.010")), "1.0%", 1));
        result.put("kMaxEditable", true);
        BigDecimal alpha = getConfigDecimal("alpha", new BigDecimal("0.06"));
        result.put("alpha", alpha.stripTrailingZeros().toPlainString());
        result.put("alphaEditable", true);
        return result;
    }

    /**
     * 按 config_key 读取配置值并转为 BigDecimal
     */
    private BigDecimal getConfigDecimal(String key, BigDecimal def) {
        ReleaseConfig c = configMapper.selectOne(
                new LambdaQueryWrapper<ReleaseConfig>().eq(ReleaseConfig::getConfigKey, key));
        if (c == null || c.getConfigValue() == null) return def;
        try {
            // 兼容 "0.50%" / "1.0%" / "0.06" 等格式
            String val = c.getConfigValue().trim();
            if (val.endsWith("%")) {
                val = val.substring(0, val.length() - 1);
                return new BigDecimal(val).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            }
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 百分比格式化：val(小数) × 100，保留指定小数位
     */
    private String fmtPct(BigDecimal val, String def, int scale) {
        if (val == null) return def;
        BigDecimal pct = val.multiply(new BigDecimal("100"));
        return pct.setScale(scale, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public Map<String, Object> updateConfig(BigDecimal kMin, BigDecimal kMax, BigDecimal alpha) {
        // k 范围安全校验
        if (kMin.compareTo(BigDecimal.ZERO) <= 0 || kMax.compareTo(kMin) <= 0) {
            throw new BusinessException(ErrorCode.K_OUT_OF_RANGE);
        }
        upsertConfig("k_min", kMin.toPlainString(), 1, "释放调节起点");
        upsertConfig("k_max", kMax.toPlainString(), 1, "释放调节终点");
        upsertConfig("alpha", alpha.toPlainString(), 1, "线性调节因子");
        return getConfig();
    }

    private void upsertConfig(String key, String value, int editable, String desc) {
        ReleaseConfig c = configMapper.selectOne(
                new LambdaQueryWrapper<ReleaseConfig>().eq(ReleaseConfig::getConfigKey, key));
        if (c == null) {
            c = new ReleaseConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            c.setEditable(editable);
            c.setDescription(desc);
            configMapper.insert(c);
        } else {
            c.setConfigValue(value);
            configMapper.updateById(c);
        }
    }

    /**
     * 释放速率计算公式（方案文档第九章）：
     * k ≤ 0.50% → rate = 0.06%
     * k ≥ 1.0%  → rate = 0.03%
     * 中间线性：rate = 0.09% - 0.06 × k
     */
    public BigDecimal calcRate(BigDecimal k) {
        BigDecimal kMin = getConfigDecimal("k_min", new BigDecimal("0.005"));
        BigDecimal kMax = getConfigDecimal("k_max", new BigDecimal("0.010"));
        BigDecimal alpha = getConfigDecimal("alpha", new BigDecimal("0.06"));

        if (k.compareTo(kMin) <= 0) return RATE_MAX;
        if (k.compareTo(kMax) >= 0) return RATE_MIN;
        // 截距 = rateMax + rateMin = 0.06% + 0.03% = 0.09%
        BigDecimal intercept = RATE_MAX.add(RATE_MIN);
        BigDecimal rate = intercept.subtract(alpha.multiply(k)).setScale(6, RoundingMode.HALF_UP);
        // 二次校验：越界直接终止（方案文档9.1）
        if (rate.compareTo(RATE_MAX) > 0 || rate.compareTo(RATE_MIN) < 0) {
            throw new BusinessException(ErrorCode.K_OUT_OF_RANGE.getCode(),
                    "释放速率越界，终止当日释放任务");
        }
        return rate;
    }
}
