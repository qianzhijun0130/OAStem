package com.kellen.oa_system.hr.employee.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 劳动合同视图。
 */
@Data
public class ContractVO {

    private Long id;

    private String contractNo;

    private Integer contractType;

    private String contractTypeLabel;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer signCount;

    private LocalDate signDate;

    private String fileIds;

    private Integer status;

    /** 距到期天数（负数表示已过期），无结束日期时为 null */
    private Long daysToExpire;
}
