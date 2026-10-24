package com.kellen.oa_system.hr.recruit.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.recruit.dto.RecruitCandidateSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitConvertRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitDemandSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitFeedbackRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitInterviewSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitOfferSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitPostSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitStageRequest;
import com.kellen.oa_system.hr.recruit.vo.RecruitBoardVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitCandidateVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitDemandVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitFeedbackVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitInterviewVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitOfferVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitPostVO;

import java.util.List;

/**
 * 招聘管理。
 *
 * <p>链路：需求 → 职位 → 候选人 → 面试 → offer → 转员工。
 * 每个环节的「不许跳过」都写在实现里（缺授权不能存简历、评价没交齐不能汇总、
 * offer 过期不能转员工），因为这些是数据可信度的底线。</p>
 */
public interface RecruitService {

    // ---------- 需求 ----------

    PageResult<RecruitDemandVO> pageDemands(String keyword, Integer status, Long deptId,
                                             Integer pageNo, Integer pageSize);

    RecruitDemandVO demandDetail(Long id);

    /** 保存需求；人数超编制缺口只提示不拦截 */
    Long saveDemand(RecruitDemandSaveRequest request);

    /** 关闭需求 */
    void closeDemand(Long id);

    /** 审批通过：状态转「招聘中」并记录通过时间（招聘周期起点） */
    void approveDemand(Long id);

    // ---------- 职位 ----------

    PageResult<RecruitPostVO> pagePosts(String keyword, Integer status, Long demandId,
                                         Integer pageNo, Integer pageSize);

    Long savePost(RecruitPostSaveRequest request);

    void deletePost(Long id);

    // ---------- 候选人 ----------

    PageResult<RecruitCandidateVO> pageCandidates(String keyword, Integer stage, Integer source,
                                                  Long postId, Integer pageNo, Integer pageSize);

    RecruitCandidateVO candidateDetail(Long id);

    /**
     * 保存候选人。
     *
     * <p>手机号已存在时<b>合并到同一人</b>（返回原记录 id），
     * 不产生两条档案。</p>
     */
    Long saveCandidate(RecruitCandidateSaveRequest request);

    /** 推进阶段（回退需 HR 权限 + 理由；已入职不可变更） */
    void changeStage(Long id, RecruitStageRequest request);

    /** 查重：按手机号 + 姓名找已有员工档案，供转员工时判断复用还是新建 */
    RecruitCandidateVO findDuplicatedEmployee(String mobile, String name);

    // ---------- 面试 ----------

    PageResult<RecruitInterviewVO> pageInterviews(Long candidateId, Integer status,
                                                   Integer pageNo, Integer pageSize);

    RecruitInterviewVO interviewDetail(Long id);

    Long saveInterview(RecruitInterviewSaveRequest request);

    /** 提交面试评价；每人只能提交一次（覆盖自己的那条） */
    void submitFeedback(RecruitFeedbackRequest request);

    /** 汇总面试结论：需所有面试官提交后才可汇总 */
    void summarizeInterview(Long id, Integer conclusion, String summary, String continueReason);

    // ---------- Offer ----------

    PageResult<RecruitOfferVO> pageOffers(Integer status, Long candidateId,
                                           Integer pageNo, Integer pageSize);

    RecruitOfferVO offerDetail(Long id);

    Long saveOffer(RecruitOfferSaveRequest request);

    /** 发出 offer（记录发送方式与时间） */
    void sendOffer(Long id, Integer sendWay);

    /** 候选人接受 */
    void acceptOffer(Long id);

    /** 候选人拒绝 */
    void refuseOffer(Long id, String reason);

    /** 转员工：手机号已存在时必须显式选择复用/新建，选择留痕 */
    Long convertToEmployee(RecruitConvertRequest request);

    // ---------- 看板 ----------

    RecruitBoardVO board(Long deptId);
}
