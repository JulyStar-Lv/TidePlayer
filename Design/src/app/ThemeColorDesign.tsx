import { type CSSProperties, useEffect, useMemo, useState } from "react";
import { AlertCircle, Check, Plus, Trash2, X } from "lucide-react";

const PRESET_COLORS = [
  { name: "Brand Pink", value: "#FF5B8A" },
  { name: "Purple", value: "#7A6CFF" },
  { name: "Blue", value: "#3D9AFF" },
  { name: "Orange", value: "#FF8A3D" },
  { name: "Green", value: "#3DCA8A" },
  { name: "Yellow", value: "#FFD93D" },
] as const;

const CUSTOM_COLOR_LIMIT = 12;

type ArtworkState = "available" | "loading" | "missing" | "failed";

function normalizeHex(value: string) {
  const body = value.trim().replace(/^#/, "").toUpperCase();
  return /^[0-9A-F]{6}$/.test(body) ? `#${body}` : null;
}

function contrastColor(hex: string) {
  const normalized = normalizeHex(hex) ?? "#FF5B8A";
  const r = parseInt(normalized.slice(1, 3), 16) / 255;
  const g = parseInt(normalized.slice(3, 5), 16) / 255;
  const b = parseInt(normalized.slice(5, 7), 16) / 255;
  const channel = (value: number) => value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  const luminance = 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
  const darkInkLuminance = 0.004;
  const darkInkContrast = (luminance + 0.05) / (darkInkLuminance + 0.05);
  const whiteContrast = 1.05 / (luminance + 0.05);
  return darkInkContrast >= whiteContrast ? "#0D0B18" : "#FFFFFF";
}

function primaryButtonColors(seed: string, dark: boolean) {
  // Match the sampled Apple Music primary action colors for the Brand Pink theme.
  if (normalizeHex(seed) === "#FF5B8A") {
    return dark
      ? { background: "#FA2E48", foreground: "#FFFFFF" }
      : { background: "#FA233B", foreground: "#FFFFFF" };
  }
  return { background: seed, foreground: contrastColor(seed) };
}

function hsvToHex(hue: number, saturation: number, value: number) {
  const c = value * saturation;
  const x = c * (1 - Math.abs((hue / 60) % 2 - 1));
  const m = value - c;
  let rgb: [number, number, number];
  if (hue < 60) rgb = [c, x, 0];
  else if (hue < 120) rgb = [x, c, 0];
  else if (hue < 180) rgb = [0, c, x];
  else if (hue < 240) rgb = [0, x, c];
  else if (hue < 300) rgb = [x, 0, c];
  else rgb = [c, 0, x];
  return `#${rgb.map(channel => Math.round((channel + m) * 255).toString(16).padStart(2, "0")).join("").toUpperCase()}`;
}

function hexToHsv(hex: string) {
  const normalized = normalizeHex(hex) ?? "#FF5B8A";
  const r = parseInt(normalized.slice(1, 3), 16) / 255;
  const g = parseInt(normalized.slice(3, 5), 16) / 255;
  const b = parseInt(normalized.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const delta = max - min;
  let hue = 0;
  if (delta !== 0) {
    if (max === r) hue = 60 * (((g - b) / delta) % 6);
    else if (max === g) hue = 60 * ((b - r) / delta + 2);
    else hue = 60 * ((r - g) / delta + 4);
  }
  if (hue < 0) hue += 360;
  return { hue, saturation: max === 0 ? 0 : delta / max, value: max };
}

function ColorSwatch({
  color,
  label,
  selected = false,
  removable = false,
  disabled = false,
  onSelect,
  onRemove,
}: {
  color: string;
  label: string;
  selected?: boolean;
  removable?: boolean;
  disabled?: boolean;
  onSelect?: () => void;
  onRemove?: () => void;
}) {
  return (
    <div className="relative flex min-w-0 flex-col items-center gap-1.5">
      <button
        type="button"
        aria-label={`${label}, ${color}${selected ? ", selected" : ""}`}
        aria-pressed={selected}
        disabled={disabled}
        onClick={onSelect}
        className="group relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full border-2 border-transparent outline-none transition-[transform,box-shadow,opacity] duration-180 hover:scale-[1.04] active:scale-95 focus-visible:border-foreground focus-visible:ring-2 focus-visible:ring-primary/40 disabled:cursor-not-allowed disabled:opacity-35"
        style={{
          backgroundColor: color,
          boxShadow: selected ? `0 0 0 3px var(--card), 0 0 0 5px ${color}` : "0 1px 5px rgba(13,11,24,.18)",
        }}
      >
        {selected && <Check aria-hidden="true" className="h-5 w-5" strokeWidth={3} style={{ color: contrastColor(color) }}/>}
      </button>
      <span className="max-w-[64px] truncate text-[9px] text-muted-foreground">{label}</span>
      {removable && (
        <button
          type="button"
          aria-label={`Remove ${label}`}
          onClick={onRemove}
          className="absolute -right-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full border border-border bg-card text-muted-foreground shadow-sm outline-none hover:text-destructive focus-visible:ring-2 focus-visible:ring-primary/40"
        >
          <X className="h-3 w-3"/>
        </button>
      )}
    </div>
  );
}

function HsvPicker({
  color,
  onChange,
}: {
  color: string;
  onChange: (color: string) => void;
}) {
  const hsv = useMemo(() => hexToHsv(color), [color]);
  const update = (hue: number, saturation: number, value: number) => onChange(hsvToHex(hue, saturation, value));

  return (
    <div className="space-y-3">
      <div
        role="slider"
        aria-label="Saturation and brightness"
        aria-valuetext={`${Math.round(hsv.saturation * 100)}% saturation, ${Math.round(hsv.value * 100)}% brightness`}
        tabIndex={0}
        className="relative h-40 w-full cursor-crosshair overflow-hidden rounded-2xl border border-border outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
        style={{
          backgroundColor: `hsl(${hsv.hue} 100% 50%)`,
          backgroundImage: "linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, transparent)",
        }}
        onPointerDown={event => {
          const target = event.currentTarget;
          target.setPointerCapture(event.pointerId);
          const rect = target.getBoundingClientRect();
          update(hsv.hue, Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)), Math.max(0, Math.min(1, 1 - (event.clientY - rect.top) / rect.height)));
        }}
        onPointerMove={event => {
          if (!event.currentTarget.hasPointerCapture(event.pointerId)) return;
          const rect = event.currentTarget.getBoundingClientRect();
          update(hsv.hue, Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)), Math.max(0, Math.min(1, 1 - (event.clientY - rect.top) / rect.height)));
        }}
        onKeyDown={event => {
          const step = event.shiftKey ? 0.1 : 0.02;
          if (event.key === "ArrowLeft") update(hsv.hue, Math.max(0, hsv.saturation - step), hsv.value);
          else if (event.key === "ArrowRight") update(hsv.hue, Math.min(1, hsv.saturation + step), hsv.value);
          else if (event.key === "ArrowUp") update(hsv.hue, hsv.saturation, Math.min(1, hsv.value + step));
          else if (event.key === "ArrowDown") update(hsv.hue, hsv.saturation, Math.max(0, hsv.value - step));
          else return;
          event.preventDefault();
        }}
      >
        <span
          aria-hidden="true"
          className="absolute h-5 w-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] border-white shadow-[0_0_0_2px_rgba(0,0,0,.72)]"
          style={{ left: `${hsv.saturation * 100}%`, top: `${(1 - hsv.value) * 100}%` }}
        />
      </div>
      <label className="block">
        <span className="mb-1.5 block text-[11px] font-semibold text-muted-foreground">Hue · {Math.round(hsv.hue)}°</span>
        <input
          aria-label="Hue"
          type="range"
          min="0"
          max="360"
          value={Math.round(hsv.hue)}
          onChange={event => update(Number(event.target.value), hsv.saturation, hsv.value)}
          className="h-8 w-full cursor-pointer appearance-none rounded-full border-4 border-card outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
          style={{ background: "linear-gradient(90deg,#F44,#FF4,#4F4,#4FF,#44F,#F4F,#F44)" }}
        />
      </label>
    </div>
  );
}

