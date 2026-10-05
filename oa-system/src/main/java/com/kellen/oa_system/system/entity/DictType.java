package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典类型实体，对应表 oa_dict_type。
 *
 * <p>字典类型是「一组枚举选项」的容器，例如 sys_gender 下挂着「未知/男/女」。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_dict_type")
public class DictType extends AuditEntity {

    private String dictName;

    /** 类型编码，全局唯一，业务代码按它取值 */
    private String dictType;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private String remark;
}
