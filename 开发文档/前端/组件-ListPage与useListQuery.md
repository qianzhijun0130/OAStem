# 列表页组件：`ListPage` + `useListQuery`

> 位置：`vue3-admin-template/src/components/list/ListPage.vue`、`src/components/list/types.ts`、`src/composables/useListQuery.ts`
> 试点页面：`src/views/workflow/instance/index.vue`（流程实例）

## 1. 定位与职责边界

| | 负责 | 不负责 |
| --- | --- | --- |
| `useListQuery` | 查询条件容器、页码/页大小、加载态、搜索/重置/刷新、并发响应去重、异常兜底 | 任何 UI、任何具体接口、权限判断 |
| `ListPage` | 卡片外框、筛选区与工具栏左右布局、表格加载/边框/斑马纹/空状态、底部分页 | 业务字段、列定义、筛选表单、行内操作、请求 |

拆分原则：**列表页真正重复的是「壳」，差异极大的是「列与筛选」**。共性收进组件、差异留在页面，才能既消重又不牺牲灵活性。

另：`ListPage` 是**受控组件**——页码/页大小完全由 props 决定，交互只发事件；唯一的真相源在 `useListQuery`。

## 2. `useListQuery`

### 2.1 Options

| 选项 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `initialQuery` | `() => Q` | ✅ | — | **工厂函数**，每次返回新对象（重置靠它拿干净副本） |
| `fetcher` | `(params: Q & PageParams) => Promise<PageResult<T> \| null \| undefined>` | ✅ | — | 取数函数；返回 `null` 会被安全降级为空页 |
| `pageSize` | `number` | | `10` | 初始每页条数 |
| `immediate` | `boolean` | | `true` | 是否 `onMounted` 自动加载 |
| `beforeFetch` | `(query: Q) => void` | | — | 取数前原地加工条件（如把 `dateRange` 拆成 `beginTime/endTime`） |
| `onError` | `(error: unknown) => void` | | — | 失败回调；**失败不清空已有数据** |

### 2.2 返回值

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list` | `Ref<T[]>` | 当前页数据 |
| `total` | `Ref<number>` | 总条数 |
| `loading` | `Ref<boolean>` | 加载态，可直接 `v-loading` |
| `query` | `Q`（reactive） | 查询条件，表单 `v-model` 直接绑 |
| `pageNo` / `pageSize` | `Ref<number>` | 传给 `ListPage` |
| `load / search / reset / refresh` | `() => Promise<void>` | 加载 / 回第 1 页搜索 / 重置条件再加载 / 刷新当前页 |
| `handleSizeChange / handlePageChange` | `(v:number) => Promise<void>` | 绑到 `ListPage` 的 `size-change` / `current-change` |

### 2.3 示例

```ts
interface InstanceQuery extends Omit<ApprovalInstancePageQuery, 'pageNo' | 'pageSize'> {
  dateRange?: string[]
}

const { list, total, loading, query, pageNo, pageSize, refresh, search, reset,
        handleSizeChange, handlePageChange } =
  useListQuery<ApprovalInstanceVO, InstanceQuery>({
    initialQuery: () => ({
      title: undefined, applicantName: undefined, status: undefined,
      flowCode: undefined, beginTime: undefined, endTime: undefined, dateRange: undefined,
    }),
    fetcher: (params) => getInstancesApi(params),
    beforeFetch: (q) => {
      q.beginTime = q.dateRange?.[0]
      q.endTime = q.dateRange?.[1]
    },
    onError: (error) => logAsyncError('加载流程实例失败', error),
  })