function ThemePreview({ color, dark = false }: { color: string; dark?: boolean }) {
  const foreground = dark ? "#F0EDF8" : "#0D0B18";
  const muted = dark ? "#9B97B0" : "#6B6880";
  const background = dark ? "#0C0A14" : "#F4F2FA";
  const card = dark ? "#161224" : "#FFFFFF";
  const primaryButton = primaryButtonColors(color, dark);
  return (
    <div className="rounded-[22px] border border-border p-3" style={{ color: foreground, background }}>
      <div className="mb-3 flex items-start justify-between gap-3">
        <div><p className="text-[13px] font-bold">Theme preview</p><p className="text-[10px]" style={{ color: muted }}>{dark ? "Dark" : "Light"} scheme generated from the seed</p></div>
        <span className="h-7 w-7 rounded-full" style={{ backgroundColor: color }}/>
      </div>
      <div className="rounded-2xl p-3" style={{ background: card }}>
        <p className="text-[12px] font-semibold">Selected playlist</p>
        <p className="mb-3 text-[10px]" style={{ color: muted }}>Secondary text stays readable</p>
        <div className="mb-3 h-1.5 overflow-hidden rounded-full" style={{ background: `${color}32` }}>
          <div className="h-full w-2/3 rounded-full" style={{ background: color }}/>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="h-8 rounded-full px-3 text-[11px] font-bold" style={{ background: primaryButton.background, color: primaryButton.foreground }}>Primary</button>
          <button type="button" className="h-8 rounded-full border px-3 text-[11px] font-bold" style={{ borderColor: `${color}88`, color }}>Secondary</button>
        </div>
      </div>
    </div>
  );
}

