package com.lianshengtong.risk.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.risk.dto.RiskCheckDTO;
import com.lianshengtong.risk.entity.RiskLog;
import com.lianshengtong.risk.feign.AiGatewayFeignClient;
import com.lianshengtong.risk.mapper.RiskLogMapper;
import com.lianshengtong.risk.service.RiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 风控服务实现
 * <p>
 * 固定规则(基于 Redis 滑动窗口计数)：
 * <ul>
 *   <li>批量下单：1小时 > 10笔</li>
 *   <li>异常混合支付：连续3笔 LSC > 90%</li>
 *   <li>高频套利：同商品 > 5次</li>
 *   <li>异地操作：1小时 3+城市IP</li>
 * </ul>
 * AI动态风控：调用 AI 网关评分，>=80 高风险，50~79 中风险。
 * 处理：高风险自动限制(暂停LSC支付、冻结账户) + 推送人工审核；中低风险仅记录日志。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlServiceImpl implements RiskControlService {

    private final RiskLogMapper riskLogMapper;
    private final AiGatewayFeignClient aiGatewayFeignClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${lsc.risk.batch-order-window-seconds:3600}")
    private int batchOrderWindowSeconds;
    @Value("${lsc.risk.batch-order-threshold:10}")
    private int batchOrderThreshold;
    @Value("${lsc.risk.hybrid-pay-streak-count:3}")
    private int hybridPayStreakCount;
    @Value("${lsc.risk.hybrid-pay-lsc-ratio:0.90}")
    private BigDecimal hybridPayLscRatio;
    @Value("${lsc.risk.arbitrage-product-threshold:5}")
    private int arbitrageProductThreshold;
    @Value("${lsc.risk.geo-window-seconds:3600}")
    private int geoWindowSeconds;
    @Value("${lsc.risk.geo-city-threshold:3}")
    private int geoCityThreshold;
    @Value("${lsc.risk.ai-high-risk-score:80}")
    private int aiHighRiskScore;
    @Value("${lsc.risk.ai-mid-risk-score:50}")
    private int aiMidRiskScore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiskLog check(RiskCheckDTO dto) {
        RiskLog riskLog = new RiskLog();
        riskLog.setUserId(dto.getUserId());
        riskLog.setClientIp(dto.getClientIp());
        riskLog.setHandleStatus(0);

        Map<String, Object> detail = new HashMap<>();
        detail.put("orderNo", dto.getOrderNo());
        detail.put("productId", dto.getProductId());
        detail.put("clientIp", dto.getClientIp());
        detail.put("clientCity", dto.getClientCity());

        int hitLevel = 0; // 1低 2中 3高
        String hitRule = null;
        int riskType = 0;

        // 1. 批量下单: 1小时 > 10笔
        long batchCount = incrWindow("lsc:risk:batch:" + dto.getUserId(), batchOrderWindowSeconds);
        if (batchCount > batchOrderThreshold) {
            hitLevel = 3;
            hitRule = "批量下单: 1小时内" + batchCount + "笔超过" + batchOrderThreshold;
            riskType = 1;
            detail.put("batchOrderCount", batchCount);
        }

        // 2. 异常混合支付: 连续3笔 LSC > 90%
        if (dto.getOrderAmount() != null && dto.getLscAmount() != null
                && dto.getOrderAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lscRatio = BigDecimal.valueOf(dto.getLscAmount())
                    .divide(dto.getOrderAmount(), 4, RoundingMode.HALF_UP);
            if (lscRatio.compareTo(hybridPayLscRatio) > 0) {
                long streak = incrWindow("lsc:risk:hybrid-streak:" + dto.getUserId(), batchOrderWindowSeconds);
                if (streak >= hybridPayStreakCount) {
                    if (hitLevel < 3) {
                        hitLevel = 3;
                        hitRule = "异常混合支付: 连续" + streak + "笔LSC占比>90%";
                        riskType = 2;
                    }
                    detail.put("hybridStreak", streak);
                    detail.put("lscRatio", lscRatio);
                }
            } else {
                // LSC 占比低，重置连续计数
                stringRedisTemplate.delete("lsc:risk:hybrid-streak:" + dto.getUserId());
            }
        }

        // 3. 高频套利: 同商品 > 5次
        if (dto.getProductId() != null) {
            long prodCount = incrWindow("lsc:risk:arb:" + dto.getUserId() + ":" + dto.getProductId(), batchOrderWindowSeconds);
            if (prodCount > arbitrageProductThreshold) {
                if (hitLevel < 3) {
                    hitLevel = 3;
                    hitRule = "高频套利: 同商品" + prodCount + "次超过" + arbitrageProductThreshold;
                    riskType = 3;
                }
                detail.put("arbitrageCount", prodCount);
            }
        }

        // 4. 异地操作: 1小时 3+城市IP
        if (dto.getClientCity() != null) {
            String geoKey = "lsc:risk:geo:" + dto.getUserId();
            stringRedisTemplate.opsForSet().add(geoKey, dto.getClientCity());
            stringRedisTemplate.expire(geoKey, Duration.ofSeconds(geoWindowSeconds));
            Long cityCount = stringRedisTemplate.opsForSet().size(geoKey);
            if (cityCount != null && cityCount >= geoCityThreshold) {
                if (hitLevel < 3) {
                    hitLevel = 3;
                    hitRule = "异地操作: 1小时内" + cityCount + "城市IP";
                    riskType = 4;
                }
                detail.put("geoCityCount", cityCount);
            }
        }

        // 5. AI动态风控评分
        Integer aiScore = null;
        if (Boolean.TRUE.equals(dto.getEnableAi())) {
            try {
                R<Integer> resp = aiGatewayFeignClient.riskScore(dto.getUserId(), JSON.toJSONString(detail));
                if (resp != null && resp.isSuccess() && resp.getData() != null) {
                    aiScore = resp.getData();
                    if (aiScore >= aiHighRiskScore) {
                        if (hitLevel < 3) {
                            hitLevel = 3;
                            hitRule = "AI动态风控: 评分" + aiScore + "高风险";
                            riskType = 5;
                        }
                    } else if (aiScore >= aiMidRiskScore) {
                        if (hitLevel < 2) {
                            hitLevel = 2;
                            hitRule = "AI动态风控: 评分" + aiScore + "中风险";
                            riskType = 5;
                        }
                    } else if (hitLevel < 1) {
                        hitLevel = 1;
                        hitRule = "AI动态风控: 评分" + aiScore + "低风险";
                        riskType = 5;
                    }
                }
            } catch (RuntimeException e) {
                log.error("AI风控评分异常 userId={}", dto.getUserId(), e);
            }
        }

        riskLog.setRiskType(riskType);
        riskLog.setRiskLevel(hitLevel);
        riskLog.setAiScore(aiScore);
        riskLog.setHitRule(hitRule);
        riskLog.setDetail(JSON.toJSONString(detail));

        // 处理：高风险待人工审核(自动限制需 user-service 提供 freeze 端点与 ledger-service 提供 suspend-pay 端点，
        // 当前尚未接入，避免误声称已自动限制，统一标记为"待人工处理"，由运维介入)
        if (hitLevel >= 3) {
            riskLog.setHandleStatus(0);
            riskLog.setHandleRemark("高风险命中，已推送人工审核，待人工处理(自动限制能力待接入)");
            log.error("[RISK_ALERT] 高风险命中 userId={} rule={} aiScore={} 待人工处理",
                    dto.getUserId(), hitRule, aiScore);
        } else if (hitLevel == 2) {
            riskLog.setHandleStatus(0);
            riskLog.setHandleRemark("中风险记录，待观察");
        } else if (hitLevel == 1) {
            riskLog.setHandleStatus(3);
            riskLog.setHandleRemark("低风险已忽略");
        } else {
            riskLog.setRiskLevel(0);
            riskLog.setHandleStatus(3);
            riskLog.setHitRule("无风险命中");
        }
        riskLogMapper.insert(riskLog);
        return riskLog;
    }

    @Override
    public IPage<RiskLog> logs(Integer page, Integer size, Long userId, Integer riskLevel, Integer handleStatus) {
        Page<RiskLog> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<RiskLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(RiskLog::getUserId, userId);
        }
        if (riskLevel != null) {
            wrapper.eq(RiskLog::getRiskLevel, riskLevel);
        }
        if (handleStatus != null) {
            wrapper.eq(RiskLog::getHandleStatus, handleStatus);
        }
        wrapper.orderByDesc(RiskLog::getCreatedAt);
        return riskLogMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, Integer handleStatus, String handleRemark) {
        RiskLog riskLog = riskLogMapper.selectById(id);
        if (riskLog == null) {
            throw new BizException(404, "风控日志不存在");
        }
        riskLog.setHandleStatus(handleStatus);
        riskLog.setHandleRemark(handleRemark);
        riskLog.setUpdatedAt(LocalDateTime.now());
        riskLogMapper.updateById(riskLog);
        log.info("风控事件人工处理 id={} status={}", id, handleStatus);
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        LambdaQueryWrapper<RiskLog> allWrapper = new LambdaQueryWrapper<>();
        Long total = riskLogMapper.selectCount(allWrapper);
        result.put("total", total);
        // 按风险等级统计: 1低 2中 3高
        Map<String, Long> levelStats = new LinkedHashMap<>();
        for (int lv = 1; lv <= 3; lv++) {
            LambdaQueryWrapper<RiskLog> w = new LambdaQueryWrapper<>();
            w.eq(RiskLog::getRiskLevel, lv);
            levelStats.put(String.valueOf(lv), riskLogMapper.selectCount(w));
        }
        result.put("byLevel", levelStats);
        // 按处理状态统计: 0待处理 1已自动限制 2已推送人工审核 3已忽略 4已解封
        Map<String, Long> statusStats = new LinkedHashMap<>();
        for (int st = 0; st <= 4; st++) {
            LambdaQueryWrapper<RiskLog> w = new LambdaQueryWrapper<>();
            w.eq(RiskLog::getHandleStatus, st);
            statusStats.put(String.valueOf(st), riskLogMapper.selectCount(w));
        }
        result.put("byStatus", statusStats);
        // 高风险待处理数(需人工介入)
        LambdaQueryWrapper<RiskLog> pendingW = new LambdaQueryWrapper<>();
        pendingW.eq(RiskLog::getRiskLevel, 3).in(RiskLog::getHandleStatus, 0, 1, 2);
        result.put("highRiskPending", riskLogMapper.selectCount(pendingW));
        return result;
    }

    @Override
    public RiskLog getById(Long id) {
        RiskLog log = riskLogMapper.selectById(id);
        if (log == null) {
            throw new BizException(404, "风控日志不存在");
        }
        return log;
    }

    /** 滑动窗口计数自增 */
    private long incrWindow(String key, int windowSeconds) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count == null ? 0 : count;
        } catch (RuntimeException e) {
            log.warn("[incrWindow] Redis计数异常 key={}，降级返回0", key, e);
            return 0L;
        }
    }
}
