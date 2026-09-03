package contracts.ledger

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should pay LSC: consumer available -> merchant available — consumer: order-service"
    name "payLsc_success"
    priority 2
    request {
        method POST()
        urlPath "/api/ledger/pay"
        headers { contentType(applicationJson()) }
        body([
                userId         : value(consumer(regex('[0-9]{1,19}')), producer(10001L)),
                counterpartyId : value(consumer(regex('[0-9]{1,19}')), producer(20001L)),
                availableDelta : value(consumer(regex('[1-9][0-9]*')), producer(3000L)),
                lockedDelta    : value(consumer(optional(regex('[0-9]*'))), producer(null)),
                orderNo        : value(consumer(anyNonBlankString()), producer("ORD-20260901-00002")),
                idempotentKey  : value(consumer(optional(anyNonBlankString())), producer("idem-pay-001")),
                transactionType: value(consumer(optional(regex('[0-9]+'))), producer(3)),
                remark         : value(consumer(optional(anyNonBlankString())), producer("消费支付"))
        ])
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([
                code     : 0,
                message  : "success",
                timestamp: value(consumer(1756900000001L), producer(regex('[0-9]{10,}'))),
                data     : [
                        userId        : 10001L,
                        totalLocked   : 0L,
                        totalAvailable: 2000L,
                        version       : 2,
                        updatedAt     : value(consumer("2026-09-01T10:05:00"), producer(regex('.+')))
                ]
        ])
    }
}
