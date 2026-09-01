# 登录接口 404 修复说明

本版修复了登录页出现 `/api/v1/auth/login`、`/api/v1/users/me`、`/api/v1/notifications/unread-count` 404 的问题。

## 原因

之前发布的精简包缺少 `.env.local`，导致 `VITE_API_PROXY_TARGET` 未加载，Vite 没有创建 `/api` 开发代理。浏览器因此把 `/api/v1/**` 请求发给了前端开发服务器自身，从而返回 404。

## 本版修复

1. 恢复项目原有 `.env.local` 联调配置。
2. `vite.config.js` 增加默认业务后端代理地址，避免环境文件缺失时再次直接请求 Vite 自身。
3. 登录/注册页不再提前请求未读消息接口；只有存在有效登录 Token 且进入业务页后才刷新通知数量。
4. API 请求遇到 404 时给出明确的“接口/代理配置”提示，而不是误提示为账号密码错误。

## 使用

修改 Vite 配置或环境变量后必须重启开发服务器：

```bash
npm run dev
```

如果浏览器曾保存旧 Token，可先退出登录，或清除站点 Local Storage 后重新登录。
