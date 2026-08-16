package com.lianshengtong.common.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 雪花ID生成器注册
 * 实体类主键使用 @TableId(type = IdType.ASSIGN_ID) 即可自动注入
 */
@Configuration
public class IdGeneratorConfig {

    @Bean
    public IdentifierGenerator identifierGenerator() {
        return entity -> SnowflakeIdUtil.id();
    }
}
