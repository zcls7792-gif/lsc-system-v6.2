package com.lianshengtong.common.sharding;

/**
 * 分库分表路由工具
 * 8库32表：按user_id取模32
 * 库序号 = (user_id % 32) / 4   范围 0..7
 * 表序号 = (user_id % 32) % 4   范围 0..3
 */
public class ShardingRouter {

    public static final int SHARDING_COUNT = 32;
    public static final int DB_COUNT = 8;
    public static final int TABLES_PER_DB = 4;

    public static int getDbIndex(long userId) {
        return (int) ((userId % SHARDING_COUNT) / TABLES_PER_DB);
    }

    public static int getTableIndex(long userId) {
        return (int) ((userId % SHARDING_COUNT) % TABLES_PER_DB);
    }

    public static String getDbName(long userId, String base) {
        return base + "_" + getDbIndex(userId);
    }

    public static String getTableName(long userId, String base) {
        return base + "_" + getTableIndex(userId);
    }
}
