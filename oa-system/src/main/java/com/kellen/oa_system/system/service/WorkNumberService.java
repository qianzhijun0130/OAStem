package com.kellen.oa_system.system.service;

/**
 * 工号服务：按部门分配员工工号。
 *
 * <p>工号结构 = 部门前缀（部门名称汉字拼音首字母，全大写）+ 部门内流水号（至少 3 位，左补零）。</p>
 *
 * <p><b>不回收、不复用</b>：流水号取自 {@code oa_department.work_no_seq}，
 * 只增不减。员工离职或被删除后该部门的流水号不回退，新员工继续向后递增。</p>
 */
public interface WorkNumberService {

    /**
     * 生成指定部门下的下一个工号。
     *
     * <p>部门尚未分配前缀时会按部门名称自动推导并回写数据库。
     * 建议在事务中调用，以保证取号行锁的有效性。</p>
     *
     * @param deptId 部门ID
     * @return 形如 {@code JSB001} 的工号
     */
    String generate(Long deptId);

    /**
     * 确保部门已分配工号前缀（为空则按部门名称推导并回写），返回最终生效的前缀。
     *
     * @param deptId 部门ID
     * @return 生效的工号前缀
     */
    String ensurePrefix(Long deptId);

    /**
     * 按部门名称推导工号前缀，遇到与其他部门冲突时追加数字序号（如 {@code JSB} / {@code JSB2}）。
     *
     * <p>纯计算，不写库，供部门保存流程复用。</p>
     *
     * @param deptName  部门名称
     * @param deptId    当前部门ID，用于排除自身；新增时可传 null
     * @return 可用的前缀
     */
    String derivePrefix(String deptName, Long deptId);

    /**
     * 校验手工指定的工号前缀是否可用（非空且未被其他部门占用）。
     *
     * @param prefix 前缀（调用方需已去除首尾空白）
     * @param deptId 当前部门ID，用于排除自身；新增时可传 null
     */
    void assertPrefixAvailable(String prefix, Long deptId);
}