export function ThemeColorPickerDialog({
  open,
  savedColor,
  customColors,
  onClose,
  onApply,
  onCustomColorsChange,
}: {
  open: boolean;
  savedColor: string;
  customColors: string[];
  onClose: () => void;
  onApply: (color: string) => void;
  onCustomColorsChange: (colors: string[]) => void;
}) {
  const [draftColor, setDraftColor] = useState(savedColor);
  const [hexInput, setHexInput] = useState(savedColor);
  const [applied, setApplied] = useState(false);
  if (!open) return null;

  const normalized = normalizeHex(hexInput);
  const duplicate = customColors.some(value => value === draftColor) || PRESET_COLORS.some(item => item.value === draftColor);
  const atLimit = customColors.length >= CUSTOM_COLOR_LIMIT;
  const lightPrimaryButton = primaryButtonColors(draftColor, false);
  const darkPrimaryButton = primaryButtonColors(draftColor, true);
  const primaryButtonStyle = {
    "--seed-button-light": lightPrimaryButton.background,
    "--seed-button-light-foreground": lightPrimaryButton.foreground,
    "--seed-button-dark": darkPrimaryButton.background,
    "--seed-button-dark-foreground": darkPrimaryButton.foreground,
  } as CSSProperties;

  const updateDraft = (color: string) => {
    setDraftColor(color);
    setHexInput(color);
    setApplied(false);
  };

  return (
    <div role="presentation" className="fixed inset-0 z-[130] flex items-center justify-center bg-black/45 p-3 sm:p-4" onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-labelledby="theme-color-dialog-title" className="flex max-h-[calc(100vh-24px)] w-full max-w-[760px] flex-col overflow-hidden rounded-[28px] border border-border bg-card shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div>
            <h2 id="theme-color-dialog-title" className="text-[18px] font-bold text-foreground">Choose theme color</h2>
            <p className="text-[11px] text-muted-foreground">Preview locally. Saved only when Apply color is pressed.</p>
          </div>
          <button type="button" aria-label="Close color picker" onClick={onClose} className="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary/40"><X className="h-5 w-5"/></button>
        </div>

        <div className="overflow-y-auto px-5 py-4">
          <div className="grid gap-5 md:grid-cols-[minmax(0,1.15fr)_minmax(240px,.85fr)]">
            <div className="min-w-0 space-y-5">
              <section>
                <p className="mb-2 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Current color</p>
                <div className="flex items-center gap-3 rounded-2xl bg-muted/60 p-3">
                  <span className="h-10 w-10 rounded-full border border-border shadow-sm" style={{ backgroundColor: draftColor }}/>
                  <div><p className="font-mono text-[14px] font-semibold text-foreground">{draftColor}</p><p className="text-[10px] text-muted-foreground">{draftColor === savedColor ? "Saved color" : "Unsaved preview"}</p></div>
                </div>
              </section>

              <section>
                <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Preset colors</p>
                <div className="flex flex-wrap gap-3">
                  {PRESET_COLORS.map(item => <ColorSwatch key={item.value} color={item.value} label={item.name} selected={draftColor === item.value} onSelect={() => updateDraft(item.value)}/>)}
                </div>
              </section>

              <section>
                <div className="mb-3 flex items-end justify-between gap-3">
                  <p className="text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Saved colors</p>
                  <span className="text-[10px] text-muted-foreground">{customColors.length}/{CUSTOM_COLOR_LIMIT}</span>
                </div>
                {customColors.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-border px-4 py-5 text-center text-[11px] text-muted-foreground">No saved custom colors yet.</div>
                ) : (
                  <div className="flex flex-wrap gap-3">
                    {customColors.map((color, index) => <ColorSwatch key={`${color}-${index}`} color={color} label={`Custom ${index + 1}`} removable selected={draftColor === color} onSelect={() => updateDraft(color)} onRemove={() => onCustomColorsChange(customColors.filter((_, itemIndex) => itemIndex !== index))}/>)}
                  </div>
                )}
                <button type="button" disabled={duplicate || atLimit || !normalized} onClick={() => onCustomColorsChange([...customColors, draftColor])} className="mt-3 flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-muted px-4 text-[12px] font-semibold text-foreground outline-none hover:bg-muted/80 focus-visible:ring-2 focus-visible:ring-primary/40 disabled:cursor-not-allowed disabled:opacity-40">
                  <Plus className="h-4 w-4"/>{duplicate ? "Already in palette" : atLimit ? "Palette limit reached" : "Add to palette"}
                </button>
              </section>

              <section>
                <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Custom color · HSV</p>
                <HsvPicker color={draftColor} onChange={updateDraft}/>
              </section>

              <section>
                <label htmlFor="theme-hex" className="mb-2 block text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Color value</label>
                <div className={`flex h-12 items-center rounded-2xl border px-3 ${normalized ? "border-border bg-muted/50 focus-within:ring-2 focus-within:ring-primary/30" : "border-destructive bg-destructive/5"}`}>
                  <span className="mr-1 font-mono text-[14px] text-muted-foreground">#</span>
                  <input id="theme-hex" value={hexInput.replace(/^#/, "")} maxLength={6} spellCheck={false} onChange={event => {
                    const value = `#${event.target.value.toUpperCase()}`;
                    setHexInput(value);
                    const valid = normalizeHex(value);
                    if (valid) setDraftColor(valid);
                    setApplied(false);
                  }} className="min-w-0 flex-1 bg-transparent font-mono text-[14px] font-semibold uppercase text-foreground outline-none"/>
                </div>
                {!normalized && <p role="alert" className="mt-2 flex items-center gap-1.5 text-[11px] text-destructive"><AlertCircle className="h-3.5 w-3.5"/>Enter six hexadecimal characters, for example FF5B8A.</p>}
              </section>
            </div>

            <div className="space-y-3 md:sticky md:top-0 md:self-start">
              <ThemePreview color={draftColor}/>
              <ThemePreview color={draftColor} dark/>
              <p className="rounded-2xl bg-muted/60 px-3 py-2 text-[10px] leading-relaxed text-muted-foreground">The seed generates the complete light and dark Miuix schemes. Error and diagnostic colors keep their semantic meaning.</p>
            </div>
          </div>
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-border px-5 py-4 sm:flex-row sm:justify-end">
          <button type="button" onClick={onClose} className="min-h-12 rounded-full bg-[var(--button-secondary)] px-5 text-[13px] font-semibold text-[var(--button-secondary-foreground)] outline-none focus-visible:ring-2 focus-visible:ring-primary/40">Cancel</button>
          <button type="button" disabled={!normalized} onClick={() => { onApply(draftColor); setApplied(true); }} className="min-h-12 rounded-full bg-[var(--seed-button-light)] px-5 text-[13px] font-bold text-[var(--seed-button-light-foreground)] outline-none focus-visible:ring-2 focus-visible:ring-primary/40 disabled:opacity-40 dark:bg-[var(--seed-button-dark)] dark:text-[var(--seed-button-dark-foreground)]" style={primaryButtonStyle}>
            {applied ? "Color applied" : "Apply color"}
          </button>
        </div>
      </div>
    </div>
  );
}

