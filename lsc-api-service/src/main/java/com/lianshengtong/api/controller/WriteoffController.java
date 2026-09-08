package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Writeoff;
import com.lianshengtong.api.repository.WriteoffRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/writeoff")
public class WriteoffController {

    private static final String[] STATUS_DESC = {"待审核", "已通过", "已拒绝"};

    private final WriteoffRepository writeoffRepo;

    public WriteoffController(WriteoffRepository writeoffRepo) {
        this.writeoffRepo = writeoffRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Writeoff>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer merchantId) {
        List<Writeoff> filtered = writeoffRepo.findAll().stream().filter(w -> {
            if (status != null && !status.equals(w.getStatus())) return false;
            if (merchantId != null && !merchantId.equals(w.getMerchantId())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Writeoff> detail(@PathVariable String orderNo) {
        return writeoffRepo.findAll().stream()
                .filter(w -> orderNo.equals(w.getOrderNo()))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("核销记录不存在"));
    }

    @GetMapping("/by-id/{id}")
    public ApiResponse<Writeoff> byId(@PathVariable long id) {
        return writeoffRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("核销记录不存在"));
    }

    /** 核销申请落库 */
    @PostMapping("/apply")
    public ApiResponse<Writeoff> apply(@RequestBody Writeoff body) {
        long newId = writeoffRepo.count() + 1;
        body.setId(newId);
        if (body.getOrderNo() == null || body.getOrderNo().isEmpty()) {
            body.setOrderNo("WO" + System.currentTimeMillis());
        }
        if (body.getStatus() == null) body.setStatus(0);
        if (body.getStatusDesc() == null && body.getStatus() >= 0 && body.getStatus() < STATUS_DESC.length) {
            body.setStatusDesc(STATUS_DESC[body.getStatus()]);
        }
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now().toString());
        }
        return ApiResponse.success(writeoffRepo.save(body));
    }

    /** 核销审核落库：status 0 -> 1(通过)/2(拒绝) */
    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object statusObj = body.get("status");
        if (idObj != null && statusObj != null) {
            long id = Long.parseLong(idObj.toString());
            int status = Integer.parseInt(statusObj.toString());
            writeoffRepo.findById(id).ifPresent(w -> {
                w.setStatus(status);
                if (status >= 0 && status < STATUS_DESC.length) {
                    w.setStatusDesc(STATUS_DESC[status]);
                }
                writeoffRepo.save(w);
            });
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        List<Writeoff> all = writeoffRepo.findAll();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalCount", all.size());
        s.put("pendingCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 0).count());
        s.put("approvedCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 1).count());
        s.put("rejectedCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 2).count());
        s.put("totalLscAmount", all.stream().mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount()).sum());
        s.put("todayCount", 5);
        s.put("todayLscAmount", 2800);
        return ApiResponse.success(s);
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> quota(@RequestParam(required = false) Integer merchantId) {
        return ApiResponse.success(Map.of("dailyLimit", 5500, "usedToday", 1200, "remaining", 4300));
    }
}
