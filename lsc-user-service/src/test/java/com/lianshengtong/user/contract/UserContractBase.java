package com.lianshengtong.user.contract;

import com.lianshengtong.user.controller.UserController;
import com.lianshengtong.user.entity.User;
import com.lianshengtong.user.service.UserService;
import com.lianshengtong.user.util.JwtUtil;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;

/**
 * Spring Cloud Contract 契约测试基底类 — lsc-user-service (Provider for promotion-service)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class UserContractBase {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    public void setup() {
        // --- getUserInfo 契约: userId=10001, referrerId=99001, isVerified=1 ---
        User mockUser = new User();
        mockUser.setUserId(10001L);
        mockUser.setUserType(0);
        mockUser.setMobile("13800000001");
        mockUser.setNickname("测试用户");
        mockUser.setAvatarUrl(null);
        mockUser.setIsVerified(1);
        mockUser.setRealName(null);
        mockUser.setIdCardNo(null);
        mockUser.setReferrerId(99001L);
        mockUser.setStatus(1);
        mockUser.setReferralCode("10001");
        mockUser.setCreatedAt(LocalDateTime.of(2026, 8, 1, 9, 0, 0));
        mockUser.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, 0, 0));

        when(userService.getUserInfo(10001L)).thenReturn(mockUser);

        RestAssuredMockMvc.standaloneSetup(userController);
    }
}
