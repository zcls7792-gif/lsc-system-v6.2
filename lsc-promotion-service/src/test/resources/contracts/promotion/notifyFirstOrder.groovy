package contracts.promotion

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should accept first-order-notify (void) — consumer: order-service (orderPromotionClient#notifyFirstOrder)"
    name "notifyFirstOrder_success"
    priority 1
    request {
        method POST()
        // 注：为了避免 StubRunner 启动时 Xeger 无法生成长 regex 的随机值，
        // consumer 侧用精确 URL（WireMock 精确匹配），producer 侧用具体值做集成测试。
        // SCC 的 url() API: consumer 精确值 -> WireMock 生成 "url" 精确匹配；
        // producer 精确值 -> 提供契约测试的具体请求。
        url '/api/promotion/first-order-notify?consumerId=10001&orderNo=ORD-20260901-00100&orderAmount=168.00&orderStatus=2&refundAmount=0.00'
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([
                code     : 0,
                message  : "success",
                timestamp: value(consumer(1756900000005L), producer(regex('[0-9]{10,}'))),
                data     : null
        ])
    }
}
