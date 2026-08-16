package com.lianshengtong.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.user.dto.MerchantApplyDTO;
import com.lianshengtong.user.entity.MerchantExtension;
import com.lianshengtong.user.entity.StoreAddress;
import com.lianshengtong.user.feign.RiskFeignClient;
import com.lianshengtong.user.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商家服务 Controller
 *
 * @author lsc
 */
@Tag(name = "商家服务")
@Slf4j
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MerchantService merchantService;
    private final RiskFeignClient riskFeignClient;

    public MerchantController(MerchantService merchantService, RiskFeignClient riskFeignClient) {
        this.merchantService = merchantService;
        this.riskFeignClient = riskFeignClient;
    }

    @Operation(summary = "商家注册（企业资质审核/信用分初始化100/日核销额度80）")
    @PostMapping("/register")
    public R<MerchantExtension> register(@Valid @RequestBody MerchantApplyDTO dto) {
        return R.ok(merchantService.register(dto));
    }

    @Operation(summary = "商家审核")
    @PostMapping("/audit")
    public R<MerchantExtension> audit(@RequestParam Long merchantId,
                                      @RequestParam Integer auditStatus,
                                      @RequestParam(required = false) String remark) {
        return R.ok(merchantService.audit(merchantId, auditStatus, remark));
    }

    @Operation(summary = "商家地址管理（每日3次修改限制）")
    @PostMapping("/address")
    public R<MerchantExtension> address(@Valid @RequestBody MerchantApplyDTO dto) {
        return R.ok(merchantService.updateAddress(dto));
    }

    @Operation(summary = "商家信息查询")
    @GetMapping("/info")
    public R<MerchantExtension> info(@RequestParam Long merchantId) {
        return R.ok(merchantService.getMerchantInfo(merchantId));
    }

    @Operation(summary = "商家信用分更新")
    @PutMapping("/credit")
    public R<MerchantExtension> credit(@RequestParam Long merchantId,
                                       @RequestParam Integer creditScore) {
        return R.ok(merchantService.updateCredit(merchantId, creditScore));
    }

    @Operation(summary = "商家列表(管理后台)")
    @GetMapping("/list")
    public R<IPage<MerchantExtension>> list(@RequestParam(required = false, defaultValue = "1") Integer page,
                                             @RequestParam(required = false, defaultValue = "20") Integer size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) Integer creditMin,
                                             @RequestParam(required = false) Integer creditMax) {
        return R.ok(merchantService.listMerchants(page, size, keyword, status, creditMin, creditMax));
    }

    @Operation(summary = "商家详情(路径变量)")
    @GetMapping("/{id}")
    public R<MerchantExtension> detail(@PathVariable("id") Long id) {
        return R.ok(merchantService.getMerchantInfo(id));
    }

    @Operation(summary = "待审核商家列表")
    @GetMapping("/audit/list")
    public R<IPage<MerchantExtension>> auditList(@RequestParam(required = false, defaultValue = "1") Integer page,
                                                 @RequestParam(required = false, defaultValue = "20") Integer size,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(merchantService.auditList(page, size, status));
    }

    @Operation(summary = "商家审核(路径变量+body)")
    @PostMapping("/audit/{id}")
    public R<MerchantExtension> auditByPath(@PathVariable("id") Long id,
                                            @RequestBody Map<String, Object> body) {
        String status = String.valueOf(body.get("status"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        // status: "pass"/"approved"/"1" -> 1通过, 其他 -> 2拒绝
        Integer auditStatus = ("pass".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)
                || "1".equals(status)) ? 1 : 2;
        return R.ok(merchantService.audit(id, auditStatus, reason));
    }

    @Operation(summary = "商家信用分明细")
    @GetMapping("/{id}/credit")
    public R<Map<String, Object>> creditDetail(@PathVariable("id") Long id) {
        return R.ok(merchantService.getCreditDetail(id));
    }

    @Operation(summary = "违规记录列表")
    @GetMapping("/violation/logs")
    public R<List<Map<String, Object>>> violationLogs(@RequestParam(required = false, defaultValue = "1") Integer page,
                                                       @RequestParam(required = false, defaultValue = "20") Integer size,
                                                       @RequestParam(required = false) Long merchantId) {
        // 违规记录由 risk-service 记录，通过 Feign 拉取后透传给前端
        try {
            R<Map<String, Object>> resp = riskFeignClient.logs(page, size, merchantId, null, null);
            if (resp != null && resp.getData() != null) {
                Object records = resp.getData().get("records");
                if (records instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> result = (List<Map<String, Object>>) (List<?>) list;
                    return R.ok(result);
                }
            }
        } catch (RuntimeException e) {
            log.warn("[violationLogs] 调用 risk-service 失败 merchantId={}", merchantId, e);
        }
        return R.ok(new ArrayList<>());
    }

    @Operation(summary = "商家处罚")
    @PostMapping("/{id}/penalty")
    public R<MerchantExtension> penalize(@PathVariable("id") Long id,
                                         @RequestBody Map<String, Object> body) {
        Integer type = body.get("type") == null ? null : Integer.parseInt(String.valueOf(body.get("type")));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        Integer days = body.get("days") == null ? null : Integer.parseInt(String.valueOf(body.get("days")));
        return R.ok(merchantService.penalize(id, type, reason, days));
    }

    @Operation(summary = "信用分调整(增量)")
    @PostMapping("/{id}/credit/adjust")
    public R<MerchantExtension> adjustCredit(@PathVariable("id") Long id,
                                             @RequestBody Map<String, Object> body) {
        Integer delta = body.get("delta") == null ? null : Integer.parseInt(String.valueOf(body.get("delta")));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return R.ok(merchantService.adjustCredit(id, delta, reason));
    }

    @Operation(summary = "更新店铺基本信息(店铺名/电话/营业时间/营业执照图片)")
    @PostMapping("/store/info")
    public R<MerchantExtension> updateStoreInfo(@RequestBody Map<String, Object> body) {
        Long merchantId = body.get("merchantId") == null ? null
                : Long.parseLong(String.valueOf(body.get("merchantId")));
        return R.ok(merchantService.updateStoreInfo(merchantId, body));
    }

    @Operation(summary = "线下门店地址列表")
    @GetMapping("/store/addresses")
    public R<List<StoreAddress>> listStoreAddresses(@RequestParam Long merchantId) {
        return R.ok(merchantService.listStoreAddresses(merchantId));
    }

    @Operation(summary = "新增/编辑线下门店地址")
    @PostMapping("/store/addresses")
    public R<StoreAddress> saveStoreAddress(@RequestBody StoreAddress body) {
        return R.ok(merchantService.saveStoreAddress(body));
    }

    @Operation(summary = "编辑线下门店地址(路径变量)")
    @PutMapping("/store/addresses/{id}")
    public R<StoreAddress> updateStoreAddress(@PathVariable("id") Long id,
                                              @RequestBody StoreAddress body) {
        body.setId(id);
        return R.ok(merchantService.saveStoreAddress(body));
    }

    @Operation(summary = "删除线下门店地址")
    @DeleteMapping("/store/addresses/{id}")
    public R<Void> deleteStoreAddress(@PathVariable("id") Long id,
                                      @RequestParam Long merchantId) {
        merchantService.deleteStoreAddress(id, merchantId);
        return R.ok();
    }

    @Operation(summary = "设置主地址")
    @PostMapping("/store/addresses/{id}/primary")
    public R<StoreAddress> setPrimaryAddress(@PathVariable("id") Long id,
                                             @RequestBody Map<String, Object> body) {
        Long merchantId = body.get("merchantId") == null ? null
                : Long.parseLong(String.valueOf(body.get("merchantId")));
        return R.ok(merchantService.setPrimaryAddress(id, merchantId));
    }

    @Operation(summary = "当日地址修改次数状态")
    @GetMapping("/store/addresses/update-state")
    public R<Map<String, Object>> addressUpdateState(@RequestParam Long merchantId) {
        return R.ok(merchantService.getAddressUpdateState(merchantId));
    }

    @Operation(summary = "更新商家最近核销日期(内部 Feign 调用)")
    @PostMapping("/last-nh-date")
    public R<Void> updateLastNhDate(@RequestParam("merchantId") Long merchantId,
                                    @RequestParam("nhDate") LocalDate nhDate) {
        merchantService.updateLastNhDate(merchantId, nhDate);
        return R.ok();
    }
}
