package com.kellen.oa_system.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 带审计字段的实体基类。
 *
 * <p>create_time / update_time 由 {@link com.kellen.oa_system.config.FillMetaObjectHandler}
 * 自动填充，业务代码不需要手工赋值；is_deleted 由 MyBatis-Plus 逻辑删除接管，
 * 调用 mapper.deleteById() 实际执行的是 UPDATE ... SET is_deleted = 1。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditEntity extends BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
