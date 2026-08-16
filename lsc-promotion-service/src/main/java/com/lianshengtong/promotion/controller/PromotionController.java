package com.lianshengtong.promotion.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.promotion.dto.FirstOrderCheckDTO;
import com.lianshengtong.promotion.dto.RewardResultDTO;
import com.lianshengtong.promotion.dto.RollbackRewardDTO;
import com.lianshengtong.promotion.entity.PromotionPending;
import com.lianshengtong.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 推广服务接口
 * <p>严格一级推荐首单奖励：首单消费金额的10%，从推荐人锁定池划转至可用池。</p>
 */
@Tag(name = "推广服务", description = "一级推荐首单判定、奖励计算划转、退款回滚、挂账补发")
@RestController
@RequestMapping("/api/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @Operation(summary = "首单判定")
    @PostMapping("/check-first-order")
    public R<RewardResultDTO> checkFirstOrder(@Valid @RequestBody FirstOrderCheckDTO dto) {
        return R.ok(promotionService.checkFirstOrder(dto));
    }

    @Operation(summary = "奖励计算与划转")
    @PostMapping("/calc-reward")
    public R<RewardResultDTO> calcReward(@Valid @RequestBody FirstOrderCheckDTO dto) {
        return R.ok(promotionService.calcReward(dto));
    }

    @Operation(summary = "首单全额退款奖励回滚")
    @PostMapping("/rollback")
    public R<Void> rollback(@Valid @RequestBody RollbackRewardDTO dto) {
        promotionService.rollbackReward(dto);
        return R.ok();
    }

    @Operation(summary = "挂账列表查询")
    @GetMapping("/pending-list")
    public R<IPage<PromotionPending>> pendingList(@RequestParam(required = false, defaultValue = "1") Integer page,
                                                   @RequestParam(required = false, defaultValue = "20") Integer size,
                                                   @RequestParam(required = false) Integer status) {
        return R.ok(promotionService.pendingList(page, size, status));
    }

    @Operation(summary = "首单完成通知(由 order-service 在订单完成时调用)")
    @PostMapping("/first-order-notify")
    public R<Void> firstOrderNotify(@RequestParam("consumerId") Long consumerId,
                                    @RequestParam("orderNo") String orderNo,
                                    @RequestParam("orderAmount") BigDecimal orderAmount,
                                    @RequestParam("orderStatus") Integer orderStatus,
                                    @RequestParam(value = "refundAmount", required = false) BigDecimal refundAmount) {
        promotionService.notifyFirstOrder(consumerId, orderNo, orderAmount, orderStatus, refundAmount);
        return R.ok();
    }
}
