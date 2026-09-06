package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Orders {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer orderType; // 0=线上商城, 1=线下消费
    private Integer paymentType; // 0=纯人民币支付, 1=LSC全额抵扣, 2=混合支付
    private Integer isFirstOrder; // 0=非首单, 1=首单
    private Long userId;
    private Long merchantId;
    private Long productId;
    private String productName;
    private BigDecimal totalPrice;
    private Long lscAmount;
    private BigDecimal rmbAmount;
    private Integer status; // 0=待支付,1=已支付,2=已完成,3=已取消,4=已退款,5=部分退款
    private Long refundLscAmount;
    private BigDecimal refundRmbAmount;
    private Long addressId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
