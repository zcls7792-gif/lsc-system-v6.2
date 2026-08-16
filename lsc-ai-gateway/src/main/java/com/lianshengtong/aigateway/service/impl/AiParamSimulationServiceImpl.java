package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiParamSimulationDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiParamSimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 参数调整仿真推演服务实现
 * <p>
 * 对释放参数变更进行模拟推演，调用外部AI模型API。
 * 超时10秒自动降级，返回空序列并提示人工评估。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiParamSimulationServiceImpl implements AiParamSimulationService {

    private static final String CAPABILITY = "param-simulation";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.param-simulation.endpoint:}")
    private String endpoint;

    @Override
    public AiParamSimulationDTO.Response simulate(AiParamSimulationDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI仿真推演模型API
     * TODO 接入真实仿真推演模型，当前为占位实现
     */
    private AiParamSimulationDTO.Response callExternalModel(AiParamSimulationDTO.Request request) {
        log.info("[AiParamSimulation] 调用外部模型 endpoint={} days={} changes={}",
                endpoint, request.getSimulateDays(),
                request.getParamChanges() == null ? 0 : request.getParamChanges().size());
        return AiParamSimulationDTO.Response.builder()
                .dailyReleaseSeries(new ArrayList<>())
                .dailyKSeries(new ArrayList<>())
                .totalRelease(0L)
                .avgK(new BigDecimal("0.0070"))
                .releaseChangePct(BigDecimal.ZERO)
                .riskHint("仿真推演完成，参数变更在可控范围内(占位)")
                .fallback(false)
                .message("参数仿真推演完成(占位)")
                .build();
    }

    private AiParamSimulationDTO.Response fallback(AiParamSimulationDTO.Request request) {
        log.warn("[AiParamSimulation] 降级，建议人工评估参数变更");
        return AiParamSimulationDTO.Response.builder()
                .dailyReleaseSeries(new ArrayList<>())
                .dailyKSeries(new ArrayList<>())
                .totalRelease(0L)
                .avgK(BigDecimal.ZERO)
                .releaseChangePct(BigDecimal.ZERO)
                .riskHint("AI模型超时或熔断，请人工评估参数变更影响")
                .fallback(true)
                .message("AI模型超时或熔断，降级为人工评估")
                .build();
    }
}
