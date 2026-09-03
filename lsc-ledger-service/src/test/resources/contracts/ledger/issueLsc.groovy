package contracts.ledger

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should issue LSC to locked pool — consumer: order-service (orderLedgerClient#issueLsc)"
    name "issueLsc_success"
    priority 1
    request {
        method POST()
        urlPath "/api/ledger/issue"
        headers { contentType(applicationJson()) }
        body([
                userId         : value(consumer(regex('[0-9]{1,19}')), producer(10001L)),
                lockedDelta    : value(consumer(regex('[1-9][0-9]*')), producer(5000L)),
                availableDelta : value(consumer(optional(regex('[0-9]*'))), producer(null)),
                counterpartyId : value(consumer(optional(regex('[0-9]*'))), producer(null)),
                orderNo        : value(consumer(anyNonBlankString()), producer("ORD-20260901-00001")),
                idempotentKey  : value(consumer(optional(anyNonBlankString())), producer("idem-issue-001")),
                transactionType: value(consumer(optional(regex('[0-9]+'))), producer(1)),
                remark         : value(consumer(optional(anyNonBlankString())), producer("消费发行-订单锁定"))
        ])
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([
                code     : 0,
                message  : "success",
                timestamp: value(consumer(1756900000000L), producer(regex('[0-9]{10,}'))),
                data     : [
                        userId        : 10001L,
                        totalLocked   : 5000L,
                        totalAvailable: 0L,
                        version       : 1,
                        // provider 侧 Jackson 默认序列化 LocalDateTime 为数组，consumer stub 侧是字符串；
                        // 所以 provider 断言用 regex 宽松匹配，consumer 保持字符串。
                        updatedAt     : value(consumer("2026-09-01T10:00:00"), producer(regex('.+')))
                ]
        ])
    }
}