export function AppearanceColorSettings({
  artworkEnabled,
  artworkState = "available",
  manualColor,
  onArtworkEnabledChange,
  onOpenPicker,
}: {
  artworkEnabled: boolean;
  artworkState?: ArtworkState;
  manualColor: string;
  onArtworkEnabledChange: (enabled: boolean) => void;
  onOpenPicker: () => void;
}) {
  const summary = "Adjust app colors from the current song artwork";
  const fallback = artworkState === "available" ? "Artwork seed is active"
    : artworkState === "loading" ? "Loading artwork · keeping the last valid seed"
    : artworkState === "failed" ? "Artwork color failed · using your saved color"
    : "No artwork · using your saved color";
  const themeColorSummary = artworkEnabled
    ? `${fallback} · Turn off Artwork color to edit`
    : `Currently using ${manualColor}`;

  return (
    <div className="overflow-hidden rounded-[24px] border border-border bg-card">
      <div className="flex min-h-[68px] items-center gap-3 px-4 py-3">
        <div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Artwork color</p><p className="mt-0.5 text-[12px] text-muted-foreground">{summary}</p></div>
        <button type="button" role="switch" aria-checked={artworkEnabled} aria-label="Artwork color" onClick={() => onArtworkEnabledChange(!artworkEnabled)} className={`relative h-8 w-[52px] rounded-full outline-none transition-colors focus-visible:ring-2 focus-visible:ring-primary/40 ${artworkEnabled ? "bg-primary" : "bg-muted"}`}><span className={`absolute left-0 top-1 h-6 w-6 rounded-full bg-white shadow transition-transform ${artworkEnabled ? "translate-x-6" : "translate-x-1"}`}/></button>
      </div>
      <button type="button" disabled={artworkEnabled} onClick={onOpenPicker} className="flex min-h-[68px] w-full items-center gap-3 border-t border-border px-4 py-3 text-left outline-none hover:bg-muted/35 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary/40 disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent">
        <div className="min-w-0 flex-1"><p className="text-[15px] font-medium text-foreground">Theme color</p><p className="mt-0.5 text-[12px] text-muted-foreground">{themeColorSummary}</p></div>
        <span aria-hidden="true" className="h-8 w-8 rounded-full border-2 border-card shadow-[0_0_0_1px_var(--border)]" style={{ backgroundColor: manualColor }}/>
      </button>
    </div>
  );
}

