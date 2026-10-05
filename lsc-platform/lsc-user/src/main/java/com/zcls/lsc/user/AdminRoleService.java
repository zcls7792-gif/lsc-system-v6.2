package com.zcls.lsc.user;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.user.enums.UserEnums.ScopeType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第12.2章 管理员角色绑定与权限校验服务。
 *
 *  - admin_role_binding：(admin_id, role_code, scope_type, scope_id) 唯一
 *  - 权限校验口径：在 valid_until 之前（或为 NULL 永久）的绑定视为有效
 *  - hasRole 用于接口层权限拦截，hasAnyScope 用于数据范围校验
 */
@Service
public class AdminRoleService {

    private final JdbcTemplate jdbc;

    public AdminRoleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 绑定管理员角色。
     * DB uk_admin_role 兜底防重复绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public long bindRole(long adminId, String roleCode, ScopeType scopeType,
                         long scopeId, LocalDateTime validUntil) {
        long bindingId = nextId();
        try {
            jdbc.update(
                    "INSERT INTO admin_role_binding(binding_id, admin_id, role_code, scope_type, "
                            + "scope_id, valid_until) VALUES(?,?,?,?,?,?)",
                    bindingId, adminId, roleCode, scopeType.name(), scopeId,
                    validUntil == null ? null : Timestamp.valueOf(validUntil));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "role binding already exists for admin=" + adminId + " role=" + roleCode);
        }
        return bindingId;
    }

    /**
     * 解除角色绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbindRole(long bindingId) {
        int updated = jdbc.update("DELETE FROM admin_role_binding WHERE binding_id=?", bindingId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "binding not found: " + bindingId);
        }
    }

    /**
     * 校验管理员是否拥有指定角色（在有效范围内）。
     *
     * @param adminId 管理员ID
     * @param roleCode 角色编码
     * @return true 表示拥有该角色
     */
    public boolean hasRole(long adminId, String roleCode) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_role_binding WHERE admin_id=? AND role_code=? "
                        + "AND (valid_until IS NULL OR valid_until > ?)",
                Integer.class, adminId, roleCode, Timestamp.valueOf(LocalDateTime.now()));
        return count != null && count > 0;
    }

    /**
     * 校验管理员在指定作用域内是否拥有指定角色。
     */
    public boolean hasRoleInScope(long adminId, String roleCode, ScopeType scopeType, long scopeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_role_binding WHERE admin_id=? AND role_code=? "
                        + "AND scope_type=? AND scope_id=? "
                        + "AND (valid_until IS NULL OR valid_until > ?)",
                Integer.class, adminId, roleCode, scopeType.name(), scopeId,
                Timestamp.valueOf(LocalDateTime.now()));
        return count != null && count > 0;
    }

    /**
     * 校验管理员是否拥有任一指定角色（OR 语义）。
     * 用于接口层多角色放行。
     */
    public boolean hasAnyRole(long adminId, List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) return false;
        for (String role : roleCodes) {
            if (hasRole(adminId, role)) return true;
        }
        return false;
    }

    /**
     * 查询管理员的所有有效角色绑定。
     */
    public List<Map<String, Object>> listBindings(long adminId) {
        return jdbc.queryForList(
                "SELECT * FROM admin_role_binding WHERE admin_id=? "
                        + "AND (valid_until IS NULL OR valid_until > ?) ORDER BY role_code",
                adminId, Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * 查询某角色下的所有管理员。
     */
    public List<Map<String, Object>> listAdminsByRole(String roleCode) {
        return jdbc.queryForList(
                "SELECT * FROM admin_role_binding WHERE role_code=? "
                        + "AND (valid_until IS NULL OR valid_until > ?)",
                roleCode, Timestamp.valueOf(LocalDateTime.now()));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
