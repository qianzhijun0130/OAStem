package com.kellen.oa_system.system.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.PinyinInitialsUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.WorkNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 工号服务实现。
 *
 * <p>并发安全依赖两件事：① 取号在事务内执行；② 读流水号用
 * {@code SELECT ... FOR UPDATE} 锁住部门行，同一部门的新增请求串行化。
 * 跨部门互不阻塞。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkNumberServiceImpl implements WorkNumberService {

    /** 流水号最小位数，不足左侧补零 */
    private static final int SEQ_MIN_WIDTH = 3;

    /** 前缀最大长度，与 oa_department.work_no_prefix 列宽一致 */
    private static final int MAX_PREFIX_LENGTH = 16;

    /** 生成工号时的最大重试次数，用于兜底冲掉历史脏数据造成的占用 */
    private static final int MAX_GENERATE_RETRY = 200;

    /** 前缀冲突时追加序号的上限 */
    private static final int MAX_PREFIX_SUFFIX = 999;

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generate(Long deptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        String prefix = ensurePrefix(dept);

        for (int retry = 0; retry < MAX_GENERATE_RETRY; retry++) {
            int seq = nextSeq(dept.getId());
            String candidate = format(prefix, seq);
            if (userMapper.countByWorkNumberIncludingDeleted(candidate, null) == 0) {
                return candidate;
            }
            // 命中占用说明历史工号与序列表脱节（如手工导入过工号），继续向后取号
            log.warn("工号已被占用，继续取号: deptId={}, workNumber={}", deptId, candidate);
        }
        throw new BizException(ErrorCode.WORK_NUMBER_GENERATE_FAILED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ensurePrefix(Long deptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        return ensurePrefix(dept);
    }

    @Override
    public String derivePrefix(String deptName, Long deptId) {
        String base = PinyinInitialsUtil.toInitials(deptName);
        if (!StringUtils.hasText(base)) {
            // 纯符号/非汉字部门名无法推导拼音，退化为固定前缀，保证流程不中断
            base = deptId == null ? "DEPT" : "D" + deptId;
        }
        base = truncate(base, MAX_PREFIX_LENGTH);

        String candidate = base;
        for (int suffix = 1; suffix <= MAX_PREFIX_SUFFIX; suffix++) {
            if (isPrefixAvailable(candidate, deptId)) {
                return candidate;
            }
            String suffixText = String.valueOf(suffix + 1);
            candidate = truncate(base, MAX_PREFIX_LENGTH - suffixText.length()) + suffixText;
        }
        throw new BizException(ErrorCode.WORK_NUMBER_GENERATE_FAILED);
    }

    @Override
    public void assertPrefixAvailable(String prefix, Long deptId) {
        if (!isPrefixAvailable(prefix, deptId)) {
            throw new BizException(ErrorCode.DEPT_WORK_NO_PREFIX_DUPLICATE);
        }
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private String ensurePrefix(Department dept) {
        String existed = dept.getWorkNoPrefix();
        if (StringUtils.hasText(existed)) {
            return existed.trim();
        }
        String prefix = derivePrefix(dept.getDeptName(), dept.getId());
        departmentMapper.updateWorkNoPrefix(dept.getId(), prefix);
        dept.setWorkNoPrefix(prefix);
        log.info("自动分配部门工号前缀: deptId={}, deptName={}, prefix={}",
                dept.getId(), dept.getDeptName(), prefix);
        return prefix;
    }

    /** 读-改-写流水号。行锁与更新在同一事务内，保证同部门取号串行且不重复 */
    private int nextSeq(Long deptId) {
        Integer current = departmentMapper.selectWorkNoSeqForUpdate(deptId);
        int next = (current == null ? 0 : current) + 1;
        departmentMapper.updateWorkNoSeq(deptId, next);
        return next;
    }

    private boolean isPrefixAvailable(String prefix, Long excludeDeptId) {
        Long used = departmentMapper.countByWorkNoPrefix(prefix, excludeDeptId == null ? -1L : excludeDeptId);
        return used == null || used == 0;
    }

    private String format(String prefix, int seq) {
        return prefix + String.format(Locale.ROOT, "%0" + SEQ_MIN_WIDTH + "d", seq);
    }

    private String truncate(String value, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
