package com.lianshengtong.ledger.service.impl;

import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.mapper.LscAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LscAccountServiceImpl 单元测试")
class LscAccountServiceImplTest {

    @Mock
    private LscAccountMapper accountMapper;

    @InjectMocks
    private LscAccountServiceImpl accountService;

    private LscAccount buildAccount(Long userId, Long locked, Long available) {
        return LscAccount.builder()
                .userId(userId)
                .totalLocked(locked)
                .totalAvailable(available)
                .version(0)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getAccount: 账户存在返回账户")
    void getAccount_accountExists_returnsAccount() {
        LscAccount account = buildAccount(1L, 100L, 50L);
        when(accountMapper.selectById(1L)).thenReturn(account);

        LscAccount result = accountService.getAccount(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(100L, result.getTotalLocked());
        assertEquals(50L, result.getTotalAvailable());
    }

    @Test
    @DisplayName("getAccount: 账户不存在返回null")
    void getAccount_accountNotFound_returnsNull() {
        when(accountMapper.selectById(999L)).thenReturn(null);

        LscAccount result = accountService.getAccount(999L);

        assertNull(result);
    }

    @Test
    @DisplayName("getAccount: 验证mapper调用次数")
    void getAccount_verifyMapperCall() {
        when(accountMapper.selectById(1L)).thenReturn(buildAccount(1L, 0L, 0L));

        accountService.getAccount(1L);
        accountService.getAccount(1L);

        verify(accountMapper, times(2)).selectById(1L);
    }

    @Test
    @DisplayName("initAccount: 初始化成功返回账户")
    void initAccount_success_returnsAccount() {
        LscAccount account = buildAccount(1L, 0L, 0L);
        when(accountMapper.insertIfNotExists(1L)).thenReturn(1);
        when(accountMapper.selectById(1L)).thenReturn(account);

        LscAccount result = accountService.initAccount(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
    }

    @Test
    @DisplayName("initAccount: 验证insertIfNotExists被调用")
    void initAccount_verifyInsertCalled() {
        when(accountMapper.insertIfNotExists(1L)).thenReturn(1);
        when(accountMapper.selectById(1L)).thenReturn(buildAccount(1L, 0L, 0L));

        accountService.initAccount(1L);

        verify(accountMapper).insertIfNotExists(1L);
    }

    @Test
    @DisplayName("initAccount: 验证insert后执行select")
    void initAccount_verifyInsertThenSelect() {
        when(accountMapper.insertIfNotExists(1L)).thenReturn(1);
        when(accountMapper.selectById(1L)).thenReturn(buildAccount(1L, 0L, 0L));

        accountService.initAccount(1L);

        verify(accountMapper).insertIfNotExists(1L);
        verify(accountMapper).selectById(1L);
    }

    @Test
    @DisplayName("getOrCreateAccount: 账户已存在直接返回")
    void getOrCreateAccount_accountExists_returnsExisting() {
        LscAccount account = buildAccount(1L, 200L, 100L);
        when(accountMapper.selectById(1L)).thenReturn(account);

        LscAccount result = accountService.getOrCreateAccount(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(200L, result.getTotalLocked());
        assertEquals(100L, result.getTotalAvailable());
    }

    @Test
    @DisplayName("getOrCreateAccount: 账户不存在则创建后返回")
    void getOrCreateAccount_accountNull_createsAndReturns() {
        LscAccount account = buildAccount(1L, 0L, 0L);
        when(accountMapper.selectById(1L))
                .thenReturn(null)
                .thenReturn(account);
        when(accountMapper.insertIfNotExists(1L)).thenReturn(1);

        LscAccount result = accountService.getOrCreateAccount(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        verify(accountMapper).insertIfNotExists(1L);
    }

    @Test
    @DisplayName("getOrCreateAccount: 账户存在时不调用insert")
    void getOrCreateAccount_existingAccount_noInsertCalled() {
        when(accountMapper.selectById(1L)).thenReturn(buildAccount(1L, 0L, 0L));

        accountService.getOrCreateAccount(1L);

        verify(accountMapper, never()).insertIfNotExists(anyLong());
    }

    @Test
    @DisplayName("getOrCreateAccount: 账户不存在时调用insert再select")
    void getOrCreateAccount_nullAccount_insertThenSelect() {
        when(accountMapper.selectById(1L))
                .thenReturn(null)
                .thenReturn(buildAccount(1L, 0L, 0L));

        accountService.getOrCreateAccount(1L);

        verify(accountMapper).insertIfNotExists(1L);
        verify(accountMapper, times(2)).selectById(1L);
    }

    @Test
    @DisplayName("getAccountMapper: 返回mapper实例")
    void getAccountMapper_returnsMapper() {
        LscAccountMapper mapper = accountService.getAccountMapper();

        assertNotNull(mapper);
        assertSame(accountMapper, mapper);
    }

    @Test
    @DisplayName("getAccount: 不同用户ID隔离查询")
    void getAccount_differentUserId_isolated() {
        when(accountMapper.selectById(1L)).thenReturn(buildAccount(1L, 100L, 50L));
        when(accountMapper.selectById(2L)).thenReturn(buildAccount(2L, 200L, 150L));

        LscAccount a1 = accountService.getAccount(1L);
        LscAccount a2 = accountService.getAccount(2L);

        assertNotSame(a1, a2);
        assertEquals(1L, a1.getUserId());
        assertEquals(2L, a2.getUserId());
    }

    @Test
    @DisplayName("initAccount: 重复初始化幂等")
    void initAccount_idempotent() {
        LscAccount account = buildAccount(1L, 0L, 0L);
        when(accountMapper.insertIfNotExists(1L)).thenReturn(0);
        when(accountMapper.selectById(1L)).thenReturn(account);

        LscAccount result1 = accountService.initAccount(1L);
        LscAccount result2 = accountService.initAccount(1L);

        assertNotNull(result1);
        assertNotNull(result2);
        verify(accountMapper, times(2)).insertIfNotExists(1L);
    }

    @Test
    @DisplayName("getOrCreateAccount: 锁定和可用余额正确传递")
    void getOrCreateAccount_balancesCorrectlyReturned() {
        LscAccount account = buildAccount(1L, 500L, 300L);
        when(accountMapper.selectById(1L)).thenReturn(account);

        LscAccount result = accountService.getOrCreateAccount(1L);

        assertEquals(500L, result.getTotalLocked());
        assertEquals(300L, result.getTotalAvailable());
    }
}