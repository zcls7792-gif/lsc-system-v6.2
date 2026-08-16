package com.lianshengtong.writeoff.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.writeoff.dto.WriteOffApplyDTO;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;
import com.lianshengtong.writeoff.service.WriteOffService;
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
 * 商家核销接口
 * <p>
 * 与 lsc-admin-web 的 writeoff.ts 契约对齐：
 * <ul>
 *   <li>GET /list 兼容 page/size 与 pageNum/pageSize, 支持 batchNo/startDate/endDate 过滤</li>
 *   <li>GET /{orderNo} 按核销订单号查询</li>
 *   <li>GET /by-id/{id} 按主键ID查询(兼容前端)</li>
 *   <li>GET /stats 核销统计</li>
 * </ul>
 * </p>
 */
@Tag(name = "核销", description = "商家LSC核销兑换现金(100:87)")
@RestController
@RequestMapping("/api/writeoff")
@RequiredArgsConstructor
public class WriteOffController {

    private final WriteOffService writeOffService;

    @Operation(summary = "申请核销")
    @PostMapping("/apply")
    public R<MerchantNhRecord> apply(@Valid @RequestBody WriteOffApplyDTO dto) {
        return R.ok(writeOffService.applyWriteOff(dto));
    }

    @Operation(summary = "根据核销订单号查询核销记录")
    @GetMapping("/{orderNo}")
    public R<MerchantNhRecord> detail(@PathVariable String orderNo) {
        return R.ok(writeOffService.getByOrderNo(orderNo));
    }

    @Operation(summary = "根据主键ID查询核销记录(管理后台兼容)")
    @GetMapping("/by-id/{id}")
    public R<MerchantNhRecord> detailById(@PathVariable Long id) {
        return R.ok(writeOffService.getById(id));
    }

    @Operation(summary = "分页查询核销记录列表(兼容 page/size 与 pageNum/pageSize)")
    @GetMapping("/list")
    public R<IPage<MerchantNhRecord>> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) Integer pageNum,
                                           @RequestParam(required = false) Integer pageSize,
                                           @RequestParam(required = false) Long merchantId,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String batchNo,
                                           @RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate) {
        Integer p = page != null ? page : pageNum;
        Integer s = size != null ? size : pageSize;
        return R.ok(writeOffService.listRecords(p, s, merchantId, status, batchNo, startDate, endDate));
    }

    @Operation(summary = "核销统计(总笔数/总金额/按状态分组)")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam(required = false) Long merchantId,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        return R.ok(writeOffService.stats(merchantId, startDate, endDate));
    }

    @Operation(summary = "核销限额预览(商家端)")
    @GetMapping("/quota")
    public R<Map<String, Object>> quota(@RequestParam Long merchantId) {
        return R.ok(writeOffService.quota(merchantId));
    }
}
