---
name: Miku_AI Design System
colors:
  surface: '#0b1326'
  surface-dim: '#0b1326'
  surface-bright: '#31394d'
  surface-container-lowest: '#060e20'
  surface-container-low: '#131b2e'
  surface-container: '#171f33'
  surface-container-high: '#222a3d'
  surface-container-highest: '#2d3449'
  on-surface: '#dae2fd'
  on-surface-variant: '#b9cacb'
  inverse-surface: '#dae2fd'
  inverse-on-surface: '#283044'
  outline: '#849495'
  outline-variant: '#3b494b'
  surface-tint: '#00dbe9'
  primary: '#dbfcff'
  on-primary: '#00363a'
  primary-container: '#00f0ff'
  on-primary-container: '#006970'
  inverse-primary: '#006970'
  secondary: '#ddb7ff'
  on-secondary: '#490080'
  secondary-container: '#6f00be'
  on-secondary-container: '#d6a9ff'
  tertiary: '#d3fff6'
  on-tertiary: '#003731'
  tertiary-container: '#66efdb'
  on-tertiary-container: '#006b60'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#7df4ff'
  primary-fixed-dim: '#00dbe9'
  on-primary-fixed: '#002022'
  on-primary-fixed-variant: '#004f54'
  secondary-fixed: '#f0dbff'
  secondary-fixed-dim: '#ddb7ff'
  on-secondary-fixed: '#2c0051'
  on-secondary-fixed-variant: '#6900b3'
  tertiary-fixed: '#71f8e4'
  tertiary-fixed-dim: '#4fdbc8'
  on-tertiary-fixed: '#00201c'
  on-tertiary-fixed-variant: '#005048'
  background: '#0b1326'
  on-background: '#dae2fd'
  surface-variant: '#2d3449'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '400'
    lineHeight: 14px
    letterSpacing: 0.8px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 0.75rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system establishes a high-performance, technical HUD aesthetic tailored for real-time assistive intelligence. Designed primarily as an ambient Android utility overlay, the visual language balances unobtrusive background persistence with surgical readability under critical, fast-moving display conditions (such as gaming, video playback, and live documents).

The brand identity combines the disciplined surface architecture of Material Design 3 with a forward-facing cyber-utility ethos:
- **Style Archetype:** Cyber-Functional Glassmorphism. Deep, slate-tinted canvas backdrops anchor lightweight acrylic surfaces, punctuated by calibrated electric cyan focal points and violet-tinted computational accents.
- **Visual Personality:** Precise, responsive, transparent, and non-blocking. UI structures should feel weightless—floating naturally above host applications like a heads-up optical display.
- **Emotional Impact:** Absolute confidence, cutting-edge speed, and non-distracting visual elegance.

## Colors

The color palette is architected specifically around OLED power efficiency and high dynamic range contrast for instant recognition during overlay projection:

- **Primary (`#00F0FF`):** Electric Cyan. Used for active screen scanning boundaries, primary floating triggers, critical confirmed translations, and active toggle states. Radiates a calibrated, subtle light bloom when active.
- **Secondary (`#A855F7`):** Cyber Purple. Applied to AI inference indicators, pipeline processing counters, model switches, and contextual confidence scores.
- **Tertiary (`#14B8A6`):** Technical Teal. Deployed for stabilized state notifications, network throughput readouts, and latency monitors.
- **Neutral System Surfaces:**
  - `Surface Scrim / Root`: `#0B0F19` (Extreme low-luminance deep charcoal for maximum contrast and battery preservation).
  - `Surface Base`: `#0F172A` (Rich slate backdrop).
  - `Surface Container`: `#1E293B` at variable opacities (80% for modal panels, 45% for passive floating HUDs).
  - `Surface High-Light`: `#334155` (Subtle divider tracks and inactive perimeter rings).
- **Text & Contrast Hierarchy:**
  - `Text High-Emphasis`: `#F8FAFC` (98% luminance white-slate).
  - `Text Medium-Emphasis`: `#94A3B8` (Cool sub-label gray).
  - `Text Disabled / Ghost`: `#475569`.

## Typography

Typography establishes an immediate division between operational UI controls, translated text content, and system telemetry:

- **Headlines & Structural Branding (`Plus Jakarta Sans`):** Delivers clean geometry with slightly rounded counters that soften the technical precision of the tool, matching Android Material 3 geometric principles.
- **Body & Target Copy (`Inter`):** Optimizes cross-language legibility at small scale within restricted overlay cards. Used for real-time OCR results, source/target text panes, and user preference descriptions.
- **HUD Telemetry & Badges (`JetBrains Mono`):** Applied to latency indicators (e.g., `18ms`), optical bounding coordinates, model engines, and token rates. Monospacing eliminates horizontal layout jitter during real-time updates.

## Layout & Spacing

Because this system runs as both a native configuration host app and a persistent Android System Alert Window (Floating Overlay), spatial rules change dynamically by context:

