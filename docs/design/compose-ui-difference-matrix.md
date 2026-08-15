# TidePlayer Compose UI 差异矩阵

审计日期：2026-07-25。设计证据按优先级来自 `Design/src/app/App.tsx`、
`Design/src/imports/pasted_text/design-system-v3.md` 与
`docs/design/design-ui-implementation.md`；生产代码仅作为当前实现证据。

| 设计元素 | 现有 Compose 文件 | 已确认差异 | 优先级 | 验收方式 |
| --- | --- | --- | --- | --- |
| 四个根入口与播放器入口 | `shared/.../navigation/HomeTab.kt`、`HomePage.kt`、`RootNavHost.kt` | 已修复：仅 Home、Search、Library、Settings 为根入口；移除了播放后自动跳转、Library 和播放列表的直达路径。唯一的 `MusicGraph.NowPlaying` 导航由 `PlaybackMiniPlayerHost` 的点击回调提供。 | 已完成 | 静态导航审查；当前工作树的 390×844 iOS 构建已在浅/深主题逐一切换四个入口，并确认 Mini Player 持续存在。桌面真实曲库已从 Mini Player 进入 Now Playing，入口唯一性另由导航测试覆盖。 |
| 紧凑底部导航 | `widgets/appbar/BottomBar.kt`、`core/.../PlayerChromeComponents.kt` | 已修复：62dp 高度、1dp 分隔线、48×28dp 指示器、20dp 图标和 10sp 标签均由 `DesignTokens.navigation` 提供；每个入口还导出名称、`Role.Tab` 和选中状态。 | 已完成 | token 单元测试与源码尺寸检查；390×844 浅/深主题实际截图检查四个选中态及点击面。 |
| 自适应应用壳与 Mini Player | `navigation/HomePage.kt`、`RootNavHost.kt`、`widgets/appbar/NavigationRailBar.kt`、`SidebarBar.kt` | 已修复：根壳保持 <600 / 600–1279 / ≥1280；`RootNavHost` 持有四个根 Tab 的状态。Home 使用同一状态渲染根壳；非 Home、非 Now Playing 二级路由复用底部导航/导航栏/桌面侧边栏及同一 `PlaybackMiniPlayerHost`，所以桌面播放器位于侧边栏右侧的主内容区底部。三种根导航均导出 Tab 名称与选中状态；Mini Player 主体导出按钮角色和本地化标签。 | 已完成 | 390×844 iOS 为底部导航；840dp、1008dp 桌面窗口为导航栏；1400dp 桌面窗口为侧边栏。三档均实际检查，浅/深主题均有运行证据；桌面播放器位于侧栏/导航栏右侧的主内容区底部。二级页壳层由导航测试覆盖，运行时只抽查外观设置页。 |
| 共享 tokens 与页面收起栏 | `theme/Theme.kt`、`components/DesignGlassComponents.kt`、`DesignPageHeader.kt`、`BottomBarSpacer.kt` | 已修复：导航和 Mini Player 关键尺寸归入 tokens；所有底部空间复用 `compactMiniBarHeight + spacing.xs`；粘性标题栏为 58dp，Home 收起距离为 48dp。 | 已完成 | `DesignTokenTest` 固定断言 48dp/58dp；390×844 iOS 真机模拟器以 797 首真实曲目滚动后已显示收起栏，底部内容未被播放器/导航覆盖。 |
| 可点击共享组件 | `components/DesignCardSurface.kt`、`DesignTabs.kt`、`DesignSearchBar.kt`、`IconButton.kt`、`TextButton.kt`、`DesignButton.kt`、`Checkbox.kt`、`DesignSwitch.kt`、`ContextMenu.kt`、`ImportCover.kt`、`DesignSettingsComponents.kt` | 已修复：卡片、Tabs、搜索清除、图标按钮、文本按钮、主按钮、复选框、开关、Mini Player 控制、封面删除和菜单项在组件表面允许时均使用 48dp 最小触控面；设置行已为 68dp。 | 已完成 | 对所有经共享组件承载的操作，在不受固定视觉尺寸限制时测量 ≥48dp。 |
| Home 真实数据与播放 | `feature/home/.../HomeState.kt`、`HomeViewModel.kt`、`HomeDesignScreen.kt`、`HomeRoot.kt` | 已修复为安全子集：默认状态为空，Home 组合 `LibraryRepository` 的曲目/专辑/艺术家及 `PlaylistRepository` 摘要；点击真实曲目建立真实播放队列，不再使用设计稿示例封面或曲目。 | 已完成 | 无库时只显示可操作空状态；有库时只显示 Repository 数据，点击曲目由 `PlaybackController` 开始播放。 |
| Home 的置顶、历史、听歌统计 | 同上；`core/domain/.../LibraryRepository.kt`、`PlaylistRepository.kt` | 当前领域契约只提供曲目、专辑、艺术家与播放列表摘要；没有置顶、播放历史、听歌统计的真实数据。按要求不能用设计稿演示数据补齐。 | 阻塞该子项 | 需要产品决定：新增持久化业务契约后显示这些区块，或在生产 Home 中隐藏/以明确空状态替代。 |
| Search | `feature/search/.../SearchDesignScreen.kt`、`SearchViewModel.kt`、`SearchRoot.kt`、`MusicSourceSearchAggregator.kt`、`shared/.../RoomSearchRepository.kt` | 已修复：生产页只展示真实 `tracks`、`history`、`suggestions`。启用账户才进入音源聚合；每个账户独立并发、8 秒超时后转为局部 `Timeout`，不丢弃其他账户及 Room 结果。Room 结果从启用/可用/可播放的 `track_source_ref` 批量取真实 `MediaId`；已有 `trackId` 的结果进入既有 Library 合成播放列表，仍不直达 Now Playing。 | 已完成，有明确取舍 | 聚合器超时/部分成功、禁用账户过滤、Room→`MediaId` 与 Search→`PlayableItem` 均有测试。远程 source-only 结果保留真实下载 `MediaId`，但当前播放契约只接受音乐库 `trackId`；UI 明确提示先加入音乐库，不再静默无效。 |
| Search 的专辑/艺术家结果 | 同上；`feature/search/domain/SearchTrackItem.kt` | 设计稿含专辑/艺术家筛选，但现有搜索领域契约只提供曲目结果。为避免伪造结果，本轮隐藏了这些筛选与卡片。 | 阻塞该子项 | 需要新增真实专辑/艺术家搜索结果及导航契约后再实现。 |
| Library | `feature/library/.../LibraryDesignScreen.kt`、`LibraryVM.kt`、`LibraryRoot.kt` | 已修复：Library 内部桌面侧栏阈值从 1024dp 对齐为 1280dp，840–1279dp 仅保留根导航栏；真实专辑、艺术家与播放列表摘要可进入既有二级页；播放后保留在页面，由 Mini Player 进入 Now Playing。固定文件夹、流派、播放列表以及任意曲库切片伪装的收藏/历史/最近项已移除；侧栏、紧凑分类、更多面板、分类操作、排序及空状态操作均改为 48dp 最小点击面。 | 已完成，有范围限制 | 840–1279 仅根导航栏；≥1280 可显示桌面 Library 侧栏；空库导入入口可达；真实专辑、艺术家、播放列表可导航；上述可点击面测量 ≥48dp。 |
| Library 的流派、文件夹、收藏、历史、最近、无损/Hi-Res、下载集合 | 同上；`core/domain/.../LibraryRepository.kt`、下载领域状态 | 这些设计集合缺少对应的真实领域状态。文件夹显示真实导入入口；其余不再以 `tracks.take(...)` 等方式伪造集合，改为明确空状态。 | 阻塞该子项 | 为每个集合提供真实 Repository 状态、排序/筛选语义与导航后再展示内容。 |
| Settings 与设置子页 | `feature/settings/.../SettingsScreen.kt`、`PlaybackSettingsScreen.kt`、`SettingsRows.kt` | 分组卡片、状态、禁用态与真实设置状态已存在。旧“播放时自动打开播放器”与 Mini Player-only 规则冲突，持久化字段保持兼容，设置页改为不可交互的 Mini Player 入口说明。 | 已完成，有取舍 | 搜索、开关、禁用、长中英文、所有子页返回。 |
| 专辑、艺术家、播放列表、音源、导入、下载、队列、歌词、正在播放 | 对应 `feature/*/presentation` 与 `service/playback/presentation` | 已补上 Library → 真实专辑/艺术家/播放列表详情的二级路由；播放列表列表及创建、编辑、导入继续复用既有 ViewModel/Repository。播放列表、专辑与艺术家的播放后直达路径均已移除。其余页面路由、真实状态和共享卡片/标题沿用既有实现；静态审计没有确认额外的安全视觉差异。 | 静态完成；运行时部分覆盖 | 390×844 隔离模拟器已用真实 797 首库检查 Library→Songs：数量、时长、中英文曲名、长艺人、Shuffle/Play all、选中根导航和无播放 Mini Player 均来自 Repository。空播放列表页也已以真实空状态检查；其他二级页完整空/加载/错误状态仍需逐页验收。 |

