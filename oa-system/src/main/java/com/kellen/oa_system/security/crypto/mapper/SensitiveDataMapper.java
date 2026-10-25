package com.kellen.oa_system.security.crypto.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 存量敏感字段直读直写。
 *
 * <p>为什么不用实体 Mapper 做迁移：实体字段上挂了加密 TypeHandler，
 * 读出来已被解密，无法判断"库里原本是明文还是密文"，迁移就失去幂等性。
 * 这里刻意绕开 TypeHandler（返回值用 Map、更新语句手写），拿到的是<b>库里的原始值</b>。</p>
 *
 * <p>表名与列名均为硬编码常量，不接受外部入参，因此不存在拼接注入风险。</p>
 */
public interface SensitiveDataMapper {

    @Select("SELECT id, id_card AS v FROM oa_user WHERE id_card IS NOT NULL AND id_card <> ''")
    List<Map<String, Object>> selectRawIdCards();

    @Update("UPDATE oa_user SET id_card = #{value} WHERE id = #{id}")
    int updateIdCard(@Param("id") Long id, @Param("value") String value);

    @Select("SELECT id, bank_account AS v FROM oa_employee_bank "
            + "WHERE bank_account IS NOT NULL AND bank_account <> ''")
    List<Map<String, Object>> selectRawBankAccounts();

    @Update("UPDATE oa_employee_bank SET bank_account = #{value} WHERE id = #{id}")
    int updateBankAccount(@Param("id") Long id, @Param("value") String value);

    @Select("SELECT id, social_no AS v FROM oa_employee_bank "
            + "WHERE social_no IS NOT NULL AND social_no <> ''")
    List<Map<String, Object>> selectRawSocialNos();

    @Update("UPDATE oa_employee_bank SET social_no = #{value} WHERE id = #{id}")
    int updateSocialNo(@Param("id") Long id, @Param("value") String value);

    @Select("SELECT id, fund_no AS v FROM oa_employee_bank "
            + "WHERE fund_no IS NOT NULL AND fund_no <> ''")
    List<Map<String, Object>> selectRawFundNos();

    @Update("UPDATE oa_employee_bank SET fund_no = #{value} WHERE id = #{id}")
    int updateFundNo(@Param("id") Long id, @Param("value") String value);
}
