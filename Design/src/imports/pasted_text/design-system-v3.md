我认为 **v3** 不应该再是一个「UI Prompt」，而应该升级成 **完整的 Product Design Specification（PDS）**。

你的目标实际上不是生成几张页面，而是建立一个可以长期维护、支持 Compose Multiplatform + compose-miuix-ui + Android/iOS/Desktop 的**设计规范**。它应该达到 Apple Human Interface Guidelines、Material Design、HyperOS Design Language 那种级别。

结合你之前所有要求（Apple Music、MIUI/HyperOS、compose-miuix-ui、多 Source、多平台），我建议 **TidePlayer Design System v3** 的定位如下：

> **Apple Music Information Architecture × HyperOS Design Language × Compose Multiplatform Responsive Design System**

---

# TidePlayer Design System v3

建议整个 Figma 文件采用下面结构（这一版以后基本不会再变）。

```
📁 00 Cover
    Brand
    Vision
    Design Principles
    Product Philosophy

📁 01 Foundation
    Color System
    Typography
    Grid
    Elevation
    Blur
    Radius
    Motion
    Iconography
    Illustration
    Haptics

📁 02 Design Tokens
    Color Tokens
    Space Tokens
    Radius Tokens
    Typography Tokens
    Motion Tokens
    Blur Tokens
    Shadow Tokens
    Icon Tokens

📁 03 Components
    Buttons
    Navigation
    Cards
    Music Cards
    Album Cards
    Playlist Cards
    Artist Cards
    Source Cards
    Mini Player
    Queue
    Lyrics
    Dialog
    Bottom Sheet
    Settings
    Search
    Preference
    Switch
    Chip
    Tag
    Slider
    Tabs
    FAB
    Context Menu

📁 04 Adaptive Layout
    Android Phone
    Android Fold
    Android Tablet
    Android Auto
    iPhone
    iPad
    Desktop
    TV（Future）

📁 05 Pages
    Home
    Search
    Library
    Settings
    Source Manager
    Now Playing
    Lyrics
    Queue
    Album
    Artist
    Playlist

📁 06 Prototype

📁 07 Motion

📁 08 Dev Mode

📁 09 Compose Mapping
```

---

# 设计原则（新增）

建议增加一页。

```
Simple

Calm

Immersive

Music First

Content First

Adaptive

Native

Cross Platform

Plugin Driven
```

这是整个产品的核心。

---

# Navigation（重新定义）

整个产品只有四个 Tab。

```
Home

Search

Library

Settings
```

没有：

```
Now Playing
```

播放器永远来自：

```
Mini Player

↓

Now Playing
```

这和 Apple Music 完全一致。

---

# Source 架构

我建议重新设计。

Source 不应该只是设置。

应该成为一级能力。

```
Library

↓

Sources

↓

WebDAV

OneDrive

Google Drive

SMB

Plex

Emby

Jellyfin

Navidrome

Local
```

每个 Source：

```
Storage

Music Count

Status

Sync

Scan

Logs

Settings
```

全部 Card 化。

---

# Library

建议重新组织。

```
Songs

Albums

Artists

Genres

Folders

Playlists

Favorites

Downloads

History

Recently Added

Recently Played

Lossless

Hi-Res

Sources
```

比 Apple Music 更适合本地音乐。

---

# Settings

参考 HyperOS。

```
Transfer

Playback

Library

Lyrics

Appearance

Plugins

Advanced

About
```

不要 Material。

全部使用：

Group Card。

---

# Breakpoint

统一。

```
Compact

0~599dp

Medium

600~839dp

Expanded

840~1279dp

Large

1280+

XL

1600+
```

---

# Android Phone

```
Bottom Navigation

Single Pane

Mini Player
```

---

# Fold

Cover：

Phone

展开：

```
Rail

Content

Detail
```

---

# Tablet

```
Rail

Library

Detail
```

播放器：

Dock。

---

# Desktop

参考 Apple Music。

```
┌─────────────────────────────────────────────┐
│ Toolbar                                     │
├────────┬──────────────────────────┬─────────┤
│Sidebar │                          │ Lyrics  │
│        │                          │ Queue   │
│        │         Content          │         │
│        │                          │         │
├────────┴──────────────────────────┴─────────┤
│               Mini Player                   │
└─────────────────────────────────────────────┘
```

支持：

Queue

Lyrics

关闭。

---

# Android Auto

重新设计。

```
Home

Library

Playing
```

卡片：

48dp+

按钮：

64dp+

字体：

更大。

---

# Motion

新增：

```
Mini

↓

Expanded

↓

Immersive
```

Artwork：

Shared Element。

歌词：

Fade。

背景：

Blur Morph。

---

# Compose Mapping

新增：

```
MiuixScaffold

DefaultTopAppBar

SuperArrow

SmallTitle

CardGroup

NavigationRail

Preference

Navigator

FloatingCard
```

做到：

Figma

↓

Compose

1:1。

---

# Design Tokens

新增：

```
Spacing

Radius

Elevation

Blur

Animation

Icon Size

Touch Target

Adaptive Width
```

全部变量化。

---

# Figma AI Prompt（完整版）

下面这份 Prompt 建议作为 **Figma Make 的 System Prompt**，后续所有页面都基于它生成。

