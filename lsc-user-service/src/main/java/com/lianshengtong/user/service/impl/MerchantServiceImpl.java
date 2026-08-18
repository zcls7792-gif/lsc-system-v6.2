package com.lianshengtong.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.enums.MerchantPenaltyStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.user.dto.MerchantApplyDTO;
import com.lianshengtong.user.entity.MerchantExtension;
import com.lianshengtong.user.entity.StoreAddress;
import com.lianshengtong.user.mapper.MerchantExtensionMapper;
import com.lianshengtong.user.mapper.StoreAddressMapper;
import com.lianshengtong.user.service.MerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家服务实现
 *
 * @author lsc
 */
@Slf4j
@Service
public class MerchantServiceImpl implements MerchantService {

    private static final int DAILY_ADDRESS_LIMIT = 3;
    private static final int INIT_CREDIT_SCORE = 100;
    private static final int INIT_DAILY_NH_LIMIT = 80;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final MerchantExtensionMapper merchantMapper;
    private final StoreAddressMapper storeAddressMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public MerchantServiceImpl(MerchantExtensionMapper merchantMapper,
                               StoreAddressMapper storeAddressMapper,
                               StringRedisTemplate stringRedisTemplate) {
        this.merchantMapper = merchantMapper;
        this.storeAddressMapper = storeAddressMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public MerchantExtension register(MerchantApplyDTO dto) {
        MerchantExtension exist = merchantMapper.selectById(dto.getMerchantId());
        if (exist != null && StrUtil.isNotBlank(exist.getBusinessLicense())) {
            throw new BizException(400, "该商家已提交入驻申请");
        }
        MerchantExtension ext = exist != null ? exist : new MerchantExtension();
        ext.setMerchantId(dto.getMerchantId());
        ext.setBusinessLicense(dto.getBusinessLicense());
        ext.setBusinessLicenseImg(dto.getBusinessLicenseImg());
        ext.setStoreName(dto.getStoreName());
        ext.setProvince(dto.getProvince());
        ext.setCity(dto.getCity());
        ext.setDistrict(dto.getDistrict());
        ext.setAddressDetail(dto.getAddressDetail());
        ext.setContactPhone(dto.getContactPhone());
        ext.setBusinessHours(dto.getBusinessHours());
        // 信用分初始化100、日核销额度80
        ext.setCreditScore(INIT_CREDIT_SCORE);
        ext.setAiRiskScore(INIT_CREDIT_SCORE);
        ext.setDailyNhLimit(INIT_DAILY_NH_LIMIT);
        ext.setNhLimitLevel(0);
        ext.setPenaltyStatus(MerchantPenaltyStatusEnum.NORMAL.getCode());
        ext.setAiAddressVerified(0);
        ext.setAuditStatus(0);
        ext.setIsSignedSupervision(0);
        ext.setAddressUpdateCount(0);

        if (exist == null) {
            merchantMapper.insert(ext);
        } else {
            merchantMapper.updateById(ext);
        }
        log.info("[商家注册] merchantId={}, businessLicense={}", dto.getMerchantId(), dto.getBusinessLicense());
        return ext;
    }

    @Override
    public MerchantExtension audit(Long merchantId, Integer auditStatus, String remark) {
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            throw new BizException(400, "审核状态非法(1通过/2拒绝)");
        }
        MerchantExtension ext = loadOrThrow(merchantId);
        ext.setAuditStatus(auditStatus);
        // 审核通过确保信用分与日核销额度已初始化
        if (auditStatus == 1) {
            if (ext.getCreditScore() == null) {
                ext.setCreditScore(INIT_CREDIT_SCORE);
            }
            if (ext.getDailyNhLimit() == null) {
                ext.setDailyNhLimit(INIT_DAILY_NH_LIMIT);
            }
        }
        merchantMapper.updateById(ext);
        log.info("[商家审核] merchantId={}, auditStatus={}, remark={}", merchantId, auditStatus, remark);
        return ext;
    }

    @Override
    public MerchantExtension updateCredit(Long merchantId, Integer creditScore) {
        if (creditScore == null || creditScore < 0 || creditScore > 100) {
            throw new BizException(400, "信用分取值范围0-100");
        }
        MerchantExtension ext = loadOrThrow(merchantId);
        ext.setCreditScore(creditScore);
        // 信用分联动处罚状态
        ext.setPenaltyStatus(MerchantPenaltyStatusEnum.fromCreditScore(creditScore).getCode());
        merchantMapper.updateById(ext);
        log.info("[信用分更新] merchantId={}, creditScore={}, penaltyStatus={}",
                merchantId, creditScore, ext.getPenaltyStatus());
        return ext;
    }