```

## 3. `ListPage`

### 3.1 Props

| Prop | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `data` | `unknown[] \| null` | `[]` | 表格数据；`null/undefined` 安全降级为 `[]` |
| `total` | `number` | `0` | 总条数；非数字/负数按 0 |
| `loading` | `boolean` | `false` | 表格加载态 |
| `pageNo` | `number` | `1` | 当前页（从 1 起） |
| `pageSize` | `number` | `10` | 每页条数 |
| `pageSizes` | `number[]` | `[10, 20, 50]` | 可选每页条数 |
| `border` | `boolean` | `true` | 表格边框 |
| `stripe` | `boolean` | `false` | 斑马纹 |
| `rowKey` | `string` | `'id'` | 行主键 |
| `emptyText` | `string` | `'暂无数据'` | 空状态文案 |
| `showPagination` | `boolean` | `true` | 是否显示分页 |
| `paginationLayout` | `string` | `total, sizes, prev, pager, next, jumper` | 分页布局 |
| `card` | `boolean` | `true` | 是否包 `el-card`（页面已有卡片外壳时传 `false`） |
| `cardShadow` | `'always' \| 'hover' \| 'never'` | `'never'` | 卡片阴影（仅 `card=true`） |

### 3.2 Events

| 事件 | 载荷 | 说明 |
| --- | --- | --- |
| `size-change` | `number` | 每页条数变化（语义事件，推荐用它触发加载） |
| `current-change` | `number` | 页码变化 |
| `update:pageSize` | `number` | 供 `v-model:page-size` 使用 |
| `update:pageNo` | `number` | 供 `v-model:page-no` 使用 |

> ⚠️ **不要同时**用 `v-model:page-size` 和 `@size-change` 去调加载：前者由组件回写、后者再触发一次，容易重复请求。二选一（本项目统一用「`:page-no` + `@size-change`/`@current-change`」）。

### 3.3 Slots

| 插槽 | 说明 |
| --- | --- |
| `filters` | 筛选表单（整块由页面提供，组件不包 `el-form`，保留多行/复杂布局自由） |
| `toolbar` | 筛选区右侧操作（新增/导出等）；与 `filters` 同一行，自动左右分布 |
| `default` | 表格列（`el-table-column`） |
| `empty` | 自定义空状态；不传则用 `emptyText` + `el-empty` |

### 3.4 完整示例

```vue
<ListPage
  :data="list"
  :total="total"
  :loading="loading"
  :page-no="pageNo"
  :page-size="pageSize"
  empty-text="暂无流程实例"
  @size-change="handleSizeChange"
  @current-change="handlePageChange"
>
  <template #filters>
    <el-form :inline="true" @submit.prevent="search">
      <el-form-item label="标题">
        <el-input v-model="query.title" placeholder="模糊匹配" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </template>

  <template #toolbar>
    <el-button type="primary" @click="openCreate">新增</el-button>
  </template>

  <el-table-column prop="title" label="申请标题" min-width="200" />
  <el-table-column label="操作" width="160" fixed="right">
    <template #default="{ row }">
      <el-button link type="primary" @click="showDetail(row)">详情</el-button>
    </template>
  </el-table-column>
</ListPage>
```

### 3.5 最小示例（无筛选、无工具栏）

```vue
<ListPage :data="list" :total="total" :loading="loading"
          :page-no="pageNo" :page-size="pageSize"
          @size-change="handleSizeChange" @current-change="handlePageChange">
  <el-table-column prop="name" label="名称" />
</ListPage>
```

## 4. 接入新列表页（5 步）

1. 定义 `XxxQuery`（继承页面查询接口并 `Omit<'pageNo'|'pageSize'>`），需要区间就补 `dateRange`。
2. `useListQuery` 里给 `initialQuery`（**工厂函数**）、`fetcher`、按需 `beforeFetch` / `onError`。
3. 模板用 `<ListPage>`：`:data/:total/:loading/:page-no/:page-size` + 两个分页事件。
4. 筛选表单放 `#filters`，右上操作放 `#toolbar`，列直接写在默认插槽。
5. 增删改后调 `refresh()`；「查询」调 `search()`；「重置」调 `reset()`。

## 5. 常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 翻页发两次请求 | 同时绑 `v-model:page-*` 与 `@size-change/@current-change` | 只用「受控 props + 语义事件」 |
| 重置后条件残留 | `initialQuery` 返回了共享对象 | 必须返回新对象；`reset` 内部已「先删后赋」 |
| 列表偶发显示旧数据 | 并发请求后发先至 | `useListQuery` 用请求序号丢弃过期响应，勿在外部再并发调用 |
| 接口异常整页空白 | 直接 `data.records` 取值 | 组件/composable 已做 `null`/`total` 兜底；页面不要再假设非空 |
| 与已有 `el-card` 重复套壳 | 页面本身已有卡片 | 传 `:card="false"` |
| **树表**（如 `system/dept`）套上后多出空分页 | 树表无分页概念 | 树表不要用本组件；确需统一外壳时用 `:show-pagination="false"`，且**不要**引入 `useListQuery`（避免伪造 pageNo/pageSize） |
| 把工具栏塞进 `#toolbar` 后位置变了 | `#toolbar` 与筛选同排右对齐 | 若页面原本是「筛选一行 + 工具栏独立一行」，把工具栏块放进 `#filters` 末尾即可保持原视觉（`notice/manage` 即如此） |

