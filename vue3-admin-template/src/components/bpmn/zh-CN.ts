import translations from 'bpmn-js-i18n/translations/zn.js'

/**
 * bpmn-js 界面汉化模块。
 *
 * <p>bpmn-js 的界面文案（调色板工具提示、右键菜单、属性面板标签、校验消息）全部经由
 * 注入的 {@code translate} 服务输出。该服务默认只做 <code>{placeholder}</code> 插值、
 * 不做翻译，因此这里用<b>同名模块覆盖</b>它：didi 解析
 * {@code translate: ['value', fn]} 时后注册者优先生效，故放在
 * {@code additionalModules} 里即可生效。</p>
 *
 * <p>词条来自社区包 {@code bpmn-js-i18n} 的 {@code zn.js}（官方收录的中文翻译）。</p>
 */

/** 占位符替换表 */
type Replacements = Record<string, string>

/**
 * didi 模块声明的最小形状。
 *
 * <p>bpmn-js 的 {@code additionalModules} 类型是 didi 的 {@code ModuleDeclaration}，
 * 它带索引签名；此处只声明实际用到的 {@code translate} 字段会因缺少索引签名而无法赋值，
 * 故显式声明为索引兼容类型。</p>
 */
export interface BpmnI18nModule {
  [key: string]: unknown
  translate: ['value', (template: string, replacements?: Replacements) => string]
}

/**
 * 生成 translate 实现。
 *
 * <p>未收录的原文<b>原样返回</b>而非回退成 key —— bpmn-js 有些地方会把
 * 「占位符模板」当 key 传入，回退成 key 会把界面搞成
 * <code>{elementName}</code> 这类难懂的形态。</p>
 */
function translate(template: string, replacements?: Replacements): string {
  if (typeof template !== 'string' || !template) {
    return ''
  }
  const dict = { ...(translations as Record<string, string>), ...PANEL_EXTRA_TRANSLATIONS }
  const text = dict[template] ?? template
  const map = replacements ?? {}
  return text.replace(/{([^}]+)}/g, (_match, key: string) =>
    Object.prototype.hasOwnProperty.call(map, key) ? map[key] : '{' + key + '}',
  )
}

/**
 * 属性面板（bpmn-js-properties-panel）专用词条。
 *
 * <p>基础 provider 的组/字段标签同样走 {@code translate} 服务（源码逐行核实过：
 * {@code label: translate('General')}），因此在这里补词条即可汉化，无需改任何库代码。
 * 未收录的词条按既有策略原样返回（回退英文），不影响功能。</p>
 *
 * <p>词条键与库源码严格一致（含句末标点——ValidationUtil 的校验消息带句点），
 * 键对不上就会静默回退英文，所以增删词条时要对照源码。</p>
 */
const PANEL_EXTRA_TRANSLATIONS: Record<string, string> = {
  // 基础 provider：分组与字段
  General: '通用',
  Name: '名称',
  ID: '标识',
  'Participant ID': '参与者标识',
  'Element documentation': '元素说明',
  'Process documentation': '流程说明',
  'Multi-instance': '多实例',
  'Loop cardinality': '循环基数',
  'Completion condition': '完成条件',
  // 流程级属性统一在「节点基本」标签页编辑，面板不重复承载；词条仍收录兜底
  Process: '流程',
  Executable: '可执行',
  // 基础库 UI 文案
  '<empty>': '<空>',
  Create: '新建',
  'Create new list item': '新建列表项',
  'Delete item': '删除项',
  'Open documentation': '打开说明',
  'Open pop-up editor': '打开弹出编辑器',
  'Opened in editor': '已在编辑器中打开',
  'Section contains a warning': '该分组包含警告',
  'Section contains an error': '该分组包含错误',
  'Section contains edits': '该分组有未保存的编辑',
  'Toggle list item': '展开或收起列表项',
  'Toggle section': '展开或收起分组',
  // 标识合法性校验消息（ValidationUtil，句末标点是源码原文）
  'ID must not be empty.': '标识不能为空',
  'ID must be unique.': '标识已被占用，必须唯一',
  'ID must not contain spaces.': '标识不能包含空格',
  'ID must not contain prefix.': '标识不能包含命名空间前缀（如 foo:bar）',
  'ID must be a valid QName.': '标识必须是合法的 QName',
}

const zhCN: BpmnI18nModule = {
  translate: ['value', translate],
}

export default zhCN
