package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("release_config")
public class ReleaseConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal rateMax;
    private BigDecimal rateMin;
    private BigDecimal kMin;
    private BigDecimal kMax;
    private BigDecimal alpha;
    private LocalDateTime updatedAt;
}