```text
Create a production-ready cross-platform Design System for a music application called "TidePlayer".

Design language:
- Xiaomi HyperOS / MIUI (compose-miuix-ui)
- Apple Music Information Architecture
- Material 3 Dynamic Color
- Compose Multiplatform

The goal is NOT to create static mockups.

The goal is to create a reusable responsive design system suitable for Android, iOS and Desktop development.

==================================================
BRAND
==================================================

Product Name:
TidePlayer

Tagline:
One Library. Every Source.

Design Keywords:

Minimal
Elegant
Immersive
Content First
Music First
Adaptive
Native
Cross Platform
Plugin Driven

==================================================
FOUNDATION
==================================================

Create reusable Design Tokens.

Spacing:
4
8
12
16
20
24
32
40
48

Radius:
12
18
24
28
36

Elevation:
Surface
Card
Popup
Floating
Overlay

Blur:
None
Light
Medium
Heavy

Typography:
Display
Headline
Title
Body
Label

Create Variables for every token.

==================================================
COLOR
==================================================

Primary:
#FF5B8A

Secondary:
#7A6CFF

Support:
Blue
Orange
Green

Support:

Light Mode

Dark Mode

Dynamic Color extracted from Album Artwork.

==================================================
COMPONENTS
==================================================

Generate reusable components.

Buttons

Navigation Bar

Navigation Rail

Sidebar

Search Bar

Top App Bar

Mini Player

Player Controls

Album Card

Artist Card

Playlist Card

Music Card

Source Card

Settings Group

Preference Item

Switch

Slider

Dialog

Bottom Sheet

Snackbar

Queue

Lyrics Panel

Everything must use Auto Layout.

Everything must use Variants.

Everything must support Dev Mode.

==================================================
RESPONSIVE LAYOUT
==================================================

Support:

Android Phone

Android Fold

Android Tablet

Android Automotive

iPhone

iPad

Desktop

Minimum desktop size:

1280×800

Recommended:

1440×900

1920×1080

3840×2160

Breakpoints:

Compact

Medium

Expanded

Large

XL

Navigation adapts automatically:

Bottom Navigation

↓

Navigation Rail

↓

Sidebar

Cards become adaptive grids.

Mini Player is persistent.

==================================================
INFORMATION ARCHITECTURE
==================================================

Bottom Navigation:

Home

Search

Library

Settings

Remove "Now Playing" tab.

Now Playing opens ONLY from Mini Player.

==================================================
HOME
==================================================

Hero Banner

Continue Listening

Recently Added

Recommended Albums

Recommended Artists

Recommended Playlist

Pinned Playlist

Recently Played

==================================================
SEARCH
==================================================

Global Search

Recent Search

Trending

Albums

Artists

Songs

Folders

Sources

==================================================
LIBRARY
==================================================

Songs

Albums

Artists

Genres

Folders

Playlists

Favorites

Downloads

History

Recently Added

Recently Played

Lossless

Hi-Res

Sources

==================================================
SOURCE MANAGER
==================================================

Every Source is a Card.

Supported Sources:

WebDAV

SMB

OneDrive

Google Drive

Dropbox

Emby

Plex

Jellyfin

Navidrome

Local Storage

Every Source Card includes:

Status

Music Count

Storage

Sync

Logs

Settings

==================================================
SETTINGS
==================================================

Use Xiaomi HyperOS Settings style.

Groups:

Transfer & Download

Playback

Library

Lyrics

Appearance

Plugins

Advanced

About

Grouped Cards.

Large rounded corners.

Soft dividers.

No Material Settings UI.

==================================================
NOW PLAYING
==================================================

Large Album Artwork

Dynamic Blur

Gradient Background

Lyrics

Queue

Audio Output

Favorite

Download

EQ

Share

Shared Element Transition from Mini Player.

==================================================
MOTION
==================================================

Use HyperOS motion rhythm.

Spring animation.

Hero transition.

Shared Element.

Blur Morph.

Album artwork zoom.

==================================================
DESKTOP
==================================================

Apple Music inspired.

Toolbar

Sidebar

Content

Lyrics Panel

Queue Panel

Resizable Sidebar

Resizable Queue

Resizable Lyrics

Persistent Mini Player

==================================================
DEV MODE
==================================================

Organize everything as reusable components.

No duplicated layers.

Proper naming.

Variables.

Design Tokens.

Component Properties.

Ready for Compose Multiplatform implementation.

Map every component to compose-miuix-ui concepts wherever possible.
```

## 下一步建议（v3.1）

如果整个项目最终要开源（你前面提供了 `NeriPlayer` 仓库），我建议继续升级到 **v3.1**：

* **建立 Figma → Compose Multiplatform 的一一映射规范**（组件命名、属性、状态、设计 Token 对应 Kotlin 常量）。
* **增加设计规范 PDF**（颜色、间距、图标、动效、响应式规则）。
* **提供完整的开发规范**（Architecture、组件目录、命名规则、Preview 规范、Adaptive API 使用方式）。
* **规划插件化 Source UI**（WebDAV、Plex、Emby 等统一接入规范），与 `SourceAdapter` 架构保持一致。

这样，TidePlayer 的设计系统就不仅是一套视觉稿，而是可以长期演进、支持跨平台开发和开源协作的完整产品设计规范。
