-- ============================================================
-- 菜单重构：把「自助」类页面从「人力资源」抽出，独立为一级目录「自助服务」
--
-- 背景：我的考勤 / 我的工资条 / 我的绩效 / 我的培训 是普通员工即可查看的个人页面，
--       挂在「人力资源」下不合适（人力资源应只放 HR 职能页面）。
--
-- 处理：新建一级目录 自助服务（menu_code=self），4 个页面迁入并把路径前缀 /hr → /self；
--       权限码（hr:my-attendance / hr:salary:my / hr:performance:my / hr:training:my）保持不变，
--       避免已授权的角色菜单与 @PreAuthorize 失效。
--
-- ⚠️ 关键：新目录必须授权给**所有角色**。菜单树 buildTree 会丢弃「父不在可见集合」的节点，
--        只给 HR 授权会导致普通员工整枝看不到「自助服务」。
--
-- 幂等：目录按主键 + menu_code 双重判重；迁移按固定主键 UPDATE；授权按 NOT EXISTS。
-- 回滚：见文件末尾注释。
--
-- 执行（含中文，必须走 stdin）：
--   mysql --default-character-set=utf8mb4 -uroot -p oa_system < hr_self_service_menu.sql
-- ============================================================

-- ---------- 1) 新建一级目录「自助服务」 ----------
INSERT INTO oa_menu (id, parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 286, 0, '自助服务', 1, '/self', 'self', NULL, 'UserRound', 9, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE id = 286);

-- 历史库中 286 可能已被占用：退化为按 menu_code 插入
INSERT INTO oa_menu (parent_id, menu_name, menu_type, path, menu_code, component, icon, sort, visible, status)
SELECT 0, '自助服务', 1, '/self', 'self', NULL, 'UserRound', 9, 1, 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM oa_menu WHERE menu_code = 'self');

SET @self_id := (SELECT id FROM oa_menu WHERE menu_code = 'self' ORDER BY id LIMIT 1);

-- ---------- 2) 四个自助页迁入，并改路径前缀 ----------
UPDATE oa_menu SET parent_id = @self_id, path = '/self/my-attendance',  sort = 1
 WHERE id = 235 AND @self_id IS NOT NULL;

UPDATE oa_menu SET parent_id = @self_id, path = '/self/my-salary',      sort = 2
 WHERE id = 256 AND @self_id IS NOT NULL;

UPDATE oa_menu SET parent_id = @self_id, path = '/self/my-performance', sort = 3
 WHERE id = 264 AND @self_id IS NOT NULL;

UPDATE oa_menu SET parent_id = @self_id, path = '/self/my-training',    sort = 4
 WHERE id = 273 AND @self_id IS NOT NULL;

-- ---------- 3) 新目录授权给所有未删除角色 ----------
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, @self_id
  FROM oa_role r
 WHERE r.is_deleted = 0
   AND @self_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = @self_id);

-- ---------- 4) 超管兜底：确保新目录在超管名下（启动期 MenuIntegrityValidator 也会自动补齐） ----------
INSERT INTO oa_role_menu (role_id, menu_id)
SELECT r.id, @self_id
  FROM oa_role r
 WHERE r.role_code = 'SUPER_ADMIN'
   AND r.is_deleted = 0
   AND @self_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM oa_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = @self_id);

-- ============================================================
-- 回滚脚本（如需还原到「挂在人力资源下」的状态）：
--   UPDATE oa_menu SET parent_id = 230, path = '/hr/my-attendance',  sort = 5  WHERE id = 235;
--   UPDATE oa_menu SET parent_id = 230, path = '/hr/my-salary',      sort = 7  WHERE id = 256;
--   UPDATE oa_menu SET parent_id = 230, path = '/hr/my-performance', sort = 9  WHERE id = 264;
--   UPDATE oa_menu SET parent_id = 230, path = '/hr/my-training',    sort = 12 WHERE id = 273;
--   DELETE FROM oa_role_menu WHERE menu_id = (SELECT id FROM oa_menu WHERE menu_code='self');
--   DELETE FROM oa_menu WHERE menu_code = 'self';
-- ============================================================
