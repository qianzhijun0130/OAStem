package com.kellen.oa_system.notice.dto;

import lombok.Data;

/**
 * 「我的公告」分页查询条件。
 *
 * <p>可见范围由服务端按当前用户身份推导，接口不接受任何用户/部门参数——
 * 从接口形态上杜绝越权查询他人公告。</p>
 */
@Data
public class MyNoticePageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    private String noticeTitle;

    private Integer noticeType;

    /** 阅读状态筛选：0只看未读 1只看已读，为空则不过滤 */
    private Integer readFlag;
}
