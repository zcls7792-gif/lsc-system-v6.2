package com.lianshengtong.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.INPUT)
    private Long itemId;
    private Long orderId;
    private Long skuId;
    private Integer itemSeq;
    private Integer qty;
    private Integer priceVersion;
    private Long unitPriceCent;
    private Long lineGoodsCent;
    private Long couponShareCent;
    private Long lscShareUnit;
    private Long rmbShareCent;
    private Long grantCoefficientPpm;
    private String costSnapshotEnc;
    private Long grantedUnit;
    private Long grantLotId;
    private Long clawbackRequiredUnit;
    private Long clawbackCompletedUnit;
    private Long clawbackPendingUnit;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
}
