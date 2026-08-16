package com.lianshengtong.ledger.service;

import com.lianshengtong.ledger.entity.LscAccount;

/**
 * LSC 账户服务接口
 * <p>
 * 提供账户查询与初始化能力，供账本核心服务 {@link LscLedgerService} 复用。
 * </p>
 *
 * @author lsc
 */
public interface LscAccountService {

    /**
     * 查询账户(不存在返回 null)
     *
     * @param userId 用户ID
     * @return 账户实体，不存在则 null
     */
    LscAccount getAccount(Long userId);

    /**
     * 初始化账户(若不存在则插入，已存在则忽略)
     *
     * @param userId 用户ID
     * @return 初始化后的账户
     */
    LscAccount initAccount(Long userId);

    /**
     * 查询账户，不存在则自动初始化
     *
     * @param userId 用户ID
     * @return 账户实体(余额可能为0)
     */
    LscAccount getOrCreateAccount(Long userId);
}
