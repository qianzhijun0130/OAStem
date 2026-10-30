package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.recruit.dto.RecruitPostSaveRequest;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.support.RecruitPostAssembler;
import com.kellen.oa_system.hr.recruit.support.RecruitSupport;
import com.kellen.oa_system.hr.recruit.vo.RecruitPostVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 招聘职位：需求之下的「对外的岗位」。
 *
 * <p>删除前必须查候选人引用——职位被删后候选人会指向空职位，
 * 面试、offer 的职位名会全部变空，且无法回溯。</p>
 */
@Service
@RequiredArgsConstructor
public class RecruitPostService {

    private final RecruitPostMapper postMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final RecruitSupport support;
    private final RecruitPostAssembler assembler;

    public PageResult<RecruitPostVO> pagePosts(String keyword, Integer status, Long demandId,
                                                Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<RecruitPost> wrapper = new LambdaQueryWrapper<RecruitPost>()
                .orderByDesc(RecruitPost::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(RecruitPost::getPostName, keyword);
        }
        wrapper.eq(status != null, RecruitPost::getStatus, status);
        wrapper.eq(demandId != null, RecruitPost::getDemandId, demandId);
        Page<RecruitPost> page = postMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(assembler.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public Long savePost(RecruitPostSaveRequest request) {
        RecruitPost entity = request.getId() == null
                ? new RecruitPost() : postMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "职位不存在");
        }
        if (request.getDemandId() != null) {
            support.requireDemand(request.getDemandId());
        }
        entity.setDemandId(request.getDemandId());
        entity.setPostName(request.getPostName());
        entity.setCity(request.getCity());
        entity.setSalaryMin(request.getSalaryMin());
        entity.setSalaryMax(request.getSalaryMax());
        entity.setHeadCount(request.getHeadCount());
        entity.setChannel(request.getChannel() == null ? 1 : request.getChannel());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setHrId(request.getHrId() == null ? SecurityContextUtil.getCurrentUserId() : request.getHrId());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            postMapper.insert(entity);
        } else {
            postMapper.updateById(entity);
        }
        return entity.getId();
    }

    public void deletePost(Long id) {
        Long used = candidateMapper.selectCount(new LambdaQueryWrapper<RecruitCandidate>()
                .eq(RecruitCandidate::getPostId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该职位下已有候选人，不能删除，可改为下线");
        }
        postMapper.deleteById(id);
    }
}
