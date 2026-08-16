package com.lianshengtong.b2b.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.b2b.entity.B2bOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * B2B 订单 Mapper
 * <p>
 * 订单状态更新统一通过 MyBatis-Plus 的 updateById 触发乐观锁(version)，
 * {@link BaseMapper#updateById(Object)} 在 version 不匹配时返回 0。
 * </p>
 */
@Mapper
public interface B2bOrderMapper extends BaseMapper<B2bOrder> {
}
