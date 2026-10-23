package com.kellen.oa_system.hr.employee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 劳动合同新增 / 续签入参。
 */
@Data
public class ContractSaveRequest {

    /** 合同ID；为空表示新增（续签也走新增，签订次数自动 +1） */
    private Long id;

    @NotBlank(message = "合同编号不能为空")
    @Size(max = 64, message = "合同编号长度不能超过 64")
    private String contractNo;

    /** 类型：1固定期限 2无固定期限 3实习 4劳务 5返聘 */
    @Min(value = 1, message = "合同类型取值 1~5")
    @Max(value = 5, message = "合同类型取值 1~5")
    private Integer contractType;

    @NotNull(message = "合同开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期：无固定期限可留空 */
    private LocalDate endDate;

    private LocalDate signDate;

    @Size(max = 255, message = "附件长度不能超过 255")
    private String fileIds;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 是否为续签：为 true 时自动继承上一份合同的签订次数 +1 */
    private Boolean renew;
}
