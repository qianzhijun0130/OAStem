package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Offer 转员工请求。
 *
 * <p><b>手机号已存在时的处理</b>：命中已有档案（如离职员工返聘）时不自动新建，
 * 而是要求明确选择「复用」还是「新建」，并把选择记进留痕——一人两条档案
 * 会让考勤、薪资数据串到别人头上。</p>
 */
@Data
public class RecruitConvertRequest {

    /** Offer */
    @NotNull(message = "Offer 不能为空")
    private Long offerId;

    /** 1复用已有档案 2新建档案 */
    private Integer reuseMode;

    /** 复用时指定的目标员工ID */
    private Long targetUserId;

    /** 新建时的工号（不传按规则生成） */
    private String workNumber;

    /** 备注 */
    private String remark;
}
