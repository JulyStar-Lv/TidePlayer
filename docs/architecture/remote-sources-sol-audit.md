# Remote sources: Sol audit and execution plan

Audit date: 2026-08-17  
Audited revision: `a8ebd30b`  
Status vocabulary: `DONE`, `PARTIAL`, `BROKEN`, `MISSING`, `TECH_DEBT`

This document is the S0/S1 review artifact for completing Navidrome,
OpenSubsonic, Emby, and OpenList support. It describes the repository before
implementation work begins. A task is complete only after Luna implements it
and Sol reads the resulting diff and accepts the relevant tests.

## Repository audit

| Feature | Current status | Code location | Problem | Target state | Priority | Task |
| --- | --- | --- | --- | --- | --- | --- |
| Navidrome authentication | PARTIAL | `RemoteServerGatewayImpl.kt`, `remote_music.rs` | Standard Subsonic ping and salted MD5 exist, but errors are classified by string matching and the Rust response validator accepts malformed JSON. | Typed authentication result and protocol/HTTP/JSON error mapping, with credentials kept in `CredentialStore`. | P0 | T03 |
| Navidrome pagination | BROKEN | `RemoteServerGatewayImpl.kt`, `ServerMusicSource.kt` | Both layers enforce a 10,000-track ceiling; duplicate-page and ignored-offset handling are absent. | Bounded UI pages and an exhaustive sync pager with ID deduplication, loop detection, and album fallback. | P0 | T04 |
| Navidrome Library sync | MISSING | `RemoteLibraryImportCoordinator.kt`, `LibrarySyncController.kt` | Server browse results never enter the unified Room library. | Server songs upsert `SourceItem`, normalized metadata, and `TrackSourceRef`, with deleted-source reconciliation. | P0 | T05, T16 |
| Navidrome lyrics | MISSING | `ServerMusicSource.kt`, lyrics domain | `Lyrics` is advertised but no gateway operation or lyrics-chain integration exists. | Server-first lyrics with existing embedded/sidecar/plugin fallback and the existing lyrics UI. | P0 | T06 |
| Navidrome artwork | PARTIAL | `RemoteServerGatewayImpl.kt` | A fixed-size cover URL is created, but provider configuration and artwork-cache/library integration are absent. | Configurable `getCoverArt` size, cached through the existing artwork path. | P0 | T06 |
| Navidrome playback | PARTIAL | `RemoteServerGatewayImpl.kt`, `PlaybackResourceResolver.kt` | Playback resolves a URL, but provider identity is fragile and bitrate/expiry recovery are absent. | Dynamic `resolvePlayback`, configured stream bitrate, no persisted credential URL, finite expiry recovery. | P0 | T01, T06, T15 |
| Navidrome playlist | MISSING | source/server and playlist data | No server playlist operations or remote playlist identity exist. | Read support plus gated writes mapped to provider/account/remote playlist ID. | P1 | T08 |
| Navidrome favorite | MISSING | source/server, favorites data | Favorites are local-only. | `getStarred`/`star`/`unstar`, with backend enforcement of the remote-write switch. | P1 | T08 |
| Navidrome scrobble | MISSING | source/server | No scrobble operation exists. | Shared server action with explicit lifecycle calls and tested failure handling. | P1 | T08 |
| OpenSubsonic extensions | MISSING | `remote_music.rs`, provider config | No capability discovery or snapshot exists. | Detect extensions on connection, cache a non-secret snapshot, and permit refresh. | P0 | T07 |
| OpenSubsonic lyrics | MISSING | source/server, lyrics domain | No `getLyricsBySongId` support. | Prefer the extension when advertised and fall back to compatible Subsonic/existing sources. | P0 | T07 |
| OpenSubsonic structured lyrics | MISSING | lyrics domain and persistence | Network/domain models do not represent all structured lyric fields. | Preserve line/word timing, byte offsets, agent, language, main/translation/pronunciation through the existing lyrics pipeline. | P0 | T07 |
| OpenSubsonic playlist | MISSING | source/server and playlist data | No remote playlist calls. | Same collision-safe remote playlist model and write gate as Navidrome. | P1 | T08 |
| Emby login | PARTIAL | `StorageRepository.kt`, `remote_music.rs` | Login returns token/UserId, but ServerId/ServerName and typed errors are missing. | Parse full login identity, persist only non-secret server metadata, and classify 401 separately. | P0 | T09 |
| Emby token storage | DONE | `StorageRepository.kt`, platform `CredentialStore` implementations | AccessToken is stored by credential reference rather than in Room. It still needs regression/security tests as the implementation expands. | Preserve this boundary for every new Emby path. | P0 | T09, T19 |
| Emby pagination | BROKEN | `RemoteServerGatewayImpl.kt` | A 10,000 ceiling remains; response total/dedup/loop behavior is not robust. | Exhaustive `StartIndex` paging with stable-ID dedup and loop termination. | P0 | T10 |
| Emby metadata | PARTIAL | `RemoteServerGatewayImpl.kt` | Only a small field subset is requested/mapped. | Map all required item, album, genre, media-source, and audio-stream fields into unified models. | P0 | T10 |
| Emby artwork | MISSING | source/server | Cover URL exists only as a transient string; cache/library integration is absent. | Resolve Emby images and reuse the artwork cache. | P0 | T10 |
| Emby playback | PARTIAL | `RemoteServerGatewayImpl.kt`, players | A tokenized audio URL is built without playback negotiation, required headers, or expiry recovery. | Prefer Direct Play/Direct Stream, propagate required headers, and re-resolve finite times. | P0 | T11, T15 |
| OpenList auth | MISSING | no provider module | No account, guest, password, OTP, or session support exists. | Password in `CredentialStore`, token in memory, guest support, OTP challenge in memory, one-shot 401 re-login. | P0 | T02, T12 |
| OpenList browse | MISSING | no provider module | No OpenList directory source exists. | Real paged directory tree with Unicode-safe API requests and correct node types. | P0 | T13 |
| OpenList folder selection | MISSING | `feature/sources`, roots data | OpenList is absent from the existing directory/root workflow. | Reuse the unified folder picker and create one `LibraryRootEntity` per selected root. | P0 | T13, T18 |
| OpenList scan | MISSING | `RemoteLibraryImportCoordinator.kt` | No backend can enumerate OpenList. | Reuse the file-source scanner with snapshot/signature reconciliation; do not invent delta support. | P0 | T14 |
| OpenList metadata | MISSING | storage backend, remote metadata reader | No ranged OpenList file access. | Feed existing Rust metadata parsing through valid byte ranges without full-file downloads. | P0 | T14 |
| OpenList playback | MISSING | source/api, playback resolver | No provider or current-link resolution. | Resolve the latest link at playback time through `MusicSource.resolvePlayback`. | P0 | T15 |
| OpenList Range | MISSING | storage backend | No direct/proxy range validation. | Prefer direct range, then direct with headers, then proxy range; verify 206/Content-Range/seek. | P0 | T14, T15 |
| OpenList search | MISSING | no provider module | No search operation exists. | Search indexed unified-library data and expose live provider search only where it is truthful and paged. | P1 | T14, T16 |
| Database | PARTIAL | `SourceEntities.kt`, `AppDatabase.kt` | Source accounts/items/refs/cursors already exist, but remote playlists lack provider/account/remote identity. | Reuse the source schema; add a formal migration only for required playlist identity fields. | P0 | T08, T20 |
| Credential security | PARTIAL | platform `CredentialStore`, diagnostics redaction | Storage primitives are strong, but new flows and tokenized URLs need explicit non-persistence/non-logging tests. | All secrets remain behind credential refs or in-memory sessions; diagnostic and model output are redacted. | P0 | T12, T19 |
| PlaybackResource | PARTIAL | `MusicSource.kt`, `PlaybackResourceResolver.kt`, platform players | URI and headers are supported, but remote ID encoding and stale-resource recovery are incomplete. | Robust identity, full header propagation, MIME/expiry use, and finite re-resolution preserving player state. | P0 | T01, T11, T15 |
| LibrarySync | PARTIAL | `RemoteLibraryImportCoordinator.kt`, `LibrarySyncController.kt` | File-source snapshot reconciliation is mature, but server providers cannot use it. | Provider-appropriate pagers feed the same normalized Library ownership model. | P0 | T05, T14, T16 |
| UI | PARTIAL | `feature/sources` | Three servers have only basic fields; OpenList, advanced config, OTP, and working sync actions are absent. | One source editor/selector with provider-specific safe fields and existing root picker/sync actions. | P0 | T18 |
| Tests | PARTIAL | KMP and Rust tests | Basic registry/source/playback tests pass, but protocol mocks, 25k paging, incremental cases, migrations, and full platform compilation are absent. | Per-task tests plus the required large fixtures, migration tests, and Android/Desktop/iOS checks. | P0 | T03-T20 |