## 实施约束与取舍

- 不修改 `Design/`：审计仅读取设计源文件，不向该目录写入生产实现改动。
- 不修改音乐库、音源、下载、播放或插件的领域契约。
- Home 的置顶、历史和统计缺少真实来源；在未获得新增业务契约授权前，不实现这些设计区块的“有数据”版本，也不保留任何演示音乐数据。
- Search 的专辑/艺术家结果以及 Library 的流派、文件夹列表、收藏、历史、最近、无损/Hi-Res、下载集合也缺少真实来源；本轮保留可用导入/管理入口，并用明确空状态替代设计稿样例。若产品要展示它们，需要先扩展领域契约，而不是在 UI 层推断或切片伪造。
- Search 聚合器当前会把 `StorageSearchSourceAccountProvider` 返回的全部账户交给 `awaitAll()`，未过滤禁用账户也没有单源超时；正式桌面库因此可持续停留在加载态。隔离 QA 数据中让账户 sourceId 不可解析后，本地 Room 真实结果可立即渲染，证明 UI 状态映射正常。建议在独立业务任务中明确禁用账户语义及超时/部分失败策略。
- Room 搜索结果通过 `LegacyStorageLookup` 映射不到 `mediaId` 时，`SearchViewModel` 不会调用播放，只发送当前页面未呈现的 `ShowMessage`。本轮没有伪造 mediaId 或修改播放契约；建议后续统一 Library Track → PlayableItem 映射，并为不可播放结果提供用户可见反馈。
- 当前工作树已安装到独立的 iPhone 14 模拟器（390×844@3x），首装数据库为空；所有移动截图均来自该构建，不使用旧模拟器中的设计演示数据。
- 根导航现显式导出名称、`Role.Tab` 和选中状态，Mini Player 主体导出 `Role.Button` 及本地化标签；自动化宿主无法可靠执行 VoiceOver/TalkBack 朗读，焦点顺序、朗读文本和动态状态仍需人工设备验收。

