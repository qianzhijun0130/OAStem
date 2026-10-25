package com.kellen.oa_system.security.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段透明加解密的 MyBatis 类型处理器。
 *
 * <p>挂到实体字段上（{@code @TableField(typeHandler = ...)}，且实体需
 * {@code @TableName(autoResultMap = true)}），写入自动加密、读取自动解密，
 * 业务代码拿到的永远是明文，无需在 Service 层到处加解密。</p>
 *
 * <p><b>使用边界（重要）</b>：该处理器只在「实体字段参与映射」时生效。
 * {@code LambdaUpdateWrapper.set(...)} 与手写 {@code @Update} 里的参数<b>不会</b>走它，
 * 因此这类写法必须自己传入已加密的值。存量迁移脚本正是利用这一点直接写密文。</p>
 */
@MappedTypes(String.class)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, AesCrypto.get().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return AesCrypto.get().decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return AesCrypto.get().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return AesCrypto.get().decrypt(cs.getString(columnIndex));
    }
}