## Architecture decisions

1. `RemoteServerKind` remains exactly Navidrome, OpenSubsonic, and Emby.
   OpenList is an independent file-oriented `MusicSource`; it is never routed
   through `ServerMusicSource`.
2. Use the existing source-account/library/playback architecture. Do not add a
   second repository, online-song database, active-server singleton, or player.
3. Adopt StorageType option B for OpenList because the existing Rust storage
   backend already provides range access to the scanner and metadata reader.
   Append (never reorder) `OpenList` in the serialized Rust enum and retain an
   old-value compatibility test. A Room schema migration is not required merely
   to add the provider string; any later schema change gets an explicit Room
   migration.
4. Stop mapping music-server editor types to `StorageType.WEBDAV`. Server
   providers remain `ServerMusicSource`; OpenList gets its own storage type.
5. Provider item IDs are opaque strings. Introduce a versioned, reversible
   playback identity codec and continue decoding the existing pipe-delimited
   form during migration. Never parse a server ID as a number.
6. Live browse is paged. Library synchronization consumes pages incrementally,
   deduplicates stable IDs, detects repeated pages, and does not collect the
   entire catalog in a single gateway `List`.
7. OpenList has no invented delta API. Its incremental behavior is snapshot plus
   canonical-path/size/modified/hash-or-sign signatures, using existing
   unavailable/deleted source semantics.
8. Provider configuration stores only non-secrets (server identity, secondary
   URL, bitrate choices, cover size, write switch, capability snapshot). Passwords
   and Emby tokens stay in `CredentialStore`; OpenList access tokens and OTP stay
   in memory.
9. Every remote playback path remains `MediaId -> MusicSource.resolvePlayback ->
   PlaybackResource -> existing platform player`. Tokenized/raw URLs are never
   durable library data.
10. OpenSubsonic structured lyrics extend and reuse the existing lyric domain,
    persistence, and UI. There is no provider-specific lyrics screen.

## Stage and task dependencies

| Gate | Tasks that must pass before the gate |
| --- | --- |
| A — Architecture & Source Model | T01-T02 |
| B — Subsonic Common Layer | T03-T04 |
| C — Navidrome | T05-T06 |
| D — OpenSubsonic | T07 |
| E — Server Playlist & Favorite | T08 |
| F — Emby | T09-T11 |
| G — OpenList | T12-T15 |
| H/I — Unified Library & Multi-account | T16-T17 |
| J — Security | T19 |
| K — UI | T18 |
| L/M — Tests & Migration | T20 |
| N — Documentation | T21 |

## Luna tasks

### T01

Task ID: T01  
Title: Remote identity and legacy adapter boundary cleanup

目标：移除音乐服务器到 `StorageType.WEBDAV` 的错误映射，并使远端播放身份对任意 Unicode/保留字符可逆，同时兼容旧身份。

背景：`SourceEditorAdapters` 把三种服务器映射成 WebDAV；远端播放 ID 使用 `|` 拼接，服务器 ID 包含该字符时会损坏。

允许修改：`source/api` 的远端身份编解码及测试；`shared` 的 SourceEditor adapter、播放解析器及相关测试。

不允许修改：OpenList 模型；网络协议；Room schema；播放器；Compose UI。

接口要求：新增带版本前缀的可逆编码；解码继续支持旧二段/三段格式；所有服务器 ID 保持 `String`。文件型 editor 映射保持原行为，服务器 editor 调用 storage adapter 必须明确拒绝。

数据库要求：无 schema 变化，不重写已有记录。

测试要求：覆盖 `|`, `:`, `%`, `/`, `?`, `#`, 空格、CJK、emoji、可选 `sourceMediaId`、旧格式，以及三种服务器均不再返回 WebDAV。

验收条件：相关 common/desktop tests 通过；Sol 确认没有新的 provider 分支或数据迁移，旧 ID 可读，新 ID 完整 round-trip。

风险：旧 ID 可能含分隔符而本来已不可无歧义恢复；只能保证既有合法旧格式兼容，新编码消除此风险。

### T02

Task ID: T02  
Title: OpenList source taxonomy and storage foundation

目标：建立 OpenList 独立文件源的最小完整类型边界，不把它加入 `RemoteServerKind`。

背景：仓库没有 OpenList source ID、editor/provider type、配置、模块或 Rust storage type。

允许修改：`source/api`、新增 `source/openlist` 模块、settings/dependencies/DI、editor/provider mappings、Rust `StorageType` 和穷举分支、相关测试。

不允许修改：OpenList 网络认证/浏览实现；Subsonic/Emby 行为；Room schema；伪造能力声明。

接口要求：提供 `BuiltInSourceIds.OpenList`、`SourceEditorType.OpenList`、`ProviderTypes.OpenList`、`OpenListSourceConfiguration`、`OpenListMusicSource` 边界；Rust enum 只允许尾部追加。未实现的能力不得出现在 capabilities 中。

数据库要求：provider string 复用现有 `SourceAccountEntity`；证明不需要 schema migration；旧 Rust enum 序列化测试必须继续通过。

测试要求：registry/type/config round-trip、`RemoteServerKind` 精确成员检查、旧 `StorageType` 值兼容。

验收条件：三端共享代码可编译；OpenList 独立注册；没有 WebDAV 冒充、TODO 能力或破坏性迁移。

风险：UniFFI enum 顺序影响持久化兼容，必须由回归测试锁定。

### T03

Task ID: T03  
Title: Typed remote protocol client foundation

目标：把 Rust 远端协议逻辑分成清晰的 Subsonic/Emby/model 边界并提供可测试的错误模型。

背景：`remote_music.rs` 是无测试的单文件实现，错误/JSON 校验不足。

允许修改：`rust-libs/app-backend` 远端协议代码、FFI model/error 映射、mock-server dev dependencies、Rust tests。

不允许修改：Kotlin UI、Room、OpenList、业务能力扩展。

接口要求：保留标准 salt/token 参数和字符串 ID；HTTP、401/403、协议失败、无效 JSON 可区分且不含 secret；现有 FFI 调用保持兼容或有同步 Kotlin 调整。

数据库要求：无变化。

测试要求：salt/token、URL 编码、字符串 ID、server error、HTTP error、401、invalid JSON；mock server 不依赖私人服务。

验收条件：Rust tests 和受影响 KMP tests 通过；Sol 确认无 secret 进入错误文本。

风险：FFI 签名变化会扩大调用面，优先保持薄兼容层。

### T04

Task ID: T04  
Title: Unbounded Subsonic paging with compatibility fallback

目标：删除 10,000 首截断，实现可终止、去重、低峰值内存的完整分页。

背景：Kotlin gateway 和 `ServerMusicSource` 都限制 10,000，且服务器忽略 offset 时会循环/重复。

允许修改：Subsonic client/gateway paging contracts、`ServerMusicSource` browse paging、fixtures/tests。

不允许修改：Emby auth、OpenList、player UI、Room schema。

接口要求：browse 接受明确页参数；sync 逐页消费；`search3` 空 query 主流程按返回量推进；检测重复页；fallback 为 `getAlbumList2 -> getAlbum`；ID 去重。

数据库要求：无变化。

测试要求：25,000 首、短页、空页、重复页、ignored offset、album fallback、字符串 ID；验证无重复/无无限循环且接口不一次返回全库 List。

验收条件：fixture 完整读出 25,000，仓库内相关 10,000 常量/夹断消失，Sol 审查终止条件和内存模型。

风险：部分服务端分页语义不标准，fallback 必须有独立去重和终止保护。

### T05

Task ID: T05  
Title: Navidrome metadata and unified Library sync

目标：把 Navidrome 目录完整分页写入现有统一 Library。

背景：当前 server browse 是瞬时节点，未创建 `SourceItem`/normalized entities/`TrackSourceRef`。

允许修改：server gateway models、server sync coordinator/adapters、Room DAO 使用、tests。

不允许修改：新数据库体系、播放器、OpenList、remote write。

接口要求：映射要求中的完整 metadata/audio properties；缺失字段才使用 fallback；stable song ID 写入 `providerItemId`；再次同步幂等。

