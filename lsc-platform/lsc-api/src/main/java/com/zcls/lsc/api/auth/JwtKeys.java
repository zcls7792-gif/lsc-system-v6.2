package com.zcls.lsc.api.auth;

/**
 * JWT 签名密钥（开发期占位，生产环境通过环境变量 LSC_JWT_SECRET 注入）。
 * 密钥至少 32 字节（HS256 要求）。
 */
public final class JwtKeys {

    /** 开发期占位密钥，生产必须替换。 */
    public static final String DEV_SECRET = "lsc-platform-dev-secret-key-7.7.2-min-32bytes!";

    /** 从环境变量读取，缺省回退开发密钥。 */
    public static String activeSecret() {
        String env = System.getenv("LSC_JWT_SECRET");
        return (env != null && env.length() >= 32) ? env : DEV_SECRET;
    }

    private JwtKeys() {}
}
