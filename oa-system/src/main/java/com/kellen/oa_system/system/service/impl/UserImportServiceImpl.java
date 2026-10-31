package com.kellen.oa_system.system.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.ExcelImportUtil;
import com.kellen.oa_system.system.service.UserImportService;
import com.kellen.oa_system.system.support.IdCardGuard;
import com.kellen.oa_system.system.support.UserImportLookup;
import com.kellen.oa_system.system.support.UserImportPersister;
import com.kellen.oa_system.system.support.UserImportRow;
import com.kellen.oa_system.system.support.UserImportRowParser;
import com.kellen.oa_system.system.support.UserImportTemplate;
import com.kellen.oa_system.system.vo.UserImportErrorVO;
import com.kellen.oa_system.system.vo.UserImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工批量导入（BIZ-OPT-09 增强版）。
 *
 * <h2>策略变更：整批拒绝 → 逐行成功</h2>
 * <p>旧实现是「任一行有问题就一行都不写」。听起来安全，实际用起来很糟：
 * 300 人的入职表里错 2 行，HR 得改完重传、再等一次全量校验，
 * 而每一次重传都要重新核对「哪些人已经建过了」。方案要求改为
 * <b>错误按行返回、部分成功不回滚</b>。</p>
 *
 * <p>代价是「导了一半」成为可能，所以提示语必须说清楚两件事：成功多少条、
 * 失败哪些行；并且明确告知<b>已成功的不会重复创建</b>——否则 HR 会担心重传会不会建两份。</p>
 *
 * <h2>为什么本方法不加 @Transactional</h2>
 * <p>外层一旦有事务，某行抛异常会让 Spring 把整个事务标记为 rollback-only；
 * 即便这里 catch 住，最终 commit 仍会抛 {@code UnexpectedRollbackException}，
 * 表现是「提示成功 N 条，实际一条都没有」。事务边界下沉到
 * {@link UserImportPersister#persist}，实现「每条一个事务」。</p>
 *
 * <h2>职责划分</h2>
 * <ul>
 *   <li>{@link UserImportTemplate}：表头定义、新旧版本识别；</li>
 *   <li>{@link UserImportLookup}：导入前一次性预取（部门/角色/上级/占用集合）；</li>
 *   <li>{@link UserImportRowParser}：单行校验与解析（每次导入新建，见其类注释）；</li>
 *   <li>{@link UserImportPersister}：单条落库（用户 + 合同，同一事务）；</li>
 *   <li>本类：只管编排与结果统计。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportServiceImpl implements UserImportService {

    /** 错误明细最多返回条数，防止一次几千条把响应撑爆 */
    private static final int MAX_ERROR_DETAIL = 200;

    private final UserImportLookup lookup;
    private final UserImportPersister persister;
    private final IdCardGuard idCardGuard;

    @Override
    public UserImportResultVO importUsers(MultipartFile file) {
        List<List<String>> sheet = ExcelImportUtil.readFirstSheet(file);
        if (sheet.isEmpty()) {
            throw new BizException(ErrorCode.USER_IMPORT_EMPTY);
        }
        // 旧模板在这里被拦下，给的是「请下载新模板」而不是「第 11 列表头应为…」
        UserImportTemplate.assertHeader(sheet.get(0));

        lookup.reload();
        UserImportRowParser parser = new UserImportRowParser(lookup, idCardGuard);

        List<UserImportRow> pending = new ArrayList<>();
        List<UserImportErrorVO> errors = new ArrayList<>();
        int totalRows = 0;

        // 第一遍：纯校验，不写库。校验通过的行攒起来，等全部校验完再统一入库——
        // 这样「文件内重复」能一次报全，不会因为第 3 行已经入库而导致第 5 行的提示变成
        // 「在系统中已存在」（同一件事两种说法）
        for (int i = 1; i < sheet.size(); i++) {
            List<String> row = sheet.get(i);
            if (ExcelImportUtil.isBlankRow(row)) {
                continue;
            }
            totalRows++;
            try {
                pending.add(parser.parse(i + 1, row));
            } catch (BizException e) {
                errors.add(new UserImportErrorVO(i + 1, e.getMessage()));
            } catch (Exception e) {
                log.error("批量导入第 {} 行解析异常", i + 1, e);
                errors.add(new UserImportErrorVO(i + 1, "该行解析异常，请联系管理员"));
            }
        }

        if (totalRows == 0) {
            throw new BizException(ErrorCode.USER_IMPORT_EMPTY);
        }

        // 第二遍：逐条入库。每条一个事务（见 UserImportPersister），
        // 因此这里能安全地 catch 掉单行失败而不影响已成功的行
        int success = 0;
        for (UserImportRow row : pending) {
            try {
                persister.persist(row);
                success++;
            } catch (Exception e) {
                log.warn("批量导入第 {} 行入库失败: {}", row.rowNo(), e.getMessage());
                errors.add(new UserImportErrorVO(row.rowNo(), renderError(e)));
            }
        }

        errors.sort((a, b) -> Integer.compare(a.getRow(), b.getRow()));
        return buildResult(totalRows, success, errors);
    }

    @Override
    public List<String> templateHeaders() {
        return UserImportTemplate.headers();
    }

    @Override
    public List<String> templateNotes() {
        return UserImportTemplate.notes();
    }

    // ------------------------------------------------------------------
    // 结果组装
    // ------------------------------------------------------------------

    private UserImportResultVO buildResult(int totalRows, int success, List<UserImportErrorVO> errors) {
        int failCount = errors.size();
        List<UserImportErrorVO> shown = failCount > MAX_ERROR_DETAIL
                ? errors.subList(0, MAX_ERROR_DETAIL) : errors;

        String message;
        if (failCount == 0) {
            message = "导入成功，共新增 " + success + " 名员工。初始密码为系统默认初始密码，"
                    + "试用期截止日与任职状态已按入职日期自动生成，请提醒员工尽快修改密码。";
        } else if (success == 0) {
            message = "校验未通过，共 " + failCount + " 行存在问题，本次未导入任何数据，请修正后重新上传";
        } else {
            message = "导入完成：成功 " + success + " 条，失败 " + failCount
                    + " 行（已跳过，不影响其余数据）。请按行号修正后重新上传——"
                    + "已成功的 " + success + " 条不会重复创建。";
        }
        if (failCount > MAX_ERROR_DETAIL) {
            message += "（仅显示前 " + MAX_ERROR_DETAIL + " 条明细）";
        }
        log.info("批量导入完成: 总行数={}, 成功={}, 失败={}", totalRows, success, failCount);
        return UserImportResultVO.builder()
                .imported(success > 0)
                .totalRows(totalRows)
                .successCount(success)
                .failCount(failCount)
                .errors(shown)
                .message(message)
                .build();
    }

    /**
     * 把入库异常翻成人能读的一句话。
     *
     * <p>业务异常直接用其 message（那是给用户看的）；其余异常一律笼统化，
     * 因为底层异常常带 SQL 片段与表名，既看不懂也不该外泄。详情进日志。</p>
     */
    private static String renderError(Exception e) {
        if (e instanceof BizException biz) {
            return biz.getMessage();
        }
        return "入库失败，请联系管理员（导入日志中已记录详情）";
    }
}
