package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典项实体，对应表 oa_dict_data。
 *
 * <p>通过 {@code dictType} 字符串关联所属字典类型，而不是外键 id——
 * 业务代码取值时只需要知道编码（如 {@code sys_gender}），不必先查类型表拿 id。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_dict_data")
public class DictData extends AuditEntity {

    private String dictType;

    /** 显示名称 */
    private String itemLabel;

    /** 数据值 */
    private String itemValue;

    private Integer sort;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 前端标签样式（el-tag 的 type），可为空 */
    private String cssClass;

    /** 是否为该类型的默认选中项：1是 0否 */
    private Integer isDefault;

    private String remark;
}
