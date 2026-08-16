package com.lianshengtong.writeoff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家核销记录 Mapper
 * <p>状态更新通过 updateById 触发乐观锁(version)，幂等通过 order_no/idempotent_key 唯一索引保障。</p>
 */
@Mapper
public interface MerchantNhRecordMapper extends BaseMapper<MerchantNhRecord> {
}
