import { useState, useEffect, useRef, useCallback } from "react";
import { createPortal } from "react-dom";
import ReactMarkdown from "react-markdown";
import DragIndicatorRoundedIcon from "@mui/icons-material/DragIndicatorRounded";
import VerticalAlignTopRoundedIcon from "@mui/icons-material/VerticalAlignTopRounded";
import { motion, AnimatePresence, Reorder, useDragControls, useReducedMotion } from "motion/react";
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";
import appIconUrl from "../../../artwork/app-icon.svg";
import embyIconUrl from "../assets/source-icons/emby.png";
import navidromeIconUrl from "../assets/source-icons/navidrome.png";
import oneDriveIconUrl from "../assets/source-icons/onedrive.svg";
import openSubsonicIconUrl from "../assets/source-icons/opensubsonic.png";
import {
  AppearanceColorSettings,
  ThemeColorDesignSpec,
  ThemeColorPickerDialog,
} from "./ThemeColorDesign";
import {
  Play, Pause, SkipForward, SkipBack, Heart, CirclePlay,
  Search, Home, Library, Settings, Music2,
  ChevronUp, ChevronRight, ChevronLeft, ChevronDown,
  Volume2, VolumeX, Shuffle, Repeat,
  MoreHorizontal, MoreVertical, Download, Share2, Sun, Moon,
  List, Grid3x3, Plus, X, Bell,
  Disc3, Headphones, Speaker, Wifi, Bluetooth, Signal, BatteryFull,
  Database, Cloud, HardDrive, ArrowLeft, ArrowRight,
  Zap, SlidersHorizontal, Folder, Server,
  RefreshCw, AlertCircle, Star, Bookmark,
  Activity, Palette, Smartphone, Tablet, Monitor,
  Radio, Package, BarChart2, Sparkles, ListMusic,
  Hash, Layers, Check, CheckCircle2, Gauge, Filter,
  LayoutDashboard, Code2, Maximize2, Minimize2,
  PanelRight, PanelRightClose, AlignLeft, Cpu,
  Plug, Puzzle, FileText, GitBranch, Terminal,
  TrendingUp, Clock, CalendarDays, Flame, Timer, Trophy,
  Heart as HeartIcon, Star as StarIcon,
  FolderOpen, Wifi as WifiIcon, Music, Mic2,
  GripVertical, Trash2, Eye, EyeOff, LocateFixed, Pencil, Pin
} from "lucide-react";

const cn = (...i: unknown[]) => twMerge(clsx(i));
const APP_VERSION = __APP_VERSION__;
const APP_VERSION_CODE = __APP_VERSION_CODE__;
const preventMouseFocus = (event: React.PointerEvent<HTMLButtonElement>) => {
  if (event.pointerType === "mouse") event.preventDefault();
};
const LIST_ROW_TRANSITION = {type:"spring" as const,stiffness:400,damping:30};
const LIST_ROW_INTERACTION = "rounded-sm outline-none transition-colors duration-[180ms] hover:bg-muted/50 active:bg-muted/70 focus-visible:ring-2 focus-visible:ring-primary/40";

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────
interface Song {
  id: number;
  title: string;
  artist: string;
  album: string;
  duration: string;
  gradient: [string,string];
  liked: boolean;
  rating?: number;
  year?: number;
  fileType?: "flac"|"alac"|"mp3"|"aac";
  quality?: "lossless"|"hi-res"|"dolby"|"standard";
}
interface Album { id: number; title: string; artist: string; year: number; gradient: [string,string]; tracks: number; genre: string; }
interface Artist { id: number; name: string; followers: string; gradient: [string,string]; genre: string; initials: string; bio: string; }
interface Playlist { id: number; title: string; description: string; gradient: [string,string]; tracks: number; duration: string; }
type Page = "home"|"search"|"library"|"playlist"|"album"|"artist"|"listening"|"settings"|"design-system";
type DSSection = "cover"|"foundation"|"tokens"|"theme-colors"|"components"|"patterns"|"compose";
type LibTab = "songs"|"albums"|"artists"|"genres"|"folders"|"playlists"|"favorites"|"downloads"|"history"|"recently-added"|"recently-played"|"lossless"|"hi-res";

// ─────────────────────────────────────────────────────────────
// MOCK DATA
// ─────────────────────────────────────────────────────────────
const G: [string,string][] = [
  ["#FF5B8A","#7A6CFF"],["#7A6CFF","#3D9AFF"],["#FF8A3D","#FF5B8A"],
  ["#3DCA8A","#3D9AFF"],["#FFD93D","#FF8A3D"],["#3D9AFF","#7A6CFF"],
  ["#FF5B8A","#FF8A3D"],["#7A6CFF","#3DCA8A"],
];

// Cover images — index 0–7 maps to song ids 1–8
const COVERS = [
  "https://images.unsplash.com/photo-1485780974122-c91bb5dcf725?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1662652148730-51e8e0e08320?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1613332953881-192eda038848?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1579133945504-259ab646fed8?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1529002045502-d3c5024f8e24?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1452696193712-6cabf5103b63?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1552031536-c64b001fdc05?auto=format&fit=crop&w=900&h=900&q=85",
  "https://images.unsplash.com/photo-1484336301471-031d6c7452dc?auto=format&fit=crop&w=900&h=900&q=85",
];

// cover(id) – id is 1-based (song/album id); any id cycles safely
function cover(id: number): string { return COVERS[(id - 1) % COVERS.length]; }

const SONGS: Song[] = [
  { id:1, title:"Midnight Cascade", artist:"Luna Waves", album:"Tidal Drift", duration:"3:42", gradient:G[0], liked:true, rating:5, year:2024, fileType:"flac", quality:"hi-res" },
  { id:2, title:"Neon Undertow", artist:"Prism Circuit", album:"Voltage Dreams", duration:"4:18", gradient:G[1], liked:false, rating:4, year:2024, fileType:"alac", quality:"lossless" },
  { id:3, title:"Silver Tide", artist:"Coastal Drift", album:"Open Water", duration:"3:55", gradient:G[2], liked:true, rating:5, year:2023, fileType:"flac", quality:"standard" },
  { id:4, title:"Aurora Sequence", artist:"Polar Echo", album:"Northern Lights", duration:"5:02", gradient:G[3], liked:false, rating:4, year:2024, fileType:"aac", quality:"dolby" },
  { id:5, title:"Depth Protocol", artist:"Ocean Syntax", album:"Subsonic", duration:"3:30", gradient:G[4], liked:true, rating:3, year:2022, fileType:"mp3", quality:"standard" },
  { id:6, title:"Glass Architecture", artist:"Fractal Mind", album:"Prism", duration:"4:44", gradient:G[5], liked:false, rating:4, year:2023, fileType:"alac", quality:"lossless" },
  { id:7, title:"Resonance Fields", artist:"Wave Function", album:"Quantum", duration:"3:15", gradient:G[6], liked:true, rating:5, year:2022, fileType:"flac", quality:"standard" },
  { id:8, title:"Liminal Space", artist:"Threshold", album:"Between", duration:"5:30", gradient:G[7], liked:false, rating:3, year:2024, fileType:"aac", quality:"hi-res" },
];
const PLAYLIST_DEMO_SONGS: Song[] = Array.from({length:20},(_,index)=>({
  id:101+index,
  title:`Demo Track ${String(index+1).padStart(2,"0")}`,
  artist:`Demo Artist ${(index%5)+1}`,
  album:"Playlist Demo",
  duration:`${3+(index%3)}:${String((index*13+17)%60).padStart(2,"0")}`,
  gradient:G[index%G.length],
  liked:false,
}));
const ALBUMS: Album[] = [
  { id:1, title:"Tidal Drift", artist:"Luna Waves", year:2024, gradient:G[0], tracks:12, genre:"Electronic" },
  { id:2, title:"Voltage Dreams", artist:"Prism Circuit", year:2024, gradient:G[1], tracks:9, genre:"Synthwave" },
  { id:3, title:"Open Water", artist:"Coastal Drift", year:2023, gradient:G[2], tracks:11, genre:"Ambient" },
  { id:4, title:"Northern Lights", artist:"Polar Echo", year:2024, gradient:G[3], tracks:8, genre:"IDM" },
  { id:5, title:"Subsonic", artist:"Ocean Syntax", year:2023, gradient:G[4], tracks:14, genre:"Techno" },
  { id:6, title:"Glass Architecture", artist:"Fractal Mind", year:2024, gradient:G[5], tracks:10, genre:"Post-Rock" },
  { id:7, title:"Quantum", artist:"Wave Function", year:2024, gradient:G[6], tracks:7, genre:"Experimental" },
  { id:8, title:"Between", artist:"Threshold", year:2023, gradient:G[7], tracks:13, genre:"Shoegaze" },
];
const ARTISTS: Artist[] = [
  { id:1, name:"Luna Waves", followers:"2.4M", gradient:G[0], genre:"Electronic", initials:"LW", bio:"Luna Waves blends nocturnal electronics with expansive coastal ambience, building immersive songs around luminous synths and slow-moving rhythms." },
  { id:2, name:"Prism Circuit", followers:"1.8M", gradient:G[1], genre:"Synthwave", initials:"PC", bio:"Prism Circuit turns neon-lit melodies, analog textures, and precise drum programming into cinematic synthwave made for late-night listening." },
  { id:3, name:"Coastal Drift", followers:"890K", gradient:G[2], genre:"Ambient", initials:"CD", bio:"Coastal Drift creates patient ambient music inspired by open water, shifting weather, and the quiet spaces between field recordings." },
  { id:4, name:"Polar Echo", followers:"3.1M", gradient:G[3], genre:"IDM", initials:"PE", bio:"Polar Echo explores detailed electronic rhythms and glacial harmony, pairing intricate production with a distinctly human sense of movement." },
  { id:5, name:"Ocean Syntax", followers:"670K", gradient:G[4], genre:"Techno", initials:"OS", bio:"Ocean Syntax combines deep techno pressure with fluid low-end design, drawing a line between underground club music and oceanic soundscapes." },
  { id:6, name:"Fractal Mind", followers:"1.2M", gradient:G[5], genre:"Post-Rock", initials:"FM", bio:"Fractal Mind builds wide-screen post-rock from layered guitars, electronic detail, and patient crescendos that reward focused listening." },
];
function tracksForAlbum(album:Album):Song[] {
  const albumSongs = SONGS.filter(song=>song.album===album.title);
  const fillerTracks = PLAYLIST_DEMO_SONGS
    .slice(0,Math.max(0,album.tracks-albumSongs.length))
    .map((song,index):Song=>({
      ...song,
      id:1000+album.id*100+index,
      artist:album.artist,
      album:album.title,
      gradient:album.gradient,
    }));
  return [...albumSongs,...fillerTracks].slice(0,album.tracks);
}
const PLAYLISTS: Playlist[] = [
  { id:1, title:"Evening Frequencies", description:"Deep electronic for golden hour", gradient:G[0], tracks:24, duration:"1h 32m" },
  { id:2, title:"Spatial Audio Mix", description:"Hi-Res Dolby Atmos collection", gradient:G[1], tracks:18, duration:"1h 08m" },
  { id:3, title:"Deep Focus", description:"Minimal ambient for concentration", gradient:G[2], tracks:32, duration:"2h 15m" },
  { id:4, title:"Night Drive", description:"Synthwave for late-night cruising", gradient:G[3], tracks:20, duration:"1h 22m" },
  { id:5, title:"Sunrise Protocol", description:"Gentle morning electronic", gradient:G[4], tracks:16, duration:"58m" },
  { id:6, title:"System Override", description:"High-energy techno and industrial", gradient:G[5], tracks:28, duration:"1h 45m" },
];
const FAVORITE_PLAYLIST: Playlist = {
  id:9,
  title:"My Favorites",
  description:"Your liked songs",
  gradient:G[0],
  tracks:SONGS.filter(song=>song.liked).length,
  duration:"14m 22s",
};
const LISTENING_MINUTES = [0,18,42,27,64,35,0,52,81,24,39,0,68,46,30,12,75,0,58,44,20,0,33,71,54,26,48,0,62,38,19,84,43,0,57,29,66,34,0,76,41,23,59,88,0,36,65,28,72,45,0,53,31,79,47,18];
const LISTENING_DAYS = LISTENING_MINUTES.map((minutes,index)=>({
  id:index,
  label:index===LISTENING_MINUTES.length-1
    ?"Today"
    :LISTENING_MINUTES.length-index-1===1?"Yesterday":`${LISTENING_MINUTES.length-index-1} days ago`,
  minutes,
}));
const LISTENING_RANKINGS = [
  { song:SONGS[0], plays:32, minutes:268 },
  { song:SONGS[3], plays:25, minutes:211 },
  { song:SONGS[1], plays:21, minutes:184 },
  { song:SONGS[5], plays:18, minutes:156 },
  { song:SONGS[2], plays:27, minutes:129 },
  { song:SONGS[7], plays:14, minutes:198 },
];
// Midnight Cascade — synced lyrics (time in seconds)
const LYRICS: { time:number; section:string; text:string }[] = [
  { time:18,  section:"Verse 1",      text:"Streetlights shimmer on the rain" },
  { time:26,  section:"Verse 1",      text:"Your voice comes softly through the train" },
  { time:34,  section:"Verse 1",      text:"I trace the river down the window glass" },
  { time:42,  section:"Verse 1",      text:"Every station sounds like something from the past" },
  { time:54,  section:"Pre-Chorus",   text:"If the signal fades, stay on the line" },
  { time:62,  section:"Pre-Chorus",   text:"I can find you in the noise tonight" },
  { time:74,  section:"Chorus",       text:"Midnight cascade, falling over me" },
  { time:82,  section:"Chorus",       text:"Silver on the water, static in the street" },
  { time:90,  section:"Chorus",       text:"Midnight cascade, don’t let the current end" },
  { time:99,  section:"Chorus",       text:"Hold me in the echo till the morning comes again" },
  { time:115, section:"Verse 2",      text:"Blue from the dashboard paints your face" },
  { time:123, section:"Verse 2",      text:"We miss the last turn just to keep this place" },
  { time:131, section:"Verse 2",      text:"The skyline flickers like a warning sign" },
  { time:139, section:"Verse 2",      text:"Your hand finds mine at exactly the right time" },
  { time:152, section:"Bridge",       text:"When every frequency goes quiet" },
  { time:160, section:"Bridge",       text:"And the city loses power" },
  { time:168, section:"Bridge",       text:"I’ll remember how you sounded" },
  { time:176, section:"Bridge",       text:"In the middle of that hour" },
  { time:188, section:"Final Chorus", text:"Midnight cascade, carry us to dawn" },
  { time:196, section:"Final Chorus", text:"Keep the signal burning after we are gone" },
  { time:208, section:"Final Chorus", text:"Silver on the water, moving through the blue" },
  { time:216, section:"Final Chorus", text:"Every road I follow leads me back to you" },
];
const LYRIC_TRANSLATIONS: Record<number,string> = {
  18:"街灯在雨幕中闪烁",
  26:"你的声音从列车那端轻轻传来",
  34:"我沿着车窗上的水痕描摹河流",
  42:"每一站都像来自往昔的回声",
  54:"如果信号消失，请别挂断",
  62:"今夜我会在噪声中找到你",
  74:"午夜瀑布般倾落在我身上",
  82:"银光落在水面，电流穿过街道",
  90:"夜色倾泻，别让这股潮流停下",
  99:"抱紧回声中的我，直到清晨再次到来",
  115:"仪表盘的蓝光映着你的脸",
  123:"我们故意错过最后一个路口，只为留住这里",
  131:"天际线闪烁得像一道警告",
  139:"你的手在恰好的时刻找到我的手",
  152:"当所有频率归于寂静",
  160:"当整座城市熄灭灯火",
  168:"我会记得你的声音",
  176:"在那一刻的中央",
  188:"午夜潮汐，带我们奔向黎明",
  196:"即使离去，也让信号继续燃烧",
  208:"水面泛着银光，穿行于蓝色夜幕",
  216:"我走过的每条路，最终都通向你",
};
const SONG_DURATION = 222; // 3:42 in seconds

// ─────────────────────────────────────────────────────────────
// PRIMITIVE COMPONENTS
// ─────────────────────────────────────────────────────────────
// CoverArt: img over gradient fallback, fills parent container
function CoverArt({ src, gradient, className, style, overlay, children }: {
  src: string; gradient: [string,string]; className?: string; style?: React.CSSProperties; overlay?: boolean; children?: React.ReactNode;
}) {
  return (
    <div className={cn("relative overflow-hidden", className)}
      style={{background:`linear-gradient(135deg,${gradient[0]},${gradient[1]})`,...style}}>
      <img src={src} alt="" className="absolute inset-0 w-full h-full object-cover"/>
      {overlay && <div className="absolute inset-0 bg-black/20"/>}
      {children}
    </div>
  );
}

// iPhone 17 Pro baseline: 59pt portrait status area and a compact 28pt landscape status area.
function MobileStatusBar({ inverse=false, glassProgress=0 }: { inverse?:boolean; glassProgress?:number }) {
  const ink = inverse ? "text-white" : "text-foreground";
  const normalizedGlassProgress = Math.max(0,Math.min(glassProgress,1));
  return (
    <div aria-hidden="true" className={cn("pointer-events-none absolute inset-x-0 top-0 z-[80] h-[59px] overflow-hidden lg:hidden landscape:h-0",ink)}>
      {!inverse&&(
        <>
          <span className="absolute inset-0 bg-background" style={{opacity:1-normalizedGlassProgress}}/>
          <span className="actionbar-liquid-glass absolute inset-0" style={{opacity:normalizedGlassProgress}}/>
        </>
      )}
      <span className="absolute left-7 top-[15px] text-[15px] font-semibold tracking-[-0.03em] landscape:hidden">9:41</span>
      <span className="absolute left-1/2 top-[10px] h-[37px] w-[126px] -translate-x-1/2 rounded-full bg-black shadow-[0_1px_0_rgba(255,255,255,0.06)] landscape:fixed landscape:left-auto landscape:right-[10px] landscape:top-1/2 landscape:h-[126px] landscape:w-[37px] landscape:translate-x-0 landscape:-translate-y-1/2"/>
      <span className="absolute right-7 top-[16px] flex items-center gap-[5px] landscape:hidden">
        <Signal className="h-[14px] w-[14px]" strokeWidth={2.4}/>
        <Wifi className="h-[14px] w-[14px]" strokeWidth={2.3}/>
        <BatteryFull className="h-[18px] w-[18px]" strokeWidth={2.25}/>
      </span>
    </div>
  );
}

function MobileHomeIndicator({ inverse=false }: { inverse?:boolean }) {
  return <span aria-hidden="true" className={cn("pointer-events-none absolute bottom-[9px] left-1/2 z-[70] h-[5px] w-[134px] -translate-x-1/2 rounded-full lg:hidden landscape:hidden",inverse?"bg-white/88":"bg-foreground/82")}/>;
}

function MobileLandscapeHomeIndicator({ inverse=false }: { inverse?:boolean }) {
  return <span aria-hidden="true" className={cn("pointer-events-none fixed bottom-[6px] left-1/2 z-[380] hidden h-[4px] w-[134px] -translate-x-1/2 rounded-full lg:hidden landscape:block",inverse?"bg-white/88":"bg-foreground/82")}/>;
}

function Btn({ children, variant="filled", size="md", className="", onClick, icon, iconOnly }: {
  children?: React.ReactNode; variant?:"filled"|"outlined"|"ghost"|"tonal"|"secondary"; size?:"sm"|"md"|"lg";
  className?:string; onClick?:()=>void; icon?:React.ReactNode; iconOnly?:boolean;
}) {
  const sz = { sm:"h-8 text-xs px-3 gap-1.5", md:"h-10 text-sm px-4 gap-2", lg:"h-12 text-base px-6 gap-2.5" };
  const isz = { sm:"h-8 w-8", md:"h-10 w-10", lg:"h-12 w-12" };
  const v = {
    filled:"bg-primary text-primary-foreground hover:opacity-90 active:scale-95",
    outlined:"border border-border text-foreground hover:bg-muted active:scale-95",
    ghost:"text-foreground hover:bg-muted active:scale-95",
    tonal:"bg-muted text-foreground hover:bg-muted/80 active:scale-95",
    secondary:"bg-[var(--button-secondary)] text-[var(--button-secondary-foreground)] hover:opacity-90 active:scale-95",
  };
  return (
    <button type="button" onPointerDown={preventMouseFocus} onClick={onClick} className={cn("inline-flex items-center justify-center font-semibold rounded-full transition-all duration-[180ms] shrink-0 select-none outline-none focus-visible:ring-2 focus-visible:ring-primary/40",
      iconOnly ? isz[size] : sz[size], v[variant], className)}>
      {icon && <span className={iconOnly?"":"shrink-0"}>{icon}</span>}
      {!iconOnly && children}
    </button>
  );
}

function DesignSwitch({ checked, onChange, label, ariaLabel, disabled=false }: {
  checked:boolean;
  onChange:(v:boolean)=>void;
  label?:string;
  ariaLabel?:string;
  disabled?:boolean;
}) {
  return (
    <label className={cn("flex items-center gap-3 select-none",disabled?"cursor-not-allowed opacity-45":"cursor-pointer")}>
      {label && <span className="text-sm text-foreground">{label}</span>}
      <button type="button" role="switch" aria-label={ariaLabel??label} aria-checked={checked} disabled={disabled} onClick={()=>onChange(!checked)}
        className={cn("relative w-12 h-7 rounded-full transition-all duration-300 shrink-0", checked?"bg-primary":"bg-switch-background")}>
        <motion.div layout transition={{ type:"spring", stiffness:700, damping:35 }}
          className="absolute top-1 w-5 h-5 bg-white rounded-full shadow-md"
          style={{ left: checked ? "calc(100% - 24px)" : "4px" }} />
      </button>
    </label>
  );
}

function DesignSlider({ value, onChange, label, accent }: { value:number; onChange:(v:number)=>void; label?:string; accent?:string }) {
  const pct = value;
  return (
    <div className="flex flex-col gap-2 w-full">
      {label && <span className="text-xs text-muted-foreground font-medium">{label}</span>}
      <div className="relative h-5 flex items-center w-full group cursor-pointer">
        <div className="absolute inset-x-0 h-1.5 bg-muted rounded-full overflow-hidden">
          <div className="h-full rounded-full transition-all" style={{ width:`${pct}%`, background:accent||"var(--brand-pink)" }} />
        </div>
        <input type="range" min={0} max={100} value={value} onChange={e=>onChange(Number(e.target.value))}
          className="absolute inset-0 w-full opacity-0 cursor-pointer h-full" />
        <div className="absolute w-5 h-5 bg-white rounded-full shadow-md border-2"
          style={{ left:`calc(${pct}% - 10px)`, borderColor:accent||"var(--brand-pink)" }} />
      </div>
    </div>
  );
}

function PillTabs({ tabs, active, onChange }: { tabs:{id:string;label:string}[]; active:string; onChange:(id:string)=>void }) {
  return (
    <div className="flex items-center gap-2 overflow-x-auto pb-1 hide-scrollbar">
      {tabs.map(t => (
        <button key={t.id} onClick={()=>onChange(t.id)}
          className={cn("shrink-0 px-4 h-9 rounded-full text-sm font-semibold transition-all",
            active===t.id?"bg-primary text-primary-foreground":"bg-muted text-muted-foreground hover:text-foreground")}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

function SegTabs({ tabs, active, onChange }: { tabs:{id:string;label:string}[]; active:string; onChange:(id:string)=>void }) {
  return (
    <div className="inline-flex bg-muted rounded-2xl p-1 gap-1">
      {tabs.map(t => (
        <button key={t.id} onClick={()=>onChange(t.id)}
          className={cn("relative px-4 h-9 rounded-xl text-sm font-semibold transition-all",
            active===t.id?"text-foreground":"text-muted-foreground hover:text-foreground")}>
          {active===t.id && <motion.div layoutId="seg-bg" className="absolute inset-0 bg-card rounded-xl shadow-sm" />}
          <span className="relative z-10">{t.label}</span>
        </button>
      ))}
    </div>
  );
}

function UnderlineTabs({ tabs, active, onChange }: { tabs:{id:string;label:string}[]; active:string; onChange:(id:string)=>void }) {
  return (
    <div className="flex items-center border-b border-border">
      {tabs.map(t => (
        <button key={t.id} onClick={()=>onChange(t.id)}
          className={cn("relative px-4 py-3 text-sm font-semibold transition-all", active===t.id?"text-primary":"text-muted-foreground hover:text-foreground")}>
          {t.label}
          {active===t.id && <motion.div layoutId="tab-line" className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />}
        </button>
      ))}
    </div>
  );
}

function SectionHeader({ title, action, onAction }: { title:string; action?:string; onAction?:()=>void }) {
  return (
    <div className="flex items-center justify-between mb-4">
      <h2 className="text-[20px] font-semibold text-foreground">{title}</h2>
      {action && <button onClick={onAction} className="text-sm text-primary font-semibold hover:text-primary/80 transition-all duration-[180ms] flex items-center gap-1">{action} <ChevronRight className="w-3.5 h-3.5" /></button>}
    </div>
  );
}

function HomeSectionHeader({ title, icon, onClick }: { title:string; icon:React.ReactNode; onClick:()=>void }) {
  return (
    <motion.button type="button" whileTap={{ scale:0.985 }} onPointerDown={preventMouseFocus} onClick={onClick}
      className="group -mx-2 mb-4 flex w-[calc(100%+16px)] items-center gap-2.5 rounded-xl px-2 py-1.5 text-left transition-colors duration-[180ms] hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/35">
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
        {icon}
      </span>
      <h2 className="text-[20px] font-semibold text-foreground">{title}</h2>
      <ChevronRight strokeWidth={2.25} className="h-6 w-6 shrink-0 text-muted-foreground transition-transform duration-[180ms] group-active:translate-x-0.5"/>
    </motion.button>
  );
}

function SkeletonBlock({ className="" }: { className?:string }) {
  return <div className={cn("bg-muted rounded-2xl animate-pulse", className)} />;
}

function EmptyState({ icon, title, subtitle, action, onAction }: { icon:React.ReactNode; title:string; subtitle?:string; action?:string; onAction?:()=>void }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-8 text-center gap-4">
      <div className="w-16 h-16 rounded-3xl bg-muted flex items-center justify-center text-muted-foreground">{icon}</div>
      <div><p className="font-semibold text-foreground mb-1">{title}</p>{subtitle&&<p className="text-sm text-muted-foreground">{subtitle}</p>}</div>
      {action&&<Btn variant="tonal" onClick={onAction}>{action}</Btn>}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// MUSIC CARD COMPONENTS
// ─────────────────────────────────────────────────────────────
function AlbumCard({ album, size="md", action="play", onClick }: { album:Album; size?:"sm"|"md"|"lg"; action?:"play"|"open"; onClick?:()=>void }) {
  const s = { sm:{a:120,w:"w-[120px]"}, md:{a:160,w:"w-[160px]"}, lg:{a:200,w:"w-[200px]"} }[size];
  return (
    <motion.button type="button" disabled={!onClick} whileTap={onClick?{scale:0.97}:undefined} transition={{type:"spring",stiffness:400,damping:30}}
      onPointerDown={preventMouseFocus}
      onClick={onClick} className={cn("shrink-0 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 rounded-[14px]",onClick?"cursor-pointer group":"cursor-default",s.w)}>
      <CoverArt src={cover(album.id)} gradient={album.gradient} className="rounded-[14px] shadow-lg mb-3" style={{width:s.a,height:s.a}}>
        {onClick&&<div className="absolute inset-0 bg-gradient-to-t from-black/30 to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end justify-end p-3">
          <div className="w-9 h-9 bg-white/95 rounded-full flex items-center justify-center shadow-lg">
            {action==="open"
              ? <ChevronRight className="h-4 w-4" style={{color:album.gradient[0]}}/>
              : <Play className="w-4 h-4 ml-0.5" style={{color:album.gradient[0]}}/>}
          </div>
        </div>}
      </CoverArt>
      <p className="text-sm font-semibold text-foreground truncate">{album.title}</p>
      <p className="text-xs text-muted-foreground truncate mt-0.5">{album.artist} · {album.year}</p>
    </motion.button>
  );
}

function ArtistCard({ artist, onClick, showFollowers=true }: { artist:Artist; onClick?:()=>void; showFollowers?:boolean }) {
  return (
    <motion.button type="button" disabled={!onClick} whileTap={onClick?{scale:0.97}:undefined} transition={{type:"spring",stiffness:400,damping:30}}
      onPointerDown={preventMouseFocus}
      onClick={onClick} className={cn("shrink-0 w-[128px] rounded-[14px] text-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40",onClick?"cursor-pointer group":"cursor-default")}>
      <div className="w-[128px] h-[128px] rounded-full overflow-hidden shadow-lg mb-3 relative mx-auto flex items-center justify-center"
        style={{background:`linear-gradient(135deg,${artist.gradient[0]},${artist.gradient[1]})`}}>
        <span className="text-3xl font-bold text-white/90 select-none">{artist.initials}</span>
        <div className="absolute inset-0 rounded-full bg-black/0 group-hover:bg-black/10 transition-colors"/>
      </div>
      <p className="text-sm font-semibold text-foreground truncate">{artist.name}</p>
      {showFollowers&&<p className="text-xs text-muted-foreground mt-0.5">{artist.followers}</p>}
    </motion.button>
  );
}

function PlaylistCard({ playlist, onClick, showMeta=true }: { playlist:Playlist; onClick?:()=>void; showMeta?:boolean }) {
  return (
    <motion.button type="button" disabled={!onClick} whileTap={onClick?{scale:0.97}:undefined} transition={{type:"spring",stiffness:400,damping:30}}
      onPointerDown={preventMouseFocus}
      onClick={onClick} className={cn("shrink-0 w-[160px] text-left rounded-[14px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40",onClick?"cursor-pointer group":"cursor-default")}>
      <CoverArt src={cover(playlist.id)} gradient={playlist.gradient} className="w-[160px] h-[160px] rounded-[14px] shadow-lg mb-3">
        {showMeta&&(
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent flex items-end p-3">
            <p className="text-xs text-white/80 font-medium">{playlist.tracks} tracks · {playlist.duration}</p>
          </div>
        )}
        {onClick&&<div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
          <div className="w-9 h-9 bg-white/95 rounded-full flex items-center justify-center shadow-lg"><Play className="w-4 h-4 ml-0.5" style={{color:playlist.gradient[0]}}/></div>
        </div>}
      </CoverArt>
      <p className="text-sm font-semibold text-foreground truncate">{playlist.title}</p>
      <p className="text-xs text-muted-foreground mt-0.5 truncate">{playlist.description}</p>
    </motion.button>
  );
}

function SongRowText({ song, active=false }: { song:Song; active?:boolean }) {
  return (
    <div className="min-w-0 flex-1">
      <div className="flex items-center gap-2">
        <p className={cn("truncate text-sm font-semibold",active?"text-primary":"text-foreground")}>{song.title}</p>
      </div>
      <p className={cn("mt-0.5 truncate text-xs font-normal",active?"text-primary":"text-muted-foreground")}>{song.artist} · {song.album}</p>
    </div>
  );
}

function MusicCard({ song, onPlay, isPlaying, highlightPlaying=true, showDuration=true, coverClassName, trackNumber }: {
  song:Song; onPlay:(s:Song)=>void; isPlaying?:boolean; highlightPlaying?:boolean; showDuration?:boolean; coverClassName?:string; trackNumber?:number;
}) {
  return (
    <motion.button type="button" whileTap={{scale:0.985}} transition={LIST_ROW_TRANSITION}
      onPointerDown={preventMouseFocus}
      onClick={()=>onPlay(song)}
      className={cn("flex w-full items-center gap-4 px-3.5 py-2.5 cursor-pointer text-left group border-b border-border/40 last:border-0",LIST_ROW_INTERACTION,isPlaying&&highlightPlaying&&"bg-primary/10 border-primary/20")}>
      {trackNumber!==undefined ? (
        <span className="w-10 shrink-0 text-center font-mono text-xs tabular-nums text-muted-foreground">{trackNumber}</span>
      ) : (
        <CoverArt src={cover(song.id)} gradient={song.gradient} className={cn("w-10 h-10 rounded-[10px] shrink-0 flex items-center justify-center",coverClassName)}>
          {isPlaying ? (
            <div className="absolute inset-0 flex items-center justify-center bg-black/30">
              <div className="flex items-end gap-0.5 h-4">
                {[1,2,3,4].map(i=>(
                  <motion.div key={i} className="w-1 bg-white rounded-full"
                    animate={{height:["40%","100%","60%","80%"]}}
                    transition={{duration:0.8,repeat:Infinity,delay:i*0.1,ease:"easeInOut"}}/>
                ))}
              </div>
            </div>
          ) : (
            <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 bg-black/30 transition-opacity">
              <Play className="w-4 h-4 text-white ml-0.5"/>
            </div>
          )}
        </CoverArt>
      )}
      <SongRowText song={song} active={!!isPlaying&&highlightPlaying}/>
      <div className={cn("flex shrink-0 items-center gap-2",trackNumber!==undefined&&"w-14 justify-between")}>
        {showDuration&&<span className="text-xs text-muted-foreground font-mono">{song.duration}</span>}
        <span className={cn("opacity-0 group-hover:opacity-100 transition-opacity",song.liked?"opacity-100":"")}>
          <Heart className={cn("w-4 h-4",song.liked?"fill-primary text-primary":"text-muted-foreground")}/>
        </span>
      </div>
    </motion.button>
  );
}

function PlaylistTrackRow({ song, trackNumber, active, onPlay }: {
  song:Song; trackNumber:number; active:boolean; onPlay:(song:Song)=>void;
}) {
  const [liked,setLiked] = useState(song.liked);

  return (
    <div className="flex h-14 w-full items-center border-b border-border/40 px-3.5">
      <motion.button type="button" whileTap={{scale:0.985}} transition={LIST_ROW_TRANSITION}
        onPointerDown={preventMouseFocus} onClick={()=>onPlay(song)}
        className="flex h-full min-w-0 flex-1 items-center gap-4 rounded-sm text-left outline-none hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-primary/40">
        <span className="flex w-10 shrink-0 items-center justify-center">
          {active ? (
            <span className="flex h-4 items-end gap-0.5" aria-label="Now playing">
              {[1,2,3,4].map(index=>(
                <motion.span key={index} className="w-1 rounded-full bg-primary"
                  animate={{height:["35%","100%","55%","80%"]}}
                  transition={{duration:0.8,repeat:Infinity,delay:index*0.1,ease:"easeInOut"}}/>
              ))}
            </span>
          ) : (
            <span className="font-mono text-xs tabular-nums text-muted-foreground">{trackNumber}</span>
          )}
        </span>
        <SongRowText song={song} active={active}/>
      </motion.button>
      <div className="flex w-16 shrink-0 items-center justify-end">
        <button type="button" aria-label={liked?`Remove ${song.title} from favorites`:`Add ${song.title} to favorites`}
          aria-pressed={liked} onPointerDown={preventMouseFocus} onClick={()=>setLiked(value=>!value)}
          className="flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40">
          <Heart className={cn("h-4 w-4",liked&&"fill-primary text-primary")}/>
        </button>
        <button type="button" aria-label={`More actions for ${song.title}`} onPointerDown={preventMouseFocus}
          className="flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40">
          <MoreVertical className="h-4 w-4"/>
        </button>
      </div>
    </div>
  );
}

function PlaylistDetailPage({ playlist, initialTracks, collectionType="playlist", allowEditing=true, currentSong, isPlaying, onBack, onPlay }: {
  playlist:Playlist;
  initialTracks?:Song[];
  collectionType?:"playlist"|"album";
  allowEditing?:boolean;
  currentSong:Song|null;
  isPlaying:boolean;
  onBack:()=>void;
  onPlay:(song:Song)=>void;
}) {
  const [headerCollapsed,setHeaderCollapsed] = useState(false);
  const [editing,setEditing] = useState(false);
  const [selectedTrackIds,setSelectedTrackIds] = useState<Set<number>>(()=>new Set());
  const playlistPageRef = useRef<HTMLDivElement>(null);
  const trackRowRefs = useRef<Map<number,HTMLElement>>(new Map());
  const baseTracks = initialTracks??(playlist.tracks===0
    ? []
    : playlist.title==="My Favorites"
      ? SONGS.filter(song=>song.liked)
      : [
          ...SONGS.map((_,index)=>SONGS[(index+Math.max(playlist.id-1,0))%SONGS.length]),
          ...PLAYLIST_DEMO_SONGS,
        ]);
  const [orderedTracks,setOrderedTracks] = useState<Song[]>(baseTracks);
  const allSelected = orderedTracks.length>0&&orderedTracks.every(song=>selectedTrackIds.has(song.id));
  const currentTrackAvailable = !!currentSong&&orderedTracks.some(song=>song.id===currentSong.id);

  useEffect(() => {
    setOrderedTracks(baseTracks);
    setSelectedTrackIds(new Set());
    setEditing(false);
  },[playlist.id]);

  const toggleEditing = () => {
    setEditing(value=>!value);
    setSelectedTrackIds(new Set());
  };
  const toggleTrackSelection = (id:number) => {
    setSelectedTrackIds(previous => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };
  const toggleSelectAll = () => {
    setSelectedTrackIds(allSelected?new Set():new Set(orderedTracks.map(song=>song.id)));
  };
  const locateCurrentTrack = () => {
    if (!currentSong) return;
    const row = trackRowRefs.current.get(currentSong.id);
    row?.scrollIntoView({
      behavior:window.matchMedia("(prefers-reduced-motion: reduce)").matches?"auto":"smooth",
      block:"center",
    });
  };

  useEffect(() => {
    const scroller = playlistPageRef.current?.closest("main");
    if (!scroller) return;
    let touchStartY = 0;
    const onWheel = (event:WheelEvent) => {
      if (event.deltaY > 12 && !editing) setHeaderCollapsed(true);
      if (event.deltaY < -12 && scroller.scrollTop <= 2) setHeaderCollapsed(false);
    };
    const onTouchStart = (event:TouchEvent) => {
      touchStartY = event.touches.item(0)?.clientY ?? 0;
    };
    const onTouchMove = (event:TouchEvent) => {
      const currentY = event.touches.item(0)?.clientY ?? touchStartY;
      const delta = touchStartY-currentY;
      if (delta > 24 && !editing) {
        setHeaderCollapsed(true);
        touchStartY = currentY;
      } else if (delta < -24 && scroller.scrollTop <= 2) {
        setHeaderCollapsed(false);
        touchStartY = currentY;
      }
    };
    setHeaderCollapsed(false);
    scroller.addEventListener("wheel",onWheel,{ passive:true });
    scroller.addEventListener("touchstart",onTouchStart,{ passive:true });
    scroller.addEventListener("touchmove",onTouchMove,{ passive:true });
    return () => {
      scroller.removeEventListener("wheel",onWheel);
      scroller.removeEventListener("touchstart",onTouchStart);
      scroller.removeEventListener("touchmove",onTouchMove);
    };
  },[editing,playlist.id]);

  return (
    <div ref={playlistPageRef} className="mx-auto w-full max-w-[960px] px-5 pb-8 lg:px-8 lg:pt-4">
      <div className="sticky top-0 z-30 -mx-5 flex h-14 items-center justify-between bg-background/90 px-4 backdrop-blur-xl supports-[backdrop-filter]:bg-background/80 lg:static lg:mx-0 lg:bg-transparent lg:px-0 lg:backdrop-blur-none">
        <button type="button" aria-label="Back" onPointerDown={preventMouseFocus} onClick={onBack}
          className="flex h-10 w-10 items-center justify-center rounded-full text-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
          <ArrowLeft className="h-5 w-5"/>
        </button>
        <AnimatePresence>
          {headerCollapsed&&(
            <motion.p initial={{opacity:0,y:5}} animate={{opacity:1,y:0}} exit={{opacity:0,y:5}}
              className="pointer-events-none absolute left-14 right-14 truncate text-left text-[16px] font-semibold text-foreground lg:hidden">
              {playlist.title}
            </motion.p>
          )}
        </AnimatePresence>
        <button type="button" aria-label={`More ${collectionType} actions`}
          className="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40">
          <MoreHorizontal className="h-5 w-5"/>
        </button>
      </div>

      <AnimatePresence initial={false}>
        {!headerCollapsed&&(
      <motion.section initial={{opacity:0,height:0,y:-10}} animate={{opacity:1,height:"auto",y:0}} exit={{opacity:0,height:0,y:-10}}
        transition={{duration:0.2,ease:"easeOut"}} className="overflow-hidden pt-3 lg:pt-5">
        <div className="flex items-center gap-4 sm:items-end sm:gap-7">
        <CoverArt src={cover(playlist.id)} gradient={playlist.gradient}
          className="aspect-square w-[112px] shrink-0 rounded-[18px] shadow-2xl sm:w-[220px] sm:rounded-[24px] lg:w-[240px]"/>
        <div className="min-w-0 flex-1 pb-0.5 text-left sm:pb-1">
          <h1 className="text-[24px] font-bold leading-[29px] text-foreground sm:text-[36px] sm:leading-[42px]">{playlist.title}</h1>
          <p className="mt-1 line-clamp-2 text-[12px] leading-4 text-muted-foreground sm:mt-2 sm:text-sm sm:leading-5">{playlist.description}</p>
          <p className="mt-3 text-xs font-medium text-muted-foreground">{playlist.tracks} tracks · {playlist.duration}</p>
        </div>
        </div>
      </motion.section>
        )}
      </AnimatePresence>

      <div className={cn(
        "sticky top-14 z-20 -mx-5 flex items-center gap-2 bg-background/90 px-5 pb-3 pt-3 backdrop-blur-xl supports-[backdrop-filter]:bg-background/80 sm:pt-5 lg:static lg:mx-0 lg:bg-transparent lg:px-0 lg:backdrop-blur-none",
        headerCollapsed&&"pt-1 sm:pt-1",
      )}>
        {editing ? (
          <button type="button" aria-pressed={allSelected} onClick={toggleSelectAll}
            className="flex h-10 items-center gap-2 rounded-full pl-2 pr-2 text-sm font-semibold text-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
            <span className={cn("flex h-[18px] w-[18px] items-center justify-center rounded-[6px] border",allSelected?"border-primary bg-primary text-primary-foreground":"border-border bg-card")}>
              {allSelected&&<Check className="h-3 w-3"/>}
            </span>
            {allSelected?"Deselect All":"Select All"}
            {!!selectedTrackIds.size&&<span className="text-muted-foreground">({selectedTrackIds.size})</span>}
          </button>
        ) : (
          <button type="button" disabled={!orderedTracks.length} onClick={()=>orderedTracks[0]&&onPlay(orderedTracks[0])}
            className="flex h-10 items-center gap-2 rounded-full pl-2 pr-2 text-sm font-semibold text-primary outline-none transition-opacity hover:opacity-80 disabled:cursor-not-allowed disabled:opacity-35 focus-visible:ring-2 focus-visible:ring-primary/40">
            <span className="flex h-[18px] w-[18px] items-center justify-center"><Play className="h-4 w-4 fill-current"/></span>Play All
          </button>
        )}
        <button type="button" aria-label="Locate current song" disabled={!currentTrackAvailable} onClick={locateCurrentTrack}
          className="ml-auto flex h-10 w-10 items-center justify-center rounded-full bg-muted text-foreground outline-none transition-colors hover:bg-muted/80 disabled:cursor-not-allowed disabled:opacity-35 focus-visible:ring-2 focus-visible:ring-primary/40">
          <LocateFixed className="h-[18px] w-[18px]"/>
        </button>
        {allowEditing&&(
          <button type="button" aria-label={editing?"Finish editing":"Edit playlist"} aria-pressed={editing} onClick={toggleEditing}
            className={cn("flex h-10 w-10 items-center justify-center rounded-full outline-none transition-colors focus-visible:ring-2 focus-visible:ring-primary/40",editing?"bg-primary text-primary-foreground":"bg-muted text-foreground hover:bg-muted/80")}>
            {editing?<Check className="h-[18px] w-[18px]"/>:<Pencil className="h-[18px] w-[18px]"/>}
          </button>
        )}
      </div>

      <section aria-label={collectionType==="album"?"Album tracks":"Playlist tracks"} className="-ml-4">
        {orderedTracks.length ? (
          editing ? (
            <Reorder.Group as="ul" axis="y" values={orderedTracks} onReorder={setOrderedTracks} className="overflow-hidden">
              {orderedTracks.map(song=>(
                <Reorder.Item as="li" key={song.id} value={song} whileDrag={{scale:1.015,boxShadow:"0 12px 34px rgba(0,0,0,0.24)"}}
                  ref={node => {
                    if (node) trackRowRefs.current.set(song.id,node);
                    else trackRowRefs.current.delete(song.id);
                  }}
                  className="flex h-14 touch-none select-none items-center gap-4 border-b border-border/40 px-3.5 py-2.5 last:border-0">
                  <button type="button" aria-label={`Select ${song.title}`} aria-pressed={selectedTrackIds.has(song.id)} onClick={()=>toggleTrackSelection(song.id)}
                    className="flex h-9 w-10 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
                    <span className={cn("flex h-5 w-5 items-center justify-center rounded-[7px] border",selectedTrackIds.has(song.id)?"border-primary bg-primary text-primary-foreground":"border-border bg-card")}>
                      {selectedTrackIds.has(song.id)&&<Check className="h-3.5 w-3.5"/>}
                    </span>
                  </button>
                  <SongRowText song={song} active={isPlaying&&currentSong?.id===song.id}/>
                  <span className="flex w-16 shrink-0 justify-end">
                    <GripVertical className="h-5 w-5 cursor-grab text-muted-foreground active:cursor-grabbing"/>
                  </span>
                </Reorder.Item>
              ))}
            </Reorder.Group>
          ) : (
            <div className="overflow-hidden">
              {orderedTracks.map((song,index)=>(
                <div key={song.id} ref={node => {
                  if (node) trackRowRefs.current.set(song.id,node);
                  else trackRowRefs.current.delete(song.id);
                }}>
                  <PlaylistTrackRow song={song} onPlay={onPlay} trackNumber={index+1} active={isPlaying&&currentSong?.id===song.id}/>
                </div>
              ))}
            </div>
          )
        ) : (
          <div className="rounded-[24px] border border-border bg-card">
            <EmptyState icon={<Music className="h-7 w-7"/>} title="No songs yet"
              subtitle={collectionType==="album"?"Songs from this album will appear here.":"Songs added to this playlist will appear here."}/>
          </div>
        )}
      </section>
    </div>
  );
}

function AlbumDetailPage({ album, currentSong, isPlaying, onBack, onPlay }: {
  album:Album;
  currentSong:Song|null;
  isPlaying:boolean;
  onBack:()=>void;
  onPlay:(song:Song)=>void;
}) {
  const tracks = tracksForAlbum(album);
  const albumAsPlaylist:Playlist = {
    id:album.id,
    title:album.title,
    description:`${album.artist} · ${album.year} · ${album.genre}`,
    gradient:album.gradient,
    tracks:tracks.length,
    duration:libraryDuration(tracks),
  };

  return (
    <PlaylistDetailPage playlist={albumAsPlaylist} initialTracks={tracks} collectionType="album" allowEditing={false}
      currentSong={currentSong} isPlaying={isPlaying} onBack={onBack} onPlay={onPlay}/>
  );
}

function ArtistDetailPage({ artist, currentSong, isPlaying, onBack, onPlay, onOpenAlbum }: {
  artist:Artist;
  currentSong:Song|null;
  isPlaying:boolean;
  onBack:()=>void;
  onPlay:(song:Song)=>void;
  onOpenAlbum:(album:Album)=>void;
}) {
  const [view,setView] = useState<"albums"|"songs">("albums");
  const artistAlbums = ALBUMS.filter(album=>album.artist===artist.name);
  const artistSongs = artistAlbums.flatMap(tracksForAlbum);

  useEffect(()=>{
    setView("albums");
  },[artist.id]);

  return (
    <div className="mx-auto w-full max-w-[960px] px-5 pb-8 lg:px-8 lg:pt-4">
      <div className="sticky top-0 z-30 -mx-5 flex h-14 items-center justify-between bg-background/90 px-4 backdrop-blur-xl supports-[backdrop-filter]:bg-background/80 lg:static lg:mx-0 lg:bg-transparent lg:px-0 lg:backdrop-blur-none">
        <button type="button" aria-label="Back" onPointerDown={preventMouseFocus} onClick={onBack}
          className="flex h-10 w-10 items-center justify-center rounded-full text-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
          <ArrowLeft className="h-5 w-5"/>
        </button>
        <button type="button" aria-label="More artist actions"
          className="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40">
          <MoreHorizontal className="h-5 w-5"/>
        </button>
      </div>

      <section className="pt-3 lg:pt-5">
        <div className="flex items-center gap-4 sm:items-end sm:gap-7">
          <div className="flex aspect-square w-[112px] shrink-0 items-center justify-center rounded-full text-[30px] font-bold text-white shadow-2xl sm:w-[220px] sm:text-[52px] lg:w-[240px]"
            style={{background:`linear-gradient(135deg,${artist.gradient[0]},${artist.gradient[1]})`}}>
            {artist.initials}
          </div>
          <div className="min-w-0 flex-1 pb-0.5 text-left sm:pb-1">
            <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-primary">Artist</p>
            <h1 className="mt-1 text-[26px] font-bold leading-[31px] text-foreground sm:text-[38px] sm:leading-[44px]">{artist.name}</h1>
            <div className="mt-3 flex items-center gap-4 text-xs font-medium text-muted-foreground">
              <span><strong className="mr-1 text-sm font-semibold text-foreground">{artistAlbums.length}</strong>{artistAlbums.length===1?"album":"albums"}</span>
              <span><strong className="mr-1 text-sm font-semibold text-foreground">{artistSongs.length}</strong>{artistSongs.length===1?"song":"songs"}</span>
            </div>
          </div>
        </div>

        <div className="mt-5 rounded-[20px] bg-card px-4 py-3.5 sm:mt-6 sm:px-5 sm:py-4">
          <h2 className="text-xs font-semibold text-foreground">About</h2>
          <p className="mt-1.5 text-[13px] leading-5 text-muted-foreground sm:text-sm sm:leading-6">{artist.bio}</p>
        </div>
      </section>

      <div className="sticky top-14 z-20 -mx-5 bg-background/90 px-5 pb-3 pt-5 backdrop-blur-xl supports-[backdrop-filter]:bg-background/80 lg:static lg:mx-0 lg:bg-transparent lg:px-0 lg:backdrop-blur-none">
        <div className="grid h-10 grid-cols-2 rounded-full bg-muted p-1">
          {([
            {id:"albums" as const,label:`Albums (${artistAlbums.length})`},
            {id:"songs" as const,label:`All Songs (${artistSongs.length})`},
          ]).map(item=>(
            <button key={item.id} type="button" aria-pressed={view===item.id} onClick={()=>setView(item.id)}
              className={cn("rounded-full text-xs font-semibold outline-none transition-all focus-visible:ring-2 focus-visible:ring-primary/40",view===item.id?"bg-card text-foreground shadow-sm":"text-muted-foreground hover:text-foreground")}>
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {view==="albums" ? (
        artistAlbums.length ? (
          <section aria-label={`${artist.name} albums`} className="grid grid-cols-2 justify-items-center gap-x-4 gap-y-6 sm:grid-cols-3 xl:grid-cols-4">
            {artistAlbums.map(album=>(
              <AlbumCard key={album.id} album={album} action="open" onClick={()=>onOpenAlbum(album)}/>
            ))}
          </section>
        ) : (
          <div className="rounded-[24px] border border-border bg-card">
            <EmptyState icon={<Disc3 className="h-7 w-7"/>} title="No albums yet" subtitle="Albums by this artist will appear here."/>
          </div>
        )
      ) : (
        <section aria-label={`${artist.name} songs`}>
          <div className="mb-1 flex h-10 items-center">
            <button type="button" disabled={!artistSongs.length} onClick={()=>artistSongs[0]&&onPlay(artistSongs[0])}
              className="flex h-10 items-center gap-2 rounded-full px-2 text-sm font-semibold text-primary outline-none transition-opacity hover:opacity-80 disabled:cursor-not-allowed disabled:opacity-35 focus-visible:ring-2 focus-visible:ring-primary/40">
              <span className="flex h-[18px] w-[18px] items-center justify-center"><Play className="h-4 w-4 fill-current"/></span>Play All
            </button>
          </div>
          {artistSongs.length ? (
            <div className="-ml-4 overflow-hidden">
              {artistSongs.map((song,index)=>(
                <PlaylistTrackRow key={song.id} song={song} onPlay={onPlay} trackNumber={index+1}
                  active={isPlaying&&currentSong?.id===song.id}/>
              ))}
            </div>
          ) : (
            <div className="rounded-[24px] border border-border bg-card">
              <EmptyState icon={<Music className="h-7 w-7"/>} title="No songs yet" subtitle="Music by this artist will appear here."/>
            </div>
          )}
        </section>
      )}
    </div>
  );
}

function SourceCard({ source }: { source:{name:string;type:string;icon:React.ReactNode;status:"connected"|"syncing"|"error"|"idle";storage:string;tracks:number;gradient:[string,string]} }) {
  const sc = { connected:{l:"Connected",c:"var(--brand-green)"}, syncing:{l:"Syncing",c:"var(--brand-blue)"}, error:{l:"Error",c:"#FF4F4F"}, idle:{l:"Idle",c:"var(--muted-foreground)"} }[source.status];
  return (
    <div className="bg-card rounded-[24px] p-5 border border-border hover:border-primary/30 transition-all group">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-2xl flex items-center justify-center text-white" style={{background:`linear-gradient(135deg,${source.gradient[0]},${source.gradient[1]})`}}>{source.icon}</div>
          <div><p className="font-semibold text-foreground text-sm">{source.name}</p><p className="text-xs text-muted-foreground">{source.type}</p></div>
        </div>
        <div className="flex items-center gap-1.5"><div className="w-1.5 h-1.5 rounded-full" style={{background:sc.c}}/><span className="text-xs font-medium" style={{color:sc.c}}>{sc.l}</span></div>
      </div>
      <div className="grid grid-cols-2 gap-2 mb-4">
        <div className="bg-muted rounded-xl p-3"><p className="text-[10px] text-muted-foreground mb-0.5">Storage</p><p className="text-sm font-semibold text-foreground">{source.storage}</p></div>
        <div className="bg-muted rounded-xl p-3"><p className="text-[10px] text-muted-foreground mb-0.5">Tracks</p><p className="text-sm font-semibold text-foreground">{source.tracks.toLocaleString()}</p></div>
      </div>
      <div className="flex gap-2">
        <Btn variant="tonal" size="sm" icon={<RefreshCw className="w-3.5 h-3.5"/>} className="flex-1">Sync</Btn>
        <Btn variant="ghost" size="sm" icon={<FileText className="w-3.5 h-3.5"/>} iconOnly/>
        <Btn variant="ghost" size="sm" icon={<SlidersHorizontal className="w-3.5 h-3.5"/>} iconOnly/>
        <Btn variant="ghost" size="sm" icon={<MoreHorizontal className="w-3.5 h-3.5"/>} iconOnly/>
      </div>
    </div>
  );
}

function SettingItem({ label, subtitle, leading, trailing, onClick, danger }: { label:string; subtitle?:string; leading?:React.ReactNode; trailing?:React.ReactNode; onClick?:()=>void; danger?:boolean }) {
  return (
    <div onClick={onClick} className={cn("w-full flex items-center gap-4 px-4 py-3.5 transition-colors text-left group", onClick && "cursor-pointer hover:bg-muted/50")}>
      {leading && <div className="w-9 h-9 rounded-xl bg-muted flex items-center justify-center shrink-0 text-muted-foreground">{leading}</div>}
      <div className="flex-1 min-w-0">
        <p className={cn("text-sm font-medium",danger?"text-destructive":"text-foreground")}>{label}</p>
        {subtitle&&<p className="text-xs text-muted-foreground mt-0.5">{subtitle}</p>}
      </div>
      {trailing!==undefined ? trailing : onClick ? <ChevronRight className="w-4 h-4 text-muted-foreground shrink-0"/> : null}
    </div>
  );
}

function SettingsCard({ title, children }: { title:string; children:React.ReactNode }) {
  return (
    <div className="mb-5">
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-1 mt-5 first:mt-0 px-1">{title}</p>
      <div className="bg-card rounded-[24px] border border-border overflow-hidden divide-y divide-border/60">{children}</div>
    </div>
  );
}

function SettingsIconBadge({ icon, gradient }: {
  icon:React.ReactNode;
  gradient:[string,string];
}) {
  return (
    <span
      aria-hidden="true"
      className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[14px] text-white shadow-sm"
      style={{background:`linear-gradient(135deg,${gradient[0]},${gradient[1]})`}}
    >
      {icon}
    </span>
  );
}

function SourcePickerIconBadge({ icon, gradient, branded=false }: {
  icon:React.ReactNode;
  gradient?:[string,string];
  branded?:boolean;
}) {
  if (!branded) {
    return <SettingsIconBadge icon={icon} gradient={gradient??G[0]}/>;
  }

  return (
    <span
      aria-hidden="true"
      className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-[14px] bg-white shadow-sm ring-1 ring-black/[0.08]"
    >
      {icon}
    </span>
  );
}

function SourcePickerOption({ title, icon, gradient, branded=false, onSelect }: {
  title:string;
  icon:React.ReactNode;
  gradient?:[string,string];
  branded?:boolean;
  onSelect:()=>void;
}) {
  return (
    <button type="button" onClick={onSelect}
      className="group flex min-h-[58px] w-full items-center gap-3 rounded-[18px] px-3 text-left outline-none transition-colors hover:bg-muted/55 focus-visible:ring-2 focus-visible:ring-primary/40">
      <SourcePickerIconBadge icon={icon} gradient={gradient} branded={branded}/>
      <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-foreground">{title}</span>
      <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground/40 transition-transform group-hover:translate-x-0.5" aria-hidden="true"/>
    </button>
  );
}

function SourcePickerQuickOption({ title, icon, gradient, branded=false, onSelect }: {
  title:string;
  icon:React.ReactNode;
  gradient?:[string,string];
  branded?:boolean;
  onSelect:()=>void;
}) {
  return (
    <button type="button" onClick={onSelect}
      className="group flex min-h-[88px] min-w-0 flex-col items-start justify-between rounded-[18px] border border-border/80 bg-muted/60 p-3 text-left shadow-sm outline-none transition-colors hover:border-primary/25 hover:bg-muted/80 focus-visible:ring-2 focus-visible:ring-primary/40">
      <SourcePickerIconBadge icon={icon} gradient={gradient} branded={branded}/>
      <span className="line-clamp-2 text-[12px] font-semibold leading-4 text-foreground">{title}</span>
    </button>
  );
}

function SourcePickerCategoryOption({ title, description, count, icon, gradient, expanded, onSelect }: {
  title:string;
  description:string;
  count:number;
  icon:React.ReactNode;
  gradient:[string,string];
  expanded:boolean;
  onSelect:()=>void;
}) {
  return (
    <button type="button" onClick={onSelect} aria-expanded={expanded}
      className="group flex min-h-[66px] w-full items-center gap-3 rounded-[18px] px-3 text-left outline-none transition-colors hover:bg-muted/55 focus-visible:ring-2 focus-visible:ring-primary/40">
      <SourcePickerIconBadge icon={icon} gradient={gradient}/>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-semibold text-foreground">{title}</span>
        <span className="mt-0.5 block truncate text-[11px] text-muted-foreground">{description}</span>
      </span>
      <span className="text-[11px] font-medium text-muted-foreground">{count}</span>
      <ChevronDown className={cn("h-4 w-4 shrink-0 text-muted-foreground/40 transition-transform",expanded&&"rotate-180")} aria-hidden="true"/>
    </button>
  );
}

type SourcePickerCategoryId = "device-network"|"cloud-drives"|"media-servers";

function AddSourcePickerDialog({ open, onClose, onWebDav, onSmb }: {
  open:boolean;
  onClose:()=>void;
  onWebDav:()=>void;
  onSmb:()=>void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const [flowNotice,setFlowNotice] = useState<string|null>(null);
  const [expandedCategory,setExpandedCategory] = useState<SourcePickerCategoryId|null>(null);

  useEffect(()=>{
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const focusFrame = window.requestAnimationFrame(()=>dialogRef.current?.focus());
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    setFlowNotice(null);
    setExpandedCategory(null);
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
    };
  },[open]);

  if (!open) return null;

  const explainFlow = (message:string) => setFlowNotice(message);
  const sourceTypes: Array<{
    id:string;
    title:string;
    category:SourcePickerCategoryId;
    icon:React.ReactNode;
    quickIcon?:React.ReactNode;
    gradient?:[string,string];
    branded?:boolean;
    onSelect:()=>void;
  }> = [
    {
      id:"local-directory",
      title:"Local directory",
      category:"device-network",
      icon:<HardDrive className="h-[18px] w-[18px]"/>,
      gradient:G[2],
      onSelect:()=>explainFlow("The production app continues with the native system folder picker, then adds the selected directory to the library."),
    },
    {
      id:"webdav",
      title:"WebDAV",
      category:"device-network",
      icon:<Server className="h-[18px] w-[18px]"/>,
      gradient:G[1],
      onSelect:onWebDav,
    },
    {
      id:"smb",
      title:"SMB",
      category:"device-network",
      icon:<Database className="h-[18px] w-[18px]"/>,
      gradient:G[4],
      onSelect:onSmb,
    },
    {
      id:"onedrive",
      title:"OneDrive",
      category:"cloud-drives",
      icon:<img src={oneDriveIconUrl} alt="" className="h-6 w-7 object-contain"/>,
      branded:true,
      onSelect:()=>explainFlow("The production app continues to Microsoft OAuth, returns to MelodyTrove, then lets you choose a OneDrive drive."),
    },
    {
      id:"navidrome",
      title:"Navidrome",
      category:"media-servers",
      icon:<img src={navidromeIconUrl} alt="" className="h-6 w-6 object-contain"/>,
      branded:true,
      onSelect:()=>explainFlow("The production app continues to the shared media-server editor for the Navidrome address and account credentials."),
    },
    {
      id:"opensubsonic",
      title:"OpenSubsonic",
      category:"media-servers",
      icon:<img src={openSubsonicIconUrl} alt="" className="h-6 w-6 object-contain"/>,
      branded:true,
      onSelect:()=>explainFlow("The production app continues to the shared media-server editor for the OpenSubsonic address and account credentials."),
    },
    {
      id:"emby",
      title:"Emby",
      category:"media-servers",
      icon:<img src={embyIconUrl} alt="" className="h-6 w-6 object-contain"/>,
      branded:true,
      onSelect:()=>explainFlow("The production app continues to the shared media-server editor for the Emby address and account credentials."),
    },
  ];
  const categories: Array<{
    id:SourcePickerCategoryId;
    title:string;
    description:string;
    icon:React.ReactNode;
    gradient:[string,string];
  }> = [
    {
      id:"device-network",
      title:"Device & network",
      description:"Folders, NAS, and shared storage",
      icon:<HardDrive className="h-5 w-5"/>,
      gradient:G[7],
    },
    {
      id:"cloud-drives",
      title:"Cloud drives",
      description:"Connected cloud accounts",
      icon:<Cloud className="h-5 w-5"/>,
      gradient:G[3],
    },
    {
      id:"media-servers",
      title:"Media servers",
      description:"Self-hosted music libraries",
      icon:<Radio className="h-5 w-5"/>,
      gradient:G[0],
    },
  ];
  const quickAccessIds = ["local-directory","webdav","onedrive"];
  const quickAccess = sourceTypes.filter(source=>quickAccessIds.includes(source.id));
  const toggleCategory = (category:SourcePickerCategoryId) => {
    setFlowNotice(null);
    setExpandedCategory(current=>current===category?null:category);
  };

  return (
    <motion.div className="fixed inset-0 z-[175] flex items-end justify-center bg-black/55 backdrop-blur-sm sm:items-center sm:p-4"
      initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
      onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="source-picker-title" tabIndex={-1}
        initial={{opacity:0,y:36}} animate={{opacity:1,y:0}} exit={{opacity:0,y:28}}
        transition={{type:"spring",stiffness:420,damping:34}}
        className="max-h-[82vh] w-full overflow-y-auto rounded-t-[30px] border border-border bg-popover px-4 pb-[max(24px,env(safe-area-inset-bottom))] pt-3 shadow-2xl outline-none sm:max-w-[560px] sm:rounded-[30px] sm:px-5 sm:pb-5">
        <div aria-hidden="true" className="mx-auto mb-3 h-1 w-10 rounded-full bg-muted-foreground/25 sm:hidden"/>

        <div className="mb-4 flex items-start gap-3 px-1">
          <div className="min-w-0 flex-1">
            <h2 id="source-picker-title" className="text-lg font-semibold text-foreground">Add source</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">Choose where your music lives</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close Add source"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40"><X className="h-4 w-4"/></button>
        </div>

        {flowNotice&&(
          <div role="status" className="mb-3 flex items-start gap-2 rounded-[16px] bg-muted/55 px-3 py-2.5 text-[11px] leading-4 text-muted-foreground">
            <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary"/>
            <span className="min-w-0 flex-1">{flowNotice}</span>
            <button type="button" onClick={()=>setFlowNotice(null)} aria-label="Dismiss source setup note" className="rounded-lg p-1 outline-none hover:bg-background focus-visible:ring-2 focus-visible:ring-primary/40"><X className="h-3.5 w-3.5"/></button>
          </div>
        )}

        <div className="space-y-4">
          <section aria-labelledby="source-quick-access-heading">
            <p id="source-quick-access-heading" className="mb-2 px-1 text-[11px] font-semibold text-muted-foreground">Quick access</p>
          <div className="grid grid-cols-3 gap-2">
            {quickAccess.map(source=>(
              <SourcePickerQuickOption key={source.id} title={source.title} icon={source.quickIcon??source.icon}
                gradient={source.gradient} branded={source.branded} onSelect={source.onSelect}/>
            ))}
          </div>
          </section>

          <section aria-labelledby="source-browse-heading">
            <p id="source-browse-heading" className="mb-1 px-1 text-[11px] font-semibold text-muted-foreground">Browse by type</p>
            <div className="rounded-[20px] bg-muted/35">
              {categories.map((category,index)=>{
                const categorySources = sourceTypes.filter(source=>source.category===category.id);
                const expanded = expandedCategory===category.id;
                return (
                  <div key={category.id} className={cn(index>0&&"border-t border-border/60")}>
                    <SourcePickerCategoryOption title={category.title} description={category.description}
                      count={categorySources.length} icon={category.icon} gradient={category.gradient} expanded={expanded}
                      onSelect={()=>toggleCategory(category.id)}/>
                    <AnimatePresence initial={false}>
                      {expanded&&(
                        <motion.div initial={{height:0,opacity:0}} animate={{height:"auto",opacity:1}} exit={{height:0,opacity:0}}
                          transition={{duration:0.18,ease:"easeOut"}} className="overflow-hidden">
                          <div className="mx-3 mb-2 divide-y divide-border/60 rounded-[16px] bg-background/45">
                            {categorySources.map(source=>(
                              <SourcePickerOption key={source.id} title={source.title} icon={source.icon}
                                gradient={source.gradient} branded={source.branded} onSelect={source.onSelect}/>
                            ))}
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                );
              })}
            </div>
          </section>
        </div>
      </motion.div>
    </motion.div>
  );
}

type WebDavConnectionState = "idle"|"testing"|"success";
type WebDavFormErrors = Partial<Record<"name"|"address"|"username"|"password",string>>;
type SmbFormErrors = Partial<Record<"name"|"host"|"port"|"share"|"username"|"password",string>>;
type MetadataScanMode = "fast"|"standard"|"full";
type SettingsSourceModel = {
  id:string;
  name:string;
  type:"Local"|"WebDAV"|"SMB";
  icon:React.ReactNode;
  enabled:boolean;
  location:string;
  tracks:number;
  lastScan:string;
  gradient:[string,string];
  localPath?:string;
  includeSubdirectories?:boolean;
  metadataScanMode?:MetadataScanMode;
  address?:string;
  username?:string;
  anonymous?:boolean;
  importedDirectories?:string[];
  smbHost?:string;
  smbPort?:number;
  smbShare?:string;
  smbRootPath?:string;
  smbDomain?:string;
  smbGuest?:boolean;
  smbRequireSigning?:boolean;
  smbRequireEncryption?:boolean;
};

function LocalSourceDialog({ source, onClose, onSave }: {
  source:SettingsSourceModel|null;
  onClose:()=>void;
  onSave:(id:string,updates:{name:string;localPath:string;includeSubdirectories:boolean;metadataScanMode:MetadataScanMode})=>void;
}) {
  const nameRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const [name,setName] = useState("");
  const [localPath,setLocalPath] = useState("");
  const [includeSubdirectories,setIncludeSubdirectories] = useState(true);
  const [metadataScanMode,setMetadataScanMode] = useState<MetadataScanMode>("full");

  useEffect(()=>{
    if (!source) return;
    const previousOverflow = document.body.style.overflow;
    const focusFrame = window.requestAnimationFrame(()=>nameRef.current?.focus());
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    setName(source.name);
    setLocalPath(source.localPath??"~/Music");
    setIncludeSubdirectories(source.includeSubdirectories??true);
    setMetadataScanMode(source.metadataScanMode??"full");
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
    };
  },[source,onClose]);

  if (!source) return null;

  const canSave = Boolean(name.trim()&&localPath.trim());
  const saveSource = (event:React.FormEvent) => {
    event.preventDefault();
    if (!canSave) return;
    onSave(source.id,{name:name.trim(),localPath:localPath.trim(),includeSubdirectories,metadataScanMode});
    onClose();
  };
  const selectFolder = (event:React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0];
    const selectedFolder = selectedFile?.webkitRelativePath.split("/")[0]||selectedFile?.name;
    if (selectedFolder) setLocalPath(selectedFolder);
    event.target.value = "";
  };
  const inputClass = "h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary/50 focus:ring-2 focus:ring-primary/20";

  return (
    <motion.div className="fixed inset-0 z-[175] flex items-end justify-center bg-black/55 backdrop-blur-sm sm:items-center sm:p-4"
      initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
      onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="dialog" aria-modal="true" aria-labelledby="configure-local-title"
        initial={{opacity:0,y:28,scale:0.985}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:18,scale:0.985}}
        transition={{type:"spring",stiffness:420,damping:34}}
        className="w-full rounded-t-[30px] border border-border bg-popover p-5 pb-[max(24px,env(safe-area-inset-bottom))] shadow-2xl sm:max-w-[480px] sm:rounded-[30px] sm:pb-5">
        <form onSubmit={saveSource}>
          <div className="mb-5 flex items-start gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] bg-primary/12 text-primary"><FolderOpen className="h-5 w-5"/></span>
            <div className="min-w-0 flex-1">
              <h2 id="configure-local-title" className="text-lg font-semibold text-foreground">Configure Local source</h2>
              <p className="mt-1 text-xs leading-[17px] text-muted-foreground">Update the folder used by this library source.</p>
            </div>
            <button type="button" onClick={onClose} aria-label="Close Local source settings"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40"><X className="h-4 w-4"/></button>
          </div>

          <div className="space-y-4">
            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold text-foreground">Source name</span>
              <input ref={nameRef} value={name} maxLength={48} placeholder="Local music" onChange={event=>setName(event.target.value)} className={inputClass}/>
            </label>
            <div>
              <span className="mb-1.5 block text-xs font-semibold text-foreground">Music folder</span>
              <input ref={folderInputRef} type="file" multiple className="sr-only" onChange={selectFolder}
                {...({webkitdirectory:"",directory:""} as React.InputHTMLAttributes<HTMLInputElement>)}/>
              <button type="button" onClick={()=>folderInputRef.current?.click()} aria-label="Choose music folder"
                className="flex min-h-[56px] w-full items-center gap-3 rounded-[18px] border border-border bg-background px-3.5 text-left outline-none transition-colors hover:bg-muted/45 focus-visible:border-primary/50 focus-visible:ring-2 focus-visible:ring-primary/20">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-primary/10 text-primary"><FolderOpen className="h-4 w-4"/></span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-foreground">{localPath||"No folder selected"}</span>
                  <span className="mt-0.5 block text-[11px] text-muted-foreground">Selected with the system file manager</span>
                </span>
                <span className="shrink-0 text-xs font-semibold text-primary">Choose</span>
              </button>
            </div>
            <div className="overflow-hidden rounded-[20px] bg-muted/55 divide-y divide-border/60">
              <div className="flex min-h-[60px] items-center gap-4 px-4 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-foreground">Scan subdirectories</p>
                  <p className="mt-0.5 text-[11px] text-muted-foreground">Include music inside nested folders</p>
                </div>
                <DesignSwitch ariaLabel="Scan subdirectories" checked={includeSubdirectories} onChange={setIncludeSubdirectories}/>
              </div>
              <FloatingSelectRow label="Metadata scan" subtitle="Choose how thoroughly metadata and artwork are read"
                value={metadataScanMode} onChange={value=>setMetadataScanMode(value as MetadataScanMode)}
                options={[{value:"fast",label:"Fast"},{value:"standard",label:"Standard"},{value:"full",label:"Full"}]}/>
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <button type="button" onClick={onClose} className="h-10 rounded-full px-4 text-sm font-semibold text-foreground outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">Cancel</button>
            <button type="submit" disabled={!canSave}
              className="h-10 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground outline-none transition-all hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:ring-2 focus-visible:ring-primary/40">Save changes</button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}

type SourceMenuAnchor = {top:number;bottom:number;left:number;right:number};

function SourceActionsMenu({ source, anchor, isDark, onClose, onManage, onEdit, onScan, onDelete }: {
  source:SettingsSourceModel|null;
  anchor:SourceMenuAnchor|null;
  isDark:boolean;
  onClose:()=>void;
  onManage:()=>void;
  onEdit:()=>void;
  onScan:()=>void;
  onDelete:()=>void;
}) {
  useEffect(()=>{
    if (!source) return;
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    window.addEventListener("keydown",handleKeyDown);
    return ()=>window.removeEventListener("keydown",handleKeyDown);
  },[source,onClose]);

  if (!source||!anchor) return null;
  const menuWidth = 224;
  const menuHeight = 204;
  const left = Math.min(window.innerWidth-menuWidth-12,Math.max(12,anchor.right-menuWidth));
  const top = anchor.bottom+8+menuHeight<=window.innerHeight
    ?anchor.bottom+8
    :Math.max(12,anchor.top-menuHeight-8);
  const runAction = (action:()=>void) => {
    onClose();
    action();
  };
  const itemClass = "flex min-h-11 w-full items-center gap-3 rounded-[14px] px-3 text-left text-[13px] font-medium text-foreground outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40";

  return createPortal(
    <div className={cn("fixed inset-0 z-[185]",isDark&&"dark")} onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="menu" aria-label={`${source.name} actions`}
        initial={{opacity:0,scale:0.96,y:-4}} animate={{opacity:1,scale:1,y:0}}
        transition={{duration:0.14,ease:"easeOut"}}
        className="fixed w-56 rounded-[20px] border border-border bg-popover p-1.5 shadow-2xl"
        style={{left,top}}>
        <button type="button" role="menuitem" onClick={()=>runAction(onManage)} className={itemClass}>
          <SlidersHorizontal className="h-4 w-4 text-muted-foreground"/>Manage music source
        </button>
        <button type="button" role="menuitem" onClick={()=>runAction(onEdit)} className={itemClass}>
          <Pencil className="h-4 w-4 text-muted-foreground"/>Edit music source
        </button>
        <button type="button" role="menuitem" onClick={()=>runAction(onScan)} className={itemClass}>
          <RefreshCw className="h-4 w-4 text-muted-foreground"/>Scan music source
        </button>
        <div className="my-1 h-px bg-border/70"/>
        <button type="button" role="menuitem" onClick={()=>runAction(onDelete)}
          className={cn(itemClass,"text-destructive hover:bg-destructive/10 focus-visible:ring-destructive/35")}>
          <Trash2 className="h-4 w-4"/>Delete music source
        </button>
      </motion.div>
    </div>,
    document.body,
  );
}

function SourceRemovalDialog({ source, isDark, onClose, onConfirm }: {
  source:SettingsSourceModel|null;
  isDark:boolean;
  onClose:()=>void;
  onConfirm:()=>void;
}) {
  if (!source) return null;
  return createPortal(
    <motion.div className={cn("fixed inset-0 z-[195] flex items-center justify-center bg-black/55 p-5 backdrop-blur-sm",isDark&&"dark")}
      initial={{opacity:0}} animate={{opacity:1}} onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="alertdialog" aria-modal="true" aria-labelledby="remove-source-title" aria-describedby="remove-source-description"
        initial={{opacity:0,scale:0.95,y:8}} animate={{opacity:1,scale:1,y:0}}
        transition={{type:"spring",stiffness:430,damping:34}}
        className="w-full max-w-[400px] rounded-[28px] border border-border bg-popover p-5 shadow-2xl">
        <span className="flex h-11 w-11 items-center justify-center rounded-[15px] bg-destructive/10 text-destructive"><Trash2 className="h-5 w-5"/></span>
        <h2 id="remove-source-title" className="mt-4 text-[18px] font-semibold text-foreground">Delete {source.name}?</h2>
        <p id="remove-source-description" className="mt-2 text-[12px] leading-[18px] text-muted-foreground">
          {source.tracks.toLocaleString()} indexed tracks and this source configuration will be removed. Original music files stay untouched.
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="h-10 rounded-full px-4 text-[12px] font-semibold text-foreground hover:bg-muted">Cancel</button>
          <button type="button" onClick={onConfirm} className="h-10 rounded-full bg-destructive px-5 text-[12px] font-semibold text-white hover:opacity-90">Delete source</button>
        </div>
      </motion.div>
    </motion.div>,
    document.body,
  );
}

function AddWebDavSourceDialog({ open, existingNames, onClose, onAdd }: {
  open:boolean;
  existingNames:string[];
  onClose:()=>void;
  onAdd:(source:{name:string;address:string;username:string;anonymous:boolean;includeSubdirectories:boolean})=>void;
}) {
  const nameRef = useRef<HTMLInputElement>(null);
  const [name,setName] = useState("");
  const [address,setAddress] = useState("");
  const [username,setUsername] = useState("");
  const [password,setPassword] = useState("");
  const [anonymous,setAnonymous] = useState(false);
  const [includeSubdirectories,setIncludeSubdirectories] = useState(true);
  const [passwordVisible,setPasswordVisible] = useState(false);
  const [errors,setErrors] = useState<WebDavFormErrors>({});
  const [connectionState,setConnectionState] = useState<WebDavConnectionState>("idle");

  useEffect(()=>{
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const focusFrame = window.requestAnimationFrame(()=>nameRef.current?.focus());
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    setName("");
    setAddress("");
    setUsername("");
    setPassword("");
    setAnonymous(false);
    setIncludeSubdirectories(true);
    setPasswordVisible(false);
    setErrors({});
    setConnectionState("idle");
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
    };
  },[open,onClose]);

  if (!open) return null;

  const markChanged = () => connectionState!=="idle"&&setConnectionState("idle");
  const validate = () => {
    const nextErrors:WebDavFormErrors = {};
    const trimmedName = name.trim();
    const trimmedAddress = address.trim();
    if (!trimmedName) nextErrors.name = "Enter a source name";
    else if (existingNames.some(item=>item.toLowerCase()===trimmedName.toLowerCase())) nextErrors.name = "A source with this name already exists";
    if (!trimmedAddress) nextErrors.address = "Enter the WebDAV server address";
    else {
      try {
        const url = new URL(trimmedAddress);
        if (url.protocol!=="http:"&&url.protocol!=="https:") nextErrors.address = "Use an HTTP or HTTPS address";
      } catch {
        nextErrors.address = "Enter a valid HTTP or HTTPS address";
      }
    }
    if (!anonymous&&!username.trim()) nextErrors.username = "Enter the username";
    if (!anonymous&&!password) nextErrors.password = "Enter the password";
    setErrors(nextErrors);
    return Object.keys(nextErrors).length===0;
  };

  const testConnection = () => {
    if (!validate()) return;
    setConnectionState("testing");
    window.setTimeout(()=>setConnectionState("success"),850);
  };

  const addSource = (event:React.FormEvent) => {
    event.preventDefault();
    if (connectionState!=="success"||!validate()) return;
    onAdd({name:name.trim(),address:address.trim(),username:anonymous?"":username.trim(),anonymous,includeSubdirectories});
    onClose();
  };

  const inputClass = "h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary/50 focus:ring-2 focus:ring-primary/20";

  return (
    <motion.div className="fixed inset-0 z-[170] flex items-end justify-center bg-black/55 p-0 backdrop-blur-sm sm:items-center sm:p-4"
      initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
      onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="dialog" aria-modal="true" aria-labelledby="add-webdav-title"
        initial={{opacity:0,y:24,scale:0.98}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:16,scale:0.98}}
        transition={{type:"spring",stiffness:420,damping:34}}
        className="w-full max-h-[92vh] overflow-y-auto rounded-t-[30px] border border-border bg-popover p-5 shadow-2xl sm:max-w-[480px] sm:rounded-[30px]">
        <form onSubmit={addSource} noValidate>
          <div className="mb-5 flex items-start gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] bg-primary/12 text-primary"><Server className="h-5 w-5"/></span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2"><h2 id="add-webdav-title" className="text-lg font-semibold text-foreground">Add WebDAV source</h2><span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold text-muted-foreground">WebDAV</span></div>
              <p className="mt-1 text-xs leading-[17px] text-muted-foreground">Test the connection before adding it to the unified library.</p>
            </div>
            <button type="button" onClick={onClose} aria-label="Close Add source"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40"><X className="h-4 w-4"/></button>
          </div>

          <div className="space-y-4">
            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold text-foreground">Source name</span>
              <input ref={nameRef} value={name} maxLength={48} placeholder="Home NAS" aria-invalid={Boolean(errors.name)}
                onChange={event=>{setName(event.target.value);setErrors(current=>({...current,name:undefined}));markChanged();}}
                className={cn(inputClass,errors.name&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
              {errors.name&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.name}</span>}
            </label>
            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold text-foreground">Server address</span>
              <input value={address} maxLength={256} inputMode="url" placeholder="https://dav.example.com/music" aria-invalid={Boolean(errors.address)}
                onChange={event=>{setAddress(event.target.value);setErrors(current=>({...current,address:undefined}));markChanged();}}
                className={cn(inputClass,errors.address&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
              {errors.address&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.address}</span>}
            </label>

            <div className="flex min-h-[58px] items-center gap-4 rounded-[20px] bg-muted/55 px-4 py-2.5">
              <div className="flex-1"><p className="text-sm font-medium text-foreground">Anonymous access</p><p className="mt-0.5 text-[11px] text-muted-foreground">Connect without a username or password</p></div>
              <DesignSwitch ariaLabel="Anonymous access" checked={anonymous} onChange={value=>{setAnonymous(value);setErrors(current=>({...current,username:undefined,password:undefined}));markChanged();}}/>
            </div>

            {!anonymous&&<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold text-foreground">Username</span>
                <input value={username} maxLength={128} autoComplete="username" placeholder="music"
                  onChange={event=>{setUsername(event.target.value);setErrors(current=>({...current,username:undefined}));markChanged();}}
                  className={cn(inputClass,errors.username&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                {errors.username&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.username}</span>}
              </label>
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold text-foreground">Password</span>
                <span className="relative block">
                  <input value={password} maxLength={128} autoComplete="current-password" placeholder="••••••••" type={passwordVisible?"text":"password"}
                    onChange={event=>{setPassword(event.target.value);setErrors(current=>({...current,password:undefined}));markChanged();}}
                    className={cn(inputClass,"pr-11",errors.password&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                  <button type="button" onClick={()=>setPasswordVisible(value=>!value)} aria-label={passwordVisible?"Hide password":"Show password"}
                    className="absolute right-1 top-1 flex h-9 w-9 items-center justify-center rounded-xl text-muted-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
                    {passwordVisible?<EyeOff className="h-4 w-4"/>:<Eye className="h-4 w-4"/>}
                  </button>
                </span>
                {errors.password&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.password}</span>}
              </label>
            </div>}
            <div className="flex min-h-[58px] items-center gap-4 rounded-[20px] bg-muted/55 px-4 py-2.5">
              <div className="min-w-0 flex-1"><p className="text-sm font-medium text-foreground">Scan subdirectories</p><p className="mt-0.5 text-[11px] text-muted-foreground">Include music inside nested folders</p></div>
              <DesignSwitch ariaLabel="Scan WebDAV subdirectories" checked={includeSubdirectories} onChange={setIncludeSubdirectories}/>
            </div>
          </div>

          <div className="mt-5 rounded-[20px] border border-border bg-card p-3">
            <div className="flex items-center gap-3">
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-foreground">Connection test</p>
                <p className={cn("mt-0.5 text-[11px]",connectionState==="success"?"text-[#3DCA8A]":"text-muted-foreground")}>
                  {connectionState==="testing"?"Connecting…":connectionState==="success"?"Connection successful":"Credentials remain only in this prototype session"}
                </p>
              </div>
              <button type="button" onClick={testConnection} disabled={connectionState==="testing"}
                className="flex h-9 items-center gap-2 rounded-full bg-muted px-3.5 text-xs font-semibold text-foreground outline-none hover:bg-muted/80 disabled:opacity-60 focus-visible:ring-2 focus-visible:ring-primary/40">
                {connectionState==="testing"?<RefreshCw className="h-3.5 w-3.5 animate-spin"/>:connectionState==="success"?<Check className="h-3.5 w-3.5 text-[#3DCA8A]"/>:<Wifi className="h-3.5 w-3.5"/>}
                {connectionState==="success"?"Test again":"Test connection"}
              </button>
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <button type="button" onClick={onClose} className="h-10 rounded-full px-4 text-sm font-semibold text-foreground outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">Cancel</button>
            <button type="submit" disabled={connectionState!=="success"}
              className="h-10 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground outline-none transition-all hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:ring-2 focus-visible:ring-primary/40">Add source</button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}

const WEB_DAV_DIRECTORY_OPTIONS = [
  {path:"/Music",description:"Primary music collection"},
  {path:"/Music Archive",description:"Older albums and archived releases"},
  {path:"/Live Recordings",description:"Concerts and live sessions"},
  {path:"/Podcasts",description:"Spoken audio and podcast downloads"},
];

function ManageWebDavSourceDialog({ source, existingNames, onClose, onSave, onDelete }: {
  source:SettingsSourceModel|null;
  existingNames:string[];
  onClose:()=>void;
  onSave:(id:string,updates:{name:string;address:string;username:string;anonymous:boolean;importedDirectories:string[];includeSubdirectories:boolean;metadataScanMode:MetadataScanMode})=>void;
  onDelete:(id:string)=>void;
}) {
  const nameRef = useRef<HTMLInputElement>(null);
  const [name,setName] = useState("");
  const [address,setAddress] = useState("");
  const [username,setUsername] = useState("");
  const [password,setPassword] = useState("");
  const [anonymous,setAnonymous] = useState(false);
  const [passwordVisible,setPasswordVisible] = useState(false);
  const [importedDirectories,setImportedDirectories] = useState<string[]>([]);
  const [includeSubdirectories,setIncludeSubdirectories] = useState(true);
  const [metadataScanMode,setMetadataScanMode] = useState<MetadataScanMode>("standard");
  const [errors,setErrors] = useState<WebDavFormErrors>({});
  const [directoryError,setDirectoryError] = useState("");
  const [connectionState,setConnectionState] = useState<WebDavConnectionState>("idle");
  const [deleteConfirm,setDeleteConfirm] = useState(false);

  useEffect(()=>{
    if (!source) return;
    const previousOverflow = document.body.style.overflow;
    const focusFrame = window.requestAnimationFrame(()=>nameRef.current?.focus());
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    setName(source.name);
    setAddress(source.address??"");
    setUsername(source.username??"");
    setPassword("");
    setAnonymous(source.anonymous??false);
    setPasswordVisible(false);
    setImportedDirectories(source.importedDirectories??[]);
    setIncludeSubdirectories(source.includeSubdirectories??true);
    setMetadataScanMode(source.metadataScanMode??"standard");
    setErrors({});
    setDirectoryError("");
    setConnectionState("idle");
    setDeleteConfirm(false);
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
    };
  },[source,onClose]);

  if (!source) return null;

  const markChanged = () => connectionState!=="idle"&&setConnectionState("idle");
  const validateConnectionFields = () => {
    const nextErrors:WebDavFormErrors = {};
    const trimmedName = name.trim();
    const trimmedAddress = address.trim();
    if (!trimmedName) nextErrors.name = "Enter a source name";
    else if (existingNames.some(item=>item.toLowerCase()===trimmedName.toLowerCase()&&item.toLowerCase()!==source.name.toLowerCase())) nextErrors.name = "A source with this name already exists";
    if (!trimmedAddress) nextErrors.address = "Enter the WebDAV server address";
    else {
      try {
        const url = new URL(trimmedAddress);
        if (url.protocol!=="http:"&&url.protocol!=="https:") nextErrors.address = "Use an HTTP or HTTPS address";
      } catch {
        nextErrors.address = "Enter a valid HTTP or HTTPS address";
      }
    }
    if (!anonymous&&!username.trim()) nextErrors.username = "Enter the username";
    setErrors(nextErrors);
    return Object.keys(nextErrors).length===0;
  };

  const testConnection = () => {
    if (!validateConnectionFields()) return;
    setConnectionState("testing");
    window.setTimeout(()=>setConnectionState("success"),850);
  };

  const saveSource = (event:React.FormEvent) => {
    event.preventDefault();
    const validFields = validateConnectionFields();
    if (importedDirectories.length===0) setDirectoryError("Select at least one directory to import");
    if (!validFields||importedDirectories.length===0) return;
    onSave(source.id,{name:name.trim(),address:address.trim(),username:anonymous?"":username.trim(),anonymous,importedDirectories,includeSubdirectories,metadataScanMode});
    onClose();
  };

  const toggleDirectory = (path:string) => {
    setImportedDirectories(current=>current.includes(path)?current.filter(item=>item!==path):[...current,path]);
    setDirectoryError("");
  };

  const inputClass = "h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary/50 focus:ring-2 focus:ring-primary/20";

  return (
    <motion.div className="fixed inset-0 z-[175] flex items-stretch justify-center bg-background sm:items-center sm:bg-black/55 sm:p-4 sm:backdrop-blur-sm"
      initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
      onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="dialog" aria-modal="true" aria-labelledby="manage-webdav-title"
        initial={{opacity:0,y:18,scale:0.985}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:12,scale:0.985}}
        transition={{type:"spring",stiffness:420,damping:34}}
        className="h-full w-full overflow-y-auto bg-background px-4 pb-6 pt-5 sm:h-auto sm:max-h-[90vh] sm:max-w-[560px] sm:rounded-[30px] sm:border sm:border-border sm:bg-popover sm:p-5 sm:shadow-2xl">
        <form onSubmit={saveSource} noValidate>
          <div className="mb-5 flex items-start gap-3">
            <button type="button" onClick={onClose} aria-label="Back to sources"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-muted text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40"><ChevronLeft className="h-5 w-5"/></button>
            <div className="min-w-0 flex-1">
              <h2 id="manage-webdav-title" className="truncate text-lg font-semibold text-foreground">{source.name}</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">WebDAV source settings</p>
            </div>
            <button type="button" onClick={()=>setDeleteConfirm(true)} aria-label={`Delete ${source.name}`}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] text-destructive outline-none hover:bg-destructive/10 focus-visible:ring-2 focus-visible:ring-destructive/30"><Trash2 className="h-4.5 w-4.5"/></button>
          </div>

          {deleteConfirm&&(
            <div className="mb-5 rounded-[22px] border border-destructive/25 bg-destructive/[0.07] p-4">
              <p className="text-sm font-semibold text-destructive">Remove this source?</p>
              <p className="mt-1 text-[12px] leading-[17px] text-muted-foreground">{source.tracks.toLocaleString()} indexed tracks will be removed from MelodyTrove. Files on the WebDAV server stay untouched.</p>
              <div className="mt-3 flex justify-end gap-2">
                <button type="button" onClick={()=>setDeleteConfirm(false)} className="h-9 rounded-full px-3.5 text-xs font-semibold text-foreground hover:bg-muted">Keep source</button>
                <button type="button" onClick={()=>{onDelete(source.id);onClose();}} className="h-9 rounded-full bg-destructive px-4 text-xs font-semibold text-white">Remove source</button>
              </div>
            </div>
          )}

          <section aria-labelledby="connection-heading" className="mb-5">
            <p id="connection-heading" className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Connection</p>
            <div className="space-y-4 rounded-[24px] border border-border bg-card p-4">
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold text-foreground">Source name</span>
                <input ref={nameRef} value={name} maxLength={48} aria-invalid={Boolean(errors.name)}
                  onChange={event=>{setName(event.target.value);setErrors(current=>({...current,name:undefined}));markChanged();}}
                  className={cn(inputClass,errors.name&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                {errors.name&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.name}</span>}
              </label>
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold text-foreground">Server address</span>
                <input value={address} maxLength={256} inputMode="url" aria-invalid={Boolean(errors.address)}
                  onChange={event=>{setAddress(event.target.value);setErrors(current=>({...current,address:undefined}));markChanged();}}
                  className={cn(inputClass,errors.address&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                {errors.address&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.address}</span>}
              </label>
              <div className="flex min-h-[58px] items-center gap-4 rounded-[20px] bg-muted/55 px-4 py-2.5">
                <div className="flex-1"><p className="text-sm font-medium text-foreground">Anonymous access</p><p className="mt-0.5 text-[11px] text-muted-foreground">Connect without saved credentials</p></div>
                <DesignSwitch ariaLabel="Anonymous access" checked={anonymous} onChange={value=>{setAnonymous(value);setErrors(current=>({...current,username:undefined}));markChanged();}}/>
              </div>
              {!anonymous&&<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">Username</span>
                  <input value={username} maxLength={128} autoComplete="username" onChange={event=>{setUsername(event.target.value);setErrors(current=>({...current,username:undefined}));markChanged();}}
                    className={cn(inputClass,errors.username&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                  {errors.username&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.username}</span>}
                </label>
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">New password <span className="font-normal text-muted-foreground">(optional)</span></span>
                  <span className="relative block">
                    <input value={password} maxLength={128} autoComplete="new-password" placeholder="Keep current password" type={passwordVisible?"text":"password"}
                      onChange={event=>{setPassword(event.target.value);markChanged();}} className={cn(inputClass,"pr-11")}/>
                    <button type="button" onClick={()=>setPasswordVisible(value=>!value)} aria-label={passwordVisible?"Hide new password":"Show new password"}
                      className="absolute right-1 top-1 flex h-9 w-9 items-center justify-center rounded-xl text-muted-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
                      {passwordVisible?<EyeOff className="h-4 w-4"/>:<Eye className="h-4 w-4"/>}
                    </button>
                  </span>
                </label>
              </div>}
              <div className="flex items-center gap-3 rounded-[20px] bg-muted/45 p-3">
                <div className="flex-1"><p className="text-sm font-medium text-foreground">Connection test</p><p className={cn("mt-0.5 text-[11px]",connectionState==="success"?"text-[#3DCA8A]":"text-muted-foreground")}>{connectionState==="testing"?"Connecting…":connectionState==="success"?"Connection successful":"Check the edited settings before saving"}</p></div>
                <button type="button" onClick={testConnection} disabled={connectionState==="testing"}
                  className="flex h-9 items-center gap-2 rounded-full bg-background px-3.5 text-xs font-semibold text-foreground outline-none disabled:opacity-60 focus-visible:ring-2 focus-visible:ring-primary/40">
                  {connectionState==="testing"?<RefreshCw className="h-3.5 w-3.5 animate-spin"/>:connectionState==="success"?<Check className="h-3.5 w-3.5 text-[#3DCA8A]"/>:<Wifi className="h-3.5 w-3.5"/>}
                  {connectionState==="success"?"Test again":"Test connection"}
                </button>
              </div>
            </div>
          </section>

          <section aria-labelledby="directories-heading" className="mb-5">
            <div className="mb-2 flex items-center justify-between gap-3 px-1"><p id="directories-heading" className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Directories to import</p><span className="text-[11px] text-muted-foreground">{importedDirectories.length} selected</span></div>
            <div className="overflow-hidden rounded-[24px] border border-border bg-card divide-y divide-border/60">
              {WEB_DAV_DIRECTORY_OPTIONS.map(directory=>{
                const selected = importedDirectories.includes(directory.path);
                return <label key={directory.path} className="flex min-h-[64px] cursor-pointer items-center gap-3 px-4 py-2.5 hover:bg-muted/40">
                  <input type="checkbox" className="sr-only" checked={selected} onChange={()=>toggleDirectory(directory.path)}/>
                  <span className={cn("flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border transition-colors",selected?"border-primary bg-primary text-primary-foreground":"border-border bg-muted text-transparent")}><Check className="h-4 w-4"/></span>
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-muted text-muted-foreground"><Folder className="h-4 w-4"/></span>
                  <span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium text-foreground">{directory.path}</span><span className="mt-0.5 block truncate text-[11px] text-muted-foreground">{directory.description}</span></span>
                </label>;
              })}
            </div>
            {directoryError&&<p className="mt-2 px-1 text-[11px] text-destructive">{directoryError}</p>}
          </section>

          <section aria-labelledby="webdav-scanning-heading" className="mb-5">
            <p id="webdav-scanning-heading" className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Scanning</p>
            <div className="overflow-hidden rounded-[24px] border border-border bg-card divide-y divide-border/60">
              <div className="flex min-h-[64px] items-center gap-4 px-4 py-2.5">
                <div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Scan subdirectories</p><p className="mt-1 text-[12px] text-muted-foreground">Include music inside nested folders</p></div>
                <DesignSwitch ariaLabel="Scan WebDAV subdirectories" checked={includeSubdirectories} onChange={setIncludeSubdirectories}/>
              </div>
              <FloatingSelectRow label="Metadata scan" subtitle="Choose how thoroughly metadata and artwork are read"
                value={metadataScanMode} onChange={value=>setMetadataScanMode(value as MetadataScanMode)}
                options={[{value:"fast",label:"Fast"},{value:"standard",label:"Standard"},{value:"full",label:"Full"}]}/>
            </div>
          </section>

          <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-border bg-background/95 px-4 pb-1 pt-3 backdrop-blur-xl sm:-mx-5 sm:bg-popover/95 sm:px-5 sm:pb-0">
            <button type="button" onClick={onClose} className="h-10 rounded-full px-4 text-sm font-semibold text-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">Cancel</button>
            <button type="submit" className="h-10 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground outline-none hover:opacity-90 focus-visible:ring-2 focus-visible:ring-primary/40">Save changes</button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}

type SmbSourceDraft = {
  name:string;
  host:string;
  port:number;
  share:string;
  rootPath:string;
  username:string;
  domain:string;
  guest:boolean;
  requireSigning:boolean;
  requireEncryption:boolean;
  includeSubdirectories:boolean;
  metadataScanMode:MetadataScanMode;
};

function SmbSourceDialog({ open, source, existingNames, onClose, onSave, onDelete }: {
  open:boolean;
  source:SettingsSourceModel|null;
  existingNames:string[];
  onClose:()=>void;
  onSave:(draft:SmbSourceDraft)=>void;
  onDelete:(id:string)=>void;
}) {
  const nameRef = useRef<HTMLInputElement>(null);
  const testTimerRef = useRef<number|null>(null);
  const [name,setName] = useState("");
  const [host,setHost] = useState("");
  const [port,setPort] = useState("445");
  const [share,setShare] = useState("");
  const [rootPath,setRootPath] = useState("");
  const [username,setUsername] = useState("");
  const [password,setPassword] = useState("");
  const [domain,setDomain] = useState("");
  const [guest,setGuest] = useState(false);
  const [requireSigning,setRequireSigning] = useState(false);
  const [requireEncryption,setRequireEncryption] = useState(false);
  const [includeSubdirectories,setIncludeSubdirectories] = useState(true);
  const [metadataScanMode,setMetadataScanMode] = useState<MetadataScanMode>("standard");
  const [passwordVisible,setPasswordVisible] = useState(false);
  const [errors,setErrors] = useState<SmbFormErrors>({});
  const [connectionState,setConnectionState] = useState<WebDavConnectionState>("idle");
  const [deleteConfirm,setDeleteConfirm] = useState(false);

  useEffect(()=>{
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const focusFrame = window.requestAnimationFrame(()=>nameRef.current?.focus());
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    setName(source?.name??"");
    setHost(source?.smbHost??"");
    setPort(String(source?.smbPort??445));
    setShare(source?.smbShare??"");
    setRootPath(source?.smbRootPath??"");
    setUsername(source?.username??"");
    setPassword("");
    setDomain(source?.smbDomain??"");
    setGuest(source?.smbGuest??false);
    setRequireSigning(source?.smbRequireSigning??false);
    setRequireEncryption(source?.smbRequireEncryption??false);
    setIncludeSubdirectories(source?.includeSubdirectories??true);
    setMetadataScanMode(source?.metadataScanMode??"standard");
    setPasswordVisible(false);
    setErrors({});
    setConnectionState("idle");
    setDeleteConfirm(false);
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
      if (testTimerRef.current!==null) window.clearTimeout(testTimerRef.current);
    };
  },[open,source,onClose]);

  if (!open) return null;

  const isEditing = source!==null;
  const markChanged = () => connectionState!=="idle"&&setConnectionState("idle");
  const validate = () => {
    const nextErrors:SmbFormErrors = {};
    const normalizedPort = Number(port);
    if (!name.trim()) nextErrors.name = "Enter a source name";
    else if (existingNames.some(item=>item.toLowerCase()===name.trim().toLowerCase()&&item.toLowerCase()!==source?.name.toLowerCase())) nextErrors.name = "A source with this name already exists";
    if (!host.trim()) nextErrors.host = "Enter the SMB server host";
    if (!Number.isInteger(normalizedPort)||normalizedPort<1||normalizedPort>65535) nextErrors.port = "Use a port from 1 to 65535";
    if (!share.trim()) nextErrors.share = "Enter the SMB share name";
    if (!guest&&!username.trim()) nextErrors.username = "Enter the username";
    if (!guest&&!isEditing&&!password) nextErrors.password = "Enter the password";
    setErrors(nextErrors);
    return Object.keys(nextErrors).length===0;
  };

  const testConnection = () => {
    if (!validate()) return;
    setConnectionState("testing");
    testTimerRef.current = window.setTimeout(()=>{
      setConnectionState("success");
      testTimerRef.current = null;
    },850);
  };

  const saveSource = (event:React.FormEvent) => {
    event.preventDefault();
    if (!validate()||(!isEditing&&connectionState!=="success")) return;
    onSave({
      name:name.trim(),
      host:host.trim(),
      port:Number(port),
      share:share.trim(),
      rootPath:rootPath.trim(),
      username:guest?"":username.trim(),
      domain:guest?"":domain.trim(),
      guest,
      requireSigning,
      requireEncryption,
      includeSubdirectories,
      metadataScanMode,
    });
    onClose();
  };

  const inputClass = "h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary/50 focus:ring-2 focus:ring-primary/20";

  return (
    <motion.div className="fixed inset-0 z-[180] flex items-stretch justify-center bg-background sm:items-center sm:bg-black/55 sm:p-4 sm:backdrop-blur-sm"
      initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
      onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="dialog" aria-modal="true" aria-labelledby="smb-source-title"
        initial={{opacity:0,y:18,scale:0.985}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:12,scale:0.985}}
        transition={{type:"spring",stiffness:420,damping:34}}
        className="h-full w-full overflow-y-auto bg-background px-4 pb-6 pt-5 sm:h-auto sm:max-h-[90vh] sm:max-w-[620px] sm:rounded-[30px] sm:border sm:border-border sm:bg-popover sm:p-5 sm:shadow-2xl">
        <form onSubmit={saveSource} noValidate>
          <div className="mb-5 flex items-start gap-3">
            <button type="button" onClick={onClose} aria-label="Back to sources"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-muted text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40"><ChevronLeft className="h-5 w-5"/></button>
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-primary/12 text-primary"><Database className="h-5 w-5"/></span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2"><h2 id="smb-source-title" className="truncate text-lg font-semibold text-foreground">{source?.name??"Add SMB source"}</h2><span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold text-muted-foreground">SMB2/3</span></div>
              <p className="mt-0.5 text-xs text-muted-foreground">Windows, NAS, and Samba network shares</p>
            </div>
            {isEditing&&<button type="button" onClick={()=>setDeleteConfirm(true)} aria-label={`Delete ${source?.name??"SMB source"}`}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] text-destructive outline-none hover:bg-destructive/10 focus-visible:ring-2 focus-visible:ring-destructive/30"><Trash2 className="h-4.5 w-4.5"/></button>}
          </div>

          {deleteConfirm&&source&&(
            <div className="mb-5 rounded-[22px] border border-destructive/25 bg-destructive/[0.07] p-4">
              <p className="text-sm font-semibold text-destructive">Remove this SMB source?</p>
              <p className="mt-1 text-[12px] leading-[17px] text-muted-foreground">Indexed tracks and source settings will be removed. Files on the SMB share stay untouched.</p>
              <div className="mt-3 flex justify-end gap-2">
                <button type="button" onClick={()=>setDeleteConfirm(false)} className="h-9 rounded-full px-3.5 text-xs font-semibold text-foreground hover:bg-muted">Keep source</button>
                <button type="button" onClick={()=>{onDelete(source.id);onClose();}} className="h-9 rounded-full bg-destructive px-4 text-xs font-semibold text-white">Remove source</button>
              </div>
            </div>
          )}

          <section aria-labelledby="smb-share-heading" className="mb-5">
            <p id="smb-share-heading" className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Network share</p>
            <div className="space-y-4 rounded-[24px] border border-border bg-card p-4">
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold text-foreground">Source name</span>
                <input ref={nameRef} value={name} maxLength={48} placeholder="Studio NAS" aria-invalid={Boolean(errors.name)}
                  onChange={event=>{setName(event.target.value);setErrors(current=>({...current,name:undefined}));markChanged();}}
                  className={cn(inputClass,errors.name&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                {errors.name&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.name}</span>}
              </label>
              <div className="grid grid-cols-[minmax(0,1fr)_104px] gap-3">
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">Server</span>
                  <input value={host} maxLength={255} placeholder="192.168.1.20" aria-invalid={Boolean(errors.host)}
                    onChange={event=>{setHost(event.target.value);setErrors(current=>({...current,host:undefined}));markChanged();}}
                    className={cn(inputClass,errors.host&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                  {errors.host&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.host}</span>}
                </label>
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">Port</span>
                  <input value={port} inputMode="numeric" maxLength={5} placeholder="445" aria-invalid={Boolean(errors.port)}
                    onChange={event=>{setPort(event.target.value.replace(/\D/g,""));setErrors(current=>({...current,port:undefined}));markChanged();}}
                    className={cn(inputClass,errors.port&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                  {errors.port&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.port}</span>}
                </label>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">Share</span>
                  <input value={share} maxLength={255} placeholder="Music" aria-invalid={Boolean(errors.share)}
                    onChange={event=>{setShare(event.target.value);setErrors(current=>({...current,share:undefined}));markChanged();}}
                    className={cn(inputClass,errors.share&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                  {errors.share&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.share}</span>}
                </label>
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">Root folder <span className="font-normal text-muted-foreground">(optional)</span></span>
                  <input value={rootPath} maxLength={512} placeholder="Library/Lossless" onChange={event=>{setRootPath(event.target.value);markChanged();}} className={inputClass}/>
                </label>
              </div>
            </div>
          </section>

          <section aria-labelledby="smb-access-heading" className="mb-5">
            <p id="smb-access-heading" className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Access</p>
            <div className="overflow-hidden rounded-[24px] border border-border bg-card divide-y divide-border/60">
              <div className="flex min-h-[64px] items-center gap-4 px-4 py-2.5">
                <div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Guest access</p><p className="mt-1 text-[12px] text-muted-foreground">Connect without a username or password</p></div>
                <DesignSwitch ariaLabel="Guest access" checked={guest} onChange={value=>{setGuest(value);setErrors(current=>({...current,username:undefined,password:undefined}));markChanged();}}/>
              </div>
              {!guest&&<div className="space-y-4 p-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <label className="block">
                    <span className="mb-1.5 block text-xs font-semibold text-foreground">Username</span>
                    <input value={username} maxLength={128} autoComplete="username" placeholder="media" aria-invalid={Boolean(errors.username)}
                      onChange={event=>{setUsername(event.target.value);setErrors(current=>({...current,username:undefined}));markChanged();}}
                      className={cn(inputClass,errors.username&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                    {errors.username&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.username}</span>}
                  </label>
                  <label className="block">
                    <span className="mb-1.5 block text-xs font-semibold text-foreground">Password {isEditing&&<span className="font-normal text-muted-foreground">(optional)</span>}</span>
                    <span className="relative block">
                      <input value={password} maxLength={128} autoComplete="new-password" placeholder={isEditing?"Keep current password":"Required"} type={passwordVisible?"text":"password"} aria-invalid={Boolean(errors.password)}
                        onChange={event=>{setPassword(event.target.value);setErrors(current=>({...current,password:undefined}));markChanged();}}
                        className={cn(inputClass,"pr-11",errors.password&&"border-destructive focus:border-destructive focus:ring-destructive/20")}/>
                      <button type="button" onClick={()=>setPasswordVisible(value=>!value)} aria-label={passwordVisible?"Hide password":"Show password"}
                        className="absolute right-1 top-1 flex h-9 w-9 items-center justify-center rounded-xl text-muted-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
                        {passwordVisible?<EyeOff className="h-4 w-4"/>:<Eye className="h-4 w-4"/>}
                      </button>
                    </span>
                    {errors.password&&<span className="mt-1.5 block text-[11px] text-destructive">{errors.password}</span>}
                  </label>
                </div>
                <label className="block">
                  <span className="mb-1.5 block text-xs font-semibold text-foreground">Domain <span className="font-normal text-muted-foreground">(optional)</span></span>
                  <input value={domain} maxLength={128} placeholder="WORKGROUP" onChange={event=>{setDomain(event.target.value);markChanged();}} className={inputClass}/>
                </label>
              </div>}
            </div>
          </section>

          <section aria-labelledby="smb-security-heading" className="mb-5">
            <p id="smb-security-heading" className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Security</p>
            <div className="overflow-hidden rounded-[24px] border border-border bg-card divide-y divide-border/60">
              <div className="flex min-h-[64px] items-center gap-4 px-4 py-2.5"><div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Require signing</p><p className="mt-1 text-[12px] text-muted-foreground">Reject sessions without SMB message signing</p></div><DesignSwitch ariaLabel="Require SMB signing" checked={requireSigning} onChange={value=>{setRequireSigning(value);markChanged();}}/></div>
              <div className="flex min-h-[64px] items-center gap-4 px-4 py-2.5"><div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Require encryption</p><p className="mt-1 text-[12px] text-muted-foreground">Only connect when SMB3 encryption is negotiated</p></div><DesignSwitch ariaLabel="Require SMB encryption" checked={requireEncryption} onChange={value=>{setRequireEncryption(value);markChanged();}}/></div>
            </div>
          </section>

          <section aria-labelledby="smb-scanning-heading" className="mb-5">
            <p id="smb-scanning-heading" className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Scanning</p>
            <div className="overflow-hidden rounded-[24px] border border-border bg-card divide-y divide-border/60">
              <div className="flex min-h-[64px] items-center gap-4 px-4 py-2.5">
                <div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Scan subdirectories</p><p className="mt-1 text-[12px] text-muted-foreground">Include music inside nested folders</p></div>
                <DesignSwitch ariaLabel="Scan SMB subdirectories" checked={includeSubdirectories} onChange={setIncludeSubdirectories}/>
              </div>
              {isEditing&&<FloatingSelectRow label="Metadata scan" subtitle="Choose how thoroughly metadata and artwork are read"
                value={metadataScanMode} onChange={value=>setMetadataScanMode(value as MetadataScanMode)}
                options={[{value:"fast",label:"Fast"},{value:"standard",label:"Standard"},{value:"full",label:"Full"}]}/>}
            </div>
          </section>

          <div className="mb-5 flex items-center gap-3 rounded-[22px] border border-border bg-card p-3">
            <div className="min-w-0 flex-1"><p className="text-sm font-medium text-foreground">Connection test</p><p className={cn("mt-0.5 text-[11px]",connectionState==="success"?"text-[#3DCA8A]":"text-muted-foreground")}>{connectionState==="testing"?"Connecting with SMB2/3…":connectionState==="success"?"Connection successful":"SMB1 is never negotiated"}</p></div>
            <button type="button" onClick={testConnection} disabled={connectionState==="testing"}
              className="flex h-9 items-center gap-2 rounded-full bg-muted px-3.5 text-xs font-semibold text-foreground outline-none disabled:opacity-60 focus-visible:ring-2 focus-visible:ring-primary/40">
              {connectionState==="testing"?<RefreshCw className="h-3.5 w-3.5 animate-spin"/>:connectionState==="success"?<Check className="h-3.5 w-3.5 text-[#3DCA8A]"/>:<Wifi className="h-3.5 w-3.5"/>}
              {connectionState==="success"?"Test again":"Test connection"}
            </button>
          </div>

          <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-border bg-background/95 px-4 pb-1 pt-3 backdrop-blur-xl sm:-mx-5 sm:bg-popover/95 sm:px-5 sm:pb-0">
            <button type="button" onClick={onClose} className="h-10 rounded-full px-4 text-sm font-semibold text-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">Cancel</button>
            <button type="submit" disabled={!isEditing&&connectionState!=="success"} className="h-10 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground outline-none hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:ring-2 focus-visible:ring-primary/40">{isEditing?"Save changes":"Add source"}</button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}

function StickyPageHeader({
  title,
  subtitle,
  className,
  onBack,
  backLabel="Back",
  showTitleOnCollapse=false,
  liquidGlass=false,
  collapseDisabled=false,
  onCollapseProgressChange,
}: {
  title:string;
  subtitle?:string;
  className?:string;
  onBack?:()=>void;
  backLabel?:string;
  showTitleOnCollapse?:boolean;
  liquidGlass?:boolean;
  collapseDisabled?:boolean;
  onCollapseProgressChange?:(progress:number)=>void;
}) {
  const headerRef = useRef<HTMLElement>(null);
  const [collapseProgress,setCollapseProgress] = useState(0);
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    if (collapseDisabled) {
      setCollapseProgress(0);
      return;
    }
    const scroller = headerRef.current?.closest("main");
    if (!scroller) return;
    const update = () => {
      const nextProgress = Math.min(scroller.scrollTop/48,1);
      setCollapseProgress(nextProgress);
      onCollapseProgressChange?.(nextProgress);
    };
    update();
    scroller.addEventListener("scroll",update,{ passive:true });
    return () => scroller.removeEventListener("scroll",update);
  },[collapseDisabled,onCollapseProgressChange,title]);

  const progress = reduceMotion ? (collapseProgress>=1?1:0) : collapseProgress;
  const detailHeader = Boolean(onBack&&showTitleOnCollapse);
  const expandedHeight = detailHeader?56:subtitle?112:96;
  const collapsedHeight = detailHeader?56:subtitle?72:58;
  const height = expandedHeight-(expandedHeight-collapsedHeight)*progress;
  const actionBarTitleSize = liquidGlass?22:detailHeader?20:24;
  const expandedTitleOpacity = Math.max(0,1-progress/0.72);
  const actionBarTitleOpacity = detailHeader
    ? (collapseDisabled?1:progress)
    : Math.max(0,(progress-0.72)/0.28);
  const glassProgress = liquidGlass?(collapseDisabled?1:progress):0;

  return (
    <header ref={headerRef} className={cn(
      "sticky top-0 z-30 border-b bg-transparent",
      className,
    )} style={{
      height,
      paddingTop:0,
      paddingBottom:0,
      borderColor:liquidGlass
        ?`color-mix(in srgb,var(--actionbar-glass-border) ${Math.round(glassProgress*100)}%,transparent)`
        :`color-mix(in srgb,var(--border) ${Math.round(progress*60)}%,transparent)`,
    }}>
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-background" style={{opacity:1-glassProgress}}/>
      {liquidGlass&&<div aria-hidden="true" className="actionbar-liquid-glass pointer-events-none absolute inset-0" style={{opacity:glassProgress}}/>}
      <div className="relative h-full">
        {onBack&&<button type="button" aria-label={backLabel} onPointerDown={preventMouseFocus} onClick={onBack}
          className={cn(
            "absolute left-4 top-1/2 z-10 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-[14px] text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40",
            liquidGlass&&detailHeader?"bg-transparent hover:bg-muted/45":"bg-muted",
          )}>
          <ChevronLeft className="h-5 w-5"/>
        </button>}
        {!detailHeader&&(
          <div className="absolute inset-x-0 bottom-3 min-w-0" style={{opacity:expandedTitleOpacity}}>
            <h1 className="truncate font-bold text-foreground" style={{fontSize:32,lineHeight:"38px"}}>{title}</h1>
            {subtitle&&<p className="mt-0.5 text-xs text-muted-foreground">{subtitle}</p>}
          </div>
        )}
        <div className="pointer-events-none absolute inset-y-0 left-14 right-14 flex min-w-0 items-center justify-center"
          style={{opacity:actionBarTitleOpacity}}>
          <h1 className={cn(
              "w-full truncate text-center text-foreground",
              liquidGlass?"font-semibold":"font-bold",
            )}
            style={{fontSize:actionBarTitleSize,lineHeight:`${actionBarTitleSize+6}px`}}>{title}</h1>
        </div>
      </div>
    </header>
  );
}

// ─────────────────────────────────────────────────────────────
// PLAYER COMPONENTS
// ─────────────────────────────────────────────────────────────
function MiniPlayerGlassRefraction({ song }: { song:Song }) {
  return (
    <>
      <div className="absolute inset-0 pointer-events-none"
        style={{
          background:`radial-gradient(circle at 14% -35%,${song.gradient[0]}38 0%,transparent 42%),radial-gradient(circle at 88% 145%,${song.gradient[1]}30 0%,transparent 46%)`,
        }}/>
      <div className="absolute inset-x-5 top-0 h-px pointer-events-none"
        style={{ background:"linear-gradient(90deg,transparent,var(--mini-player-glass-highlight),transparent)" }}/>
      <div className="absolute inset-px rounded-[21px] border border-white/[0.04] pointer-events-none"/>
    </>
  );
}

function MiniPlayer({ song, isPlaying, onPlayPause, onNext, onExpand }: { song:Song|null; isPlaying:boolean; onPlayPause:()=>void; onNext:()=>void; onExpand:()=>void }) {
  if (!song) return null;
  const glassStyle: React.CSSProperties = {
    background:"var(--mini-player-glass-background)",
    backdropFilter:"blur(30px) saturate(1.85)",
    WebkitBackdropFilter:"blur(30px) saturate(1.85)",
    border:"1px solid var(--mini-player-glass-border)",
    boxShadow:"var(--mini-player-glass-shadow)",
  };
  return (
    <motion.div initial={{y:80,opacity:0}} animate={{y:0,opacity:1}} exit={{y:80,opacity:0}} transition={{type:"spring",stiffness:400,damping:35}}>
      <div className="hidden lg:block px-4 pb-3">
        <div className="relative flex items-center h-[72px] px-4 gap-4 cursor-pointer rounded-[22px] overflow-hidden"
          style={glassStyle} onClick={onExpand}>
        <MiniPlayerGlassRefraction song={song}/>
        {/* Left zone: artwork + title/artist */}
        <div className="relative z-10 w-1/3 flex items-center gap-3 min-w-0">
          <CoverArt src={cover(song.id)} gradient={song.gradient} className="w-11 h-11 rounded-[12px] shrink-0 shadow-lg ring-1 ring-white/20">
            {isPlaying && (
              <div className="absolute inset-0 flex items-end justify-center pb-1 bg-black/20">
                <div className="flex items-end gap-0.5 h-3">{[1,2,3].map(i=><motion.div key={i} className="w-0.5 bg-white rounded-full" animate={{height:["30%","100%","60%"]}} transition={{duration:0.7,repeat:Infinity,delay:i*0.15,ease:"easeInOut"}}/>)}</div>
              </div>
            )}
          </CoverArt>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-foreground truncate">{song.title}</p>
            <p className="text-xs text-muted-foreground truncate">{song.artist}</p>
          </div>
        </div>
        {/* Center zone: transport + progress */}
        <div className="relative z-10 w-1/3 flex flex-col items-center gap-1.5" onClick={e=>e.stopPropagation()}>
          <div className="flex items-center gap-1">
            <button type="button" aria-label="Previous track" onPointerDown={preventMouseFocus} onClick={e=>{e.stopPropagation();}} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--mini-player-glass-control)] transition-all duration-[180ms] active:scale-[0.92] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
              <SkipBack className="w-4 h-4 text-foreground/80"/>
            </button>
            <button type="button" aria-label={isPlaying?"Pause":"Play"} onPointerDown={preventMouseFocus} onClick={e=>{e.stopPropagation();onPlayPause();}} className="w-9 h-9 rounded-full flex items-center justify-center bg-[var(--mini-player-glass-control)] hover:bg-[var(--mini-player-glass-control)] transition-all duration-[180ms] active:scale-[0.92] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
              {isPlaying?<Pause className="w-5 h-5 text-foreground fill-foreground"/>:<Play className="w-5 h-5 text-foreground fill-foreground ml-0.5"/>}
            </button>
            <button type="button" aria-label="Next track" onPointerDown={preventMouseFocus} onClick={e=>{e.stopPropagation();onNext();}} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--mini-player-glass-control)] transition-all duration-[180ms] active:scale-[0.92] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
              <SkipForward className="w-4 h-4 text-foreground/80"/>
            </button>
          </div>
          <div className="w-full h-[3px] rounded-full overflow-hidden" style={{background:"var(--mini-player-glass-track)"}}>
            <motion.div className="h-full rounded-full" style={{background:`linear-gradient(90deg,${song.gradient[0]},${song.gradient[1]})`}}
              animate={{width:isPlaying?"65%":"40%"}} transition={{duration:isPlaying?5:0,repeat:isPlaying?Infinity:0,ease:"linear"}}/>
          </div>
        </div>
        {/* Right zone: volume + expand */}
        <div className="relative z-10 w-1/3 flex items-center justify-end gap-2" onClick={e=>e.stopPropagation()}>
          <Volume2 className="w-4 h-4 text-muted-foreground shrink-0"/>
          <div className="w-24"><DesignSlider value={75} onChange={()=>{}} accent={song.gradient[0]}/></div>
          <button type="button" aria-label="Open full player" onPointerDown={preventMouseFocus} onClick={e=>{e.stopPropagation();onExpand();}} className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-[var(--mini-player-glass-control)] transition-all duration-[180ms] active:scale-[0.92] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
            <Maximize2 className="w-4 h-4 text-muted-foreground"/>
          </button>
        </div>
      </div>
      </div>
      {/* Mobile mini player — theme-aware liquid-glass */}
      <div className="lg:hidden mx-3 mb-2">
        <div className="relative flex items-center gap-3 px-3.5 rounded-[22px] cursor-pointer overflow-hidden"
          style={{ ...glassStyle, height:72 }}
          onClick={onExpand}>
          <MiniPlayerGlassRefraction song={song}/>
          <CoverArt src={cover(song.id)} gradient={song.gradient} className="w-11 h-11 rounded-[12px] shrink-0 relative z-10 shadow-lg ring-1 ring-white/20"/>
          <div className="flex-1 min-w-0 relative z-10">
            <p className="text-[14px] font-semibold text-foreground truncate">{song.title}</p>
            <p className="text-[12px] text-muted-foreground truncate">{song.artist}</p>
          </div>
          {/* DesignPink progress bar at bottom */}
          <div className="absolute bottom-0 left-0 right-0 h-[2.5px] rounded-b-[22px] overflow-hidden"
            style={{ background:"var(--mini-player-glass-track)" }}>
            <motion.div className="h-full" style={{ background:"var(--brand-pink)", borderRadius:"0 2px 2px 0" }}
              animate={{ width:isPlaying?"65%":"40%" }}
              transition={{ duration:isPlaying?5:0, repeat:isPlaying?Infinity:0, ease:"linear" }}/>
          </div>
          <div className="flex items-center gap-0.5 relative z-10" onClick={e => e.stopPropagation()}>
            <button type="button" aria-label={isPlaying?"Pause":"Play"} onPointerDown={preventMouseFocus} onClick={onPlayPause}
              className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-[var(--mini-player-glass-control)] active:scale-[0.92] transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
              {isPlaying
                ? <Pause style={{ width:20, height:20, fill:"var(--foreground)", color:"var(--foreground)" }}/>
                : <Play  style={{ width:20, height:20, fill:"var(--foreground)", color:"var(--foreground)", marginLeft:2 }}/>
              }
            </button>
            <button type="button" aria-label="Next track" onPointerDown={preventMouseFocus} onClick={onNext}
              className="w-11 h-11 rounded-full flex items-center justify-center hover:bg-[var(--mini-player-glass-control)] active:scale-[0.92] transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
              <SkipForward style={{ width:20, height:20, color:"var(--muted-foreground)" }}/>
            </button>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// Hook: true when viewport is >= 860w AND >= 520h (two-column player threshold)
function usePlayerWide() {
  const check = () => typeof window !== "undefined" && window.innerWidth >= 860 && window.innerHeight >= 520;
  const [wide, setWide] = useState(check);
  useEffect(() => {
    const fn = () => setWide(check());
    window.addEventListener("resize", fn);
    return () => window.removeEventListener("resize", fn);
  }, []);
  return wide;
}

// Dedicated phone/tablet landscape player. Without this branch, short landscape
// viewports inherit the portrait hero offsets and push playback controls off rhythm.
function usePlayerLandscape() {
  const check = () => typeof window !== "undefined"
    && window.innerWidth >= 640
    && window.innerWidth > window.innerHeight
    && window.innerHeight < 520;
  const [landscape, setLandscape] = useState(check);
  useEffect(() => {
    const fn = () => setLandscape(check());
    window.addEventListener("resize", fn);
    return () => window.removeEventListener("resize", fn);
  }, []);
  return landscape;
}

// true when the sidebar/desktop layout is active (matches Tailwind lg = 1024px)
function useIsDesktop() {
  const check = () => typeof window !== "undefined" && window.innerWidth >= 1024;
  const [desktop, setDesktop] = useState(check);
  useEffect(() => {
    const fn = () => setDesktop(check());
    window.addEventListener("resize", fn);
    return () => window.removeEventListener("resize", fn);
  }, []);
  return desktop;
}

function FullPlayer({ song, isPlaying, onPlay, onPlayPause, onNext, onPrev, onClose, progress, onSeek, volume, onVolume }: {
  song:Song; isPlaying:boolean; onPlayPause:()=>void; onNext:()=>void; onPrev:()=>void;
  onPlay:(song:Song)=>void; onClose:()=>void; progress:number; onSeek:(v:number)=>void; volume:number; onVolume:(v:number)=>void;
}) {
  const [liked, setLiked]        = useState(song.liked);
  const [mobileView, setMobileView] = useState<"player"|"lyrics">("player");
  const [queueOpen, setQueueOpen] = useState(false);
  const [queueTracks, setQueueTracks] = useState<Song[]>(()=>[song,...SONGS.filter(item=>item.id!==song.id)]);
  const [sleepTimer, setSleepTimer] = useState(false);
  const [repeat, setRepeat]      = useState(false);
  const wide = usePlayerWide();
  const landscape = usePlayerLandscape();
  const lyricsScrollRef = useRef<HTMLDivElement>(null);
  const activeLyricRef  = useRef<HTMLDivElement>(null);
  const queueLocateRef = useRef<HTMLButtonElement>(null);
  const queueClearRef = useRef<HTMLButtonElement>(null);
  const queueRowRefs = useRef<Map<number,HTMLDivElement>>(new Map());
  const queueTriggerRef = useRef<HTMLButtonElement>(null);
  const reduceMotion = useReducedMotion();

  useEffect(() => { setLiked(song.liked); }, [song.id]);

  useEffect(() => {
    if (!queueOpen) return;
    const handleKeyDown = (event:KeyboardEvent) => {
      if (event.key === "Escape") {
        setQueueOpen(false);
        window.requestAnimationFrame(() => queueTriggerRef.current?.focus());
      }
    };
    window.addEventListener("keydown",handleKeyDown);
    (queueTracks.length?queueLocateRef.current:queueClearRef.current)?.focus();
    return () => window.removeEventListener("keydown",handleKeyDown);
  },[queueOpen,queueTracks.length]);

  const closeQueue = () => {
    setQueueOpen(false);
    window.requestAnimationFrame(() => queueTriggerRef.current?.focus());
  };

  const currentQueueTrackAvailable = queueTracks.some(item=>item.id===song.id);
  const locateCurrentQueueTrack = () => {
    queueRowRefs.current.get(song.id)?.scrollIntoView({
      behavior:reduceMotion?"auto":"smooth",
      block:"center",
    });
  };

  const currentSec  = Math.round(progress * SONG_DURATION / 100);
  const elapsed     = `${Math.floor(currentSec/60)}:${String(currentSec%60).padStart(2,"0")}`;
  const remainSec   = SONG_DURATION - currentSec;
  const remaining   = `-${Math.floor(remainSec/60)}:${String(remainSec%60).padStart(2,"0")}`;
  const currentTime = progress * SONG_DURATION / 100;
  const activeIdx   = LYRICS.reduce((acc, l, i) => l.time <= currentTime ? i : acc, -1);
  const previewIndex = Math.max(activeIdx,0);
  const previewLine = LYRICS[previewIndex];
  const previewTranslation = LYRIC_TRANSLATIONS[previewLine.time];
  // Auto-scroll active lyric to vertical center (respects prefers-reduced-motion)
  useEffect(() => {
    if (!wide && mobileView !== "lyrics") return;
    const el = activeLyricRef.current;
    const container = lyricsScrollRef.current;
    if (!el || !container) return;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const containerRect = container.getBoundingClientRect();
    const lyricRect = el.getBoundingClientRect();
    const top = container.scrollTop + lyricRect.top - containerRect.top - container.clientHeight/2 + lyricRect.height/2;
    container.scrollTo({ top: Math.max(0, top), behavior: reducedMotion ? "instant" : "smooth" });
  }, [activeIdx, mobileView, wide]);

  // ── Background: artwork-derived blur fill ─────────────────────
  const Backdrop = () => (
    <div className="absolute inset-0 overflow-hidden bg-[#08060e]" aria-hidden="true">
      <div className="absolute inset-0" style={{ background:`linear-gradient(135deg,${song.gradient[0]},${song.gradient[1]})` }}/>
      <img
        src={cover(song.id)}
        alt=""
        className="absolute left-[-12%] top-[-12%] h-[124%] w-[124%] max-w-none scale-110 object-cover opacity-75"
        style={{ filter:"blur(54px) saturate(1.18)" }}
      />
      <div className="absolute inset-0 bg-black/10"/>
      <div
        className="absolute inset-0"
        style={{ background:"linear-gradient(180deg,rgba(8,6,14,0.28) 0%,rgba(8,6,14,0.46) 52%,rgba(8,6,14,0.72) 100%)" }}
      />
    </div>
  );

  // ── Transport mirrors the Compose MusicPanel ──────────────────
  const Transport = () => (
    <div className="flex items-center justify-between">
      <motion.button type="button" aria-label="Sleep timer" aria-pressed={sleepTimer} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={() => setSleepTimer(!sleepTimer)}
        className="relative flex items-center justify-center w-10 h-10 rounded-full transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
        style={{ color: sleepTimer ? "var(--brand-pink)" : "rgba(255,255,255,0.45)" }}>
        <Timer style={{ width:18, height:18 }}/>
        {sleepTimer && <span className="absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary"/>}
      </motion.button>
      <motion.button type="button" aria-label="Previous track" whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={onPrev}
        className="flex items-center justify-center w-11 h-11 rounded-full transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
        style={{ color:"rgba(255,255,255,0.90)" }}>
        <SkipBack style={{ width:28, height:28, fill:"rgba(255,255,255,0.90)" }}/>
      </motion.button>
      <motion.button type="button" aria-label={isPlaying?"Pause":"Play"} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={onPlayPause}
        className="flex items-center justify-center rounded-full bg-white transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
        style={{ width:62, height:62, boxShadow:"0 4px 20px rgba(255,255,255,0.20)" }}>
        {isPlaying
          ? <Pause  style={{ width:24, height:24, fill:"#06040e", color:"#06040e" }}/>
          : <Play   style={{ width:24, height:24, fill:"#06040e", color:"#06040e", marginLeft:2 }}/>}
      </motion.button>
      <motion.button type="button" aria-label="Next track" whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={onNext}
        className="flex items-center justify-center w-11 h-11 rounded-full transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
        style={{ color:"rgba(255,255,255,0.90)" }}>
        <SkipForward style={{ width:28, height:28, fill:"rgba(255,255,255,0.90)" }}/>
      </motion.button>
      <motion.button type="button" aria-label="Repeat" whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={() => setRepeat(!repeat)}
        className="relative flex items-center justify-center w-10 h-10 rounded-full transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
        style={{ color: repeat ? "var(--brand-pink)" : "rgba(255,255,255,0.45)" }}>
        <Repeat style={{ width:17, height:17 }}/>
        {repeat && <span className="absolute bottom-1.5 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary"/>}
      </motion.button>
    </div>
  );

  // ── Lyrics: preserve the wide scale while giving handheld layouts more air ──
  const lyricStyle = (dist: number, density:"wide"|"mobile"|"landscape"="wide"): React.CSSProperties => {
    const opacity =
      dist === 0 ? 1 :
      dist === 1 ? 0.62 :
      dist === 2 ? 0.42 :
      0.26;
    const blurPx =
      dist <= 1 ? 0 :
      Math.min(3.45, (Math.min(dist,4)-1)*1.15);
    const fontSize = dist === 0
      ? density === "landscape" ? 24 : density === "mobile" ? 28 : 32
      : density === "landscape" ? 20 : density === "mobile" ? 23 : 27;
    return {
      opacity,
      filter: blurPx > 0 ? `blur(${blurPx}px)` : undefined,
      fontSize,
      fontWeight: dist === 0 ? 700 : 600,
      lineHeight: dist === 0 ? "1.25" : "1.30",
      color: "white",
      textAlign: "left",
      transition: "opacity 280ms ease, filter 280ms ease, transform 280ms ease",
      transform: dist === 0 ? "scale(1)" : "scale(0.94)",
      transformOrigin: "left center",
    };
  };

  const LyricsPreview = ({ density }: { density:"mobile"|"landscape" }) => {
    if (density==="landscape") {
      const start = Math.min(previewIndex,Math.max(0,LYRICS.length-2));
      return (
        <span className="block min-w-0 max-w-[520px]" aria-live="polite">
          {LYRICS.slice(start,start+2).map((line,index) => {
            const active = start+index===previewIndex;
            return (
              <span key={line.time} className={cn("block",index===0?"":"mt-2.5")}>
                <span className={cn("block whitespace-normal break-words text-[17px] leading-[1.38] transition-[color,opacity] duration-300",active?"font-bold text-white":"font-semibold text-white/48")}>
                  {line.text}
                </span>
                <span className={cn("mt-1 block whitespace-normal break-words text-[12px] font-medium leading-4",active?"text-white/55":"text-white/30")}>
                  {LYRIC_TRANSLATIONS[line.time]}
                </span>
              </span>
            );
          })}
        </span>
      );
    }

    return (
      <span className="block min-w-0 max-w-[520px]" aria-live="polite">
        <span
          className="block whitespace-normal break-words font-bold text-white transition-[color,opacity] duration-300"
          style={{ fontSize:17,lineHeight:"1.38" }}>
          {previewLine.text}
        </span>
        <span className="mt-1.5 block whitespace-normal break-words text-[12px] font-medium leading-4 text-white/55">
          {previewTranslation}
        </span>
      </span>
    );
  };

  const LyricsContent = () => (
    <div ref={lyricsScrollRef}
      className="min-h-0 flex-1 overflow-y-auto hide-scrollbar"
      style={{
        maskImage:"linear-gradient(to bottom,transparent 0%,black 12%,black 88%,transparent 100%)",
        WebkitMaskImage:"linear-gradient(to bottom,transparent 0%,black 12%,black 88%,transparent 100%)",
      }}>
      <div style={{ height:"35vh" }}/>
      <div style={{ maxWidth:680, margin:"0 auto", padding:"0 28px" }}>
        {LYRICS.map((line, index) => {
          const dist = Math.abs(index-activeIdx);
          return (
            <div key={line.time} ref={index===activeIdx?activeLyricRef:undefined}>
              <button type="button" onClick={() => onSeek(line.time/SONG_DURATION*100)}
                aria-current={index===activeIdx?"true":undefined}
                className="block w-full rounded-xl px-3 py-1 text-left outline-none focus-visible:ring-2 focus-visible:ring-white/35"
                style={{ ...lyricStyle(dist),marginBottom:18,cursor:"pointer" }}>
                {line.text}
              </button>
            </div>
          );
        })}
        <div style={{ height:"35vh" }}/>
      </div>
    </div>
  );

  const MobileHeroProgress = ({ compact=false }: { compact?:boolean }) => (
    <div className="w-full">
      <div className={cn("group relative flex items-center",compact?"h-4":"h-6")}>
        <div className="absolute inset-x-0 top-1/2 h-[3px] -translate-y-1/2 rounded-full bg-white/20">
          <div className="absolute inset-y-0 left-0 rounded-full bg-white/85" style={{ width:`${progress}%` }}/>
          <div className="absolute top-1/2 h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-white shadow-sm" style={{ left:`${progress}%` }}/>
        </div>
        <input type="range" min={0} max={100} value={progress} onChange={e=>onSeek(Number(e.target.value))}
          className="absolute inset-0 h-full w-full cursor-pointer opacity-0" aria-label="Seek"/>
      </div>
      <div className={cn("grid grid-cols-2 items-center",compact?"pt-0.5":"pt-1")}>
        <span className={cn("text-left font-mono tabular-nums text-white/52",compact?"text-[12px]":"text-[15px]")}>{elapsed}</span>
        <span className={cn("text-right font-mono tabular-nums text-white/52",compact?"text-[12px]":"text-[15px]")}>{remaining}</span>
      </div>
    </div>
  );

  const MobileHeroTransport = ({ compact=false }: { compact?:boolean }) => (
    <div className={cn("grid grid-cols-5 items-center justify-items-center",compact?"h-[62px]":"mt-1 h-[84px]")}>
      <motion.button type="button" aria-label="Repeat" aria-pressed={repeat} whileTap={{ scale:0.90 }} onPointerDown={preventMouseFocus} onClick={() => setRepeat(!repeat)}
        className={cn("flex items-center justify-center rounded-full outline-none focus-visible:ring-2 focus-visible:ring-white/40",compact?"h-11 w-11":"h-14 w-14")}
        style={{ color:repeat?"var(--brand-pink)":"rgba(255,255,255,0.82)" }}>
        <Repeat className={compact?"h-[21px] w-[21px]":"h-6 w-6"}/>
      </motion.button>
      <motion.button type="button" aria-label="Previous track" whileTap={{ scale:0.90 }} onPointerDown={preventMouseFocus} onClick={onPrev}
        className={cn("flex items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40",compact?"h-11 w-11":"h-14 w-14")}>
        <SkipBack className={cn("fill-current",compact?"h-7 w-7":"h-[30px] w-[30px]")}/>
      </motion.button>
      <motion.button type="button" aria-label={isPlaying?"Pause":"Play"} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={onPlayPause}
        className={cn("flex items-center justify-center rounded-full bg-white/[0.16] text-white shadow-[0_10px_34px_rgba(0,0,0,0.18)] outline-none backdrop-blur-lg focus-visible:ring-2 focus-visible:ring-white/45",compact?"h-[58px] w-[58px]":"h-[72px] w-[72px]")}>
        {isPlaying
          ? <Pause className={cn("fill-current",compact?"h-7 w-7":"h-8 w-8")}/>
          : <Play className={cn("ml-1 fill-current",compact?"h-8 w-8":"h-9 w-9")}/>
        }
      </motion.button>
      <motion.button type="button" aria-label="Next track" whileTap={{ scale:0.90 }} onPointerDown={preventMouseFocus} onClick={onNext}
        className={cn("flex items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40",compact?"h-11 w-11":"h-14 w-14")}>
        <SkipForward className={cn("fill-current",compact?"h-7 w-7":"h-[30px] w-[30px]")}/>
      </motion.button>
      <motion.button ref={queueTriggerRef} type="button" aria-label="Open play queue" aria-haspopup="dialog" aria-expanded={queueOpen}
        whileTap={{ scale:0.90 }} onPointerDown={preventMouseFocus} onClick={() => setQueueOpen(true)}
        className={cn("flex items-center justify-center rounded-full text-white/72 outline-none focus-visible:ring-2 focus-visible:ring-white/40",compact?"h-11 w-11":"h-14 w-14")}>
        <ListMusic className={compact?"h-[22px] w-[22px]":"h-[25px] w-[25px]"}/>
      </motion.button>
    </div>
  );

  const MobileTrackHeader = () => (
    <div className="flex h-[136px] shrink-0 items-center gap-2 px-4 pt-[50px]">
      <CoverArt src={cover(song.id)} gradient={song.gradient} className="h-14 w-14 shrink-0 rounded-[13px] shadow-lg ring-1 ring-white/10"/>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[20px] font-bold leading-6 text-white">{song.title}</p>
        <p className="mt-1 truncate text-[15px]" style={{ color:"rgba(255,255,255,0.62)" }}>{song.artist}</p>
      </div>
      <motion.button type="button" aria-label={liked?"Remove from favorites":"Add to favorites"} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={() => setLiked(!liked)}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white/10 outline-none focus-visible:ring-2 focus-visible:ring-white/40">
        <Heart style={{ width:22,height:22,fill:liked?"var(--brand-pink)":"none",color:liked?"var(--brand-pink)":"white" }}/>
      </motion.button>
      <button type="button" aria-label="More options" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white/10 text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
        <MoreHorizontal style={{ width:23,height:23 }}/>
      </button>
    </div>
  );

  const QueueDialog = () => {
    const sideDialog = wide||landscape;

    return (
      <AnimatePresence>
        {queueOpen&&(
          <motion.div className="dark fixed inset-0 z-[360]" role="presentation"
            initial={{ opacity:0 }} animate={{ opacity:1 }} exit={{ opacity:0 }} transition={{ duration:0.18 }}>
            <div className="absolute inset-0 bg-black/50 backdrop-blur-[2px]" onClick={closeQueue}/>
            <motion.section role="dialog" aria-modal="true" aria-labelledby="queue-dialog-title"
              initial={reduceMotion?{opacity:0}:sideDialog?{x:"100%"}:{y:"100%"}}
              animate={reduceMotion?{opacity:1}:sideDialog?{x:0}:{y:0}}
              exit={reduceMotion?{opacity:0}:sideDialog?{x:"100%"}:{y:"100%"}}
              transition={reduceMotion?{duration:0.12}:{type:"spring",stiffness:360,damping:38,mass:0.9}}
              className={cn(
                "absolute flex flex-col overflow-hidden bg-card text-foreground shadow-2xl",
                sideDialog
                  ? "inset-y-0 right-0 w-[min(480px,100vw)] border-l border-border"
                  : "bottom-0 left-1/2 h-[min(76vh,720px)] w-full max-w-[680px] -translate-x-1/2 rounded-t-[28px] border-t border-border",
              )}>
              {!sideDialog&&<div aria-hidden="true" className="mx-auto mt-3 h-1.5 w-12 shrink-0 rounded-full bg-muted-foreground/30"/>}
              <header className="flex shrink-0 items-center gap-3 border-b border-border px-5 pb-4 pt-5">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-primary/12 text-primary">
                  <ListMusic className="h-5 w-5"/>
                </div>
                <div className="min-w-0 flex-1">
                  <h2 id="queue-dialog-title" className="truncate text-[20px] font-bold leading-6">当前播放列表({queueTracks.length})</h2>
                </div>
                <button ref={queueLocateRef} type="button" aria-label="定位当前歌曲" title="定位当前歌曲" disabled={!currentQueueTrackAvailable}
                  onPointerDown={preventMouseFocus} onClick={locateCurrentQueueTrack}
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground outline-none transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-35 focus-visible:ring-2 focus-visible:ring-primary/40">
                  <LocateFixed className="h-5 w-5"/>
                </button>
                <button ref={queueClearRef} type="button" aria-label="清空播放列表" title="清空播放列表"
                  onPointerDown={preventMouseFocus} onClick={()=>setQueueTracks([])}
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground outline-none transition-colors hover:bg-destructive/12 hover:text-destructive focus-visible:ring-2 focus-visible:ring-primary/40">
                  <Trash2 className="h-5 w-5"/>
                </button>
              </header>
              <div className="hide-scrollbar min-h-0 flex-1 overflow-y-auto px-2 pb-[max(16px,env(safe-area-inset-bottom))] pt-2">
                {queueTracks.length ? queueTracks.map((item,index)=><div key={item.id} ref={element=>{
                  if (element) queueRowRefs.current.set(item.id,element);
                  else queueRowRefs.current.delete(item.id);
                }}>
                  <PlaylistTrackRow song={item} trackNumber={index+1} active={isPlaying&&item.id===song.id} onPlay={onPlay}/>
                </div>) : (
                  <div className="flex min-h-52 flex-col items-center justify-center px-6 text-center">
                    <ListMusic className="h-9 w-9 text-muted-foreground/45"/>
                    <p className="mt-3 text-sm font-medium text-muted-foreground">播放列表为空</p>
                  </div>
                )}
              </div>
            </motion.section>
          </motion.div>
        )}
      </AnimatePresence>
    );
  };

  // ══════════════════════════════════════════════════════════════
  // WIDE LAYOUT (≥860×520) — Apple Music–style 42/58 grid
  // ══════════════════════════════════════════════════════════════
  if (landscape) return (
    <motion.div initial={{ opacity:0 }} animate={{ opacity:1 }} exit={{ opacity:0 }}
      transition={{ duration:0.22,ease:"easeOut" }}
      className="fixed inset-0 z-[300] flex overflow-hidden">
      <Backdrop/>
      <MobileStatusBar inverse/>
      <MobileLandscapeHomeIndicator inverse/>

      <aside className="relative z-10 order-2 flex w-[47%] min-w-[413px] shrink-0 self-center flex-col items-end justify-center pl-5 pr-[87px]"
        style={{ height:"min(clamp(300px, 82vh, 340px), calc(100vh - 34px))" }}>
        <motion.div animate={{ scale:isPlaying?1:0.95 }} transition={{ type:"spring",stiffness:180,damping:26 }}>
          <CoverArt src={cover(song.id)} gradient={song.gradient}
            className="shadow-[0_16px_42px_rgba(0,0,0,0.32)] ring-1 ring-white/10"
            style={{ width:"min(clamp(300px, 82vh, 340px), calc(100vh - 34px))",height:"min(clamp(300px, 82vh, 340px), calc(100vh - 34px))",borderRadius:18 }}/>
        </motion.div>
      </aside>

      <section className="relative z-10 order-1 flex min-w-0 flex-1 self-center flex-col pl-10 pr-2"
        style={{ height:"min(clamp(300px, 82vh, 340px), calc(100vh - 34px))" }}>
        <AnimatePresence mode="wait" initial={false}>
          {mobileView==="player"&&(
            <motion.div key="landscape-player" initial={{ opacity:0,x:-10 }} animate={{ opacity:1,x:0 }} exit={{ opacity:0,x:-10 }}
              transition={{ duration:0.18,ease:"easeOut" }} className="flex min-h-0 flex-1 flex-col">
              <div className="flex shrink-0 items-start gap-2 px-2">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[20px] font-bold leading-7 text-white">{song.title}</p>
                  <p className="mt-0.5 truncate text-[14px] font-medium text-white/52">{song.artist}</p>
                </div>
                <motion.button type="button" aria-label={liked?"Remove from favorites":"Add to favorites"} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={() => setLiked(!liked)}
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
                  <Heart className="h-[22px] w-[22px]" style={{ fill:liked?"var(--brand-pink)":"none",color:liked?"var(--brand-pink)":"white" }}/>
                </motion.button>
                <button type="button" aria-label="More options" className="flex h-10 w-9 shrink-0 items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
                  <MoreVertical className="h-6 w-6"/>
                </button>
              </div>
              <button type="button" aria-label="Open lyrics" onClick={() => setMobileView("lyrics")}
                className="mt-2 min-h-0 flex-1 overflow-hidden rounded-2xl px-2 pb-2 pt-0 text-left outline-none focus-visible:ring-2 focus-visible:ring-white/35">
                <LyricsPreview density="landscape"/>
              </button>
              <div className="shrink-0 px-2">
                <div className="-translate-y-2"><MobileHeroProgress compact/></div>
                <MobileHeroTransport compact/>
              </div>
            </motion.div>
          )}

          {mobileView==="lyrics"&&(
            <motion.div key="landscape-lyrics" initial={{ opacity:0,x:12 }} animate={{ opacity:1,x:0 }} exit={{ opacity:0,x:12 }}
              transition={{ duration:0.18,ease:"easeOut" }} className="flex min-h-0 flex-1 flex-col">
              <div className="flex h-10 shrink-0 items-center px-2 pb-1">
                <p className="text-[12px] font-semibold uppercase tracking-[0.12em] text-white/36">Lyrics</p>
              </div>
              <div ref={lyricsScrollRef} className="hide-scrollbar min-h-0 flex-1 overflow-y-auto px-2"
                style={{ maskImage:"linear-gradient(to bottom,transparent 0%,black 10%,black 90%,transparent 100%)",WebkitMaskImage:"linear-gradient(to bottom,transparent 0%,black 10%,black 90%,transparent 100%)" }}>
                <div style={{ height:"22vh" }}/>
                {LYRICS.map((line,i) => {
                  const dist = Math.abs(i-activeIdx);
                  const translation = LYRIC_TRANSLATIONS[line.time];
                  return (
                    <div key={i} ref={i===activeIdx?activeLyricRef:undefined}>
                      <button type="button" onClick={() => onSeek(line.time/SONG_DURATION*100)} aria-current={i===activeIdx?"true":undefined}
                        className="block w-full rounded-xl px-1 py-1 text-left outline-none focus-visible:ring-2 focus-visible:ring-white/35"
                        style={{ ...lyricStyle(dist,"landscape"),marginBottom:14,cursor:"pointer" }}>
                        <span className="block">{line.text}</span>
                        {translation&&<span className="mt-1 block text-[13px] font-medium leading-5" style={{ color:i===activeIdx?"rgba(255,255,255,0.54)":"rgba(255,255,255,0.32)" }}>{translation}</span>}
                      </button>
                    </div>
                  );
                })}
                <div style={{ height:"22vh" }}/>
              </div>
            </motion.div>
          )}

        </AnimatePresence>
      </section>
      <QueueDialog/>
    </motion.div>
  );

  if (wide) return (
    <motion.div initial={{ opacity:0 }} animate={{ opacity:1 }} exit={{ opacity:0 }}
      transition={{ duration:0.24, ease:"easeOut" }}
      className="fixed inset-0 z-[300] flex flex-col overflow-hidden">
      <Backdrop/>

      {/* ── Top control rail (52px) ── */}
      <div className="relative z-10 flex h-16 shrink-0 items-center px-[clamp(24px,4vw,64px)]"
        style={{ borderBottom:"1px solid rgba(255,255,255,0.06)" }}>
        {/* Collapse */}
        <motion.button type="button" aria-label="Close player" whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={onClose}
          className="flex h-9 w-9 items-center justify-center rounded-full outline-none transition-all duration-[180ms] focus-visible:ring-2 focus-visible:ring-primary/40">
          <ChevronDown style={{ width:18, height:18, color:"rgba(255,255,255,0.72)" }}/>
        </motion.button>
        <div className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-center">
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-white/48">Now Playing</p>
        </div>
      </div>

      {/* ── Two-column body ── */}
      <div className="relative z-10 mx-auto grid w-full max-w-[1600px] min-h-0 flex-1 gap-[clamp(28px,4vw,72px)] px-[clamp(24px,4vw,64px)] py-[clamp(20px,3vh,36px)]"
        style={{ gridTemplateColumns:"minmax(300px,0.82fr) minmax(380px,1.18fr)" }}>

        {/* Left 42%: artwork top → metadata → progress → transport */}
        <div className="flex min-h-0 flex-col justify-center overflow-hidden">
          {/* Artwork — near top, clamp sizing, square, object-cover */}
          <div className="mx-auto mb-[clamp(14px,2.4vh,24px)] shrink-0">
            <motion.div
              animate={{ scale: isPlaying ? 1 : 0.94 }}
              transition={{ type:"spring", stiffness:180, damping:26 }}>
              <CoverArt
                src={cover(song.id)}
                gradient={song.gradient}
                style={{
                  width:"min(34vw, calc(100vh - 360px), 420px)",
                  height:"min(34vw, calc(100vh - 360px), 420px)",
                  borderRadius:22,
                  boxShadow:"0 24px 64px rgba(0,0,0,0.38)",
                  flexShrink:0,
                }}/>
            </motion.div>
          </div>

          {/* Metadata */}
          <div className="mx-auto mb-[clamp(12px,2vh,20px)] flex w-full max-w-[420px] shrink-0 items-start gap-3">
            <div className="flex-1 min-w-0">
              <p className="truncate font-bold"
                style={{ fontSize:"clamp(20px,2vw,25px)", lineHeight:"30px", color:"white" }}>{song.title}</p>
              <div className="flex items-center gap-2 mt-1">
                <p className="text-[14px] truncate" style={{ color:"rgba(255,255,255,0.62)" }}>{song.artist}</p>
              </div>
            </div>
            <motion.button type="button" aria-label={liked?"Remove from favorites":"Add to favorites"} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={() => setLiked(!liked)}
              className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
              <Heart className="h-[28px] w-[28px]" style={{ fill:liked?"var(--brand-pink)":"none",color:liked?"var(--brand-pink)":"white" }}/>
            </motion.button>
            <button type="button" aria-label="More options" className="flex h-12 w-10 shrink-0 items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
              <MoreVertical className="h-7 w-7"/>
            </button>
          </div>

          {/* Progress */}
          <div className="mx-auto mb-[clamp(12px,2vh,20px)] w-full max-w-[420px] shrink-0">
            <MobileHeroProgress/>
          </div>

          {/* Transport */}
          <div className="mx-auto w-full max-w-[420px] shrink-0">
            <MobileHeroTransport/>
          </div>
        </div>

        {/* Divider */}
        <div className="hidden shrink-0 self-stretch my-8"
          style={{ width:1, background:"rgba(255,255,255,0.07)" }}/>

        {/* Right 58%: synced lyrics */}
        <div className="flex min-w-0 flex-col overflow-hidden p-[clamp(14px,2vw,24px)]">
          <LyricsContent/>
        </div>
      </div>
      <QueueDialog/>
    </motion.div>
  );

  // ══════════════════════════════════════════════════════════════
  // COMPACT / MOBILE LAYOUT
  // ══════════════════════════════════════════════════════════════
  return (
    <motion.div initial={{ y:"100%" }} animate={{ y:0 }} exit={{ y:"100%" }}
      transition={{ type:"spring", stiffness:280, damping:32 }}
      className="fixed inset-0 z-[300] flex flex-col overflow-hidden">
      <Backdrop/>
      <MobileStatusBar inverse/>
      <AnimatePresence initial={false}>
        {mobileView==="player"&&(
          <motion.div key="mobile-player"
            initial={{ opacity:0,x:-12 }}
            animate={{ opacity:1,x:0,scale:1 }}
            exit={{ opacity:0,x:-12 }}
            transition={{ duration:0.2,ease:"easeOut" }}
            className="absolute inset-0 z-10 h-full w-full overflow-hidden transform-gpu will-change-transform">
            <div className="relative z-10 flex h-full flex-col" style={{ paddingTop:"max(90px,calc(env(safe-area-inset-top) + 56px))",paddingBottom:"max(44px,calc(env(safe-area-inset-bottom) + 28px))" }}>
              <div
                className="mx-auto flex h-full min-h-0 flex-col items-stretch"
                style={{ width:"min(88%, 356px)" }}>
                <motion.div className="aspect-square w-full shrink-0" animate={{ scale:isPlaying?1:0.96 }} transition={{ type:"spring",stiffness:180,damping:26 }}>
                  <CoverArt src={cover(song.id)} gradient={song.gradient}
                    className="h-full w-full ring-1 ring-white/10 shadow-[0_20px_44px_rgba(0,0,0,0.28)]"
                    style={{ borderRadius:28 }}/>
                </motion.div>

                <div className="mt-5 flex w-full shrink-0 items-center gap-2 px-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[20px] font-bold leading-7 text-white">{song.title}</p>
                    <p className="mt-1 truncate text-[14px] font-medium text-white/56">{song.artist}</p>
                  </div>
                  <motion.button type="button" aria-label={liked?"Remove from favorites":"Add to favorites"} whileTap={{ scale:0.92 }} onPointerDown={preventMouseFocus} onClick={() => setLiked(!liked)}
                    className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
                    <Heart className="h-[28px] w-[28px]" style={{ fill:liked?"var(--brand-pink)":"none",color:liked?"var(--brand-pink)":"white" }}/>
                  </motion.button>
                  <button type="button" aria-label="More options" className="flex h-12 w-10 shrink-0 items-center justify-center rounded-full text-white outline-none focus-visible:ring-2 focus-visible:ring-white/40">
                    <MoreVertical className="h-7 w-7"/>
                  </button>
                </div>

                <button type="button" aria-label="Open lyrics" onClick={() => setMobileView("lyrics")}
                  className="mt-5 w-full shrink-0 overflow-visible rounded-2xl px-2 text-left outline-none focus-visible:ring-2 focus-visible:ring-white/35">
                  <LyricsPreview density="mobile"/>
                </button>
                <div className="min-h-3 flex-1"/>

                <div className="w-full shrink-0 px-2">
                  <div className="-translate-y-2"><MobileHeroProgress/></div>
                  <MobileHeroTransport/>
                </div>
              </div>
            </div>
          </motion.div>
        )}

        {mobileView==="lyrics"&&(
          <motion.div key="mobile-lyrics"
            initial={{ opacity:0,x:16 }}
            animate={{ opacity:1,x:0,scale:1 }}
            exit={{ opacity:0,x:16 }}
            transition={{ duration:0.2,ease:"easeOut" }}
            className="absolute inset-0 z-10 flex h-full w-full flex-col transform-gpu will-change-transform">
            <MobileTrackHeader/>
            <div ref={lyricsScrollRef} className="hide-scrollbar min-h-0 flex-1 overflow-y-auto px-5 pb-[38px]"
              style={{ maskImage:"linear-gradient(to bottom,transparent 0%,black 8%,black 94%,transparent 100%)",WebkitMaskImage:"linear-gradient(to bottom,transparent 0%,black 8%,black 94%,transparent 100%)" }}>
              <div style={{ height:"30vh" }}/>
              {LYRICS.map((line,i) => {
                const dist = Math.abs(i-activeIdx);
                const translation = LYRIC_TRANSLATIONS[line.time];
                return (
                  <div key={i} ref={i===activeIdx?activeLyricRef:undefined}>
                    <button type="button" onClick={() => onSeek(line.time/SONG_DURATION*100)} aria-current={i===activeIdx?"true":undefined}
                      className="block w-full rounded-xl px-1 py-1 text-left outline-none focus-visible:ring-2 focus-visible:ring-white/35"
                      style={{ ...lyricStyle(dist,"mobile"),marginBottom:22,cursor:"pointer" }}>
                      <span className="block" style={i===activeIdx?{
                        color:"transparent",
                        background:"linear-gradient(90deg,#ffffff 0%,#ffffff 58%,rgba(255,255,255,0.32) 58%,rgba(255,255,255,0.32) 100%)",
                        WebkitBackgroundClip:"text",
                        backgroundClip:"text",
                      }:undefined}>{line.text}</span>
                      {translation&&<span className="mt-1.5 block text-[14px] font-medium leading-5" style={{ color:i===activeIdx?"rgba(255,255,255,0.54)":"rgba(255,255,255,0.34)" }}>{translation}</span>}
                    </button>
                  </div>
                );
              })}
              <div style={{ height:"30vh" }}/>
            </div>
          </motion.div>
        )}

      </AnimatePresence>

      <QueueDialog/>
      <MobileHomeIndicator inverse/>
    </motion.div>
  );
}

// ─────────────────────────────────────────────────────────────
// APP PAGES
// ─────────────────────────────────────────────────────────────
// ── Daily Picks liquid-glass hero ─────────────────────────────
function HalcyonDailyPicksBackground() {
  const reduceMotion = useReducedMotion();
  const blobs = [
    { color:"var(--daily-picks-blob-1)", size:190, left:"-18%", top:"-55%", x:[0,84,32,0], y:[0,18,82,0], scale:[1,1.18,0.94,1], duration:18 },
    { color:"var(--daily-picks-blob-2)", size:180, left:"42%", top:"-48%", x:[0,-58,-22,0], y:[0,62,28,0], scale:[1.04,0.92,1.2,1.04], duration:22 },
    { color:"var(--daily-picks-blob-3)", size:210, left:"54%", top:"28%", x:[0,-76,16,0], y:[0,-36,-18,0], scale:[0.96,1.16,1,0.96], duration:26 },
    { color:"var(--daily-picks-blob-4)", size:170, left:"8%", top:"48%", x:[0,62,98,0], y:[0,-54,-16,0], scale:[1.12,0.96,1.18,1.12], duration:30 },
  ];
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none" aria-hidden="true"
      style={{ background:"var(--daily-picks-surface)" }}>
      {blobs.map((blob,index)=>(
        <motion.div key={index} className="absolute rounded-full will-change-transform"
          style={{
            width:blob.size,
            height:blob.size,
            left:blob.left,
            top:blob.top,
            background:blob.color,
            filter:"blur(32px)",
            opacity:0.9,
          }}
          animate={reduceMotion?undefined:{ x:blob.x, y:blob.y, scale:blob.scale, rotate:[0,90,210,360] }}
          transition={{ duration:blob.duration, repeat:Infinity, ease:"easeInOut" }}/>
      ))}
      <motion.div className="absolute -inset-[35%] opacity-35"
        style={{
          background:"radial-gradient(circle at 20% 30%,rgba(255,255,255,0.30),transparent 34%), radial-gradient(circle at 78% 70%,rgba(255,255,255,0.14),transparent 30%)",
          mixBlendMode:"soft-light",
        }}
        animate={reduceMotion?undefined:{ rotate:[0,360] }}
        transition={{ duration:70, repeat:Infinity, ease:"linear" }}/>
    </div>
  );
}

function OverflowMarquee({ text }: { text:string }) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLSpanElement>(null);
  const [dimensions,setDimensions] = useState({ viewport:0, content:0 });
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    const measure = () => setDimensions({
      viewport:viewportRef.current?.clientWidth??0,
      content:contentRef.current?.scrollWidth??0,
    });
    measure();
    const observer = new ResizeObserver(measure);
    if (viewportRef.current) observer.observe(viewportRef.current);
    if (contentRef.current) observer.observe(contentRef.current);
    return () => observer.disconnect();
  },[text]);

  const isOverflowing = dimensions.content > dimensions.viewport + 1;
  const distance = dimensions.content + 32;

  return (
    <div ref={viewportRef} className="w-[180px] overflow-hidden whitespace-nowrap" role="status" aria-live="polite" aria-label={text} title={text}>
      <motion.div key={text} className="flex w-max" style={{ columnGap:32 }} aria-hidden="true"
        animate={isOverflowing&&!reduceMotion?{ x:[0,0,-distance,-distance] }:{ x:0 }}
        transition={isOverflowing&&!reduceMotion?{ duration:Math.max(8,distance/28), times:[0,0.12,0.88,1], repeat:Infinity, ease:"linear" }:{ duration:0 }}>
        <span ref={contentRef}>{text}</span>
        {isOverflowing&&!reduceMotion&&<span>{text}</span>}
      </motion.div>
    </div>
  );
}

function DailyPicksHero({ onPlay, currentSong }: { onPlay:(s:Song)=>void; currentSong:Song|null }) {
  const [fallbackSong] = useState(() => SONGS[Math.floor(Math.random()*SONGS.length)]);
  const currentTrackTitle = currentSong?.title??fallbackSong.title;
  const nowPlayingLabel = `Now Playing: ${currentTrackTitle}`;
  return (
    <div className="mx-4 lg:mx-0 mt-5 relative rounded-[22px] overflow-hidden"
      style={{ height:152 }}>
      <HalcyonDailyPicksBackground/>
      {/* Airy luminous sheen layer */}
      <div className="absolute inset-0 pointer-events-none"
        style={{ background:"var(--daily-picks-sheen)" }}/>
      {/* Subtle inner top highlight */}
      <div className="absolute top-0 inset-x-0 h-px"
        style={{ background:"var(--daily-picks-highlight)" }}/>

      {/* Left: text + play. Reserve the right-side artwork zone on compact screens. */}
      <div className="absolute left-5 right-[154px] top-0 bottom-0 flex min-w-0 flex-col justify-center lg:right-auto">
        <p className="text-[20px] font-black leading-tight mb-1" style={{ color:"var(--daily-picks-foreground)" }}>Daily Picks</p>
        <div className="mb-3 min-w-0 lg:hidden" style={{ color:"var(--daily-picks-muted)" }}>
          <p className="text-[10px] font-semibold uppercase tracking-[0.08em]">Now playing</p>
          <p className="mt-0.5 truncate text-[14px] font-medium" title={currentTrackTitle}>{currentTrackTitle}</p>
        </div>
        <div className="mb-3 hidden text-[13px] lg:block" style={{ color:"var(--daily-picks-muted)" }}>
          <OverflowMarquee text={nowPlayingLabel}/>
        </div>
        <motion.button type="button" whileTap={{ scale:0.95 }} onPointerDown={preventMouseFocus} onClick={() => onPlay(SONGS[0])}
          aria-label="Play Daily Picks"
          className="self-start flex h-12 w-12 items-center justify-center text-primary outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
          <Play className="h-8 w-8 fill-none" strokeWidth={2.25}/>
        </motion.button>
      </div>

      {/* Right: three overlapping expressive cover shapes */}
      <div className="absolute right-4 top-1/2 -translate-y-1/2" style={{ width:140, height:112 }}>
        {/* Circle */}
        <div className="absolute w-[72px] h-[72px] rounded-full overflow-hidden"
          style={{ top:0, right:0, transform:"rotate(-6deg)", border:"2.5px solid rgba(255,255,255,0.75)", boxShadow:"0 4px 16px rgba(0,0,0,0.14)" }}>
          <img src={COVERS[3]} alt="" className="w-full h-full object-cover"/>
        </div>
        {/* Scalloped / organic blob */}
        <div className="absolute w-[65px] h-[65px] overflow-hidden"
          style={{ bottom:0, right:20, transform:"rotate(8deg)", borderRadius:"62% 38% 46% 54% / 54% 46% 54% 46%", border:"2.5px solid rgba(255,255,255,0.70)", boxShadow:"0 4px 14px rgba(0,0,0,0.12)" }}>
          <img src={COVERS[5]} alt="" className="w-full h-full object-cover"/>
        </div>
        {/* Four-lobed clover */}
        <div className="absolute w-[60px] h-[60px] overflow-hidden"
          style={{ top:22, left:0, transform:"rotate(14deg)", borderRadius:"50% 50% 50% 50% / 38% 38% 62% 62%", border:"2.5px solid rgba(255,255,255,0.65)", boxShadow:"0 6px 20px rgba(0,0,0,0.15)" }}>
          <img src={COVERS[7]} alt="" className="w-full h-full object-cover"/>
        </div>
      </div>
    </div>
  );
}

// ── Recently played ranked row ─────────────────────────────────
function RecentlyPlayedRow({ rank, song, playedAt, detail, showDetail=true, isPlaying, onPlay }: {
  rank:number; song:Song; playedAt:string; detail?:string; showDetail?:boolean; isPlaying:boolean; onPlay:(s:Song)=>void;
}) {
  const reduceMotion = useReducedMotion();
  return (
    <motion.button whileTap={{ scale:0.985 }} transition={LIST_ROW_TRANSITION}
      onPointerDown={preventMouseFocus} onClick={() => onPlay(song)}
      className={cn("flex w-full items-center gap-3 border-b border-border/60 px-3.5 py-3 text-left last:border-b-0",LIST_ROW_INTERACTION)}>
      <span className="w-5 shrink-0 text-center text-[14px] font-bold text-muted-foreground">{rank}</span>
      <CoverArt src={cover(song.id)} gradient={song.gradient} className="w-12 h-12 rounded-[14px] shrink-0">
        {isPlaying&&(
          <div className="absolute inset-0 flex items-center justify-center bg-black/35">
            <div className="flex h-4 items-end gap-0.5">
              {[1,2,3,4].map(index=><motion.span key={index} className="w-0.5 rounded-full bg-white"
                style={reduceMotion?{ height:8 }:undefined}
                animate={reduceMotion?undefined:{ height:[5,14,8,12] }}
                transition={{ duration:0.75, repeat:Infinity, delay:index*0.1, ease:"easeInOut" }}/>) }
            </div>
          </div>
        )}
      </CoverArt>
      <div className="flex-1 min-w-0">
        <p className="text-[15px] font-semibold text-foreground truncate">{song.title}</p>
        <p className="text-[13px] text-muted-foreground truncate">{song.artist}</p>
      </div>
      <div className="shrink-0 text-right">
        <p className="text-[11px] font-medium text-muted-foreground">{playedAt}</p>
        {showDetail&&<p className="mt-0.5 text-[12px] font-medium text-foreground/70">{detail??song.duration}</p>}
      </div>
    </motion.button>
  );
}

// ── HeroBanner kept for desktop continue-listening usage (hidden on mobile home) ──
function HeroBanner({ onPlay }: { onPlay:(s:Song)=>void }) {
  const [idx,setIdx] = useState(0);
  const [saved,setSaved] = useState(false);
  const items = PLAYLISTS.slice(0,4);
  useEffect(()=>{const t=setInterval(()=>setIdx(i=>(i+1)%items.length),5000);return()=>clearInterval(t);},[items.length]);
  const item = items[idx];
  return (
    <div className="relative rounded-[16px] overflow-hidden h-[220px] lg:h-[260px] mb-6">
      <AnimatePresence mode="wait">
        <motion.div key={idx} initial={{opacity:0,scale:1.05}} animate={{opacity:1,scale:1}} exit={{opacity:0,scale:0.97}} transition={{duration:0.5}}
          className="absolute inset-0 overflow-hidden" style={{background:`linear-gradient(135deg,${item.gradient[0]},${item.gradient[1]})`}}>
          <img src={cover(item.id)} alt="" className="absolute inset-0 w-full h-full object-cover"/>
          <div className="absolute inset-0" style={{background:`linear-gradient(135deg,${item.gradient[0]}70,${item.gradient[1]}50)`}}/>
        </motion.div>
      </AnimatePresence>
      <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-transparent"/>
      <div className="absolute inset-0 flex flex-col justify-end p-6">
        <span className="text-white/70 text-xs font-bold uppercase tracking-widest mb-1">Featured Playlist</span>
        <h2 className="text-2xl font-bold text-white mb-1">{item.title}</h2>
        <p className="text-sm text-white/70 mb-4">{item.description}</p>
        <div className="flex items-center gap-3">
          <motion.button type="button" whileTap={{scale:0.95}} onPointerDown={preventMouseFocus} onClick={()=>onPlay(SONGS[0])} className="flex items-center gap-2 px-5 py-2.5 bg-white text-gray-900 rounded-full text-sm font-bold hover:bg-white/90 transition-colors duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"><Play className="w-4 h-4 fill-gray-900"/>Play</motion.button>
          <motion.button type="button" aria-pressed={saved} whileTap={{scale:0.95}} onPointerDown={preventMouseFocus} onClick={()=>setSaved(!saved)} className="flex items-center gap-2 px-5 py-2.5 bg-white/20 text-white rounded-full text-sm font-semibold backdrop-blur-sm hover:bg-white/30 transition-colors duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40"><Bookmark className={cn("w-4 h-4",saved&&"fill-white")}/>{saved?"Saved":"Save"}</motion.button>
        </div>
      </div>
      <div className="absolute top-4 right-4 flex gap-1.5">
        {items.map((_,i)=><button key={i} onClick={()=>setIdx(i)} className={cn("rounded-full transition-all",i===idx?"w-5 h-1.5 bg-white":"w-1.5 h-1.5 bg-white/40")}/>)}
      </div>
    </div>
  );
}

function formatListeningMinutes(minutes:number) {
  const hours = Math.floor(minutes/60);
  const remainder = minutes%60;
  return hours>0?`${hours}h ${remainder}m`:`${remainder}m`;
}

function ListeningHeatmap({ compact=false, selected, onSelect }: {
  compact?:boolean; selected?:number; onSelect?:(id:number)=>void;
}) {
  const days = compact?LISTENING_DAYS.slice(-28):LISTENING_DAYS;
  return (
    <div className={cn("grid",compact?"gap-[3px]":"gap-1.5")} style={{
      gridAutoFlow:"column",
      gridTemplateRows:compact?"repeat(7,8px)":"repeat(7,minmax(0,1fr))",
      gridTemplateColumns:compact?"repeat(4,8px)":"repeat(8,minmax(0,1fr))",
    }}>
      {days.map(day=>{
        const style = {
          background:day.minutes>0
            ?`color-mix(in srgb,var(--brand-pink) ${Math.min(88,24+day.minutes*0.72)}%,var(--muted))`
            :"var(--muted)",
        };
        if (compact) return <span key={day.id} aria-hidden="true" className="h-2 w-2 rounded-[3px]" style={style}/>;
        return <button key={day.id} type="button"
          aria-label={`${day.label}, ${day.minutes} minutes listened`}
          onClick={()=>onSelect?.(day.id)}
          className={cn(
            "aspect-square min-w-0 rounded-[5px] outline-none transition-transform hover:scale-105 focus-visible:ring-2 focus-visible:ring-primary/50",
            selected===day.id&&"ring-2 ring-primary ring-offset-2 ring-offset-card",
          )}
          style={style}/>;
      })}
    </div>
  );
}

function ListeningHomePreview({ onPlay, currentSong, isPlaying }: {
  onPlay:(song:Song)=>void; currentSong:Song|null; isPlaying:boolean;
}) {
  const [rankingPage,setRankingPage] = useState(0);
  const rankings = [
    {
      id:"time",
      title:"Top tracks by time",
      items:[...LISTENING_RANKINGS].sort((a,b)=>b.minutes-a.minutes).slice(0,3),
      primary:(item:typeof LISTENING_RANKINGS[number])=>formatListeningMinutes(item.minutes),
      secondary:(item:typeof LISTENING_RANKINGS[number])=>`${item.plays} plays`,
    },
    {
      id:"plays",
      title:"Top tracks by plays",
      items:[...LISTENING_RANKINGS].sort((a,b)=>b.plays-a.plays).slice(0,3),
      primary:(item:typeof LISTENING_RANKINGS[number])=>`${item.plays} plays`,
      secondary:(item:typeof LISTENING_RANKINGS[number])=>formatListeningMinutes(item.minutes),
    },
  ];
  return (
    <div>
      <div className="-mx-4 flex snap-x snap-mandatory overflow-x-auto overscroll-x-contain hide-scrollbar lg:mx-0 lg:grid lg:grid-cols-2 lg:gap-5 lg:overflow-visible"
        onScroll={event=>setRankingPage(Math.round(event.currentTarget.scrollLeft/event.currentTarget.clientWidth))}>
        {rankings.map(ranking=>(
          <section key={ranking.id} aria-label={ranking.title} className="min-w-full snap-start px-4 lg:min-w-0 lg:px-0">
            <div className="mb-2 flex items-center justify-between px-2">
              <p className="text-[12px] font-semibold text-foreground">{ranking.title}</p>
              <p className="text-[10px] text-muted-foreground">This month</p>
            </div>
            <div>
              {ranking.items.map((item,index)=><RecentlyPlayedRow key={item.song.id}
                rank={index+1} song={item.song} playedAt={ranking.primary(item)} detail={ranking.secondary(item)}
                isPlaying={isPlaying&&currentSong?.id===item.song.id} onPlay={onPlay}/>) }
            </div>
          </section>
        ))}
      </div>
      <div className="mt-3 flex items-center justify-center gap-1.5 lg:hidden" aria-label={`Top tracks page ${rankingPage+1} of ${rankings.length}`}>
        {rankings.map((ranking,index)=><span key={ranking.id} aria-hidden="true"
          className={cn("h-1.5 rounded-full transition-all duration-[180ms]",rankingPage===index?"w-5 bg-primary":"w-1.5 bg-muted-foreground/30")}/>) }
      </div>
    </div>
  );
}

function ListeningMetricCard({ icon, label, value, detail }: {
  icon:React.ReactNode; label:string; value:string; detail:string;
}) {
  return (
    <div className="rounded-[20px] border border-border bg-card p-4">
      <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10 text-primary">{icon}</div>
      <p className="text-[21px] font-bold text-foreground">{value}</p>
      <p className="mt-0.5 text-[12px] font-semibold text-foreground/80">{label}</p>
      <p className="mt-1 text-[11px] text-muted-foreground">{detail}</p>
    </div>
  );
}

function ListeningPage({ onBack, onPlay }: { onBack:()=>void; onPlay:(song:Song)=>void }) {
  const [tab,setTab] = useState("overview");
  const [rankingMetric,setRankingMetric] = useState("time");
  const [selectedDay,setSelectedDay] = useState(LISTENING_DAYS.length-1);
  const pageRef = useRef<HTMLDivElement>(null);
  const selected = LISTENING_DAYS[selectedDay];
  const selectedPlays = Math.max(1,Math.round(selected.minutes/18));
  const selectedUniqueTracks = Math.max(1,Math.round(selected.minutes/30));
  const maxRanking = Math.max(...LISTENING_RANKINGS.map(item=>rankingMetric==="time"?item.minutes:item.plays));
  const ranked = [...LISTENING_RANKINGS].sort((a,b)=>(rankingMetric==="time"?b.minutes-a.minutes:b.plays-a.plays));

  useEffect(()=>{ pageRef.current?.closest("main")?.scrollTo({top:0}); },[tab]);

  return (
    <div ref={pageRef} className="mx-auto w-full px-4 pb-10 pt-0 lg:max-w-[1180px] lg:px-8">
      <div className="sticky top-0 z-30 -mx-4 mb-3 flex h-[60px] items-center gap-2 border-b border-border/60 bg-background px-4 lg:hidden">
        <button type="button" onClick={onBack} aria-label="Back to Home"
          className="flex h-10 w-10 items-center justify-center rounded-full text-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40">
          <ArrowLeft className="h-5 w-5"/>
        </button>
        <h1 className="text-[24px] font-bold text-foreground">Listening</h1>
      </div>
      <StickyPageHeader title="Listening" className="-mx-8 mb-3 hidden px-8 py-3 lg:block"/>

      <div className="mb-5 overflow-x-auto hide-scrollbar">
        <PillTabs tabs={[
          {id:"overview",label:"Overview"},
          {id:"calendar",label:"Calendar"},
          {id:"rankings",label:"Rankings"},
        ]} active={tab} onChange={setTab}/>
      </div>

      {tab==="overview"&&(
        <div className="space-y-5">
          <section className="rounded-[24px] border border-primary/20 bg-card p-5 lg:p-7" aria-labelledby="monthly-report-title">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-primary">July 2026</p>
                <h2 id="monthly-report-title" className="mt-1 text-[26px] font-bold text-foreground">Your month in music</h2>
                <p className="mt-1 text-[13px] text-muted-foreground">You listened on 12 days, with evenings leading the way.</p>
              </div>
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/12 text-primary"><BarChart2 className="h-5 w-5"/></span>
            </div>
            <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
              {[
                ["18h 42m","Listening time"],["126","Total plays"],["12 days","Active days"],["42","Unique songs"],
              ].map(([value,label])=><div key={label} className="rounded-2xl bg-muted/65 px-4 py-3"><p className="text-[20px] font-bold text-foreground">{value}</p><p className="text-[11px] text-muted-foreground">{label}</p></div>)}
            </div>
          </section>

          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <ListeningMetricCard icon={<Clock className="h-4 w-4"/>} value="7:40 PM" label="Peak time" detail="Evening listener"/>
            <ListeningMetricCard icon={<Flame className="h-4 w-4"/>} value="6 days" label="Longest streak" detail="Jul 8 – Jul 13"/>
            <ListeningMetricCard icon={<Timer className="h-4 w-4"/>} value="1h 34m" label="Active-day average" detail="18% above June"/>
            <ListeningMetricCard icon={<TrendingUp className="h-4 w-4"/>} value="+21%" label="Monthly change" detail="3h 12m more"/>
          </div>

          <div className="grid gap-5 lg:grid-cols-[1.05fr_.95fr]">
            <section className="rounded-[24px] border border-border bg-card p-5" aria-labelledby="favorite-title">
              <SectionHeader title="Most played this month" action="View rankings" onAction={()=>setTab("rankings")}/>
              <div className="space-y-1">
                {LISTENING_RANKINGS.slice(0,3).map((item,index)=><motion.button type="button" key={item.song.id}
                  whileTap={{scale:0.985}} transition={LIST_ROW_TRANSITION}
                  onPointerDown={preventMouseFocus} onClick={()=>onPlay(item.song)}
                  className={cn("flex w-full items-center gap-3 px-2 py-2 text-left",LIST_ROW_INTERACTION)}>
                  <span className="w-5 text-center text-[12px] font-bold text-muted-foreground">{index+1}</span>
                  <CoverArt src={cover(item.song.id)} gradient={item.song.gradient} className="h-11 w-11 shrink-0 rounded-xl"/>
                  <div className="min-w-0 flex-1"><p className="truncate text-[13px] font-semibold text-foreground">{item.song.title}</p><p className="truncate text-[11px] text-muted-foreground">{item.song.artist}</p></div>
                  <p className="text-[11px] font-semibold text-muted-foreground">{item.plays} plays</p>
                </motion.button>)}
              </div>
            </section>

            <section className="rounded-[24px] border border-border bg-card p-5" aria-labelledby="activity-title">
              <div className="mb-4 flex items-start justify-between gap-3">
                <div><h2 id="activity-title" className="text-[20px] font-semibold text-foreground">Listening activity</h2><p className="mt-0.5 text-[11px] text-muted-foreground">Daily time across the past 4 weeks</p></div>
                <button type="button" onClick={()=>setTab("calendar")} className="text-[12px] font-semibold text-primary">Calendar</button>
              </div>
              <ListeningHeatmap compact/>
              <div className="mt-4 flex items-center justify-between text-[10px] text-muted-foreground"><span>4 weeks ago</span><span>Today · 18m</span></div>
            </section>
          </div>
        </div>
      )}

      {tab==="calendar"&&(
        <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
          <section className="rounded-[24px] border border-border bg-card p-5 lg:p-6" aria-labelledby="calendar-title">
            <div className="mb-5 flex items-start justify-between gap-3">
              <div><h2 id="calendar-title" className="text-[20px] font-semibold text-foreground">Past 8 weeks</h2><p className="mt-0.5 text-[12px] text-muted-foreground">Select a day to see its listening total.</p></div>
              <CalendarDays className="h-5 w-5 text-primary"/>
            </div>
            <ListeningHeatmap selected={selectedDay} onSelect={setSelectedDay}/>
            <div className="mt-5 flex items-center justify-between text-[10px] text-muted-foreground"><span>8 weeks ago</span><span>Less <span className="mx-1 text-primary">■ ■ ■</span> More</span><span>Today</span></div>
          </section>
          <aside className="rounded-[24px] border border-border bg-card p-5">
            <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-primary">{selected.label}</p>
            <p className="mt-2 text-[28px] font-bold text-foreground">{formatListeningMinutes(selected.minutes)}</p>
            <p className="text-[12px] text-muted-foreground">Total listening time</p>
            <div className="my-5 h-px bg-border"/>
            {selected.minutes>0?<>
              <p className="text-[12px] font-semibold text-foreground">Top track</p>
              <motion.button type="button" whileTap={{scale:0.985}} transition={LIST_ROW_TRANSITION}
                onPointerDown={preventMouseFocus} onClick={()=>onPlay(SONGS[selected.id%SONGS.length])}
                className={cn("mt-3 flex w-full items-center gap-3 bg-muted/60 p-3 text-left",LIST_ROW_INTERACTION)}>
                <CoverArt src={cover((selected.id%SONGS.length)+1)} gradient={SONGS[selected.id%SONGS.length].gradient} className="h-12 w-12 shrink-0 rounded-[14px]"/>
                <div className="min-w-0"><p className="truncate text-[13px] font-semibold text-foreground">{SONGS[selected.id%SONGS.length].title}</p><p className="truncate text-[11px] text-muted-foreground">{SONGS[selected.id%SONGS.length].artist}</p></div>
              </motion.button>
              <p className="mt-4 text-[11px] text-muted-foreground">{selectedPlays} {selectedPlays===1?"play":"plays"} · {selectedUniqueTracks} unique {selectedUniqueTracks===1?"track":"tracks"}</p>
            </>:<p className="text-[12px] leading-5 text-muted-foreground">No listening recorded on this day.</p>}
          </aside>
        </div>
      )}

      {tab==="rankings"&&(
        <section className="rounded-[24px] border border-border bg-card p-4 lg:p-6" aria-labelledby="rankings-title">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div><h2 id="rankings-title" className="text-[20px] font-semibold text-foreground">Top tracks</h2><p className="mt-0.5 text-[12px] text-muted-foreground">All-time listening statistics</p></div>
            <SegTabs tabs={[{id:"time",label:"Time"},{id:"plays",label:"Plays"}]} active={rankingMetric} onChange={setRankingMetric}/>
          </div>
          <div className="space-y-1">
            {ranked.map((item,index)=>{
              const value = rankingMetric==="time"?item.minutes:item.plays;
              return <motion.button type="button" key={item.song.id}
                whileTap={{scale:0.985}} transition={LIST_ROW_TRANSITION}
                onPointerDown={preventMouseFocus} onClick={()=>onPlay(item.song)}
                className={cn("group flex w-full items-center gap-3 px-2 py-3 text-left",LIST_ROW_INTERACTION)}>
                <span className={cn("w-7 text-center text-[14px] font-bold",index<3?"text-primary":"text-muted-foreground")}>{index+1}</span>
                <CoverArt src={cover(item.song.id)} gradient={item.song.gradient} className="h-12 w-12 shrink-0 rounded-[14px]"/>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-3"><p className="truncate text-[14px] font-semibold text-foreground">{item.song.title}</p><p className="shrink-0 text-[12px] font-semibold text-foreground">{rankingMetric==="time"?formatListeningMinutes(item.minutes):`${item.plays} plays`}</p></div>
                  <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{item.song.artist} · {item.song.album}</p>
                  <div className="mt-2 h-1 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-primary" style={{width:`${Math.round(value/maxRanking*100)}%`}}/></div>
                </div>
                {index===0&&<Trophy className="hidden h-4 w-4 shrink-0 text-primary sm:block"/>}
              </motion.button>;
            })}
          </div>
        </section>
      )}
    </div>
  );
}

function HomePage({ onPlay, currentSong, isPlaying, pinnedPlaylists, onOpenLibrary, onOpenPlaylist, onOpenAlbum, onOpenArtist, onOpenListening }: {
  onPlay:(s:Song)=>void;
  currentSong:Song|null;
  isPlaying:boolean;
  pinnedPlaylists:Playlist[];
  onOpenLibrary:(tab:LibTab)=>void;
  onOpenPlaylist:(playlist:Playlist)=>void;
  onOpenAlbum:(album:Album)=>void;
  onOpenArtist:(artist:Artist)=>void;
  onOpenListening:()=>void;
}) {
  const isDesktop = useIsDesktop();
  const [recentPage,setRecentPage] = useState(0);

  const playedAt = ["12 min ago","28 min ago","1 hr ago","2 hr ago","Yesterday","Yesterday","2 days ago","3 days ago"];
  const recentTracks = SONGS.map((song,index)=>({ rank:index+1, song, playedAt:playedAt[index] }));
  const recentTrackPages = Array.from({ length:Math.ceil(recentTracks.length/3) },(_,pageIndex)=>
    recentTracks.slice(pageIndex*3,pageIndex*3+3)
  );
  const continuePlaylists: Playlist[] = [
    { id:5, title:"Daily Tide", description:"Your calm mix", gradient:G[4], tracks:12, duration:"42m" },
    { id:2, title:"Night Drive", description:"After-dark energy", gradient:G[1], tracks:20, duration:"1h 22m" },
    PLAYLISTS[2],
    PLAYLISTS[1],
    PLAYLISTS[4],
    PLAYLISTS[5],
  ];

  if (!isDesktop) {
    // ── Mobile Home layout ──
    return (
      <div className="pt-3 pb-6">
        <StickyPageHeader title="Home" className="px-5 py-4"/>

        {/* 1. Daily Picks hero */}
        <DailyPicksHero onPlay={onPlay} currentSong={currentSong}/>

        {/* 2. Pinned Playlists */}
        <div className="mt-7">
          <div className="px-4"><HomeSectionHeader title="Pinned Playlists" icon={<Bookmark className="h-4 w-4"/>} onClick={()=>onOpenLibrary("playlists")}/></div>
          <div className="mt-3 flex gap-4 px-4 overflow-x-auto hide-scrollbar pb-1">
            {pinnedPlaylists.map(playlist=>(
              <PlaylistCard key={playlist.id} playlist={playlist}
                onClick={()=>onOpenPlaylist(playlist)}/>
            ))}
          </div>
        </div>

        {/* 3. Your Listening */}
        <div className="mt-7 px-4">
          <HomeSectionHeader title="Your Listening" icon={<Activity className="h-4 w-4"/>} onClick={onOpenListening}/>
          <ListeningHomePreview onPlay={onPlay} currentSong={currentSong} isPlaying={isPlaying}/>
        </div>

        {/* 4. Continue Playing */}
        <div className="mt-7">
          <div className="px-4"><HomeSectionHeader title="Continue Playing" icon={<Headphones className="h-4 w-4"/>} onClick={()=>onOpenLibrary("history")}/></div>
          <div className="mt-3 flex gap-4 px-4 overflow-x-auto hide-scrollbar pb-1">
            {continuePlaylists.map(playlist=>(
              <PlaylistCard key={playlist.title} playlist={playlist} showMeta={false} onClick={()=>onOpenPlaylist(playlist)}/>
            ))}
          </div>
        </div>

        {/* 5. Recently Played */}
        <div className="mt-7">
          <div className="px-4"><HomeSectionHeader title="Recently Played" icon={<Clock className="h-4 w-4"/>} onClick={()=>onOpenLibrary("recently-played")}/></div>
          <div className="mt-1 flex overflow-x-auto hide-scrollbar snap-x snap-mandatory overscroll-x-contain"
            onScroll={event=>setRecentPage(Math.round(event.currentTarget.scrollLeft/event.currentTarget.clientWidth))}>
            {recentTrackPages.map((tracks,pageIndex)=>(
              <div key={pageIndex} className="min-w-full snap-start px-4">
                {tracks.map(({ rank, song, playedAt }) => (
                  <RecentlyPlayedRow key={song.id} rank={rank} song={song} playedAt={playedAt}
                    showDetail={false} isPlaying={isPlaying&&currentSong?.id===song.id} onPlay={onPlay}/>
                ))}
              </div>
            ))}
          </div>
          <div className="mt-3 flex items-center justify-center gap-1.5" aria-label={`Recently Played page ${recentPage+1} of ${recentTrackPages.length}`}>
            {recentTrackPages.map((_,pageIndex)=><span key={pageIndex} aria-hidden="true"
              className={cn("h-1.5 rounded-full transition-all duration-[180ms]",recentPage===pageIndex?"w-5 bg-primary":"w-1.5 bg-muted-foreground/30")}/>) }
          </div>
        </div>

        {/* 6. New Songs */}
        <div className="mt-7">
          <div className="px-4"><HomeSectionHeader title="New Songs" icon={<Sparkles className="h-4 w-4"/>} onClick={()=>onOpenLibrary("recently-added")}/></div>
          <div className="mt-3 flex gap-4 px-4 overflow-x-auto hide-scrollbar pb-1">
            {ALBUMS.slice(2,8).map(album=>(
              <AlbumCard key={album.id} album={album} size="sm" onClick={()=>onPlay(SONGS[album.id-1]||SONGS[0])}/>
            ))}
          </div>
        </div>

        {/* 7. Suggested Albums */}
        <div className="mt-7">
          <div className="px-4"><HomeSectionHeader title="Suggested Albums" icon={<Disc3 className="h-4 w-4"/>} onClick={()=>onOpenLibrary("albums")}/></div>
          <div className="mt-3 flex gap-4 px-4 overflow-x-auto hide-scrollbar pb-1">
            {ALBUMS.slice(0,6).map(album=>(
              <AlbumCard key={album.id} album={album} size="md" action="open" onClick={()=>onOpenAlbum(album)}/>
            ))}
          </div>
        </div>

        {/* 8. Recommended Artists */}
        <div className="mt-7">
          <div className="px-4"><HomeSectionHeader title="Recommended Artists" icon={<Mic2 className="h-4 w-4"/>} onClick={()=>onOpenLibrary("artists")}/></div>
          <div className="mt-3 flex gap-4 px-4 overflow-x-auto hide-scrollbar pb-1">
            {ARTISTS.map(artist=><ArtistCard key={artist.id} artist={artist} showFollowers={false} onClick={()=>onOpenArtist(artist)}/>)}
          </div>
        </div>
      </div>
    );
  }

  // ── Desktop Home layout (original) ──
  return (
    <div className="mx-auto w-full max-w-[1180px] px-8 pt-3 pb-4">
      <StickyPageHeader title="Good Evening" className="-mx-8 px-8 py-3 mb-3"/>
      <HeroBanner onPlay={onPlay}/>
      <div className="mb-6"><HomeSectionHeader title="Pinned Playlists" icon={<Bookmark className="h-4 w-4"/>} onClick={()=>onOpenLibrary("playlists")}/>
        <div className="flex gap-4 overflow-x-auto pb-2 hide-scrollbar">{pinnedPlaylists.map(p=><PlaylistCard key={p.id} playlist={p} onClick={()=>onOpenPlaylist(p)}/>)}</div></div>
      <div className="mb-6">
        <HomeSectionHeader title="Your Listening" icon={<Activity className="h-4 w-4"/>} onClick={onOpenListening}/>
        <ListeningHomePreview onPlay={onPlay} currentSong={currentSong} isPlaying={isPlaying}/>
      </div>
      <div className="mb-6"><HomeSectionHeader title="Continue Playing" icon={<Headphones className="h-4 w-4"/>} onClick={()=>onOpenLibrary("history")}/>
        <div className="flex gap-4 overflow-x-auto pb-2 hide-scrollbar">{ALBUMS.slice(0,6).map(a=><AlbumCard key={a.id} album={a} onClick={()=>onPlay(SONGS[a.id-1]||SONGS[0])}/>)}</div></div>
      <div className="mb-6"><HomeSectionHeader title="Recently Played" icon={<Clock className="h-4 w-4"/>} onClick={()=>onOpenLibrary("recently-played")}/>
        <div className="space-y-1">{SONGS.slice(0,6).map(s=><MusicCard key={s.id} song={s} onPlay={onPlay}
          isPlaying={isPlaying&&currentSong?.id===s.id} highlightPlaying={false} showDuration={false} coverClassName="rounded-[14px]"/>)}</div></div>
      <div className="mb-6"><HomeSectionHeader title="New Songs" icon={<Sparkles className="h-4 w-4"/>} onClick={()=>onOpenLibrary("recently-added")}/>
        <div className="flex gap-4 overflow-x-auto pb-2 hide-scrollbar">{ALBUMS.slice(2,8).map(a=><AlbumCard key={a.id} album={a} size="sm" onClick={()=>onPlay(SONGS[a.id-1]||SONGS[0])}/>)}</div></div>
      <div className="mb-6"><HomeSectionHeader title="Suggested Albums" icon={<Disc3 className="h-4 w-4"/>} onClick={()=>onOpenLibrary("albums")}/>
        <div className="flex gap-4 overflow-x-auto pb-2 hide-scrollbar">{ALBUMS.slice(0,6).map(a=><AlbumCard key={a.id} album={a} size="md" action="open" onClick={()=>onOpenAlbum(a)}/>)}</div></div>
      <div className="mb-6"><HomeSectionHeader title="Recommended Artists" icon={<Mic2 className="h-4 w-4"/>} onClick={()=>onOpenLibrary("artists")}/>
        <div className="flex gap-4 overflow-x-auto pb-2 hide-scrollbar">{ARTISTS.map(a=><ArtistCard key={a.id} artist={a} showFollowers={false} onClick={()=>onOpenArtist(a)}/>)}</div></div>
    </div>
  );
}

function SearchPage({ onPlay }: { onPlay:(s:Song)=>void }) {
  const [q,setQ] = useState("");
  const [filter,setFilter] = useState<"all"|"songs"|"albums"|"artists">("all");
  const [recentSearches,setRecentSearches] = useState(["Luna Waves","Synthwave","Midnight Cascade","Hi-Res","Ambient"]);
  const trending = SONGS.slice(0,6);
  const query = q.trim().toLowerCase();
  const songResults = query ? SONGS.filter(song => {
    const genre = ALBUMS.find(album=>album.title===song.album)?.genre||"";
    return [song.title,song.artist,song.album,song.quality||"",genre].some(value=>value.toLowerCase().includes(query));
  }) : [];
  const albumResults = query ? ALBUMS.filter(album=>[album.title,album.artist,album.genre].some(value=>value.toLowerCase().includes(query))) : [];
  const artistResults = query ? ARTISTS.filter(artist=>[artist.name,artist.genre].some(value=>value.toLowerCase().includes(query))) : [];
  const resultCount = songResults.length+albumResults.length+artistResults.length;
  const filters = [
    {id:"all" as const,label:"All",count:resultCount},
    {id:"songs" as const,label:"Songs",count:songResults.length},
    {id:"albums" as const,label:"Albums",count:albumResults.length},
    {id:"artists" as const,label:"Artists",count:artistResults.length},
  ];
  const rememberSearch = (value:string) => {
    const term = value.trim();
    if (!term) return;
    setRecentSearches(current=>[term,...current.filter(item=>item.toLowerCase()!==term.toLowerCase())].slice(0,6));
  };
  const runSearch = (value:string) => {
    setQ(value);
    setFilter("all");
    rememberSearch(value);
  };
  return (
    <div className="mx-auto w-full max-w-[1180px] px-4 pt-2 pb-4 lg:px-8 lg:pt-3">
      <StickyPageHeader title="Search" className="hidden lg:block -mx-8 px-8 py-3 mb-3"/>
      <div className="relative mb-5">
        <Search aria-hidden="true" className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground"/>
        <input type="text" role="searchbox" aria-label="Search music" placeholder="Search songs, artists, albums, and genres" value={q}
          onChange={event=>setQ(event.target.value)}
          onKeyDown={event=>{
            if (event.key==="Enter") rememberSearch(q);
            if (event.key==="Escape") { setQ(""); setFilter("all"); }
          }}
          className="w-full h-12 pl-11 pr-12 bg-muted rounded-2xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/30 transition-all duration-[180ms]"/>
        {q&&(
          <button type="button" aria-label="Clear search" onClick={()=>{setQ("");setFilter("all");}}
            className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full flex items-center justify-center text-muted-foreground hover:bg-background/60 hover:text-foreground transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
            <X className="w-4 h-4"/>
          </button>
        )}
      </div>
      {!query&&(<>
        {recentSearches.length>0&&(
          <section className="mb-6" aria-labelledby="recent-searches-heading">
            <div className="flex items-center justify-between mb-3">
              <h2 id="recent-searches-heading" className="text-[20px] font-semibold text-foreground">Recent Searches</h2>
              <button type="button" onClick={()=>setRecentSearches([])} className="text-sm font-semibold text-primary hover:text-primary/80 transition-colors">Clear</button>
            </div>
            <div className="flex flex-wrap gap-2">{recentSearches.map(search=>(
              <button type="button" key={search} onClick={()=>runSearch(search)}
                className="flex items-center gap-2 min-h-9 px-3.5 py-2 bg-muted rounded-full text-sm text-muted-foreground hover:text-foreground hover:bg-muted/80 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
                <Clock className="w-3.5 h-3.5"/>{search}
              </button>
            ))}</div>
          </section>
        )}
        <section aria-labelledby="library-trending-heading">
          <div className="mb-3">
            <h2 id="library-trending-heading" className="text-[20px] font-semibold text-foreground">Trending in Your Library</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">Your most-played tracks · Last 7 days</p>
          </div>
          <div className="space-y-1">{trending.map((song,index)=>(
            <motion.button type="button" key={song.id} whileTap={{scale:0.985}} transition={LIST_ROW_TRANSITION}
              onPointerDown={preventMouseFocus} onClick={()=>onPlay(song)}
              aria-label={`${index+1}. ${song.title} by ${song.artist}`}
              className={cn("group flex w-full items-center gap-3 border-b border-border/40 px-2 py-2.5 text-left last:border-0",LIST_ROW_INTERACTION)}>
              <span className="w-5 shrink-0 text-center text-sm font-bold text-muted-foreground">{index+1}</span>
              <CoverArt src={cover(song.id)} gradient={song.gradient} className="h-11 w-11 shrink-0 rounded-[11px]">
                <div className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-hover:bg-black/30 group-hover:opacity-100">
                  <Play className="h-4 w-4 fill-white text-white"/>
                </div>
              </CoverArt>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-semibold text-foreground">{song.title}</p>
                </div>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">{song.artist} · {song.album}</p>
              </div>
            </motion.button>
          ))}</div>
        </section>
      </>)}
      {query&&(
        <section aria-labelledby="search-results-heading">
          <div className="flex items-end justify-between gap-4 mb-3">
            <div>
              <h2 id="search-results-heading" className="text-[20px] font-semibold text-foreground">Search Results</h2>
              <p className="text-xs text-muted-foreground mt-0.5">{resultCount} {resultCount===1?"match":"matches"} for “{q.trim()}”</p>
            </div>
          </div>
          <div className="flex gap-2 overflow-x-auto hide-scrollbar pb-1 mb-5" aria-label="Filter search results">
            {filters.map(item=>(
              <button type="button" key={item.id} onClick={()=>setFilter(item.id)} aria-pressed={filter===item.id}
                className={cn("shrink-0 min-h-9 px-4 rounded-full text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40",
                  filter===item.id?"bg-primary text-primary-foreground":"bg-muted text-muted-foreground hover:text-foreground")}>
                {item.label} <span className={filter===item.id?"text-white/70":"text-muted-foreground/70"}>{item.count}</span>
              </button>
            ))}
          </div>

          {resultCount===0&&(
            <EmptyState icon={<Search className="w-7 h-7"/>} title="No matches yet"
              subtitle={`Try a song, artist, album, or genre instead of “${q.trim()}”.`} action="Clear search" onAction={()=>setQ("")}/>
          )}

          {resultCount>0&&(filter==="all"||filter==="songs")&&songResults.length>0&&(
            <div className="mb-7">
              <div className="flex items-center justify-between mb-2"><h3 className="text-base font-semibold text-foreground">Songs</h3><span className="text-xs text-muted-foreground">{songResults.length}</span></div>
              <div className="space-y-1">{songResults.map(song=><MusicCard key={song.id} song={song} onPlay={onPlay}/>)}</div>
            </div>
          )}

          {resultCount>0&&(
            <div className={cn(filter==="all"&&"lg:grid lg:grid-cols-2 lg:gap-8")}>
              {(filter==="all"||filter==="albums")&&albumResults.length>0&&(
                <div className="mb-7">
                  <div className="flex items-center justify-between mb-3"><h3 className="text-base font-semibold text-foreground">Albums</h3><span className="text-xs text-muted-foreground">{albumResults.length}</span></div>
                  <div className="flex gap-4 overflow-x-auto hide-scrollbar pb-1">{albumResults.map(album=><AlbumCard key={album.id} album={album} size="sm" onClick={()=>onPlay(SONGS.find(song=>song.album===album.title)||SONGS[0])}/>)}</div>
                </div>
              )}

              {(filter==="all"||filter==="artists")&&artistResults.length>0&&(
                <div className="mb-7">
                  <div className="flex items-center justify-between mb-3"><h3 className="text-base font-semibold text-foreground">Artists</h3><span className="text-xs text-muted-foreground">{artistResults.length}</span></div>
                  <div className="flex gap-4 overflow-x-auto hide-scrollbar pb-1">{artistResults.map(artist=><ArtistCard key={artist.id} artist={artist} onClick={()=>onPlay(SONGS.find(song=>song.artist===artist.name)||SONGS[0])}/>)}</div>
                </div>
              )}
            </div>
          )}

          {resultCount>0&&((filter==="songs"&&songResults.length===0)||(filter==="albums"&&albumResults.length===0)||(filter==="artists"&&artistResults.length===0))&&(
            <EmptyState icon={<Filter className="w-7 h-7"/>} title={`No ${filter} found`} subtitle="Choose another result type to keep browsing."/>
          )}
        </section>
      )}
    </div>
  );
}

const PRIMARY_LIB_TABS: {id:LibTab;label:string}[] = [
  {id:"playlists",label:"Playlists"},
  {id:"songs",label:"Songs"},
  {id:"albums",label:"Albums"},
  {id:"artists",label:"Artists"},
];

const LIB_TAB_LABELS: Record<LibTab,string> = {
  songs:"Songs", albums:"Albums", artists:"Artists", genres:"Genres", folders:"Folders",
  playlists:"Playlists", favorites:"Favorites", downloads:"Downloads", history:"History",
  "recently-added":"New Songs", "recently-played":"Recently Played", lossless:"Lossless",
  "hi-res":"Hi-Res",
};

function libraryDuration(songs:Song[]) {
  const totalSeconds = songs.reduce((total,song) => {
    const [minutes,seconds] = song.duration.split(":").map(Number);
    return total + minutes*60 + seconds;
  },0);
  const totalMinutes = Math.ceil(totalSeconds/60);
  return totalMinutes >= 60
    ? `${Math.floor(totalMinutes/60)}h ${totalMinutes%60}m`
    : `${totalMinutes} min`;
}

function LibraryPlaylistRow({ playlist, editing, pinned, onEnterEdit, onDelete, onOpen, onTogglePin }: {
  playlist:Playlist;
  editing:boolean;
  pinned:boolean;
  onEnterEdit:()=>void;
  onDelete:()=>void;
  onOpen:()=>void;
  onTogglePin:()=>void;
}) {
  const dragControls = useDragControls();
  const longPressTimer = useRef<ReturnType<typeof setTimeout>|null>(null);
  const pointerOrigin = useRef<{x:number;y:number}|null>(null);
  const longPressTriggered = useRef(false);

  const cancelLongPress = () => {
    if (longPressTimer.current) clearTimeout(longPressTimer.current);
    longPressTimer.current = null;
    pointerOrigin.current = null;
  };
  const beginLongPress = (event:React.PointerEvent<HTMLButtonElement>) => {
    preventMouseFocus(event);
    if (editing||event.button!==0) return;
    cancelLongPress();
    longPressTriggered.current = false;
    pointerOrigin.current = {x:event.clientX,y:event.clientY};
    longPressTimer.current = setTimeout(() => {
      longPressTriggered.current = true;
      onEnterEdit();
      cancelLongPress();
    },520);
  };
  const trackLongPress = (event:React.PointerEvent<HTMLButtonElement>) => {
    if (!pointerOrigin.current||!longPressTimer.current) return;
    const moved = Math.hypot(event.clientX-pointerOrigin.current.x,event.clientY-pointerOrigin.current.y);
    if (moved>8) cancelLongPress();
  };
  const handleRowClick = () => {
    if (longPressTriggered.current) {
      longPressTriggered.current = false;
      return;
    }
    if (!editing) onOpen();
  };

  useEffect(() => () => cancelLongPress(),[]);

  return (
    <Reorder.Item as="li" value={playlist} dragListener={false} dragControls={dragControls}
      whileDrag={{scale:1.015}} transition={{type:"spring",stiffness:420,damping:34}}
      className={cn("flex items-center gap-2 rounded-sm border-b border-border/40 last:border-0",editing&&"bg-card shadow-sm")}>
      <motion.button type="button" disabled={editing} whileTap={editing?undefined:{scale:0.985}} transition={LIST_ROW_TRANSITION}
        onPointerDown={beginLongPress} onPointerMove={trackLongPress} onPointerUp={cancelLongPress}
        onPointerCancel={cancelLongPress} onPointerLeave={cancelLongPress} onContextMenu={event=>event.preventDefault()}
        onDragStart={event=>event.preventDefault()} onClick={handleRowClick}
        className={cn("flex min-w-0 flex-1 items-center gap-3 px-2 py-2.5 text-left select-none touch-pan-y disabled:cursor-default",editing?"rounded-sm":LIST_ROW_INTERACTION)}>
        <CoverArt src={cover(playlist.id)} gradient={playlist.gradient} className="w-14 h-14 rounded-[12px] shrink-0 shadow-sm"/>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-foreground truncate">{playlist.title}</p>
          <p className="text-xs text-muted-foreground truncate mt-0.5">{playlist.description}</p>
        </div>
        <div className="hidden sm:block shrink-0 text-right">
          <p className="text-xs font-medium text-foreground">{playlist.tracks} tracks</p>
          <p className="text-[11px] text-muted-foreground mt-0.5">{playlist.duration}</p>
        </div>
      </motion.button>
      {!editing&&(
        <button type="button" aria-label={pinned?`Unpin ${playlist.title} from Home`:`Pin ${playlist.title} to Home`}
          aria-pressed={pinned} title={pinned?"Unpin from Home":"Pin to Home"} onPointerDown={preventMouseFocus} onClick={onTogglePin}
          className={cn("mr-2 flex h-10 w-10 shrink-0 items-center justify-center rounded-full outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40",pinned?"text-primary":"text-muted-foreground hover:text-foreground")}>
          <Pin className={cn("h-[18px] w-[18px]",pinned&&"fill-current")}/>
        </button>
      )}
      {editing&&(
        <div className="flex items-center gap-1 pr-2 shrink-0">
          <button type="button" aria-label={`Drag ${playlist.title}`} title="Drag to reorder"
            onPointerDown={event=>dragControls.start(event)}
            className="w-10 h-10 rounded-xl flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-muted touch-none cursor-grab active:cursor-grabbing outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
            <GripVertical className="w-5 h-5"/>
          </button>
          <button type="button" aria-label={`Delete ${playlist.title}`} title="Delete playlist" onClick={onDelete}
            className="w-10 h-10 rounded-xl flex items-center justify-center text-destructive hover:bg-destructive/10 outline-none focus-visible:ring-2 focus-visible:ring-destructive/40">
            <Trash2 className="w-4.5 h-4.5"/>
          </button>
        </div>
      )}
    </Reorder.Item>
  );
}

type SongFilterPanel = "main"|"rating"|"year"|"fileType"|"quality";
type SongFilterState = {
  favorites: boolean;
  rating: "all"|"3"|"4"|"5";
  year: "all"|"2022"|"2023"|"2024";
  fileType: "all"|"flac"|"alac"|"mp3"|"aac";
  quality: "all"|"lossless"|"hi-res"|"dolby"|"standard";
};
const DEFAULT_SONG_FILTERS: SongFilterState = {
  favorites:false,
  rating:"all",
  year:"all",
  fileType:"all",
  quality:"all",
};

function LibraryPage({ onPlay, onOpenPlaylist, onOpenAlbum, onOpenArtist, pinnedPlaylistIds, onTogglePlaylistPin, currentSong, isPlaying, tab, onTab }: {
  onPlay:(s:Song)=>void;
  onOpenPlaylist:(playlist:Playlist)=>void;
  onOpenAlbum:(album:Album)=>void;
  onOpenArtist:(artist:Artist)=>void;
  pinnedPlaylistIds:number[];
  onTogglePlaylistPin:(playlist:Playlist)=>void;
  currentSong:Song|null;
  isPlaying:boolean;
  tab:LibTab;
  onTab:(tab:LibTab)=>void;
}) {
  const [query,setQuery] = useState("");
  const [sortBy,setSortBy] = useState<"title"|"artist"|"album">("title");
  const [editingPlaylists,setEditingPlaylists] = useState(false);
  const [creatingPlaylist,setCreatingPlaylist] = useState(false);
  const [newPlaylistName,setNewPlaylistName] = useState("");
  const [newPlaylistDescription,setNewPlaylistDescription] = useState("");
  const [activeArtistLetter,setActiveArtistLetter] = useState<string|null>(null);
  const [songFilters,setSongFilters] = useState<SongFilterState>(DEFAULT_SONG_FILTERS);
  const [filterMenuPanel,setFilterMenuPanel] = useState<SongFilterPanel>("main");
  const [filterMenuOpen,setFilterMenuOpen] = useState(false);
  const [filterMenuDark,setFilterMenuDark] = useState(false);
  const [filterMenuPosition,setFilterMenuPosition] = useState({top:0,right:16,maxHeight:360});
  const newPlaylistNameRef = useRef<HTMLInputElement>(null);
  const filterTriggerRef = useRef<HTMLButtonElement>(null);
  const filterMenuRef = useRef<HTMLDivElement>(null);
  const nextPlaylistId = useRef(Math.max(FAVORITE_PLAYLIST.id,...PLAYLISTS.map(playlist=>playlist.id))+1);
  const [libraryPlaylists,setLibraryPlaylists] = useState<Playlist[]>(() => [
    FAVORITE_PLAYLIST,
    ...PLAYLISTS,
  ]);
  const genres = ["Electronic","Ambient","Synthwave","Techno","IDM","Post-Rock","Shoegaze","Experimental","Jazz","Classical"];
  const folders = ["/Music/Electronic","/Music/Ambient","/Downloads/Music","/Synced/WebDAV","/SD Card/Music"];
  const artistGroups = [...ARTISTS].sort((a,b)=>a.name.localeCompare(b.name)).reduce<{letter:string;artists:Artist[]}[]>((groups,artist) => {
    const letter = artist.name.charAt(0).toUpperCase();
    const currentGroup = groups[groups.length-1];
    if (currentGroup?.letter===letter) currentGroup.artists.push(artist);
    else groups.push({letter,artists:[artist]});
    return groups;
  },[]);
  const availableArtistLetters = new Set(artistGroups.map(group=>group.letter));
  const selectedArtistLetter = activeArtistLetter??artistGroups[0]?.letter;
  const libraryGroups: {label:string;items:{id:LibTab;label:string;icon:React.ReactNode}[]}[] = [
    {label:"Collection",items:[
      {id:"playlists",label:"Playlists",icon:<ListMusic className="w-4 h-4"/>},
      {id:"songs",label:"Songs",icon:<Music className="w-4 h-4"/>},
      {id:"albums",label:"Albums",icon:<Disc3 className="w-4 h-4"/>},
      {id:"artists",label:"Artists",icon:<Mic2 className="w-4 h-4"/>},
      {id:"genres",label:"Genres",icon:<Hash className="w-4 h-4"/>},
    ]},
  ];

  useEffect(() => {
    setQuery("");
    setEditingPlaylists(false);
    setCreatingPlaylist(false);
    setNewPlaylistName("");
    setNewPlaylistDescription("");
    setActiveArtistLetter(null);
    setFilterMenuOpen(false);
    setFilterMenuPanel("main");
  },[tab]);

  useEffect(() => {
    if (creatingPlaylist) newPlaylistNameRef.current?.focus();
  },[creatingPlaylist]);

  useEffect(() => {
    if (!filterMenuOpen) return;
    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event:KeyboardEvent) => {
      if (event.key==="Escape") closeFilterMenu();
    };
    const handleResize = () => closeFilterMenu(false);
    document.body.style.overflow = "hidden";
    const focusFrame = window.requestAnimationFrame(()=>filterMenuRef.current?.focus());
    window.addEventListener("keydown",handleKeyDown);
    window.addEventListener("resize",handleResize);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
      window.removeEventListener("resize",handleResize);
    };
  },[filterMenuOpen]);

  const songTabs: LibTab[] = ["songs","favorites","history","recently-added","recently-played","lossless","hi-res"];
  const supportsSongTools = songTabs.includes(tab);
  const tabSongs = tab==="favorites" ? SONGS.filter(song=>song.liked)
    : tab==="history" ? [...SONGS].reverse()
    : tab==="recently-added"||tab==="recently-played" ? SONGS.slice(0,6)
    : tab==="lossless" ? SONGS.filter(song=>song.quality==="lossless")
    : tab==="hi-res" ? SONGS.filter(song=>song.quality==="hi-res")
    : tab==="songs" ? SONGS
    : [];
  const normalizedQuery = query.trim().toLowerCase();
  const activeSongFilterCount = Number(songFilters.favorites)
    + Number(songFilters.rating!=="all")
    + Number(songFilters.year!=="all")
    + Number(songFilters.fileType!=="all")
    + Number(songFilters.quality!=="all");
  const visibleSongs = [...tabSongs]
    .filter(song=>!normalizedQuery||[song.title,song.artist,song.album].some(value=>value.toLowerCase().includes(normalizedQuery)))
    .filter(song=>tab!=="songs"||(
      (!songFilters.favorites||song.liked)
      && (songFilters.rating==="all"||(song.rating??0)>=Number(songFilters.rating))
      && (songFilters.year==="all"||String(song.year)===songFilters.year)
      && (songFilters.fileType==="all"||song.fileType===songFilters.fileType)
      && (songFilters.quality==="all"||(song.quality??"standard")===songFilters.quality)
    ))
    .sort((a,b)=>a[sortBy].localeCompare(b[sortBy]));
  const title = LIB_TAB_LABELS[tab];
  const meta = supportsSongTools
    ? `${tab==="songs"&&activeSongFilterCount?`${visibleSongs.length} of `:""}${tabSongs.length} ${tabSongs.length===1?"song":"songs"}${tabSongs.length?` · ${libraryDuration(tabSongs)}`:""}`
    : tab==="albums" ? `${ALBUMS.length} albums · ${ALBUMS.reduce((total,album)=>total+album.tracks,0)} tracks`
    : tab==="artists" ? `${ARTISTS.length} artists`
    : tab==="genres" ? `${genres.length} genres`
    : tab==="folders" ? `${folders.length} folders`
    : tab==="playlists" ? editingPlaylists
      ? "Drag to reorder · tap delete to remove"
      : `${libraryPlaylists.length} playlists · Long press to edit`
    : tab==="downloads" ? "Available offline"
    : "";

  const selectTab = (nextTab:LibTab) => {
    onTab(nextTab);
  };
  const filterPanelOptions: Record<Exclude<SongFilterPanel,"main">,{value:string;label:string}[]> = {
    rating:[
      {value:"all",label:"All Ratings"},
      {value:"3",label:"3 Stars & Up"},
      {value:"4",label:"4 Stars & Up"},
      {value:"5",label:"5 Stars"},
    ],
    year:[
      {value:"all",label:"All Years"},
      {value:"2024",label:"2024"},
      {value:"2023",label:"2023"},
      {value:"2022",label:"2022"},
    ],
    fileType:[
      {value:"all",label:"All File Types"},
      {value:"flac",label:"FLAC"},
      {value:"alac",label:"ALAC"},
      {value:"mp3",label:"MP3"},
      {value:"aac",label:"AAC"},
    ],
    quality:[
      {value:"all",label:"All Quality"},
      {value:"lossless",label:"Lossless"},
      {value:"hi-res",label:"Hi-Res"},
      {value:"dolby",label:"Dolby Atmos"},
      {value:"standard",label:"Standard"},
    ],
  };
  const filterPanelTitles: Record<Exclude<SongFilterPanel,"main">,string> = {
    rating:"Rating",
    year:"Year",
    fileType:"File Type",
    quality:"Audio Quality",
  };
  const filterCategoryRows: {panel:Exclude<SongFilterPanel,"main">;label:string;summary:string}[] = [
    {panel:"rating",label:"Rating",summary:songFilters.rating==="all"?"All":`${songFilters.rating}★ & up`},
    {panel:"year",label:"Year",summary:songFilters.year==="all"?"All":songFilters.year},
    {panel:"fileType",label:"File Type",summary:songFilters.fileType==="all"?"All":songFilters.fileType.toUpperCase()},
    {panel:"quality",label:"Audio Quality",summary:songFilters.quality==="all"?"All":songFilters.quality==="hi-res"?"Hi-Res":songFilters.quality==="dolby"?"Dolby Atmos":songFilters.quality[0].toUpperCase()+songFilters.quality.slice(1)},
  ];
  function showFilterMenu() {
    const rect = filterTriggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const viewportPadding = 16;
    const gap = 8;
    const estimatedHeight = Math.min(6*56+16,360,window.innerHeight-viewportPadding*2);
    const below = rect.bottom+gap;
    const placeAbove = rect.top>window.innerHeight/2||below+estimatedHeight>window.innerHeight-viewportPadding;
    const top = placeAbove?Math.max(viewportPadding,rect.top-estimatedHeight-gap):below;
    const right = Math.max(viewportPadding,window.innerWidth-rect.right);
    setFilterMenuPosition({top,right,maxHeight:window.innerHeight-top-viewportPadding});
    setFilterMenuDark(Boolean(filterTriggerRef.current?.closest(".dark")));
    setFilterMenuPanel("main");
    setFilterMenuOpen(true);
  }
  function closeFilterMenu(restoreFocus=true) {
    setFilterMenuOpen(false);
    if (restoreFocus) window.requestAnimationFrame(()=>filterTriggerRef.current?.focus());
  }
  function selectSongFilter(panel:Exclude<SongFilterPanel,"main">,value:string) {
    setSongFilters(filters => {
      if (panel==="rating") return {...filters,rating:value as SongFilterState["rating"]};
      if (panel==="year") return {...filters,year:value as SongFilterState["year"]};
      if (panel==="fileType") return {...filters,fileType:value as SongFilterState["fileType"]};
      return {...filters,quality:value as SongFilterState["quality"]};
    });
    closeFilterMenu();
  }
  const jumpToArtistLetter = (letter:string) => {
    setActiveArtistLetter(letter);
    document.getElementById(`library-artists-${letter}`)?.scrollIntoView({behavior:"smooth",block:"start"});
  };
  const closeCreatePlaylist = () => {
    setCreatingPlaylist(false);
    setNewPlaylistName("");
    setNewPlaylistDescription("");
  };
  const createPlaylist = (event:React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const title = newPlaylistName.trim();
    if (!title) return;

    const id = nextPlaylistId.current++;
    const playlist:Playlist = {
      id,
      title,
      description:newPlaylistDescription.trim()||"No description",
      gradient:G[id%G.length],
      tracks:0,
      duration:"0m",
    };
    setLibraryPlaylists(items => {
      const favoriteIndex = items.findIndex(item=>item.id===8);
      if (favoriteIndex<0) return [playlist,...items];
      return [...items.slice(0,favoriteIndex+1),playlist,...items.slice(favoriteIndex+1)];
    });
    closeCreatePlaylist();
  };
  const playAll = () => visibleSongs.length&&onPlay(visibleSongs[0]);
  const shuffle = () => visibleSongs.length&&onPlay(visibleSongs[Math.min(3,visibleSongs.length-1)]);

  return (
    <div className="lg:flex lg:h-full lg:overflow-hidden">
      <nav aria-label="Library categories" className="hidden lg:block w-[196px] shrink-0 border-r border-border overflow-y-auto py-4 px-2.5">
        {libraryGroups.map(group=>(
          <div key={group.label} className="mb-3.5 last:mb-0">
            <p className="px-2 mb-1 text-[10px] font-bold uppercase tracking-[0.12em] text-muted-foreground/70">{group.label}</p>
            <div className="space-y-0.5">
              {group.items.map(item=>(
                <button type="button" key={item.id} onPointerDown={preventMouseFocus} onClick={()=>selectTab(item.id)}
                  aria-current={tab===item.id?"page":undefined}
                  className={cn("w-full flex items-center gap-2.5 px-3 h-8 rounded-[10px] text-left text-xs font-semibold transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40",
                    tab===item.id?"bg-[var(--surface-selected)] text-primary":"text-muted-foreground hover:bg-[var(--surface-hover)] hover:text-foreground")}>
                  {item.icon}<span className="truncate">{item.label}</span>
                </button>
              ))}
            </div>
          </div>
        ))}
      </nav>

      <section className="flex-1 min-w-0 px-6 pt-2 pb-8 lg:overflow-y-auto lg:px-8 lg:pt-3">
        <div className="mx-auto w-full max-w-[800px] lg:px-8">
          <StickyPageHeader title="Library" className="hidden lg:block -mx-8 px-8 py-3 mb-3"/>

          <div className="lg:hidden mb-5">
            <div className="grid grid-cols-5 gap-1 rounded-2xl border border-border bg-card/70 p-1">
              {PRIMARY_LIB_TABS.map(item=>(
                <button type="button" key={item.id} onPointerDown={preventMouseFocus} onClick={()=>selectTab(item.id)}
                  aria-pressed={tab===item.id}
                  className={cn("h-9 rounded-xl text-[11px] font-semibold transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40",
                    tab===item.id?"bg-primary text-primary-foreground shadow-sm":"text-muted-foreground hover:text-foreground hover:bg-muted/60")}>
                  {item.label}
                </button>
              ))}
              <button type="button" onPointerDown={preventMouseFocus} onClick={()=>selectTab("genres")}
                aria-pressed={tab==="genres"}
                className={cn("h-9 rounded-xl text-[11px] font-semibold transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40",
                  tab==="genres"?"bg-primary text-primary-foreground shadow-sm":"text-muted-foreground hover:text-foreground hover:bg-muted/60")}>
                Genres
              </button>
            </div>
          </div>

          <div className="flex items-end justify-between gap-4 mb-4">
            <div className="min-w-0">
              <h2 className="text-[22px] font-semibold text-foreground leading-[28px]">{title}</h2>
              <p className="text-xs text-muted-foreground mt-1">{meta}</p>
            </div>
            {supportsSongTools&&tabSongs.length>0&&(
              <div className="flex items-center gap-2 shrink-0">
                <Btn variant="tonal" size="sm" onClick={shuffle} icon={<Shuffle className="w-4 h-4"/>} className="hidden sm:inline-flex">Shuffle</Btn>
                <Btn size="sm" onClick={playAll} icon={<Play className="w-4 h-4 fill-current"/>}>Play all</Btn>
              </div>
            )}
            {tab==="playlists"&&(
              <Btn variant={editingPlaylists?"tonal":"filled"} size="sm" className="w-[88px]"
                onClick={()=>editingPlaylists?setEditingPlaylists(false):setCreatingPlaylist(true)}
                icon={editingPlaylists?<Check className="w-4 h-4"/>:<Plus className="w-4 h-4"/>}>
                {editingPlaylists?"Done":"New"}
              </Btn>
            )}
          </div>

          {supportsSongTools&&tabSongs.length>0&&(
            <div className="flex items-center gap-2 mb-4">
              <div className="flex flex-1 min-w-0 items-center gap-2.5 h-10 px-3.5 rounded-2xl bg-card border border-border focus-within:ring-2 focus-within:ring-primary/30">
                <Search className="w-4 h-4 text-muted-foreground shrink-0"/>
                <input value={query} onChange={event=>setQuery(event.target.value)} aria-label={tab==="songs"?"Search songs, artists, or albums":`Search ${title.toLowerCase()}`}
                  placeholder={tab==="songs"?"Search songs, artists, or albums":`Search ${title.toLowerCase()}`} className="w-full min-w-0 bg-transparent border-0 outline-none text-sm text-foreground placeholder:text-muted-foreground"/>
                {query&&<button type="button" onClick={()=>setQuery("")} aria-label="Clear library search" className="text-muted-foreground hover:text-foreground"><X className="w-4 h-4"/></button>}
              </div>
              {tab==="songs" ? (
                <button ref={filterTriggerRef} type="button" onPointerDown={preventMouseFocus} onClick={showFilterMenu}
                  aria-label={`Filter songs${activeSongFilterCount?`, ${activeSongFilterCount} active`:""}`} aria-haspopup="menu" aria-expanded={filterMenuOpen}
                  className={cn("relative flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border bg-card outline-none transition-colors focus-visible:ring-2 focus-visible:ring-primary/30",filterMenuOpen||activeSongFilterCount?"border-primary/40 text-primary":"border-border text-muted-foreground hover:text-foreground")}>
                  <Filter className="h-4 w-4"/>
                  {activeSongFilterCount>0&&<span aria-hidden="true" className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-primary"/>}
                </button>
              ) : <label className="flex items-center gap-2 h-10 px-3 rounded-2xl bg-card border border-border text-muted-foreground shrink-0">
                <SlidersHorizontal className="w-4 h-4"/>
                <select value={sortBy} onChange={event=>setSortBy(event.target.value as "title"|"artist"|"album")}
                  aria-label="Sort library" className="bg-transparent border-0 outline-none text-xs font-semibold text-foreground cursor-pointer max-w-[84px]">
                  <option value="title">Title</option><option value="artist">Artist</option><option value="album">Album</option>
                </select>
              </label>}
            </div>
          )}

          {supportsSongTools&&tabSongs.length>0&&(
            visibleSongs.length>0 ? (
              <div className={cn("overflow-hidden",tab==="songs"&&"-ml-4 lg:ml-0")}>
                {visibleSongs.map((song,index)=>tab==="songs"
                  ? <PlaylistTrackRow key={song.id} song={song} onPlay={onPlay} trackNumber={index+1} active={isPlaying&&currentSong?.id===song.id}/>
                  : <MusicCard key={song.id} song={song} onPlay={onPlay}/>)}
              </div>
            ) : (
              <div className="rounded-[24px] border border-border bg-card">
                <EmptyState icon={<Search className="w-7 h-7"/>}
                  title={query?`No matches for “${query}”`:"No songs match these filters"}
                  subtitle={query?"Try a title, artist, or album name.":"Adjust or clear the active filters to see more songs."}
                  action={query?"Clear search":"Clear filters"}
                  onAction={()=>query?setQuery(""):setSongFilters(DEFAULT_SONG_FILTERS)}/>
              </div>
            )
          )}
          {tab==="albums"&&<div className="grid grid-cols-2 justify-items-center gap-x-4 gap-y-6 sm:grid-cols-3 xl:grid-cols-4">{ALBUMS.map(album=><AlbumCard key={album.id} album={album} action="open" onClick={()=>onOpenAlbum(album)}/>)}</div>}
          {tab==="artists"&&(
            <div className="grid grid-cols-[minmax(0,1fr)_24px] items-start gap-2">
              <div className="min-w-0">
                {artistGroups.map(group=>(
                  <section key={group.letter} id={`library-artists-${group.letter}`} aria-labelledby={`library-artists-${group.letter}-heading`} className="scroll-mt-24 mb-2 last:mb-0">
                    <h3 id={`library-artists-${group.letter}-heading`} className="h-7 px-2 flex items-center text-[11px] font-bold text-muted-foreground">{group.letter}</h3>
                    <div>
                      {group.artists.map(artist=>(
                        <motion.button type="button" key={artist.id} whileTap={{scale:0.99}} transition={LIST_ROW_TRANSITION}
                          onPointerDown={preventMouseFocus} onClick={()=>onOpenArtist(artist)}
                          className={cn("group flex w-full items-center gap-3 border-b border-border/40 px-2 py-2.5 text-left last:border-0",LIST_ROW_INTERACTION)}>
                          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white shadow-sm"
                            style={{background:`linear-gradient(135deg,${artist.gradient[0]},${artist.gradient[1]})`}}>{artist.initials}</span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm font-semibold text-foreground">{artist.name}</span>
                            <span className="mt-0.5 block truncate text-xs text-muted-foreground">{artist.genre}</span>
                          </span>
                          <span className="hidden shrink-0 text-xs font-medium text-muted-foreground sm:block">{artist.followers}</span>
                          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground/70 transition-transform group-hover:translate-x-0.5"/>
                        </motion.button>
                      ))}
                    </div>
                  </section>
                ))}
              </div>
              <nav aria-label="Artist alphabet index" className="sticky top-20 flex flex-col items-center rounded-full bg-card/70 py-1 shadow-sm ring-1 ring-border/60 backdrop-blur-md">
                {"ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("").map(letter=>{
                  const available = availableArtistLetters.has(letter);
                  return <button type="button" key={letter} disabled={!available} onPointerDown={preventMouseFocus} onClick={()=>jumpToArtistLetter(letter)}
                    aria-label={available?`Jump to artists starting with ${letter}`:`No artists starting with ${letter}`}
                    aria-current={selectedArtistLetter===letter?"location":undefined}
                    className={cn("flex h-5 w-6 items-center justify-center rounded-full text-[9px] font-bold outline-none transition-colors focus-visible:ring-2 focus-visible:ring-primary/40",
                      selectedArtistLetter===letter?"bg-primary text-primary-foreground":available?"text-muted-foreground hover:bg-muted hover:text-foreground":"text-muted-foreground/25")}>
                    {letter}
                  </button>;
                })}
              </nav>
            </div>
          )}
          {tab==="genres"&&<div className="grid grid-cols-2 lg:grid-cols-3 gap-3">{genres.map((genre,index)=>(
            <motion.button type="button" key={genre} whileTap={{scale:0.97}} onPointerDown={preventMouseFocus} onClick={()=>onPlay(SONGS[index%SONGS.length])}
              className="h-24 rounded-[20px] flex items-end p-4 overflow-hidden relative text-left outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
              style={{background:`linear-gradient(135deg,${G[index%8][0]},${G[index%8][1]})`}}>
              <span className="font-bold text-white text-sm">{genre}</span>
              <span className="absolute top-3 right-3 bg-white/20 rounded-xl px-2 py-1 text-white text-[10px] font-semibold">{6+(index*7)%19} albums</span>
            </motion.button>
          ))}</div>}
          {tab==="folders"&&<div className="rounded-[24px] border border-border bg-card overflow-hidden divide-y divide-border/50">{folders.map(folder=>(
            <div key={folder} className="flex items-center gap-3 px-4 py-3.5">
              <div className="w-10 h-10 rounded-xl bg-muted flex items-center justify-center"><Folder className="w-5 h-5 text-muted-foreground"/></div>
              <div className="flex-1 min-w-0"><p className="text-sm font-medium text-foreground">{folder.split("/").pop()}</p><p className="text-xs text-muted-foreground truncate">{folder}</p></div>
              <span className="text-[10px] font-semibold text-muted-foreground rounded-lg bg-muted px-2 py-1">Folder</span>
            </div>
          ))}</div>}
          {tab==="playlists"&&(
            libraryPlaylists.length>0 ? (
              <Reorder.Group as="ul" axis="y" values={libraryPlaylists} onReorder={setLibraryPlaylists} className="overflow-hidden">
                {libraryPlaylists.map(playlist=>{
                  const pinned = pinnedPlaylistIds.includes(playlist.id);
                  return <LibraryPlaylistRow key={playlist.id} playlist={playlist} editing={editingPlaylists} pinned={pinned}
                    onEnterEdit={()=>setEditingPlaylists(true)}
                    onDelete={()=>{
                      setLibraryPlaylists(items=>items.filter(item=>item.id!==playlist.id));
                      if (pinned) onTogglePlaylistPin(playlist);
                    }}
                    onOpen={()=>onOpenPlaylist(playlist)} onTogglePin={()=>onTogglePlaylistPin(playlist)}/>;
                }) }
              </Reorder.Group>
            ) : (
              <div className="rounded-[24px] border border-border bg-card">
                <EmptyState icon={<ListMusic className="w-7 h-7"/>} title="No playlists" subtitle="Your playlists will appear here."/>
              </div>
            )
          )}
          {tab==="downloads"&&<div className="rounded-[24px] border border-border bg-card"><EmptyState icon={<Download className="w-7 h-7"/>} title="No downloads yet" subtitle="Keep music available when you are offline." action="Browse songs" onAction={()=>selectTab("songs")}/></div>}
        </div>
      </section>

      <AnimatePresence>
        {creatingPlaylist&&(
          <motion.div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
            initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
            onMouseDown={event=>event.target===event.currentTarget&&closeCreatePlaylist()}
            onKeyDown={event=>event.key==="Escape"&&closeCreatePlaylist()}>
            <motion.div role="dialog" aria-modal="true" aria-labelledby="create-playlist-title"
              initial={{opacity:0,y:14,scale:0.98}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:10,scale:0.98}}
              transition={{type:"spring",stiffness:420,damping:34}}
              className="w-full max-w-[420px] rounded-[28px] border border-border bg-popover p-5 shadow-2xl">
              <form onSubmit={createPlaylist}>
                <div className="mb-5 flex items-start justify-between gap-4">
                  <div>
                    <h3 id="create-playlist-title" className="text-lg font-semibold text-foreground">Create playlist</h3>
                    <p className="mt-1 text-xs text-muted-foreground">Give your new playlist a name to get started.</p>
                  </div>
                  <button type="button" onClick={closeCreatePlaylist} aria-label="Close create playlist"
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
                    <X className="h-4 w-4"/>
                  </button>
                </div>

                <div className="space-y-4">
                  <label className="block">
                    <span className="mb-1.5 block text-xs font-semibold text-foreground">Name</span>
                    <input ref={newPlaylistNameRef} value={newPlaylistName} onChange={event=>setNewPlaylistName(event.target.value)}
                      maxLength={48} required placeholder="My playlist"
                      className="h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary/50 focus:ring-2 focus:ring-primary/20"/>
                  </label>
                  <label className="block">
                    <span className="mb-1.5 block text-xs font-semibold text-foreground">Description <span className="font-normal text-muted-foreground">(optional)</span></span>
                    <input value={newPlaylistDescription} onChange={event=>setNewPlaylistDescription(event.target.value)}
                      maxLength={96} placeholder="What is this playlist for?"
                      className="h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-primary/50 focus:ring-2 focus:ring-primary/20"/>
                  </label>
                </div>

                <div className="mt-6 flex justify-end gap-2">
                  <button type="button" onClick={closeCreatePlaylist}
                    className="h-10 rounded-full px-4 text-sm font-semibold text-foreground transition-colors hover:bg-muted outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
                    Cancel
                  </button>
                  <button type="submit" disabled={!newPlaylistName.trim()}
                    className="h-10 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground transition-all hover:opacity-90 active:scale-95 disabled:cursor-not-allowed disabled:opacity-40 disabled:active:scale-100 outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
                    Create
                  </button>
                </div>
              </form>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
      {filterMenuOpen&&createPortal(
        <AnimatePresence>
          <motion.div className={cn("fixed inset-0 z-[150]",filterMenuDark?"bg-black/45":"bg-black/25")}
            initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
            onClick={()=>closeFilterMenu()}>
            <motion.div ref={filterMenuRef} role="menu" aria-label="Filter songs" tabIndex={-1}
              className={cn("fixed w-max min-w-[260px] max-w-[calc(100vw-32px)] overflow-y-auto rounded-[28px] p-2 shadow-[0_24px_80px_rgba(0,0,0,0.34)] outline-none",filterMenuDark?"bg-[#2b2b2d]":"bg-[#f3f3f5]")}
              style={{top:filterMenuPosition.top,right:filterMenuPosition.right,maxHeight:filterMenuPosition.maxHeight}}
              initial={{opacity:0,scale:0.94,y:-6}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:0.96,y:-4}}
              transition={{type:"spring",stiffness:430,damping:34,mass:0.72}}
              onClick={event=>event.stopPropagation()}>
              {filterMenuPanel==="main" ? (
                <>
                  <button type="button" role="menuitemcheckbox" aria-checked={songFilters.favorites}
                    onClick={()=>setSongFilters(filters=>({...filters,favorites:!filters.favorites}))}
                    className={cn(
                      "flex h-14 w-max min-w-full items-center gap-4 rounded-[20px] px-5 text-left text-[16px] font-medium outline-none transition-colors",
                      filterMenuDark?"hover:bg-white/[0.06] focus-visible:bg-white/[0.08]":"hover:bg-black/[0.045] focus-visible:bg-black/[0.06]",
                      songFilters.favorites?"text-[#4F8DFF]":filterMenuDark?"text-white":"text-[#1f1f21]",
                    )}>
                    <span className="flex-1 whitespace-nowrap">Favorites</span>
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center" aria-hidden="true">
                      {songFilters.favorites&&<Check className="h-6 w-6 stroke-[2.5]"/>}
                    </span>
                  </button>
                  {filterCategoryRows.map(item=>(
                    <button key={item.panel} type="button" role="menuitem" onClick={()=>setFilterMenuPanel(item.panel)}
                      className={cn(
                        "flex h-14 w-max min-w-full items-center gap-3 rounded-[20px] px-5 text-left text-[16px] font-medium outline-none transition-colors",
                        filterMenuDark?"text-white hover:bg-white/[0.06] focus-visible:bg-white/[0.08]":"text-[#1f1f21] hover:bg-black/[0.045] focus-visible:bg-black/[0.06]",
                      )}>
                      <span className="flex-1 whitespace-nowrap">{item.label}</span>
                      <span className={cn("max-w-[112px] truncate text-[13px] font-normal",item.summary==="All"?filterMenuDark?"text-white/45":"text-black/45":"text-[#4F8DFF]")}>{item.summary}</span>
                      <ChevronRight className={cn("h-5 w-5 shrink-0",filterMenuDark?"text-white/45":"text-black/40")} aria-hidden="true"/>
                    </button>
                  ))}
                  <div className={cn("mx-3 border-t",filterMenuDark?"border-white/[0.08]":"border-black/[0.08]")}/>
                  <button type="button" role="menuitem" disabled={!activeSongFilterCount}
                    onClick={()=>{setSongFilters(DEFAULT_SONG_FILTERS);closeFilterMenu();}}
                    className={cn(
                      "flex h-14 w-max min-w-full items-center rounded-[20px] px-5 text-left text-[16px] font-medium text-[#FF5B6E] outline-none transition-colors disabled:cursor-default disabled:opacity-35",
                      filterMenuDark?"hover:bg-white/[0.06] focus-visible:bg-white/[0.08]":"hover:bg-black/[0.045] focus-visible:bg-black/[0.06]",
                    )}>
                    <span className="flex-1 whitespace-nowrap">Clear Filters</span>
                  </button>
                </>
              ) : (
                <>
                  <button type="button" role="menuitem" onClick={()=>setFilterMenuPanel("main")}
                    className={cn(
                      "flex h-14 w-max min-w-full items-center gap-3 rounded-[20px] px-5 text-left text-[16px] font-semibold outline-none transition-colors",
                      filterMenuDark?"text-white hover:bg-white/[0.06] focus-visible:bg-white/[0.08]":"text-[#1f1f21] hover:bg-black/[0.045] focus-visible:bg-black/[0.06]",
                    )}>
                    <ChevronLeft className="h-5 w-5 shrink-0" aria-hidden="true"/>
                    <span className="flex-1 whitespace-nowrap">{filterPanelTitles[filterMenuPanel]}</span>
                  </button>
                  <div className={cn("mx-3 border-t",filterMenuDark?"border-white/[0.08]":"border-black/[0.08]")}/>
                  {filterPanelOptions[filterMenuPanel].map(option=>{
                    const selected = songFilters[filterMenuPanel]===option.value;
                    return (
                      <button key={option.value} type="button" role="menuitemradio" aria-checked={selected}
                        onClick={()=>selectSongFilter(filterMenuPanel,option.value)}
                        className={cn(
                          "flex h-14 w-max min-w-full items-center gap-4 rounded-[20px] px-5 text-left text-[16px] font-medium outline-none transition-colors",
                          filterMenuDark?"hover:bg-white/[0.06] focus-visible:bg-white/[0.08]":"hover:bg-black/[0.045] focus-visible:bg-black/[0.06]",
                          selected?"text-[#4F8DFF]":filterMenuDark?"text-white":"text-[#1f1f21]",
                        )}>
                        <span className="flex-1 whitespace-nowrap">{option.label}</span>
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center" aria-hidden="true">
                          {selected&&<Check className="h-6 w-6 stroke-[2.5]"/>}
                        </span>
                      </button>
                    );
                  })}
                </>
              )}
            </motion.div>
          </motion.div>
        </AnimatePresence>,
        document.body,
      )}
    </div>
  );
}

type SettingsSub = "appearance"|"playback"|"lyrics"|"sources"|"plugins"|"network-cache"|"storage"|"about";
type SettingsGroup = "personalization"|"playback"|"library-data"|"app-info";
type SettingsActionState = "idle"|"confirm"|"busy"|"success"|"error";
type LibraryScanState = "idle"|"scanning"|"complete";
type ThemeMode = "system"|"light"|"dark";
type LyricSourcePriorityItem = {
  id:string;
  label:string;
  category:"Embedded"|"External";
};

const INITIAL_LYRIC_SOURCE_PRIORITY: LyricSourcePriorityItem[] = [
  {id:"embedded-ttml",label:"Embedded TTML",category:"Embedded"},
  {id:"embedded-word-timed",label:"Embedded word-timed",category:"Embedded"},
  {id:"embedded-plain",label:"Embedded LRC / plain",category:"Embedded"},
  {id:"external-ttml",label:"External TTML",category:"External"},
  {id:"external-word-timed",label:"External word-timed",category:"External"},
  {id:"external-plain",label:"External LRC / plain",category:"External"},
];

const SETTINGS_SUB_LABELS: Record<SettingsSub,string> = {
  appearance:"Appearance & language",
  playback:"Playback settings",
  lyrics:"Lyrics settings",
  sources:"Library & sources",
  plugins:"Metadata plugins",
  "network-cache":"Network & cache",
  storage:"Storage & data",
  about:"About",
};

type MetadataPluginConfigDependency = { key:string; value:string };
type MetadataPluginConfigField =
  | {
      type:"markdown";
      key:string;
      title:string;
      content:string;
      dependsOn?:MetadataPluginConfigDependency;
    }
  | {
      type:"select";
      key:string;
      title:string;
      summary?:string;
      options:{ value:string; label:string }[];
      dependsOn?:MetadataPluginConfigDependency;
    }
  | {
      type:"text"|"password";
      key:string;
      title:string;
      summary?:string;
      placeholder?:string;
      dependsOn?:MetadataPluginConfigDependency;
    }
  | {
      type:"switch";
      key:string;
      title:string;
      summary?:string;
      dependsOn?:MetadataPluginConfigDependency;
    };

type MetadataPluginModel = {
  id:string;
  name:string;
  description:string;
  version:string;
  author:string;
  enabled:boolean;
  allowManual:boolean;
  allowAutomatic:boolean;
  allowBatch:boolean;
  capabilities:string[];
  configFields?:MetadataPluginConfigField[];
  configValues?:Record<string,string>;
};

const INITIAL_METADATA_PLUGINS: MetadataPluginModel[] = [
  {
    id:"com.applemusic.source",
    name:"Apple Music",
    description:"Apple Music 搜索源插件",
    version:"0.2.1",
    author:"Replica0110",
    enabled:true,
    allowManual:true,
    allowAutomatic:false,
    allowBatch:false,
    capabilities:["Song search","Lyrics","Artwork"],
    configFields:[
      {
        type:"markdown",
        key:"lyrics_provider_notice",
        title:"歌词源说明",
        content:"### 歌词源说明\n\n- **第三方**：通过 [PaxSenix Apple Music Lyrics](https://lyrics.paxsenix.org/apple-music/lyrics) 获取歌词，不需要 Apple Music 登录态。\n- **官方**：通过 Apple Music 官方接口获取歌词，需要填写网页登录态 Cookie 中的 `media-user-token`。\n\n官方源限制：\n\n- `media-user-token` 所属地区需要与下方选择的地区一致。\n- `media-user-token` 会过期，失效后需要重新获取。\n- 高频请求或批量匹配可能触发 Apple 风控、限流或临时封禁。\n- 只有账号和地区有权限访问的歌曲才可能返回官方歌词。",
      },
      {
        type:"select",
        key:"lyrics_provider",
        title:"歌词源",
        options:[
          {value:"third_party",label:"第三方"},
          {value:"official",label:"官方"},
        ],
      },
      {
        type:"password",
        key:"media_user_token",
        title:"Media User Token",
        placeholder:"粘贴 media-user-token",
        dependsOn:{key:"lyrics_provider",value:"official"},
      },
      {
        type:"select",
        key:"region",
        title:"地区",
        summary:"用于 Apple Music catalog storefront，例如 cn/us/jp。需要和 media-user-token 所属地区匹配",
        options:[
          {value:"cn",label:"中国大陆"},
          {value:"us",label:"美国"},
          {value:"jp",label:"日本"},
          {value:"kr",label:"韩国"},
          {value:"tr",label:"土耳其"},
          {value:"hk",label:"香港"},
          {value:"tw",label:"台湾"},
        ],
      },
      {
        type:"select",
        key:"language",
        title:"语言",
        summary:"用于 Apple Music localization 参数",
        options:[
          {value:"zh-Hans",label:"简体中文"},
          {value:"zh-Hant",label:"繁体中文"},
          {value:"en-US",label:"English"},
          {value:"ja-JP",label:"日本語"},
          {value:"ko-KR",label:"한국어"},
          {value:"tr-TR",label:"Türkçe"},
        ],
      },
      {
        type:"select",
        key:"cover_size",
        title:"封面大小",
        summary:"Apple Music 封面图片尺寸",
        options:[
          {value:"500",label:"500 × 500"},
          {value:"1000",label:"1000 × 1000"},
          {value:"3000",label:"3000 × 3000"},
        ],
      },
    ],
    configValues:{
      lyrics_provider:"third_party",
      media_user_token:"",
      region:"cn",
      language:"zh-Hans",
      cover_size:"3000",
    },
  },
  {
    id:"com.kugou.source",
    name:"酷狗音乐",
    description:"酷狗搜索源插件",
    version:"0.2.0",
    author:"Replica0110",
    enabled:true,
    allowManual:true,
    allowAutomatic:false,
    allowBatch:false,
    capabilities:["Song search","Lyrics","Artwork"],
  },
  {
    id:"com.neteasecloudmusic.source",
    name:"网易云音乐",
    description:"网易云搜索源插件",
    version:"0.2.1",
    author:"Replica0110",
    enabled:true,
    allowManual:true,
    allowAutomatic:false,
    allowBatch:false,
    capabilities:["Song search","Lyrics","Artwork"],
    configFields:[
      {
        type:"select",
        key:"comment_content",
        title:"注释写入内容",
        summary:"控制搜索结果写入注释字段的内容",
        options:[
          {value:"none",label:"不写入"},
          {value:"alias",label:"歌曲别名"},
          {value:"netease_163_key",label:"网易云 163key"},
        ],
      },
    ],
    configValues:{comment_content:"alias"},
  },
  {
    id:"com.qqmusic.source",
    name:"QQ音乐",
    description:"QQ音乐搜索源插件",
    version:"0.2.0",
    author:"Replica0110",
    enabled:true,
    allowManual:true,
    allowAutomatic:false,
    allowBatch:false,
    capabilities:["Song search","Lyrics","Artwork"],
    configFields:[
      {
        type:"select",
        key:"cover_size",
        title:"封面大小",
        summary:"QQ 音乐封面图片尺寸",
        options:[
          {value:"500",label:"500 x 500"},
          {value:"800",label:"800 x 800"},
          {value:"1200",label:"1200 x 1200"},
        ],
      },
      {
        type:"switch",
        key:"replaygain",
        title:"回放增益",
        summary:"搜索歌曲时获取回放增益信息",
      },
    ],
    configValues:{cover_size:"1200",replaygain:"true"},
  },
  {
    id:"com.sodamusic.source",
    name:"汽水音乐",
    description:"汽水音乐搜索源插件",
    version:"0.2.0",
    author:"Replica0110",
    enabled:true,
    allowManual:true,
    allowAutomatic:false,
    allowBatch:false,
    capabilities:["Song search","Lyrics","Artwork"],
  },
];

function FloatingSelectRow({ label, subtitle, value, onChange, options }: {
  label:string;
  subtitle?:string;
  value:string;
  onChange:(value:string)=>void;
  options:{ value:string; label:string }[];
}) {
  const [open,setOpen] = useState(false);
  const [darkMenu,setDarkMenu] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [menuPosition,setMenuPosition] = useState({top:0,right:16,maxHeight:360});
  const selectedLabel = options.find(option=>option.value===value)?.label??value;

  function showOptions() {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const viewportPadding = 16;
    const gap = 8;
    const estimatedHeight = Math.min(options.length*56+16,360,window.innerHeight-viewportPadding*2);
    const below = rect.bottom+gap;
    const placeAbove = rect.top>window.innerHeight/2||below+estimatedHeight>window.innerHeight-viewportPadding;
    const top = placeAbove?Math.max(viewportPadding,rect.top-estimatedHeight-gap):below;
    const right = Math.max(viewportPadding,window.innerWidth-rect.right);
    setMenuPosition({top,right,maxHeight:window.innerHeight-top-viewportPadding});
    setDarkMenu(Boolean(triggerRef.current.closest(".dark")));
    setOpen(true);
  }

  function closeOptions(restoreFocus=true) {
    setOpen(false);
    if (restoreFocus) window.requestAnimationFrame(()=>triggerRef.current?.focus());
  }

  useEffect(()=>{
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event:KeyboardEvent) => {
      if (event.key!=="Escape") return;
      event.stopImmediatePropagation();
      closeOptions();
    };
    const handleResize = () => closeOptions(false);
    document.body.style.overflow = "hidden";
    const focusFrame = window.requestAnimationFrame(()=>menuRef.current?.focus());
    window.addEventListener("keydown",handleKeyDown,true);
    window.addEventListener("resize",handleResize);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown,true);
      window.removeEventListener("resize",handleResize);
    };
  },[open]);

  return (
    <>
      <button ref={triggerRef} type="button" aria-label={`${label}, ${selectedLabel}`} aria-haspopup="listbox" aria-expanded={open}
        onClick={showOptions}
        className="flex min-h-[64px] w-full items-center gap-4 px-4 py-2.5 text-left outline-none transition-colors hover:bg-muted/40 focus-visible:ring-2 focus-visible:ring-primary/40">
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] font-medium leading-tight text-foreground">{label}</span>
          {subtitle&&<span className="mt-1 block text-[12px] leading-[17px] text-muted-foreground">{subtitle}</span>}
        </span>
        <span className={cn("flex max-w-[48%] shrink-0 items-center gap-2 text-[13px]",open?"text-[#4F8DFF]":"text-muted-foreground")}>
          <span className="truncate">{selectedLabel}</span>
          <span className="flex flex-col -space-y-1" aria-hidden="true">
            <ChevronUp className="h-3.5 w-3.5"/>
            <ChevronDown className="h-3.5 w-3.5"/>
          </span>
        </span>
      </button>
      {open&&createPortal(
        <AnimatePresence>
          <motion.div className={cn("fixed inset-0 z-[210]",darkMenu?"bg-black/45":"bg-black/25")}
            initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
            onClick={()=>closeOptions()}>
            <motion.div ref={menuRef} role="listbox" aria-label={label} tabIndex={-1}
              className={cn("fixed w-max min-w-[200px] max-w-[calc(100vw-32px)] overflow-y-auto rounded-[28px] p-2 shadow-[0_24px_80px_rgba(0,0,0,0.34)] outline-none",darkMenu?"bg-[#2b2b2d]":"bg-[#f3f3f5]")}
              style={{top:menuPosition.top,right:menuPosition.right,maxHeight:menuPosition.maxHeight}}
              initial={{opacity:0,scale:0.94,y:-6}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:0.96,y:-4}}
              transition={{type:"spring",stiffness:430,damping:34,mass:0.72}}
              onClick={event=>event.stopPropagation()}>
              {options.map(option=>{
                const selected = option.value===value;
                return (
                  <button key={option.value} type="button" role="option" aria-selected={selected}
                    onClick={()=>{onChange(option.value);closeOptions();}}
                    className={cn(
                      "flex h-14 w-max min-w-full items-center gap-4 rounded-[20px] px-5 text-left text-[16px] font-medium outline-none transition-colors",
                      darkMenu?"hover:bg-white/[0.06] focus-visible:bg-white/[0.08]":"hover:bg-black/[0.045] focus-visible:bg-black/[0.06]",
                      selected?"text-[#4F8DFF]":darkMenu?"text-white":"text-[#1f1f21]",
                    )}>
                    <span className="flex-1 whitespace-nowrap">{option.label}</span>
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center" aria-hidden="true">
                      {selected&&<Check className="h-6 w-6 stroke-[2.5]"/>}
                    </span>
                  </button>
                );
              })}
            </motion.div>
          </motion.div>
        </AnimatePresence>,
        document.body,
      )}
    </>
  );
}

function MetadataPluginDialog({ plugin, isDark, onClose, onChange }: {
  plugin:MetadataPluginModel|null;
  isDark:boolean;
  onClose:()=>void;
  onChange:(plugin:MetadataPluginModel)=>void;
}) {
  const [configValues,setConfigValues] = useState<Record<string,string>>({});
  const [clearingCache,setClearingCache] = useState(false);
  const sheetDragControls = useDragControls();

  useEffect(()=>{
    if (!plugin) return;
    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    setConfigValues(plugin.configValues??{});
    setClearingCache(false);
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown",handleKeyDown);
    };
  },[plugin?.id,onClose]);

  if (!plugin) return null;

  const patchPlugin = (patch:Partial<MetadataPluginModel>) => onChange({...plugin,...patch});
  const configFields = plugin.configFields??[];
  const visibleConfigFields = configFields.filter(field=>
    !field.dependsOn||configValues[field.dependsOn.key]===field.dependsOn.value
  );
  const markdownFields = visibleConfigFields.filter(field=>field.type==="markdown");
  const editableFields = visibleConfigFields.filter(field=>field.type!=="markdown");
  const updateConfigValue = (key:string,value:string) =>
    setConfigValues(current=>({...current,[key]:value}));
  const clearCache = () => {
    setClearingCache(true);
    window.setTimeout(()=>setClearingCache(false),850);
  };

  return createPortal(
    <AnimatePresence>
      <motion.div className={cn("fixed inset-0 z-[180] flex items-end justify-center bg-black/55 backdrop-blur-sm sm:items-center sm:p-4",isDark&&"dark")}
        initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
        onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
        <motion.div role="dialog" aria-modal="true" aria-label={`${plugin.name} configuration`}
          initial={{opacity:0,y:26,scale:0.98}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:18,scale:0.98}}
          transition={{type:"spring",stiffness:420,damping:34}}
          drag="y" dragListener={false} dragControls={sheetDragControls}
          dragConstraints={{top:0,bottom:0}} dragElastic={{top:0,bottom:0.5}}
          onDragEnd={(_,info)=>(info.offset.y>=72||info.velocity.y>=900)&&onClose()}
          className="flex max-h-[92vh] w-full flex-col overflow-hidden rounded-t-[30px] border border-border bg-background shadow-2xl sm:max-w-[520px] sm:rounded-[30px]">
          <div className="touch-none select-none px-5 pt-2.5 sm:hidden"
            onPointerDown={event=>sheetDragControls.start(event)}>
            <div className="mx-auto h-1.5 w-10 rounded-full bg-muted" aria-hidden="true"/>
            <div className="flex min-h-9 items-center justify-center">
              <h2 className="truncate text-[19px] font-semibold text-foreground">{plugin.name}</h2>
            </div>
          </div>
          <div className="min-h-0 overflow-y-auto px-5 pb-6 sm:p-6">
            <div className="hidden min-h-12 items-center gap-3 sm:flex">
              <span className="hidden h-12 w-12 shrink-0 items-center justify-center rounded-[16px] bg-primary/12 text-primary sm:flex">
                <Puzzle className="h-5 w-5"/>
              </span>
              <div className="min-w-0 flex-1 text-left">
                <h2 className="truncate text-[19px] font-semibold text-foreground">{plugin.name}</h2>
              </div>
            </div>

            {markdownFields.map(field=>(
              <section key={field.key} className="mt-5 rounded-[22px] border border-border bg-card p-4 sm:p-5" aria-label={field.title}>
                <ReactMarkdown components={{
                  h3:({children})=><h3 className="text-[15px] font-semibold text-foreground">{children}</h3>,
                  p:({children})=><p className="mt-3 text-[12px] leading-[19px] text-muted-foreground">{children}</p>,
                  ul:({children})=><ul className="mt-2.5 list-disc space-y-1.5 pl-5 text-[12px] leading-[19px] text-muted-foreground">{children}</ul>,
                  li:({children})=><li className="pl-0.5">{children}</li>,
                  strong:({children})=><strong className="font-semibold text-foreground">{children}</strong>,
                  code:({children})=><code className="rounded bg-muted px-1 py-0.5 font-mono text-[11px] text-foreground">{children}</code>,
                  a:({children,href})=><a className="font-medium text-primary underline underline-offset-2 hover:opacity-80" href={href} target="_blank" rel="noreferrer">{children}</a>,
                }}>
                  {field.content}
                </ReactMarkdown>
              </section>
            ))}

          {editableFields.length>0&&(
            <section className="mt-5" aria-labelledby="plugin-configuration-title">
              <p id="plugin-configuration-title" className="mb-2 px-1 text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">Configuration</p>
              <div className="divide-y divide-border/60 overflow-hidden rounded-[22px] border border-border bg-card">
                {editableFields.map(field=>field.type==="select"?(
                  <FloatingSelectRow key={field.key} label={field.title} subtitle={field.summary}
                    value={configValues[field.key]??field.options[0]?.value??""}
                    onChange={value=>updateConfigValue(field.key,value)}
                    options={field.options}/>
                ):field.type==="switch"?(
                  <div key={field.key} className="flex min-h-[64px] items-center gap-4 px-4 py-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="text-[15px] font-medium text-foreground">{field.title}</p>
                      {field.summary&&<p className="mt-1 text-[12px] leading-[17px] text-muted-foreground">{field.summary}</p>}
                    </div>
                    <DesignSwitch ariaLabel={field.title} checked={configValues[field.key]==="true"}
                      onChange={value=>updateConfigValue(field.key,value.toString())}/>
                  </div>
                ):(
                  <label key={field.key} className="block px-4 py-4">
                    <span className="block text-[14px] font-medium text-foreground">{field.title}</span>
                    <input type={field.type==="password"?"password":"text"} value={configValues[field.key]??""}
                      placeholder={field.placeholder} onChange={event=>updateConfigValue(field.key,event.target.value)}
                      className="mt-3 h-11 w-full rounded-[15px] border border-border bg-input-background px-3.5 text-sm text-foreground outline-none placeholder:text-muted-foreground/70 focus:border-primary/50 focus:ring-2 focus:ring-primary/20"/>
                    {field.summary&&<span className="mt-2 block text-[11px] leading-4 text-muted-foreground">{field.summary}</span>}
                  </label>
                ))}
              </div>
            </section>
          )}

          <section className="mt-5" aria-labelledby="plugin-permissions-title">
            <p id="plugin-permissions-title" className="mb-2 px-1 text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">Additional access</p>
            <div className="divide-y divide-border/60 overflow-hidden rounded-[22px] border border-border bg-card">
              {[
                {key:"allowAutomatic" as const,label:"Automatic lookup",summary:"Use during background metadata refresh"},
                {key:"allowBatch" as const,label:"Batch lookup",summary:"Use when updating multiple tracks"},
              ].map(permission=>(
                <div key={permission.key} className="flex min-h-[62px] items-center gap-4 px-4 py-2.5">
                  <div className="min-w-0 flex-1">
                    <p className="text-[14px] font-medium text-foreground">{permission.label}</p>
                    <p className="mt-0.5 text-[11px] leading-4 text-muted-foreground">{permission.summary}</p>
                  </div>
                  <DesignSwitch ariaLabel={`${permission.label} for ${plugin.name}`} checked={plugin[permission.key]}
                    disabled={!plugin.enabled} onChange={value=>patchPlugin({[permission.key]:value})}/>
                </div>
              ))}
            </div>
          </section>

            <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-border/70 pt-5">
              <button type="button" onClick={clearCache} disabled={clearingCache}
                className="inline-flex h-10 items-center gap-2 rounded-full bg-muted px-4 text-[12px] font-semibold text-foreground outline-none hover:bg-muted/80 disabled:opacity-55 focus-visible:ring-2 focus-visible:ring-primary/40">
                <RefreshCw className={cn("h-3.5 w-3.5",clearingCache&&"animate-spin")}/>
                {clearingCache?"Clearing…":"Clear cache"}
              </button>
              <button type="button" onClick={()=>{patchPlugin({configValues});onClose();}}
                className="ml-auto inline-flex h-10 items-center rounded-full bg-primary px-5 text-[12px] font-semibold text-primary-foreground outline-none hover:opacity-90 focus-visible:ring-2 focus-visible:ring-primary/40">
                {editableFields.length?"Save":"Done"}
              </button>
            </div>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>,
    document.body,
  );
}

function MetadataPluginRemovalDialog({ plugin, onClose, onConfirm }: {
  plugin:MetadataPluginModel|null;
  onClose:()=>void;
  onConfirm:()=>void;
}) {
  if (!plugin) return null;
  return createPortal(
    <motion.div className="fixed inset-0 z-[190] flex items-center justify-center bg-black/55 p-5 backdrop-blur-sm"
      initial={{opacity:0}} animate={{opacity:1}} onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div role="alertdialog" aria-modal="true" aria-labelledby="remove-plugin-title" aria-describedby="remove-plugin-description"
        initial={{opacity:0,scale:0.95,y:8}} animate={{opacity:1,scale:1,y:0}}
        transition={{type:"spring",stiffness:430,damping:34}}
        className="w-full max-w-[400px] rounded-[28px] border border-border bg-popover p-5 shadow-2xl">
        <span className="flex h-11 w-11 items-center justify-center rounded-[15px] bg-destructive/10 text-destructive"><Trash2 className="h-5 w-5"/></span>
        <h2 id="remove-plugin-title" className="mt-4 text-[18px] font-semibold text-foreground">Uninstall {plugin.name}?</h2>
        <p id="remove-plugin-description" className="mt-2 text-[12px] leading-[18px] text-muted-foreground">Plugin files, configuration, cache, and private runtime context will be removed from this device.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="h-10 rounded-full px-4 text-[12px] font-semibold text-foreground hover:bg-muted">Cancel</button>
          <button type="button" onClick={onConfirm} className="h-10 rounded-full bg-destructive px-5 text-[12px] font-semibold text-white hover:opacity-90">Uninstall</button>
        </div>
      </motion.div>
    </motion.div>,
    document.body,
  );
}

function LyricSourcePriorityRow({ source, index, onMoveToTop }: {
  source:LyricSourcePriorityItem;
  index:number;
  onMoveToTop:()=>void;
}) {
  const dragControls = useDragControls();
  const isFirst = index===0;

  return (
    <Reorder.Item as="li" value={source} dragListener={false} dragControls={dragControls}
      data-testid={`lyrics-priority-row-${source.id}`}
      whileDrag={{scale:1.012}} transition={{type:"spring",stiffness:430,damping:34}}
      className="relative flex min-h-[66px] items-center gap-3 bg-popover px-4 after:absolute after:inset-x-5 after:bottom-0 after:h-px after:bg-border/60 last:after:hidden sm:min-h-[76px] sm:px-5">
      <span aria-hidden="true" className="flex w-10 shrink-0 items-center justify-center">
        <span className="font-mono text-xs tabular-nums text-muted-foreground">{index+1}</span>
      </span>
      <span className="min-w-0 flex-1 truncate text-[14px] font-medium text-foreground sm:text-[15px]">{source.label}</span>
      {isFirst?(
        <span aria-hidden="true" className="h-10 w-10 shrink-0"/>
      ):(
        <button type="button" onPointerDown={preventMouseFocus} onClick={onMoveToTop}
          data-testid={`lyrics-priority-top-${source.id}`}
          aria-label={`Move ${source.label} to top`} title="Move to top"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-secondary outline-none focus-visible:ring-2 focus-visible:ring-secondary/40">
          <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-secondary/[0.08] transition-colors hover:bg-secondary/[0.15]">
            <VerticalAlignTopRoundedIcon sx={{fontSize:20}} aria-hidden="true"/>
          </span>
        </button>
      )}
      <button type="button" aria-label={`Drag ${source.label} to reorder`} title="Drag to reorder"
        data-testid={`lyrics-priority-drag-${source.id}`}
        onPointerDown={event=>dragControls.start(event)}
        className="flex h-10 w-10 shrink-0 touch-none cursor-grab items-center justify-center rounded-full text-muted-foreground outline-none active:cursor-grabbing focus-visible:ring-2 focus-visible:ring-secondary/40">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-secondary/[0.06] transition-colors hover:bg-secondary/[0.12] hover:text-foreground">
          <DragIndicatorRoundedIcon sx={{fontSize:22}} aria-hidden="true"/>
        </span>
      </button>
    </Reorder.Item>
  );
}

function LyricsPriorityDialog({ open, isDark, sources, onReorder, onMoveToTop, onClose }: {
  open:boolean;
  isDark:boolean;
  sources:LyricSourcePriorityItem[];
  onReorder:(sources:LyricSourcePriorityItem[])=>void;
  onMoveToTop:(id:string)=>void;
  onClose:()=>void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(()=>{
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const focusFrame = window.requestAnimationFrame(()=>dialogRef.current?.focus());
    const handleKeyDown = (event:KeyboardEvent) => event.key==="Escape"&&onClose();
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown",handleKeyDown);
    return ()=>{
      document.body.style.overflow = previousOverflow;
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown",handleKeyDown);
    };
  },[open,onClose]);

  if (!open) return null;

  return createPortal(
    <motion.div className={cn(
      "fixed inset-0 z-[200] flex items-end justify-center bg-black/45 backdrop-blur-sm sm:items-center sm:p-4",
      isDark&&"dark",
    )} initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:0.16}}
      onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
      <motion.div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="lyrics-priority-title"
        data-testid="lyrics-priority-dialog"
        aria-describedby="lyrics-priority-description" tabIndex={-1}
        initial={{opacity:0,y:32,scale:0.99}} animate={{opacity:1,y:0,scale:1}} exit={{opacity:0,y:24,scale:0.99}}
        transition={{type:"spring",stiffness:420,damping:34}}
        className="flex max-h-[calc(100vh-72px)] w-full flex-col overflow-hidden rounded-t-[30px] border border-border bg-popover pb-[max(18px,env(safe-area-inset-bottom))] shadow-2xl outline-none sm:max-w-[560px] sm:rounded-[30px] sm:pb-0">
        <div className="relative shrink-0 px-5 pb-4 pt-3 sm:px-6 sm:pt-5">
          <div className="mx-auto mb-4 h-1.5 w-10 rounded-full bg-muted-foreground/35 sm:hidden" aria-hidden="true"/>
          <h2 id="lyrics-priority-title" className="text-[22px] font-bold tracking-[-0.02em] text-foreground sm:text-[24px]">Set source priority</h2>
          <p id="lyrics-priority-description" className="mt-1 text-[12px] leading-[18px] text-muted-foreground">Drag to reorder, or move any source directly to the top.</p>
          <button type="button" onClick={onClose} aria-label="Close source priority"
            className="absolute right-4 top-4 hidden h-9 w-9 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40 sm:flex">
            <X className="h-4 w-4"/>
          </button>
        </div>
        <Reorder.Group as="ol" axis="y" values={sources} onReorder={onReorder}
          className="min-h-0 overflow-y-auto border-t border-border/60 bg-popover">
          {sources.map((source,index)=>(
            <LyricSourcePriorityRow key={source.id} source={source} index={index}
              onMoveToTop={()=>onMoveToTop(source.id)}/>
          ))}
        </Reorder.Group>
      </motion.div>
    </motion.div>,
    document.body,
  );
}

function SettingsPage({ sub, onSubChange, themeMode, isDark, onThemeModeChange }: {
  sub:SettingsSub|null;
  onSubChange:(sub:SettingsSub|null)=>void;
  themeMode:ThemeMode;
  isDark:boolean;
  onThemeModeChange:(mode:ThemeMode)=>void;
}) {
  const [searchQ, setSearchQ] = useState("");
  const isDesktop = useIsDesktop();
  const settingsRootRef = useRef<HTMLDivElement>(null);
  const settingsDetailRef = useRef<HTMLElement>(null);
  const libraryScanTimerRef = useRef<number|null>(null);

  useEffect(()=>{
    if (isDesktop) settingsDetailRef.current?.scrollTo({top:0});
    else settingsRootRef.current?.closest("main")?.scrollTo({top:0});
  },[sub,isDesktop]);

  useEffect(()=>{
    return ()=>{
      if (libraryScanTimerRef.current!==null) window.clearTimeout(libraryScanTimerRef.current);
    };
  },[]);

  const [artworkColor, setArtworkColor] = useState(true);
  const [manualThemeColor, setManualThemeColor] = useState("#FF5B8A");
  const [customThemeColors, setCustomThemeColors] = useState(["#C55BFF"]);
  const [themeColorPickerOpen, setThemeColorPickerOpen] = useState(false);
  const [appLanguage, setAppLanguage] = useState("system");
  const [audioFocus, setAudioFocus] = useState("pause");
  const [pauseOnDisconnect, setPauseOnDisconnect] = useState(true);
  const [gapless, setGapless] = useState(true);
  const [retryPlayback, setRetryPlayback] = useState(true);
  const [resumeNetwork, setResumeNetwork] = useState(true);
  const [keepScreenOn, setKeepScreenOn] = useState(false);
  const [crossfade, setCrossfade] = useState(0);
  const [replayGain, setReplayGain] = useState("auto");
  const [audioEffects, setAudioEffects] = useState(false);
  const [lyricsAlignment, setLyricsAlignment] = useState("center");
  const [lyricsSource, setLyricsSource] = useState("auto");
  const [lyricsPriorityOpen, setLyricsPriorityOpen] = useState(false);
  const [lyricSourcePriority, setLyricSourcePriority] = useState<LyricSourcePriorityItem[]>(INITIAL_LYRIC_SOURCE_PRIORITY);
  const [ignoreLyricHeaderTags, setIgnoreLyricHeaderTags] = useState(true);
  const [lyricsSize, setLyricsSize] = useState(34);
  const [showTranslation, setShowTranslation] = useState(true);
  const [lyricsBlur, setLyricsBlur] = useState(true);
  const [tapLyricsToSeek, setTapLyricsToSeek] = useState(true);
  const [floatingLyrics, setFloatingLyrics] = useState(false);
  const [autoScan, setAutoScan] = useState("startup");
  const [minimumDuration, setMinimumDuration] = useState("30");
  const [missingFilePolicy, setMissingFilePolicy] = useState("mark");
  const [duplicatePolicy, setDuplicatePolicy] = useState("versions");
  const [libraryScanState, setLibraryScanState] = useState<LibraryScanState>("idle");
  const [pluginImportState, setPluginImportState] = useState<"idle"|"selected"|"installing"|"success">("idle");
  const [metadataPlugins,setMetadataPlugins] = useState<MetadataPluginModel[]>(INITIAL_METADATA_PLUGINS);
  const [editingPluginId,setEditingPluginId] = useState<string|null>(null);
  const [pendingPluginRemovalId,setPendingPluginRemovalId] = useState<string|null>(null);
  const [allowMobileNetwork, setAllowMobileNetwork] = useState(false);
  const [listenAndCache, setListenAndCache] = useState(true);
  const [audioCache, setAudioCache] = useState("512");
  const [imageCache, setImageCache] = useState("256");
  const [timeout, setTimeoutValue] = useState("30");
  const [retryCount, setRetryCount] = useState("2");
  const [backupAppearance, setBackupAppearance] = useState(true);
  const [backupPlayback, setBackupPlayback] = useState(true);
  const [backupLyrics, setBackupLyrics] = useState(true);
  const [backupLibrary, setBackupLibrary] = useState(true);
  const [backupNetwork, setBackupNetwork] = useState(false);
  const [backupSchedule, setBackupSchedule] = useState("weekly");
  const [clearAudioState, setClearAudioState] = useState<SettingsActionState>("idle");
  const [clearImageState, setClearImageState] = useState<SettingsActionState>("idle");
  const [sourcePickerOpen,setSourcePickerOpen] = useState(false);
  const [addSourceOpen,setAddSourceOpen] = useState(false);
  const [editingLocalSourceId,setEditingLocalSourceId] = useState<string|null>(null);
  const [editingSourceId,setEditingSourceId] = useState<string|null>(null);
  const [smbSourceOpen,setSmbSourceOpen] = useState(false);
  const [editingSmbSourceId,setEditingSmbSourceId] = useState<string|null>(null);
  const [sourceMenu,setSourceMenu] = useState<{id:string;anchor:SourceMenuAnchor}|null>(null);
  const [pendingSourceRemovalId,setPendingSourceRemovalId] = useState<string|null>(null);
  const [sources,setSources] = useState<SettingsSourceModel[]>([
    {id:"local-storage",name:"Local music",type:"Local",icon:<HardDrive className="w-5 h-5"/>,enabled:true,location:"1 directory",tracks:1284,lastScan:"Completed · 12 min ago",gradient:G[2],localPath:"~/Music",includeSubdirectories:true,metadataScanMode:"full"},
    {id:"personal-nas",name:"Personal NAS",type:"WebDAV",icon:<Server className="w-5 h-5"/>,enabled:true,location:"/Music",tracks:5820,lastScan:"Completed · 12 min ago",gradient:G[1],address:"https://nas.local/dav",username:"music",anonymous:false,importedDirectories:["/Music"],includeSubdirectories:true,metadataScanMode:"standard"},
    {id:"studio-smb",name:"Studio share",type:"SMB",icon:<Database className="w-5 h-5"/>,enabled:true,location:"//192.168.1.28/Music/Library",tracks:2460,lastScan:"Completed · 18 min ago",gradient:G[4],smbHost:"192.168.1.28",smbPort:445,smbShare:"Music",smbRootPath:"Library",username:"media",smbDomain:"WORKGROUP",smbGuest:false,smbRequireSigning:true,smbRequireEncryption:false,includeSubdirectories:true,metadataScanMode:"standard"},
  ]);
  const sourceTrackCount = sources.reduce((total,source)=>total+source.tracks,0);
  const enabledSourceCount = sources.filter(source=>source.enabled).length;
  const editingLocalSource = sources.find(source=>source.id===editingLocalSourceId&&source.type==="Local")??null;
  const editingSource = sources.find(source=>source.id===editingSourceId&&source.type==="WebDAV")??null;
  const editingSmbSource = sources.find(source=>source.id===editingSmbSourceId&&source.type==="SMB")??null;
  const menuSource = sources.find(source=>source.id===sourceMenu?.id)??null;
  const pendingSourceRemoval = sources.find(source=>source.id===pendingSourceRemovalId)??null;
  const editingPlugin = metadataPlugins.find(plugin=>plugin.id===editingPluginId)??null;
  const pendingPluginRemoval = metadataPlugins.find(plugin=>plugin.id===pendingPluginRemovalId)??null;
  const enabledPluginCount = metadataPlugins.filter(plugin=>plugin.enabled).length;

  function addWebDavSource(source:{name:string;address:string;username:string;anonymous:boolean;includeSubdirectories:boolean}) {
    let location = source.address;
    try {
      const url = new URL(source.address);
      const path = url.pathname.replace(/\/$/,"");
      location = path||url.hostname;
    } catch {}
    setSources(current=>[
      ...current,
      {id:`webdav-${Date.now()}`,name:source.name,type:"WebDAV",icon:<Server className="w-5 h-5"/>,enabled:true,location,tracks:0,lastScan:"Never scanned",gradient:G[current.length%G.length],address:source.address,username:source.username,anonymous:source.anonymous,importedDirectories:["/Music"],includeSubdirectories:source.includeSubdirectories,metadataScanMode:"standard"},
    ]);
  }

  function saveLocalSource(id:string,updates:{name:string;localPath:string;includeSubdirectories:boolean;metadataScanMode:MetadataScanMode}) {
    setSources(current=>current.map(source=>source.id===id?{...source,...updates}:source));
  }

  function saveWebDavSource(id:string,updates:{name:string;address:string;username:string;anonymous:boolean;importedDirectories:string[];includeSubdirectories:boolean;metadataScanMode:MetadataScanMode}) {
    const location = updates.importedDirectories.length>1
      ?`${updates.importedDirectories[0]} +${updates.importedDirectories.length-1}`
      :updates.importedDirectories[0]??"/Music";
    setSources(current=>current.map(source=>source.id===id?{...source,...updates,location}:source));
  }

  function deleteWebDavSource(id:string) {
    setSources(current=>current.filter(source=>source.id!==id));
    setEditingSourceId(null);
  }

  function saveSmbSource(draft:SmbSourceDraft) {
    const normalizedRoot = draft.rootPath.replace(/^\/+|\/+$/g,"");
    const location = `//${draft.host}${draft.port===445?"":`:${draft.port}`}/${draft.share}${normalizedRoot?`/${normalizedRoot}`:""}`;
    if (editingSmbSourceId) {
      setSources(current=>current.map(source=>source.id===editingSmbSourceId?{
        ...source,
        name:draft.name,
        location,
        smbHost:draft.host,
        smbPort:draft.port,
        smbShare:draft.share,
        smbRootPath:normalizedRoot,
        username:draft.username,
        smbDomain:draft.domain,
        smbGuest:draft.guest,
        smbRequireSigning:draft.requireSigning,
        smbRequireEncryption:draft.requireEncryption,
        includeSubdirectories:draft.includeSubdirectories,
        metadataScanMode:draft.metadataScanMode,
      }:source));
      return;
    }
    setSources(current=>[
      ...current,
      {id:`smb-${Date.now()}`,name:draft.name,type:"SMB",icon:<Database className="w-5 h-5"/>,enabled:true,location,tracks:0,lastScan:"Never scanned",gradient:G[current.length%G.length],smbHost:draft.host,smbPort:draft.port,smbShare:draft.share,smbRootPath:normalizedRoot,username:draft.username,smbDomain:draft.domain,smbGuest:draft.guest,smbRequireSigning:draft.requireSigning,smbRequireEncryption:draft.requireEncryption,includeSubdirectories:draft.includeSubdirectories,metadataScanMode:draft.metadataScanMode},
    ]);
  }

  function deleteSmbSource(id:string) {
    setSources(current=>current.filter(source=>source.id!==id));
    setEditingSmbSourceId(null);
  }

  function openSourceEditor(source:SettingsSourceModel) {
    if (source.type==="Local") setEditingLocalSourceId(source.id);
    else if (source.type==="WebDAV") setEditingSourceId(source.id);
    else setEditingSmbSourceId(source.id);
  }

  function requestSourceRemoval(id:string) {
    setSourceMenu(null);
    setPendingSourceRemovalId(id);
  }

  function confirmSourceRemoval() {
    if (!pendingSourceRemovalId) return;
    setSources(current=>current.filter(source=>source.id!==pendingSourceRemovalId));
    setEditingLocalSourceId(current=>current===pendingSourceRemovalId?null:current);
    setEditingSourceId(current=>current===pendingSourceRemovalId?null:current);
    setEditingSmbSourceId(current=>current===pendingSourceRemovalId?null:current);
    setPendingSourceRemovalId(null);
  }

  function openWebDavFromPicker() {
    setSourcePickerOpen(false);
    setAddSourceOpen(true);
  }

  function openSmbFromPicker() {
    setSourcePickerOpen(false);
    setSmbSourceOpen(true);
  }

  function closeSmbSource() {
    setSmbSourceOpen(false);
    setEditingSmbSourceId(null);
  }

  function setSourceEnabled(id:string, enabled:boolean) {
    setSources(current=>current.map(source=>source.id===id?{...source,enabled}:source));
  }

  function scanSource(id:string) {
    setSources(current=>current.map(source=>source.id===id?{...source,lastScan:"Scanning…"}:source));
    window.setTimeout(()=>{
      setSources(current=>current.map(source=>source.id===id?{...source,lastScan:"Completed · just now"}:source));
    },1200);
  }

  function runLibraryScan() {
    if (libraryScanState==="scanning") {
      if (libraryScanTimerRef.current!==null) window.clearTimeout(libraryScanTimerRef.current);
      libraryScanTimerRef.current = null;
      setLibraryScanState("idle");
      return;
    }
    setLibraryScanState("scanning");
    libraryScanTimerRef.current = window.setTimeout(()=>{
      setLibraryScanState("complete");
      libraryScanTimerRef.current = null;
    },1200);
  }

  function updateMetadataPlugin(updated:MetadataPluginModel) {
    setMetadataPlugins(current=>current.map(plugin=>plugin.id===updated.id?updated:plugin));
  }

  function installMetadataPlugin() {
    if (pluginImportState!=="selected") return;
    setPluginImportState("installing");
    window.setTimeout(()=>{
      setMetadataPlugins(current=>current.some(plugin=>plugin.id==="imported-provider")
        ?current
        :[...current,{
          id:"imported-provider",
          name:"Imported Provider",
          description:"Custom metadata search provider",
          version:"1.0.0",
          author:"Local plugin",
          enabled:false,
          allowManual:true,
          allowAutomatic:false,
          allowBatch:false,
          capabilities:["Song search","Lyrics"],
        }]);
      setPluginImportState("success");
      window.setTimeout(()=>setPluginImportState("idle"),1800);
    },900);
  }

  function requestPluginRemoval(plugin:MetadataPluginModel) {
    setEditingPluginId(null);
    setPendingPluginRemovalId(plugin.id);
  }

  function moveLyricSourceToTop(id:string) {
    setLyricSourcePriority(current=>{
      const selected = current.find(source=>source.id===id);
      return selected?[selected,...current.filter(source=>source.id!==id)]:current;
    });
  }

  const closeLyricsPriority = useCallback(()=>setLyricsPriorityOpen(false),[]);

  function confirmPluginRemoval() {
    if (!pendingPluginRemovalId) return;
    setMetadataPlugins(current=>current.filter(plugin=>plugin.id!==pendingPluginRemovalId));
    setPendingPluginRemovalId(null);
  }

  const SUMMARIES: Record<SettingsSub,string> = {
    appearance:"Theme, artwork color, manual theme color, and app language",
    playback:"Audio focus, queue behavior, ReplayGain, and DSP",
    lyrics:"Sources, alignment, type, effects, and external output",
    sources:`${sources.length} ${sources.length===1?"source":"sources"} · ${enabledSourceCount} enabled · ${sourceTrackCount.toLocaleString()} tracks`,
    plugins:`${metadataPlugins.length} installed · ${enabledPluginCount} enabled · Lyrico Plugin API v3`,
    "network-cache":"Streaming policy, cache limits, timeout, and retries",
    storage:"1.8 GB used · cleanup, backup, and diagnostics",
    about:`MelodyTrove ${APP_VERSION} · build, links, privacy, and licenses`,
  };

  const GROUPS: { id:SettingsGroup; label:string; description:string; items:{ id:SettingsSub; icon:React.ReactNode; gradient:[string,string] }[] }[] = [
    { id:"personalization", label:"Personalization", description:"Look, language, and lyrics", items:[
      {id:"appearance",icon:<Palette className="w-[18px] h-[18px]"/>,gradient:G[2]},
      {id:"lyrics",icon:<ListMusic className="w-[18px] h-[18px]"/>,gradient:G[1]},
    ]},
    { id:"playback", label:"Playback", description:"Listening behavior and sound", items:[
      {id:"playback",icon:<CirclePlay className="w-[18px] h-[18px]"/>,gradient:G[0]},
    ]},
    { id:"library-data", label:"Library & data", description:"Sources, plugins, cache, and storage", items:[
      {id:"sources",icon:<Cloud className="w-[18px] h-[18px]"/>,gradient:G[3]},
      {id:"plugins",icon:<Puzzle className="w-[18px] h-[18px]"/>,gradient:["#A4C936","#2EAD72"]},
      {id:"network-cache",icon:<Wifi className="w-[18px] h-[18px]"/>,gradient:["#29C5C8","#117B8A"]},
      {id:"storage",icon:<HardDrive className="w-[18px] h-[18px]"/>,gradient:G[4]},
    ]},
    { id:"app-info", label:"App & info", description:"Version, privacy, and open source", items:[
      {id:"about",icon:<img src={appIconUrl} alt="" className="h-10 w-10 object-cover"/>,gradient:G[0]},
    ]},
  ];

  const ALL_ITEMS = GROUPS.flatMap(group => group.items);
  const SEARCH_TERMS: Record<SettingsSub,string> = {
    appearance:"theme light dark artwork cover manual seed color palette hsv language chinese english system",
    playback:"audio focus pause duck mix gapless retry queue crossfade replaygain equalizer dsp",
    lyrics:"lyrics ttml lrc translation alignment font blur perspective floating bluetooth car",
    sources:"library source local folder webdav smb samba nas share signing encryption scan artwork metadata duplicate",
    plugins:"metadata plugin lyrico v3 zip provider lookup import apple kugou netease qq qishui",
    "network-cache":"network metered streaming cache audio image timeout retry preload",
    storage:"storage usage cleanup backup restore diagnostics reset database downloads",
    about:"about version build commit github repository issue privacy license open source",
  };

  function openSub(id: SettingsSub) {
    onSubChange(id);
    setSearchQ("");
  }

  function SelectRow({ label, subtitle, value, onChange, options }: {
    label:string; subtitle?:string; value:string;
    onChange:(value:string)=>void; options:{v:string;l:string}[];
  }) {
    return (
      <FloatingSelectRow label={label} subtitle={subtitle} value={value} onChange={onChange}
        options={options.map(option=>({value:option.v,label:option.l}))}/>
    );
  }

  function SettingsSourceRow({ source, menuOpen, onMore }: {
    source:SettingsSourceModel;
    menuOpen:boolean;
    onMore:(anchor:SourceMenuAnchor)=>void;
  }) {
    const lastScan = libraryScanState==="complete"?"Completed · just now":source.lastScan;
    const scanMinutes = lastScan.match(/(\d+)\s+min/);
    const compactLastScan = lastScan.includes("just now")?"now":scanMinutes?`${scanMinutes[1]}m`:lastScan==="Never scanned"?"Never":"Done";
    const connectionLabel = source.enabled?(source.type==="Local"?"Available":"Connected"):"Disconnected";
    const indexLabel = (libraryScanState==="scanning"&&source.enabled)||source.lastScan==="Scanning…"
      ?"Scanning"
      :source.tracks>0?"Indexed":"Not indexed";
    return (
      <div className="flex min-h-[88px] w-full items-start gap-3 px-4 py-2.5">
        <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] text-white shadow-sm"
          style={{background:`linear-gradient(135deg,${source.gradient[0]},${source.gradient[1]})`}}>
          {source.icon}
        </span>
        <div className="min-w-0 flex-1">
          <span className="flex min-w-0 flex-wrap items-center gap-1.5">
            <span className="truncate text-[15px] font-semibold text-foreground">{source.name}</span>
            <span className={cn("inline-flex h-5 shrink-0 items-center rounded-full px-2 text-[10px] font-semibold",
              source.enabled?"bg-[#3DCA8A]/12 text-[#3DCA8A]":"bg-muted text-muted-foreground")}>
              {source.enabled?"Enabled":"Paused"}
            </span>
          </span>
          <span className="block text-[12px] text-muted-foreground mt-1 truncate">
            {source.type} · {source.tracks.toLocaleString()} tracks
          </span>
          <span className="mt-1.5 flex min-w-0 items-center gap-1 text-[10px] text-muted-foreground/80" title={lastScan} aria-label={`${connectionLabel}, ${indexLabel}, ${lastScan}`}>
            <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full",source.enabled?"bg-[#3DCA8A]":"bg-muted-foreground/55")}/>
            <span className={cn("shrink-0 font-medium",source.enabled?"text-[#32B97C]":"text-muted-foreground")}>{connectionLabel}</span>
            <span aria-hidden="true">·</span>
            {indexLabel==="Scanning"&&<RefreshCw className="h-2.5 w-2.5 shrink-0 animate-spin text-primary"/>}
            <span className={cn("shrink-0",indexLabel==="Scanning"&&"text-primary")}>{indexLabel}</span>
            {indexLabel!=="Scanning"&&<><span aria-hidden="true">·</span><span className="truncate">{compactLastScan}</span></>}
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-0.5 sm:gap-1">
          <DesignSwitch ariaLabel={`${source.enabled?"Disable":"Enable"} ${source.name}`} checked={source.enabled}
            onChange={enabled=>setSourceEnabled(source.id,enabled)}/>
          <button type="button" aria-label={`More actions for ${source.name}`} aria-haspopup="menu" aria-expanded={menuOpen}
            onClick={event=>{
              const rect = event.currentTarget.getBoundingClientRect();
              onMore({top:rect.top,bottom:rect.bottom,left:rect.left,right:rect.right});
            }}
            className="flex h-10 w-9 shrink-0 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40 sm:w-10">
            <MoreVertical className="h-[18px] w-[18px]"/>
          </button>
        </div>
      </div>
    );
  }

  function SwitchRow({ label, subtitle, checked, onChange }: {
    label:string; subtitle?:string; checked:boolean; onChange:(value:boolean)=>void;
  }) {
    return (
      <div className="flex items-center gap-4 px-4 min-h-[60px] py-2.5">
        <div className="flex-1 min-w-0">
          <p className="text-[15px] font-medium text-foreground leading-tight">{label}</p>
          {subtitle&&<p className="text-[12px] text-muted-foreground mt-1 leading-[17px]">{subtitle}</p>}
        </div>
        <DesignSwitch checked={checked} onChange={onChange}/>
      </div>
    );
  }

  function ValueRow({ label, value, subtitle, onClick, danger=false }: {
    label:string; value?:string; subtitle?:string; onClick?:()=>void; danger?:boolean;
  }) {
    return (
      <button type="button" onClick={onClick} disabled={!onClick}
        className={cn("w-full flex items-center gap-4 px-4 min-h-[60px] py-2.5 text-left outline-none",
          onClick&&"hover:bg-muted/40 focus-visible:ring-2 focus-visible:ring-primary/40 transition-colors") }>
        <div className="flex-1 min-w-0">
          <p className={cn("text-[15px] font-medium leading-tight",danger?"text-destructive":"text-foreground")}>{label}</p>
          {subtitle&&<p className="text-[12px] text-muted-foreground mt-1 leading-[17px]">{subtitle}</p>}
        </div>
        {value&&<span className="text-[12px] text-muted-foreground text-right max-w-[48%] truncate">{value}</span>}
        {onClick&&<ChevronRight className="w-4 h-4 text-muted-foreground/50 shrink-0"/>}
      </button>
    );
  }

  function ActionRow({ label, subtitle, state, actionLabel="Clear", onStateChange }: {
    label:string; subtitle:string; state:SettingsActionState; actionLabel?:string;
    onStateChange:(state:SettingsActionState)=>void;
  }) {
    const confirm = () => {
      onStateChange("busy");
      window.setTimeout(()=>onStateChange("success"),900);
      window.setTimeout(()=>onStateChange("idle"),2600);
    };
    return (
      <div className="px-4 py-3.5 min-h-[64px] flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <p className="text-[15px] font-medium text-foreground">{label}</p>
          <p className="text-[12px] text-muted-foreground mt-1 leading-[17px]">
            {state==="busy"?"Working…":state==="success"?"Done":state==="error"?"Failed — tap to retry":subtitle}
          </p>
          {state==="confirm"&&(
            <div className="flex gap-2 mt-3">
              <motion.button type="button" whileTap={{scale:0.95}} onClick={confirm}
                className="px-3 h-8 rounded-xl text-[12px] font-semibold text-white bg-destructive">Confirm</motion.button>
              <button type="button" onClick={()=>onStateChange("idle")}
                className="px-3 h-8 rounded-xl text-[12px] font-semibold text-muted-foreground bg-muted hover:text-foreground">Cancel</button>
            </div>
          )}
        </div>
        {state==="busy"?<RefreshCw className="w-4 h-4 mt-2 text-muted-foreground animate-spin"/>
        :state==="success"?<CheckCircle2 className="w-4 h-4 mt-2 text-[#3DCA8A]"/>
        :state==="idle"&&<button type="button" onClick={()=>onStateChange("confirm")}
          className="px-3 h-8 rounded-xl text-[12px] font-semibold text-foreground bg-muted hover:bg-muted/80 shrink-0">{actionLabel}</button>}
      </div>
    );
  }

  function NavRow({ id, icon, gradient, selected=false }: { id:SettingsSub; icon:React.ReactNode; gradient:[string,string]; selected?:boolean }) {
    return (
      <button type="button" onClick={()=>!selected&&openSub(id)} aria-current={selected?"page":undefined}
        className={cn("w-full flex items-center gap-3 px-4 min-h-[66px] text-left transition-colors group focus-visible:ring-2 focus-visible:ring-primary/40 outline-none",
          selected?"bg-primary/[0.08]":"hover:bg-muted/40")}>
        <SettingsIconBadge icon={icon} gradient={gradient}/>
        <div className="flex-1 min-w-0 py-3.5">
          <p className={cn("text-[15px] font-semibold leading-tight",selected?"text-primary":"text-foreground")}>{SETTINGS_SUB_LABELS[id]}</p>
          <p className="text-[12px] text-muted-foreground mt-1 truncate">{SUMMARIES[id]}</p>
        </div>
        <ChevronRight className="w-4 h-4 text-muted-foreground/50 shrink-0"/>
      </button>
    );
  }

  const search = searchQ.trim().toLowerCase();
  const searchResults = search
    ? ALL_ITEMS.filter(item=>`${SETTINGS_SUB_LABELS[item.id]} ${SUMMARIES[item.id]} ${SEARCH_TERMS[item.id]}`.toLowerCase().includes(search))
    : [];

  function SearchBox() {
    return (
      <div className="relative mb-6">
        <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none"/>
        <input type="search" aria-label="Search settings" placeholder="Search settings…" value={searchQ} onChange={event=>setSearchQ(event.target.value)}
          className="w-full h-11 pl-10 pr-4 bg-muted rounded-2xl text-[14px] text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/30 transition-all"/>
      </div>
    );
  }

  function HomeContent({ selectedId }: { selectedId?:SettingsSub } = {}) {
    return (
      <div className="pb-8">
        <SearchBox/>
        {search&&(
          <div className="bg-card rounded-[24px] border border-border overflow-hidden divide-y divide-border/50 mb-6">
            {searchResults.length===0
              ?<div className="px-4 py-7 text-center"><p className="text-[14px] font-medium text-foreground">No settings found</p><p className="text-[12px] text-muted-foreground mt-1">Try a feature name such as cache, lyrics, or WebDAV.</p></div>
              :searchResults.map(item=><NavRow key={item.id} id={item.id} icon={item.icon} gradient={item.gradient} selected={selectedId===item.id}/>) }
          </div>
        )}
        {!search&&GROUPS.map(groupItem=>(
          <section key={groupItem.id} className="mb-6">
            <div className="px-1 mb-2">
              <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">{groupItem.label}</p>
            </div>
            <div className="bg-card rounded-[24px] border border-border overflow-hidden divide-y divide-border/50">
              {groupItem.items.map(item=><NavRow key={item.id} id={item.id} icon={item.icon} gradient={item.gradient} selected={selectedId===item.id}/>) }
            </div>
          </section>
        ))}
      </div>
    );
  }

  function DetailContent({ id }: { id:SettingsSub }) {
    if (id==="appearance") return (
      <div className="pb-8">
        <SettingsCard title="Theme">
          <SelectRow label="Theme" subtitle="Choose how MelodyTrove follows system appearance" value={themeMode} onChange={value=>onThemeModeChange(value as ThemeMode)}
            options={[{v:"system",l:"System"},{v:"light",l:"Light"},{v:"dark",l:"Dark"}]}/>
        </SettingsCard>
        <div className="mb-6">
          <p className="mb-2 px-1 text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">Color</p>
          <AppearanceColorSettings
            artworkEnabled={artworkColor}
            artworkState="available"
            manualColor={manualThemeColor}
            onArtworkEnabledChange={setArtworkColor}
            onOpenPicker={()=>setThemeColorPickerOpen(true)}
          />
        </div>
        <SettingsCard title="Language">
          <SelectRow label="App language" subtitle="Some screens may require a restart to refresh" value={appLanguage} onChange={setAppLanguage}
            options={[{v:"system",l:"System"},{v:"zh",l:"中文"},{v:"en",l:"English"}]}/>
        </SettingsCard>
      </div>
    );

    if (id==="playback") return (
      <div className="pb-8">
        <SettingsCard title="Audio focus">
          <SelectRow label="When another app plays audio" value={audioFocus} onChange={setAudioFocus}
            options={[{v:"pause",l:"Pause other audio"},{v:"duck",l:"Lower volume"},{v:"mix",l:"Mix audio"}]}/>
        </SettingsCard>
        <SettingsCard title="Playback behavior">
          <SwitchRow label="Pause on disconnect" subtitle="Pause when the active audio device disconnects" checked={pauseOnDisconnect} onChange={setPauseOnDisconnect}/>
          <SwitchRow label="Gapless playback" subtitle="Remove supported track transition gaps" checked={gapless} onChange={setGapless}/>
          <SwitchRow label="Retry playback failures" subtitle="Retry transient source and player failures" checked={retryPlayback} onChange={setRetryPlayback}/>
          <SwitchRow label="Resume after network recovery" checked={resumeNetwork} onChange={setResumeNetwork}/>
          <SwitchRow label="Keep Now Playing awake" checked={keepScreenOn} onChange={setKeepScreenOn}/>
        </SettingsCard>
        <SettingsCard title="Playback enhancement">
          <div className="px-4 py-4">
            <div className="flex items-center justify-between mb-2"><p className="text-[15px] font-medium text-foreground">Crossfade</p><span className="text-[12px] text-muted-foreground">{Math.round(crossfade*12/100)} s</span></div>
            <DesignSlider value={crossfade} onChange={setCrossfade}/>
          </div>
          <SelectRow label="ReplayGain" value={replayGain} onChange={setReplayGain}
            options={[{v:"off",l:"Off"},{v:"track",l:"Track gain"},{v:"album",l:"Album gain"},{v:"auto",l:"Automatic"}]}/>
        </SettingsCard>
        <SettingsCard title="Equalizer and DSP">
          <SwitchRow label="Enable audio effects" subtitle="Equalizer, bass, treble, compressor, stereo width, and reverb" checked={audioEffects} onChange={setAudioEffects}/>
          {audioEffects&&<ValueRow label="Open sound controls" value="Flat" onClick={()=>{}}/>}
        </SettingsCard>
      </div>
    );

    if (id==="lyrics") return (
      <div className="pb-8">
        <SettingsCard title="Lyrics source">
          <SelectRow label="Source mode"
            subtitle={lyricsSource==="auto"
              ?"Use the first available source in priority order"
              :lyricsSource==="embedded"
                ?"Only use lyrics stored inside the audio file"
                :"Only use sidecar or provider lyrics"}
            value={lyricsSource} onChange={setLyricsSource}
            options={[{v:"auto",l:"Automatic"},{v:"embedded",l:"Embedded only"},{v:"external",l:"External only"}]}/>
          <ValueRow label="Source priority" value={`${lyricSourcePriority[0].label} first`}
            subtitle={`${lyricSourcePriority.length} sources · Drag to reorder or move to top`}
            onClick={()=>setLyricsPriorityOpen(true)}/>
        </SettingsCard>
        <SettingsCard title="Lyric cleanup">
          <SwitchRow label="Ignore lyric header tags" subtitle="Hide artist, album, offset, and provider metadata tags"
            checked={ignoreLyricHeaderTags} onChange={setIgnoreLyricHeaderTags}/>
          <ValueRow label="Lyric line blacklist" value="0 blocked" onClick={()=>{}}/>
        </SettingsCard>
        <SettingsCard title="Lyrics style">
          <SelectRow label="Alignment" value={lyricsAlignment} onChange={setLyricsAlignment}
            options={[{v:"left",l:"Left"},{v:"center",l:"Center"},{v:"right",l:"Right"}]}/>
          <div className="px-4 py-4">
            <div className="flex items-center justify-between mb-2"><p className="text-[15px] font-medium text-foreground">Lyrics font size</p><span className="text-[12px] text-muted-foreground">{lyricsSize} sp</span></div>
            <DesignSlider value={(lyricsSize-24)*100/32} onChange={value=>setLyricsSize(Math.round(24+value*32/100))}/>
          </div>
        </SettingsCard>
        <SettingsCard title="Display & effects">
          <SwitchRow label="Show translation" checked={showTranslation} onChange={setShowTranslation}/>
          <SwitchRow label="Distance blur" subtitle="Blur lyric lines farther from the current line" checked={lyricsBlur} onChange={setLyricsBlur}/>
          <SwitchRow label="Tap lyrics to seek" checked={tapLyricsToSeek} onChange={setTapLyricsToSeek}/>
        </SettingsCard>
        <SettingsCard title="External lyric output">
          <SwitchRow label="Floating lyrics" subtitle="Shown only on supported platforms" checked={floatingLyrics} onChange={setFloatingLyrics}/>
          <ValueRow label="More output targets" value="Bluetooth, car & status bar" onClick={()=>{}}/>
        </SettingsCard>
      </div>
    );

    if (id==="sources") return (
      <div className="pb-8">
        <div className="relative mb-6 overflow-hidden rounded-[28px] border border-primary/15 bg-card p-5">
          <div className="pointer-events-none absolute -right-16 -top-20 h-48 w-48 rounded-full bg-primary/10 blur-3xl"/>
          <div className="relative flex items-start gap-3">
            <SettingsIconBadge icon={<Layers className="h-[18px] w-[18px]"/>} gradient={G[0]}/>
            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-[16px] font-semibold text-foreground">Unified library</p>
                <span className={cn("inline-flex h-6 shrink-0 items-center rounded-full px-2.5 text-[10px] font-semibold",
                  enabledSourceCount===sources.length?"bg-[#3DCA8A]/12 text-[#3DCA8A]":"bg-[#FFD93D]/12 text-[#D7A800]")}>
                  {enabledSourceCount===sources.length?"Up to date":`${sources.length-enabledSourceCount} paused`}
                </span>
              </div>
              <p className="mt-1 text-[12px] leading-[18px] text-muted-foreground">Local and remote music share one searchable index. Pausing a source keeps its files untouched.</p>
            </div>
          </div>
          <div className="relative mt-5 grid grid-cols-3 divide-x divide-border/60 border-t border-border/60 pt-4">
            <div className="pr-3"><p className="text-[18px] font-semibold text-foreground tabular-nums">{sourceTrackCount.toLocaleString()}</p><p className="text-[10px] text-muted-foreground">Tracks indexed</p></div>
            <div className="px-3"><p className="text-[18px] font-semibold text-foreground tabular-nums">{enabledSourceCount}/{sources.length}</p><p className="text-[10px] text-muted-foreground">Sources enabled</p></div>
            <div className="pl-3"><p className="text-[18px] font-semibold text-foreground tabular-nums">{libraryScanState==="complete"?"Now":"12m"}</p><p className="text-[10px] text-muted-foreground">Last scan</p></div>
          </div>
        </div>

        <SettingsCard title="Sources">
          {sources.map(source=><SettingsSourceRow key={source.id} source={source}
            menuOpen={sourceMenu?.id===source.id}
            onMore={anchor=>setSourceMenu(current=>current?.id===source.id?null:{id:source.id,anchor})}/>) }
          <button type="button" onClick={()=>setSourcePickerOpen(true)}
            className="group flex min-h-[76px] w-full items-center gap-3 px-4 py-3 text-left outline-none transition-colors hover:bg-muted/40 focus-visible:ring-2 focus-visible:ring-primary/40">
            <SettingsIconBadge icon={<Plus className="h-[18px] w-[18px]"/>} gradient={G[2]}/>
            <span className="min-w-0 flex-1"><span className="block text-[14px] font-semibold text-foreground">Add source</span><span className="mt-1 block text-[11px] text-muted-foreground">Local, network, cloud, or media server</span></span>
            <span className="hidden rounded-full bg-muted px-2.5 py-1 text-[10px] font-semibold text-muted-foreground sm:inline-flex">7 types</span>
            <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground/45 transition-transform group-hover:translate-x-0.5" aria-hidden="true"/>
          </button>
        </SettingsCard>

        <div className={cn("mb-6 rounded-[24px] border p-4",
          libraryScanState==="scanning"?"border-primary/25 bg-primary/[0.06]":"border-border bg-card")}>
          <div className="flex items-center gap-3">
            <SettingsIconBadge
              icon={libraryScanState==="scanning"
                ?<RefreshCw className="h-[18px] w-[18px] animate-spin"/>
                :<CheckCircle2 className="h-[18px] w-[18px]"/>}
              gradient={libraryScanState==="scanning"?G[0]:G[3]}
            />
            <span className="min-w-0 flex-1">
              <span className="block text-[15px] font-semibold text-foreground">
                {libraryScanState==="scanning"?"Scanning enabled sources":"Library is up to date"}
              </span>
              <span className="mt-1 block text-[11px] leading-4 text-muted-foreground">
                {libraryScanState==="scanning"
                  ?`${Math.round(sourceTrackCount*0.76).toLocaleString()} items checked · keep this screen open to monitor progress`
                  :libraryScanState==="complete"
                    ?`Completed just now · ${sourceTrackCount.toLocaleString()} tracks · no failures`
                    :`Completed 12 minutes ago · ${sourceTrackCount.toLocaleString()} tracks · no failures`}
              </span>
            </span>
            <button type="button" onClick={runLibraryScan}
              className={cn("inline-flex h-10 shrink-0 items-center gap-2 rounded-full px-4 text-[12px] font-semibold outline-none transition-all focus-visible:ring-2 focus-visible:ring-primary/40",
                libraryScanState==="scanning"?"bg-muted text-foreground hover:bg-muted/80":"bg-primary text-primary-foreground hover:opacity-90")}>
              {libraryScanState!=="scanning"&&<RefreshCw className="h-3.5 w-3.5"/>}
              {libraryScanState==="scanning"?"Cancel":"Scan now"}
            </button>
          </div>
          {libraryScanState==="scanning"&&(
            <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-primary/10">
              <motion.div className="h-full rounded-full bg-primary" initial={{width:"12%"}} animate={{width:"76%"}} transition={{duration:1.1,ease:"easeOut"}}/>
            </div>
          )}
        </div>

        <SettingsCard title="Automatic scanning">
          <SelectRow label="Automatic scan" value={autoScan} onChange={setAutoScan}
            options={[{v:"off",l:"Off"},{v:"startup",l:"On startup"},{v:"periodic",l:"Periodic"}]}/>
        </SettingsCard>

        <SettingsCard title="Import rules">
          <SelectRow label="Minimum audio duration" value={minimumDuration} onChange={setMinimumDuration}
            options={[{v:"0",l:"Off"},{v:"10",l:"10 seconds"},{v:"30",l:"30 seconds"},{v:"custom",l:"Custom"}]}/>
          <SelectRow label="Missing files" subtitle="Keep metadata but prevent playback" value={missingFilePolicy} onChange={setMissingFilePolicy}
            options={[{v:"mark",l:"Mark unavailable"},{v:"remove",l:"Remove scan result"}]}/>
          <SelectRow label="Duplicate tracks" subtitle="Group matches as versions without deleting source files" value={duplicatePolicy} onChange={setDuplicatePolicy}
            options={[{v:"versions",l:"Merge as versions"},{v:"separate",l:"Show separately"}]}/>
        </SettingsCard>

        <SettingsCard title="Maintenance">
          <ValueRow label="Complete missing artwork" subtitle="Re-read artwork for WebDAV tracks that do not have it" onClick={()=>{}}/>
          <ValueRow label="Complete missing lyrics" subtitle="Re-read lyrics for WebDAV tracks that do not have them" onClick={()=>{}}/>
          <ValueRow label="Rebuild library" subtitle="Clear regenerated scan data, then scan configured sources again" danger onClick={()=>{}}/>
        </SettingsCard>
      </div>
    );

    if (id==="plugins") return (
      <div className="pb-8">
        <div className="mb-6 rounded-[24px] border border-primary/20 bg-primary/[0.06] p-5">
          <div className="flex items-start gap-4">
            <div className="w-11 h-11 rounded-[15px] bg-primary/15 text-primary flex items-center justify-center shrink-0"><Puzzle className="w-5 h-5"/></div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-[16px] font-semibold text-foreground">Metadata providers</p>
                <span className="inline-flex h-6 items-center rounded-full bg-card/75 px-2.5 text-[10px] font-semibold text-muted-foreground">Lyrico API v3</span>
              </div>
              <p className="mt-1 text-[12px] leading-[18px] text-muted-foreground">Enabled plugins are available for manual lookup. Automatic and batch access can be granted separately.</p>
              <div className="mt-3 flex items-center gap-4 border-t border-primary/10 pt-3 text-[11px] text-muted-foreground">
                <span><strong className="text-[15px] font-semibold text-foreground">{metadataPlugins.length}</strong> installed</span>
                <span><strong className="text-[15px] font-semibold text-[#2EAE75]">{enabledPluginCount}</strong> enabled</span>
              </div>
            </div>
          </div>
        </div>

        <SettingsCard title="Installed plugins">
          {metadataPlugins.length===0?(
            <div className="px-5 py-8 text-center">
              <Package className="mx-auto mb-3 h-7 w-7 text-muted-foreground"/>
              <p className="text-[14px] font-medium text-foreground">No plugins installed</p>
              <p className="mt-1 text-[12px] text-muted-foreground">Import a ZIP that follows Lyrico Plugin API v3.</p>
            </div>
          ):metadataPlugins.map(plugin=>(
            <div key={plugin.id} className={cn("flex min-h-[82px] items-center gap-1.5 px-3 py-3 transition-colors hover:bg-muted/30 sm:gap-2 sm:px-4",
              !plugin.enabled&&"bg-muted/[0.16]")}>
              <div className="min-w-0 flex-1 px-1 py-1">
                <span className="flex min-w-0 items-center gap-2">
                  <span className="truncate text-[17px] font-semibold tracking-[-0.01em] text-foreground">{plugin.name}</span>
                  <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full",plugin.enabled?"bg-[#3DCA8A]":"bg-switch-background")} aria-hidden="true"/>
                </span>
                <span className="mt-1 block line-clamp-2 text-[12px] leading-[17px] text-muted-foreground">
                  {plugin.description} · v{plugin.version}
                </span>
              </div>
              <div className="flex shrink-0 items-center gap-0.5 sm:gap-1">
                {!!plugin.configFields?.length&&(
                  <button type="button" aria-label={`Configure ${plugin.name}`} onClick={()=>setEditingPluginId(plugin.id)}
                    className="flex h-10 w-9 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary/40 sm:w-10">
                    <SlidersHorizontal className="h-[18px] w-[18px]"/>
                  </button>
                )}
                <button type="button" aria-label={`Uninstall ${plugin.name}`} onClick={()=>requestPluginRemoval(plugin)}
                  className="flex h-10 w-9 items-center justify-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-destructive/10 hover:text-destructive focus-visible:ring-2 focus-visible:ring-destructive/35 sm:w-10">
                  <Trash2 className="h-[18px] w-[18px]"/>
                </button>
                <DesignSwitch ariaLabel={`Enable ${plugin.name}`} checked={plugin.enabled}
                  onChange={enabled=>updateMetadataPlugin({...plugin,enabled})}/>
              </div>
            </div>
          ))}
        </SettingsCard>

        <SettingsCard title="Import">
          <div className="flex min-h-[72px] items-center gap-3 px-4 py-3">
            <span className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px]",
              pluginImportState==="success"?"bg-[#3DCA8A]/12 text-[#2EAE75]":"bg-muted text-primary")}>
              {pluginImportState==="success"?<CheckCircle2 className="h-5 w-5"/>:<FolderOpen className="h-5 w-5"/>}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[14px] font-medium text-foreground">
                {pluginImportState==="idle"?"Import local ZIP":pluginImportState==="success"?"Plugin installed":"provider-plugin.zip"}
              </p>
              <p className="mt-1 text-[11px] leading-4 text-muted-foreground">
                {pluginImportState==="idle"?"Archives are validated before an existing version is replaced"
                  :pluginImportState==="selected"?"Ready to validate and install"
                  :pluginImportState==="installing"?"Validating archive and plugin manifest…"
                  :"Imported Provider is disabled until you review it"}
              </p>
            </div>
            {pluginImportState==="idle"&&(
              <Btn variant="tonal" size="sm" onClick={()=>setPluginImportState("selected")}>Choose ZIP</Btn>
            )}
            {pluginImportState==="selected"&&(
              <div className="flex shrink-0 items-center gap-1">
                <button type="button" onClick={()=>setPluginImportState("idle")} className="h-9 rounded-full px-3 text-[11px] font-semibold text-muted-foreground hover:bg-muted">Cancel</button>
                <button type="button" onClick={installMetadataPlugin} className="h-9 rounded-full bg-primary px-4 text-[11px] font-semibold text-primary-foreground hover:opacity-90">Install</button>
              </div>
            )}
            {pluginImportState==="installing"&&<RefreshCw className="h-4 w-4 shrink-0 animate-spin text-primary"/>}
          </div>
        </SettingsCard>
      </div>
    );

    if (id==="network-cache") return (
      <div className="pb-8">
        <SettingsCard title="Network">
          <SwitchRow label="Allow mobile network usage" subtitle="Remote playback and background sync" checked={allowMobileNetwork} onChange={setAllowMobileNetwork}/>
          <SwitchRow label="Resume after network recovery" checked={resumeNetwork} onChange={setResumeNetwork}/>
        </SettingsCard>
        <SettingsCard title="Cache limits">
          <SwitchRow label="Cache while playing" subtitle="Save remote audio for replay and offline listening" checked={listenAndCache} onChange={setListenAndCache}/>
          <SelectRow label="Audio cache" value={audioCache} onChange={setAudioCache}
            options={[{v:"0",l:"Disabled"},{v:"256",l:"256 MB"},{v:"512",l:"512 MB"},{v:"1024",l:"1 GB"}]}/>
          <SelectRow label="Image cache" value={imageCache} onChange={setImageCache}
            options={[{v:"0",l:"Disabled"},{v:"128",l:"128 MB"},{v:"256",l:"256 MB"},{v:"512",l:"512 MB"}]}/>
        </SettingsCard>
        <SettingsCard title="Advanced">
          <SelectRow label="Connection timeout" value={timeout} onChange={setTimeoutValue}
            options={[{v:"10",l:"10 seconds"},{v:"20",l:"20 seconds"},{v:"30",l:"30 seconds"},{v:"60",l:"60 seconds"}]}/>
          <SelectRow label="Network retries" value={retryCount} onChange={setRetryCount}
            options={[{v:"0",l:"No retries"},{v:"1",l:"1 retry"},{v:"2",l:"2 retries"},{v:"3",l:"3 retries"},{v:"5",l:"5 retries"}]}/>
        </SettingsCard>
      </div>
    );

    if (id==="storage") return (
      <div className="pb-8">
        <SettingsCard title="Usage">
          <ValueRow label="Audio cache" value="512 MB"/>
          <ValueRow label="Image cache" value="186 MB"/>
          <ValueRow label="Downloads" value="1.1 GB"/>
          <ValueRow label="Database & logs" value="38 MB"/>
          <ValueRow label="Total" value="1.8 GB"/>
        </SettingsCard>
        <SettingsCard title="Cleanup">
          <ActionRow label="Clear audio cache" subtitle="Downloaded files are kept" state={clearAudioState} onStateChange={setClearAudioState}/>
          <ActionRow label="Clear image cache" subtitle="Artwork and thumbnails will load again when needed" state={clearImageState} onStateChange={setClearImageState}/>
        </SettingsCard>
        <SettingsCard title="Selective backup">
          <SwitchRow label="Appearance settings" checked={backupAppearance} onChange={setBackupAppearance}/>
          <SwitchRow label="Playback and audio settings" checked={backupPlayback} onChange={setBackupPlayback}/>
          <SwitchRow label="Lyrics settings" checked={backupLyrics} onChange={setBackupLyrics}/>
          <SwitchRow label="Library and metadata settings" checked={backupLibrary} onChange={setBackupLibrary}/>
          <SwitchRow label="Network and cache settings" checked={backupNetwork} onChange={setBackupNetwork}/>
          <SelectRow label="Automatic backup schedule" value={backupSchedule} onChange={setBackupSchedule}
            options={[{v:"off",l:"Off"},{v:"daily",l:"Daily"},{v:"weekly",l:"Weekly"}]}/>
          <ValueRow label="Create backup now" value="Local app data" onClick={()=>{}}/>
          <ValueRow label="Restore latest backup" onClick={()=>{}}/>
        </SettingsCard>
        <SettingsCard title="Data & diagnostics">
          <ValueRow label="Export diagnostic log" subtitle="Credentials and tokens are excluded" onClick={()=>{}}/>
          <ValueRow label="Restore default settings" subtitle="Sources and library data are kept" danger onClick={()=>{}}/>
        </SettingsCard>
      </div>
    );

    return (
      <div className="pb-8">
        <div className="rounded-[24px] border border-border bg-card p-6 mb-6 flex items-center gap-4">
          <img src={appIconUrl} alt="" className="h-14 w-14 rounded-[18px] object-cover shadow-lg"/>
          <div><p className="text-[20px] font-bold text-foreground">MelodyTrove</p><p className="text-[13px] text-muted-foreground mt-0.5">One Library. Every Source.</p></div>
        </div>
        <SettingsCard title="App">
          <ValueRow label="Version" value={APP_VERSION}/>
          <ValueRow label="Build" value={`release · ${APP_VERSION_CODE}`}/>
          <ValueRow label="Git commit" value="local build"/>
        </SettingsCard>
        <SettingsCard title="Links">
          <ValueRow label="Open-source licenses" value="View" onClick={()=>{}}/>
          <ValueRow label="Project homepage" value="GitHub" onClick={()=>{}}/>
          <ValueRow label="Report an issue" value="GitHub Issues" onClick={()=>{}}/>
          <ValueRow label="Privacy" subtitle="MelodyTrove is local-first. Diagnostic exports exclude credentials and tokens."/>
        </SettingsCard>
      </div>
    );
  }

  if (isDesktop) {
    const selectedId = sub??"appearance";
    return (
      <>
        <div ref={settingsRootRef} className="flex h-full overflow-hidden">
          <aside aria-label="Settings" className="h-full w-[360px] shrink-0 overflow-y-auto border-r border-border px-4 pt-5">
            <div className="mb-6 min-w-0 px-1">
              <h1 className="truncate text-[28px] font-bold tracking-[-0.02em] text-foreground">Settings</h1>
              <p className="mt-1 truncate text-[12px] text-muted-foreground">MelodyTrove {APP_VERSION}</p>
            </div>
            <HomeContent selectedId={selectedId}/>
          </aside>
          <main ref={settingsDetailRef} className="mx-auto w-full max-w-[800px] flex-1 overflow-y-auto px-8 pt-3 pb-24">
            <StickyPageHeader title={SETTINGS_SUB_LABELS[selectedId]} subtitle={SUMMARIES[selectedId]} className="-mx-8 px-8 mb-4"/>
            <DetailContent id={selectedId}/>
          </main>
        </div>
        <AddSourcePickerDialog open={sourcePickerOpen} onClose={()=>setSourcePickerOpen(false)} onWebDav={openWebDavFromPicker} onSmb={openSmbFromPicker}/>
        <AddWebDavSourceDialog open={addSourceOpen} existingNames={sources.map(source=>source.name)} onClose={()=>setAddSourceOpen(false)} onAdd={addWebDavSource}/>
        <SourceActionsMenu source={menuSource} anchor={sourceMenu?.anchor??null} isDark={isDark} onClose={()=>setSourceMenu(null)}
          onManage={()=>menuSource&&openSourceEditor(menuSource)}
          onEdit={()=>menuSource&&openSourceEditor(menuSource)}
          onScan={()=>menuSource&&scanSource(menuSource.id)}
          onDelete={()=>menuSource&&requestSourceRemoval(menuSource.id)}/>
        <SourceRemovalDialog source={pendingSourceRemoval} isDark={isDark} onClose={()=>setPendingSourceRemovalId(null)} onConfirm={confirmSourceRemoval}/>
        <LocalSourceDialog source={editingLocalSource} onClose={()=>setEditingLocalSourceId(null)} onSave={saveLocalSource}/>
        <ManageWebDavSourceDialog source={editingSource} existingNames={sources.map(source=>source.name)} onClose={()=>setEditingSourceId(null)} onSave={saveWebDavSource} onDelete={deleteWebDavSource}/>
        <SmbSourceDialog open={smbSourceOpen||editingSmbSource!==null} source={editingSmbSource} existingNames={sources.map(source=>source.name)} onClose={closeSmbSource} onSave={saveSmbSource} onDelete={deleteSmbSource}/>
        <MetadataPluginDialog plugin={editingPlugin} isDark={isDark} onClose={()=>setEditingPluginId(null)} onChange={updateMetadataPlugin}/>
        <MetadataPluginRemovalDialog plugin={pendingPluginRemoval} onClose={()=>setPendingPluginRemovalId(null)} onConfirm={confirmPluginRemoval}/>
        <LyricsPriorityDialog open={lyricsPriorityOpen} isDark={isDark} sources={lyricSourcePriority}
          onReorder={setLyricSourcePriority} onMoveToTop={moveLyricSourceToTop} onClose={closeLyricsPriority}/>
        <ThemeColorPickerDialog open={themeColorPickerOpen} savedColor={manualThemeColor} customColors={customThemeColors} onClose={()=>setThemeColorPickerOpen(false)} onApply={color=>{setManualThemeColor(color);setThemeColorPickerOpen(false);}} onCustomColorsChange={setCustomThemeColors}/>
      </>
    );
  }

  return (
    <>
      <div ref={settingsRootRef} className="mx-auto w-full max-w-[800px] px-4 pt-5 pb-8">
        {sub?<DetailContent id={sub}/>:<HomeContent/>}
      </div>
      <AddSourcePickerDialog open={sourcePickerOpen} onClose={()=>setSourcePickerOpen(false)} onWebDav={openWebDavFromPicker} onSmb={openSmbFromPicker}/>
      <AddWebDavSourceDialog open={addSourceOpen} existingNames={sources.map(source=>source.name)} onClose={()=>setAddSourceOpen(false)} onAdd={addWebDavSource}/>
      <SourceActionsMenu source={menuSource} anchor={sourceMenu?.anchor??null} isDark={isDark} onClose={()=>setSourceMenu(null)}
        onManage={()=>menuSource&&openSourceEditor(menuSource)}
        onEdit={()=>menuSource&&openSourceEditor(menuSource)}
        onScan={()=>menuSource&&scanSource(menuSource.id)}
        onDelete={()=>menuSource&&requestSourceRemoval(menuSource.id)}/>
      <SourceRemovalDialog source={pendingSourceRemoval} isDark={isDark} onClose={()=>setPendingSourceRemovalId(null)} onConfirm={confirmSourceRemoval}/>
      <LocalSourceDialog source={editingLocalSource} onClose={()=>setEditingLocalSourceId(null)} onSave={saveLocalSource}/>
      <ManageWebDavSourceDialog source={editingSource} existingNames={sources.map(source=>source.name)} onClose={()=>setEditingSourceId(null)} onSave={saveWebDavSource} onDelete={deleteWebDavSource}/>
      <SmbSourceDialog open={smbSourceOpen||editingSmbSource!==null} source={editingSmbSource} existingNames={sources.map(source=>source.name)} onClose={closeSmbSource} onSave={saveSmbSource} onDelete={deleteSmbSource}/>
      <MetadataPluginDialog plugin={editingPlugin} isDark={isDark} onClose={()=>setEditingPluginId(null)} onChange={updateMetadataPlugin}/>
      <MetadataPluginRemovalDialog plugin={pendingPluginRemoval} onClose={()=>setPendingPluginRemovalId(null)} onConfirm={confirmPluginRemoval}/>
      <LyricsPriorityDialog open={lyricsPriorityOpen} isDark={isDark} sources={lyricSourcePriority}
        onReorder={setLyricSourcePriority} onMoveToTop={moveLyricSourceToTop} onClose={closeLyricsPriority}/>
      <ThemeColorPickerDialog open={themeColorPickerOpen} savedColor={manualThemeColor} customColors={customThemeColors} onClose={()=>setThemeColorPickerOpen(false)} onApply={color=>{setManualThemeColor(color);setThemeColorPickerOpen(false);}} onCustomColorsChange={setCustomThemeColors}/>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// DESIGN SYSTEM PAGES
// ─────────────────────────────────────────────────────────────
function DSCover() {
  const structure = [
    {n:"00 Cover",sub:["Brand","Vision","Principles","Philosophy"]},
    {n:"01 Foundation",sub:["Color","Typography","Grid","Elevation","Blur","Radius","Motion","Icons"]},
    {n:"02 Tokens",sub:["Color Tokens","Space Tokens","Radius Tokens","Motion Tokens","Shadow Tokens"]},
    {n:"03 Components",sub:["Buttons","Navigation","Cards","Player","Settings","Search","Dialogs","Feedback"]},
    {n:"04 Adaptive Layout",sub:["Phone","Fold","Tablet","Auto","iPhone","iPad","Desktop"]},
    {n:"05 Pages",sub:["Home","Search","Library","Settings","Player","Source Manager"]},
    {n:"06 Prototype",sub:[]},
    {n:"07 Motion",sub:["Spring","Shared Element","Blur Morph","Hero"]},
    {n:"08 Dev Mode",sub:[]},
    {n:"09 Compose",sub:["MiuixScaffold","CardGroup","SuperArrow","Preference"]},
  ];
  const principles = ["Simple","Calm","Immersive","Music First","Content First","Adaptive","Native","Cross Platform","Plugin Driven"];
  return (
    <div className="px-4 pt-2 pb-8 space-y-10">
      {/* Hero */}
      <div className="relative rounded-[32px] overflow-hidden p-8 pb-10" style={{background:`linear-gradient(135deg,${G[0][0]}30,${G[1][1]}20,transparent)`}}>
        <div className="absolute inset-0 rounded-[32px] border border-primary/20"/>
        <div className="relative z-10">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-14 h-14 rounded-[18px] flex items-center justify-center shadow-xl" style={{background:"linear-gradient(135deg,var(--brand-pink),var(--brand-purple))"}}>
              <Music2 className="w-7 h-7 text-white"/>
            </div>
            <div>
              <h1 className="text-3xl font-black tracking-tight" style={{background:`linear-gradient(135deg,${G[0][0]},${G[1][1]})`,WebkitBackgroundClip:"text",WebkitTextFillColor:"transparent",backgroundClip:"text"}}>MelodyTrove DS</h1>
              <p className="text-sm text-muted-foreground font-medium">Repository-aligned · v4 · 2026</p>
            </div>
          </div>
          <p className="text-base text-foreground font-medium mb-1">One Library. Every Source.</p>
          <p className="text-sm text-muted-foreground max-w-md">Apple Music Information Architecture × HyperOS Design Language × Compose Multiplatform. A production-ready cross-platform design system for Android, iOS, and Desktop.</p>
        </div>
      </div>

      {/* Principles */}
      <section>
        <SectionHeader title="Design Principles"/>
        <div className="flex flex-wrap gap-2.5">
          {principles.map((p,i)=>(
            <div key={p} className="flex items-center gap-2.5 px-4 py-2.5 rounded-full border border-border bg-card text-sm font-semibold text-foreground hover:border-primary/40 transition-colors cursor-default">
              <div className="w-2 h-2 rounded-full" style={{background:G[i%8][0]}}/>
              {p}
            </div>
          ))}
        </div>
      </section>

      {/* Structure */}
      <section>
        <SectionHeader title="File Structure"/>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {structure.map((s,i)=>(
            <div key={s.n} className="bg-card rounded-2xl border border-border p-4">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-mono font-bold text-muted-foreground">{String(i).padStart(2,"0")}</span>
                <p className="text-sm font-bold text-foreground">{s.n.split(" ").slice(1).join(" ")}</p>
              </div>
              {s.sub.length>0&&<div className="flex flex-wrap gap-1.5">{s.sub.map(sub=>(
                <span key={sub} className="text-[10px] text-muted-foreground bg-muted px-2 py-1 rounded-lg font-medium">{sub}</span>
              ))}</div>}
            </div>
          ))}
        </div>
      </section>

      {/* Platform targets */}
      <section>
        <SectionHeader title="Platform Targets"/>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[{i:<Smartphone className="w-5 h-5"/>,n:"Android Phone",b:"Compact · Medium"},{i:<Tablet className="w-5 h-5"/>,n:"Android Tablet",b:"Expanded · Large"},{i:<Monitor className="w-5 h-5"/>,n:"Desktop",b:"Large · XL"},{i:<Smartphone className="w-5 h-5"/>,n:"Automotive",b:"Landscape Only"}].map(p=>(
            <div key={p.n} className="bg-card rounded-2xl border border-border p-4 flex flex-col items-center text-center gap-2">
              <div className="w-10 h-10 rounded-2xl bg-muted flex items-center justify-center text-muted-foreground">{p.i}</div>
              <p className="text-sm font-semibold text-foreground">{p.n}</p>
              <p className="text-xs text-muted-foreground">{p.b}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function DSFoundation() {
  const colors = [
    {name:"DesignPink",hex:"#FF5B8A",role:"Primary"},{name:"DesignPurple",hex:"#7A6CFF",role:"Secondary"},
    {name:"DesignOrange",hex:"#FF8A3D",role:"Support"},{name:"DesignGreen",hex:"#3DCA8A",role:"Support"},
    {name:"DesignBlue",hex:"#3D9AFF",role:"Support"},{name:"DesignYellow",hex:"#FFD93D",role:"Support"},
  ];
  const typescale = [
    {name:"Display",cls:"text-4xl font-black",size:"36px · 900"},
    {name:"Headline",cls:"text-3xl font-bold",size:"30px · 700"},
    {name:"Title Large",cls:"text-2xl font-bold",size:"24px · 700"},
    {name:"Title",cls:"text-xl font-semibold",size:"20px · 600"},
    {name:"Body Large",cls:"text-base",size:"16px · 400"},
    {name:"Body",cls:"text-sm",size:"14px · 400"},
    {name:"Label",cls:"text-xs font-medium tracking-wide",size:"12px · 500"},
    {name:"Caption",cls:"text-[10px] font-bold tracking-widest uppercase",size:"10px · 700"},
  ];
  const breakpoints = [
    {name:"Compact",range:"0 – 599dp",nav:"Bottom Navigation",layout:"Single Pane",color:G[0][0]},
    {name:"Medium",range:"600 – 839dp",nav:"Bottom Navigation",layout:"Single / Two Pane",color:G[1][0]},
    {name:"Expanded",range:"840 – 1279dp",nav:"Navigation Rail",layout:"Two Pane",color:G[2][0]},
    {name:"Large",range:"1280+ dp",nav:"Sidebar",layout:"Three Pane",color:G[3][0]},
    {name:"XL",range:"1600+ dp",nav:"Sidebar (Wide)",layout:"Three Pane+",color:G[4][0]},
  ];
  const radii = [{l:"Small",v:12},{l:"Medium",v:20},{l:"Large",v:28},{l:"XL",v:36},{l:"Full",v:9999}];
  const elevations = [
    {n:"Surface",s:"none"},{n:"Card",s:"0 2px 8px rgba(0,0,0,0.08)"},{n:"Popup",s:"0 8px 24px rgba(0,0,0,0.14)"},
    {n:"Floating",s:"0 12px 40px rgba(0,0,0,0.22)"},{n:"Overlay",s:"0 24px 64px rgba(0,0,0,0.35)"},
  ];
  return (
    <div className="space-y-10 px-4 py-2 pb-8">
      <section>
        <SectionHeader title="Brand Colors"/>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {colors.map(c=>(
            <div key={c.name} className="bg-card rounded-3xl border border-border overflow-hidden">
              <div className="h-16" style={{background:c.hex}}/>
              <div className="p-3"><p className="text-sm font-semibold text-foreground">{c.name}</p>
                <p className="text-xs font-mono text-muted-foreground">{c.hex}</p>
                <span className="text-[10px] font-bold uppercase tracking-widest" style={{color:c.hex}}>{c.role}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
      <section>
        <SectionHeader title="Gradient Pairs"/>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {G.map((g,i)=>(
            <div key={i} className="rounded-2xl overflow-hidden">
              <div className="h-20 rounded-2xl" style={{background:`linear-gradient(135deg,${g[0]},${g[1]})`}}/>
              <p className="text-[9px] font-mono text-muted-foreground mt-1.5 text-center">{g[0]}</p>
            </div>
          ))}
        </div>
      </section>
      <section>
        <SectionHeader title="Typography Scale · Plus Jakarta Sans"/>
        <div className="bg-card rounded-3xl border border-border overflow-hidden divide-y divide-border/60">
          {typescale.map(t=>(
            <div key={t.name} className="flex items-baseline justify-between px-5 py-4 gap-4">
              <p className={cn("text-foreground truncate",t.cls)}>MelodyTrove</p>
              <div className="text-right shrink-0"><p className="text-xs font-semibold text-foreground">{t.name}</p><p className="text-[10px] font-mono text-muted-foreground">{t.size}</p></div>
            </div>
          ))}
        </div>
      </section>
      <section>
        <SectionHeader title="Responsive Breakpoints"/>
        <div className="space-y-2">
          {breakpoints.map(bp=>(
            <div key={bp.name} className="flex items-center gap-4 p-4 bg-card rounded-2xl border border-border">
              <div className="w-3 h-3 rounded-full shrink-0" style={{background:bp.color}}/>
              <div className="w-24 shrink-0"><p className="text-sm font-bold text-foreground">{bp.name}</p><p className="text-[10px] font-mono text-muted-foreground">{bp.range}</p></div>
              <div className="flex gap-2 flex-wrap">
                <span className="text-xs bg-muted text-muted-foreground px-2.5 py-1 rounded-xl font-medium">{bp.nav}</span>
                <span className="text-xs bg-muted text-muted-foreground px-2.5 py-1 rounded-xl font-medium">{bp.layout}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
      <section>
        <SectionHeader title="Corner Radius"/>
        <div className="flex flex-wrap gap-6 items-end">
          {radii.map(r=>(
            <div key={r.l} className="flex flex-col items-center gap-2">
              <div className="w-20 h-20 bg-primary/15 border-2 border-primary/30" style={{borderRadius:r.v===9999?9999:r.v}}/>
              <p className="text-xs font-semibold text-foreground">{r.l}</p>
              <p className="text-[10px] font-mono text-muted-foreground">{r.v===9999?"∞":r.v+"px"}</p>
            </div>
          ))}
        </div>
      </section>
      <section>
        <SectionHeader title="Elevation Scale"/>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          {elevations.map(e=>(
            <div key={e.n} className="bg-card rounded-2xl p-4" style={{boxShadow:e.s}}>
              <p className="text-sm font-semibold text-foreground">{e.n}</p>
              <p className="text-[10px] font-mono text-muted-foreground mt-1 break-all">{e.s||"none"}</p>
            </div>
          ))}
        </div>
      </section>
      <section>
        <SectionHeader title="Spacing Scale · 8dp Grid"/>
        <div className="flex flex-wrap gap-4 items-end">
          {[4,8,12,16,20,24,32,40,48].map(s=>(
            <div key={s} className="flex flex-col items-center gap-2">
              <div className="bg-secondary/30 rounded" style={{width:s,height:s}}/>
              <span className="text-[10px] font-mono text-muted-foreground">{s}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function DSTokens() {
  const colors = [
    {name:"--brand-pink",  value:"#FF5B8A", role:"Brand Primary"},
    {name:"--brand-purple",value:"#7A6CFF", role:"Brand Secondary"},
    {name:"--brand-blue",  value:"#3D9AFF", role:"Support"},
    {name:"--brand-orange",value:"#FF8A3D", role:"Support"},
    {name:"--brand-green", value:"#3DCA8A", role:"Support"},
    {name:"--brand-yellow",value:"#FFD93D", role:"Support"},
  ];
  const semanticDark = [
    {name:"--background",value:"#0C0A14",role:"Canvas"},
    {name:"--card",      value:"#161224",role:"Surface container"},
    {name:"--muted",     value:"#1E1A30",role:"Raised / variant"},
    {name:"--foreground",value:"#F0EDF8",role:"On-surface primary"},
    {name:"--muted-foreground",value:"#9B97B0",role:"On-surface secondary"},
    {name:"--border",    value:"rgba(240,237,248,0.07)",role:"Outline"},
    {name:"--switch-background",value:"#3A3555",role:"Selected deep"},
  ];
  const semanticLight = [
    {name:"--background",value:"#F4F2FA",role:"Canvas"},
    {name:"--card",      value:"#FFFFFF", role:"Surface container"},
    {name:"--muted",     value:"#EAE7F5",role:"Raised / variant"},
    {name:"--foreground",value:"#0D0B18",role:"On-surface primary"},
    {name:"--muted-foreground",value:"#6B6880",role:"On-surface secondary"},
    {name:"--border",    value:"rgba(13,11,24,0.08)",role:"Outline"},
    {name:"--switch-background",value:"#C5C2D8",role:"Inactive toggle"},
  ];
  // spacing: 0,4,8,12,16,24,32,48
  const spacing = [
    {name:"--space-0", px:"0",  role:"None"},
    {name:"--space-1", px:"4",  role:"Tight"},
    {name:"--space-2", px:"8",  role:"Small"},
    {name:"--space-3", px:"12", role:"Base −"},
    {name:"--space-4", px:"16", role:"Base (page compact)"},
    {name:"--space-6", px:"24", role:"Medium (page expanded)"},
    {name:"--space-8", px:"32", role:"Large"},
    {name:"--space-12",px:"48", role:"XL"},
  ];
  // radii: 0,4,8,12,20,28,36,40,999
  const radii = [
    {name:"--radius-none",px:"0",   use:"Sharp"},
    {name:"--radius-xs",  px:"4",   use:"Chip"},
    {name:"--radius-sm",  px:"8",   use:"Badge / input"},
    {name:"--radius-md",  px:"12",  use:"Search bar"},
    {name:"--radius-lg",  px:"20",  use:"Button pill / nav"},
    {name:"--radius-xl",  px:"28",  use:"DesignCardSurface"},
    {name:"--radius-2xl", px:"36",  use:"FullPlayer art"},
    {name:"--radius-3xl", px:"40",  use:"Hero / cover"},
    {name:"--radius-full",px:"999", use:"Circular"},
  ];
  // blur: 0,8,16,32,48
  const blurs = [
    {name:"--blur-0", px:"0",  use:"None"},
    {name:"--blur-sm",px:"8",  use:"Subtle overlay"},
    {name:"--blur-md",px:"16", use:"Card backdrop"},
    {name:"--blur-lg",px:"32", use:"Player background"},
    {name:"--blur-xl",px:"48", use:"Full-screen overlay"},
  ];
  // motion: 100,180,280,380,400,500 + player 380
  const motion = [
    {name:"--duration-xs",    ms:"100", use:"Micro feedback"},
    {name:"--duration-sm",    ms:"180", use:"Icon / state swap"},
    {name:"--duration-md",    ms:"280", use:"Card expand"},
    {name:"--duration-lg",    ms:"380", use:"Page transition / player expand"},
    {name:"--duration-xl",    ms:"500", use:"Hero morph"},
    {name:"--duration-theme", ms:"400", use:"Theme seed and dark ↔ light transition"},
    {name:"--duration-player",ms:"380", use:"Mini → Full player"},
  ];

  function TokenRow({ name, value, role }: { name:string; value:string; role:string }) {
    return (
      <div className="flex items-center gap-3 px-4 py-2.5">
        <code className="text-[11px] font-mono text-primary w-44 shrink-0 truncate">{name}</code>
        <code className="text-[11px] font-mono text-muted-foreground flex-1 truncate">{value}</code>
        <span className="text-[10px] text-muted-foreground shrink-0 hidden sm:block">{role}</span>
      </div>
    );
  }
  function SwatchRow({ name, value, role }: { name:string; value:string; role:string }) {
    return (
      <div className="flex items-center gap-3 px-4 py-2.5">
        <div className="w-7 h-7 rounded-lg border border-border shrink-0" style={{background:value}}/>
        <code className="text-[11px] font-mono text-primary w-40 shrink-0 truncate">{name}</code>
        <code className="text-[11px] font-mono text-muted-foreground w-20 shrink-0 truncate">{value}</code>
        <span className="text-[10px] text-muted-foreground flex-1 truncate">{role}</span>
      </div>
    );
  }

  return (
    <div className="space-y-8 px-4 py-2 pb-8">
      {/* header */}
      <div className="bg-card rounded-[28px] border border-border p-5">
        <p className="text-sm font-semibold text-foreground mb-1">Repository Token Contract</p>
        <p className="text-xs text-muted-foreground">CSS custom properties mapped via <code className="font-mono text-primary bg-primary/10 px-1 rounded">@theme inline</code>. All scales match the Compose Multiplatform production codebase.</p>
      </div>

      {/* brand colors */}
      <section>
        <SectionHeader title="Brand Colors"/>
        <div className="bg-card rounded-[28px] border border-border overflow-hidden divide-y divide-border/60">
          {colors.map(c=><SwatchRow key={c.name} name={c.name} value={c.value} role={c.role}/>)}
        </div>
      </section>

      {/* semantic dark */}
      <section>
        <SectionHeader title="Semantic — Dark"/>
        <div className="bg-card rounded-[28px] border border-border overflow-hidden divide-y divide-border/60">
          {semanticDark.map(c=><SwatchRow key={c.name} name={c.name} value={c.value} role={c.role}/>)}
        </div>
      </section>

      {/* semantic light */}
      <section>
        <SectionHeader title="Semantic — Light"/>
        <div className="bg-card rounded-[28px] border border-border overflow-hidden divide-y divide-border/60">
          {semanticLight.map(c=><SwatchRow key={c.name} name={c.name} value={c.value} role={c.role}/>)}
        </div>
      </section>

      {/* spacing */}
      <section>
        <SectionHeader title="Spacing — 0 · 4 · 8 · 12 · 16 · 24 · 32 · 48"/>
        <div className="bg-card rounded-[28px] border border-border overflow-hidden divide-y divide-border/60">
          {spacing.map(s=>(
            <div key={s.name} className="flex items-center gap-3 px-4 py-2.5">
              <div className="shrink-0 bg-secondary/25 rounded" style={{width:Math.max(Number(s.px),2),height:Math.max(Number(s.px),2),minWidth:2,minHeight:2,maxWidth:48,maxHeight:48}}/>
              <code className="text-[11px] font-mono text-primary w-24 shrink-0">{s.name}</code>
              <code className="text-[11px] font-mono text-muted-foreground w-10 shrink-0">{s.px}px</code>
              <span className="text-[10px] text-muted-foreground flex-1">{s.role}</span>
            </div>
          ))}
        </div>
      </section>

      {/* radii */}
      <section>
        <SectionHeader title="Radius — 0 · 4 · 8 · 12 · 20 · 28 · 36 · 40 · 999"/>
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-3">
          {radii.map(r=>(
            <div key={r.name} className="bg-card border border-border p-3 flex flex-col items-center gap-2 text-center" style={{borderRadius:Number(r.px)>40?40:Number(r.px)}}>
              <div className="w-10 h-10 bg-primary/15 border border-primary/30" style={{borderRadius:Number(r.px)>40?40:Number(r.px)}}/>
              <code className="text-[9px] font-mono text-primary leading-tight">{r.px}px</code>
              <span className="text-[9px] text-muted-foreground leading-tight">{r.use}</span>
            </div>
          ))}
        </div>
      </section>

      {/* blur */}
      <section>
        <SectionHeader title="Blur — 0 · 8 · 16 · 32 · 48"/>
        <div className="bg-card rounded-[28px] border border-border overflow-hidden divide-y divide-border/60">
          {blurs.map(b=>(
            <div key={b.name} className="flex items-center gap-3 px-4 py-2.5">
              <div className="w-10 h-7 rounded-lg shrink-0 overflow-hidden relative">
                <div className="absolute inset-0" style={{background:`linear-gradient(135deg,${G[0][0]},${G[1][1]})`}}/>
                <div className="absolute inset-0 bg-card/60" style={{backdropFilter:`blur(${b.px}px)`}}/>
              </div>
              <code className="text-[11px] font-mono text-primary w-20 shrink-0">{b.name}</code>
              <code className="text-[11px] font-mono text-muted-foreground w-10 shrink-0">{b.px}px</code>
              <span className="text-[10px] text-muted-foreground flex-1">{b.use}</span>
            </div>
          ))}
        </div>
      </section>

      {/* motion */}
      <section>
        <SectionHeader title="Motion — 100 · 180 · 280 · 380 · theme 400 · 500 · player 380"/>
        <div className="bg-card rounded-[28px] border border-border overflow-hidden divide-y divide-border/60">
          {motion.map(m=>(
            <div key={m.name} className="flex items-center gap-3 px-4 py-2.5">
              <div className="w-16 h-2 bg-muted rounded-full shrink-0 overflow-hidden">
                <div className="h-full bg-primary rounded-full" style={{width:`${Math.round(Number(m.ms)/500*100)}%`}}/>
              </div>
              <code className="text-[11px] font-mono text-primary w-36 shrink-0">{m.name}</code>
              <code className="text-[11px] font-mono text-muted-foreground w-10 shrink-0">{m.ms}ms</code>
              <span className="text-[10px] text-muted-foreground flex-1">{m.use}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function DSComponents() {
  const [sw1,setSw1]=useState(true); const [sw2,setSw2]=useState(false);
  const [sl1,setSl1]=useState(65); const [sl2,setSl2]=useState(40);
  const [tab1,setTab1]=useState("a"); const [tab2,setTab2]=useState("b"); const [tab3,setTab3]=useState("c");
  // Settings Items demo state
  const [ssw1,setSsw1]=useState(true);
  const [ssl1,setSsl1]=useState(14);
  const [stext1,setStext1]=useState("https://nas.home:8096");
  const [sshowPass,setSshowPass]=useState(false);
  const [spicker,setSpicker]=useState("center");
  const [scolor,setScolor]=useState("#FF5B8A");
  const [sbusyAction,setSbusyAction]=useState<"idle"|"busy"|"success"|"error">("idle");
  const [sPick,setSPick]=useState("center");
  const [sSelOpt,setSSelOpt]=useState("auto");
  return (
    <div className="space-y-10 px-4 py-2 pb-8">
      <section><SectionHeader title="Buttons"/>
        <div className="bg-card rounded-3xl border border-border p-5 space-y-4">
          <div className="flex flex-wrap gap-3"><Btn variant="filled">Filled</Btn><Btn variant="secondary">Secondary</Btn><Btn variant="tonal">Tonal</Btn><Btn variant="outlined">Outlined</Btn><Btn variant="ghost">Ghost</Btn></div>
          <div className="flex flex-wrap gap-3"><Btn variant="filled" size="sm">Small</Btn><Btn variant="filled" size="md">Medium</Btn><Btn variant="filled" size="lg">Large</Btn></div>
          <div className="flex flex-wrap gap-3 items-center">
            <Btn variant="filled" icon={<Play className="w-4 h-4 fill-white"/>}>Play</Btn>
            <Btn variant="tonal" icon={<Download className="w-4 h-4"/>}>Download</Btn>
            <Btn variant="outlined" icon={<Share2 className="w-4 h-4"/>}>Share</Btn>
            <Btn variant="filled" icon={<Play className="w-4 h-4 fill-white"/>} iconOnly/>
            <Btn variant="tonal" icon={<Heart className="w-4 h-4"/>} iconOnly/>
            <Btn variant="ghost" icon={<MoreHorizontal className="w-4 h-4"/>} iconOnly/>
          </div>
        </div>
      </section>
      <section><SectionHeader title="Controls — Switch & Slider"/>
        <div className="bg-card rounded-3xl border border-border p-5 space-y-5">
          <div className="flex flex-wrap gap-8"><DesignSwitch checked={sw1} onChange={setSw1} label="Dynamic Color"/><DesignSwitch checked={sw2} onChange={setSw2} label="Blur Effect"/></div>
          <DesignSlider value={sl1} onChange={setSl1} label="Volume"/>
          <DesignSlider value={sl2} onChange={setSl2} label="Treble" accent="var(--brand-purple)"/>
        </div>
      </section>
      <section><SectionHeader title="Tabs"/>
        <div className="bg-card rounded-3xl border border-border p-5 space-y-5">
          <UnderlineTabs tabs={[{id:"a",label:"Songs"},{id:"b",label:"Albums"},{id:"c",label:"Artists"}]} active={tab1} onChange={setTab1}/>
          <PillTabs tabs={[{id:"a",label:"Albums"},{id:"b",label:"Playlists"},{id:"c",label:"Folders"},{id:"d",label:"Sources"}]} active={tab2} onChange={setTab2}/>
          <SegTabs tabs={[{id:"a",label:"Lyrics"},{id:"b",label:"Queue"},{id:"c",label:"EQ"}]} active={tab3} onChange={setTab3}/>
        </div>
      </section>
      <section><SectionHeader title="Album Cards"/>
        <div className="bg-card rounded-3xl border border-border p-5 overflow-x-auto hide-scrollbar">
          <div className="flex gap-4 pb-2">{ALBUMS.slice(0,4).map(a=><AlbumCard key={a.id} album={a}/>)}</div>
        </div>
      </section>
      <section><SectionHeader title="Artist Cards"/>
        <div className="bg-card rounded-3xl border border-border p-5 overflow-x-auto hide-scrollbar">
          <div className="flex gap-4 pb-2">{ARTISTS.slice(0,4).map(a=><ArtistCard key={a.id} artist={a}/>)}</div>
        </div>
      </section>
      <section><SectionHeader title="Playlist Cards"/>
        <div className="bg-card rounded-3xl border border-border p-5 overflow-x-auto hide-scrollbar">
          <div className="flex gap-4 pb-2">{PLAYLISTS.slice(0,4).map(p=><PlaylistCard key={p.id} playlist={p}/>)}</div>
        </div>
      </section>
      <section><SectionHeader title="Music List Items"/>
        <div className="bg-card rounded-3xl border border-border overflow-hidden">
          {SONGS.slice(0,4).map(s=><MusicCard key={s.id} song={s} onPlay={()=>{}} isPlaying={s.id===1}/>)}
        </div>
      </section>
      <section><SectionHeader title="Source Cards"/>
        <div className="space-y-4">
          <SourceCard source={{name:"Personal NAS",type:"WebDAV",icon:<Server className="w-5 h-5"/>,status:"connected",storage:"128 GB",tracks:5820,gradient:G[1]}}/>
          <SourceCard source={{name:"Jellyfin Home",type:"Jellyfin",icon:<Radio className="w-5 h-5"/>,status:"syncing",storage:"512 GB",tracks:18200,gradient:G[3]}}/>
        </div>
      </section>
      <section id="settings-items"><SectionHeader title="Settings Items"/>

        {/* ─── 1. Anatomy & Row Types ─────────────────────── */}
        <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground mb-3 mt-1">Anatomy &amp; Row Types</p>
        <div className="bg-card rounded-3xl border border-border overflow-hidden divide-y divide-border/50 mb-2">

          {/* 1 — Category */}
          <div className="px-5 pt-4 pb-3">
            <div className="flex items-center gap-2">
              <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">Playback</p>
              <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground">Category</span>
            </div>
            <p className="text-[11px] text-muted-foreground/70 mt-0.5">Audio output and quality control</p>
          </div>

          {/* 2 — Navigation */}
          <div className="flex items-center gap-3 px-5 min-h-[56px] hover:bg-muted/40 transition-colors cursor-pointer">
            <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0"><Palette className="w-[15px] h-[15px] text-muted-foreground"/></div>
            <div className="flex-1 min-w-0 py-3.5">
              <p className="text-[15px] font-medium text-foreground leading-tight">Theme</p>
              <p className="text-[12px] text-muted-foreground mt-0.5">System appearance and blur</p>
            </div>
            <span className="text-[13px] text-muted-foreground shrink-0">Dark</span>
            <ChevronRight className="w-4 h-4 text-muted-foreground/50 shrink-0"/>
            <span className="ml-2 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Navigation</span>
          </div>

          {/* 3 — Switch */}
          <div className="flex items-center gap-3 px-5 min-h-[56px] hover:bg-muted/40 transition-colors">
            <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0"><AlignLeft className="w-[15px] h-[15px] text-muted-foreground"/></div>
            <div className="flex-1 min-w-0 py-3.5">
              <p className="text-[15px] font-medium text-foreground leading-tight">Enable Lyrics</p>
              <p className="text-[12px] text-muted-foreground mt-0.5">Show synced lyrics while playing</p>
            </div>
            <DesignSwitch checked={ssw1} onChange={setSsw1}/>
            <span className="ml-3 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Switch</span>
          </div>

          {/* 4 — Select */}
          <div className="flex items-center gap-3 px-5 min-h-[56px] hover:bg-muted/40 transition-colors">
            <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0"><AlignLeft className="w-[15px] h-[15px] text-muted-foreground"/></div>
            <div className="flex-1 min-w-0 py-3.5">
              <p className="text-[15px] font-medium text-foreground leading-tight">Lyrics Alignment</p>
              <p className="text-[12px] text-muted-foreground mt-0.5">Text alignment in lyrics view</p>
            </div>
            <select value={spicker} onChange={e=>setSpicker(e.target.value)}
              className="text-[13px] text-muted-foreground bg-muted rounded-[10px] px-2.5 py-1.5 border-none outline-none cursor-pointer shrink-0 min-h-[36px]">
              <option value="left">Left</option>
              <option value="center">Center</option>
              <option value="right">Right</option>
            </select>
            <span className="ml-2 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Select</span>
          </div>

          {/* 5 — Slider */}
          <div className="px-5 py-3.5 min-h-[56px]">
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0 mt-0.5"><Hash className="w-[15px] h-[15px] text-muted-foreground"/></div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-0.5">
                  <p className="text-[15px] font-medium text-foreground">Font Size</p>
                  <div className="flex items-center gap-2">
                    <span className="text-[13px] font-mono text-muted-foreground">{ssl1}px</span>
                    <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground">Slider</span>
                  </div>
                </div>
                <p className="text-[12px] text-muted-foreground mb-2">Lyrics and interface text size</p>
                <div className="flex items-center gap-2.5">
                  <span className="text-[10px] text-muted-foreground shrink-0">10px</span>
                  <div className="flex-1"><DesignSlider value={(ssl1-10)/(24-10)*100} onChange={v=>setSsl1(Math.round(10+v*(24-10)/100))}/></div>
                  <span className="text-[10px] text-muted-foreground shrink-0">24px</span>
                </div>
              </div>
            </div>
          </div>

          {/* 6 — Text */}
          <div className="px-5 py-3.5 min-h-[56px]">
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0 mt-0.5"><Server className="w-[15px] h-[15px] text-muted-foreground"/></div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-1.5">
                  <p className="text-[15px] font-medium text-foreground">Server Address</p>
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground">Text</span>
                </div>
                <input value={stext1} onChange={e=>setStext1(e.target.value)}
                  className="w-full h-9 px-3 rounded-xl bg-muted text-[13px] text-foreground font-mono border border-transparent focus:border-primary/40 focus:ring-2 focus:ring-primary/15 outline-none transition-all"/>
              </div>
            </div>
          </div>

          {/* 7 — Password */}
          <div className="px-5 py-3.5 min-h-[56px]">
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0 mt-0.5"><Database className="w-[15px] h-[15px] text-muted-foreground"/></div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-1.5">
                  <p className="text-[15px] font-medium text-foreground">API Key</p>
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground">Password</span>
                </div>
                <div className="flex gap-2">
                  <div className="flex-1 relative">
                    <input type={sshowPass?"text":"password"} defaultValue="sk-test-abc123xyz"
                      className="w-full h-9 px-3 pr-9 rounded-xl bg-muted text-[13px] text-foreground font-mono border border-transparent focus:border-primary/40 focus:ring-2 focus:ring-primary/15 outline-none transition-all"/>
                    <button onClick={()=>setSshowPass(!sshowPass)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-0.5">
                      {sshowPass?<EyeOff className="w-3.5 h-3.5"/>:<Eye className="w-3.5 h-3.5"/>}
                    </button>
                  </div>
                  <button className="h-9 px-3 rounded-xl bg-muted text-[12px] font-medium text-muted-foreground hover:text-foreground transition-colors shrink-0">Clear</button>
                </div>
              </div>
            </div>
          </div>

          {/* — Library category divider */}
          <div className="px-5 pt-4 pb-3 bg-muted/30">
            <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">Library</p>
          </div>

          {/* 8 — Picker (single-choice list) */}
          {(["Left","Center","Right"] as const).map((opt,i)=>(
            <div key={opt} onClick={()=>setSPick(opt.toLowerCase())}
              className="flex items-center gap-3 px-5 min-h-[48px] cursor-pointer hover:bg-muted/40 transition-colors">
              <p className="flex-1 text-[15px] text-foreground">{opt}</p>
              {i===0&&<span className="mr-2 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground">Picker</span>}
              <div className={cn("w-[18px] h-[18px] rounded-full border-2 flex items-center justify-center shrink-0 transition-all",
                sPick===opt.toLowerCase()?"border-primary":"border-border")}>
                {sPick===opt.toLowerCase()&&<div className="w-2 h-2 rounded-full bg-primary"/>}
              </div>
            </div>
          ))}

          {/* 9 — Color */}
          <div className="flex items-center gap-3 px-5 min-h-[56px] hover:bg-muted/40 transition-colors">
            <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0"><Palette className="w-[15px] h-[15px] text-muted-foreground"/></div>
            <p className="flex-1 text-[15px] font-medium text-foreground">Accent Color</p>
            <div className="flex items-center gap-1.5 shrink-0">
              {(["#FF5B8A","#7A6CFF","#3D9AFF","#3DCA8A","#FF8A3D"] as const).map(c=>(
                <button key={c} onClick={()=>setScolor(c)}
                  className="w-[22px] h-[22px] rounded-full transition-all"
                  style={{background:c,outline:scolor===c?`2.5px solid ${c}`:undefined,outlineOffset:scolor===c?"2px":undefined,border:scolor===c?"2px solid white":"2px solid transparent"}}/>
              ))}
            </div>
            <span className="ml-3 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Color</span>
          </div>

          {/* 10 — Reorder */}
          {[{lbl:"Music Folders",on:true},{lbl:"Show Genres",on:false}].map((r,i)=>(
            <div key={r.lbl} className="flex items-center gap-2.5 px-5 min-h-[52px] hover:bg-muted/40 transition-colors">
              <GripVertical className="w-4 h-4 text-muted-foreground/40 shrink-0 cursor-grab active:cursor-grabbing"/>
              <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0"><Folder className="w-[15px] h-[15px] text-muted-foreground"/></div>
              <p className="flex-1 text-[15px] font-medium text-foreground">{r.lbl}</p>
              <DesignSwitch checked={r.on} onChange={()=>{}}/>
              {i===0&&<span className="ml-2 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Reorder</span>}
            </div>
          ))}

          {/* — Advanced category divider */}
          <div className="px-5 pt-4 pb-3 bg-muted/30">
            <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground">Advanced</p>
          </div>

          {/* 11 — Action */}
          <motion.button whileTap={{scale:0.99}}
            onClick={()=>{if(sbusyAction!=="idle")return;setSbusyAction("busy");setTimeout(()=>setSbusyAction("success"),1800);setTimeout(()=>setSbusyAction("idle"),4000);}}
            className="flex items-center gap-3 px-5 min-h-[52px] w-full text-left hover:bg-muted/40 transition-colors disabled:opacity-50 disabled:pointer-events-none"
            disabled={sbusyAction==="busy"}>
            <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0">
              {sbusyAction==="busy"?<RefreshCw className="w-[15px] h-[15px] text-muted-foreground animate-spin"/>
                :sbusyAction==="success"?<CheckCircle2 className="w-[15px] h-[15px] text-[#3DCA8A]"/>
                :<Cloud className="w-[15px] h-[15px] text-muted-foreground"/>}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-[15px] font-medium text-foreground">Clear Online Cache</p>
              <p className="text-[12px] text-muted-foreground mt-0.5">
                {sbusyAction==="busy"?"Clearing cached content…":sbusyAction==="success"?"Cache cleared successfully":"Free up streamed content storage"}
              </p>
            </div>
            <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Action</span>
          </motion.button>

          {/* 12 — Destructive */}
          <div className="flex items-center gap-3 px-5 min-h-[52px] hover:bg-destructive/5 transition-colors cursor-pointer">
            <div className="w-8 h-8 rounded-[10px] bg-destructive/10 flex items-center justify-center shrink-0"><X className="w-[15px] h-[15px] text-destructive"/></div>
            <div className="flex-1 min-w-0">
              <p className="text-[15px] font-medium text-destructive">Delete Backup</p>
              <p className="text-[12px] text-muted-foreground mt-0.5">Permanently remove backup — cannot be undone</p>
            </div>
            <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Destructive</span>
          </div>

          {/* 13 — Permission */}
          <div className="flex items-center gap-3 px-5 min-h-[52px]">
            <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center shrink-0"><FolderOpen className="w-[15px] h-[15px] text-muted-foreground"/></div>
            <div className="flex-1 min-w-0">
              <p className="text-[15px] font-medium text-foreground">Grant Folder Access</p>
              <p className="text-[12px] text-muted-foreground mt-0.5">Music Folders — required to load library</p>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold" style={{background:"rgba(255,211,61,0.14)",color:"#967000"}}>Required</span>
              <motion.button whileTap={{scale:0.93}}
                className="px-3 py-1.5 rounded-[10px] text-[12px] font-semibold text-white"
                style={{background:"var(--brand-pink)"}}>Grant</motion.button>
            </div>
            <span className="ml-2 px-1.5 py-0.5 rounded text-[9px] font-mono bg-muted text-muted-foreground shrink-0">Permission</span>
          </div>
        </div>

        {/* ─── Settings/Server ─────────────────────────────── */}
        <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground mb-3 mt-8">Settings / Server</p>
        <div className="space-y-2.5 mb-2">
          {([
            {name:"Home Navidrome",type:"Navidrome",ep:"navidrome.home:4533",status:"connected",tracks:6140,storage:"94 GB",g:G[1]},
            {name:"Studio Emby",type:"Emby",ep:"emby.studio.local:8096",status:"error",tracks:12800,storage:"320 GB",g:G[3]},
            {name:"Archive WebDAV",type:"WebDAV",ep:"dav.archive.example:443",status:"syncing",tracks:2380,storage:"48 GB",g:G[5]},
          ] as {name:string;type:string;ep:string;status:"connected"|"error"|"syncing";tracks:number;storage:string;g:[string,string]}[]).map(s=>(
            <div key={s.name} className="bg-card rounded-2xl border border-border p-4 flex items-center gap-3">
              <div className="w-10 h-10 rounded-[12px] flex items-center justify-center shrink-0"
                style={{background:`linear-gradient(135deg,${s.g[0]},${s.g[1]})`}}>
                <Server className="w-4 h-4 text-white"/>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1.5 flex-wrap">
                  <p className="text-[15px] font-semibold text-foreground">{s.name}</p>
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-mono" style={{background:"rgba(13,11,24,0.06)",color:"var(--muted-foreground)"}}>{s.type}</span>
                </div>
                <p className="text-[12px] text-muted-foreground font-mono truncate mt-0.5">{s.ep}</p>
                <div className="flex items-center gap-3 mt-1 flex-wrap">
                  <span className={cn("flex items-center gap-1 text-[11px] font-semibold",
                    s.status==="connected"?"text-[#3DCA8A]":s.status==="error"?"text-destructive":"text-[#C28B00]")}>
                    <span className="w-1.5 h-1.5 rounded-full bg-current inline-block"/>
                    {s.status==="connected"?"Connected":s.status==="error"?"Connection error":"Syncing"}
                  </span>
                  <span className="text-[11px] text-muted-foreground">{s.tracks.toLocaleString()} tracks</span>
                  <span className="text-[11px] text-muted-foreground">{s.storage}</span>
                </div>
              </div>
              <div className="flex items-center gap-0.5 shrink-0">
                <button className="w-9 h-9 rounded-[10px] flex items-center justify-center hover:bg-muted transition-colors"><SlidersHorizontal className="w-3.5 h-3.5 text-muted-foreground"/></button>
                <button className="w-9 h-9 rounded-[10px] flex items-center justify-center hover:bg-destructive/10 transition-colors group"><X className="w-3.5 h-3.5 text-muted-foreground group-hover:text-destructive transition-colors"/></button>
              </div>
            </div>
          ))}
        </div>

        {/* ─── Settings/Selection ──────────────────────────── */}
        <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground mb-3 mt-8">Settings / Selection — Output Bit Depth</p>
        <div className="bg-card rounded-3xl border border-border overflow-hidden divide-y divide-border/50 mb-2">
          {([
            {id:"auto",lbl:"Auto",desc:"Match source bit depth automatically"},
            {id:"16",lbl:"16-bit",desc:"CD quality — 16-bit integer PCM"},
            {id:"24",lbl:"24-bit",desc:"Studio quality — 24-bit integer PCM"},
            {id:"32",lbl:"32-bit",desc:"High-precision — 32-bit integer PCM"},
            {id:"float32",lbl:"Float32",desc:"Floating-point — for DSP chains"},
          ] as {id:string;lbl:string;desc:string}[]).map(opt=>{
            const sel = sSelOpt===opt.id;
            return (
              <div key={opt.id} onClick={()=>setSSelOpt(opt.id)}
                className="flex items-center gap-3 px-5 min-h-[52px] cursor-pointer transition-colors hover:brightness-95"
                style={{background: sel ? "rgba(255,91,138,0.07)" : undefined}}>
                <div className="flex-1 min-w-0">
                  <p className="text-[15px] font-medium transition-colors" style={{color: sel ? "var(--brand-pink)" : "var(--foreground)"}}>{opt.lbl}</p>
                  <p className="text-[12px] text-muted-foreground">{opt.desc}</p>
                </div>
                <div className="w-[18px] h-[18px] rounded-full border-2 flex items-center justify-center shrink-0 transition-all"
                  style={{borderColor: sel ? "var(--brand-pink)" : "var(--border)"}}>
                  {sel && <div className="w-2 h-2 rounded-full" style={{background:"var(--brand-pink)"}}/>}
                </div>
              </div>
            );
          })}
        </div>

        {/* ─── 2. State Matrix ─────────────────────────────── */}
        <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground mb-3 mt-8">State Matrix</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 mb-2">
          {([
            {state:"Default",badge:undefined,note:"Base interactive — transparent bg",indicator:<DesignSwitch checked={true} onChange={()=>{}}/>},
            {state:"Hover",badge:undefined,note:"Pointer-over, pre-press — bg-muted/40",indicator:<DesignSwitch checked={false} onChange={()=>{}}/>},
            {state:"Focus-visible",badge:undefined,note:"Keyboard nav — ring-2 ring-primary/40",indicator:<div className="h-9 px-3 rounded-xl bg-muted text-[13px] text-foreground font-mono flex items-center ring-2 ring-primary/40" style={{minWidth:80}}>Value</div>},
            {state:"Pressed",badge:undefined,note:"Touch/click hold — scale-[0.99]",indicator:<motion.div whileTap={{scale:0.95}} className="px-3 py-1.5 rounded-xl text-[13px] font-semibold text-white cursor-pointer" style={{background:"var(--brand-pink)"}}>Action</motion.div>},
            {state:"Selected",badge:undefined,note:"Active choice — bg-primary/5",indicator:<div className="w-[18px] h-[18px] rounded-full border-2 border-primary flex items-center justify-center"><div className="w-2 h-2 rounded-full bg-primary"/></div>},
            {state:"Disabled",badge:undefined,note:"opacity-40, pointer-events-none",indicator:<div className="opacity-40 pointer-events-none"><DesignSwitch checked={false} onChange={()=>{}}/></div>},
            {state:"Dependent-disabled",badge:undefined,note:"Gated by parent — opacity-55",indicator:<div className="opacity-55 pointer-events-none"><DesignSwitch checked={false} onChange={()=>{}}/></div>},
            {state:"Permission-required",badge:<span className="px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0" style={{background:"rgba(255,211,61,0.14)",color:"#967000"}}>Required</span>,note:"Grant action required",indicator:undefined},
            {state:"Busy",badge:undefined,note:"Async in-progress — spinner",indicator:<RefreshCw className="w-4 h-4 text-muted-foreground animate-spin"/>},
            {state:"Success",badge:undefined,note:"Operation complete — DesignGreen",indicator:<CheckCircle2 className="w-4 h-4 text-[#3DCA8A]"/>},
            {state:"Error",badge:undefined,note:"Operation failed — destructive",indicator:<AlertCircle className="w-4 h-4 text-destructive"/>},
            {state:"Destructive",badge:undefined,note:"Irreversible — red title + icon bg",indicator:<span className="text-[13px] font-semibold text-destructive">Delete</span>},
          ] as {state:string;badge:React.ReactNode;note:string;indicator:React.ReactNode}[]).map(row=>(
            <div key={row.state}
              className={cn("bg-card rounded-2xl border flex items-center gap-3 px-4 min-h-[58px] transition-all",
                row.state==="Destructive"?"border-destructive/20":
                row.state==="Selected"?"border-primary/20 bg-primary/[0.04]":
                "border-border")}>
              <div className="flex-1 min-w-0">
                <p className={cn("text-[14px] font-semibold",row.state==="Destructive"?"text-destructive":"text-foreground")}>{row.state}</p>
                <p className="text-[11px] text-muted-foreground mt-0.5">{row.note}</p>
              </div>
              {row.badge}
              {row.indicator}
            </div>
          ))}
        </div>

        {/* ─── 3. Usage Rules ──────────────────────────────── */}
        <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-muted-foreground mb-3 mt-8">Usage Rules</p>
        <div className="bg-card rounded-2xl border border-border p-5 space-y-3">
          {([
            ["Hit targets","All row heights ≥ 44px. Icon containers 32×32px with 15px visual icon inside. Action buttons ≥ 44×44px touch area."],
            ["Typography","Row title: 15px/medium/foreground. Summary: 12px/muted-foreground. Category label: 11px/bold/uppercase/0.12em tracking."],
            ["Focus","ring-2 ring-primary/40 outline-offset-2 on all interactive elements. Never suppress for keyboard users."],
            ["Dependent rows","Apply opacity-50 to child rows gated by a parent switch. Keep them visually indented or grouped under the same category."],
            ["Destructive","Pair bg-destructive/10 icon container with text-destructive title. Always include a warning subtitle and confirm via sheet before executing."],
            ["Animations","Use duration-[180ms] for hover/press transitions. Respect prefers-reduced-motion: prefer CSS transitions over spring-based motion for Settings rows."],
          ] as [string,string][]).map(([title,body])=>(
            <div key={title} className="flex gap-3">
              <div className="w-1.5 h-1.5 rounded-full bg-primary mt-[7px] shrink-0"/>
              <p className="text-[13px] text-muted-foreground"><span className="font-semibold text-foreground">{title}: </span>{body}</p>
            </div>
          ))}
        </div>
      </section>
      <section><SectionHeader title="Skeleton"/>
        <div className="bg-card rounded-3xl border border-border p-5 space-y-3">
          <div className="flex items-center gap-3"><SkeletonBlock className="w-11 h-11 rounded-xl"/><div className="flex-1 space-y-2"><SkeletonBlock className="h-4 w-3/4 rounded-xl"/><SkeletonBlock className="h-3 w-1/2 rounded-xl"/></div></div>
          <div className="flex gap-3">{[0,1,2,3].map(i=><div key={i} className="flex flex-col gap-2"><SkeletonBlock className="w-[140px] h-[140px] rounded-3xl"/><SkeletonBlock className="h-3 w-24 rounded-xl"/><SkeletonBlock className="h-2.5 w-16 rounded-xl"/></div>)}</div>
        </div>
      </section>
      <section><SectionHeader title="Empty State"/>
        <div className="bg-card rounded-3xl border border-border">
          <EmptyState icon={<Music2 className="w-7 h-7"/>} title="No songs found" subtitle="Add a source to get started" action="Add Source"/>
        </div>
      </section>
    </div>
  );
}

function DSPatterns() {
  return (
    <div className="space-y-10 px-4 py-2 pb-8">
      <section><SectionHeader title="Mini Player"/>
        <div className="bg-muted rounded-3xl p-4">
          <div className="relative flex items-center gap-3 px-4 h-[68px] rounded-[28px]" style={{background:"var(--card)",border:"1px solid var(--border)",boxShadow:"0 8px 32px rgba(0,0,0,0.15)"}}>
            <div className="absolute inset-0 rounded-[28px] overflow-hidden opacity-[0.08]" style={{background:`linear-gradient(90deg,${G[0][0]},${G[0][1]})`}}/>
            <div className="w-11 h-11 rounded-xl shrink-0" style={{background:`linear-gradient(135deg,${G[0][0]},${G[0][1]})`}}/>
            <div className="flex-1"><p className="text-sm font-semibold text-foreground">Midnight Cascade</p><p className="text-xs text-muted-foreground">Luna Waves</p></div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-border rounded-full overflow-hidden"><div className="h-full w-[40%] rounded-full" style={{background:`linear-gradient(90deg,${G[0][0]},${G[0][1]})`}}/></div>
            <div className="flex items-center gap-1">
              <div className="w-10 h-10 rounded-full bg-muted/50 flex items-center justify-center"><Play className="w-5 h-5 fill-foreground"/></div>
              <div className="w-10 h-10 rounded-full bg-muted/50 flex items-center justify-center"><SkipForward className="w-5 h-5 text-foreground"/></div>
            </div>
          </div>
        </div>
      </section>
      <section><SectionHeader title="Navigation Bar (Phone · Compact)"/>
        <div className="bg-muted rounded-3xl p-4">
          <div className="flex items-center justify-around px-4 h-16 rounded-3xl" style={{background:"var(--card)",border:"1px solid var(--border)"}}>
            {[{i:<Home className="w-5 h-5"/>,l:"Home",a:true},{i:<Search className="w-5 h-5"/>,l:"Search",a:false},{i:<Library className="w-5 h-5"/>,l:"Library",a:false},{i:<Settings className="w-5 h-5"/>,l:"Settings",a:false}].map(item=>(
              <div key={item.l} className={cn("flex flex-col items-center gap-1 px-4 py-1 rounded-2xl",item.a?"text-primary":"text-muted-foreground")}>
                <div className={cn("p-1.5 rounded-xl",item.a?"bg-primary/15":"")}>{item.i}</div>
                <span className="text-[10px] font-semibold">{item.l}</span>
              </div>
            ))}
          </div>
        </div>
      </section>
      <section><SectionHeader title="Navigation Rail (Tablet · Expanded)"/>
        <div className="bg-muted rounded-3xl p-4">
          <div className="w-20 flex flex-col items-center py-4 gap-1 rounded-3xl" style={{background:"var(--card)",border:"1px solid var(--border)"}}>
            <div className="w-10 h-10 rounded-2xl mb-3 flex items-center justify-center" style={{background:"linear-gradient(135deg,var(--brand-pink),var(--brand-purple))"}}><Music2 className="w-5 h-5 text-white"/></div>
            {[{i:<Home className="w-4 h-4"/>,l:"Home",a:true},{i:<Search className="w-4 h-4"/>,l:"Search",a:false},{i:<Library className="w-4 h-4"/>,l:"Library",a:false},{i:<Settings className="w-4 h-4"/>,l:"Settings",a:false}].map(item=>(
              <div key={item.l} className={cn("flex flex-col items-center gap-1 w-full px-2 py-2.5 rounded-2xl",item.a?"bg-primary/15 text-primary":"text-muted-foreground")}>
                {item.i}<span className="text-[9px] font-bold">{item.l}</span>
              </div>
            ))}
          </div>
        </div>
      </section>
      <section><SectionHeader title="Desktop Layout (Large · XL)"/>
        <div className="bg-muted rounded-3xl p-4 overflow-hidden">
          <div className="bg-card rounded-3xl border border-border overflow-hidden" style={{minHeight:300}}>
            {/* Toolbar */}
            <div className="flex items-center gap-3 px-4 h-12 border-b border-border bg-card/80">
              <div className="flex gap-1.5"><div className="w-3 h-3 rounded-full bg-red-400"/><div className="w-3 h-3 rounded-full bg-yellow-400"/><div className="w-3 h-3 rounded-full bg-green-400"/></div>
              <div className="flex gap-1"><div className="w-6 h-6 rounded-lg bg-muted flex items-center justify-center"><ArrowLeft className="w-3 h-3 text-muted-foreground"/></div><div className="w-6 h-6 rounded-lg bg-muted flex items-center justify-center"><ArrowRight className="w-3 h-3 text-muted-foreground"/></div></div>
              <div className="flex-1 h-7 bg-muted rounded-xl flex items-center px-3"><Search className="w-3 h-3 text-muted-foreground mr-2"/><span className="text-xs text-muted-foreground">Search MelodyTrove…</span></div>
              <div className="flex gap-1.5"><div className="w-6 h-6 rounded-lg bg-muted"/><div className="w-6 h-6 rounded-lg bg-muted"/><div className="w-6 h-6 rounded-lg bg-muted"/></div>
            </div>
            <div className="flex" style={{height:220}}>
              {/* Sidebar */}
              <div className="w-40 border-r border-border p-3 flex flex-col gap-1">
                {["Home","Search","Library","Settings"].map((n,i)=><div key={n} className={cn("h-8 rounded-xl flex items-center px-3 text-xs font-semibold",i===0?"bg-primary/15 text-primary":"text-muted-foreground hover:bg-muted")}>{n}</div>)}
                <div className="mt-3 border-t border-border pt-3"><p className="text-[9px] font-bold uppercase tracking-widest text-muted-foreground px-2 mb-2">Design System</p>
                {["Foundation","Components","Patterns"].map(n=><div key={n} className="h-7 rounded-xl flex items-center px-3 text-[10px] font-semibold text-muted-foreground hover:bg-muted">{n}</div>)}
                </div>
              </div>
              {/* Content */}
              <div className="flex-1 p-4"><div className="h-full bg-muted/50 rounded-2xl flex items-center justify-center"><span className="text-xs text-muted-foreground">Content Area</span></div></div>
              {/* Right panel */}
              <div className="w-28 border-l border-border p-3"><p className="text-[10px] font-bold text-muted-foreground mb-2">Lyrics</p><div className="space-y-1.5">{[80,60,90,50,70].map((w,i)=><div key={i} className="h-2 rounded-full bg-muted" style={{width:`${w}%`}}/>)}</div></div>
            </div>
            {/* Mini player */}
            <div className="h-14 border-t border-border flex items-center px-4 gap-3 bg-card/80">
              <div className="w-9 h-9 rounded-xl" style={{background:`linear-gradient(135deg,${G[0][0]},${G[0][1]})`}}/>
              <div className="flex-1"><div className="h-2.5 bg-muted rounded-full w-32 mb-1"/><div className="h-2 bg-muted rounded-full w-20"/></div>
              <div className="flex gap-2">{[SkipBack,Play,SkipForward].map((Icon,i)=><div key={i} className="w-7 h-7 rounded-full bg-muted flex items-center justify-center"><Icon className="w-3.5 h-3.5 text-muted-foreground"/></div>)}</div>
              <div className="w-24 h-1.5 bg-muted rounded-full overflow-hidden"><div className="h-full w-2/5 rounded-full" style={{background:G[0][0]}}/></div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

function DSCompose() {
  const mappings = [
    {figma:"Button (Filled)",compose:"MiuixButton",module:"miuix.compose.ui.components",props:"text, onClick, enabled, colors"},
    {figma:"Settings Group Card",compose:"CardGroup",module:"miuix.compose.ui.layout",props:"title, items"},
    {figma:"Settings Item → Arrow",compose:"SuperArrow",module:"miuix.compose.ui.components",props:"title, summary, rightText, onClick"},
    {figma:"Navigation Rail",compose:"NavigationRail",module:"miuix.compose.ui.components",props:"items, selectedItem, onItemSelected"},
    {figma:"Navigation Bar",compose:"BottomNavBar",module:"miuix.compose.ui.components",props:"items, selectedItem, onItemSelected"},
    {figma:"Switch",compose:"MiuixSwitch",module:"miuix.compose.ui.components",props:"checked, onCheckedChange, enabled"},
    {figma:"Slider",compose:"MiuixSlider",module:"miuix.compose.ui.components",props:"value, onValueChange, valueRange"},
    {figma:"Top App Bar",compose:"DefaultTopAppBar / SmallTopAppBar",module:"miuix.compose.ui.components",props:"title, actions, navigationIcon"},
    {figma:"Scaffold",compose:"MiuixScaffold",module:"miuix.compose.ui.layout",props:"topBar, bottomBar, floatingActionButton, content"},
    {figma:"Dialog",compose:"MiuixDialog",module:"miuix.compose.ui.components",props:"title, summary, onDismiss, buttons"},
    {figma:"Small Title",compose:"SmallTitle",module:"miuix.compose.ui.components",props:"text, modifier"},
    {figma:"Page Navigator",compose:"Navigator",module:"miuix.compose.extra",props:"items, pageTransition, springSpec"},
    {figma:"Floating Card / Mini Player",compose:"FloatingCard",module:"miuix.compose.ui.layout",props:"content, modifier, elevation"},
    {figma:"Preference Item",compose:"Preference",module:"miuix.compose.ui.components",props:"title, summary, icon, trailing"},
  ];
  return (
    <div className="space-y-6 px-4 py-2 pb-8">
      <div className="bg-card/60 rounded-3xl border border-border p-5">
        <div className="flex items-center gap-3 mb-3">
          <div className="w-10 h-10 rounded-2xl flex items-center justify-center" style={{background:"linear-gradient(135deg,var(--brand-green),var(--brand-blue))"}}><Code2 className="w-5 h-5 text-white"/></div>
          <div><p className="text-sm font-bold text-foreground">Compose Multiplatform Mapping</p><p className="text-xs text-muted-foreground">Figma → compose-miuix-ui 1:1</p></div>
        </div>
        <p className="text-xs text-muted-foreground">Each Figma component maps directly to a compose-miuix-ui component. The goal is Figma → Compose 1:1 so designers and developers share the same vocabulary.</p>
      </div>

      <div className="bg-card rounded-3xl border border-border overflow-hidden divide-y divide-border/60">
        <div className="grid grid-cols-[1fr,1fr] gap-4 px-4 py-2.5 bg-muted/50">
          <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Figma Component</p>
          <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Compose Component</p>
        </div>
        {mappings.map(m=>(
          <div key={m.figma} className="px-4 py-3 hover:bg-muted/30 transition-colors">
            <div className="grid grid-cols-[1fr,1fr] gap-4 mb-1.5">
              <p className="text-sm font-semibold text-foreground">{m.figma}</p>
              <code className="text-sm font-mono text-primary">{m.compose}</code>
            </div>
            <div className="grid grid-cols-[1fr,1fr] gap-4">
              <code className="text-[10px] font-mono text-muted-foreground">{m.module}</code>
              <p className="text-[10px] text-muted-foreground font-mono">{m.props}</p>
            </div>
          </div>
        ))}
      </div>

      <section>
        <SectionHeader title="Motion Mapping"/>
        <div className="bg-card rounded-3xl border border-border overflow-hidden divide-y divide-border/60">
          {[
            {figma:"Mini Player → Full Player",compose:"SharedTransitionScope + AnimatedContent",note:"Shared element with artwork key"},
            {figma:"Page transition",compose:"Navigator pageTransition + MiuixScrollBehavior",note:"Spring spec: stiffness=400, damping=35"},
            {figma:"Card hover / press",compose:"scale(0.97) via Indication",note:"rememberRipple or custom pressedScale"},
            {figma:"Blur Morph",compose:"BlurTransform + AnimatedBlur",note:"Compose 1.7+ BlurMask"},
          ].map(r=>(
            <div key={r.figma} className="px-4 py-3">
              <div className="flex items-start gap-3">
                <div className="w-1.5 h-1.5 rounded-full bg-primary mt-1.5 shrink-0"/>
                <div>
                  <p className="text-sm font-semibold text-foreground">{r.figma}</p>
                  <code className="text-xs font-mono text-secondary">{r.compose}</code>
                  <p className="text-xs text-muted-foreground mt-0.5">{r.note}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// NAVIGATION & TOOLBAR
// ─────────────────────────────────────────────────────────────
const APP_NAV = [
  {id:"home" as Page,icon:Home,label:"Home"},
  {id:"search" as Page,icon:Search,label:"Search"},
  {id:"library" as Page,icon:Library,label:"Library"},
  {id:"settings" as Page,icon:Settings,label:"Settings"},
];
const DESKTOP_APP_NAV = [
  APP_NAV[0],
  APP_NAV[1],
  APP_NAV[2],
  {id:"listening" as Page,icon:Activity,label:"Listening"},
  APP_NAV[3],
];
const DS_NAV = [
  {id:"cover" as DSSection,icon:Sparkles,label:"Cover"},
  {id:"foundation" as DSSection,icon:Palette,label:"Foundation"},
  {id:"tokens" as DSSection,icon:Code2,label:"Tokens"},
  {id:"theme-colors" as DSSection,icon:Palette,label:"Theme Colors"},
  {id:"components" as DSSection,icon:Layers,label:"Components"},
  {id:"patterns" as DSSection,icon:LayoutDashboard,label:"Patterns"},
  {id:"compose" as DSSection,icon:Cpu,label:"Compose"},
];

function Sidebar({ page, onPage, dsSection, onDsSection, isDark, onToggleDark }: {
  page:Page; onPage:(p:Page)=>void; dsSection:DSSection; onDsSection:(s:DSSection)=>void; isDark:boolean; onToggleDark:()=>void;
}) {
  return (
    <aside className="hidden lg:flex flex-col w-56 shrink-0 bg-sidebar border-r border-border h-full overflow-y-auto hide-scrollbar">
      {/* Logo */}
      <div className="flex items-center gap-3 px-4 pt-5 pb-4 shrink-0">
        <div className="w-8 h-8 rounded-xl flex items-center justify-center shrink-0" style={{background:"linear-gradient(135deg,var(--brand-pink),var(--brand-purple))"}}>
          <Music2 className="w-4 h-4 text-white"/>
        </div>
        <div><p className="text-sm font-black text-foreground tracking-tight leading-none">MelodyTrove</p><p className="text-[9px] text-muted-foreground font-medium">One Library. Every Source.</p></div>
      </div>
      {/* App Nav */}
      <div className="px-2 mb-1">
        <p className="text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground/60 px-4 mb-1 mt-5 first:mt-0">App</p>
        {DESKTOP_APP_NAV.map(item=>{const Icon=item.icon; const active=(page===item.id||((page==="playlist"||page==="album"||page==="artist")&&item.id==="library"))&&page!=="design-system";
          return <button type="button" key={item.id} onPointerDown={preventMouseFocus} onClick={()=>onPage(item.id)} className={cn("w-full flex items-center gap-2.5 px-4 h-9 rounded-[10px] mb-0.5 text-xs font-semibold transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40",active?"bg-[var(--surface-selected)] text-primary":"text-muted-foreground hover:bg-[var(--surface-hover)] hover:text-foreground")}>
            <Icon style={{width:15,height:15}}/>{item.label}
          </button>;
        })}
      </div>
      {/* DS Nav */}
      <div className="px-2 mb-2">
        <p className="text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground/60 px-4 mb-1 mt-5">Design System</p>
        {DS_NAV.map(item=>{const Icon=item.icon; const active=page==="design-system"&&dsSection===item.id;
          return <button type="button" key={item.id} onPointerDown={preventMouseFocus} onClick={()=>{onPage("design-system");onDsSection(item.id);}} className={cn("w-full flex items-center gap-2.5 px-4 h-9 rounded-[10px] mb-0.5 text-xs font-semibold transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40",active?"bg-[var(--surface-selected)] text-primary":"text-muted-foreground hover:bg-[var(--surface-hover)] hover:text-foreground")}>
            <Icon style={{width:15,height:15}}/>{item.label}
          </button>;
        })}
      </div>
      <div className="flex-1"/>
      <div className="px-2 pb-4 shrink-0">
        <button type="button" onPointerDown={preventMouseFocus} onClick={onToggleDark} className="w-full flex items-center gap-2.5 px-4 h-9 rounded-[10px] hover:bg-[var(--surface-hover)] transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
          {isDark?<Sun style={{width:15,height:15}} className="text-muted-foreground"/>:<Moon style={{width:15,height:15}} className="text-muted-foreground"/>}
          <span className="text-xs font-semibold text-sidebar-foreground">{isDark?"Light Mode":"Dark Mode"}</span>
        </button>
      </div>
    </aside>
  );
}

function BottomNav({ page, onPage }: { page:Page; onPage:(p:Page)=>void }) {
  const items = APP_NAV;
  return (
    <nav className="relative order-last flex h-[88px] shrink-0 items-center justify-around border-t border-[var(--mobile-nav-border)] px-3 pb-[26px] lg:hidden landscape:order-first landscape:h-full landscape:w-[74px] landscape:flex-col landscape:justify-start landscape:gap-2 landscape:border-r landscape:border-t-0 landscape:px-2 landscape:pb-3 landscape:pt-4"
      style={{
        background:"var(--mobile-nav-background)",
        backdropFilter:"blur(20px) saturate(1.6)",
        WebkitBackdropFilter:"blur(20px) saturate(1.6)",
      }}>
      {items.map(item => {
        const Icon = item.icon;
        const active = page === item.id || ((page==="playlist"||page==="album"||page==="artist")&&item.id==="library");
        return (
          <button type="button" key={item.id} onPointerDown={preventMouseFocus} onClick={() => onPage(item.id)}
            className={cn("flex flex-col items-center gap-0.5 rounded-xl transition-all duration-[180ms] outline-none focus-visible:ring-2 focus-visible:ring-primary/40", active ? "text-primary" : "text-muted-foreground")}>
            <div className={cn("flex items-center justify-center w-12 h-7 rounded-full transition-all duration-[180ms]",
              active ? "bg-primary/12" : "")}>
              <Icon style={{ width:20, height:20 }}/>
            </div>
            <span className={cn("text-[10px] font-semibold transition-colors", active ? "text-primary" : "text-muted-foreground")}>
              {item.label}
            </span>
          </button>
        );
      })}
      <MobileHomeIndicator/>
    </nav>
  );
}

// ─────────────────────────────────────────────────────────────
// ROOT APP
// ─────────────────────────────────────────────────────────────
export default function App() {
  const [themeMode,setThemeMode] = useState<ThemeMode>("dark");
  const [systemIsDark,setSystemIsDark] = useState(() =>
    typeof window === "undefined" || window.matchMedia("(prefers-color-scheme: dark)").matches,
  );
  const [page,setPage] = useState<Page>("home");
  const [settingsSub,setSettingsSub] = useState<SettingsSub|null>(null);
  const [dsSection,setDsSection] = useState<DSSection>("cover");
  const [currentSong,setCurrentSong] = useState<Song|null>(SONGS[0]);
  const [isPlaying,setIsPlaying] = useState(false);
  const [playerOpen,setPlayerOpen] = useState(false);
  const [progress,setProgress] = useState(46); // demo: 1:42 of 3:42
  const [volume,setVolume] = useState(75);
  const [songIdx,setSongIdx] = useState(0);
  const [libraryTab,setLibraryTab] = useState<LibTab>("playlists");
  const [selectedPlaylist,setSelectedPlaylist] = useState<Playlist|null>(null);
  const [selectedAlbum,setSelectedAlbum] = useState<Album|null>(null);
  const [selectedArtist,setSelectedArtist] = useState<Artist|null>(null);
  const [pinnedPlaylists,setPinnedPlaylists] = useState<Playlist[]>([FAVORITE_PLAYLIST]);
  const [playlistReturnPage,setPlaylistReturnPage] = useState<Page>("library");
  const [albumReturnPage,setAlbumReturnPage] = useState<Page>("library");
  const [artistReturnPage,setArtistReturnPage] = useState<Page>("library");
  const [settingsHeaderGlassProgress,setSettingsHeaderGlassProgress] = useState(0);
  const mainScrollRef = useRef<HTMLElement>(null);

  useEffect(()=>{
    mainScrollRef.current?.scrollTo({top:0});
    if (page!=="settings") setSettingsSub(null);
  },[page]);

  useEffect(()=>{
    setSettingsHeaderGlassProgress(0);
  },[page,settingsSub]);

  useEffect(()=>{
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const updateSystemTheme = () => setSystemIsDark(mediaQuery.matches);
    mediaQuery.addEventListener("change",updateSystemTheme);
    return () => mediaQuery.removeEventListener("change",updateSystemTheme);
  },[]);

  const handlePlay = (song:Song) => { setCurrentSong(song); setIsPlaying(true); setSongIdx(SONGS.findIndex(s=>s.id===song.id)); };
  const handleNext = () => { const n=(songIdx+1)%SONGS.length; setSongIdx(n); setCurrentSong(SONGS[n]); setIsPlaying(true); };
  const handlePrev = () => { const p=(songIdx-1+SONGS.length)%SONGS.length; setSongIdx(p); setCurrentSong(SONGS[p]); setIsPlaying(true); };
  const handleOpenLibrary = (tab:LibTab) => { setLibraryTab(tab); setPage("library"); };
  const handleOpenPlaylist = (playlist:Playlist) => {
    setSelectedPlaylist(playlist);
    setPlaylistReturnPage(page==="library"?"library":"home");
    setPage("playlist");
  };
  const handleOpenAlbum = (album:Album) => {
    setSelectedAlbum(album);
    setAlbumReturnPage(page==="artist"?"artist":"library");
    setPage("album");
  };
  const handleOpenArtist = (artist:Artist) => {
    setSelectedArtist(artist);
    setArtistReturnPage(page==="library"?"library":"home");
    setPage("artist");
  };
  const handleTogglePlaylistPin = (playlist:Playlist) => {
    setPinnedPlaylists(items=>items.some(item=>item.id===playlist.id)
      ? items.filter(item=>item.id!==playlist.id)
      : [...items,playlist]);
  };

  const mobilePageTitle: Partial<Record<Page,string>> = {
    home:"Good Evening", search:"Search", library:"Library", listening:"Listening", settings:"Settings",
  };
  const dsTitles: Record<DSSection,string> = {
    cover:"Design System",foundation:"Foundation",tokens:"Tokens","theme-colors":"Theme Colors",components:"Components",patterns:"Patterns",compose:"Compose",
  };
  const settingsDetailTitle = page==="settings"&&settingsSub?SETTINGS_SUB_LABELS[settingsSub]:undefined;
  const isDark = themeMode==="system"?systemIsDark:themeMode==="dark";

  return (
    <div className={cn("flex h-screen w-screen overflow-hidden",isDark?"dark":"")}>
      <div className="relative flex h-full w-full bg-background text-foreground overflow-hidden">
        <MobileStatusBar glassProgress={page==="settings"?(settingsDetailTitle?1:settingsHeaderGlassProgress):0}/>
        <MobileLandscapeHomeIndicator/>
        <Sidebar page={page} onPage={setPage} dsSection={dsSection} onDsSection={setDsSection} isDark={isDark} onToggleDark={()=>setThemeMode(isDark?"light":"dark")}/>

        <div className="flex h-full min-w-0 flex-1 flex-col overflow-hidden max-lg:landscape:pb-[18px] max-lg:landscape:pr-16 landscape:flex-row">
          <div className="order-first flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden landscape:order-last">
          {/* Content */}
          <div className="flex flex-1 min-h-0 overflow-hidden">
            {/* Main content */}
            <main ref={mainScrollRef} className="mt-[59px] flex-1 overflow-y-auto lg:mt-0 landscape:mt-0">
              {page !== "home" && page !== "playlist" && page !== "album" && page !== "artist" && page !== "listening" && (
                <StickyPageHeader
                  title={settingsDetailTitle??(page==="design-system"?dsTitles[dsSection]:mobilePageTitle[page]||"MelodyTrove")}
                  subtitle={settingsDetailTitle?undefined:page==="design-system"?"MelodyTrove DS · v3.0":undefined}
                  onBack={settingsDetailTitle?()=>setSettingsSub(null):undefined}
                  backLabel="Back to Settings"
                  showTitleOnCollapse={Boolean(settingsDetailTitle)}
                  liquidGlass={page==="settings"}
                  collapseDisabled={Boolean(settingsDetailTitle)}
                  onCollapseProgressChange={page==="settings"&&!settingsDetailTitle?setSettingsHeaderGlassProgress:undefined}
                  className={cn(
                    "lg:hidden",
                    !settingsDetailTitle&&"pt-5 pb-3",
                    settingsDetailTitle?"px-0":page==="library"?"px-6":"px-5",
                    page==="search"&&"before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:z-10 before:h-px before:bg-background before:content-['']",
                  )}
                />
              )}
              {page==="design-system"&&(
                <>
                  <StickyPageHeader title={dsTitles[dsSection]} subtitle="MelodyTrove DS · v3.0" className="hidden lg:block px-8 py-3"/>
                  <div className="lg:hidden px-4 py-2 overflow-x-auto hide-scrollbar">
                    <div className="flex gap-2">{DS_NAV.map(s=>(
                      <button key={s.id} onClick={()=>setDsSection(s.id)} className={cn("shrink-0 px-3.5 h-8 rounded-full text-xs font-semibold transition-all",dsSection===s.id?"bg-secondary text-secondary-foreground":"bg-muted text-muted-foreground")}>{s.label}</button>
                    ))}</div>
                  </div>
                </>
              )}
              <AnimatePresence mode="wait">
                <motion.div key={page==="design-system"?`ds-${dsSection}`:page==="playlist"?`playlist-${selectedPlaylist?.id}`:page==="album"?`album-${selectedAlbum?.id}`:page==="artist"?`artist-${selectedArtist?.id}`:page} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-8}} transition={{duration:0.18,ease:"easeOut"}} className="min-h-full">
                  {page==="home"&&<HomePage onPlay={handlePlay} currentSong={currentSong} isPlaying={isPlaying} pinnedPlaylists={pinnedPlaylists} onOpenLibrary={handleOpenLibrary} onOpenPlaylist={handleOpenPlaylist} onOpenAlbum={handleOpenAlbum} onOpenArtist={handleOpenArtist} onOpenListening={()=>setPage("listening")}/>}
                  {page==="search"&&<SearchPage onPlay={handlePlay}/>}
                  {page==="library"&&<LibraryPage onPlay={handlePlay} onOpenPlaylist={handleOpenPlaylist} onOpenAlbum={handleOpenAlbum} onOpenArtist={handleOpenArtist}
                    pinnedPlaylistIds={pinnedPlaylists.map(playlist=>playlist.id)} onTogglePlaylistPin={handleTogglePlaylistPin}
                    currentSong={currentSong} isPlaying={isPlaying} tab={libraryTab} onTab={setLibraryTab}/>}
                  {page==="playlist"&&selectedPlaylist&&<PlaylistDetailPage playlist={selectedPlaylist} currentSong={currentSong} isPlaying={isPlaying} onBack={()=>setPage(playlistReturnPage)} onPlay={handlePlay}/>}
                  {page==="album"&&selectedAlbum&&<AlbumDetailPage album={selectedAlbum} currentSong={currentSong} isPlaying={isPlaying} onBack={()=>setPage(albumReturnPage)} onPlay={handlePlay}/>}
                  {page==="artist"&&selectedArtist&&<ArtistDetailPage artist={selectedArtist} currentSong={currentSong} isPlaying={isPlaying} onBack={()=>setPage(artistReturnPage)} onPlay={handlePlay} onOpenAlbum={handleOpenAlbum}/>}
                  {page==="listening"&&<ListeningPage onBack={()=>setPage("home")} onPlay={handlePlay}/>}
                  {page==="settings"&&<SettingsPage sub={settingsSub} onSubChange={setSettingsSub} themeMode={themeMode} isDark={isDark} onThemeModeChange={setThemeMode}/>}
                  {page==="design-system"&&dsSection==="cover"&&<DSCover/>}
                  {page==="design-system"&&dsSection==="foundation"&&<DSFoundation/>}
                  {page==="design-system"&&dsSection==="tokens"&&<DSTokens/>}
                  {page==="design-system"&&dsSection==="theme-colors"&&<ThemeColorDesignSpec/>}
                  {page==="design-system"&&dsSection==="components"&&<DSComponents/>}
                  {page==="design-system"&&dsSection==="patterns"&&<DSPatterns/>}
                  {page==="design-system"&&dsSection==="compose"&&<DSCompose/>}
                </motion.div>
              </AnimatePresence>
            </main>

          </div>

          {/* Mini Player */}
          <AnimatePresence>
            {currentSong&&<MiniPlayer song={currentSong} isPlaying={isPlaying} onPlayPause={()=>setIsPlaying(!isPlaying)} onNext={handleNext} onExpand={()=>setPlayerOpen(true)}/>}
          </AnimatePresence>
          </div>

          {/* Bottom Nav */}
          <BottomNav page={page} onPage={setPage}/>
        </div>
      </div>

      {/* Full Player */}
      <AnimatePresence>
        {playerOpen&&currentSong&&<FullPlayer song={currentSong} isPlaying={isPlaying} onPlay={handlePlay} onPlayPause={()=>setIsPlaying(!isPlaying)} onNext={handleNext} onPrev={handlePrev} onClose={()=>setPlayerOpen(false)} progress={progress} onSeek={setProgress} volume={volume} onVolume={setVolume}/>}
      </AnimatePresence>
    </div>
  );
}
