# Element Plus 接入说明

本版本已在现有 Vue 3 + Vite 项目中完成 Element Plus 的全局接入，并启用中文语言包。

## 已完成

- 在 `package.json` 中加入 `element-plus` 依赖。
- 在 `src/main.js` 中注册 Element Plus、中文语言包与默认样式。
- 将“车辆管理”页渐进式迁移为 Element Plus 组件：输入框、下拉选择、按钮、表格、标签、消息提示和详情弹窗。
- 保留其余页面的原有实现、整体视觉样式、高德地图和实时模拟逻辑。
- 生成 `pnpm-lock.yaml`，便于稳定安装依赖。

## 运行

```bash
pnpm install
pnpm dev
```

也可使用 npm：

```bash
npm install
npm run dev
```

真实地图仍需按照 `.env.example` 配置高德地图 Key 与安全密钥。

## 后续迁移建议

后续新增或大改页面时，优先采用 `el-table`、`el-form`、`el-dialog`、`el-select`、`el-pagination` 等组件；现有稳定页面无需一次性重写。
