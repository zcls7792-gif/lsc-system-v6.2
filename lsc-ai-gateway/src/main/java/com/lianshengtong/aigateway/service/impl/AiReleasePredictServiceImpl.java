package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiReleasePredictDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiReleasePredictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * LSC释放趋势预测服务实现
 * <p>
 * 基于历史核销率序列，预测未来7-30天核销率走势，调用外部AI模型API。
 * 超时10秒自动降级，返回最近一日核销率作为保守预测。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReleasePredictServiceImpl implements AiReleasePredictService {

    private static final String CAPABILITY = "release-predict";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.release-predict.endpoint:}")
    private String endpoint;

    @Override
    public AiReleasePredictDTO.Response predict(AiReleasePredictDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI时序预测模型API
     * TODO 接入真实释放趋势预测模型，当前为占位实现
     */
    private AiReleasePredictDTO.Response callExternalModel(AiReleasePredictDTO.Request request) {
        log.info("[AiReleasePredict] 调用外部模型 endpoint={} days={} historySize={}",
                endpoint, request.getPredictDays(),
                request.getHistoryKSeries() == null ? 0 : request.getHistoryKSeries().size());
        return AiReleasePredictDTO.Response.builder()
                .predictedKSeries(new ArrayList<>())
                .predictedDates(new ArrayList<>())
                .predictedK7d(new BigDecimal("0.0070"))
                .predictedK30d(new BigDecimal("0.0072"))
                .confidence(new BigDecimal("0.78"))
                .trend("FLAT")
                .fallback(false)
                .message("释放趋势预测完成(占位)")
                .build();
    }

    private AiReleasePredictDTO.Response fallback(AiReleasePredictDTO.Request request) {
        log.warn("[AiReleasePredict] 降级，使用最近一日核销率作为保守预测");
        // 降级：取历史序列最后一个值作为保守预测
        BigDecimal lastK = BigDecimal.ZERO;
        if (request.getHistoryKSeries() != null && !request.getHistoryKSeries().isEmpty()) {
            lastK = request.getHistoryKSeries().get(request.getHistoryKSeries().size() - 1);
        }
        return AiReleasePredictDTO.Response.builder()
                .predictedKSeries(new ArrayList<>())
                .predictedDates(new ArrayList<>())
                .predictedK7d(lastK)
                .predictedK30d(lastK)
                .confidence(BigDecimal.ZERO)
                .trend("FLAT")
                .fallback(true)
                .message("AI模型超时或熔断，降级使用最近一日核销率")
                .build();
    }
}