数据库要求：复用现有 source tables，无 schema 变化；删除只标记该 source item/ref 不可用，不删除仍有其他来源的 Track。

测试要求：新增/不变/修改/删除、重复 ID、同曲多来源、25k pager integration。

验收条件：Navidrome tracks 可从统一 Library 查询且来源关系正确；二次同步不重复。

风险：现有 import coordinator 偏文件签名，不得把 server metadata 硬装成文件下载流程。

### T06

Task ID: T06  
Title: Navidrome artwork, lyrics, playback, and provider config

目标：完成 Navidrome P0 的非同步能力并接入现有缓存/歌词/播放器链。

背景：当前只有固定 cover URL 和基础 stream URL，Lyrics 能力声明不真实。

允许修改：Navidrome gateway/source、provider config codec、artwork/lyrics integrations、download/playback resolver、tests。

不允许修改：OpenSubsonic-only extensions；playlist/favorite/scrobble；新 player/UI 页面。

接口要求：stream/download bitrate 分离，0 表示原始/服务端默认；cover size 限定 256/512/768/1024；server lyrics 优先后走现有 fallback；所有播放仍返回 `PlaybackResource`。

数据库要求：非敏感 config 存 providerConfig；不得存 password/token/完整播放 URL。

测试要求：bitrate 参数、cover size/cache key、lyrics fallback、dynamic playback/download URL、credential URL 不持久化。

验收条件：Browse/Library 得到的歌曲能通过现有播放器和歌词 UI 工作，能力声明均有业务链。

风险：stream 与 download 参数容易误共用；配置 codec 必须向后兼容空 config。

### T07

Task ID: T07  
Title: OpenSubsonic capability detection and structured lyrics

目标：在共享 Subsonic 层上实现扩展探测及保真结构化歌词。

背景：现有层没有扩展或 `getLyricsBySongId`，歌词模型可能不足以表达 byte/agent/language variants。

允许修改：Subsonic protocol/model、provider config capability snapshot、现有 lyrics domain/persistence/UI adapters、tests。

不允许修改：独立 OpenSubsonic 歌词 UI；假定所有服务器支持扩展；remote playlist writes。

接口要求：能力按 name+versions 表示且可刷新；保留 start/end/value/byteStart/byteEnd/agentId/language/main/translation/pronunciation；不支持时回退。

数据库要求：若现有歌词表无法无损表示，单独、正式 migration 并保留旧歌词；否则不改 schema。

测试要求：无扩展、不同版本、结构化/同步/多语言/翻译/读音、fallback、持久化 round-trip 和现有 UI mapping。

验收条件：网络层不扁平化；Now Playing 使用现有歌词链展示；migration（若有）可从旧库升级。

风险：byte offsets 不是字符 offsets，模型命名与转换必须明确。

### T08

Task ID: T08  
Title: Server playlists, favorites, and scrobble with write gate

目标：实现 Subsonic 服务器 P1 读写能力，并在后端强制默认关闭远端写入。

背景：当前歌单/收藏均为本地身份，无法避免多账号同名/同 ID 冲突。

允许修改：server APIs/gateway/source、playlist/favorite/scrobble domain/data、provider config、Room migration、tests。

不允许修改：OpenList；Emby playlist；仅靠 UI 隐藏写操作；本地歌单身份破坏。

接口要求：远端歌单身份由 provider+accountId+remotePlaylistId 唯一确定；write endpoints 在 gateway/source 层验证 `remoteWriteEnabled`；默认 false。

数据库要求：只添加必要字段/表和正式 migration；保留本地歌单和旧排序/成员数据；禁止 destructive migration。

测试要求：多账号同名歌单、read、create/update/delete、star/unstar、scrobble、开关关闭拒绝、migration。

验收条件：本地/远端歌单不冲突；绕过 UI 直接调用也无法在关闭时写服务器。

风险：远端与本地成员 ID 的所有权边界需要在 migration 前锁定。

### T09

Task ID: T09  
Title: Emby authentication, account identity, and safe editing

目标：完成 Emby 登录身份、非敏感配置和已有账号安全编辑。

背景：token/UserId 已保存正确，但 ServerId/Name 缺失；错误映射粗糙。

允许修改：Emby Rust/Kotlin auth、account/provider config、storage repository/editor logic、tests。

不允许修改：Emby library/playback；token 入 Room；空密码时重新登录。

接口要求：解析 AccessToken/User.Id/ServerId/ServerName；空新密码用已有 token 请求 `Users/{userId}`；401 返回 Unauthorized/NeedsReauthentication。

数据库要求：AccessToken 仅 CredentialStore；Room 仅 endpoint/UserId/credentialRef/非敏感 config；无 schema 变化。

测试要求：login、token/UserId/server identity、编辑空密码、401、无效 JSON、secret 非持久化。

验收条件：已有账号可无密码验证；失败不会泄露 token；UI state 不回填旧密码。

风险：不同 Emby 版本响应字段可选，config 解析需容错但不可伪造身份。

### T10

Task ID: T10  
Title: Emby exhaustive library, metadata, and artwork

目标：完整分页同步 Emby Audio 到统一 Library，并映射要求字段和 artwork。

背景：当前字段集和分页终止不足，且存在 10,000 上限。

允许修改：Emby request/model/gateway、server sync integration、artwork cache integration、tests。

不允许修改：login、playback negotiation、OpenList、Room schema（除非由已批准任务提供）。

接口要求：`Users/{userId}/Items` 使用规定分页和 Fields；stable Id 去重；完整映射 MediaSources/MediaStreams/UserData。

数据库要求：复用 source/library tables；删除只影响对应 source ref；不保存图像 token URL。

测试要求：25k、TotalRecordCount 异常/缺失、重复页、metadata/audio mapping、artwork、增删改和多账号。

验收条件：无 10k 截断；统一 Library 可查询准确 metadata 和来源；重复同步幂等。

风险：同一 item 多 MediaSource 的首选策略需确定且保留 sourceMediaId。

### T11

Task ID: T11  
Title: Emby Direct Play and Direct Stream negotiation

目标：播放时动态协商 Emby 资源并传递 required headers。

背景：当前直接拼接 token URL，未使用 playback info 或 header metadata。

允许修改：Emby protocol/gateway、`PlaybackResource` mapping、playback resolver/players 的通用 header path、tests。

不允许修改：新增 EmbyPlayer；默认 HLS AAC 转码；持久化 URL/token。

接口要求：优先 Direct Play 再 Direct Stream；MediaSourceId 来自同步身份；headers/mime/expiry 写入 PlaybackResource；所有平台沿现有 player 路径。

数据库要求：仅保存 item/media-source identity，不保存 resolved URL 或 headers。

测试要求：direct play、direct stream、required headers、401、unsupported fallback、Android/Desktop/iOS mapping compile。

验收条件：现有播放器能使用 header 资源；Room/日志无 `api_key` URL。

风险：平台播放器对 redirect/range/header 行为不同，需保留共同契约并做平台编译。

### T12

Task ID: T12  
Title: OpenList password, guest, OTP, and in-memory session

目标：实现 OpenList 安全认证和有限 401 恢复。

背景：官方 API 使用 login envelope、raw Authorization token，并可能要求 OTP；guest 使用空 token。

允许修改：OpenList client/source/account repository、credential/session abstractions、auth state models、tests。

不允许修改：Cookie/token/header 手动输入；OTP 持久化/日志；无限 retry；browse/sync/playback。

接口要求：支持 guest、username/password、OTP challenge；password 进 CredentialStore，token/OTP 只在内存；401 最多自动重登一次，OTP 用户返回 NeedsReauthentication。

数据库要求：Room 仅 account endpoint/credentialRef/config，不含 password/token/OTP。

测试要求：login、guest enabled/disabled、2FA challenge/success、Authorization、401 re-login、retry bound、process-session reset、redaction。

验收条件：auth tests 使用 mock server；静态搜索和数据库断言证明 secret 未持久化。

风险：OpenList envelope code 与 HTTP status 不总一致，必须同时正确分类。

### T13

Task ID: T13  
Title: OpenList paged browse and unified folder selection

目标：实现真实目录树并复用现有目录选择/LibraryRoot 流程。

背景：OpenList list 响应没有稳定对象 ID，路径和分页必须成为一等输入。

允许修改：OpenList client/source、node mapper、root picker data integration、tests。

