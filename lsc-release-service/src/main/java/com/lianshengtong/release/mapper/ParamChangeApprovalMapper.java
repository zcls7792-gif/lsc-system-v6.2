package com.lianshengtong.release.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.release.entity.ParamChangeApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 参数变更审批 Mapper
 */
@Mapper
public interface ParamChangeApprovalMapper extends BaseMapper<ParamChangeApproval> {

    /**
     * 按配置键查询最近一条审批记录
     */
    default ParamChangeApproval selectLatestByKey(String configKey) {
        return selectOne(new LambdaQueryWrapper<ParamChangeApproval>()
                .eq(ParamChangeApproval::getConfigKey, configKey)
                .orderByDesc(ParamChangeApproval::getId)
                .last("LIMIT 1"));
    }
}
