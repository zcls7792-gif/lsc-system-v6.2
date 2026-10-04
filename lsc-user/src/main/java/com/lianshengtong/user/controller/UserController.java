package com.lianshengtong.user.controller;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.result.R;
import com.lianshengtong.user.entity.BusinessProfile;
import com.lianshengtong.user.entity.Referral;
import com.lianshengtong.user.entity.User;
import com.lianshengtong.user.mapper.BusinessProfileMapper;
import com.lianshengtong.user.mapper.ReferralMapper;
import com.lianshengtong.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户接口（V7.7.2 第三章 3.1）
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final BusinessProfileMapper profileMapper;
    private final ReferralMapper referralMapper;

    /** 当前用户信息 */
    @GetMapping("/me")
    public R<Map<String, Object>> me(@RequestAttribute("userId") Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return R.fail(404, "用户不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("nickname", user.getNickname());
        data.put("userType", user.getUserType());
        data.put("accountStatus", user.getAccountStatus());
        data.put("referrerUserId", user.getReferrerUserId());
        return R.ok(data);
    }

    /** 绑定推荐人（注册时） */
    @PostMapping("/{userId}/referral")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> bindReferral(@PathVariable Long userId,
                                                @RequestParam Long referrerUserId) {
        if (userId.equals(referrerUserId)) return R.fail(400, "不能推荐自己");
        User user = userMapper.selectById(userId);
        if (user == null) return R.fail(404, "用户不存在");
        if (user.getReferrerUserId() != null) return R.fail(400, "已绑定推荐人，不可修改");

        // 检查推荐环路
        User referrer = userMapper.selectById(referrerUserId);
        if (referrer == null) return R.fail(404, "推荐人不存在");
        if (userId.equals(referrer.getReferrerUserId())) return R.fail(400, "检测到推荐环路");

        user.setReferrerUserId(referrerUserId);
        userMapper.updateById(user);

        Referral referral = new Referral();
        referral.setReferralId(IdUtil.getSnowflakeNextId());
        referral.setReferredUserId(userId);
        referral.setReferrerUserId(referrerUserId);
        referral.setBoundAt(LocalDateTime.now());
        referral.setTriggerStatus("PENDING");
        referral.setCreatedAt(LocalDateTime.now());
        referralMapper.insert(referral);

        Map<String, Object> data = new HashMap<>();
        data.put("referralId", referral.getReferralId());
        return R.ok(data);
    }

    /** B端资质申请 */
    @PostMapping("/{userId}/business")
    public R<Map<String, Object>> applyBusiness(@PathVariable Long userId,
                                                 @RequestBody BusinessProfile profile) {
        profile.setUserId(userId);
        profile.setBusinessStatus("PENDING");
        profile.setApprovedVersion(0);
        profileMapper.insert(profile);
        Map<String, Object> data = new HashMap<>();
        data.put("status", "PENDING");
        return R.ok(data);
    }

    /** 查询B端资质状态 */
    @GetMapping("/{userId}/business")
    public R<BusinessProfile> getBusiness(@PathVariable Long userId) {
        BusinessProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<BusinessProfile>().eq(BusinessProfile::getUserId, userId));
        return R.ok(profile);
    }

    /** 登录（简化：手机号登录，返回token） */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String mobile = body.get("mobile");
        if (mobile == null || mobile.isEmpty()) {
            return R.fail(400, "手机号不能为空");
        }
        // 简化：查找或创建用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getMobileLookupHash, mobile));
        if (user == null) {
            user = new User();
            user.setUserId(IdUtil.getSnowflakeNextId());
            user.setMobileLookupHash(mobile);
            user.setNickname("用户" + mobile.substring(mobile.length() - 4));
            user.setUserType("C");
            user.setAccountStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }
        // 简化：生成token（实际使用JWT）
        String token = "mock-token-" + user.getUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getUserId());
        data.put("nickname", user.getNickname());
        data.put("userType", user.getUserType());
        return R.ok(data);
    }
}