function StateCard({ title, description, color = "#FF5B8A" }: { title: string; description: string; color?: string }) {
  return <div className="rounded-2xl border border-border bg-card p-3"><div className="mb-2 flex items-center gap-2"><span className="h-5 w-5 rounded-full" style={{ background: color }}/><p className="text-[12px] font-semibold text-foreground">{title}</p></div><p className="text-[10px] leading-relaxed text-muted-foreground">{description}</p></div>;
}

export function ThemeColorDesignSpec() {
  const [manualColor, setManualColor] = useState("#FF5B8A");
  const [customColors, setCustomColors] = useState(["#C55BFF", "#12B8A6"]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [artworkEnabled, setArtworkEnabled] = useState(true);
  useEffect(() => {
    if (artworkEnabled) setDialogOpen(false);
  }, [artworkEnabled]);

  return (
    <div className="space-y-8 px-4 py-2 pb-10">
      <section>
        <div className="mb-4 rounded-[28px] border border-border bg-card p-5">
          <p className="text-[16px] font-bold text-foreground">Theme color · source design</p>
          <p className="mt-1 text-[12px] leading-relaxed text-muted-foreground">Artwork Theme Seed → Manual Theme Seed fallback. Turn Artwork color off before editing the manual seed.</p>
        </div>
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Appearance settings · live prototype</p>
        <AppearanceColorSettings artworkEnabled={artworkEnabled} manualColor={manualColor} onArtworkEnabledChange={setArtworkEnabled} onOpenPicker={() => setDialogOpen(true)}/>
      </section>

      <section>
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Artwork state matrix</p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <StateCard title="On · available" description="Use the artwork seed." color="#3D9AFF"/>
          <StateCard title="On · loading" description="Keep the previous valid seed; otherwise use the manual seed." color="#7A6CFF"/>
          <StateCard title="On · no artwork" description="Use the saved manual seed."/>
          <StateCard title="On · failed" description="Use the saved manual seed and show the fallback summary." color="#FF8A3D"/>
          <StateCard title="Off · any" description="Always use the saved manual seed."/>
        </div>
        <p className="mt-3 rounded-2xl border border-border bg-card px-4 py-3 text-[11px] leading-relaxed text-muted-foreground">Mutual exclusion: while Artwork color is on, the Theme color row remains visible as fallback status but is disabled. Turning Artwork color off enables the row and its picker.</p>
      </section>

      <section>
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Swatch component states · 48 px / 48 px touch target</p>
        <div className="flex flex-wrap gap-5 rounded-[24px] border border-border bg-card p-5">
          <ColorSwatch color="#FF5B8A" label="Default"/>
          <ColorSwatch color="#7A6CFF" label="Hover"/>
          <ColorSwatch color="#3D9AFF" label="Pressed"/>
          <ColorSwatch color="#FF8A3D" label="Focused"/>
          <ColorSwatch color="#3DCA8A" label="Selected" selected/>
          <ColorSwatch color="#C55BFF" label="Removable" removable onRemove={() => {}}/>
          <ColorSwatch color="#FFD93D" label="Built-in"/>
          <ColorSwatch color="#9B97B0" label="Disabled" disabled/>
        </div>
      </section>

      <section>
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Responsive layout contract</p>
        <div className="grid gap-3 md:grid-cols-3">
          <StateCard title="Compact · phone" description="16 px edge margin, near-full-width modal, vertical actions, wrapping swatches, one-column picker and preview, scrollable content."/>
          <StateCard title="Medium · tablet" description="Centered modal, maximum width 640 px, more swatch columns, horizontal actions, grouped picker and preview." color="#7A6CFF"/>
          <StateCard title="Expanded · desktop" description="Centered 760 px modal, two columns, bounded height, mouse hover/drag and visible keyboard focus." color="#3D9AFF"/>
        </div>
      </section>

      <section>
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Seed contrast checks</p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {["#FF5B8A", "#FFD93D", "#3D9AFF"].flatMap(color => [false, true].map(dark => <ThemePreview key={`${color}-${dark}`} color={color} dark={dark}/>))}
        </div>
      </section>

      <section>
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[.12em] text-muted-foreground">Picker validation states</p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StateCard title="Initial" description="Starts from the persisted manual color."/>
          <StateCard title="Dragging" description="Preview updates locally; DataStore remains unchanged." color="#7A6CFF"/>
          <StateCard title="Invalid Hex" description="Inline message plus disabled Apply action." color="#EF4444"/>
          <StateCard title="Saved" description="Apply persists the color and closes or confirms success." color="#3DCA8A"/>
          <StateCard title="Duplicate" description="Add to palette is disabled with an explicit label." color="#FFD93D"/>
          <StateCard title="Limit reached" description={`At ${CUSTOM_COLOR_LIMIT} custom colors, adding is disabled.`} color="#FF8A3D"/>
          <StateCard title="Delete current favorite" description="Removes only the saved swatch; the active theme seed does not reset." color="#C55BFF"/>
          <StateCard title="Cancel" description="Discard the draft and retain the persisted color."/>
        </div>
      </section>

      <ThemeColorPickerDialog open={dialogOpen} savedColor={manualColor} customColors={customColors} onClose={() => setDialogOpen(false)} onApply={color => { setManualColor(color); setDialogOpen(false); }} onCustomColorsChange={setCustomColors}/>
    </div>
  );
}
