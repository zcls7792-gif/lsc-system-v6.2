package com.lianshengtong.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 区块链存证记录 Mapper
 */
@Mapper
public interface BlockchainRecordMapper extends BaseMapper<BlockchainRecord> {
}
