package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("lsc_account")
public class LscAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long totalLocked;
    private Long totalAvailable;
    @Version
    private Integer version;
    private LocalDateTime updatedAt;
}
