package com.kellen.oa_system.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型视图对象。
 */
@Data
public class DictTypeVO {

    private Long id;

    private String dictName;

    private String dictType;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
