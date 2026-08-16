package com.lianshengtong.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 参数变更审批实体 (param_change_approvals 表)
 * <p>关键参数(如释放比例 editable=1)变更需双人审批：发起人提交后，至少2名管理员签名方可生效，并链上存证。</p>
 * <ul>
 *   <li>status: 0待审批 1已通过 2已拒绝 3已生效</li>
 * </ul>
 */
@Data
@TableName("param_change_approvals")
public class ParamChangeApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 原值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 发起人(管理员ID) */
    private Long initiatorId;

    /** 状态 0待审批 1已通过 2已拒绝 3已生效 */
    private Integer status;

    /** 审批签名列表(JSON数组, 至少2名管理员签名) */
    private String approverSignatures;

    /** 已签名管理员ID列表(JSON) */
    private String signedAdminIds;

    /** 链上存证交易哈希 */
    private String evidenceTxHash;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
