package contracts.order

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should create mall order (Feign simplify params) — consumer: mall-service (mallOrderClient#createMallOrder)"
    name "createMallOrder_success"
    priority 1
    request {
        method POST()
        // 注意：consumer 侧用 regex，且 '?' 显式转义为 regex 安全的 '\?'
        // 为了避免 Xeger 在解析 consumer regex 时崩溃，
        // 这里用精确 URL（consumer 侧测试代码确实就是用这些参数）。
        url '/api/order/create-mall?productId=101&merchantId=20001&consumerId=10001&lscAmount=5000&rmbAmount=19.90&totalPrice=69.90'
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([
                code     : 0,
                message  : "success",
                timestamp: value(consumer(1756900000003L), producer(regex('[0-9]{10,}'))),
                data     : "MALL-20260901-00001"
        ])
    }
}
