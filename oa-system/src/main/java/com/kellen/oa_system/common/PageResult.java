package com.kellen.oa_system.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应体。
 *
 * <p>与 MyBatis-Plus 的 IPage 解耦：Controller 层不直接暴露 IPage，
 * 避免分页实现细节泄漏到接口契约中。</p>
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;

    private Long total;

    private Long pageNo;

    private Long pageSize;

    private Long totalPages;

    public static <T> PageResult<T> of(List<T> records, Long total, Long pageNo, Long pageSize) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setRecords(records == null ? Collections.emptyList() : records);
        pageResult.setTotal(total);
        pageResult.setPageNo(pageNo);
        pageResult.setPageSize(pageSize);
        pageResult.setTotalPages(pageSize == null || pageSize == 0
                ? 0L
                : (total + pageSize - 1) / pageSize);
        return pageResult;
    }

    public static <T> PageResult<T> empty(Long pageNo, Long pageSize) {
        return of(Collections.emptyList(), 0L, pageNo, pageSize);
    }
}
