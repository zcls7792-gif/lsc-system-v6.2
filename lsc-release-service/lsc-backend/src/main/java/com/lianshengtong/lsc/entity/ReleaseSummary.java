package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("release_summary")
public class ReleaseSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate releaseDate;
    private BigDecimal mTotal;
    private BigDecimal nTotal;
    private BigDecimal kValue;
    private BigDecimal rate;
    private Long lLocked;
    private Long tRelease;
    private Integer status;
}