不允许修改：手写 URL 拼接；临时 OnlineSong；metadata/playback implementation；新目录选择器。

接口要求：标准 URL builder；folder/audio/image/lyric/other 类型；canonical path 作为 provider identity；逐页 list；多 Root 按现有能力支持。

数据库要求：每个选择保存现有 `LibraryRootEntity`；不保存 raw URL。

测试要求：CJK、空格、`#`, `%`, `?`, emoji、嵌套目录、分页、空目录、重复 entry、node types、多 Root。

验收条件：用户可在现有 picker 中选择并保存 OpenList roots；路径 round-trip 无损。

风险：路径规范化不可把不同 Unicode/编码路径错误合并。

### T14

Task ID: T14  
Title: OpenList scanner, ranged metadata, and snapshot sync

目标：让 OpenList 通过现有 file-source pipeline 进入统一 Library。

背景：需要新的 Rust backend 支持 list/get/range，但不能虚构 delta。

允许修改：Rust storage backend、app-backend builder/FFI、OpenList source adapter、`RemoteLibraryImportCoordinator` integration、tests。

不允许修改：完整文件下载作为默认 metadata 路径；宣称 IncrementalSync capability；server sync architecture。

接口要求：recursive scan；signature 使用 path/size/modified/hash/sign；unchanged 跳过 metadata/artwork；Range 验证 206/Content-Range；复用 Fast/Standard/Full。

数据库要求：复用 `SourceItem`/`TrackSourceRef`/cursor/root；added/modified/deleted 语义与现有 file sources 一致。

测试要求：range mock、错误 Content-Range、1GB 长度资源只取小范围、两次 sync 100 unchanged/10 modified/5 added/4 deleted。

验收条件：OpenList tracks 出现在统一 Library；未变化项不重解析；删除仅使该来源不可用。

风险：直链可能不支持 Range，需要在 backend 中显式选择受验证的 proxy fallback。

### T15

Task ID: T15  
Title: OpenList dynamic playback, headers, Range, and expiry recovery

目标：播放时获取最新资源并在临时链接失效时有限重解。

背景：raw URL 可能过期或要求 headers，当前播放器没有完整的 401/403 re-resolve lifecycle。

允许修改：OpenList link resolution、generic playback resource/retry path、platform player adapters、tests。

不允许修改：新增 OpenListPlayer；持久化 URL/headers；无限 retry；整首预下载。

接口要求：direct+range > direct+headers > proxy+range；完整 headers/mime/expiry；401/403/expired signature 最多有限重解且保留 queue/position/play state。

数据库要求：只保存 item identity/canonical path。

测试要求：headers、206/Content-Range/seek、expired link、一次恢复、重复失败终止、position/state preservation、各平台编译。

验收条件：真实业务链经过现有播放器；测试证明没有永久 raw/proxy URL。

风险：重试必须区分资源失效与认证/普通网络错误，避免重复副作用。

### T16

Task ID: T16  
Title: Unified server source reconciliation and multi-source semantics

目标：收敛三种音乐服务器的 Library 同步与来源可用性语义。

背景：T05/T10 提供 provider 实现，需要共同行为覆盖重复同步、删除和同曲多来源。

允许修改：server sync orchestration、library matching/upsert helpers、DAOs/tests。

不允许修改：第二套数据库；删除仍有其他来源的 Track；provider-specific UI。

接口要求：请求始终基于 SourceAccountId；同 account+provider item 幂等；删除标 item deleted/ref unavailable；匹配策略不丢失独立来源。

数据库要求：优先现有唯一索引和关系；需要 schema 变化时移交 T20 migration，不得临时 destructive fallback。

测试要求：Navidrome/OpenSubsonic/Emby 两账号、同曲七类来源、重复 ID、删除单来源、重新出现。

验收条件：Library 查询/播放选择仍可使用剩余来源；无全局 activeServer。

风险：错误合并比重复记录更危险，保守匹配并保留 source refs。

### T17

Task ID: T17  
Title: Provider configuration and secondary endpoint policy

目标：统一非敏感 provider config，并实现只对连接类错误使用备用地址。

背景：现有 server config 仅基本凭据，没有码率、封面、write gate、capability 或 secondary URL。

允许修改：provider config models/codecs、gateway request selection、account editor state/domain tests。

不允许修改：401/403 fallback；secret 放 providerConfig；全局活动账号。

接口要求：每账号 primary/secondary；仅 timeout/DNS/refused/unreachable fallback；包含 provider 所需非敏感字段并向后兼容空/旧 JSON。

数据库要求：复用 providerConfig 字符串，无 schema 变化。

测试要求：各 provider config round-trip、多账号隔离、允许/禁止 fallback 错误分类、旧 config。

验收条件：认证错误不切换地址，连接错误按单请求策略切换一次。

风险：错误分类需跨 Rust/Kotlin 一致，不能用本地化 message substring。

### T18

Task ID: T18  
Title: Existing source UI completion

目标：在 `feature/sources` 完成所有 provider 的安全设置、OTP、目录和同步操作。

背景：当前三服务器只有基础表单，OpenList 和高级设置缺失，sync chip 没有真实动作。

允许修改：现有 source selector/editor/viewmodel、navigation resources、existing root picker/sync action wiring、UI tests。

不允许修改：第二套设置页面；Cookie/token/header/User-Agent 输入；回填旧密码；业务规则只放 UI。

接口要求：显示 8 类 source；按 provider 展示要求字段；OTP 仅 challenge 时出现并清空；advanced fields 对接 T17；保存/测试/选择 root/sync 有真实业务链。

数据库要求：通过 repository 写 account/root/config，UI 不直接访问 Room 或 secret storage。

测试要求：selector、字段可见性、OTP lifecycle、空密码编辑 Emby、guest OpenList、remote write 默认关、sync action。

验收条件：三平台 Compose 编译；无敏感手动字段；按钮均调用真实 use case。

风险：状态恢复机制可能意外持久化 OTP/password，必须排除 saveable state 和日志。

### T19

Task ID: T19  
Title: Credential, URL, logging, and diagnostics hardening

目标：对完成后的所有远端路径进行独立安全收敛。

背景：CredentialStore/diagnostic redaction 已存在，但新增 token、header、URL 和 auth state 增加暴露面。

允许修改：credential boundaries、redaction utilities/tests、sensitive model string representations、logging call sites。

不允许修改：改变协议行为；新增 secret 输入；用删日志掩盖业务错误。

接口要求：password/token/Authorization/api_key/OTP 不出现在 Room/providerConfig/log/Toast/exception/diagnostic archive；播放 URL 仅短生命周期内存。

数据库要求：使用 fixture 检查存储值；只允许 credentialRef 和非敏感 config。

测试要求：静态搜索清单、redaction variants、exception paths、diagnostic export、model `toString`, credential replace/delete 生命周期。

验收条件：Sol 对敏感词搜索的每个命中完成代码级分类，无未解释持久化/记录点。

风险：测试输出本身也可能泄露 fixture secret，使用固定假 secret 并断言只出现掩码。

### T20

Task ID: T20  
Title: Scale, migration, and multiplatform acceptance suite

目标：补齐最终集成、25k、增量、migration 和三平台门禁测试。

背景：当前局部 desktop tests 通过，但不足以证明完整链路和数据库兼容。

允许修改：test fixtures/harness、Room migrations/schema snapshots（仅已批准变化）、CI/build test configuration、必要的缺陷修复。

不允许修改：降低断言；依赖私人服务器；destructive migration；以 TODO/skip 代替平台失败。

接口要求：mock protocols 可重放；大库分页逐页；所有 provider 经统一 playback resolver。

数据库要求：每个 schema change 有旧库 fixture -> 新库 migration，旧用户数据断言保留。

测试要求：附件要求的 Rust/common tests；25,000 全量；第二次 100 unchanged/10 modified/5 added/4 deleted；Android compile、Desktop tests、iOS shared compilation。

验收条件：所有命令成功且结果记录在 test report；无静默截断、duplicate、跳过 migration。

风险：大 fixture 需流式/生成式构造，避免让测试自身成为不合理内存基准。

### T21

Task ID: T21  
Title: Truthful final documentation

目标：让中英文 README 和架构/边界/测试文档准确描述实际通过验收的能力。

背景：现有文档只描述薄的 server browse/stream，且没有 OpenList。

允许修改：`README.md`, `README.en.md`, 指定 architecture/boundary/test docs。

不允许修改：宣称未通过 gate 的能力；隐藏已知限制；修改产品行为。

