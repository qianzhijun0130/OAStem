package com.kellen.oa_system.hr.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡单，对应表 oa_card_supplement。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_card_supplement")
public class CardSupplement extends AuditEntity {


    /** 单据编号 */
    private String billCode;

    /** 申请人ID */
    private Long userId;

    /** 补卡日期 */
    private LocalDate cardDate;

    /** 方向：1上班 2下班 */
    private Integer direction;

    /**
     * 补卡类型：1 主动补卡（计入每月 3 次额度）/ 2 漏卡补卡（系统已识别缺卡，不计额度）。
     *
     * <p>区分的是<b>事实来源</b>而不是流程：漏卡是「系统已经知道这个人那天缺了哪一侧」
     * （有排班、只有一次刷卡），员工只是认领；主动补卡则什么记录都没有。
     * 不区分的话，真实漏卡多的人会先被 3 次额度挡在门外，而这恰恰是制度上应允许修正的差错。</p>
     */
    private Integer cardType;

    /** 补卡时间 */
    private LocalDateTime cardTime;

    /** 补卡原因 */
    private String reason;

    /** 关联流程实例ID */
    private Long instanceId;

    /** 状态：0草稿 1审批中 2已通过 3已驳回 4已撤销 */
    private Integer cardStatus;
}
