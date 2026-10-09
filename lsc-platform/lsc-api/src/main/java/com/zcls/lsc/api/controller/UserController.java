package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.user.AdminRoleService;
import com.zcls.lsc.user.BusinessProfileService;
import com.zcls.lsc.user.LegalEntityService;
import com.zcls.lsc.user.enums.UserEnums.EntityRole;
import com.zcls.lsc.user.enums.UserEnums.EntityStatus;
import com.zcls.lsc.user.enums.UserEnums.ScopeType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第12.2章 用户主体与权限 REST 接口。
 * 法律主体 + B端资质 + 管理员角色绑定。
 */
@RestController
@RequestMapping("/admin")
public class UserController {

    private final LegalEntityService legalEntityService;
    private final BusinessProfileService businessProfileService;
    private final AdminRoleService adminRoleService;

    public UserController(LegalEntityService legalEntityService,
                          BusinessProfileService businessProfileService,
                          AdminRoleService adminRoleService) {
        this.legalEntityService = legalEntityService;
        this.businessProfileService = businessProfileService;
        this.adminRoleService = adminRoleService;
    }

    // ==================== 法律主体 ====================

    @PostMapping("/legal-entities")
    public ApiResponse<Long> createEntity(@RequestParam String legalName,
                                          @RequestParam String registrationNo,
                                          @RequestParam EntityRole role,
                                          @RequestParam(required = false) String payMerchantId) {
        return ApiResponse.ok(legalEntityService.createEntity(
                legalName, registrationNo, role, payMerchantId));
    }

    @PostMapping("/legal-entities/{id}/verify")
    public ApiResponse<Void> verifyEntity(@PathVariable long id) {
        legalEntityService.verifyEntity(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/legal-entities/{id}/reject")
    public ApiResponse<Void> rejectEntity(@PathVariable long id,
                                          @RequestParam(required = false) String reason) {
        legalEntityService.rejectEntity(id, reason);
        return ApiResponse.ok(null);
    }

    @PostMapping("/legal-entities/{id}/disable")
    public ApiResponse<Void> disableEntity(@PathVariable long id) {
        legalEntityService.disableEntity(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/legal-entities/{id}")
    public ApiResponse<Map<String, Object>> getEntity(@PathVariable long id) {
        return ApiResponse.ok(legalEntityService.getEntity(id));
    }

    @GetMapping("/legal-entities")
    public ApiResponse<List<Map<String, Object>>> listEntities(
            @RequestParam(required = false) EntityRole role,
            @RequestParam(required = false) EntityStatus status) {
        if (role != null) {
            return ApiResponse.ok(legalEntityService.listByRole(role));
        }
        if (status != null) {
            return ApiResponse.ok(legalEntityService.listByStatus(status));
        }
        return ApiResponse.ok(List.of());
    }

    // ==================== B端资质 ====================

    @PostMapping("/business-profiles/{userId}/apply")
    public ApiResponse<Long> applyBusiness(@PathVariable long userId,
                                           @RequestParam String entityName,
                                           @RequestParam String licenseNo,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate licenseExpiry,
                                           @RequestParam String licenseObjectKey) {
        return ApiResponse.ok(businessProfileService.submitApplication(
                userId, entityName, licenseNo, licenseExpiry, licenseObjectKey));
    }

    @PostMapping("/business-audits/{auditId}/audit")
    public ApiResponse<Void> auditBusiness(@PathVariable long auditId,
                                           @RequestParam long auditorId,
                                           @RequestParam boolean approved,
                                           @RequestParam(required = false) String remark) {
        businessProfileService.auditApplication(auditId, auditorId, approved, remark);
        return ApiResponse.ok(null);
    }

    @PostMapping("/business-profiles/{userId}/suspend")
    public ApiResponse<Void> suspendBusiness(@PathVariable long userId,
                                             @RequestParam(required = false) String reason) {
        businessProfileService.suspendProfile(userId, reason);
        return ApiResponse.ok(null);
    }

    @PostMapping("/business-profiles/{userId}/resume")
    public ApiResponse<Void> resumeBusiness(@PathVariable long userId) {
        businessProfileService.resumeProfile(userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/business-profiles/{userId}")
    public ApiResponse<Map<String, Object>> getBusinessProfile(@PathVariable long userId) {
        return ApiResponse.ok(businessProfileService.getProfile(userId));
    }

    @GetMapping("/business-profiles/{userId}/audits")
    public ApiResponse<List<Map<String, Object>>> listAuditHistory(@PathVariable long userId) {
        return ApiResponse.ok(businessProfileService.listAuditHistory(userId));
    }

    @PostMapping("/business-profiles/expire-scan")
    public ApiResponse<Integer> expireScan() {
        return ApiResponse.ok(businessProfileService.expireOverdueProfiles());
    }

    // ==================== 管理员角色 ====================

    @PostMapping("/admin-roles/bindings")
    public ApiResponse<Long> bindRole(@RequestParam long adminId,
                                      @RequestParam String roleCode,
                                      @RequestParam ScopeType scopeType,
                                      @RequestParam long scopeId,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime validUntil) {
        return ApiResponse.ok(adminRoleService.bindRole(
                adminId, roleCode, scopeType, scopeId, validUntil));
    }

    @DeleteMapping("/admin-roles/bindings/{bindingId}")
    public ApiResponse<Void> unbindRole(@PathVariable long bindingId) {
        adminRoleService.unbindRole(bindingId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin-roles/{adminId}/check")
    public ApiResponse<Boolean> checkRole(@PathVariable long adminId,
                                          @RequestParam String roleCode) {
        return ApiResponse.ok(adminRoleService.hasRole(adminId, roleCode));
    }

    @GetMapping("/admin-roles/{adminId}/bindings")
    public ApiResponse<List<Map<String, Object>>> listBindings(@PathVariable long adminId) {
        return ApiResponse.ok(adminRoleService.listBindings(adminId));
    }

    @GetMapping("/admin-roles/{roleCode}/admins")
    public ApiResponse<List<Map<String, Object>>> listAdminsByRole(@PathVariable String roleCode) {
        return ApiResponse.ok(adminRoleService.listAdminsByRole(roleCode));
    }
}
