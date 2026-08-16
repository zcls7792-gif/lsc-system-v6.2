package com.lianshengtong.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.user.dto.MerchantApplyDTO;
import com.lianshengtong.user.entity.MerchantExtension;
import com.lianshengtong.user.entity.StoreAddress;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 商家服务接口
 * <p>
 * 覆盖商家注册（企业资质审核、信用分初始化100、日核销额度80）、商家审核、
 * 信用分更新、核销档位更新、地址管理（每日3次修改限制）。
 * </p>
 *
 * @author lsc
 */
public interface MerchantService {

    /**
     * 商家注册：提交企业资质待审核，初始化信用分100、日核销额度80
     *
     * @param dto 商家入驻申请参数
     * @return 商家扩展信息
     */
    MerchantExtension register(MerchantApplyDTO dto);

    /**
     * 商家审核
     *
     * @param merchantId 商家ID
     * @param auditStatus 审核状态 1通过 2拒绝
     * @param remark 审核备注（拒绝原因等）
     * @return 商家扩展信息
     */
    MerchantExtension audit(Long merchantId, Integer auditStatus, String remark);

    /**
     * 信用分更新（联动处罚状态）
     *
     * @param merchantId 商家ID
     * @param creditScore 信用分 0-100
     * @return 商家扩展信息
     */
    MerchantExtension updateCredit(Long merchantId, Integer creditScore);

    /**
     * 核销档位更新
     *
     * @param merchantId 商家ID
     * @param level 核销档位 0-16
     * @return 商家扩展信息
     */
    MerchantExtension updateWriteOffLevel(Long merchantId, Integer level);

    /**
     * 地址管理（每日3次修改限制）
     *
     * @param dto 地址信息
     * @return 商家扩展信息
     */
    MerchantExtension updateAddress(MerchantApplyDTO dto);

    /**
     * 商家信息查询
     *
     * @param merchantId 商家ID
     * @return 商家扩展信息
     */
    MerchantExtension getMerchantInfo(Long merchantId);

    /**
     * 商家分页列表(管理后台)
     *
     * @param page      页码
     * @param size      每页条数
     * @param keyword   店铺名/营业执照号关键词(可空)
     * @param status    审核状态(可空)
     * @param creditMin 信用分下限(可空)
     * @param creditMax 信用分上限(可空)
     * @return 分页结果
     */
    IPage<MerchantExtension> listMerchants(Integer page, Integer size, String keyword,
                                           Integer status, Integer creditMin, Integer creditMax);

    /**
     * 待审核商家列表
     *
     * @param page  页码
     * @param size  每页条数
     * @param status 审核状态(可空，默认0=待审核)
     * @return 分页结果
     */
    IPage<MerchantExtension> auditList(Integer page, Integer size, Integer status);

    /**
     * 商家信用分明细
     *
     * @param merchantId 商家ID
     * @return 信用分信息
     */
    Map<String, Object> getCreditDetail(Long merchantId);

    /**
     * 商家处罚(扣信用分/设置处罚状态)
     *
     * @param merchantId  商家ID
     * @param penaltyType 处罚类型 0正常 1一级 2二级 3三级 4清退
     * @param reason      处罚原因
     * @param days        处罚天数(可空)
     * @return 商家扩展信息
     */
    MerchantExtension penalize(Long merchantId, Integer penaltyType, String reason, Integer days);

    /**
     * 信用分调整(增量)
     *
     * @param merchantId 商家ID
     * @param delta      增减量(正加负减)
     * @param reason     调整原因
     * @return 商家扩展信息
     */
    MerchantExtension adjustCredit(Long merchantId, Integer delta, String reason);

    /**
     * 更新店铺基本信息(店铺名/电话/营业时间/营业执照图片)
     *
     * @param merchantId 商家ID
     * @param params     更新字段
     * @return 商家扩展信息
     */
    MerchantExtension updateStoreInfo(Long merchantId, Map<String, Object> params);

    /**
     * 线下门店地址列表
     *
     * @param merchantId 商家ID
     * @return 地址列表
     */
    List<StoreAddress> listStoreAddresses(Long merchantId);

    /**
     * 新增/编辑线下门店地址(同受每日3次修改限制)
     *
     * @param address 地址信息(id 为空则新增)
     * @return 地址信息
     */
    StoreAddress saveStoreAddress(StoreAddress address);

    /**
     * 删除线下门店地址
     *
     * @param id         地址ID
     * @param merchantId 商家ID(权限校验)
     */
    void deleteStoreAddress(Long id, Long merchantId);

    /**
     * 设置主地址(取消原主地址)
     *
     * @param id         地址ID
     * @param merchantId 商家ID
     * @return 地址信息
     */
    StoreAddress setPrimaryAddress(Long id, Long merchantId);

    /**
     * 当日地址修改次数状态
     *
     * @param merchantId 商家ID
     * @return todayUpdatedCount/dailyLimit/remaining
     */
    Map<String, Object> getAddressUpdateState(Long merchantId);

    /**
     * 更新商家最近核销日期(由 writeoff-service 通过 Feign 调用)
     * <p>核销成功后调用，用于"每日限 1 次核销"的次数校验。</p>
     *
     * @param merchantId 商家ID
     * @param nhDate     核销日期
     */
    void updateLastNhDate(Long merchantId, LocalDate nhDate);
}
