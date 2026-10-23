import type { Component } from 'vue'
import {
  Activity,
  Archive,
  ArrowLeft,
  ArrowRight,
  Ban,
  Bell,
  Briefcase,
  Building2,
  CalendarCheck,
  CalendarDays,
  Camera,
  ChartColumn,
  Check,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  CircleCheck,
  Clock,
  Copy,
  Download,
  Eye,
  FileText,
  FormInput,
  House,
  IdCard,
  ImagePlus,
  KeyRound,
  LayoutTemplate,
  Library,
  Lock,
  LogIn,
  LogOut,
  MailCheck,
  Menu as MenuIcon,
  OctagonX,
  Pause,
  PenTool,
  Pin,
  Power,
  Rocket,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  PenLine,
  Plus,
  Receipt,
  RotateCcw,
  Save,
  Search,
  Send,
  Settings,
  Share2,
  ShieldCheck,
  Ticket,
  Trash2,
  Upload,
  Users,
  User,
  UserRound,
  Workflow,
  X,
} from '@lucide/vue'

/**
 * 项目统一图标注册表（Lucide Icons）。
 *
 * <p><b>为什么集中注册</b>：菜单图标名存在数据库里（`oa_menu.icon`），运行时按名字解析。
 * 若各处直接 `import { X } from '@lucide/vue'`，名字就散落在模板里、也无法兼容历史数据；
 * 集中成一张表后，新增图标只需在此登记一次，数据库与设计器都能立即用上。</p>
 *
 * <p><b>按需引入</b>：只显式 import 用到的图标，打包器可 tree-shake，
 * 不会把 Lucide 全量（上千个）打进产物。</p>
 */
export const LUCIDE_ICONS: Record<string, Component> = {
  Activity,
  CalendarDays,
  Receipt,
  Archive,
  ArrowLeft,
  ArrowRight,
  Ban,
  Bell,
  Briefcase,
  Building2,
  CalendarCheck,
  Camera,
  ChartColumn,
  Check,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  CircleCheck,
  Clock,
  Copy,
  Download,
  Eye,
  FileText,
  FormInput,
  House,
  IdCard,
  ImagePlus,
  KeyRound,
  LayoutTemplate,
  Library,
  Lock,
  LogIn,
  LogOut,
  MailCheck,
  Menu: MenuIcon,
  OctagonX,
  Pause,
  PenTool,
  Pin,
  Power,
  Rocket,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  PenLine,
  Plus,
  RotateCcw,
  Save,
  Search,
  Send,
  Settings,
  Share2,
  ShieldCheck,
  Ticket,
  Trash2,
  Upload,
  User,
  UserRound,
  Users,
  Workflow,
  X,
}

/**
 * 历史 Element Plus 图标名 → Lucide 名。
 *
 * <p>库里存量数据仍是 EP 名（Document/HomeFilled…）。保留这层映射，
 * 保证「先把前端切到 Lucide、再改库」的过渡期图标不会消失；
 * 库改完后此表仍可长期兜底（自定义模板/旧导出数据）。</p>
 */
export const LEGACY_EP_ICONS: Record<string, string> = {
  Document: 'FileText',
  EditPen: 'PenLine',
  Bell: 'Bell',
  Tickets: 'Ticket',
  HomeFilled: 'House',
  Setting: 'Settings',
  User: 'User',
  UserFilled: 'UserRound',
  Menu: 'Menu',
  OfficeBuilding: 'Building2',
  Promotion: 'Send',
  Share: 'Share2',
  Clock: 'Clock',
  Select: 'CircleCheck',
  Collection: 'Library',
  Key: 'KeyRound',
  ArrowRight: 'ArrowRight',
  Expand: 'PanelLeftOpen',
  Fold: 'PanelLeftClose',
}

/** 可供选择器展示的图标名（保持字典序，便于查找） */
export const LUCIDE_ICON_NAMES: string[] = Object.keys(LUCIDE_ICONS).sort()

/**
 * 按名字解析图标组件：先查 Lucide 名，再回落到历史 EP 映射。
 *
 * @param name 图标名（新名或旧 EP 名均可）
 * @returns 组件；名字为空或无法识别时返回 undefined（调用方应据此不渲染，避免出现空占位）
 */
export function resolveIcon(name?: string | null): Component | undefined {
  if (!name) {
    return undefined
  }
  const direct = LUCIDE_ICONS[name]
  if (direct) {
    return direct
  }
  const legacy = LEGACY_EP_ICONS[name]
  return legacy ? LUCIDE_ICONS[legacy] : undefined
}