接口要求：记录 source 分类、认证/secret 边界、Library/PlaybackResource 链、分页/同步策略、OpenList Range/OTP、remote-write 默认值。

数据库要求：记录最终 schema/migration 版本和升级保证。

测试要求：文档中的命令和能力逐项对照 T20 真实结果；链接/路径检查。

验收条件：Sol 的 10 Gate 全部有代码和测试证据后才可接受文档措辞。

风险：文档更新不能先于实现形成新的 overclaim。

## Baseline verification

Before Luna changes, the focused KMP baseline completed successfully:

```text
./gradlew :source:api:desktopTest :source:server:desktopTest :shared:desktopTest \
  --tests io.github.julystar.musicapp.source.api.MusicSourceRegistryTest \
  --tests io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolverTest \
  --no-daemon --no-configuration-cache --console plain

BUILD SUCCESSFUL
```

This baseline is not final acceptance; it only establishes that T01 starts from
a clean, passing focused test set.

## Sol review ledger

| Review | Result | Evidence |
| --- | --- | --- |
| A01 / T01 | PASS | Sol reviewed the codec, legacy adapter, server mapping, and tests after `T01-FIX-01`. New IDs use a no-pipe versioned form, old two/three-part pipe IDs remain readable, malformed new IDs are rejected, and server editor types fail instead of mapping to WebDAV. `:source:api:desktopTest`, `:source:server:desktopTest`, and focused `:shared:desktopTest` completed successfully (312 tasks). |
| A02 / T02 | PASS | Sol reviewed all Kotlin/UniFFI mappings after `T02-FIX-01`. OpenList is an independent empty-capability source and file provider, `RemoteServerKind` remains unchanged, `StorageType::OpenList` is appended after SMB and fails explicitly until its backend task, and credentials remain outside the Room entity. Focused Gradle verification completed successfully (317 tasks); Rust compatibility/backend tests, `cargo check`, rustfmt, and diff checks passed. |
| Stage A | PASS | T01 and T02 both passed code-level review. This gate accepts only the source taxonomy and compatibility foundation; it does not claim that OpenList network operations are implemented. |
| B01 / T03 | PASS | Sol reviewed the dedicated UniFFI remote-music error, HTTP/transport/protocol mappings, strict Subsonic and Emby JSON validation, bounded localhost mock, and Kotlin typed auth mapping after `T03-FIX-02`. All 78 app-backend tests, `cargo check`, rustfmt, and the shared typed-mapping test passed. |
| B02 / T04 | PASS | Sol reviewed the cold paged gateway/source contracts, raw-count offset progression, collection-local song/album deduplication, repeated-page termination, blank-search album fallback, and bounded page buffers after `T04-FIX-01`. The generated 25,000-song fixture, first-empty fallback, short-page stop, ignored-offset, nonblank-search, and source first-page tests passed; the remaining 10,000 clamp is isolated to Emby for T10. |
| Stage B | PASS | T03 and T04 both passed code-level review. This gate accepts the shared typed remote protocol and exhaustive Subsonic paging foundation; it does not yet claim Navidrome Library persistence, lyrics, or provider configuration. |
| C01 / T05 | PASS | Sol reviewed the metadata-rich Subsonic mapping and dedicated Navidrome snapshot coordinator after `T05-FIX-02`. It consumes cold pages in bounded transactions, deduplicates opaque provider IDs, preserves canonical/locked metadata and missing-field relationships, reuses unified Room entities, batches missing deletion and relationship replacement, and never performs missing deletion after a failed page. Generated 25,000-track Room sync, idempotency, modification, duplicate, ambiguity, alternate-source, delete/restore, and failure regressions passed independently with T03/T04/source regressions (315 Gradle tasks, 1m15s). No Room schema/version/migration changed. |
| C02 / T06 | PASS | Sol reviewed Navidrome provider configuration, dynamic stream/download resolution, artwork caching, server-first lyrics, capability declarations, secret boundaries, and fallback behavior after `T06-FIX-02`. Stream and download bitrates are independent (`0` preserves server/original behavior), cover sizes are constrained, resource URLs are resolved only at use time, cache identities are non-secret, and cancellation is never converted into fallback. Source API/server tests plus focused shared playback, artwork, lyrics, sync, and regression tests passed independently (311 Gradle tasks). `git diff --check` passed; no Room entity shape, database version, migration, or schema snapshot changed. |
| Stage C | PASS | T05 and T06 both passed code-level review. This gate accepts the Navidrome Library, metadata, artwork, lyrics, and playback chain with non-sensitive provider configuration; it does not yet claim OpenSubsonic extensions/structured lyrics or remote write features. |
| D01 / T07 | PASS | Sol reviewed the OpenSubsonic extension snapshot, explicit refresh path, v1/v2 `songLyrics` negotiation, typed structured hierarchy, classic fallback boundary, Room persistence, and existing lyrics adapters after `T07-FIX-02`. The gateway returns a lossless typed document; per-track agents/cue lines/cues retain UTF-8 byte offsets, while the data resolver projects main/translation/pronunciation into the existing lyric chain without duplicating background-agent lines. Empty extensions, v1/v2 parameters, refresh replacement, auth/permission/cancellation behavior, safe cache reuse, invalid refs, classic and embedded fallback, `lang=xxx`, and 22→23 migration passed real Room/focused tests independently (315-task combination plus final 307-task integration rerun). Machine comparison of schema 22 and 23 found only nullable `lyrics.structuredContent`; old lyric fields remain intact and `git diff --check` passed. |
| E01 / T08 | PASS | Sol reviewed the typed Subsonic playlist/favorite/scrobble gateway, ordered duplicate query-pair bridge, backend default-off write gate, provider-config preservation, account-scoped remote playlist mirror, local-playlist query isolation, and 23→24 migration after the final acceptance fixes. Direct writes are rejected before credential lookup/network when disabled or malformed; negative playlist indexes are rejected locally; remote membership resolves only through the requested account even when two accounts share identical playlist, song IDs, and names; identity failure leaves the existing mirror unchanged. Rust remote-music tests (9), focused API/server/shared Gradle tests (315 tasks), final coordinator/gateway rerun (307 tasks), migration test, rustfmt, schema comparison, and `git diff --check` passed. Machine comparison found no non-playlist entity/view/setup drift; schema 24 adds only nullable `providerType`/`sourceAccountId`/`remotePlaylistId` and their unique composite index, preserving legacy playlist rows and members. |
| Stage E | PASS | T08 passed code-level review. This gate accepts Navidrome/OpenSubsonic server playlists, favorites, scrobble requests, and the enforced default-off remote-write policy; it does not claim Emby playlists or automatic player scrobbling. |
| F01 / T09 | PASS | Sol reviewed the typed Rust/UniFFI Emby login identity, gateway auth mapping, non-secret provider configuration, CredentialStore boundary, persisted-account edit verification, and editor reauthentication behavior after the final acceptance fixes. Login requires nonblank AccessToken/User.Id/ServerId, supports valid UserDto fallbacks and nullable ServerName, and never echoes response secrets in typed errors. Empty-password verification uses the persisted Emby account/UserId and existing token, rejects cross-provider credentials plus UserId/ServerId mismatches before mutation, preserves verified username/server metadata, and maps 401 to token-free `NeedsReauthenticationException`/Unauthorized UI state. The JSON codec ignores arbitrary future fields, handles null/malformed values without fabricating identity, escapes Unicode/control content correctly, and strips unsafe secondary URLs. Rust remote-music tests (9) and the focused API/server/shared KMP combination (315 tasks) passed independently after fixes; cancellation propagation, replacement-password 401, Room/credential non-mutation, editor secret redaction, and full-account persistence were covered. `git diff --check` passed. Schema 24, `AppDatabase.kt`, and migrations retained their pre-T09 SHA-256 hashes, and no schema 25 exists. |
| F02 / T10 | PASS | Sol reviewed the exhaustive Emby Audio pager, complete metadata/media mapping, provider-neutral Library sync engine with fixed Navidrome/Emby wrappers, non-secret source properties, and tag-aware remote artwork cache after `T10-FIX-02`. The cold pager has no 10,000 cap, advances `StartIndex` by raw `Items` count, treats `TotalRecordCount` as advisory, globally deduplicates stable IDs, fails missing/wrong-typed `Items`, and detects both adjacent and non-adjacent repeated full pages so an incomplete snapshot cannot run deletion. Emby maps ordered Artists/Genres, album identity, scalar metadata, nested MediaSources/MediaStreams, selected source audio properties, UserData, and `ImageTags.Primary`; selected `sourceMediaId`, album ID, UserData, and image tag are stored only as account-scoped non-secret `source_item_property` values because T10 forbids a Room schema change. Sync is idempotent for complete and sparse metadata, counts property removal as modification, isolates equal remote IDs across accounts, and only marks the missing account's source ref unavailable while retaining a shared canonical track. Artwork cache identity includes provider/account/item/tag/size; the resolver, production repository, and image loader bypass unversioned artwork-only caches for remote art, and no token URL or endpoint is persisted. Sol's independent `:source:api:desktopTest :source:server:desktopTest :shared:desktopTest` focused combination completed successfully (311 tasks, 1m05s): Emby pager 6, gateway 15, sync 9, resource integration 7, and Legacy artwork 12 tests, all with zero failures. `git diff --check` passed; schema 24, `AppDatabase.kt`, and migrations retained their T09 SHA-256 hashes and no schema 25 exists. Emby artwork size remains fixed at 512; playback negotiation is deliberately deferred to T11. |
| F03 / T11 | PASS | Sol reviewed Emby `PlaybackInfo` negotiation, exact synchronized `sourceMediaId` selection, deterministic Direct Play-before-Direct Stream fallback, typed credential-free static audio URL construction, and the common header-capable playback proxy after `T11-FIX-02`. The selected media source must expose a nonblank ID and direct capability; required headers are strict string values with invalid, hop-by-hop/range, CRLF, or case-insensitive duplicate names rejected. `X-Emby-Token`, MIME metadata, and a five-minute in-memory expiry are carried only in `PlaybackResource`; the localhost proxy removes headers/expiry from the player-facing resource, fails closed when protected headers cannot be proxied, forwards them only across same-origin redirects, bounds redirect chains at ten, and preserves header-free cross-origin redirects for existing CDN-backed sources. The resolver reads and trims `sourceMediaId` once per item, shares it between the negotiation target and versioned cache identity, preserves concrete remote failures, and continues through alternate candidates after proxy failure. Sol independently passed all 86 app-backend tests and 36 focused Kotlin tests (playback cache 2, resolver 15, gateway 19), plus Desktop, Android, and iOS simulator shared compilation, rustfmt, and `git diff --check`. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes; no schema 25 exists. No tokenized Emby playback URL, playback headers, or resolved resource is persisted. Transcoding remains deliberately unsupported. |
| Stage F | PASS | T09 through T11 passed code-level review. This gate accepts Emby authentication, exhaustive Library sync, metadata/artwork, and Direct Play/Direct Stream negotiation through the shared player path; it does not claim Emby transcoding, playlists, or remote writes. |
| G01 / T12 | PASS | Sol reviewed the dedicated OpenList authentication transport and account-scoped in-memory session manager after `T12-FIX-02`. Login uses `POST /api/auth/login`, validation uses `GET /api/me`, `Authorization` carries the raw token, guest requests omit the header, and both HTTP status and OpenList envelope codes are classified without exposing credentials. Passwords remain in `CredentialStore`; tokens and OTP values remain memory-only; the only persisted provider configuration is the non-secret `requiresOtp` marker, which drives fail-fast reauthentication after process loss. Authentication and non-mutating probes bind no session until validation succeeds, password sessions perform at most one login/retry after a 401, known OTP sessions use the common `NeedsReauthenticationException`, account IDs remain isolated, guest conversion clears credentials and OTP state, and failed persistence restores prior credentials and clears the new session. Sol independently passed all 95 app-backend tests, the OpenList API tests (3), source tests (3), session-manager tests (8), and focused Room integration tests, followed by Desktop, Android, and iOS simulator shared compilation, rustfmt, `cargo check`, and `git diff --check`. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes; no schema 25 exists. This gate accepts authentication/session behavior only: OTP entry UI is deferred to T18, and browse/sync/playback remain unclaimed. |
| G02 / T13 | PASS | Sol reviewed the dedicated OpenList browse client, cold paged source, canonical path mapper, and existing root-picker integration after `T13-FIX-02`. Browse calls `POST /api/fs/list` through the shared URL builder with raw JSON paths, one-based bounded pages, raw or omitted `Authorization`, strict HTTP/envelope classification, and strict `content` handling that accepts the official `null` empty-directory form but rejects missing or malformed payloads. The source advertises only `Browse`, requires the dedicated client instead of falling back to the unsupported legacy backend, preserves CJK, spaces, `#`, `%`, `?`, emoji, and composed/decomposed Unicode without URL decoding or Unicode normalization, uses the canonical raw path for `nodeId`/`remoteId`/`path`, rejects traversal and noncanonical parents before network access, advances by raw entries, deduplicates exact paths, and terminates on empty/short/repeated/total-complete pages or a hard page bound. Folder, audio, image, `.lrc`, and other objects map to the unified five node types without fabricating MIME values. The existing picker retains the clicked raw display name, exposes OpenList root selection while keeping the unimplemented scan action hidden, and idempotently persists multiple existing `LibraryRootEntity` rows with equal provider/canonical paths without overwriting the account root or storing raw file URLs. Sol independently passed all 99 app-backend tests; forced reruns of API OpenList tests (3), OpenList source tests (9), importer path fidelity (1), Settings view-model tests (17), session browse client tests (3), and the three-test storage/Room integration class; the focused shared rerun executed 311 Gradle tasks and the source/feature rerun executed 72 tasks. Desktop, Android, and iOS simulator shared compilation, rustfmt, and `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes; no schema 25 exists. Scanner, ranged metadata, synchronization, search, playback, and OTP UI remain deliberately deferred. |
| G03 / T14 | PASS | Sol reviewed the dedicated transient-token OpenList storage backend, recursive scanner, ranged metadata seam, complete-snapshot integration, and multi-root Settings dispatch after `T14-FIX-03`. The generic persisted-storage builder still rejects OpenList so a password cannot be mistaken for a bearer token; scan and metadata instead receive a validated in-memory session through dedicated UniFFI entry points. Directory pages use `/api/fs/list`, preserve exact raw `%`, Unicode, spaces, `#`, `?`, emoji, and backslash identities through both the source picker and scanner, deduplicate exact paths, and stop on empty/short/total-complete pages or bounded repeated-page failure. Full-file `get` is unsupported. Metadata obtains a fresh `/api/fs/get` resource for each bounded read, sends `Range` without the API token to the direct URL, accepts only an exact `206`/`Content-Range`/body/total match, and falls back only from a direct `200` to a freshly signed or unsigned same-server `/p` URL; malformed direct `206` responses never trigger fallback. Stable `hashinfo` or sorted `hash_info`, size, and modified time drive content equality, while potentially expiring `sign` is retained only as revision metadata and does not force reparsing. Kotlin now selects provider-aware path semantics before root lookup, deduplication, scan rules, signature planning, metadata correlation, source-item persistence, and stable track IDs: legacy providers retain slash normalization, while OpenList only adds a leading slash and otherwise preserves the raw identity through real Room root persistence. A two-snapshot Room fixture proves 100 unchanged, 10 modified, 5 added, and 4 deleted entries, only 15 metadata reads on the second run, exact raw root/item/provider IDs, and deletion of only the OpenList source reference while another provider remains available. Settings scans every configured enabled OpenList root and Scan All forwards all three raw path fields unchanged. Sol independently passed rustfmt, all 51 storage-backend tests, all 101 app-backend tests, forced source/API/server/importer/Settings desktop tests (77 Gradle tasks), a final OpenList source/importer raw-path rerun (49 Gradle tasks), the complete shared desktop suite (311 Gradle tasks), and Desktop, Android, and iOS simulator shared compilation. `git diff --check` passed; schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. This gate accepts exhaustive snapshot sync and ranged metadata only; dynamic playback, expiry recovery, search, downloads, and OTP UI remain deferred. |
| G04 / T15 | PASS | Sol reviewed the dedicated OpenList playback backend, UniFFI/session-manager wiring, stable loopback gateway, strict ranged transport, route selection, and finite expiry recovery after `T15-FIX-02`. Playback resolves `/api/fs/get` authoritatively, prefers an exact bare-direct `206`, consults optional admin-only `/api/fs/link` headers only when the bare direct resource is unauthorized or returns `200`, and otherwise uses only a same-server signed or unsigned `/p` route. Exact `Content-Range`, optional `Content-Length`, actual body length, total size, URL scheme/userinfo/fragment, redirect origin, and request headers are validated; only `200` is range-unsupported, while malformed `206` and all other non-`206` success statuses fail closed without link/proxy fallback. Link `Expiration` and `/get` sign expiry remain memory-only. A generation-checked async refresh lock gives concurrent readers one refreshed resource, and a session-wide one-shot recovery budget terminates repeated `401`/`403` or expired-sign failures while the player keeps the same loopback URL, queue state, position, and play state. The generic persisted OpenList storage builder remains unsupported; Room stores only canonical source identity/revision data, and no resolved URL, playback headers, token, or `PlaybackResource` is persisted. The common HTTP cache backend forwards protected headers only across same-origin redirects, bounds redirect chains, and now bypasses environment proxies only for numeric loopback IP origins, preventing the OpenList loopback token path from leaking to a configured proxy while remote origins retain proxy behavior. Sol independently passed all 66 storage-backend tests, the app-backend suite after the proxy fix (104/104), and the formerly flaky same-origin redirect test ten consecutive times; a forced `:source:api:desktopTest :source:openlist:desktopTest :shared:desktopTest` rerun executed 315/315 tasks, and forced Desktop, Android Debug, and iOS Simulator compilation executed 770/770 tasks. Rustfmt and `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. Search, downloads, and OTP UI remain deliberately deferred. |
| Stage G | PASS | T12 through T15 passed code-level review. This gate accepts OpenList authentication/session boundaries, raw-path browsing and root selection, complete snapshot sync with ranged metadata, and dynamic ranged playback with headers and finite expiry recovery through the unified player path. Search, downloads, and the final OTP/account UI are intentionally deferred to T16 through T19. |
| H01 / T16 | PASS | Sol reviewed the shared server reconciliation engine, new OpenSubsonic thin coordinator, account-derived provider-neutral production entry point, Koin assembly, response-account guard, and multi-source Room tests. The unified entry accepts only `SourceAccountId`, loads the persisted provider type, and fail-closes missing, malformed-route, and non-server accounts before gateway access; callers cannot supply a provider kind and no active/current server state exists. All three server providers use the same engine, while a returned `RemoteServerTrack.accountId` mismatch now fails before that page is written. A real Room matrix covers two accounts each for Navidrome, OpenSubsonic, and Emby, exact gateway kind/account/page-size routing, same-provider remote-ID isolation, same-page and cross-page deduplication, idempotent re-sync, and provider mismatch rejection. A seven-remote-source fixture converges WebDAV, SMB, OneDrive, OpenList, Navidrome, OpenSubsonic, and Emby onto one conservatively matched canonical Track while preserving seven account-scoped items/refs; deleting only the OpenSubsonic source removes only that playback candidate, leaves six usable candidates and the Library Track, and reappearance restores the original item/ref/Track identities. Sol independently passed the four new Room tests, then forced `:source:api:desktopTest :source:server:desktopTest :shared:desktopTest` with 315/315 tasks executed, and rechecked Desktop, Android Debug, and iOS Simulator compilation. `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. The production coordinator is intentionally not wired to the existing UI/task-status path until T18. |
| I01 / T17 | PASS | Sol reviewed the backward-compatible Navidrome, OpenSubsonic, and Emby non-secret provider configurations, editor repository persistence, typed Rust transport boundary, and stateless per-request secondary-endpoint policy after the final classifier fixes. Only transport timeout, DNS, refused connection, and host/network-unreachable categories may retry the current account's sanitized secondary URL once; `401`, `403`, other HTTP/protocol/JSON failures, TLS handshake failures, connection reset, cancellation, invalid URLs, and a second failure never trigger another endpoint. Response-body timeouts remain eligible, while completed malformed JSON remains `InvalidResponse`. The policy returns the successful endpoint, which is used for Subsonic page/playlist resource URLs and Emby PlaybackInfo-derived playback URLs; two accounts with different secondary URLs have exact isolated request sequences and there is no active/preferred-server state. OpenSubsonic bitrate, cover, remote-write, secondary URL, and capability snapshot survive both capability refresh and editor updates; Emby empty-password edits preserve verified server identity while persisting an edited secondary URL. The generic configuration-only `authenticate` seam intentionally remains single-endpoint because it has neither account identity nor provider config; the real editor test/save paths own account-scoped fallback, and pure URL builders without an adjacent successful request deterministically remain on primary rather than adding a probe or affinity singleton. Sol independently passed all 12 remote-music focused tests and a forced 313-task Kotlin combination covering provider codecs, endpoint policy, gateway, pagers, and Room editor integrations. Luna's forced full API/server/shared desktop run passed 488 tests with one pre-existing skip (311 tasks), all 105 app-backend tests passed, and Desktop, Android Debug, and iOS Simulator shared compilation completed 766 tasks successfully. Rustfmt and `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. Direct Subsonic playback/download/artwork URL builders cannot fail over before the player fetches them; final documentation must state this boundary rather than claim transparent media-fetch failover. |
| Stage H/I | PASS | T16 and T17 passed code-level review. This gate accepts unified account-derived server reconciliation, conservative seven-source ownership semantics, backward-compatible non-secret provider configuration, and account-scoped one-shot fallback for typed connection failures. It does not yet claim the final source editor/OTP/sync UI, the independent security audit, or transparent secondary failover for pure media URL fetches. |
| J01 / T19 | PASS | Sol classified every security-search hit after `T19-BLOCKER-02`: source credentials are confined to platform `CredentialStore`/temporary transport arguments, source-account Room rows retain only credential references and non-secret configuration, OpenList tokens/OTP and plugin candidate context remain memory-only, playback URI/header models redact their string form and resolved loopback resources remain non-durable, remote exceptions are typed, and diagnostics/log/incident/export persistence passes the synchronized Rust/Kotlin v2 redactor. The redactor covers URL userinfo and queries, Authorization/Cookie/Set-Cookie/X-Emby variants, password/token/API-key/OTP key spellings, JSON escaped values, loopback capability paths, and the restored WebDAV/SMB/plugin-prefixed legacy forms while preserving ordinary `code=42`. OneDrive no longer logs malformed response bodies. Generic, SMB, and server credential writes now restore the prior credential or delete a newly created credential when the following DAO upsert fails, without masking the original DAO failure. A real Room matrix covers all seven remote provider types, credential replacement/removal and rollback, and verifies that resolving a real loopback `PlaybackResource` does not change persisted account values. Source model/action nesting, OAuth/token-bearing results, and diagnostic archive/error paths have explicit non-leaking tests; test failure messages themselves contain only case indexes. The previous Rust panic hook and platform uncaught handlers remain for platform crash visibility; production remote paths use fixed/typed panic and exception text, while application-owned crash artifacts are centrally redacted. Sol independently passed all 107 app-backend tests, a 30-test infra rerun after the final redaction fix, and a forced Kotlin security-focused combination across core/domain, source/api, playback/domain, sources, settings, and shared (327 tasks), then reran the final shared diagnostics tests (307 tasks). Luna's complete shared desktop rerun passed 469 tests with one pre-existing skip after one isolated single-loop timing timeout passed both focused and complete reruns; all affected Desktop, Android Debug, and iOS Simulator modules compiled in a 770-task run. Rustfmt, `cargo check`, and `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. The unrelated legacy plugin-settings table is outside the remote-source credential boundary; T19 neither migrates nor expands it. |
| Stage J | PASS | T19 passed independent code-level security review. This gate accepts the remote-source credential, transient URL/header/session, model-output, log, exception, diagnostics-export, and credential-rollback boundaries; it does not claim that every ordinary non-query webpage URL is removed from diagnostics. |
| K01 / T18 | PASS | Sol reviewed the completed eight-source selector, provider-specific editor states and Compose fields, OpenList guest/OTP lifecycle, Emby blank-password identity display, T17 advanced configuration mapping, exact-account root picker, dashboard/editor sync actions, account-derived production dispatcher, and tests after `T18-FIX-01`. Local remains a UI-only picker action; OpenList is an independent persistent editor type; no Cookie, token, Authorization, header, or User-Agent input exists. Passwords are never backfilled into the server/OpenList UI. OTP text exists only in a private `EditStorageVM` string and Compose-local state, while outward state exposes only non-secret challenge/has-value/reset-generation flags; identity/provider/guest changes reset the visible value, successful OTP testing retains it only until save, save/navigation clears it, and the transient action redacts its `toString`. File pickers are locked to the exact account, fail closed on callback-account mismatch, and forward raw remote ID/canonical path unchanged. Both UI sync entry points call a provider-neutral `SourceAccountLibrarySyncController` with only `SourceAccountId`; the singleton dispatcher reads the enabled account/provider and persisted roots from Room, routes servers to the T16 coordinator and files to the existing folder sync, and never accepts a UI kind/page size. The final fix prevents an unsaved file-to-server or server-kind conversion from displaying or executing editor sync, so UI intent cannot diverge from the persisted provider. Same-account duplicate taps are suppressed, cancellation is rethrown without success/reload, and success reloads account state. Sol independently reran all 15 feature-source tests plus focused real-Room dispatcher/editor-adapter tests (316 Gradle tasks); Luna's relevant API/server/shared Desktop suites remained green and final Desktop, Android Debug, and iOS Simulator source/shared compilation succeeded. `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. |
| Stage K | PASS | T18 passed code-level review. This gate accepts the existing source UI completion, memory-only OTP entry, real account/root/sync actions, and three-platform Compose compilation. It does not yet accept the independent credential/logging/diagnostics security audit in T19. |
| L01 / T20 | PASS | Sol reviewed the final scale and cross-provider playback acceptance additions, including the two fail-closed production corrections found during review. A seven-provider resolver matrix proves WebDAV, OneDrive, SMB, OpenList, Navidrome, OpenSubsonic, and Emby all resolve persisted account-scoped candidates through their registered `MusicSource`; opaque file paths and versioned server remote IDs retain exact identity. Unknown persisted provider strings now return `UnsupportedAccount` without calling WebDAV, and the Rust storage adapter now exposes only Local, WebDAV, OneDrive, SMB, and OpenList accounts, rejecting all server and corrupt provider types instead of disguising them as WebDAV. Existing synthetic tests exercise 25,000 Subsonic and Emby items over 50 pages without truncation or duplication, while a real Room Navidrome coordinator consumes 25,000 tracks in bounded 500-item pages. The real Room OpenList second-snapshot fixture proves exactly 100 unchanged, 10 modified, 5 added, and 4 deleted items, only 15 metadata reads, and source-local deletion semantics. Sol independently forced the resolver, credential-storage, migration, 25k, and incremental tests: 28 tests executed with zero failures, errors, or skips; the real Room 25k case completed in 62.683 seconds. Luna's complete Rust workspace run passed 296 tests with only four pre-existing opt-in Samba fixture tests ignored, and the forced API/server/OpenList/source-UI/shared Desktop run executed 322 Gradle tasks with 532 tests, zero failures, and one pre-existing opt-in live WebDAV smoke skipped. Those five external-fixture cases are manual tests, were not added by T20, and are not part of the required mock/Room CI gates. Desktop, Android Debug, and iOS Simulator compilation completed 770 tasks successfully. Rustfmt, `cargo check --workspace`, and `git diff --check` passed. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes, and no schema 25 exists. |
| Stage L | PASS | The required Rust, Kotlin common, integration, large-library, deduplication, incremental-sync, and provider-routing acceptance coverage is present and green without private-server dependencies or newly skipped assertions. |
| Stage M | PASS | A formal sequential 22→23→24 migration test preserves legacy lyrics, local playlist rows and members, introduces nullable structured-lyrics and remote-playlist identity fields, and preserves cross-account remote identity. No schema or migration implementation changed in T20, schema 25 does not exist, and no `fallbackToDestructiveMigration` call is present. |
| N01 / T21 | PASS | Sol reviewed all five required documentation updates and requested three final wording corrections before acceptance. The Chinese and English READMEs now symmetrically list the eight source choices and accurately distinguish Navidrome download support from OpenSubsonic, Emby, and OpenList; OpenList search is described only as unified Room index search after synchronization, and server/OpenList synchronization is identified as a complete snapshot rather than protocol delta. The architecture documents record `source/openlist` as an independent `MusicSource` outside `RemoteServerKind`, the seven-remote-provider registry/resolver/player chain, account-derived synchronization, provider capability limits, typed request-scoped secondary fallback, the lack of transparent failover for later pure Subsonic resource fetches, OpenList guest/password/OTP and strict ranged playback boundaries, credential/transient-resource/redaction ownership, current schema 24, and formal sequential 22→23→24 migration. They do not claim Emby transcoding/playlists/writes/downloads/lyrics, provider-native OpenList search/download/delta, or permanent schema finality. The test report truthfully records Rust 296 passed plus four existing opt-in Samba ignores, app-backend 107/107, 532 Gradle tests with zero failures and one existing opt-in live WebDAV skip, the 770-task three-platform compilation, Sol's 28/28 focused T20 run, the 25k and exact 100/10/5/4 fixtures, and migration evidence. `git diff --check` passed and the T21-scoped diff contains only `README.md`, `README.en.md`, `docs/architecture/final-architecture.md`, `docs/architecture/rust-kmp-boundary.md`, and `docs/testing/test-report.md`. Schema 24, `AppDatabase.kt`, migrations, and all four protected concurrent-work files retained their recorded SHA-256 hashes; no schema 25 exists. |
| Stage N | PASS | The required user, architecture, Rust/KMP-boundary, and test documentation matches the accepted implementation and explicitly preserves its unsupported-capability and external-fixture boundaries. |
| Final Gate 1 — Architecture | PASS | `source/openlist` is independently registered in the eight-entry `MusicSourceRegistry`; `RemoteServerKind` contains exactly Navidrome, OpenSubsonic, and Emby. No OpenList, Emby, or Navidrome player class/file exists. |
| Final Gate 2 — Navidrome | PASS | Typed authentication, exhaustive paging, unified Room library reconciliation, metadata, cached artwork, lyrics-chain integration, dynamic playback, downloads, playlists, favorites, and scrobbling are implemented and covered by the accepted Rust/Kotlin suites. |
| Final Gate 3 — OpenSubsonic | PASS | Extension discovery/snapshot persistence, structured line/word timing data, and fallback to compatible Subsonic/existing lyrics sources are implemented and tested. |
| Final Gate 4 — Emby | PASS | Login parses AccessToken, UserId, ServerId, and ServerName at the correct secret/non-secret boundaries; exhaustive paging, metadata/audio-source mapping, artwork, and Direct Play/Direct Stream negotiation are implemented and tested without claiming transcoding. |
| Final Gate 5 — OpenList | PASS | Guest/password/OTP login, raw-path browse and exact-account folder selection, multi-root complete scans, ranged metadata, unified Room persistence, strict ranged playback, optional validated headers, stable loopback serving, and finite refresh recovery are implemented and tested. |
| Final Gate 6 — Library | PASS | Synthetic Subsonic/Emby and real-Room Navidrome 25,000-track tests prove no 10k truncation and correct paging/deduplication; the OpenList 100/10/5/4 second snapshot and seven-provider Room fixtures prove sync, deletion isolation, and multi-provider ownership. |
| Final Gate 7 — Playback | PASS | Persisted source candidates resolve through `MusicSourceRegistry -> MusicSource.resolvePlayback -> PlaybackResource -> PlaybackEngineResource ->` the existing platform player. Seven-provider positive routing and corrupt-provider fail-closed tests pass; no provider-specific player exists. |
| Final Gate 8 — Security | PASS | The final password/token/Authorization/API-key/Cookie/OTP review found source credentials only in credential stores or transient arguments, non-secret Room account fields, memory-only session/OTP/resolved resources, explicit safe string forms, and centralized redaction v2; no sensitive source-account Room field or newly exposed diagnostic path remains. |
| Final Gate 9 — Multiplatform | PASS | Sol independently forced 770/770 compilation tasks for the affected source API/server/OpenList/Sources/shared modules on Desktop, Android Debug, and iOS Simulator Arm64. |
| Final Gate 10 — Tests | PASS | Sol independently passed the complete Rust workspace (296 passed, four existing manual Samba ignores), 28/28 focused Room/resolver/scale/migration tests, and 59/59 forced source API/server/OpenList/Sources tests. Luna's complete forced Desktop gate reported 532 tests, zero failures, and the single existing opt-in live WebDAV skip. Formal 22→23→24 migration, large-library, and integration coverage are present; production code contains no destructive migration fallback. |
| FINAL 10-GATE ACCEPTANCE | PASS | `git diff --check`, documentation review, production static rejection checks, protected-file hashes, schema 24/AppDatabase/migration hashes, and schema-25 absence all passed after the final test and multiplatform runs. |