    @Override
    public MerchantExtension updateWriteOffLevel(Long merchantId, Integer level) {
        if (level == null || level < 0 || level > 16) {
            throw new BizException(400, "核销档位取值范围0-16");
        }
        MerchantExtension ext = loadOrThrow(merchantId);
        ext.setNhLimitLevel(level);
        merchantMapper.updateById(ext);
        log.info("[核销档位更新] merchantId={}, level={}", merchantId, level);
        return ext;
    }

    @Override
    public MerchantExtension updateAddress(MerchantApplyDTO dto) {
        MerchantExtension ext = loadOrThrow(dto.getMerchantId());
        // 每日3次修改限制（Redis 计数，按天滚动）
        String today = LocalDate.now().format(DATE_FMT);
        String key = "lsc:merchant:addr:count:" + dto.getMerchantId() + ":" + today;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > DAILY_ADDRESS_LIMIT) {
            throw new BizException(ResultCode.ADDRESS_DAILY_LIMIT);
        }
        ext.setProvince(dto.getProvince());
        ext.setCity(dto.getCity());
        ext.setDistrict(dto.getDistrict());
        ext.setAddressDetail(dto.getAddressDetail());
        ext.setContactPhone(dto.getContactPhone());
        ext.setBusinessHours(dto.getBusinessHours());
        ext.setAiAddressVerified(0);
        ext.setAddressUpdateCount(ext.getAddressUpdateCount() == null ? 1 : ext.getAddressUpdateCount() + 1);
        merchantMapper.updateById(ext);
        log.info("[地址更新] merchantId={}, todayCount={}", dto.getMerchantId(), count);
        return ext;
    }

    @Override
    public MerchantExtension getMerchantInfo(Long merchantId) {
        return loadOrThrow(merchantId);
    }

    @Override
    public IPage<MerchantExtension> listMerchants(Integer page, Integer size, String keyword,
                                                   Integer status, Integer creditMin, Integer creditMax) {
        Page<MerchantExtension> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<MerchantExtension> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(MerchantExtension::getStoreName, keyword)
                    .or().like(MerchantExtension::getBusinessLicense, keyword));
        }
        if (status != null) {
            wrapper.eq(MerchantExtension::getAuditStatus, status);
        }
        if (creditMin != null) {
            wrapper.ge(MerchantExtension::getCreditScore, creditMin);
        }
        if (creditMax != null) {
            wrapper.le(MerchantExtension::getCreditScore, creditMax);
        }
        wrapper.orderByDesc(MerchantExtension::getCreatedAt);
        return merchantMapper.selectPage(p, wrapper);
    }

    @Override
    public IPage<MerchantExtension> auditList(Integer page, Integer size, Integer status) {
        Page<MerchantExtension> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<MerchantExtension> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantExtension::getAuditStatus, status == null ? 0 : status);
        wrapper.orderByDesc(MerchantExtension::getCreatedAt);
        return merchantMapper.selectPage(p, wrapper);
    }

    @Override
    public Map<String, Object> getCreditDetail(Long merchantId) {
        MerchantExtension ext = loadOrThrow(merchantId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("merchantId", ext.getMerchantId());
        detail.put("storeName", ext.getStoreName());
        detail.put("creditScore", ext.getCreditScore() == null ? 0 : ext.getCreditScore());
        detail.put("aiRiskScore", ext.getAiRiskScore());
        detail.put("penaltyStatus", ext.getPenaltyStatus());
        detail.put("penaltyDesc", MerchantPenaltyStatusEnum.fromCreditScore(
                ext.getCreditScore() == null ? 0 : ext.getCreditScore()).getDesc());
        detail.put("dailyNhLimit", ext.getDailyNhLimit());
        detail.put("nhLimitLevel", ext.getNhLimitLevel());
        return detail;
    }

    @Override
    public MerchantExtension penalize(Long merchantId, Integer penaltyType, String reason, Integer days) {
        if (penaltyType == null || penaltyType < 0 || penaltyType > 4) {
            throw new BizException(400, "处罚类型非法(0-4)");
        }
        MerchantExtension ext = loadOrThrow(merchantId);
        ext.setPenaltyStatus(penaltyType);
        // 清退(4) -> 信用分置0；其他按处罚等级递减信用分
        int creditDelta = switch (penaltyType) {
            case 1 -> -10;
            case 2 -> -20;
            case 3 -> -30;
            case 4 -> -ext.getCreditScore();
            default -> 0;
        };
        int newCredit = Math.max(0, (ext.getCreditScore() == null ? 100 : ext.getCreditScore()) + creditDelta);
        ext.setCreditScore(newCredit);
        merchantMapper.updateById(ext);
        log.warn("[商家处罚] merchantId={}, penaltyType={}, creditDelta={}, newCredit={}, reason={}, days={}",
                merchantId, penaltyType, creditDelta, newCredit, reason, days);
        return ext;
    }

    @Override
    public MerchantExtension adjustCredit(Long merchantId, Integer delta, String reason) {
        if (delta == null || delta == 0) {
            throw new BizException(400, "信用分调整量不能为0");
        }
        MerchantExtension ext = loadOrThrow(merchantId);
        int current = ext.getCreditScore() == null ? 100 : ext.getCreditScore();
        int newCredit = Math.max(0, Math.min(100, current + delta));
        ext.setCreditScore(newCredit);
        ext.setPenaltyStatus(MerchantPenaltyStatusEnum.fromCreditScore(newCredit).getCode());
        merchantMapper.updateById(ext);
        log.info("[信用分调整] merchantId={}, delta={}, current={}, newCredit={}, reason={}",
                merchantId, delta, current, newCredit, reason);
        return ext;
    }

    @Override
    public MerchantExtension updateStoreInfo(Long merchantId, Map<String, Object> params) {
        MerchantExtension ext = loadOrThrow(merchantId);
        if (params == null || params.isEmpty()) {
            throw new BizException(400, "更新参数不能为空");
        }
        if (params.containsKey("storeName")) {
            ext.setStoreName(asString(params.get("storeName")));
        }
        if (params.containsKey("contactPhone")) {
            ext.setContactPhone(asString(params.get("contactPhone")));
        }
        if (params.containsKey("businessHours")) {
            ext.setBusinessHours(asString(params.get("businessHours")));
        }
        if (params.containsKey("businessLicense")) {
            ext.setBusinessLicense(asString(params.get("businessLicense")));
        }
        if (params.containsKey("businessLicenseImg")) {
            ext.setBusinessLicenseImg(asString(params.get("businessLicenseImg")));
        }
        merchantMapper.updateById(ext);
        log.info("[店铺信息更新] merchantId={}, fields={}", merchantId, params.keySet());
        return ext;
    }

    @Override
    public List<StoreAddress> listStoreAddresses(Long merchantId) {
        loadOrThrow(merchantId);
        LambdaQueryWrapper<StoreAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StoreAddress::getMerchantId, merchantId)
                .orderByDesc(StoreAddress::getIsPrimary)
                .orderByDesc(StoreAddress::getCreatedAt);
        return storeAddressMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreAddress saveStoreAddress(StoreAddress address) {
        if (address.getMerchantId() == null) {
            throw new BizException(400, "merchantId不能为空");
        }
        loadOrThrow(address.getMerchantId());
        // 每日3次修改限制(新增/编辑均计数)
        consumeAddressQuota(address.getMerchantId());
        if (address.getIsPrimary() == null) {
            address.setIsPrimary(0);
        }
        if (address.getId() == null) {
            // 新增：若为首条地址，自动设为主地址
            long cnt = storeAddressMapper.selectCount(new LambdaQueryWrapper<StoreAddress>()
                    .eq(StoreAddress::getMerchantId, address.getMerchantId()));
            if (cnt == 0) {
                address.setIsPrimary(1);
            }
            storeAddressMapper.insert(address);
            // 设为主地址时取消其他主地址
            if (Integer.valueOf(1).equals(address.getIsPrimary())) {
                clearOtherPrimary(address.getId(), address.getMerchantId());
                syncPrimaryToExtension(address);
            }
            log.info("[地址新增] merchantId={}, id={}", address.getMerchantId(), address.getId());
        } else {
            // 编辑：校验归属
            StoreAddress exist = storeAddressMapper.selectById(address.getId());
            if (exist == null || !address.getMerchantId().equals(exist.getMerchantId())) {
                throw new BizException(ResultCode.FORBIDDEN, "地址不存在或无权操作");
            }
            storeAddressMapper.updateById(address);
            if (Integer.valueOf(1).equals(address.getIsPrimary())) {
                clearOtherPrimary(address.getId(), address.getMerchantId());
                syncPrimaryToExtension(address);
            }
            log.info("[地址编辑] merchantId={}, id={}", address.getMerchantId(), address.getId());
        }
        return address;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStoreAddress(Long id, Long merchantId) {
        StoreAddress exist = storeAddressMapper.selectById(id);
        if (exist == null || !merchantId.equals(exist.getMerchantId())) {
            throw new BizException(ResultCode.FORBIDDEN, "地址不存在或无权操作");
        }
        storeAddressMapper.deleteById(id);
        // 删除的是主地址时，自动将最近一条提升为主地址
        if (Integer.valueOf(1).equals(exist.getIsPrimary())) {
            LambdaQueryWrapper<StoreAddress> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StoreAddress::getMerchantId, merchantId)
                    .orderByDesc(StoreAddress::getCreatedAt)
                    .last("LIMIT 1");
            StoreAddress next = storeAddressMapper.selectOne(wrapper);
            if (next != null) {
                next.setIsPrimary(1);
                storeAddressMapper.updateById(next);
                syncPrimaryToExtension(next);
            }
        }
        log.info("[地址删除] merchantId={}, id={}", merchantId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreAddress setPrimaryAddress(Long id, Long merchantId) {
        StoreAddress exist = storeAddressMapper.selectById(id);
        if (exist == null || !merchantId.equals(exist.getMerchantId())) {
            throw new BizException(ResultCode.FORBIDDEN, "地址不存在或无权操作");
        }
        clearOtherPrimary(id, merchantId);
        exist.setIsPrimary(1);
        storeAddressMapper.updateById(exist);
        syncPrimaryToExtension(exist);
        log.info("[主地址设置] merchantId={}, id={}", merchantId, id);
        return exist;
    }

    @Override
    public Map<String, Object> getAddressUpdateState(Long merchantId) {
        loadOrThrow(merchantId);
        String today = LocalDate.now().format(DATE_FMT);
        String key = "lsc:merchant:addr:count:" + merchantId + ":" + today;
        String val = stringRedisTemplate.opsForValue().get(key);
        long used = val == null ? 0 : Long.parseLong(val);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("todayUpdatedCount", used);
        state.put("dailyLimit", DAILY_ADDRESS_LIMIT);
        state.put("remaining", Math.max(0, DAILY_ADDRESS_LIMIT - used));
        return state;
    }

    /** 消耗当日地址修改配额(超限抛异常) */
    private void consumeAddressQuota(Long merchantId) {
        String today = LocalDate.now().format(DATE_FMT);
        String key = "lsc:merchant:addr:count:" + merchantId + ":" + today;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > DAILY_ADDRESS_LIMIT) {
            throw new BizException(ResultCode.ADDRESS_DAILY_LIMIT);
        }
    }

    /** 取消其他主地址(置为0) */
    private void clearOtherPrimary(Long keepId, Long merchantId) {
        LambdaUpdateWrapper<StoreAddress> update = new LambdaUpdateWrapper<>();
        update.eq(StoreAddress::getMerchantId, merchantId)
                .eq(StoreAddress::getIsPrimary, 1)
                .ne(StoreAddress::getId, keepId)
                .set(StoreAddress::getIsPrimary, 0);
        storeAddressMapper.update(null, update);
    }

    /** 同步主地址到商家扩展表(用于核销/地图定位) */
    private void syncPrimaryToExtension(StoreAddress addr) {
        MerchantExtension ext = merchantMapper.selectById(addr.getMerchantId());
        if (ext == null) {
            return;
        }
        ext.setProvince(addr.getProvince());
        ext.setCity(addr.getCity());
        ext.setDistrict(addr.getDistrict());
        ext.setAddressDetail(addr.getAddressDetail());
        ext.setLongitude(addr.getLongitude());
        ext.setLatitude(addr.getLatitude());
        ext.setAiAddressVerified(0);
        merchantMapper.updateById(ext);
    }

    /** 安全转字符串 */
    private String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private MerchantExtension loadOrThrow(Long merchantId) {
        MerchantExtension ext = merchantMapper.selectById(merchantId);
        if (ext == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商家信息不存在");
        }
        return ext;
    }

    @Override
    public void updateLastNhDate(Long merchantId, LocalDate nhDate) {
        // 仅更新 last_nh_date 字段，避免覆盖其他并发更新
        LambdaUpdateWrapper<MerchantExtension> update = new LambdaUpdateWrapper<>();
        update.eq(MerchantExtension::getMerchantId, merchantId)
                .set(MerchantExtension::getLastNhDate, nhDate);
        int rows = merchantMapper.update(null, update);
        if (rows == 0) {
            log.warn("[updateLastNhDate] 商家不存在或更新失败 merchantId={} nhDate={}", merchantId, nhDate);
        }
    }
}
