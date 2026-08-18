package com.lianshengtong.common.sharding;

/**
 * 分库分表路由工具
 * 8库循环：每32个连续用户一个库，共8库轮转
 * - 库序号 = (用户块号) % 8，其中块号 = userId / 32
 * - 表序号 = userId % 4 （范围 0..3）
 * 负数ID使用正向取模修正，保证始终落在有效范围内。
 */
public class ShardingRouter {

    public static final int SHARDING_COUNT = 32;
    public static final int DB_COUNT = 8;
    public static final int TABLES_PER_DB = 4;

    public static int getDbIndex(long userId) {
        long block = userId / SHARDING_COUNT;
        return (int) (((block % DB_COUNT) + DB_COUNT) % DB_COUNT);
    }

    public static int getTableIndex(long userId) {
        return (int) (((userId % TABLES_PER_DB) + TABLES_PER_DB) % TABLES_PER_DB);
    }

    public static String getDbName(long userId, String base) {
        return base + "_" + getDbIndex(userId);
    }

    public static String getTableName(long userId, String base) {
        return base + "_" + getTableIndex(userId);
    }
}
