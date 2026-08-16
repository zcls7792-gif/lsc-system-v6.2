package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class B2bOrderManualVerifyDTO {

    private Boolean result;

    @Size(max = 500, message = "备注长度不能超过500字")
    private String remark;
}
