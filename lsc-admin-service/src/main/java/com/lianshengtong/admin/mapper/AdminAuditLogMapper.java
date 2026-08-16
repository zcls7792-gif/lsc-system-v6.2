package com.lianshengtong.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.admin.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员操作审计日志 Mapper
 */
@Mapper
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
