package com.kellen.oa_system.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据项视图对象。
 */
@Data
public class DictDataVO {

    private Long id;

    private String dictType;

    private String itemLabel;

    private String itemValue;

    private Integer sort;

    private Integer status;

    private String cssClass;

    private Integer isDefault;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