- **Native App Mode:** Standard 4-column (Mobile) and 8-column (Tablet) fluid grid with a tight 16px outer margin. Spacing uses an 8pt base grid with a 4pt sub-unit for compact control alignment.
- **Floating HUD Overlay Mode:** Unanchored, freeform contextual layout conforming to Android Display Cutouts and WindowMetrics safe insets. Spacing relies on compact tokens (`space-xs` through `space-md`) to ensure minimal screen occlusion.
- **Reflow Rules:**
  - Floating translation bubbles default to dynamic max-widths (capped at 85% screen width on portrait, 45% on landscape).
  - Action pills and speed controls collapse into an iconic radial cluster when translation is idle.

## Elevation & Depth

Hierarchy is established via **Frosted Acrylic Glassmorphism** combined with **Tonal Luminosity Borders**, entirely replacing legacy drop-shadows with neon edge diffusions:

1. **Passive Layer (Host Screen Backplane):** `blur(0px)` with variable alpha tinting (`#0B0F19` at 40% opacity) during area-selection states.
2. **Surface Tier 1 (Docked HUD & Translation Strips):** Background `#0F172A` at 65% opacity with `backdrop-filter: blur(16px)`. Border is a crisp 1px stroke using `#334155` at 50% opacity.
3. **Surface Tier 2 (Floating Inspect Windows & Context Menus):** Background `#1E293B` at 80% opacity with `backdrop-filter: blur(24px)`. Border is 1px tinted `#00F0FF` at 30% opacity, paired with an ambient perimeter glow of `box-shadow: 0 0 16px rgba(0, 240, 255, 0.15)`.
4. **Active Selection & Bounding Boxes (L3 Overlay):** Fully transparent interior with 1.5px solid `#00F0FF` outline and an active glow of `0 0 8px rgba(0, 240, 255, 0.45)`.

## Shapes

The interface embraces a strict **Pill-shaped (Level 3)** geometry to provide comfortable thumb ergonomics and visual distinction against underlying square/rectangular third-party video, manga, and gaming content:

- **Floating Controllers & Quick Toggles:** Full pill encapsulation (`border-radius: 9999px`), ensuring no sharp visual noise interferes with the underlying screen.
- **Translation Popovers & Data Sheets:** Generous rounded profiles (`rounded-xl`, 24px/1.5rem to 32px/2rem), softening the technical aesthetic.
- **Sub-elements & Action Chips:** Pill-shaped capsules for language switchers, recognition badges, and latency chips.

## Components

### Floating Quick HUD & Action Button (FAB)
- **Visuals:** Double-ring pill button. Interior surface `#0F172A` (80% opacity, `blur(12px)`). Outer border: 1.5px `#00F0FF` glowing contour.
- **States:** Idle state pulses an electric cyan telemetry dot. Active scanning transforms the border into a rotating dual-gradient ring (`#00F0FF` transitioning to `#A855F7`).

### Translation Panels & Replacement Overlays
- **Structure:** Acrylic container pinned over target text.
- **Styling:** Surface `#0F172A` at 85% opacity, `backdrop-filter: blur(20px)`. 1px stroke of `rgba(255, 255, 255, 0.08)`.
- **Typography:** Source preview in `label-sm` (`#94A3B8`), translated output in `body-lg` (`#F8FAFC`, semi-bold).

### Technical Status Badges & Chips
- **Structure:** Ultra-compact inline pill containing a 4px monospaced status indicator.
- **Styling:** Inactive chips use `#1E293B` with `#64748B` text. Active AI/Engine chips switch to `rgba(168, 85, 247, 0.15)` background, `#A855F7` text, and a 1px border of `rgba(168, 85, 247, 0.4)`.

### Buttons
- **Primary:** Full electric cyan fill (`#00F0FF`) with dark obsidian text (`#0B0F19`, font weight 600). Pill radius, zero inner shadow, subtle outer cyan aura on press.
- **Secondary (Tonal Glass):** `#1E293B` at 70% opacity, border 1px `rgba(0, 240, 255, 0.3)`, text `#00F0FF`.
- **Destructive/Halt:** Tinted ruby red background (`rgba(239, 68, 68, 0.15)`) with `#EF4444` border.

### Input Fields & Selectors
- **Styling:** Recessed `#0B0F19` field with 1px border in `#334155`. Focused state illuminates the perimeter in `#00F0FF` with an elevated 12px backdrop blur.
- **Typography:** Monospaced placeholder for translation keys/regex rules; Inter for contextual natural language notes.

### Checkboxes, Radios, & Micro-Switches
- **Switches:** Android M3 thumb-and-track format with high-contrast cyber styling. Unchecked track `#1E293B`; checked track `#00F0FF` with deep obsidian thumb (`#0B0F19`).
- **Checkboxes:** Rounded 6px square with 1.5px `#00F0FF` outline and high-contrast checkmark vector.