package com.lianshengtong.release.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.release.entity.ReleaseConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 释放配置 Mapper
 */
@Mapper
public interface ReleaseConfigMapper extends BaseMapper<ReleaseConfig> {

    /**
     * 按配置键查询
     */
    default ReleaseConfig findByKey(String configKey) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReleaseConfig>()
                .eq(ReleaseConfig::getConfigKey, configKey));
    }
}
