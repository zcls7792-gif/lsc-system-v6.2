package com.lianshengtong.map.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 导航唤起结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavigateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 唤起方式 amap/baidu/tencent/apple/browser */
    private String scheme;

    /** 导航URL/scheme */
    private String url;

    /** 起点经纬度 */
    private String origin;

    /** 终点经纬度 */
    private String destination;
}