## 当前验证记录

- 通过：`./gradlew :core:presentation:desktopTest :feature:home:desktopTest :feature:search:desktopTest :feature:library:desktopTest :feature:settings:desktopTest :feature:album:desktopTest :feature:artist:desktopTest :feature:playlist:desktopTest :feature:sources:desktopTest :feature:importing:desktopTest :feature:downloads:desktopTest :feature:queue:desktopTest :feature:lyrics:desktopTest :service:playback:presentation:desktopTest :shared:desktopTest :desktopApp:compileKotlinDesktop`。
- 通过：Android Studio JBR 21 下运行 `:desktopApp:run`；实际检查 840dp、1008dp、1400dp 窗口，覆盖浅/深主题。840/1008 使用导航栏，1400 使用 224dp 侧边栏，播放器均位于主内容区底部。
- 通过：`xcodebuild` 针对 iPhone 14 / iOS 26.4 Simulator 构建当前工作树；产物安装到全新 390×844 模拟器。Home、Search、Library、Settings 在浅/深主题均实际截图，外观与语言子页在深色下抽查；Search/Library/Home 均显示真实空状态，无设计演示音乐数据。
- 通过：桌面真实曲库播放“180度”后，Mini Player 显示真实封面、标题、进度、暂停/下一首；仅点击 Mini Player 主体进入 Now Playing。840dp 下由 Now Playing 打开包含 797 首真实曲目的 Queue，并检查 History、Continue playing、当前选中态和切换曲目。
- 通过：隔离的数据库副本中禁用 source 路由后，Search 对真实 Room 曲目“告白气球”返回 1 条结果，状态从加载正确切换为结果；该临时 QA 操作未修改仓库代码或用户数据库。
- 通过：根导航与 Mini Player 的辅助功能语义补齐后，`:core:presentation:desktopTest`、`:service:playback:presentation:desktopTest`、`:shared:desktopTest` 和 `:desktopApp:compileKotlinDesktop` 均通过。
- 通过：`git diff --check`。
- 已知运行时风险：桌面长时间运行时，既有 WebDAV 后端在 `request_xml_with_retry` 调用 Tokio 定时器时触发 Rust panic 并以 `SIGABRT` 退出。该问题位于音源业务实现，不是本轮 UI 差异；按“不改动音源业务契约”约束未修复。
- 未完成：TalkBack/VoiceOver 人工朗读；Home 48dp 临界滚动动画；歌词页及其余二级页的完整真实加载/错误/播放状态逐页截图。
