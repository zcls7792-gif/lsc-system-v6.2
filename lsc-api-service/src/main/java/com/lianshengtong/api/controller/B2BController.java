package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.B2BOrder;
import com.lianshengtong.api.repository.B2BOrderRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/b2b")
public class B2BController {

    private static final String[] STATUS_DESC = {"待确认", "已确认", "已完成", "已取消"};

    private final B2BOrderRepository b2bRepo;

    public B2BController(B2BOrderRepository b2bRepo) {
        this.b2bRepo = b2bRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<B2BOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        List<B2BOrder> filtered = b2bRepo.findAll().stream().filter(o -> {
            if (status != null && !status.equals(o.getStatus())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<B2BOrder> detail(@PathVariable String orderNo) {
        return b2bRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("B2B订单不存在"));
    }

    /** 创建 B2B 订单落库 */
    @PostMapping("/create")
    public ApiResponse<B2BOrder> create(@RequestBody B2BOrder body) {
        long newId = b2bRepo.count() + 1;
        body.setId(newId);
        if (body.getOrderNo() == null || body.getOrderNo().isEmpty()) {
            body.setOrderNo("B2B" + System.currentTimeMillis());
        }
        if (body.getStatus() == null) body.setStatus(0);
        if (body.getStatusDesc() == null && body.getStatus() >= 0 && body.getStatus() < STATUS_DESC.length) {
            body.setStatusDesc(STATUS_DESC[body.getStatus()]);
        }
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now().toString());
        }
        return ApiResponse.success(b2bRepo.save(body));
    }

    /** 确认订单落库：status 0 -> 1 */
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, Object> body) {
        updateStatus(body, 1, "已确认");
        return ApiResponse.success(null);
    }

    /** 取消订单落库：status -> 3 */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body) {
        updateStatus(body, 3, "已取消");
        return ApiResponse.success(null);
    }

    /** 完成订单落库：status -> 2 */
    @PostMapping("/complete")
    public ApiResponse<Void> complete(@RequestBody Map<String, Object> body) {
        updateStatus(body, 2, "已完成");
        return ApiResponse.success(null);
    }

    @GetMapping("/{orderNo}/documents")
    public ApiResponse<List<Map<String, Object>>> documents(@PathVariable String orderNo) {
        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(mapOf("id", 1, "name", "采购合同", "url", "/files/contract.pdf"));
        docs.add(mapOf("id", 2, "name", "发票", "url", "/files/invoice.pdf"));
        return ApiResponse.success(docs);
    }

    @GetMapping("/{orderNo}/verify-result")
    public ApiResponse<Map<String, Object>> verifyResult(@PathVariable String orderNo) {
        return ApiResponse.success(mapOf("verified", true, "score", 92,
                "items", List.of("营业执照匹配", "账户信息一致", "商品品类合规")));
    }

    @PostMapping("/{orderNo}/verify-confirm")
    public ApiResponse<Void> verifyConfirm(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    private void updateStatus(Map<String, Object> body, int status, String statusDesc) {
        Object orderNoObj = body.get("orderNo");
        Object idObj = body.get("id");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(status);
                        o.setStatusDesc(statusDesc);
                        b2bRepo.save(o);
                    });
        } else if (idObj != null) {
            long id = Long.parseLong(idObj.toString());
            b2bRepo.findById(id).ifPresent(o -> {
                o.setStatus(status);
                o.setStatusDesc(statusDesc);
                b2bRepo.save(o);
            });
        }
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
