package com.zcls.lsc.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序配置 — 从环境变量读取敏感凭证，不入仓库。
 *
 * 对应环境变量：
 *   WX_MP_APP_ID      小程序 AppID
 *   WX_MP_APP_SECRET  小程序 AppSecret（仅服务端使用，禁止写入前端 manifest/.env）
 *   WX_MP_TOKEN       消息校验 token（可选，消息推送时使用）
 *   WX_MP_AES_KEY     消息加解密密钥（可选）
 */
@Configuration
@ConfigurationProperties(prefix = "wx.mp")
public class WxMpProperties {

    /** 小程序 AppID（wx 开头 18 位） */
    private String appId;

    /** 小程序 AppSecret — 服务端独有，禁止下发前端 */
    private String appSecret;

    /** 消息推送校验 token（可选） */
    private String token;

    /** 消息加解密 AES 密钥（可选） */
    private String aesKey;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }
}
