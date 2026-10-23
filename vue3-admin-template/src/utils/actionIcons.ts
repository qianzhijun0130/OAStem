/**
 * 「操作文字 → Lucide 图标名」映射。
 *
 * <p>按钮文案是业务语言（新增/编辑/删除…），图标是视觉语言；把映射集中在这里，
 * 全站 160+ 个按钮就能自动获得同一套图标，不必逐个按钮 import 组件。</p>
 *
 * <p><b>按顺序匹配，先命中先生效</b>：数组自上而下是「越具体越靠前」。
 * 因此「确认修改」命中先出现的「修改」→ 铅笔，而不是排在后面的「确定」→ 勾；
 * 「确定抄送」同理命中「抄送」→ 纸飞机。只有文案里没有更具体的动作词时，
 * 才算作单纯的确认动作（「确定」「确认」→ 勾）。</p>
 */
const RULES: Array<{ icon: string; keywords: string[] }> = [
  { icon: 'Plus', keywords: ['新增', '新建', '创建', '添加'] },
  { icon: 'PenTool', keywords: ['设计'] },
  { icon: 'Pencil', keywords: ['编辑', '修改'] },
  { icon: 'Rocket', keywords: ['发布'] },
  { icon: 'Archive', keywords: ['下架'] },
  { icon: 'Pin', keywords: ['置顶'] },
  { icon: 'Power', keywords: ['启用'] },
  { icon: 'Ban', keywords: ['停用', '禁用'] },
  { icon: 'Pause', keywords: ['挂起'] },
  { icon: 'OctagonX', keywords: ['终止'] },
  { icon: 'Trash2', keywords: ['删除', '移除'] },
  { icon: 'Save', keywords: ['保存'] },
  { icon: 'X', keywords: ['取消', '关闭'] },
  { icon: 'RotateCcw', keywords: ['重置', '刷新'] },
  { icon: 'Search', keywords: ['查询', '搜索', '筛选'] },
  { icon: 'Eye', keywords: ['详情', '查看', '预览'] },
  { icon: 'Download', keywords: ['导出', '下载'] },
  { icon: 'Upload', keywords: ['导入', '上传'] },
  { icon: 'CheckCheck', keywords: ['标记已读', '全部已读', '办理'] },
  { icon: 'Send', keywords: ['抄送', '发送', '提交'] },
  { icon: 'Copy', keywords: ['复制', '克隆'] },
  { icon: 'ShieldCheck', keywords: ['分配', '授权'] },
  { icon: 'ChevronsUpDown', keywords: ['展开', '收起'] },
  { icon: 'LogIn', keywords: ['登录', '登陆'] },
  { icon: 'Check', keywords: ['确定', '确认', '同意', '通过'] },
  { icon: 'X', keywords: ['返回', '退出'] },
]

/**
 * 根据按钮文字挑选图标。
 *
 * @param text 按钮文案（已去空白）
 * @returns Lucide 图标名；无法识别时返回 undefined（按钮保持纯文字）
 */
export function actionIcon(text: string): string | undefined {
  if (!text) {
    return undefined
  }
  // 按钮文案常用「登 录」这类字间加空格做视觉留白，匹配前先去掉所有空白
  const compact = text.replace(/\s+/g, '')
  const hit = RULES.find((rule) => rule.keywords.some((kw) => compact.includes(kw)))
  return hit?.icon
}