## 6. 维护说明与变更记录

- **扩展新能力**优先加在 composable（逻辑）而非组件（视图）；视图只加插槽，不加业务 props。
- `types.ts` 只放跨页面共用的形状，**禁止**塞入具体业务字段。
- 修改组件 props/事件属于破坏性变更：需同步更新本文档与试点页面，并跑流程实例页的浏览器回归（`F:/tmp/m09_inst_ui.js`，10 项）。

### 已迁移页面

| 页面 | 文件 | 迁移日 | 说明 |
| --- | --- | --- | --- |
| 流程实例 | `views/workflow/instance/index.vue` | 2026-10-19 | 首个试点；`beforeFetch` 把 `dateRange` 映射为 `beginTime/endTime` |
| 公告管理 | `views/notice/manage/index.vue` | 2026-10-19 | `immediate: false` 保持「先选项、后列表」；工具栏留在筛选区下方独立一行 |
| 角色管理 | `views/system/role/index.vue` | 2026-10-19 | 分页项 `[10,20,50,100]`、layout 含 jumper；工具栏同样保留独立一行；权限树/克隆弹窗逻辑未动 |
| 系统字典（右栏） | `views/system/dict/index.vue` | 2026-10-19 | 左右两栏：仅右栏「表格+分页」用 `ListPage :card="false"`，**卡片头部与筛选保持原位**；`dictType` 由 fetcher 合并，不暴露为筛选条件 |
| 登录日志 | `views/system/login-log/index.vue` | 2026-10-19 | `beforeFetch` 把 `timeRange` 映射为 `startTime/endTime`；日期区间是页面私有状态，`handleReset` 需另行清空后再 `reset()` |
| 操作日志 | `views/system/operation-log/index.vue` | 2026-10-19 | 同上；详情用 `el-drawer`，与列表解耦 |
| 员工管理 | `views/system/user/index.vue` | 2026-10-19 | 4 个筛选（部门树/角色/状态/关键字）+ 导入弹窗；分页 `[10,20,50,100]`+jumper；工具栏独立一行 |

> 不适用：`system/dept`（树表、无分页）、`workflow/definition`（画布 + 多标签，非列表形态）。

| 日期 | 变更 | 验证 |
| --- | --- | --- |
| 2026-10-19 | 新增 `ListPage` / `useListQuery` / `types.ts`；流程实例页切换到新骨架 | `vue-tsc -b` 通过；dev 编译 200；浏览器回归 **10/10**（表头/分页/筛选/状态标签/按钮可见性/重置/无权限拦截） |
| 2026-10-19 | 公告管理页迁移 | `vue-tsc -b` 通过；浏览器冒烟 **9/9**（表头/分页/新增按钮/有数据/筛选命中/无命中空态/重置恢复且清空条件/无 500） |
| 2026-10-19 | 角色管理页迁移 | `vue-tsc -b` 通过；浏览器冒烟 **10/10**（表头/分页/新增按钮/有数据/行内四按钮齐全/筛选命中/无命中空态/重置恢复且清空条件/无 500） |
| 2026-10-19 | 系统字典右栏迁移 | `vue-tsc -b` 通过；浏览器冒烟 **8/8**（类型列表/右栏表头/分页/默认类型有数据/切换类型重载/无命中空态/清空恢复/无 500） |
| 2026-10-19 | 登录日志页迁移 | `vue-tsc -b` 通过；浏览器冒烟 **9/9**（表头/分页/工具栏/有数据/账号筛选/无命中空态/重置恢复且清空/无 500） |
| 2026-10-19 | 操作日志页迁移 | `vue-tsc -b` 通过；浏览器冒烟 **10/10**（同上 + 「查看」详情抽屉） |
| 2026-10-19 | 员工管理页迁移 | `vue-tsc -b` 通过；浏览器冒烟 **10/10**（表头/分页/工具栏/有数据/行内三按钮/关键字筛选命中/无命中空态/重置恢复且清空/无 500） |
