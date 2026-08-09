# HMI 中英文国际化审计

## 范围

运行时 HMI 文本包括页面标题、导航、按钮、菜单、空状态、错误提示、Toast、通知、对话框、表单标签、无障碍描述和设置说明。音乐标题、艺术家、专辑、文件路径、服务器名称及插件返回内容属于用户或外部内容，不应翻译。

## 资源规范

- Compose Multiplatform 默认英文：`composeResources/values/strings.xml`
- Compose Multiplatform 简体中文：`composeResources/values-zh/strings.xml`
- Android 默认英文：`res/values/strings.xml`
- Android 简体中文：`res/values-zh-rCN/strings.xml`
- 两种语言必须具有相同的 `string` / `plurals` 名称、资源类型和格式占位符。
- Kotlin 运行时代码不得直接写界面文案；应使用模块自己的 `Res.string.*` 与 `stringResource(...)`。
- 当前产品品牌名 `TidePlayer`、协议名和音频术语可保持原文，例如 TidePlayer、WebDAV、SMB、OAuth、DSP、ReplayGain。
- `MelodyTrove` 和 `TideTunes` 只应出现在历史兼容、迁移说明或明确需要识别旧数据的文本中，不应作为当前 HMI 品牌名。

## 本次发现与修复

资源键缺失会使中文环境直接回退到英文。本次补齐以下资源组：

- `shared/src/commonMain/composeResources/values-zh/strings.xml`
- `feature/search/src/commonMain/composeResources/values-zh/strings.xml`
- `feature/downloads/src/commonMain/composeResources/values-zh/strings.xml`
- `shared/src/androidMain/res/values-zh-rCN/strings.xml`
- `androidApp/src/main/res/values-zh-rCN/strings.xml` 中遗留的英文调试与根目录词条

新增 `scripts/audit-hmi-i18n.py`，检查所有资源组的缺失键、多余键、资源类型和格式占位符，并报告生产源码中的疑似硬编码 HMI 文本。

## 已识别的硬编码迁移重点

以下运行时文件仍包含明显的英文 HMI 文本，应按页面拆分资源并逐步清零：

1. `feature/library/.../LibraryScreen.kt`：分类、统计、按钮、状态、空状态、无障碍描述。
2. `feature/artist/.../ArtistScreen.kt`：加载、错误、重试、专辑与歌曲计数、播放与下载按钮。
3. `shared/.../navigation/HomeTab.kt` 与 `widgets/appbar/*`：底部导航、侧栏、导航栏标题和副标题。
4. `feature/album`、`feature/browse`、`feature/radio`、`feature/onboarding`、`feature/recentlyadded`、`feature/recentlyplayed`：页面状态和操作文案。
5. `shared/.../plugin/management`：插件管理、手动元数据搜索和表单反馈。

运行：

```bash
python3 scripts/audit-hmi-i18n.py
python3 scripts/audit-hmi-i18n.py --strict-hardcoded
```

第一条命令严格校验资源一致性并报告硬编码；第二条命令会让硬编码结果也以非零状态退出，适合在完成存量迁移后接入 CI。
