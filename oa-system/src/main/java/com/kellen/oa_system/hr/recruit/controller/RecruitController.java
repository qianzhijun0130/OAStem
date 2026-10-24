package com.kellen.oa_system.hr.recruit.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招聘管理接口。
 *
 * <p>权限分三档：</p>
 * <ul>
 *   <li><b>登录即可</b>：面试评价（面试官本人）、offer 接受/拒绝；</li>
 *   <li><b>HR</b>：需求、职位、候选人、offer、看板；</li>
 *   <li><b>阶段回退</b>：需 HR 角色（{@code HrRoles.isHr()}），不靠权限码。</li>
 * </ul>
 */
@RestController
@RequestMapping("/hr/recruit")
@RequiredArgsConstructor
public class RecruitController {

    private final RecruitService recruitService;

    // ================== 需求 ==================

    @GetMapping("/demand/page")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<PageResult<RecruitDemandVO>> pageDemands(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) Long deptId,
                                                           @RequestParam(required = false) Integer pageNo,
                                                           @RequestParam(required = false) Integer pageSize) {
        return Result.success(recruitService.pageDemands(keyword, status, deptId, pageNo, pageSize));
    }

    @GetMapping("/demand/{id}")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<RecruitDemandVO> demandDetail(@PathVariable Long id) {
        return Result.success(recruitService.demandDetail(id));
    }

    @PostMapping("/demand")
    @PreAuthorize("hasAuthority('hr:recruit:demand')")
    @OperationLog(module = "招聘管理", operation = "保存招聘需求 {#request.reason}")
    public Result<Long> saveDemand(@Valid @RequestBody RecruitDemandSaveRequest request) {
        return Result.success(recruitService.saveDemand(request));
    }

    @PostMapping("/demand/{id}/close")
    @PreAuthorize("hasAuthority('hr:recruit:demand')")
    @OperationLog(module = "招聘管理", operation = "关闭招聘需求 {#id}")
    public Result<Void> closeDemand(@PathVariable Long id) {
        recruitService.closeDemand(id);
        return Result.success();
    }

    @PostMapping("/demand/{id}/approve")
    @PreAuthorize("hasAuthority('hr:recruit:demand')")
    @OperationLog(module = "招聘管理", operation = "审批通过招聘需求 {#id}")
    public Result<Void> approveDemand(@PathVariable Long id) {
        recruitService.approveDemand(id);
        return Result.success();
    }

    // ================== 职位 ==================

    @GetMapping("/post/page")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<PageResult<RecruitPostVO>> pagePosts(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false) Long demandId,
                                                       @RequestParam(required = false) Integer pageNo,
                                                       @RequestParam(required = false) Integer pageSize) {
        return Result.success(recruitService.pagePosts(keyword, status, demandId, pageNo, pageSize));
    }

    @PostMapping("/post")
    @PreAuthorize("hasAuthority('hr:recruit:post')")
    @OperationLog(module = "招聘管理", operation = "保存招聘职位 {#request.postName}")
    public Result<Long> savePost(@Valid @RequestBody RecruitPostSaveRequest request) {
        return Result.success(recruitService.savePost(request));
    }

    @DeleteMapping("/post/{id}")
    @PreAuthorize("hasAuthority('hr:recruit:post')")
    @OperationLog(module = "招聘管理", operation = "删除招聘职位 {#id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        recruitService.deletePost(id);
        return Result.success();
    }

    // ================== 候选人 ==================

    @GetMapping("/candidate/page")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<PageResult<RecruitCandidateVO>> pageCandidates(@RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) Integer stage,
                                                                   @RequestParam(required = false) Integer source,
                                                                   @RequestParam(required = false) Long postId,
                                                                   @RequestParam(required = false) Integer pageNo,
                                                                   @RequestParam(required = false) Integer pageSize) {
        return Result.success(recruitService.pageCandidates(keyword, stage, source, postId, pageNo, pageSize));
    }

    @GetMapping("/candidate/{id}")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<RecruitCandidateVO> candidateDetail(@PathVariable Long id) {
        return Result.success(recruitService.candidateDetail(id));
    }

    @PostMapping("/candidate")
    @PreAuthorize("hasAuthority('hr:recruit:candidate')")
    @OperationLog(module = "招聘管理", operation = "保存候选人 {#request.name}")
    public Result<Long> saveCandidate(@Valid @RequestBody RecruitCandidateSaveRequest request) {
        return Result.success(recruitService.saveCandidate(request));
    }

    @PostMapping("/candidate/{id}/stage")
    @PreAuthorize("hasAuthority('hr:recruit:candidate')")
    @OperationLog(module = "招聘管理", operation = "变更候选人阶段 {#id}")
    public Result<Void> changeStage(@PathVariable Long id, @Valid @RequestBody RecruitStageRequest request) {
        recruitService.changeStage(id, request);
        return Result.success();
    }

    @GetMapping("/candidate/duplicate")
    @PreAuthorize("hasAuthority('hr:recruit:offer')")
    public Result<RecruitCandidateVO> findDuplicate(@RequestParam String mobile,
                                                     @RequestParam(required = false) String name) {
        return Result.success(recruitService.findDuplicatedEmployee(mobile, name));
    }

    // ================== 面试 ==================

    @GetMapping("/interview/page")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<PageResult<RecruitInterviewVO>> pageInterviews(@RequestParam(required = false) Long candidateId,
                                                                  @RequestParam(required = false) Integer status,
                                                                  @RequestParam(required = false) Integer pageNo,
                                                                  @RequestParam(required = false) Integer pageSize) {
        return Result.success(recruitService.pageInterviews(candidateId, status, pageNo, pageSize));
    }

    @GetMapping("/interview/{id}")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<RecruitInterviewVO> interviewDetail(@PathVariable Long id) {
        return Result.success(recruitService.interviewDetail(id));
    }

    @PostMapping("/interview")
    @PreAuthorize("hasAuthority('hr:recruit:interview')")
    @OperationLog(module = "招聘管理", operation = "安排面试 {#request.candidateId}")
    public Result<Long> saveInterview(@Valid @RequestBody RecruitInterviewSaveRequest request) {
        return Result.success(recruitService.saveInterview(request));
    }

    @PostMapping("/interview/feedback")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "招聘管理", operation = "提交面试评价 {#request.interviewId}")
    public Result<Void> submitFeedback(@Valid @RequestBody RecruitFeedbackRequest request) {
        recruitService.submitFeedback(request);
        return Result.success();
    }

    @PostMapping("/interview/{id}/summarize")
    @PreAuthorize("hasAuthority('hr:recruit:interview')")
    @OperationLog(module = "招聘管理", operation = "汇总面试结论 {#id}")
    public Result<Void> summarize(@PathVariable Long id,
                                  @RequestParam Integer conclusion,
                                  @RequestParam(required = false) String summary,
                                  @RequestParam(required = false) String continueReason) {
        recruitService.summarizeInterview(id, conclusion, summary, continueReason);
        return Result.success();
    }

    // ================== Offer ==================

    @GetMapping("/offer/page")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<PageResult<RecruitOfferVO>> pageOffers(@RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) Long candidateId,
                                                          @RequestParam(required = false) Integer pageNo,
                                                          @RequestParam(required = false) Integer pageSize) {
        return Result.success(recruitService.pageOffers(status, candidateId, pageNo, pageSize));
    }

    @GetMapping("/offer/{id}")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<RecruitOfferVO> offerDetail(@PathVariable Long id) {
        return Result.success(recruitService.offerDetail(id));
    }

    @PostMapping("/offer")
    @PreAuthorize("hasAuthority('hr:recruit:offer')")
    @OperationLog(module = "招聘管理", operation = "发起 Offer {#request.candidateId}")
    public Result<Long> saveOffer(@Valid @RequestBody RecruitOfferSaveRequest request) {
        return Result.success(recruitService.saveOffer(request));
    }

    @PostMapping("/offer/{id}/send")
    @PreAuthorize("hasAuthority('hr:recruit:offer')")
    @OperationLog(module = "招聘管理", operation = "发出 Offer {#id}")
    public Result<Void> sendOffer(@PathVariable Long id,
                                  @RequestParam(required = false) Integer sendWay) {
        recruitService.sendOffer(id, sendWay);
        return Result.success();
    }

    @PostMapping("/offer/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "招聘管理", operation = "接受 Offer {#id}")
    public Result<Void> acceptOffer(@PathVariable Long id) {
        recruitService.acceptOffer(id);
        return Result.success();
    }

    @PostMapping("/offer/{id}/refuse")
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "招聘管理", operation = "拒绝 Offer {#id}")
    public Result<Void> refuseOffer(@PathVariable Long id, @RequestParam(required = false) String reason) {
        recruitService.refuseOffer(id, reason);
        return Result.success();
    }

    @PostMapping("/offer/convert")
    @PreAuthorize("hasAuthority('hr:recruit:offer')")
    @OperationLog(module = "招聘管理", operation = "Offer 转员工 {#request.offerId}")
    public Result<Long> convert(@Valid @RequestBody RecruitConvertRequest request) {
        return Result.success(recruitService.convertToEmployee(request));
    }

    // ================== 看板 ==================

    @GetMapping("/board")
    @PreAuthorize("hasAuthority('hr:recruit')")
    public Result<RecruitBoardVO> board(@RequestParam(required = false) Long deptId) {
        return Result.success(recruitService.board(deptId));
    }
}
