package com.fashion.dto;

import lombok.Data;

/**
 * 地址管理参数DTO
 */
@Data
public class AddressBookDTO {
    private Long id;
    private String consignee;
    private String phone;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String detail;
    private Integer isDefault;
}
