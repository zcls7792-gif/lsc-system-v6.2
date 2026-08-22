# lsc-mobile-app 构建说明

> **创建日期**：2026-08-22
> **依据**：审计改进项 I-04（见 `LSC_V6.2_Reports/LSC_V6.2_Code_Quality_Completeness_Audit_20260822.md`）

## 关于 package-lock.json

本工程此前缺失 `package-lock.json`，与其他前端工程（`lsc-admin-web`、`lsc-merchant-web` 均含 lock 文件）不一致，影响可重现构建。

已新增 `.npmrc` 启用 `package-lock=true`。

### 生成 package-lock.json（需在本地或 CI 环境执行）

```bash
cd lsc-mobile-app
npm install
```

执行后将生成 `package-lock.json`，请将其一并提交到仓库。

### 为什么不直接提交 lock 文件

`package-lock.json` 由 npm 根据当前 `package.json` 与 npm registry 实时解析生成，需要在能访问 npm registry 的环境（本地或 CI）中执行 `npm install` 才能生成准确内容。手动构造的 lock 文件会与实际依赖树不一致，反而破坏可重现构建。

## CI/CD 集成

新增的 `.github/workflows/test.yml` 已覆盖前端工程的标准化构建检查。建议在前端单独的 CI 步骤中加入：

```yaml
- name: Install frontend deps
  working-directory: lsc-mobile-app
  run: npm ci  # npm ci 要求 package-lock.json 存在，会校验 lock 与 package.json 一致性
```

`npm ci` 比 `npm install` 更严格（要求 lock 文件存在且与 package.json 一致），适合 CI 环境。
