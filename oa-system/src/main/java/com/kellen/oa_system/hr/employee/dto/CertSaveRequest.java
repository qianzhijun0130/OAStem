package com.kellen.oa_system.hr.employee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工证照新增 / 修改入参。
 */
@Data
public class CertSaveRequest {

    private Long id;

    /** 类型：1学历 2学位 3职业资格 4职称 5特种作业 6培训证书 */
    @NotNull(message = "证照类型不能为空")
    @Min(value = 1, message = "证照类型取值 1~6")
    @Max(value = 6, message = "证照类型取值 1~6")
    private Integer certType;

    @NotBlank(message = "证照名称不能为空")
    @Size(max = 100, message = "证照名称长度不能超过 100")
    private String certName;

    @Size(max = 64, message = "证书编号长度不能超过 64")
    private String certNo;

    @Size(max = 100, message = "发证机构长度不能超过 100")
    private String issueOrg;

    private LocalDate issueDate;

    /** 有效期至，空表示长期有效 */
    private LocalDate expireDate;

    @Size(max = 255, message = "附件长度不能超过 255")
    private String fileIds;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
