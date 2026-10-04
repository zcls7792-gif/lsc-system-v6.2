package com.lianshengtong.ledger.controller;

import com.lianshengtong.common.lsc.LscUnitConstants;
import com.lianshengtong.common.lsc.ReleaseRateCalculator;
import com.lianshengtong.common.result.R;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscEvent;
import com.lianshengtong.ledger.entity.LscGrantLot;
import com.lianshengtong.ledger.mapper.LscEventMapper;
import com.lianshengtong.ledger.mapper.LscGrantLotMapper;
import com.lianshengtong.ledger.service.LscLedgerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * LSC 权益账本接口（V7.7.2 第十三章）
 */
@RestController
@RequestMapping("/v1/lsc")
@RequiredArgsConstructor
public class LscLedgerController {

    private final LscLedgerService ledgerService;
    private final LscEventMapper eventMapper;
    private final LscGrantLotMapper grantLotMapper;

    /** 查询账户：5桶余额 + 追偿（unit 字符串） */
    @GetMapping("/account")
    public R<Map<String, Object>> getAccount(@RequestAttribute("userId") Long userId) {
        LscAccount acc = ledgerService.getAccount(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("lockedUnit", String.valueOf(acc.getLockedUnit()));
        data.put("availableUnit", String.valueOf(acc.getAvailableUnit()));
        data.put("reservedUnit", String.valueOf(acc.getReservedUnit()));
        data.put("frozenLockedUnit", String.valueOf(acc.getFrozenLockedUnit()));
        data.put("frozenAvailableUnit", String.valueOf(acc.getFrozenAvailableUnit()));
        data.put("pendingRecoveryUnit", String.valueOf(acc.getPendingRecoveryUnit()));
        // 合计展示
        data.put("totalUnit", String.valueOf(
                acc.getLockedUnit() + acc.getAvailableUnit() + acc.getReservedUnit()
                        + acc.getFrozenLockedUnit() + acc.getFrozenAvailableUnit()));
        data.put("frozenTotalUnit", String.valueOf(
                acc.getFrozenLockedUnit() + acc.getFrozenAvailableUnit()));
        return R.ok(data);
    }

    /** 权益明细：释放、返还、扣回、过期 */
    @GetMapping("/events")
    public R<IPage<LscEvent>> listEvents(@RequestAttribute("userId") Long userId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Page<LscEvent> p = new Page<>(page, size);
        LambdaQueryWrapper<LscEvent> w = new LambdaQueryWrapper<LscEvent>()
                .eq(LscEvent::getUserId, userId)
                .orderByDesc(LscEvent::getUserEventSeq);
        return R.ok(eventMapper.selectPage(p, w));
    }

    /** 查询用户的 GrantLot 列表 */
    @GetMapping("/grant-lots")
    public R<IPage<LscGrantLot>> listGrantLots(@RequestAttribute("userId") Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Page<LscGrantLot> p = new Page<>(page, size);
        LambdaQueryWrapper<LscGrantLot> w = new LambdaQueryWrapper<LscGrantLot>()
                .eq(LscGrantLot::getUserId, userId)
                .orderByDesc(LscGrantLot::getGrantBusinessDate);
        return R.ok(grantLotMapper.selectPage(p, w));
    }

    // ==================== 管理端接口 ====================

    /** 手动触发指定用户某日释放（运维补偿用） */
    @PostMapping("/admin/release/{userId}")
    public R<Map<String, Object>> manualRelease(@PathVariable Long userId,
                                                 @RequestParam(required = false) String bizDate,
                                                 @RequestParam(defaultValue = "750000") long ratePpb) {
        LocalDate date = bizDate != null ? LocalDate.parse(bizDate) : LocalDate.now();
        ledgerService.dailyRelease(userId, date, ratePpb);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("bizDate", date.toString());
        data.put("ratePpb", ratePpb);
        return R.ok(data);
    }

    /** 过期作废扫描（手动触发） */
    @PostMapping("/admin/expire/{userId}")
    public R<Void> manualExpire(@PathVariable Long userId) {
        ledgerService.expireAvailableLots(userId, java.time.LocalDateTime.now());
        return R.ok();
    }

    /** 管理端查询任意用户账户 */
    @GetMapping("/admin/account/{userId}")
    public R<Map<String, Object>> getAccountAdmin(@PathVariable Long userId) {
        return getAccount(userId);
    }
}
