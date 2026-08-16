package com.lianshengtong.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 *
 * @author lsc
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
