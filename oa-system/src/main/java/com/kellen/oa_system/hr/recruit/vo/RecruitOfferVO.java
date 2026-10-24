package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer VO。
 */
@Data
public class RecruitOfferVO {

    private Long id;
    private String offerNo;
    private Long candidateId;
    private String candidateName;
    private String candidateMobile;
    private Long postId;
    private String postName;
    private Long positionId;
    private String positionName;
    private BigDecimal salary;
    private Integer probationMonths;
    private LocalDate reportDate;
    private LocalDate expireDate;

    /** 距失效天数（负数=已过期） */
    private Integer remainDays;

    /** 1已过期 */
    private Integer expired;

    private Integer status;
    private String statusLabel;
    private Integer sendWay;
    private String sendWayLabel;
    private LocalDateTime sendTime;
    private LocalDateTime acceptTime;
    private String refuseReason;
    private Long userId;
    private String convertLog;
    private String remark;
    private LocalDateTime createTime;
}
