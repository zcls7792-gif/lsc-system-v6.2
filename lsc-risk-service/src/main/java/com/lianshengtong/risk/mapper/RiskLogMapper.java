package com.lianshengtong.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.risk.entity.RiskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风控日志 Mapper
 */
@Mapper
public interface RiskLogMapper extends BaseMapper<RiskLog> {
}
