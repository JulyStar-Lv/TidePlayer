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