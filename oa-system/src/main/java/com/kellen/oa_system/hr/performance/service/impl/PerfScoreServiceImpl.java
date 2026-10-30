package com.kellen.oa_system.hr.performance.service.impl;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.hr.performance.dto.PerfAppealHandleRequest;
import com.kellen.oa_system.hr.performance.dto.PerfAppealRequest;
import com.kellen.oa_system.hr.performance.dto.PerfCalibrateRequest;
import com.kellen.oa_system.hr.performance.dto.PerfScoreRequest;
import com.kellen.oa_system.hr.performance.dto.PerfSheetPageQuery;
import com.kellen.oa_system.hr.performance.dto.PerfTargetSaveRequest;
import com.kellen.oa_system.hr.performance.service.PerfScoreService;
import com.kellen.oa_system.hr.performance.vo.PerfAppealVO;
import com.kellen.oa_system.hr.performance.vo.PerfDistributionVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetDetailVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetVO;
import com.kellen.oa_system.hr.performance.vo.PerfImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * 绩效评分链路门面（BE-OPT-08 拆分后）。
 *
 * <p>本类只做两件事：<b>持有事务与数据权限注解</b>、<b>按动作把调用转给四个协作类</b>。
 * 对外签名、返回值、异常码、事务注解位置（8 处 readOnly + 8 处 rollbackFor）、
 * {@code @DataScope} 位置全部与拆分前一致。</p>
 *
 * <p><b>为什么 {@code @DataScope} 必须留在门面</b>：切面在门面方法执行前后往 ThreadLocal
 * 写数据范围快照，协作类里的 {@code visibleUserIds()} 读的是该快照。注解下沉到协作类虽然也能跑，
 * 但会让人误以为「只有那个协作类做数据过滤」，而实际入口在门面。</p>
 */
@Service
@RequiredArgsConstructor
public class PerfScoreServiceImpl implements PerfScoreService {

    private final PerfTargetService targetService;
    private final PerfScoreWriteService writeService;
    private final PerfAppealService appealService;
    private final PerfQueryService queryService;
    private final PerfImportService importService;

    // ==================================================================
    // 我的
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public PerfSheetDetailVO mySheet(Long cycleId) {
        return queryService.mySheet(cycleId);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<PerfSheetVO> myHistory(Integer pageNo, Integer pageSize) {
        return queryService.myHistory(pageNo, pageSize);
    }

    // ==================================================================
    // 目标 / 评分 / 校准 / 确认
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTarget(PerfTargetSaveRequest request) {
        targetService.saveTarget(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTarget(Long sheetId, Boolean pass, String opinion) {
        targetService.approveTarget(sheetId, pass, opinion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSelf(PerfScoreRequest request) {
        writeService.submitSelf(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitManager(PerfScoreRequest request) {
        writeService.submitManager(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calibrate(PerfCalibrateRequest request) {
        writeService.calibrate(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long sheetId) {
        writeService.confirm(sheetId);
    }

    // ==================================================================
    // 申诉
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appeal(PerfAppealRequest request) {
        appealService.appeal(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAppeal(PerfAppealHandleRequest request) {
        appealService.handleAppeal(request);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PerfAppealVO> pendingAppeals(Long cycleId) {
        return appealService.pendingAppeals(cycleId);
    }

    // ==================================================================
    // 结果视图
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public PageResult<PerfSheetVO> pageSheets(PerfSheetPageQuery query) {
        return queryService.pageSheets(query);
    }

    @Transactional(readOnly = true)
    @Override
    @DataScope
    public PageResult<PerfSheetVO> pageTeam(PerfSheetPageQuery query) {
        return queryService.pageTeam(query);
    }

    @Transactional(readOnly = true)
    @Override
    public PerfSheetDetailVO detail(Long sheetId) {
        return queryService.detail(sheetId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PerfDistributionVO> distribution(Long cycleId) {
        return queryService.distribution(cycleId);
    }

    @Transactional(readOnly = true)
    @Override
    public void export(Long cycleId, HttpServletResponse response) {
        queryService.export(cycleId, response);
    }

    // ==================================================================
    // 系数（薪酬侧调用）
    // ==================================================================

    @Override
    public BigDecimal coefficientOf(Long userId, String month) {
        return queryService.coefficientOf(userId, month);
    }

    @Override
    public java.util.Map<Long, BigDecimal> coefficientMap(java.util.Collection<Long> userIds, String month) {
        return queryService.coefficientMap(userIds, month);
    }

    // ==================================================================
    // 结果导入（BIZ-OPT-10）
    // ==================================================================

    @Transactional(readOnly = true)
    @Override
    public void downloadImportTemplate(Long cycleId, HttpServletResponse response) {
        importService.downloadTemplate(cycleId, response);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PerfImportResultVO importResults(Long cycleId, MultipartFile file) {
        return importService.importResults(cycleId, file);
    }
}
