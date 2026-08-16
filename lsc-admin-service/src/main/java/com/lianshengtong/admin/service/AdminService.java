package com.lianshengtong.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.admin.entity.Admin;

import java.util.Map;

/**
 * 管理员服务接口
 * <p>管理员登录、权限校验、CRUD。</p>
 */
public interface AdminService {

    /**
     * 管理员登录
     *
     * @param username 账号
     * @param password 密码
     * @param clientIp 登录IP
     * @return 含 token 与管理员信息
     */
    Map<String, Object> login(String username, String password, String clientIp);

    /**
     * 获取管理员信息
     *
     * @param adminId 管理员ID
     * @return 管理员实体
     */
    Admin getAdminInfo(Long adminId);

    /**
     * 权限校验
     *
     * @param adminId 管理员ID
     * @param requiredRole 需要的角色(0超管拥有全部权限)
     * @return 是否有权限
     */
    boolean checkPermission(Long adminId, Integer requiredRole);

    /**
     * 管理员分页列表
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键词(用户名/真实姓名)
     * @param role    角色(可空)
     * @return 分页结果
     */
    IPage<Admin> listAdmins(Integer page, Integer size, String keyword, Integer role);

    /**
     * 新增管理员
     *
     * @param admin    管理员信息(含明文密码 password 字段，服务端 BCrypt 加密)
     * @return 新建后的管理员(不含密码哈希)
     */
    Admin addAdmin(Admin admin);

    /**
     * 修改管理员(密码为空表示不修改密码)
     *
     * @param adminId 管理员ID
     * @param admin   管理员信息
     * @return 修改后的管理员
     */
    Admin updateAdmin(Long adminId, Admin admin);

    /**
     * 删除管理员(软删除：status 置 0)
     *
     * @param adminId 管理员ID
     */
    void deleteAdmin(Long adminId);
}
