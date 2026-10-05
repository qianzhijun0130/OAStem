import { ElMessage } from "element-plus";
import router from "@/router";
import { useUserStore } from "@/stores/user";
import type { MenuVO } from "@/api/types";

/** 无需登录即可访问的路径 */
const WHITE_LIST = ["/login", "/404"];

/**
 * 取菜单树中第一个可访问页面的路径，作为根路径 `/` 的落地页。
 *
 * <p>不能硬编码跳 `/dashboard`：菜单由后端按角色下发，没有工作台权限的角色
 * 会被硬编码的目标导向 404。这里按用户实际菜单取首个可用页面。</p>
 *
 * @returns 首个可访问路径；用户无任何菜单权限时返回 null
 */
function firstAccessiblePath(menus: MenuVO[]): string | null {
  for (const menu of menus) {
    if (menu.menuType === 2 && menu.path) {
      return menu.path;
    }
    if (menu.children && menu.children.length > 0) {
      const hit = firstAccessiblePath(menu.children);
      if (hit) {
        return hit;
      }
    }
  }
  return null;
}

// 第二个参数 from 未使用，以下划线开头避免 noUnusedParameters 报错
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore();

  if (userStore.token) {
    if (to.path === "/login") {
      // 已登录访问登录页，直接回首页
      next({ path: "/" });
      return;
    }
    if (!userStore.loaded) {
      try {
        await userStore.loadUserInfo();
        // 动态路由刚注册完，必须重新进入一次目标路由才能匹配到
        next({ ...to, replace: true });
      } catch (error) {
        ElMessage.error("获取用户信息失败，请重新登录");
        userStore.resetState();
        next(`/login?redirect=${encodeURIComponent(to.fullPath)}`);
      }
      return;
    }
    // 根路径本身没有路由定义，落到该用户有权限的第一个菜单；无权限时才进 404
    if (to.path === "/") {
      const target = firstAccessiblePath(userStore.menus);
      next({ path: target ?? "/404", replace: true });
      return;
    }
    next();
    return;
  }

  if (WHITE_LIST.includes(to.path)) {
    next();
    return;
  }
  next(`/login?redirect=${encodeURIComponent(to.fullPath)}`);
});

router.afterEach((to) => {
  const title = to.meta.title as string | undefined;
  document.title = title ? `${title} - OA 系统` : "OA 系统";
});
