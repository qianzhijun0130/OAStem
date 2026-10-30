package com.kellen.oa_system.hr.recruit.service.impl;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.recruit.dto.RecruitCandidateSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitConvertRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitDemandSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitFeedbackRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitInterviewSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitOfferSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitPostSaveRequest;
import com.kellen.oa_system.hr.recruit.dto.RecruitStageRequest;
import com.kellen.oa_system.hr.recruit.service.RecruitService;
import com.kellen.oa_system.hr.recruit.vo.RecruitBoardVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitCandidateVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitDemandVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitInterviewVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitOfferVO;
import com.kellen.oa_system.hr.recruit.vo.RecruitPostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 招聘管理门面。
 *
 * <p>本类只做两件事：<b>事务边界</b>（15 处写事务全部留在这里）与<b>委派</b>。</p>
 *
 * <p>链路：需求 → 职位 → 候选人 → 面试 → offer → 转员工。三条数据可信度底线
 * （缺授权不能存简历、评价没交齐不能汇总、offer 过期不能转员工）分别落在
 * 候选人 / 面试 / offer 三个服务里，不在这一层重复实现。</p>
 *
 * <p>本模块没有 {@code @DataScope}：招聘数据按业务归属共享（HR 之间不隔离），
 * 不按部门收敛。若将来要加，必须加在这一层——参考薪酬与考勤的做法，
 * 注解下沉到协作类不会生效，只会让「谁负责过滤」变得含糊。</p>
 */
@Service
@RequiredArgsConstructor
public class RecruitServiceImpl implements RecruitService {

    private final RecruitDemandService demandService;
    private final RecruitPostService postService;
    private final RecruitCandidateService candidateService;
    private final RecruitInterviewService interviewService;
    private final RecruitOfferService offerService;
    private final RecruitBoardService boardService;

    // ---------- 需求 ----------

    @Override
    public PageResult<RecruitDemandVO> pageDemands(String keyword, Integer status, Long deptId,
                                                    Integer pageNo, Integer pageSize) {
        return demandService.pageDemands(keyword, status, deptId, pageNo, pageSize);
    }

    @Override
    public RecruitDemandVO demandDetail(Long id) {
        return demandService.demandDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDemand(RecruitDemandSaveRequest request) {
        return demandService.saveDemand(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeDemand(Long id) {
        demandService.closeDemand(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveDemand(Long id) {
        demandService.approveDemand(id);
    }

    // ---------- 职位 ----------

    @Override
    public PageResult<RecruitPostVO> pagePosts(String keyword, Integer status, Long demandId,
                                                Integer pageNo, Integer pageSize) {
        return postService.pagePosts(keyword, status, demandId, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePost(RecruitPostSaveRequest request) {
        return postService.savePost(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        postService.deletePost(id);
    }

    // ---------- 候选人 ----------

    @Override
    public PageResult<RecruitCandidateVO> pageCandidates(String keyword, Integer stage, Integer source,
                                                         Long postId, Integer pageNo, Integer pageSize) {
        return candidateService.pageCandidates(keyword, stage, source, postId, pageNo, pageSize);
    }

    @Override
    public RecruitCandidateVO candidateDetail(Long id) {
        return candidateService.candidateDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCandidate(RecruitCandidateSaveRequest request) {
        return candidateService.saveCandidate(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(Long id, RecruitStageRequest request) {
        candidateService.changeStage(id, request);
    }

    @Override
    public RecruitCandidateVO findDuplicatedEmployee(String mobile, String name) {
        return candidateService.findDuplicatedEmployee(mobile, name);
    }

    // ---------- 面试 ----------

    @Override
    public PageResult<RecruitInterviewVO> pageInterviews(Long candidateId, Integer status,
                                                          Integer pageNo, Integer pageSize) {
        return interviewService.pageInterviews(candidateId, status, pageNo, pageSize);
    }

    @Override
    public RecruitInterviewVO interviewDetail(Long id) {
        return interviewService.interviewDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveInterview(RecruitInterviewSaveRequest request) {
        return interviewService.saveInterview(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(RecruitFeedbackRequest request) {
        interviewService.submitFeedback(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void summarizeInterview(Long id, Integer conclusion, String summary, String continueReason) {
        interviewService.summarizeInterview(id, conclusion, summary, continueReason);
    }

    // ---------- Offer ----------

    @Override
    public PageResult<RecruitOfferVO> pageOffers(Integer status, Long candidateId,
                                                  Integer pageNo, Integer pageSize) {
        return offerService.pageOffers(status, candidateId, pageNo, pageSize);
    }

    @Override
    public RecruitOfferVO offerDetail(Long id) {
        return offerService.offerDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOffer(RecruitOfferSaveRequest request) {
        return offerService.saveOffer(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendOffer(Long id, Integer sendWay) {
        offerService.sendOffer(id, sendWay);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOffer(Long id) {
        offerService.acceptOffer(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refuseOffer(Long id, String reason) {
        offerService.refuseOffer(id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToEmployee(RecruitConvertRequest request) {
        return offerService.convertToEmployee(request);
    }

    // ---------- 看板 ----------

    @Override
    public RecruitBoardVO board(Long deptId) {
        return boardService.board(deptId);
    }
}
