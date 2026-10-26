// ESLint 扁平配置（ESLint 10 + typescript-eslint 8 + eslint-plugin-vue 10）
//
// 定位：**只拦正确性问题，不管代码风格**。
// 理由：风格类规则（缩进、属性换行、自闭合）会产生上千条噪声，
// 把它接进 `npm run build` 只会逼人加 `eslint-disable`，门禁等于没有。
// 因此这里把 vue 的排版类规则全部关闭，只保留「会出 bug」的规则。
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'dist_probe/**',
      'node_modules/**',
      'coverage/**',
      'public/**',
      // 第三方设计器定制代码（FormCreate/BPMN），规则不同且不属本项目维护范围
      'src/components/bpmn/**',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      // ---- 排版/风格：交给编辑器与人工，不进 CI ----
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/html-closing-bracket-spacing': 'off',
      'vue/attributes-order': 'off',
      'vue/first-attribute-linebreak': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/v-on-event-hyphenation': 'off',
      'vue/order-in-components': 'off',
      'vue/require-default-prop': 'off',
      // 路由级页面统一命名为 index.vue（views/xxx/index.vue），多词组件名规则不适用
      'vue/multi-word-component-names': 'off',
      // `interface XxxQuery extends BaseQuery {}` 是本项目声明同形查询类型的惯用写法
      '@typescript-eslint/no-empty-object-type': 'off',
      'vue/require-explicit-emits': 'warn',

      // ---- 正确性问题：保留 ----
      'no-debugger': 'error',
      'no-dupe-keys': 'error',
      'no-unreachable': 'error',
      'no-fallthrough': 'error',
      'no-self-assign': 'error',
      'no-constant-condition': ['error', { checkLoops: false }],
      'vue/no-mutating-props': 'error',
      'vue/no-dupe-keys': 'error',
      'vue/no-parsing-error': 'error',
      // 存量 4 处（FormRender 3 处、BranchConditionEditor 1 处）属第三方设计器集成代码，
      // 改动有回归风险，先降为警告并登记，待专项处理后再提回 error
      'vue/no-side-effects-in-computed-properties': 'warn',
      'vue/no-unused-components': 'warn',
      'vue/no-v-html': 'warn',

      // ---- 团队约定：控制台与 any 只提示不阻断 ----
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-expressions': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_',
      }],
      // TS 已经做了未定义检查，避免与 typescript-eslint 重复报错
      'no-undef': 'off',
      'no-unused-vars': 'off',
    },
  },
  {
    // 只被工具链加载的脚本，允许 console
    files: ['**/*.config.*', 'scripts/**/*.mjs'],
    rules: { 'no-console': 'off' },
  },
)
