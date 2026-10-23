package com.kellen.oa_system.hr.employee.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 员工证照视图。
 */
@Data
public class CertVO {

    private Long id;

    private Integer certType;

    private String certTypeLabel;

    private String certName;

    private String certNo;

    private String issueOrg;

    private LocalDate issueDate;

    private LocalDate expireDate;

    private String fileIds;

    /** 距到期天数；长期有效为 null */
    private Long daysToExpire;
}
