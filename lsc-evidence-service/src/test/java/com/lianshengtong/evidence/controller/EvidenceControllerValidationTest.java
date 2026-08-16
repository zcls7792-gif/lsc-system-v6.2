package com.lianshengtong.evidence.controller;

import com.lianshengtong.common.result.R;
import com.lianshengtong.evidence.config.EvidenceGlobalExceptionHandler;
import com.lianshengtong.evidence.dto.VerifyRequest;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.DailySnapshotRecord;
import com.lianshengtong.evidence.security.JwtUtil;
import com.lianshengtong.evidence.service.EvidenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {EvidenceController.class},
        includeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.lianshengtong\\.common\\.exception\\..*"
        ),
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        })
@AutoConfigureMockMvc(addFilters = false)
@Import({EvidenceGlobalExceptionHandler.class})
@ActiveProfiles("standalone")
@DisplayName("EvidenceController 参数校验与异常处理测试")
class EvidenceControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EvidenceService evidenceService;

    @MockBean
    private com.lianshengtong.evidence.security.JwtUtil jwtUtil;

    @MockBean
    private com.lianshengtong.evidence.security.LoginAttemptService loginAttemptService;

    @MockBean
    private com.lianshengtong.evidence.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedissonClient redissonClient;

    private static final String BASE_URL = "/api/evidence";

    @Nested
    @DisplayName("POST /save 存证保存校验")
    class SaveEndpointTests {

        @Test
        @DisplayName("bizType 为空时应返回400")
        void save_bizTypeBlank() throws Exception {
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "")
                            .param("bizId", "ORD-001")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("bizType 格式不合法(小写字母)应返回400")
        void save_bizTypeInvalidPattern() throws Exception {
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "order_type")
                            .param("bizId", "ORD-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("bizId 为空时应返回400")
        void save_bizIdBlank() throws Exception {
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "ORDER")
                            .param("bizId", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("bizType 超长应返回400")
        void save_bizTypeTooLong() throws Exception {
            String longBizType = "A".repeat(33);
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", longBizType)
                            .param("bizId", "ORD-001"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("bizId 超长应返回400")
        void save_bizIdTooLong() throws Exception {
            String longBizId = "B".repeat(129);
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "ORDER")
                            .param("bizId", longBizId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("dataHash 格式不合法应返回400")
        void save_dataHashInvalid() throws Exception {
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "ORDER")
                            .param("bizId", "ORD-001")
                            .param("dataHash", "invalid-hash"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("payload 超长应返回400")
        void save_payloadTooLong() throws Exception {
            String longPayload = "P".repeat(10001);
            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "ORDER")
                            .param("bizId", "ORD-001")
                            .param("payload", longPayload))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("所有参数合法时应成功")
        void save_validParams() throws Exception {
            when(evidenceService.saveEvidence("ORDER", "ORD-001", null, null))
                    .thenReturn("12345");

            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "ORDER")
                            .param("bizId", "ORD-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value("12345"));

            verify(evidenceService).saveEvidence("ORDER", "ORD-001", null, null);
        }

        @Test
        @DisplayName("dataHash 合法0x64位hex应成功")
        void save_validDataHash() throws Exception {
            String hash = "0x" + "a".repeat(64);
            when(evidenceService.saveEvidence("ORDER", "ORD-001", hash, null))
                    .thenReturn("12345");

            mockMvc.perform(post(BASE_URL + "/save")
                            .param("bizType", "ORDER")
                            .param("bizId", "ORD-001")
                            .param("dataHash", hash))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("GET /list 分页查询校验")
    class ListEndpointTests {

        @Test
        @DisplayName("page为0时应返回400")
        void list_pageZero() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list")
                            .param("page", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("page为负数时应返回400")
        void list_pageNegative() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("size为0时应返回400")
        void list_sizeZero() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("size超过100时应返回400")
        void list_sizeExceedsMax() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("startDate格式错误应返回400")
        void list_startDateInvalid() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list")
                            .param("startDate", "2026/01/01"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("endDate格式错误应返回400")
        void list_endDateInvalid() throws Exception {
            mockMvc.perform(get(BASE_URL + "/list")
                            .param("endDate", "not-a-date"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("分页参数合法时应成功")
        void list_validParams() throws Exception {
            when(evidenceService.listPage(1, 20, null, null, null, null, null))
                    .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

            mockMvc.perform(get(BASE_URL + "/list")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("GET /{id} 详情查询校验")
    class DetailEndpointTests {

        @Test
        @DisplayName("id为负数时应返回400")
        void detail_idNegative() throws Exception {
            mockMvc.perform(get(BASE_URL + "/-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("id为0时应返回400")
        void detail_idZero() throws Exception {
            mockMvc.perform(get(BASE_URL + "/0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("id为合法正数时应成功")
        void detail_idValid() throws Exception {
            BlockchainRecord record = new BlockchainRecord();
            record.setId(1L);
            record.setBizType("ORDER");
            record.setBizId("ORD-001");
            when(evidenceService.getById(1L)).thenReturn(record);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("POST /verify 校验校验")
    class VerifyPostEndpointTests {

        @Test
        @DisplayName("日期格式不合法应返回400")
        void verify_invalidDateFormat() throws Exception {
            Map<String, String> body = Map.of("date", "2026/08/07");
            mockMvc.perform(post(BASE_URL + "/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("日期格式合法时应成功")
        void verify_validDate() throws Exception {
            Map<String, String> body = Map.of("date", "2026-08-07");
            when(evidenceService.verify(LocalDate.of(2026, 8, 7))).thenReturn(true);

            mockMvc.perform(post(BASE_URL + "/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("无日期参数时使用当前日期")
        void verify_noDate() throws Exception {
            VerifyRequest req = new VerifyRequest();
            when(evidenceService.verify(any(LocalDate.class))).thenReturn(true);

            mockMvc.perform(post(BASE_URL + "/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("GET /verify-report 报告校验")
    class VerifyReportEndpointTests {

        @Test
        @DisplayName("日期格式不合法应返回400")
        void report_invalidDate() throws Exception {
            mockMvc.perform(get(BASE_URL + "/verify-report")
                            .param("date", "invalid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("日期格式合法应成功")
        void report_validDate() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("date", "2026-08-07");
            result.put("total", 10);
            result.put("passed", 10);
            result.put("failed", 0);
            when(evidenceService.verifyReport(LocalDate.of(2026, 8, 7))).thenReturn(result);

            mockMvc.perform(get(BASE_URL + "/verify-report")
                            .param("date", "2026-08-07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("异常处理层级测试")
    class ExceptionHandlerTests {

        @Test
        @DisplayName("BizException 应返回对应错误码")
        void bizException() throws Exception {
            when(evidenceService.getById(999L))
                    .thenThrow(new com.lianshengtong.common.exception.BizException(404, "存证记录不存在"));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("存证记录不存在"));
        }

        @Test
        @DisplayName("IllegalArgumentException 应返回400")
        void illegalArgument() throws Exception {
            when(evidenceService.getById(1L))
                    .thenThrow(new IllegalArgumentException("非法参数"));

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("未知异常应返回500")
        void genericException() throws Exception {
            when(evidenceService.getById(1L))
                    .thenThrow(new RuntimeException("未知错误"));

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("系统错误，请稍后重试"));
        }
    }

    @Nested
    @DisplayName("POST /snapshot 快照校验")
    class SnapshotEndpointTests {

        @Test
        @DisplayName("日期合法时应成功")
        void snapshot_validDate() throws Exception {
            DailySnapshotRecord snapshot = new DailySnapshotRecord();
            snapshot.setSnapshotDate(LocalDate.of(2026, 8, 7));
            snapshot.setStatus(1);
            when(evidenceService.dailySnapshot(LocalDate.of(2026, 8, 7))).thenReturn(snapshot);

            mockMvc.perform(post(BASE_URL + "/snapshot")
                            .param("date", "2026-08-07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("无日期参数时使用昨日")
        void snapshot_noDate() throws Exception {
            DailySnapshotRecord snapshot = new DailySnapshotRecord();
            snapshot.setSnapshotDate(LocalDate.now().minusDays(1));
            when(evidenceService.dailySnapshot(isNull())).thenReturn(snapshot);

            mockMvc.perform(post(BASE_URL + "/snapshot"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}
