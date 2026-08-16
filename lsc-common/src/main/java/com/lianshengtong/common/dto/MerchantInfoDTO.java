package com.lianshengtong.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家信息DTO
 * 供Feign跨服务调用
 */
public class MerchantInfoDTO implements Serializable {

    private Long merchantId;
    private String businessLicense;
    private String storeName;
    private Integer creditScore;
    private Integer aiRiskScore;
    private BigDecimal monthlyRevenue;
    private Integer nhLimitLevel;
    private Integer dailyNhLimit;
    private String regulatoryAccountNo;
    private String mainAccountNo;
    private java.util.Date lastNhDate;
    private Integer penaltyStatus;
    private Boolean isSignedSupervision;
    private Integer auditStatus;
    private String province;
    private String city;
    private String district;
    private String addressDetail;
    private String contactPhone;
    private String businessHours;

    public MerchantInfoDTO() {}

    public MerchantInfoDTO(Long merchantId, String businessLicense, String storeName,
            Integer creditScore, Integer aiRiskScore, BigDecimal monthlyRevenue,
            Integer nhLimitLevel, Integer dailyNhLimit, String regulatoryAccountNo,
            String mainAccountNo, java.util.Date lastNhDate, Integer penaltyStatus,
            Boolean isSignedSupervision, Integer auditStatus, String province,
            String city, String district, String addressDetail, String contactPhone,
            String businessHours) {
        this.merchantId = merchantId;
        this.businessLicense = businessLicense;
        this.storeName = storeName;
        this.creditScore = creditScore;
        this.aiRiskScore = aiRiskScore;
        this.monthlyRevenue = monthlyRevenue;
        this.nhLimitLevel = nhLimitLevel;
        this.dailyNhLimit = dailyNhLimit;
        this.regulatoryAccountNo = regulatoryAccountNo;
        this.mainAccountNo = mainAccountNo;
        this.lastNhDate = lastNhDate;
        this.penaltyStatus = penaltyStatus;
        this.isSignedSupervision = isSignedSupervision;
        this.auditStatus = auditStatus;
        this.province = province;
        this.city = city;
        this.district = district;
        this.addressDetail = addressDetail;
        this.contactPhone = contactPhone;
        this.businessHours = businessHours;
    }

    
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private MerchantInfoDTO obj = new MerchantInfoDTO();
        public Builder merchantId(Long v) { obj.merchantId = v; return this; }
        public Builder businessLicense(String v) { obj.businessLicense = v; return this; }
        public Builder storeName(String v) { obj.storeName = v; return this; }
        public Builder creditScore(Integer v) { obj.creditScore = v; return this; }
        public Builder aiRiskScore(Integer v) { obj.aiRiskScore = v; return this; }
        public Builder monthlyRevenue(BigDecimal v) { obj.monthlyRevenue = v; return this; }
        public Builder nhLimitLevel(Integer v) { obj.nhLimitLevel = v; return this; }
        public Builder dailyNhLimit(Integer v) { obj.dailyNhLimit = v; return this; }
        public Builder regulatoryAccountNo(String v) { obj.regulatoryAccountNo = v; return this; }
        public Builder mainAccountNo(String v) { obj.mainAccountNo = v; return this; }
        public Builder penaltyStatus(Integer v) { obj.penaltyStatus = v; return this; }
        public Builder isSignedSupervision(Boolean v) { obj.isSignedSupervision = v; return this; }
        public Builder auditStatus(Integer v) { obj.auditStatus = v; return this; }
        public Builder province(String v) { obj.province = v; return this; }
        public Builder city(String v) { obj.city = v; return this; }
        public Builder district(String v) { obj.district = v; return this; }
        public Builder addressDetail(String v) { obj.addressDetail = v; return this; }
        public Builder contactPhone(String v) { obj.contactPhone = v; return this; }
        public Builder businessHours(String v) { obj.businessHours = v; return this; }
        public MerchantInfoDTO build() { return obj; }
    }


    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long v) { this.merchantId = v; }
    public String getBusinessLicense() { return businessLicense; }
    public void setBusinessLicense(String v) { this.businessLicense = v; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String v) { this.storeName = v; }
    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer v) { this.creditScore = v; }
    public Integer getAiRiskScore() { return aiRiskScore; }
    public void setAiRiskScore(Integer v) { this.aiRiskScore = v; }
    public BigDecimal getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(BigDecimal v) { this.monthlyRevenue = v; }
    public Integer getNhLimitLevel() { return nhLimitLevel; }
    public void setNhLimitLevel(Integer v) { this.nhLimitLevel = v; }
    public Integer getDailyNhLimit() { return dailyNhLimit; }
    public void setDailyNhLimit(Integer v) { this.dailyNhLimit = v; }
    public String getRegulatoryAccountNo() { return regulatoryAccountNo; }
    public void setRegulatoryAccountNo(String v) { this.regulatoryAccountNo = v; }
    public String getMainAccountNo() { return mainAccountNo; }
    public void setMainAccountNo(String v) { this.mainAccountNo = v; }
    public java.util.Date getLastNhDate() { return lastNhDate; }
    public void setLastNhDate(java.util.Date v) { this.lastNhDate = v; }
    public Integer getPenaltyStatus() { return penaltyStatus; }
    public void setPenaltyStatus(Integer v) { this.penaltyStatus = v; }
    public Boolean getIsSignedSupervision() { return isSignedSupervision; }
    public void setIsSignedSupervision(Boolean v) { this.isSignedSupervision = v; }
    public Integer getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Integer v) { this.auditStatus = v; }
    public String getProvince() { return province; }
    public void setProvince(String v) { this.province = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getDistrict() { return district; }
    public void setDistrict(String v) { this.district = v; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String v) { this.addressDetail = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String v) { this.contactPhone = v; }
    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String v) { this.businessHours = v; }


}
