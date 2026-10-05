<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { useUserStore } from "@/stores/user";
import type { LoginRequest } from "@/api/types";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive<LoginRequest>({
  username: "",
  password: "",
});

const rules: FormRules<LoginRequest> = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

async function handleLogin() {
  // 防重复提交-闸门：提交进行中，忽略后续触发（同时覆盖按钮点击与表单回车两个入口）
  if (loading.value) return;
  if (!formRef.value) return;

  loading.value = true;
  try {
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) {
      loading.value = false;
      return;
    }

    await userStore.login(form);

    // 默认落到根路径 `/`，由 permission.ts 的全局守卫按该用户实际菜单
    // 取第一个可访问页面（超管即工作台 `/dashboard`）。
    // 不能硬编码 `/dashboard`：工作台的菜单权限是按角色下发的，
    // 没有该权限的角色会被导向 404。
    const rawRedirect = route.query.redirect;
    const redirect =
      typeof rawRedirect === "string" && rawRedirect ? rawRedirect : "/";

    // 必须 await：router.push 是异步的，若不等它完成就复位开关，会出现
    // 「登录请求已返回、页面尚未跳走」的窗口期——用户在该窗口内再次点击会
    // 发出第二次登录请求，这正是本缺陷的真实成因（已由对照实验证实）。
    await router.push(redirect);
    ElMessage.success("登录成功");

    // 成功后刻意「不复位」开关：页面随即卸载，保持禁用可杜绝任何尾随点击；
    // 跳转失败会在 catch 中复位，不会让按钮永久卡死。
  } catch (error) {
    // 具体错误信息已由请求拦截器统一提示
    console.error("登录失败", error);
    // 仅在校验通过后的异常路径复位，保证用户可重试
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-card__title">OA 企业办公管理系统</div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="'User'"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="'Lock'"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          style="width: 100%"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="login-card__tip">
        初始账号 admin，首次登录后请及时修改密码
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.login-card {
  width: 400px;
}

.login-card__title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.login-card__tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}
</style>
