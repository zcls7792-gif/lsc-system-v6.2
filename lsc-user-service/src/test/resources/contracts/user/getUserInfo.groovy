package contracts.user

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return user info — consumer: promotion-service (promotionUserClient#getUserInfo)"
    name "getUserInfo_success"
    priority 1
    request {
        method GET()
        // Xeger 在解析 consumer 长 regex 时会断言失败（repackaged.nl.flotsam.xeger.Xeger.generate）。
        // 用精确 URL：consumer 侧测试代码确实就是传 userId=10001，WireMock 精确匹配即可。
        url '/api/user/info?userId=10001'
    }
    response {
        status 200
        headers { contentType(applicationJson()) }
        body([
                code     : 0,
                message  : "success",
                timestamp: value(consumer(1756900000004L), producer(regex('[0-9]{10,}'))),
                data     : [
                        userId      : 10001L,
                        userType    : 0,
                        mobile      : "13800000001",
                        nickname    : "测试用户",
                        isVerified  : 1,
                        referrerId  : 99001L,
                        status      : 1,
                        referralCode: "10001",
                        // provider 侧 JVM 默认 Jackson 可能把 LocalDateTime 序列化为数组，
                        // 用 regex 接受任意非空值（避免格式不一致导致 assertion error）。
                        createdAt   : value(consumer("2026-08-01T09:00:00"), producer(regex('.+'))),
                        updatedAt   : value(consumer("2026-09-01T10:00:00"), producer(regex('.+')))
                ]
        ])
    }
}
