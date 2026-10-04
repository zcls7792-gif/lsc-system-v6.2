package com.lianshengtong.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.product.entity.ProductPriceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductPriceVersionMapper extends BaseMapper<ProductPriceVersion> {

    @Select("SELECT * FROM product_price_version WHERE sku_id = #{skuId} AND effective_at <= NOW() " +
            "ORDER BY price_version DESC LIMIT 1")
    ProductPriceVersion selectLatestEffective(@Param("skuId") Long skuId);
}
