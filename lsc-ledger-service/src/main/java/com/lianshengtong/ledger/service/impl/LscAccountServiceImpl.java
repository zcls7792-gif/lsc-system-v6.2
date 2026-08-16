package com.lianshengtong.ledger.service.impl;

import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.mapper.LscAccountMapper;
import com.lianshengtong.ledger.service.LscAccountService;
import org.springframework.stereotype.Service;


/**
 * LSC 账户服务实现
 *
 * @author lsc
 */
@Service
public class LscAccountServiceImpl implements LscAccountService {

    private final LscAccountMapper accountMapper;

    public LscAccountServiceImpl(LscAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public LscAccount getAccount(Long userId) {
        return accountMapper.selectById(userId);
    }

    @Override
    public LscAccount initAccount(Long userId) {
        accountMapper.insertIfNotExists(userId);
        return accountMapper.selectById(userId);
    }

    @Override
    public LscAccount getOrCreateAccount(Long userId) {
        LscAccount acc = accountMapper.selectById(userId);
        if (acc == null) {
            accountMapper.insertIfNotExists(userId);
            acc = accountMapper.selectById(userId);
        }
        return acc;
    }


    public LscAccountMapper getAccountMapper() { return accountMapper; }
}
