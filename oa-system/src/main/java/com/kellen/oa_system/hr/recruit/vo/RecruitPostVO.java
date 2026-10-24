package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位 VO。
 */
@Data
public class RecruitPostVO {

    private Long id;
    private Long demandId;
    private String demandCode;
    private String postName;
    private String city;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Integer headCount;
    private Integer channel;
    private String channelLabel;
    private Integer status;
    private String statusLabel;
    private Long hrId;
    private String hrName;
    private LocalDateTime publishTime;

    /** 已投递候选人数 */
    private Integer candidateCount;

    /** 已入职人数 */
    private Integer hiredCount;

    private String remark;
    private LocalDateTime createTime;
}
