package com.kellen.oa_system.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.file.entity.FileRecord;
import com.kellen.oa_system.file.mapper.FileRecordMapper;
import com.kellen.oa_system.notice.dto.MyNoticePageQuery;
import com.kellen.oa_system.notice.dto.NoticePageQuery;
import com.kellen.oa_system.notice.dto.NoticeSaveRequest;
import com.kellen.oa_system.notice.entity.Notice;
import com.kellen.oa_system.notice.entity.NoticeRead;
import com.kellen.oa_system.notice.mapper.NoticeMapper;
import com.kellen.oa_system.notice.mapper.NoticeReadMapper;
import com.kellen.oa_system.notice.service.NoticeService;
import com.kellen.oa_system.notice.vo.MyNoticeDetailVO;
import com.kellen.oa_system.notice.vo.MyNoticeVO;
import com.kellen.oa_system.notice.vo.NoticeAttachmentVO;
import com.kellen.oa_system.notice.vo.NoticeDetailVO;
import com.kellen.oa_system.notice.vo.NoticeVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 公告通知服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    /** 发布范围 */
    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_DEPT = 2;
    private static final int SCOPE_USER = 3;

    /** 公告状态 */
    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_OFFLINE = 2;

    private static final int FLAG_NO = 0;

    private static final String ID_SEPARATOR = ",";

    /** 附件下载地址前缀，指向受鉴权保护的下载接口（不走公开的 /uploads 静态映射） */
    private static final String DOWNLOAD_URL_PREFIX = "/file/download/";

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final UserMapper userMapper;
    private final FileRecordMapper fileRecordMapper;

    // ==================== 管理端 ====================

    @Override
    public PageResult<NoticeVO> pageNotices(NoticePageQuery query) {
        Page<NoticeVO> page = new Page<>(query.getPageNo(), query.getPageSize());
        IPage<NoticeVO> result = noticeMapper.selectAdminPage(page, query);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public NoticeDetailVO getNoticeDetail(Long id) {
        Notice notice = requireNotice(id);
        NoticeDetailVO vo = new NoticeDetailVO();
        vo.setId(notice.getId());
        vo.setNoticeTitle(notice.getNoticeTitle());
        vo.setNoticeType(notice.getNoticeType());
        vo.setNoticeLevel(notice.getNoticeLevel());
        vo.setNoticeStatus(notice.getNoticeStatus());
        vo.setTopFlag(notice.getTopFlag());
        vo.setPublishScope(notice.getPublishScope());
        vo.setContent(notice.getContent());
        vo.setScopeDeptIds(parseIds(notice.getScopeDeptIds()));
        vo.setScopeUserIds(parseIds(notice.getScopeUserIds()));
        vo.setPublishTime(notice.getPublishTime());
        vo.setPublisherId(notice.getPublisherId());
        vo.setViewCount(notice.getViewCount());
        vo.setCreateTime(notice.getCreateTime());
        vo.setUpdateTime(notice.getUpdateTime());

        List<Long> attachmentIds = parseIds(notice.getAttachmentIds());
        vo.setAttachmentIds(attachmentIds);
        vo.setAttachments(loadAttachments(attachmentIds));

        User publisher = notice.getPublisherId() == null ? null : userMapper.selectById(notice.getPublisherId());
        if (publisher != null) {
            vo.setPublisherName(StringUtils.hasText(publisher.getRealName())
                    ? publisher.getRealName() : publisher.getUsername());
        }
        vo.setReadCount(noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getNoticeId, id)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveNotice(NoticeSaveRequest request) {
        assertScopeValid(request);
        assertScheduleValid(request);

        if (request.getId() == null) {
            Notice notice = new Notice();
            applyRequest(notice, request);
            notice.setNoticeStatus(STATUS_DRAFT);
            notice.setPublisherId(SecurityContextUtil.getCurrentUserId());
            notice.setViewCount(0);
            noticeMapper.insert(notice);
            log.info("公告已创建 id={} title={}", notice.getId(), notice.getNoticeTitle());
            return notice.getId();
        }

        Notice existing = requireNotice(request.getId());
        // 已发布/已下架的公告先下架再改：让「已发出的内容被谁改过」这件事有明确的状态边界
        if (!Objects.equals(existing.getNoticeStatus(), STATUS_DRAFT)) {
            throw new BizException(ErrorCode.NOTICE_PUBLISHED_CANNOT_EDIT);
        }
        // 只提交「id + 变更字段」的部分实体：整实体回写会带上从库里读出的旧 updateTime，
        // MyBatis-Plus 的自动填充不覆盖非空值，旧值会被显式写进 SET，
        // 使 MySQL 的 ON UPDATE CURRENT_TIMESTAMP 失效（详见 DictTypeServiceImpl#saveType）。
        Notice update = new Notice();
        update.setId(existing.getId());
        applyRequest(update, request);
        noticeMapper.updateById(update);
        log.info("公告已更新 id={}", existing.getId());
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Notice notice = requireNotice(id);
        if (Objects.equals(notice.getNoticeStatus(), STATUS_PUBLISHED)) {
            throw new BizException(ErrorCode.NOTICE_STATUS_ILLEGAL, "公告已处于发布状态");
        }
        Notice update = new Notice();
        update.setId(id);
        update.setNoticeStatus(STATUS_PUBLISHED);
        // 发布时刻截断到秒：publish_time 列是 datetime（秒精度），MySQL 会对写入值做四舍五入，
        // 亚秒 ≥0.5s 时会被进位到下一秒 → publish_time > NOW()，公告在用户端会「发布后短暂看不到」
        update.setPublishTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        noticeMapper.updateById(update);
        log.info("公告已发布 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        Notice notice = requireNotice(id);
        if (!Objects.equals(notice.getNoticeStatus(), STATUS_PUBLISHED)) {
            throw new BizException(ErrorCode.NOTICE_STATUS_ILLEGAL, "仅已发布的公告可以下架");
        }
        Notice update = new Notice();
        update.setId(id);
        update.setNoticeStatus(STATUS_OFFLINE);
        noticeMapper.updateById(update);
        log.info("公告已下架 id={}", id);
    }

    @Override
    public void updateTopFlag(Long id, Integer topFlag) {
        Notice notice = requireNotice(id);
        // 置顶是「已发布内容的展示排序属性」：草稿与已下架在用户端本就不可见，置顶无意义。
        // 与 publish / offline 保持同一套状态机口径，避免状态机出现「无校验」的缺口
        if (!Objects.equals(notice.getNoticeStatus(), STATUS_PUBLISHED)) {
            throw new BizException(ErrorCode.NOTICE_STATUS_ILLEGAL, "仅已发布的公告可以调整置顶");
        }
        Notice update = new Notice();
        update.setId(id);
        update.setTopFlag(topFlag == null ? FLAG_NO : topFlag);
        noticeMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long id) {
        requireNotice(id);
        // 阅读记录是公告的附属数据，公告删除后不存在独立价值，这里一并物理删除
        noticeReadMapper.delete(new LambdaQueryWrapper<NoticeRead>().eq(NoticeRead::getNoticeId, id));
        noticeMapper.deleteById(id);
        log.info("公告已删除 id={}", id);
    }

    // ==================== 用户端 ====================

    @Override
    public PageResult<MyNoticeVO> pageMyNotices(MyNoticePageQuery query) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        Long deptId = currentDeptId(userId);
        Page<MyNoticeVO> page = new Page<>(query.getPageNo(), query.getPageSize());
        IPage<MyNoticeVO> result = noticeMapper.selectVisiblePage(page, userId, deptId, query);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MyNoticeDetailVO getMyNoticeDetail(Long id) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        Long deptId = currentDeptId(userId);

        Notice notice = requireNotice(id);
        assertVisible(notice, userId, deptId);

        markRead(id, userId);
        // 用 SQL 自增而非「读出来 +1 再写回」，避免并发阅读时相互覆盖
        noticeMapper.update(null, new LambdaUpdateWrapper<Notice>()
                .eq(Notice::getId, id)
                .setSql("view_count = view_count + 1"));

        MyNoticeDetailVO vo = new MyNoticeDetailVO();
        vo.setId(notice.getId());
        vo.setNoticeTitle(notice.getNoticeTitle());
        vo.setNoticeType(notice.getNoticeType());
        vo.setNoticeLevel(notice.getNoticeLevel());
        vo.setTopFlag(notice.getTopFlag());
        vo.setPublishTime(notice.getPublishTime());
        vo.setPublisherId(notice.getPublisherId());
        vo.setContent(notice.getContent());
        vo.setViewCount((notice.getViewCount() == null ? 0 : notice.getViewCount()) + 1);
        vo.setReadFlag(1);

        User publisher = notice.getPublisherId() == null ? null : userMapper.selectById(notice.getPublisherId());
        if (publisher != null) {
            vo.setPublisherName(StringUtils.hasText(publisher.getRealName())
                    ? publisher.getRealName() : publisher.getUsername());
        }
        List<Long> attachmentIds = parseIds(notice.getAttachmentIds());
        vo.setAttachments(loadAttachments(attachmentIds));
        return vo;
    }

    @Override
    public long countUnread() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        return noticeMapper.countUnread(userId, currentDeptId(userId));
    }

    @Override
    public List<MyNoticeVO> listLatest(int limit) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        return noticeMapper.selectVisibleList(userId, currentDeptId(userId), Math.max(limit, 1));
    }

    // ==================== 内部方法 ====================

    private Notice requireNotice(Long id) {
        Notice notice = id == null ? null : noticeMapper.selectById(id);
        if (notice == null) {
            throw new BizException(ErrorCode.NOTICE_NOT_FOUND);
        }
        return notice;
    }

    /**
     * 单条公告的可见性判断。
     *
     * <p>与列表 SQL 的过滤条件必须保持一致：SQL 负责「查得少」，这里负责「挡得住」，
     * 二者缺一都会造成越权（只靠列表过滤，猜出 ID 就能直接读详情）。</p>
     */
    private void assertVisible(Notice notice, Long userId, Long deptId) {
        if (!Objects.equals(notice.getNoticeStatus(), STATUS_PUBLISHED)) {
            throw new BizException(ErrorCode.NOTICE_NOT_PUBLISHED);
        }
        // 与三条可见性 SQL 的「publish_time IS NULL OR publish_time <= NOW()」对齐：
        // SQL 负责「查得少」、这里负责「挡得住」。少了这一条，未到发布时间的公告可被猜 id 直读
        LocalDateTime publishTime = notice.getPublishTime();
        if (publishTime != null && publishTime.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.NOTICE_NOT_PUBLISHED);
        }
        int scope = notice.getPublishScope() == null ? SCOPE_ALL : notice.getPublishScope();
        boolean visible = switch (scope) {
            case SCOPE_ALL -> true;
            case SCOPE_DEPT -> containsId(notice.getScopeDeptIds(), deptId);
            case SCOPE_USER -> containsId(notice.getScopeUserIds(), userId);
            default -> false;
        };
        if (!visible) {
            throw new BizException(ErrorCode.FORBIDDEN, "该公告不在你的可见范围内");
        }
    }

    private void markRead(Long noticeId, Long userId) {
        Long exists = noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getNoticeId, noticeId)
                .eq(NoticeRead::getUserId, userId));
        if (exists != null && exists > 0) {
            return;
        }
        // 用 INSERT IGNORE 而非「先查后插、靠唯一索引拦失败」：并发窗口内另一请求可能已写入同一
        // (notice_id, user_id)，若直接 insert 会抛 DuplicateKeyException 并让整个详情请求回滚
        // （表现为 code=5000、阅读数自增随之丢失）。IGNORE 把冲突降级为「无操作」，天然幂等
        noticeReadMapper.insertIgnore(noticeId, userId, LocalDateTime.now());
    }

    private void assertScopeValid(NoticeSaveRequest request) {
        if (Objects.equals(request.getPublishScope(), SCOPE_DEPT)
                && !hasAnyValidId(request.getScopeDeptIds())) {
            throw new BizException(ErrorCode.NOTICE_SCOPE_REQUIRED);
        }
        if (Objects.equals(request.getPublishScope(), SCOPE_USER)
                && !hasAnyValidId(request.getScopeUserIds())) {
            throw new BizException(ErrorCode.NOTICE_SCOPE_REQUIRED);
        }
    }

    /**
     * 判定发布范围是否至少含一个「有效 id」。
     *
     * <p>不能只看 {@code CollectionUtils.isEmpty}：{@code [null]} 这类空壳范围长度为 1、却没有任何
     * 有效元素，落库后 {@code scope_dept_ids} 仍是 NULL，与 {@code publish_scope=2} 不自洽，
     * 会静默产生一条「用户端永久不可见」的公告。</p>
     */
    private static boolean hasAnyValidId(List<Long> ids) {
        return ids != null && ids.stream().anyMatch(Objects::nonNull);
    }

    /**
     * 校验定时发布 / 自动下架时间的合法性。
     *
     * <p>计划发布时间必须晚于当前时间——否则「计划发布」就成了「立即发布」的隐式别名，
     * 与手工发布动作语义重叠；到期时间必须晚于计划发布时间。</p>
     */
    private void assertScheduleValid(NoticeSaveRequest request) {
        LocalDateTime scheduled = request.getScheduledPublishTime();
        LocalDateTime expire = request.getExpireTime();
        if (scheduled != null && !scheduled.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "计划发布时间必须晚于当前时间");
        }
        if (expire != null && scheduled != null && !expire.isAfter(scheduled)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "自动下架时间必须晚于计划发布时间");
        }
    }

    private void applyRequest(Notice notice, NoticeSaveRequest request) {
        notice.setNoticeTitle(request.getNoticeTitle());
        notice.setNoticeType(request.getNoticeType());
        notice.setNoticeLevel(request.getNoticeLevel());
        notice.setContent(request.getContent());
        notice.setPublishScope(request.getPublishScope());
        // 范围与附件都以逗号串落库：结构稳定、便于 FIND_IN_SET 过滤，无需中间表
        notice.setScopeDeptIds(joinIds(request.getScopeDeptIds()));
        notice.setScopeUserIds(joinIds(request.getScopeUserIds()));
        notice.setAttachmentIds(joinIds(request.getAttachmentIds()));
        notice.setTopFlag(request.getTopFlag() == null ? FLAG_NO : request.getTopFlag());
        // 定时发布 / 自动下架：由 NoticeScheduleTask 每分钟扫描生效
        notice.setScheduledPublishTime(request.getScheduledPublishTime());
        notice.setExpireTime(request.getExpireTime());
    }

    private Long currentDeptId(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    private List<NoticeAttachmentVO> loadAttachments(List<Long> attachmentIds) {
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return List.of();
        }
        return fileRecordMapper.selectBatchIds(attachmentIds).stream()
                .map(file -> toAttachmentVO(file))
                .toList();
    }

    private NoticeAttachmentVO toAttachmentVO(FileRecord file) {
        NoticeAttachmentVO vo = new NoticeAttachmentVO();
        vo.setId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileSize(file.getFileSize());
        vo.setUrl(DOWNLOAD_URL_PREFIX + file.getId());
        return vo;
    }

    private static String joinIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(ID_SEPARATOR));
    }

    private static List<Long> parseIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return List.of();
        }
        return Arrays.stream(ids.split(ID_SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private static boolean containsId(String csv, Long targetId) {
        if (targetId == null || !StringUtils.hasText(csv)) {
            return false;
        }
        return parseIds(csv).contains(targetId);
    }
}
