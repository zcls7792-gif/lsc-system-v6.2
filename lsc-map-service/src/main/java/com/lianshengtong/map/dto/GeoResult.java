package com.lianshengtong.map.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地理编码结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 格式化地址 */
    private String formattedAddress;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 区县 */
    private String district;

    /** 数据来源 amap/baidu */
    private String source;
}
