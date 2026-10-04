package com.lianshengtong.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.supplychain.entity.StockBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StockBalanceMapper extends BaseMapper<StockBalance> {

    @Select("SELECT * FROM stock_balance WHERE warehouse_id = #{w} AND sku_id = #{s} AND batch_no = #{b} FOR UPDATE")
    StockBalance selectForUpdate(@Param("w") Long warehouseId, @Param("s") Long skuId, @Param("b") String batchNo);
}
