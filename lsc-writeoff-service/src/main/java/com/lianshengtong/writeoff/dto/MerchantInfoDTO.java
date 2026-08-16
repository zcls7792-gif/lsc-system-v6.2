package com.lianshengtong.writeoff.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商家扩展信息 DTO
 * <p>由 lsc-user-service 通过 Feign 返回，仅包含核销校验所需字段。</p>
 */
@Data
public class MerchantInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商家ID */
    private Long merchantId;

    /** 信用评分(默认100) */
    private Integer creditScore;

    /** 每日核销限额(默认80) */
    private Integer dailyNhLimit;

    /** 监管账户号 */
    private String regulatoryAccountNo;

    /** 主账户号 */
    private String mainAccountNo;

    /** 最近核销日期 */
    private LocalDate lastNhDate;

    /** 处罚状态 0正常 1一级 2二级 3三级 4清退 */
    private Integer penaltyStatus;
}
