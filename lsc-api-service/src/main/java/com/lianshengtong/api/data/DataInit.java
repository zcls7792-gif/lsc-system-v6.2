package com.lianshengtong.api.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInit implements CommandLineRunner {
    @Override
    public void run(String... args) {
        MockData.init();
        System.out.println("[LSC API] 内存数据初始化完成: 商家" + MockData.merchants.size()
                + " 商品" + MockData.products.size()
                + " 订单" + MockData.orders.size());
    }
}
