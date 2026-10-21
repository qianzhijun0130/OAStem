/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_APP_ENV: string
  readonly VITE_APP_BASE_API: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/**
 * bpmn-js-i18n 未随包提供类型声明。
 *
 * <p>该包只有各语言的词条文件（`translations/zn.js` 等），没有 `types` 入口，
 * TS 会隐式判为 any。词条本身是纯 `Record<string, string>`，此处显式声明。</p>
 */
declare module 'bpmn-js-i18n/translations/*' {
  const translations: Record<string, string>
  export default translations
}

/**
 * bpmn-io 官方属性面板基础库（@bpmn-io/properties-panel@3.56.1）无 TS 类型。
 *
 * <p>该库是 Preact 实现，入口只发 `dist/index.esm.js`。这里按「本仓库实际用到
 * 的导出」做最小声明：条目工厂（TextFieldEntry/TextAreaEntry/DescriptionEntry）、
 * 分组组件（Group）与条目/分组定义形状。entry 是函数调用而非 JSX，因此
 * {@link EntryComponent} 只需声明为「接收 props 返回渲染结果」的函数。</p>
 */
declare module '@bpmn-io/properties-panel' {
  import type { Element } from 'diagram-js/lib/model/Types'

  /** 条目组件的渲染入参（面板渲染时注入 element 与 entry id） */
  export interface EntryProps {
    element: Element
    id: string
  }

  /** 条目组件：Preact 函数组件 */
  export type EntryComponent = (props: EntryProps) => unknown

  /** 面板条目定义（component 为条目组件，isEdited 控制「有未保存编辑」圆点） */
  export interface EntryDefinition {
    id: string
    component: EntryComponent
    isEdited?: (element: Element, entry: EntryDefinition) => boolean
  }

  /** 面板分组定义 */
  export interface GroupDefinition {
    id: string
    label: string
    component: unknown
    entries: EntryDefinition[]
  }

  export interface TextFieldEntryProps {
    element: Element
    id: string
    label: string
    description?: string
    getValue: (element: Element) => string | undefined
    setValue: (value: string | undefined, error?: string) => void
    disabled?: boolean
    debounce?: unknown
    validate?: (value: string) => string | undefined
  }
  export function TextFieldEntry(props: TextFieldEntryProps): EntryComponent
  export function isTextFieldEntryEdited(element: Element, entry: EntryDefinition): boolean

  export interface TextAreaEntryProps {
    element: Element
    id: string
    label: string
    description?: string
    getValue: (element: Element) => string | undefined
    setValue: (value: string | undefined, error?: string) => void
    disabled?: boolean
    debounce?: unknown
    monospace?: boolean
    rows?: number
    validate?: (value: string) => string | undefined
  }
  export function TextAreaEntry(props: TextAreaEntryProps): EntryComponent
  export function isTextAreaEntryEdited(element: Element, entry: EntryDefinition): boolean

  /** 说明行（无标签的纯文本，用于给条目补提示） */
  export interface DescriptionEntryProps {
    element: Element
    forId: string
    value?: string
  }
  export function DescriptionEntry(props: DescriptionEntryProps): EntryComponent

  /** 分组渲染组件（官方 provider 的 component: Group 即此物） */
  export const Group: unknown
}

/**
 * bpmn-js 官方属性面板适配层（bpmn-js-properties-panel@5.65.2）无 TS 类型。
 *
 * <p>只声明本仓库实际使用的三个导出：面板挂载模块、官方 provider 模块、
 * 以及条目组件内取 didi 服务的 useService 钩子。</p>
 */
declare module 'bpmn-js-properties-panel' {
  /** didi 模块（additionalModules 的元素） */
  export const BpmnPropertiesPanelModule: Record<string, unknown>
  export const BpmnPropertiesProviderModule: Record<string, unknown>

  /** 在条目组件内按名取 bpmn-js 服务（commandStack/bpmnFactory/eventBus 等） */
  export function useService<T = unknown>(name: string): T
}
