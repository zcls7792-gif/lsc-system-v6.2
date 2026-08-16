package com.lianshengtong.ledger.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;
import com.lianshengtong.ledger.service.LscLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * LSC 账本服务 Controller
 * <p>
 * 统一使用 {@link LscLedgerOpDTO} 接收请求：
 * <ul>
 *   <li>{@code userId} 操作发起方用户ID</li>
 *   <li>{@code counterpartyId} 对手方用户ID(支付/B2B 必填)</li>
 *   <li>{@code lockedDelta} 锁定变更量(发行场景使用)</li>
 *   <li>{@code availableDelta} 可用变更量(释放/支付/流转/核销/退款 使用)</li>
 *   <li>{@code orderNo} 关联订单号(幂等键来源)</li>
 * </ul>
 * </p>
 *
 * @author lsc
 */
@Tag(name = "LSC账本服务")
@RestController
@RequestMapping("/api/ledger")
public class LscLedgerController {

    private final LscLedgerService ledgerService;

    public LscLedgerController(LscLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Operation(summary = "消费发行LSC(锁定池增加)")
    @PostMapping("/issue")
    public R<LscAccount> issue(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.issueLsc(dto.getUserId(), resolveAmount(dto, true), dto.getOrderNo()));
    }

    @Operation(summary = "每日释放(锁定转可用)")
    @PostMapping("/release")
    public R<LscAccount> release(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.releaseLsc(dto.getUserId(), resolveAmount(dto, false), dto.getOrderNo()));
    }

    @Operation(summary = "消费支付(消费者可用转商家可用)")
    @PostMapping("/pay")
    public R<LscAccount> pay(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.payLsc(dto.getUserId(), dto.getCounterpartyId(),
                resolveAmount(dto, false), dto.getOrderNo()));
    }

    @Operation(summary = "B2B流转(商家间1:1,接收方有效期重置365天)")
    @PostMapping("/b2b-transfer")
    public R<LscAccount> b2bTransfer(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.b2bTransfer(dto.getUserId(), dto.getCounterpartyId(),
                resolveAmount(dto, false), dto.getOrderNo()));
    }

    @Operation(summary = "商家核销(可用余额销毁)")
    @PostMapping("/write-off")
    public R<LscAccount> writeOff(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.writeOffLsc(dto.getUserId(), resolveAmount(dto, false), dto.getOrderNo()));
    }

    @Operation(summary = "退款退回(消费者可用余额入账)")
    @PostMapping("/refund")
    public R<LscAccount> refund(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.refundLsc(dto.getUserId(), resolveAmount(dto, false), dto.getOrderNo()));
    }

    @Operation(summary = "过期转回(可用转锁定)")
    @PostMapping("/expire-transfer")
    public R<Long> expireTransfer(@RequestBody LscLedgerOpDTO dto) {
        return R.ok(ledgerService.expireTransfer(dto.getUserId()));
    }

    @Operation(summary = "全网过期转回(扫描全网可用明细，由 release-service 定时任务调用)")
    @PostMapping("/expire-transfer-all")
    public R<Map<String, Object>> expireTransferAll() {
        return R.ok(ledgerService.expireTransferAll());
    }

    @Operation(summary = "账户余额查询")
    @GetMapping("/account/{userId}")
    public R<LscAccount> account(@PathVariable Long userId) {
        return R.ok(ledgerService.getBalance(userId));
    }

    @Operation(summary = "按日期+流水类型聚合统计(对账场景使用)")
    @GetMapping("/daily-summary")
    public R<Map<String, Object>> dailySummary(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "types", required = false) String types) {
        // types 为逗号分隔的流水类型，可空表示全部
        List<Integer> typeList = null;
        if (types != null && !types.isBlank()) {
            typeList = Arrays.stream(types.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::valueOf)
                    .collect(Collectors.toList());
        }
        return R.ok(ledgerService.dailySummary(date, typeList));
    }

    @Operation(summary = "全网锁定余额汇总(每日释放任务加载待释放明细使用)")
    @GetMapping("/locked-summary")
    public R<Map<String, Object>> lockedSummary() {
        return R.ok(ledgerService.lockedSummary());
    }

    @Operation(summary = "批量释放(锁定 -> 可用，每批默认10万条)")
    @PostMapping("/release/batch")
    public R<Map<String, Object>> releaseBatch(@RequestBody List<LscLedgerOpDTO> opList) {
        return R.ok(ledgerService.releaseBatch(opList));
    }

    @Operation(summary = "用户流水分页查询(商家/管理后台)")
    @GetMapping("/transactions")
    public R<IPage<LscTransaction>> transactions(@RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false, defaultValue = "1") Integer page,
                                                  @RequestParam(required = false, defaultValue = "20") Integer size,
                                                  @RequestParam(required = false) Integer type,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false) String orderNo) {
        return R.ok(ledgerService.transactionList(userId, page, size, type, startDate, endDate, orderNo));
    }

    @Operation(summary = "用户可用LSC明细分页查询")
    @GetMapping("/available-details")
    public R<IPage<AvailableLscDetail>> availableDetails(@RequestParam(required = false) Long userId,
                                                           @RequestParam(required = false, defaultValue = "1") Integer page,
                                                           @RequestParam(required = false, defaultValue = "20") Integer size,
                                                           @RequestParam(required = false) Integer status) {
        return R.ok(ledgerService.availableDetails(userId, page, size, status));
    }

    @Operation(summary = "近N天交易趋势(按日聚合)")
    @GetMapping("/recent-trend")
    public R<List<Map<String, Object>>> recentTrend(@RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false, defaultValue = "7") Integer days) {
        return R.ok(ledgerService.recentTrend(userId, days));
    }

    @Operation(summary = "用户LSC概览(锁定/可用/已核销/月收入等)")
    @GetMapping("/overview/{userId}")
    public R<Map<String, Object>> overview(@PathVariable("userId") Long userId) {
        return R.ok(ledgerService.overview(userId));
    }

    /**
     * 解析操作数量：发行取 lockedDelta，其余取 availableDelta，自动回退取非空值，取绝对值
     */
    private Long resolveAmount(LscLedgerOpDTO dto, boolean useLocked) {
        Long locked = dto.getLockedDelta();
        Long available = dto.getAvailableDelta();
        Long raw = useLocked ? locked : available;
        if (raw == null) {
            raw = locked;
        }
        if (raw == null) {
            raw = available;
        }
        if (raw == null) {
            throw new BizException(400, "操作数量(lockedDelta/availableDelta)不能为空");
        }
        long amount = Math.abs(raw);
        if (amount <= 0) {
            throw new BizException(400, "操作数量必须为正数");
        }
        return amount;
    }


    public LscLedgerService getLedgerService() { return ledgerService; }
}
