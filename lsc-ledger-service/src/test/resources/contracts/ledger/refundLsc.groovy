package contracts.ledger

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should refund LSC: merchant available -> consumer available — consumer: order-service"
    name "refundLsc_success"
    priority 3
    request {
        method POST()
        urlPath "/api/ledger/refund"
        headers { contentType(applicationJson()) }
        body([
                userId         : value(consumer(regex('[0-9]{1,19}')), producer(10001L)),
                availableDelta : value(consumer(regex('[1-9][0-9]*')), producer(1500L)),
                counterpartyId : value(consumer(optional(regex('[0-9]*'))), producer(20001L)),
                lockedDelta    : value(consumer(optional(regex('[0-9]*'))), producer(null)),
                orderNo        : value(consumer(anyNonBlankString()), producer("ORD-20260901-00003")),
                idempotentKey  : value(consumer(optional(anyNonBlankString())), producer("idem-refund-001")),
                transactionType: value(consumer(optional(regex('[0-9]+'))), producer(5)),
                remark         : value(consumer(optional(anyNonBlankString())), producer("订单退款"))
        ])
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([
                code     : 0,
                message  : "success",
                timestamp: value(consumer(1756900000002L), producer(regex('[0-9]{10,}'))),
                data     : [
                        userId        : 10001L,
                        totalLocked   : 3500L,
                        totalAvailable: 3500L,
                        version       : 3,
                        updatedAt     : value(consumer("2026-09-01T10:10:00"), producer(regex('.+')))
                ]
        ])
    }
}
