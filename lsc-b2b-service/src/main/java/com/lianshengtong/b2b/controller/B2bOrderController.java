package com.lianshengtong.b2b.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.b2b.dto.B2bOrderCancelDTO;
import com.lianshengtong.b2b.dto.B2bOrderCompleteDTO;
import com.lianshengtong.b2b.dto.B2bOrderConfirmDTO;
import com.lianshengtong.b2b.dto.B2bOrderCreateDTO;
import com.lianshengtong.b2b.dto.B2bOrderManualVerifyDTO;
import com.lianshengtong.b2b.dto.B2bOrderTransferDTO;
import com.lianshengtong.b2b.dto.B2bOrderVoidDTO;
import com.lianshengtong.b2b.entity.B2bOrder;
import com.lianshengtong.b2b.service.B2bOrderService;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.security.RequireAdminRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * B2B 交易订单接口
 */
@Tag(name = "B2B交易", description = "商家间LSC 1:1流转订单管理")
@RestController
@RequestMapping("/api/b2b")
@RequiredArgsConstructor
public class B2bOrderController {

    private final B2bOrderService b2bOrderService;

    @Operation(summary = "创建B2B订单")
    @PostMapping("/create")
    public R<B2bOrder> create(@Valid @RequestBody B2bOrderCreateDTO dto) {
        return R.ok(b2bOrderService.createOrder(dto));
    }

    @Operation(summary = "对手方确认订单")
    @PostMapping("/confirm")
    public R<B2bOrder> confirm(@Valid @RequestBody B2bOrderConfirmDTO dto) {
        return R.ok(b2bOrderService.confirmOrder(dto));
    }

    @Operation(summary = "执行LSC 1:1流转")
    @PostMapping("/transfer")
    public R<B2bOrder> transfer(@Valid @RequestBody B2bOrderTransferDTO dto) {
        return R.ok(b2bOrderService.executeTransfer(dto));
    }

    @Operation(summary = "双方取消订单")
    @PostMapping("/cancel")
    public R<B2bOrder> cancel(@Valid @RequestBody B2bOrderCancelDTO dto) {
        return R.ok(b2bOrderService.cancelOrder(dto));
    }

    @Operation(summary = "作废订单(AI核验/人工判定虚假贸易)")
    @PostMapping("/void")
    @RequireAdminRole(2)
    public R<B2bOrder> voidOrder(@Valid @RequestBody B2bOrderVoidDTO dto) {
        return R.ok(b2bOrderService.voidOrder(dto));
    }

    @Operation(summary = "标记B2B订单完成(商家端,对手方已收到货/履约完成)")
    @PostMapping("/complete")
    public R<B2bOrder> complete(@Valid @RequestBody B2bOrderCompleteDTO dto) {
        B2bOrderTransferDTO transferDto = new B2bOrderTransferDTO();
        transferDto.setOrderNo(dto.getOrderNo());
        transferDto.setOperatorId(dto.getOperatorId());
        return R.ok(b2bOrderService.executeTransfer(transferDto));
    }

    @Operation(summary = "获取AI核验结果")
    @GetMapping("/ai-verify/{orderNo}")
    public R<Map<String, Object>> aiVerify(@PathVariable String orderNo) {
        return R.ok(b2bOrderService.getAiVerification(orderNo));
    }

    @Operation(summary = "获取AI核验结果(管理后台兼容路径)")
    @GetMapping("/{orderNo}/verify-result")
    public R<Map<String, Object>> verifyResult(@PathVariable String orderNo) {
        return R.ok(b2bOrderService.getAiVerification(orderNo));
    }

    @Operation(summary = "分页查询订单列表(兼容 page/size 与 pageNum/pageSize)")
    @GetMapping("/list")
    public R<IPage<B2bOrder>> list(@RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size,
                                   @RequestParam(required = false) Integer pageNum,
                                   @RequestParam(required = false) Integer pageSize,
                                   @RequestParam(required = false) Long userId,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(required = false) String orderNo,
                                   @RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate) {
        // 兼容前端 page/size 与旧版 pageNum/pageSize
        Integer p = page != null ? page : pageNum;
        Integer s = size != null ? size : pageSize;
        return R.ok(b2bOrderService.listOrders(p, s, userId, status, orderNo, startDate, endDate));
    }

    @Operation(summary = "根据订单号查询订单详情")
    @GetMapping("/{orderNo}")
    public R<B2bOrder> detail(@PathVariable String orderNo) {
        return R.ok(b2bOrderService.getByOrderNo(orderNo));
    }

    @Operation(summary = "获取B2B订单贸易凭证列表(管理后台)")
    @GetMapping("/{orderNo}/documents")
    public R<Map<String, Object>> documents(@PathVariable String orderNo) {
        B2bOrder order = b2bOrderService.getByOrderNo(orderNo);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("contractNo", order.getContractNo());
        data.put("tradeEvidenceUrls", order.getTradeEvidenceUrls());
        data.put("tradeDescription", order.getTradeDescription());
        return R.ok(data);
    }

    @Operation(summary = "人工核验确认B2B订单(管理后台)")
    @PostMapping("/{orderNo}/verify-confirm")
    @RequireAdminRole(2)
    public R<B2bOrder> verifyConfirm(@PathVariable String orderNo,
                                     @Valid @RequestBody B2bOrderManualVerifyDTO dto) {
        return R.ok(b2bOrderService.manualVerifyConfirm(orderNo, dto.getResult(), dto.getRemark()));
    }
}
