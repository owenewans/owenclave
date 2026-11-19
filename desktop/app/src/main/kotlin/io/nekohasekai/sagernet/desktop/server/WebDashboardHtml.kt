package io.nekohasekai.sagernet.desktop.server

/**
 * Self-contained, zero-CDN-dependency Owenclave Material 3 Expressive (`M3E`) Web UI served at `/ui/`.
 *
 * Mirrors the visual identity, palettes (`Color.kt`), typography (`Google Sans Flex` with `ROND 100`
 * from `Type.kt`), `AppLogo` organic Duster layers (`AppLogo.kt`), `ShapedIcon` polygons (`ExpressiveComponents.kt`),
 * `ProfileCard` / `GroupCard` geometry, grouped M3E item stacks, blur-scrim dialogs, and the floating
 * `UnifiedBottomBar` (`ComposeMainActivity.kt`) of the native Owenclave app.
 *
 * Uses strict DOM/SVG node construction (`textContent`) rather than HTML string interpolation
 * so untrusted subscription/profile/rule names cannot trigger XSS.
 */
object WebDashboardHtml {

    const val FAVICON_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="84 84 600 600">
  <defs>
    <clipPath id="badge">
      <rect x="100" y="100" width="568" height="568" rx="128" ry="128"/>
    </clipPath>
  </defs>
  <g clip-path="url(#badge)">
    <rect x="100" y="100" width="568" height="568" fill="#D8DEE5"/>
    <g transform="translate(147.2, 147.2) scale(1.85, 1.85)">
      <path fill="#4A5866" d="M 118.00 -2.60 C 143.13 0.07 165.90 43.45 183.37 67.87 C 200.84 92.29 227.50 122.36 222.80 143.92 C 218.10 165.47 181.69 182.41 155.18 197.20 C 128.67 211.99 87.64 241.49 63.75 232.66 C 39.85 223.83 17.00 174.37 11.81 144.24 C 6.62 114.11 14.89 76.36 32.59 51.89 C 50.29 27.42 92.87 -5.26 118.00 -2.60 Z"/>
      <path fill="#5B6B7B" fill-opacity="0.92" d="M 140.00 49.04 C 169.05 49.44 211.58 37.24 225.94 52.06 C 240.29 66.89 226.29 109.53 226.12 138.00 C 225.94 166.47 239.25 203.70 224.89 222.89 C 210.54 242.09 167.52 253.95 140.00 253.17 C 112.48 252.39 75.15 237.40 59.79 218.21 C 44.44 199.01 49.20 166.09 47.85 138.00 C 46.49 109.91 36.30 64.48 51.65 49.65 C 67.01 34.83 110.95 48.64 140.00 49.04 Z"/>
    </g>
    <path fill="#DCE4EC" fill-rule="evenodd" d="M 384 298.9 C 431.00073 298.9 469.1 336.99927 469.1 384 C 469.1 431.00073 431.00073 469.1 384 469.1 C 336.99927 469.1 298.9 431.00073 298.9 384 C 298.9 336.99927 336.99927 298.9 384 298.9 Z M 384 335.9 C 410.56563 335.9 432.1 357.43437 432.1 384 C 432.1 410.56563 410.56563 432.1 384 432.1 C 357.43437 432.1 335.9 410.56563 335.9 384 C 335.9 357.43437 357.43437 335.9 384 335.9 Z"/>
  </g>
</svg>"""

    val INDEX_HTML: String = """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Owenclave Web Console</title>
<link rel="icon" type="image/svg+xml" href="/ui/favicon.svg">
<style>
  @font-face {
    font-family: 'Google Sans Flex';
    src: url('/ui/fonts/google-sans-flex.ttf') format('truetype');
    font-weight: 100 900;
    font-display: swap;
  }

  :root {
    --bg: #1f1519;
    --surface: #170f13;
    --surface-low: #23181d;
    --surface-high: #2d1f25;
    --surface-bright: #3a2931;
    --on-surface: #f0e0e5;
    --on-surface-variant: #d5c2c7;
    --primary: #ffb1c8;
    --on-primary: #5e1133;
    --primary-container: #7e2949;
    --on-primary-container: #ffd9e2;
    --secondary-container: #562337;
    --on-secondary-container: #ffd9e2;
    --tertiary-container: #633c5c;
    --on-tertiary-container: #ffd7f0;
    --outline: #9e8d91;
    --outline-variant: #514347;
    --error: #ffb4ab;
    --on-error: #690005;
    --error-container: #93000a;
    --on-error-container: #ffdad6;
    --ok: #6dd58c;
    --ok-container: #133822;
    --on-ok-container: #b7f3c6;
    --warn: #f7be57;
    --warn-container: #402d05;
    --on-warn-container: #ffdf9e;
    --shadow-bar: 0 14px 38px rgba(0, 0, 0, 0.46), 0 2px 10px rgba(0, 0, 0, 0.28);
    --font-sans: 'Google Sans Flex', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    --ease-m3: cubic-bezier(0.2, 0, 0, 1);
    --ease-spring: cubic-bezier(0.34, 1.42, 0.64, 1);
  }

  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }

  html, body {
    margin: 0;
    padding: 0;
    min-height: 100%;
    background: var(--bg);
    color: var(--on-surface);
    font-family: var(--font-sans);
    font-size: 14.5px;
    line-height: 1.45;
    transition: background-color 220ms var(--ease-m3), color 220ms var(--ease-m3);
  }

  .round-type, h1, h2, h3, button {
    font-family: var(--font-sans);
    font-variation-settings: 'ROND' 100;
  }

  .mono {
    font-family: var(--font-mono);
    font-variation-settings: normal;
  }

  .app-shell {
    max-width: 980px;
    margin: 0 auto;
    padding: 18px 18px 132px;
    transition: filter 220ms var(--ease-m3), transform 220ms var(--ease-m3);
  }

  .app-shell--blurred {
    filter: blur(10px);
    pointer-events: none;
    user-select: none;
  }

  /* ── Top App Bar (OwenclaveTopAppBar) ── */
  .top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 8px 4px 18px;
    flex-wrap: wrap;
  }

  .brand-cluster {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
  }

  .logo-wrap {
    width: 48px;
    height: 48px;
    flex-shrink: 0;
    display: grid;
    place-items: center;
    cursor: pointer;
    transition: transform 260ms var(--ease-spring);
  }

  .logo-wrap:hover { transform: scale(1.06) rotate(-4deg); }
  .logo-wrap:active { transform: scale(0.94); }

  .screen-heading {
    min-width: 0;
  }

  .screen-title {
    margin: 0;
    font-size: 26px;
    font-weight: 650;
    letter-spacing: -0.01em;
    line-height: 1.18;
    color: var(--on-surface);
  }

  .screen-subtitle {
    margin-top: 2px;
    font-size: 12.5px;
    color: var(--on-surface-variant);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .top-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  /* ── Expressive Segmented Mode Control (ExpressiveSegmentedButton) ── */
  .seg-group {
    display: inline-flex;
    background: var(--surface);
    padding: 4px;
    border-radius: 999px;
    gap: 2px;
  }

  .seg-btn {
    border: none;
    background: transparent;
    color: var(--on-surface-variant);
    padding: 7px 14px;
    border-radius: 999px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: background-color 180ms var(--ease-m3), color 180ms var(--ease-m3), transform 140ms var(--ease-spring);
  }

  .seg-btn:hover { color: var(--on-surface); }
  .seg-btn:active { transform: scale(0.95); }

  .seg-btn--active {
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  /* ── Buttons ── */
  .m3-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    border: none;
    border-radius: 999px;
    padding: 10px 18px;
    font-size: 13.5px;
    font-weight: 600;
    background: var(--primary);
    color: var(--on-primary);
    cursor: pointer;
    transition: transform 150ms var(--ease-spring), filter 150ms ease, background-color 180ms var(--ease-m3);
  }

  .m3-btn:hover { filter: brightness(1.06); }
  .m3-btn:active { transform: scale(0.96); }
  .m3-btn:focus-visible, .seg-btn:focus-visible, .choice-pill:focus-visible, .nav-item:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
  }

  .m3-btn--tonal {
    background: var(--secondary-container);
    color: var(--on-secondary-container);
  }

  .m3-btn--surface {
    background: var(--surface-high);
    color: var(--on-surface);
  }

  .m3-btn--danger {
    background: var(--error-container);
    color: var(--on-error-container);
  }

  .m3-btn--sm {
    padding: 7px 13px;
    font-size: 12.5px;
  }

  .icon-btn {
    width: 40px;
    height: 40px;
    border-radius: 999px;
    border: none;
    background: var(--surface-high);
    color: var(--on-surface-variant);
    display: inline-grid;
    place-items: center;
    cursor: pointer;
    flex-shrink: 0;
    transition: transform 150ms var(--ease-spring), background-color 160ms var(--ease-m3), color 160ms var(--ease-m3);
  }

  .icon-btn:hover {
    background: var(--surface-bright);
    color: var(--on-surface);
  }

  .icon-btn:active { transform: scale(0.92); }

  .icon-btn--on-primary {
    background: rgba(255, 255, 255, 0.12);
    color: var(--on-primary-container);
  }

  .icon-btn--on-primary:hover {
    background: rgba(255, 255, 255, 0.2);
    color: var(--on-primary-container);
  }

  .icon-btn--danger:hover {
    background: var(--error-container);
    color: var(--on-error-container);
  }

  /* ── Hero Connection Card ── */
  .hero-card {
    background: var(--surface);
    border-radius: 28px;
    padding: 20px 22px;
    margin-bottom: 18px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
    transition: background-color 240ms var(--ease-m3);
  }

  .hero-card--connected {
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  .hero-card--bypass {
    background: var(--warn-container);
    color: var(--on-warn-container);
  }

  .hero-left {
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 0;
    flex: 1;
  }

  .hero-meta {
    min-width: 0;
  }

  .hero-carrier {
    font-size: 20px;
    font-weight: 650;
    line-height: 1.22;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .hero-subline {
    margin-top: 4px;
    font-size: 13px;
    opacity: 0.84;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .hero-pills {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 11px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 600;
    background: var(--surface-high);
    color: var(--on-surface);
  }

  .hero-card--connected .pill {
    background: rgba(0, 0, 0, 0.2);
    color: var(--on-primary-container);
  }

  .hero-card--bypass .pill {
    background: rgba(0, 0, 0, 0.2);
    color: var(--on-warn-container);
  }

  .pill--ok {
    background: var(--ok-container) !important;
    color: var(--on-ok-container) !important;
  }

  .pill--dead {
    background: var(--error-container) !important;
    color: var(--on-error-container) !important;
  }

  .pill--primary {
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  /* ── ShapedIcon (M3E Polygon Container) ── */
  .shaped-icon {
    width: 48px;
    height: 48px;
    flex-shrink: 0;
    position: relative;
    display: grid;
    place-items: center;
  }

  .shaped-icon--lg {
    width: 56px;
    height: 56px;
  }

  .shaped-icon__bg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  .shaped-icon--spin .shaped-icon__bg {
    animation: m3Spin 7s linear infinite;
  }

  .shaped-icon__fg {
    position: relative;
    z-index: 1;
    width: 22px;
    height: 22px;
    pointer-events: none;
  }

  .shaped-icon--lg .shaped-icon__fg {
    width: 26px;
    height: 26px;
  }

  @keyframes m3Spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  /* ── Section Header & Filter Chips ── */
  .section-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin: 20px 4px 10px;
    flex-wrap: wrap;
  }

  .section-title {
    margin: 0;
    font-size: 16px;
    font-weight: 620;
    color: var(--on-surface);
  }

  .filter-strip {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    margin-bottom: 12px;
  }

  .filter-chip {
    border: none;
    background: var(--surface);
    color: var(--on-surface-variant);
    padding: 7px 14px;
    border-radius: 999px;
    font-size: 12.5px;
    font-weight: 600;
    cursor: pointer;
    transition: background-color 160ms var(--ease-m3), color 160ms var(--ease-m3), transform 130ms var(--ease-spring);
  }

  .filter-chip:hover { color: var(--on-surface); background: var(--surface-low); }
  .filter-chip:active { transform: scale(0.95); }

  .filter-chip--active {
    background: var(--secondary-container);
    color: var(--on-secondary-container);
  }

  /* ── Owenclave ProfileCard (ProfileCard.kt) ── */
  .profile-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .profile-card {
    background: var(--surface);
    color: var(--on-surface);
    border-radius: 28px;
    padding: 13px 16px;
    display: flex;
    align-items: center;
    gap: 14px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: transform 180ms var(--ease-spring), background-color 200ms var(--ease-m3);
  }

  .profile-card:hover { background: var(--surface-low); }
  .profile-card:active { transform: scale(0.985); }

  .profile-card--selected {
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  .profile-card--selected:hover {
    background: var(--primary-container);
    filter: brightness(1.04);
  }

  .profile-body {
    flex: 1;
    min-width: 0;
  }

  .profile-name {
    font-size: 16px;
    font-weight: 620;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .profile-addr {
    font-size: 12.5px;
    opacity: 0.72;
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .profile-tags {
    margin-top: 5px;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    font-size: 12px;
  }

  .proto-label {
    opacity: 0.68;
    font-weight: 600;
  }

  .ping-tag {
    font-weight: 650;
    font-size: 12px;
  }

  .ping-tag--alive { color: var(--ok); }
  .ping-tag--dead { color: var(--error); }
  .profile-card--selected .ping-tag--alive { color: var(--on-ok-container); background: var(--ok-container); padding: 1px 8px; border-radius: 999px; }
  .profile-card--selected .ping-tag--dead { color: var(--on-error-container); background: var(--error-container); padding: 1px 8px; border-radius: 999px; }

  .profile-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }

  /* ── M3E Grouped Stack (groupedItemShape outer=22px inner=5px gap=2px) ── */
  .m3-stack {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .m3-stack-item {
    background: var(--surface);
    padding: 15px 18px;
    border-radius: 6px;
    transition: background-color 180ms var(--ease-m3);
  }

  .m3-stack-item:first-child {
    border-top-left-radius: 24px;
    border-top-right-radius: 24px;
  }

  .m3-stack-item:last-child {
    border-bottom-left-radius: 24px;
    border-bottom-right-radius: 24px;
  }

  .m3-stack-item:only-child {
    border-radius: 24px;
  }

  /* ── Proxy Groups & Choice Pills ── */
  .group-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
    flex-wrap: wrap;
  }

  .group-name {
    font-size: 15.5px;
    font-weight: 620;
  }

  .group-meta {
    font-size: 12.5px;
    color: var(--on-surface-variant);
  }

  .choice-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 7px;
  }

  .choice-pill {
    border: none;
    background: var(--surface-low);
    color: var(--on-surface);
    padding: 8px 13px;
    border-radius: 14px;
    font-size: 13px;
    font-weight: 550;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 7px;
    transition: background-color 160ms var(--ease-m3), color 160ms var(--ease-m3), transform 130ms var(--ease-spring);
  }

  .choice-pill:hover { background: var(--surface-high); }
  .choice-pill:active { transform: scale(0.95); }

  .choice-pill--active {
    background: var(--primary-container);
    color: var(--on-primary-container);
    font-weight: 650;
  }

  .health-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
    background: var(--outline);
  }

  .health-dot--alive { background: var(--ok); }
  .health-dot--dead { background: var(--error); }

  /* ── Subscription GroupCard (GroupCard.kt) ── */
  .sub-card {
    background: var(--surface);
    border-radius: 28px;
    padding: 15px 18px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    position: relative;
    overflow: hidden;
  }

  .sub-progress {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, transparent, var(--primary), transparent);
    background-size: 200% 100%;
    animation: waveBar 1.1s linear infinite;
  }

  @keyframes waveBar {
    from { background-position: 200% 0; }
    to { background-position: -200% 0; }
  }

  .sub-main {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .sub-error-note {
    background: var(--warn-container);
    color: var(--on-warn-container);
    border-radius: 14px;
    padding: 9px 13px;
    font-size: 12.5px;
  }

  /* ── Inputs (M3E Tonal Filled Fields — no harsh outline box) ── */
  .m3-field {
    display: flex;
    flex-direction: column;
    gap: 5px;
    flex: 1;
    min-width: 150px;
  }

  .m3-label {
    font-size: 12px;
    font-weight: 560;
    color: var(--on-surface-variant);
    padding-left: 4px;
  }

  .m3-input, .m3-select, .m3-textarea {
    width: 100%;
    border: 1.5px solid transparent;
    background: var(--surface-low);
    color: var(--on-surface);
    border-radius: 16px;
    padding: 10px 14px;
    font-family: var(--font-sans);
    font-size: 13.5px;
    outline: none;
    transition: border-color 160ms var(--ease-m3), background-color 160ms var(--ease-m3);
  }

  .m3-input:focus, .m3-select:focus, .m3-textarea:focus {
    border-color: var(--primary);
    background: var(--surface-high);
  }

  .m3-textarea {
    min-height: 112px;
    resize: vertical;
    font-family: var(--font-mono);
    font-size: 12.5px;
  }

  /* ── M3 Switch ── */
  .pref-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }

  .pref-info {
    min-width: 0;
    flex: 1;
  }

  .pref-title {
    font-size: 15px;
    font-weight: 580;
    color: var(--on-surface);
  }

  .pref-desc {
    font-size: 12.5px;
    color: var(--on-surface-variant);
    margin-top: 2px;
  }

  .m3-switch {
    position: relative;
    width: 52px;
    height: 30px;
    flex-shrink: 0;
    display: inline-block;
  }

  .m3-switch input {
    opacity: 0;
    width: 0;
    height: 0;
  }

  .m3-switch__track {
    position: absolute;
    inset: 0;
    border-radius: 999px;
    background: var(--surface-high);
    border: 2px solid var(--outline);
    cursor: pointer;
    transition: background-color 180ms var(--ease-m3), border-color 180ms var(--ease-m3);
  }

  .m3-switch__track::after {
    content: '';
    position: absolute;
    top: 5px;
    left: 5px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--outline);
    transition: transform 200ms var(--ease-spring), width 160ms ease, height 160ms ease, top 160ms ease, background-color 180ms ease;
  }

  .m3-switch input:checked + .m3-switch__track {
    background: var(--primary);
    border-color: var(--primary);
  }

  .m3-switch input:checked + .m3-switch__track::after {
    transform: translateX(20px);
    top: 3px;
    width: 20px;
    height: 20px;
    background: var(--on-primary);
  }

  /* ── Theme Swatches ── */
  .swatch-row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 10px;
  }

  .theme-swatch {
    border: 2px solid transparent;
    background: var(--surface-low);
    border-radius: 18px;
    padding: 8px 12px;
    display: inline-flex;
    align-items: center;
    gap: 9px;
    color: var(--on-surface);
    font-size: 13px;
    font-weight: 580;
    cursor: pointer;
    transition: transform 150ms var(--ease-spring), border-color 160ms ease;
  }

  .theme-swatch:hover { transform: translateY(-1px); }
  .theme-swatch--active {
    border-color: var(--primary);
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  .swatch-dot {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  /* ── Route Rule Rows ── */
  .rule-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .rule-left {
    min-width: 0;
    flex: 1;
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .rule-type {
    font-size: 11.5px;
    font-weight: 650;
    padding: 3px 9px;
    border-radius: 8px;
    background: var(--surface-high);
    color: var(--on-surface-variant);
  }

  .rule-payload {
    font-size: 13px;
    word-break: break-all;
  }

  /* ── Logcat Stream ── */
  .logcat-surface {
    background: var(--surface);
    border-radius: 24px;
    padding: 14px 16px;
    max-height: 62vh;
    overflow-y: auto;
    font-size: 12px;
    line-height: 1.52;
  }

  .log-entry {
    padding: 4px 0;
    border-bottom: 1px solid rgba(128, 128, 128, 0.08);
    word-break: break-word;
  }

  .log-entry--error { color: var(--error); }
  .log-entry--warning { color: var(--warn); }
  .log-entry--info { color: var(--on-surface); }
  .log-entry--debug { color: var(--on-surface-variant); }
  .log-ts { opacity: 0.55; margin-right: 6px; }

  /* ── Empty State (CommonComponents.kt) ── */
  .empty-state {
    padding: 44px 20px;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
    color: var(--on-surface-variant);
  }

  /* ── UnifiedBottomBar (ComposeMainActivity.kt:616) ── */
  .bottom-dock {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 18px;
    display: flex;
    justify-content: center;
    padding: 0 12px;
    z-index: 50;
    pointer-events: none;
  }

  .unified-bar {
    pointer-events: auto;
    background: var(--surface-high);
    border-radius: 32px;
    padding: 8px 10px;
    box-shadow: var(--shadow-bar);
    display: inline-flex;
    align-items: center;
    gap: 8px;
    max-width: 100%;
    transition: background-color 220ms var(--ease-m3);
  }

  .unified-bar--testing {
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  .bar-status {
    padding: 0 8px 0 10px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .pulse-wrap {
    width: 22px;
    height: 22px;
    position: relative;
    display: grid;
    place-items: center;
  }

  .pulse-ring {
    position: absolute;
    inset: 0;
    border-radius: 50%;
    background: var(--outline-variant);
    opacity: 0.35;
  }

  .pulse-wrap--on .pulse-ring {
    background: var(--primary);
    animation: statusPulse 1.8s ease-in-out infinite;
  }

  .pulse-dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--outline-variant);
    position: relative;
    z-index: 1;
  }

  .pulse-wrap--on .pulse-dot {
    background: var(--primary);
  }

  @keyframes statusPulse {
    0%, 100% { transform: scale(0.68); opacity: 0.75; }
    50% { transform: scale(1.12); opacity: 0.2; }
  }

  .bar-divider {
    width: 1px;
    height: 30px;
    background: var(--on-surface-variant);
    opacity: 0.18;
    flex-shrink: 0;
  }

  .nav-track {
    display: flex;
    align-items: center;
    gap: 4px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .nav-track::-webkit-scrollbar { display: none; }

  .nav-item {
    border: none;
    background: transparent;
    color: var(--on-surface-variant);
    height: 46px;
    padding: 0 14px;
    border-radius: 24px;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    font-size: 13.5px;
    font-weight: 600;
    white-space: nowrap;
    transition: background-color 200ms var(--ease-m3), color 200ms var(--ease-m3), transform 200ms var(--ease-spring);
  }

  .nav-item:hover { color: var(--on-surface); }
  .nav-item:active { transform: scale(0.93); }

  .nav-item--active {
    background: var(--primary-container);
    color: var(--on-primary-container);
    transform: scale(1.03);
  }

  .unified-bar--testing .nav-item--active {
    background: var(--primary);
    color: var(--on-primary);
  }

  .nav-item svg { width: 21px; height: 21px; flex-shrink: 0; }

  .power-fab {
    width: 48px;
    height: 48px;
    border-radius: 22px;
    border: none;
    background: var(--primary);
    color: var(--on-primary);
    display: grid;
    place-items: center;
    cursor: pointer;
    flex-shrink: 0;
    transition: transform 200ms var(--ease-spring), border-radius 200ms var(--ease-m3), background-color 200ms var(--ease-m3);
  }

  .power-fab:hover { transform: scale(1.05); }
  .power-fab:active { transform: scale(0.92); border-radius: 16px; }
  .power-fab--idle {
    background: var(--primary-container);
    color: var(--on-primary-container);
  }

  /* ── Expressive Dialog (ExpressiveDialog.kt) ── */
  .scrim {
    position: fixed;
    inset: 0;
    background: rgba(8, 6, 8, 0.56);
    display: none;
    align-items: center;
    justify-content: center;
    padding: 16px;
    z-index: 90;
  }

  .scrim--open { display: flex; }

  .m3-dialog {
    background: var(--surface-high);
    color: var(--on-surface);
    border-radius: 28px;
    padding: 24px;
    width: 100%;
    max-width: 520px;
    max-height: 90vh;
    overflow-y: auto;
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5);
    animation: dialogPop 220ms var(--ease-spring);
  }

  @keyframes dialogPop {
    from { opacity: 0; transform: scale(0.93) translateY(8px); }
    to { opacity: 1; transform: scale(1) translateY(0); }
  }

  .dialog-title {
    margin: 0 0 6px;
    font-size: 21px;
    font-weight: 650;
  }

  .dialog-desc {
    margin: 0 0 16px;
    font-size: 13px;
    color: var(--on-surface-variant);
  }

  .form-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 12px;
  }

  .dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 18px;
  }

  /* ── Snackbar Toast ── */
  .snackbar {
    position: fixed;
    bottom: 92px;
    left: 50%;
    transform: translateX(-50%) translateY(14px);
    background: var(--on-surface);
    color: var(--surface);
    padding: 11px 20px;
    border-radius: 16px;
    font-size: 13.5px;
    font-weight: 580;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
    opacity: 0;
    pointer-events: none;
    z-index: 120;
    transition: opacity 180ms ease, transform 200ms var(--ease-spring);
  }

  .snackbar--show {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }

  @media (max-width: 680px) {
    .nav-label { display: none; }
    .nav-item { padding: 0 12px; width: 46px; justify-content: center; }
    .app-shell { padding: 14px 12px 124px; }
    .screen-title { font-size: 22px; }
    .hero-card { padding: 16px; }
  }

  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
      animation-duration: 0.01ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.01ms !important;
    }
  }
</style>
</head>
<body>

<div class="app-shell" id="appShell">
  <header class="top-bar">
    <div class="brand-cluster">
      <div class="logo-wrap" id="brandLogoSlot" title="Owenclave"></div>
      <div class="screen-heading">
        <h1 class="screen-title" id="screenTitle">Профили</h1>
        <div class="screen-subtitle mono" id="listenerSubtitle">Owenclave</div>
      </div>
    </div>
    <div class="top-actions">
      <div class="seg-group" id="modeSwitch" role="tablist">
        <button class="seg-btn" data-mode="rule" id="modeRuleBtn">По правилам</button>
        <button class="seg-btn" data-mode="global" id="modeGlobalBtn">Глобально</button>
        <button class="seg-btn" data-mode="direct" id="modeDirectBtn">Напрямую</button>
      </div>
      <button class="m3-btn m3-btn--tonal m3-btn--sm" id="topPrimaryActionBtn"></button>
    </div>
  </header>

  <!-- 1. CONFIGURATION (Profiles) -->
  <section id="screen-configuration">
    <div class="hero-card" id="heroCard">
      <div class="hero-left">
        <div id="heroIconSlot"></div>
        <div class="hero-meta">
          <div class="hero-carrier round-type" id="heroCarrierName">Загрузка...</div>
          <div class="hero-subline" id="heroSubline"></div>
        </div>
      </div>
      <div class="hero-pills" id="heroPills"></div>
    </div>

    <div class="section-bar">
      <h2 class="section-title" id="profilesSectionTitle">Серверы</h2>
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <button class="m3-btn m3-btn--surface m3-btn--sm" id="batchPingBtn"></button>
      </div>
    </div>
    <div class="filter-strip" id="profileFilterStrip"></div>
    <div class="profile-list" id="profileCardsList"></div>
  </section>

  <!-- 2. GROUPS & SUBSCRIPTIONS -->
  <section id="screen-groups" style="display:none">
    <div class="section-bar">
      <h2 class="section-title" id="subsSectionTitle">Подписки</h2>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="m3-btn m3-btn--surface m3-btn--sm" id="refreshAllSubsBtn"></button>
        <button class="m3-btn m3-btn--tonal m3-btn--sm" id="openAddSubBtn"></button>
      </div>
    </div>
    <div class="profile-list" id="subsCardsList"></div>

    <div class="section-bar" style="margin-top:26px">
      <h2 class="section-title" id="proxyGroupsSectionTitle">Группы маршрутизации</h2>
    </div>
    <div class="m3-stack" id="groupsStack"></div>
  </section>

  <!-- 3. ROUTE (Rules) -->
  <section id="screen-route" style="display:none">
    <div class="filter-strip" id="ruleFilterStrip"></div>
    <div class="form-grid" style="margin-bottom:14px">
      <div class="m3-field">
        <input type="text" class="m3-input" id="ruleSearchInput">
      </div>
    </div>
    <div class="m3-stack" id="rulesStack"></div>
  </section>

  <!-- 4. LOGCAT -->
  <section id="screen-logcat" style="display:none">
    <div class="section-bar" style="margin-top:4px">
      <div class="filter-strip" id="logLevelStrip" style="margin-bottom:0"></div>
      <div style="display:flex;gap:8px;align-items:center">
        <input type="text" class="m3-input" id="logSearchInput" style="width:190px;padding:7px 12px">
        <button class="m3-btn m3-btn--surface m3-btn--sm" id="copyLogsBtn"></button>
        <button class="m3-btn m3-btn--tonal m3-btn--sm" id="refreshLogsBtn"></button>
      </div>
    </div>
    <div class="logcat-surface mono" id="logcatBox"></div>
  </section>

  <!-- 5. SETTINGS -->
  <section id="screen-settings" style="display:none">
    <div class="section-bar" style="margin-top:4px">
      <h2 class="section-title" id="appearanceHeading">Оформление Owenclave</h2>
    </div>
    <div class="m3-stack">
      <div class="m3-stack-item">
        <div class="pref-title" id="paletteTitle">Цветовая тема</div>
        <div class="swatch-row" id="themeSwatches"></div>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="nightModeTitle">Режим темы</div>
          <div class="pref-desc" id="nightModeDesc">Тёмная, светлая или чистый чёрный AMOLED</div>
        </div>
        <div class="seg-group" id="nightModeGroup">
          <button class="seg-btn" data-night="dark" id="nightDarkBtn">Тёмная</button>
          <button class="seg-btn" data-night="amoled" id="nightAmoledBtn">AMOLED</button>
          <button class="seg-btn" data-night="light" id="nightLightBtn">Светлая</button>
        </div>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="logoStyleTitle">Форма логотипа</div>
          <div class="pref-desc">Duster Split, Flower, Cookie, Sunny, Gem</div>
        </div>
        <div class="seg-group" id="logoStyleGroup">
          <button class="seg-btn" data-logo="split">Split</button>
          <button class="seg-btn" data-logo="flower">Flower</button>
          <button class="seg-btn" data-logo="cookie">Cookie</button>
          <button class="seg-btn" data-logo="sunny">Sunny</button>
          <button class="seg-btn" data-logo="gem">Gem</button>
        </div>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="langTitle">Язык интерфейса</div>
        </div>
        <div class="seg-group" id="langGroup">
          <button class="seg-btn" data-lang="ru">Русский</button>
          <button class="seg-btn" data-lang="en">English</button>
        </div>
      </div>
    </div>

    <div class="section-bar">
      <h2 class="section-title" id="failoverHeading">Отказоустойчивость и проверка узлов</h2>
    </div>
    <div class="m3-stack">
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="fbDirectTitle">Уходить в Bypass при недоступности всех серверов</div>
          <div class="pref-desc" id="fbDirectDesc">Если ни один прокси-сервер не отвечает, трафик идёт напрямую через интерфейс без отвала интернета</div>
        </div>
        <label class="m3-switch">
          <input type="checkbox" id="cfgFallbackDirect">
          <span class="m3-switch__track"></span>
        </label>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="hcIntervalTitle">Интервал проверки серверов (сек)</div>
          <div class="pref-desc" id="hcIntervalDesc">Частота проб multiObservatory для автоматического переключения</div>
        </div>
        <input type="number" class="m3-input mono" id="cfgHealthInterval" min="0" max="3600" style="width:110px">
      </div>
    </div>

    <div class="section-bar">
      <h2 class="section-title" id="networkHeading">Сеть и порты демона</h2>
    </div>
    <div class="m3-stack">
      <div class="m3-stack-item">
        <div class="form-grid" style="margin-bottom:0">
          <div class="m3-field">
            <label class="m3-label" id="lblMixedPort">Mixed порт (HTTP + SOCKS5)</label>
            <input type="number" class="m3-input mono" id="cfgMixedPort" min="0" max="65535">
          </div>
          <div class="m3-field">
            <label class="m3-label" id="lblTproxyPort">TProxy порт (Linux)</label>
            <input type="number" class="m3-input mono" id="cfgTproxyPort" min="0" max="65535">
          </div>
          <div class="m3-field">
            <label class="m3-label" id="lblBindAddr">Адрес прослушивания</label>
            <input type="text" class="m3-input mono" id="cfgBindAddress" placeholder="0.0.0.0">
          </div>
          <div class="m3-field">
            <label class="m3-label" id="lblIface">Исходящий интерфейс</label>
            <input type="text" class="m3-input mono" id="cfgInterface" placeholder="enp7s0">
          </div>
        </div>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="lblAllowLan">Разрешить подключения из локальной сети (LAN)</div>
        </div>
        <label class="m3-switch">
          <input type="checkbox" id="cfgAllowLan">
          <span class="m3-switch__track"></span>
        </label>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="lblIpv6">Поддержка IPv6</div>
        </div>
        <label class="m3-switch">
          <input type="checkbox" id="cfgIpv6">
          <span class="m3-switch__track"></span>
        </label>
      </div>
      <div class="m3-stack-item pref-row">
        <div class="pref-info">
          <div class="pref-title" id="lblLogLevel">Уровень логирования ядра</div>
        </div>
        <select class="m3-select mono" id="cfgLogLevel" style="width:150px">
          <option value="debug">debug</option>
          <option value="info">info</option>
          <option value="warning">warning</option>
          <option value="error">error</option>
          <option value="silent">silent</option>
        </select>
      </div>
    </div>

    <div style="display:flex;justify-content:space-between;flex-wrap:wrap;gap:10px;margin-top:18px">
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="m3-btn m3-btn--surface" id="openSecretModalBtn"></button>
        <button class="m3-btn m3-btn--danger" id="forceReloadBtn"></button>
      </div>
      <button class="m3-btn" id="saveSettingsBtn"></button>
    </div>
  </section>
</div>

<!-- Floating M3E UnifiedBottomBar -->
<div class="bottom-dock">
  <nav class="unified-bar" id="unifiedBar" aria-label="Primary">
    <div class="bar-status" id="barStatusSlot">
      <div class="pulse-wrap" id="statusPulseWrap">
        <span class="pulse-ring"></span>
        <span class="pulse-dot"></span>
      </div>
    </div>
    <div class="bar-divider"></div>
    <div class="nav-track" id="navTrack"></div>
    <div class="bar-divider"></div>
    <button class="power-fab" id="powerTestFab" title="Test connection"></button>
  </nav>
</div>

<!-- MODAL 1: Add / Import Profile -->
<div class="scrim" id="importModal">
  <div class="m3-dialog">
    <h3 class="dialog-title" id="importDialogTitle">Добавить профиль</h3>
    <div class="seg-group" id="importModeTabs" style="margin-bottom:14px">
      <button class="seg-btn seg-btn--active" data-imode="links" id="itabLinks">Ссылки / YAML</button>
      <button class="seg-btn" data-imode="http" id="itabHttp">HTTP / HTTPS</button>
      <button class="seg-btn" data-imode="raw" id="itabRaw">Raw JSON</button>
    </div>

    <div id="importPaneLinks">
      <p class="dialog-desc" id="importLinksDesc">Вставьте ссылки vless://, hy2://, naive+https://, ss://, trojan:// или Clash YAML.</p>
      <textarea class="m3-textarea" id="importText" placeholder="vless://...&#10;hy2://...&#10;naive+https://..."></textarea>
    </div>

    <div id="importPaneHttp" style="display:none">
      <div class="form-grid">
        <div class="m3-field" style="max-width:150px">
          <label class="m3-label">Протокол</label>
          <select class="m3-select" id="httpProtoSelect">
            <option value="http">HTTP</option>
            <option value="https">HTTPS (TLS)</option>
          </select>
        </div>
        <div class="m3-field">
          <label class="m3-label">Имя профиля</label>
          <input type="text" class="m3-input" id="httpName" placeholder="Необязательно">
        </div>
      </div>
      <div class="form-grid">
        <div class="m3-field">
          <label class="m3-label">Хост или IP</label>
          <input type="text" class="m3-input mono" id="httpHost" placeholder="proxy.example.com">
        </div>
        <div class="m3-field" style="max-width:120px">
          <label class="m3-label">Порт</label>
          <input type="number" class="m3-input mono" id="httpPort" value="8080" min="1" max="65535">
        </div>
      </div>
      <div class="form-grid">
        <div class="m3-field">
          <label class="m3-label">Логин</label>
          <input type="text" class="m3-input" id="httpUser">
        </div>
        <div class="m3-field">
          <label class="m3-label">Пароль</label>
          <input type="password" class="m3-input" id="httpPass">
        </div>
      </div>
      <div class="form-grid" id="httpsTlsRow" style="display:none">
        <div class="m3-field">
          <label class="m3-label">TLS SNI</label>
          <input type="text" class="m3-input mono" id="httpSni">
        </div>
        <label style="display:flex;align-items:center;gap:8px;font-size:13px;padding-top:18px">
          <input type="checkbox" id="httpInsecure"> Allow insecure
        </label>
      </div>
      <label style="display:flex;align-items:center;gap:8px;font-size:13px">
        <input type="checkbox" id="httpUot"> UDP over TCP
      </label>
    </div>

    <div class="dialog-actions">
      <button class="m3-btn m3-btn--surface" data-close="importModal" id="cancelImportBtn">Отмена</button>
      <button class="m3-btn" id="submitImportBtn">Импортировать</button>
    </div>
  </div>
</div>

<!-- MODAL 2: Add Subscription -->
<div class="scrim" id="subModal">
  <div class="m3-dialog">
    <h3 class="dialog-title" id="subDialogTitle">Новая подписка</h3>
    <p class="dialog-desc" id="subDialogDesc">Поддерживаются Remnawave / Mihomo YAML, Base64 списки и ссылки happ://crypt.</p>
    <div class="form-grid">
      <div class="m3-field">
        <label class="m3-label">Название</label>
        <input type="text" class="m3-input" id="subName" placeholder="Основная подписка">
      </div>
    </div>
    <div class="form-grid">
      <div class="m3-field">
        <label class="m3-label">URL подписки</label>
        <input type="text" class="m3-input mono" id="subUrl" placeholder="https://...">
      </div>
    </div>
    <div class="form-grid">
      <div class="m3-field">
        <label class="m3-label">User-Agent (необязательно)</label>
        <input type="text" class="m3-input mono" id="subUa" placeholder="Owenclave">
      </div>
    </div>
    <label style="display:flex;align-items:center;gap:8px;font-size:13px">
      <input type="checkbox" id="subHwid"> Отправлять заголовки HWID устройства
    </label>
    <div class="dialog-actions">
      <button class="m3-btn m3-btn--surface" data-close="subModal">Отмена</button>
      <button class="m3-btn" id="submitSubBtn">Добавить и загрузить</button>
    </div>
  </div>
</div>

<!-- MODAL 3: Add Routing Rule -->
<div class="scrim" id="ruleModal">
  <div class="m3-dialog">
    <h3 class="dialog-title" id="ruleDialogTitle">Пользовательское правило</h3>
    <p class="dialog-desc" id="ruleDialogDesc">Пользовательские правила имеют приоритет над правилами из подписки.</p>
    <div class="form-grid">
      <div class="m3-field">
        <label class="m3-label">Название</label>
        <input type="text" class="m3-input" id="ruleName" placeholder="Например, Рабочий домен">
      </div>
      <div class="m3-field" style="max-width:170px">
        <label class="m3-label">Действие</label>
        <select class="m3-select" id="ruleTarget">
          <option value="proxy">PROXY</option>
          <option value="direct">DIRECT (Bypass)</option>
          <option value="block">REJECT (Блок)</option>
        </select>
      </div>
    </div>
    <div class="form-grid">
      <div class="m3-field">
        <label class="m3-label">Домены</label>
        <input type="text" class="m3-input mono" id="ruleDomains" placeholder="domain:example.com, geosite:category-ru">
      </div>
    </div>
    <div class="form-grid">
      <div class="m3-field">
        <label class="m3-label">IP / CIDR</label>
        <input type="text" class="m3-input mono" id="ruleIp" placeholder="192.168.0.0/16, geoip:ru">
      </div>
      <div class="m3-field" style="max-width:130px">
        <label class="m3-label">Порт</label>
        <input type="text" class="m3-input mono" id="rulePort" placeholder="443">
      </div>
      <div class="m3-field" style="max-width:140px">
        <label class="m3-label">Сеть</label>
        <select class="m3-select" id="ruleNetwork">
          <option value="">TCP + UDP</option>
          <option value="tcp">TCP</option>
          <option value="udp">UDP</option>
        </select>
      </div>
    </div>
    <div class="dialog-actions">
      <button class="m3-btn m3-btn--surface" data-close="ruleModal">Отмена</button>
      <button class="m3-btn" id="submitRuleBtn">Добавить правило</button>
    </div>
  </div>
</div>

<!-- MODAL 4: Controller API Secret -->
<div class="scrim" id="authModal">
  <div class="m3-dialog" style="max-width:410px">
    <h3 class="dialog-title" id="authDialogTitle">Ключ доступа API</h3>
    <p class="dialog-desc" id="authDialogDesc">Введите secret, заданный для контроллера Owenclave.</p>
    <div class="form-grid">
      <div class="m3-field">
        <input type="password" class="m3-input mono" id="secretInput" placeholder="Bearer secret">
      </div>
    </div>
    <div class="dialog-actions">
      <button class="m3-btn m3-btn--surface" data-close="authModal">Закрыть</button>
      <button class="m3-btn" id="saveSecretBtn">Подключиться</button>
    </div>
  </div>
</div>

<div class="snackbar" id="snackbar"></div>

<script>
(() => {
  const SVG_NS = 'http://www.w3.org/2000/svg';

  /* ── Authentic Owenclave Color.kt Palettes ── */
  const THEMES = {
    pink: {
      label: 'Pink', swatch: '#E91E63',
      dark: {
        bg: '#1f1519', surface: '#170f13', low: '#23181d', high: '#2d1f25', bright: '#3a2931',
        onSurface: '#f0e0e5', onVariant: '#d5c2c7',
        primary: '#ffb1c8', onPrimary: '#5e1133', primaryContainer: '#7e2949', onPrimaryContainer: '#ffd9e2',
        secondaryContainer: '#562337', onSecondaryContainer: '#ffd9e2',
        outline: '#9e8d91', outlineVariant: '#514347'
      },
      light: {
        bg: '#f8e4eb', surface: '#fff5f8', low: '#fcebf0', high: '#f2d7e0', bright: '#ffffff',
        onSurface: '#201018', onVariant: '#514347',
        primary: '#e91e63', onPrimary: '#ffffff', primaryContainer: '#ffd9e2', onPrimaryContainer: '#3e001d',
        secondaryContainer: '#f7cdd9', onSecondaryContainer: '#3e001d',
        outline: '#837377', outlineVariant: '#d5c2c7'
      }
    },
    unrecovery: {
      label: 'Unrecovery', swatch: '#6B8FD4',
      dark: {
        bg: '#251a1e', surface: '#1e1518', low: '#2a1e22', high: '#35272c', bright: '#423238',
        onSurface: '#f2f2f2', onVariant: '#c0b0b4',
        primary: '#6b8fd4', onPrimary: '#0f1f40', primaryContainer: '#2d3d5c', onPrimaryContainer: '#d0d8f0',
        secondaryContainer: '#362a2f', onSecondaryContainer: '#d0c8cc',
        outline: '#8b7b7f', outlineVariant: '#4a3a3e'
      },
      light: {
        bg: '#ebe5e6', surface: '#f7f4f4', low: '#f1ecec', high: '#e3dbdd', bright: '#ffffff',
        onSurface: '#26191c', onVariant: '#4a3f43',
        primary: '#0f1f40', onPrimary: '#f2f2f2', primaryContainer: '#dde4ff', onPrimaryContainer: '#001a41',
        secondaryContainer: '#dbc8cc', onSecondaryContainer: '#26191c',
        outline: '#7a6f73', outlineVariant: '#ccc8c8'
      }
    },
    bluegrey: {
      label: 'Duster', swatch: '#607D8B',
      dark: {
        bg: '#151c1f', surface: '#0f1416', low: '#192125', high: '#222c31', bright: '#2d393f',
        onSurface: '#dfe3e6', onVariant: '#c1c7ca',
        primary: '#afcce0', onPrimary: '#0f3445', primaryContainer: '#2f4a5b', onPrimaryContainer: '#cbe4f0',
        secondaryContainer: '#293c48', onSecondaryContainer: '#cbe4f0',
        outline: '#8b9294', outlineVariant: '#414849'
      },
      light: {
        bg: '#ebf1f5', surface: '#fafcfe', low: '#f2f6f9', high: '#e1eaef', bright: '#ffffff',
        onSurface: '#191c1e', onVariant: '#414849',
        primary: '#607d8b', onPrimary: '#ffffff', primaryContainer: '#cbe4f0', onPrimaryContainer: '#072130',
        secondaryContainer: '#d5e6ef', onSecondaryContainer: '#072130',
        outline: '#71787a', outlineVariant: '#dde3e6'
      }
    },
    deeppurple: {
      label: 'Deep Purple', swatch: '#7E57C2',
      dark: {
        bg: '#1b1821', surface: '#141218', low: '#1f1c26', high: '#292533', bright: '#353042',
        onSurface: '#e6e1e9', onVariant: '#ccc4cf',
        primary: '#cfbcff', onPrimary: '#381e73', primaryContainer: '#4f378b', onPrimaryContainer: '#e9ddff',
        secondaryContainer: '#3d3157', onSecondaryContainer: '#e9ddff',
        outline: '#948f9a', outlineVariant: '#49454e'
      },
      light: {
        bg: '#f2ebf9', surface: '#fef7ff', low: '#f7f0fc', high: '#eadff5', bright: '#ffffff',
        onSurface: '#1d1a20', onVariant: '#49454e',
        primary: '#673ab7', onPrimary: '#ffffff', primaryContainer: '#e9ddff', onPrimaryContainer: '#21005d',
        secondaryContainer: '#e6dbf7', onSecondaryContainer: '#21005d',
        outline: '#7a757f', outlineVariant: '#e7e0eb'
      }
    },
    teal: {
      label: 'Teal', swatch: '#009688',
      dark: {
        bg: '#131c19', surface: '#0e1412', low: '#17211e', high: '#1f2d29', bright: '#2a3b36',
        onSurface: '#dfe3de', onVariant: '#bec9c4',
        primary: '#53dbc9', onPrimary: '#003731', primaryContainer: '#005048', onPrimaryContainer: '#9cf0e4',
        secondaryContainer: '#1b3f3a', onSecondaryContainer: '#b5ece3',
        outline: '#88938f', outlineVariant: '#3f4946'
      },
      light: {
        bg: '#e7f2ee', surface: '#fafdf9', low: '#f0f7f4', high: '#dcebe5', bright: '#ffffff',
        onSurface: '#191c1a', onVariant: '#3f4946',
        primary: '#009688', onPrimary: '#ffffff', primaryContainer: '#9cf0e4', onPrimaryContainer: '#00201d',
        secondaryContainer: '#c7ece6', onSecondaryContainer: '#00201d',
        outline: '#6f7975', outlineVariant: '#dae5e1'
      }
    },
    blue: {
      label: 'Blue', swatch: '#2196F3',
      dark: {
        bg: '#17191e', surface: '#121316', low: '#1b1d23', high: '#242830', bright: '#303540',
        onSurface: '#e4e2e9', onVariant: '#c4c6d0',
        primary: '#afc6ff', onPrimary: '#002f69', primaryContainer: '#004693', onPrimaryContainer: '#d8e2ff',
        secondaryContainer: '#233658', onSecondaryContainer: '#d8e2ff',
        outline: '#8e9099', outlineVariant: '#44474f'
      },
      light: {
        bg: '#eaf0fb', surface: '#fefbff', low: '#f3f6fc', high: '#e1e8f5', bright: '#ffffff',
        onSurface: '#1a1b1f', onVariant: '#44474f',
        primary: '#2196f3', onPrimary: '#ffffff', primaryContainer: '#d8e2ff', onPrimaryContainer: '#001a41',
        secondaryContainer: '#d6e3fc', onSecondaryContainer: '#001a41',
        outline: '#74777f', outlineVariant: '#e1e2ec'
      }
    }
  };

  /* ── Bilingual Copy (RU / EN) ── */
  const I18N = {
    ru: {
      nav: {
        configuration: 'Профили',
        groups: 'Группы',
        route: 'Маршруты',
        logcat: 'Журнал',
        settings: 'Настройки'
      },
      modes: { rule: 'По правилам', global: 'Глобально', direct: 'Напрямую' },
      addImport: '+ Профиль',
      addSub: '+ Подписка',
      addRule: '+ Правило',
      updateAll: 'Обновить все',
      batchPing: 'Проверить пинг',
      servers: 'Серверы',
      subs: 'Подписки',
      proxyGroups: 'Группы маршрутизации',
      filterAll: 'Все',
      filterAlive: 'Доступные',
      filterManual: 'Вручную',
      filterUserRules: 'Мои правила',
      ruleSearchPlaceholder: 'Поиск по домену, IP, типу или группе...',
      logSearchPlaceholder: 'Фильтр логов...',
      copyLogs: 'Копировать',
      refreshLogs: 'Обновить',
      connectedVia: 'Трафик через',
      bypassActiveTitle: 'Аварийный Bypass активен',
      bypassActiveSub: 'Все прокси-серверы недоступны — трафик идёт напрямую без разрыва интернета',
      directModeSub: 'Режим прямого соединения без проксирования',
      failoverProtected: 'Failover → Bypass включён',
      aliveCount: 'живых',
      offline: 'Недоступен',
      noProfiles: 'Профили ещё не добавлены. Импортируйте ссылку или добавьте подписку.',
      noSubs: 'Подписки не настроены. Добавьте URL подписки Remnawave / Clash YAML.',
      noRules: 'Подходящих правил не найдено.',
      copiedUri: 'Ссылка профиля скопирована',
      copiedLogs: 'Журнал скопирован в буфер обмена',
      savedSettings: 'Настройки сохранены',
      reloadedCore: 'Ядро перезапущено',
      apiSecretBtn: 'Ключ API',
      forceRestartBtn: 'Перезапустить ядро',
      saveApplyBtn: 'Сохранить настройки',
      updatedAt: 'Обновлено: ',
      never: 'никогда',
      keptSessionNote: 'Активные сессии сохранены. Последняя ошибка: '
    },
    en: {
      nav: {
        configuration: 'Configuration',
        groups: 'Groups',
        route: 'Route',
        logcat: 'Logcat',
        settings: 'Settings'
      },
      modes: { rule: 'Rule', global: 'Global', direct: 'Direct' },
      addImport: '+ Add profile',
      addSub: '+ Subscription',
      addRule: '+ Add rule',
      updateAll: 'Update all',
      batchPing: 'Test latency',
      servers: 'Profiles',
      subs: 'Subscriptions',
      proxyGroups: 'Proxy groups',
      filterAll: 'All',
      filterAlive: 'Available',
      filterManual: 'Manual',
      filterUserRules: 'Custom',
      ruleSearchPlaceholder: 'Search domain, CIDR, rule type, or target...',
      logSearchPlaceholder: 'Filter logs...',
      copyLogs: 'Copy',
      refreshLogs: 'Refresh',
      connectedVia: 'Routing via',
      bypassActiveTitle: 'Direct bypass failover active',
      bypassActiveSub: 'All upstream servers are unreachable — routing directly so LAN stays online',
      directModeSub: 'All traffic is routed directly without proxying',
      failoverProtected: 'Failover → Bypass on',
      aliveCount: 'alive',
      offline: 'Offline',
      noProfiles: 'No profiles yet. Import a share link or add a subscription.',
      noSubs: 'No subscriptions configured yet.',
      noRules: 'No matching routing rules.',
      copiedUri: 'Copied share URI',
      copiedLogs: 'Copied logs to clipboard',
      savedSettings: 'Settings applied',
      reloadedCore: 'Core restarted',
      apiSecretBtn: 'API secret',
      forceRestartBtn: 'Force restart core',
      saveApplyBtn: 'Save changes',
      updatedAt: 'Updated: ',
      never: 'never',
      keptSessionNote: 'Active sessions kept intact. Last error: '
    }
  };

  /* ── Vector Icons (24x24 paths) ── */
  const ICONS = {
    configuration: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM8 13h8v2H8v-2zm0 4h5v2H8v-2z',
    groups: 'M4 6h16v2H4V6zm0 5h16v2H4v-2zm0 5h16v2H4v-2z',
    route: 'M21.71 11.29l-9-9a1 1 0 0 0-1.42 0l-9 9a1 1 0 0 0 0 1.42l9 9a1 1 0 0 0 1.42 0l9-9a1 1 0 0 0 0-1.42zM14 14.5V12h-4v3H8v-4a1 1 0 0 1 1-1h5V7.5l3.5 3.5-3.5 3.5z',
    logcat: 'M20 8h-2.81a5.99 5.99 0 0 0-1.82-1.96L17 4.41 15.59 3l-2.17 2.17a6 6 0 0 0-2.84 0L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81A6 6 0 0 0 17.19 18H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z',
    settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z',
    bolt: 'M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66l.1-.16L12.92 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15L11 21z',
    power: 'M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42A6.92 6.92 0 0 1 19 12c0 3.87-3.13 7-7 7A6.995 6.995 0 0 1 7.58 6.58L6.17 5.17A8.932 8.932 0 0 0 3 12a9 9 0 0 0 18 0c0-2.74-1.23-5.18-3.17-6.83z',
    cloud: 'M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
    public: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1 17.93A8.001 8.001 0 0 1 4 12c0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41a7.998 7.998 0 0 1 2.9 12.8z',
    shield: 'M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z',
    lock: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9a2 2 0 1 1 0-4 2 2 0 0 1 0 4zm3.1-9H8.9V6a3.1 3.1 0 0 1 6.2 0v2z',
    key: 'M12.65 10A5.99 5.99 0 0 0 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6a5.99 5.99 0 0 0 5.65-4H17v4h4v-4h2v-4H12.65zM7 14a2 2 0 1 1 0-4 2 2 0 0 1 0 4z',
    speed: 'M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z',
    hub: 'M17 16l-4-4V8.82C14.16 8.4 15 7.3 15 6a3 3 0 0 0-6 0c0 1.3.84 2.4 2 2.82V12l-4 4H3v5h5v-3.05l4-4.2 4 4.2V21h5v-5h-4z',
    ping: 'M12 3a9 9 0 0 0-9 9h2a7 7 0 0 1 14 0h2a9 9 0 0 0-9-9zm0 4a5 5 0 0 0-5 5h2a3 3 0 0 1 6 0h2a5 5 0 0 0-5-5zm0 4a1 1 0 1 0 0 2 1 1 0 0 0 0-2z',
    share: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81a3 3 0 0 0 0-6 3 3 0 0 0-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9a3 3 0 0 0 0 6c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65a2.92 2.92 0 1 0 2.92-2.92z',
    trash: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    refresh: 'M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z'
  };

  const PROFILE_ICON_KEYS = ['bolt', 'cloud', 'public', 'shield', 'lock', 'key', 'speed', 'hub'];

  /* ── State ── */
  let state = null;
  let activeScreen = localStorage.getItem('owenclave_screen') || 'configuration';
  if (!['configuration', 'groups', 'route', 'logcat', 'settings'].includes(activeScreen)) {
    activeScreen = 'configuration';
  }
  let profileFilter = 'all';
  let ruleFilterKind = 'all';
  let logFilterLevel = 'all';
  let importTabMode = 'links';
  let updatingSubIds = new Set();
  let pingingIds = new Set();
  let batchProgress = null; // { done, total }

  let uiPrefs = {
    theme: localStorage.getItem('owenclave_theme') || 'pink',
    night: localStorage.getItem('owenclave_night') || 'dark',
    logo: localStorage.getItem('owenclave_logo') || 'split',
    lang: localStorage.getItem('owenclave_lang') || ((navigator.language || '').toLowerCase().startsWith('ru') ? 'ru' : 'en')
  };

  const getSecret = () => localStorage.getItem('owenclave_secret') || '';
  const setSecret = (s) => localStorage.setItem('owenclave_secret', s);
  const t = () => I18N[uiPrefs.lang] || I18N.ru;

  function hashSeed(str) {
    let h = 0;
    const s = String(str || '');
    for (let i = 0; i < s.length; i++) {
      h = ((h << 5) - h + s.charCodeAt(i)) | 0;
    }
    return Math.abs(h);
  }

  /* ── Procedural SVG Shapes (AppLogo.kt & ExpressiveComponents.kt) ── */
  function scallopedPath(cx, cy, R, petals, depth) {
    const steps = petals * 24;
    let d = '';
    for (let i = 0; i < steps; i++) {
      const a = (2 * Math.PI * i) / steps;
      const r = R - depth * (0.5 - 0.5 * Math.cos(petals * a));
      const x = (cx + r * Math.cos(a - Math.PI / 2)).toFixed(2);
      const y = (cy + r * Math.sin(a - Math.PI / 2)).toFixed(2);
      d += (i === 0 ? 'M ' : ' L ') + x + ' ' + y;
    }
    return d + ' Z';
  }

  const M3_SHAPES = [
    scallopedPath(50, 50, 48, 9, 7.5),   // Cookie9Sided
    scallopedPath(50, 50, 48, 7, 8.5),   // Cookie7Sided
    scallopedPath(50, 50, 48, 4, 12.0),  // Clover4Leaf
    scallopedPath(50, 50, 48, 12, 8.0),  // Sunny
    scallopedPath(50, 50, 48, 8, 11.5),  // Flower
    scallopedPath(50, 50, 48, 6, 8.0),   // Cookie6Sided
    'M50 3 C64 3 76 12 86 22 C95 31 97 42 97 50 C97 58 95 69 86 78 C76 88 64 97 50 97 C36 97 24 88 14 78 C5 69 3 58 3 50 C3 42 5 31 14 22 C24 12 36 3 50 3 Z'
  ];

  function svgEl(tag, attrs = {}) {
    const n = document.createElementNS(SVG_NS, tag);
    for (const [k, v] of Object.entries(attrs)) {
      n.setAttribute(k, String(v));
    }
    return n;
  }

  function makeIconSvg(iconKey, size = 22) {
    const s = svgEl('svg', { viewBox: '0 0 24 24', width: size, height: size, fill: 'currentColor' });
    s.appendChild(svgEl('path', { d: ICONS[iconKey] || ICONS.bolt }));
    return s;
  }

  /* Renders AppLogo.kt styles with authentic transparent punched-out O */
  function createAppLogo(styleName, size = 48) {
    const maskId = 'logo-mask-' + styleName + '-' + Math.random().toString(36).slice(2, 7);
    const s = svgEl('svg', { viewBox: '0 0 256 256', width: size, height: size });
    if (styleName === 'gem') {
      s.appendChild(svgEl('path', {
        fill: '#5B6B7B',
        d: 'M128 18 C150 18 168 30 188 42 C208 54 220 66 226 92 C232 118 226 138 226 164 C226 190 208 202 188 214 C168 226 150 238 128 238 C106 238 88 226 68 214 C48 202 30 190 30 164 C30 138 24 118 30 92 C36 66 48 54 68 42 C88 30 106 18 128 18 Z'
      }));
      s.appendChild(svgEl('circle', {
        cx: 128, cy: 128, r: 42, fill: 'none', stroke: '#AEBCC9', 'stroke-width': 20
      }));
      return s;
    }

    const defs = svgEl('defs');
    const mask = svgEl('mask', { id: maskId });
    mask.appendChild(svgEl('rect', { width: 256, height: 256, fill: '#fff' }));
    mask.appendChild(svgEl('circle', { cx: 128, cy: 128, r: 46, fill: '#000' }));
    defs.appendChild(mask);
    s.appendChild(defs);

    const g = svgEl('g', { mask: 'url(#' + maskId + ')' });
    if (styleName === 'flower') {
      g.appendChild(svgEl('path', { fill: '#5B6B7B', d: scallopedPath(128, 128, 116, 8, 32) }));
    } else if (styleName === 'cookie') {
      g.appendChild(svgEl('path', { fill: '#647585', d: scallopedPath(128, 128, 116, 9, 20) }));
    } else if (styleName === 'sunny') {
      g.appendChild(svgEl('path', { fill: '#516170', d: scallopedPath(128, 128, 116, 12, 24) }));
    } else {
      g.appendChild(svgEl('path', {
        fill: '#4A5866',
        d: 'M 118.00 -2.60 C 143.13 0.07 165.90 43.45 183.37 67.87 C 200.84 92.29 227.50 122.36 222.80 143.92 C 218.10 165.47 181.69 182.41 155.18 197.20 C 128.67 211.99 87.64 241.49 63.75 232.66 C 39.85 223.83 17.00 174.37 11.81 144.24 C 6.62 114.11 14.89 76.36 32.59 51.89 C 50.29 27.42 92.87 -5.26 118.00 -2.60 Z'
      }));
      g.appendChild(svgEl('path', {
        fill: '#5B6B7B', 'fill-opacity': '0.92',
        d: 'M 140.00 49.04 C 169.05 49.44 211.58 37.24 225.94 52.06 C 240.29 66.89 226.29 109.53 226.12 138.00 C 225.94 166.47 239.25 203.70 224.89 222.89 C 210.54 242.09 167.52 253.95 140.00 253.17 C 112.48 252.39 75.15 237.40 59.79 218.21 C 44.44 199.01 49.20 166.09 47.85 138.00 C 46.49 109.91 36.30 64.48 51.65 49.65 C 67.01 34.83 110.95 48.64 140.00 49.04 Z'
      }));
    }
    s.appendChild(g);
    return s;
  }

  function createShapedIcon(seed, opts = {}) {
    const h = hashSeed(seed);
    const shapeD = M3_SHAPES[h % M3_SHAPES.length];
    const iconKey = opts.iconKey || PROFILE_ICON_KEYS[h % PROFILE_ICON_KEYS.length];
    const bgFill = opts.bg || 'var(--secondary-container)';
    const fgColor = opts.fg || 'var(--on-secondary-container)';
    const wrap = el('div', {
      className: 'shaped-icon' + (opts.large ? ' shaped-icon--lg' : '') + (opts.spin ? ' shaped-icon--spin' : '')
    });
    const bgSvg = svgEl('svg', { viewBox: '0 0 100 100', class: 'shaped-icon__bg' });
    bgSvg.appendChild(svgEl('path', { d: shapeD, fill: bgFill }));
    const fgSvg = makeIconSvg(iconKey, opts.large ? 26 : 22);
    fgSvg.setAttribute('class', 'shaped-icon__fg');
    fgSvg.style.color = fgColor;
    wrap.appendChild(bgSvg);
    wrap.appendChild(fgSvg);
    return wrap;
  }

  function el(tag, attrs = {}, children = []) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null) continue;
      if (k === 'text') node.textContent = String(v);
      else if (k === 'className') node.className = String(v);
      else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v);
      else node.setAttribute(k, String(v));
    }
    for (const child of children) {
      if (child == null) continue;
      node.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
    }
    return node;
  }

  function toast(msg) {
    const bar = document.getElementById('snackbar');
    bar.textContent = msg;
    bar.classList.add('snackbar--show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => bar.classList.remove('snackbar--show'), 2900);
  }

  function openModal(id) {
    document.getElementById(id).classList.add('scrim--open');
    document.getElementById('appShell').classList.add('app-shell--blurred');
  }

  function closeModal(id) {
    document.getElementById(id).classList.remove('scrim--open');
    const anyOpen = document.querySelectorAll('.scrim--open').length > 0;
    if (!anyOpen) document.getElementById('appShell').classList.remove('app-shell--blurred');
  }

  /* ── Apply Theme & Preferences ── */
  function applyAppearance() {
    const entry = THEMES[uiPrefs.theme] || THEMES.pink;
    const isLight = uiPrefs.night === 'light';
    const isAmoled = uiPrefs.night === 'amoled';
    const p = isLight ? entry.light : entry.dark;
    const r = document.documentElement.style;

    r.setProperty('--bg', isAmoled ? '#000000' : p.bg);
    r.setProperty('--surface', isAmoled ? '#09090b' : p.surface);
    r.setProperty('--surface-low', isAmoled ? '#111114' : p.low);
    r.setProperty('--surface-high', isAmoled ? '#19191d' : p.high);
    r.setProperty('--surface-bright', isAmoled ? '#242429' : p.bright);
    r.setProperty('--on-surface', p.onSurface);
    r.setProperty('--on-surface-variant', p.onVariant);
    r.setProperty('--primary', p.primary);
    r.setProperty('--on-primary', p.onPrimary);
    r.setProperty('--primary-container', p.primaryContainer);
    r.setProperty('--on-primary-container', p.onPrimaryContainer);
    r.setProperty('--secondary-container', p.secondaryContainer);
    r.setProperty('--on-secondary-container', p.onSecondaryContainer);
    r.setProperty('--outline', p.outline);
    r.setProperty('--outline-variant', p.outlineVariant);

    if (isLight) {
      r.setProperty('--error', '#ba1a1a');
      r.setProperty('--on-error', '#ffffff');
      r.setProperty('--error-container', '#ffdad6');
      r.setProperty('--on-error-container', '#410002');
      r.setProperty('--ok', '#1b7e3c');
      r.setProperty('--ok-container', '#c8f5d4');
      r.setProperty('--on-ok-container', '#072711');
      r.setProperty('--warn', '#946200');
      r.setProperty('--warn-container', '#ffdf9e');
      r.setProperty('--on-warn-container', '#2c1b00');
    } else {
      r.setProperty('--error', '#ffb4ab');
      r.setProperty('--on-error', '#690005');
      r.setProperty('--error-container', '#93000a');
      r.setProperty('--on-error-container', '#ffdad6');
      r.setProperty('--ok', '#6dd58c');
      r.setProperty('--ok-container', '#133822');
      r.setProperty('--on-ok-container', '#b7f3c6');
      r.setProperty('--warn', '#f7be57');
      r.setProperty('--warn-container', '#402d05');
      r.setProperty('--on-warn-container', '#ffdf9e');
    }

    const logoSlot = document.getElementById('brandLogoSlot');
    logoSlot.replaceChildren(createAppLogo(uiPrefs.logo, 48));

    document.querySelectorAll('#nightModeGroup .seg-btn').forEach(b =>
      b.classList.toggle('seg-btn--active', b.dataset.night === uiPrefs.night)
    );
    document.querySelectorAll('#logoStyleGroup .seg-btn').forEach(b =>
      b.classList.toggle('seg-btn--active', b.dataset.logo === uiPrefs.logo)
    );
    document.querySelectorAll('#langGroup .seg-btn').forEach(b =>
      b.classList.toggle('seg-btn--active', b.dataset.lang === uiPrefs.lang)
    );
    renderThemeSwatches();
    applyStaticCopy();
  }

  function renderThemeSwatches() {
    const wrap = document.getElementById('themeSwatches');
    wrap.replaceChildren();
    for (const [key, val] of Object.entries(THEMES)) {
      wrap.appendChild(el('button', {
        className: 'theme-swatch' + (uiPrefs.theme === key ? ' theme-swatch--active' : ''),
        onClick: () => {
          uiPrefs.theme = key;
          localStorage.setItem('owenclave_theme', key);
          applyAppearance();
          render();
        }
      }, [
        el('span', { className: 'swatch-dot', style: 'background:' + val.swatch }),
        el('span', { text: val.label })
      ]));
    }
  }

  function applyStaticCopy() {
    const tr = t();
    document.documentElement.lang = uiPrefs.lang;
    document.getElementById('modeRuleBtn').textContent = tr.modes.rule;
    document.getElementById('modeGlobalBtn').textContent = tr.modes.global;
    document.getElementById('modeDirectBtn').textContent = tr.modes.direct;
    document.getElementById('subsSectionTitle').textContent = tr.subs;
    document.getElementById('proxyGroupsSectionTitle').textContent = tr.proxyGroups;
    document.getElementById('refreshAllSubsBtn').textContent = tr.updateAll;
    document.getElementById('openAddSubBtn').textContent = tr.addSub;
    document.getElementById('batchPingBtn').textContent = tr.batchPing;
    document.getElementById('ruleSearchInput').placeholder = tr.ruleSearchPlaceholder;
    document.getElementById('logSearchInput').placeholder = tr.logSearchPlaceholder;
    document.getElementById('copyLogsBtn').textContent = tr.copyLogs;
    document.getElementById('refreshLogsBtn').textContent = tr.refreshLogs;
    document.getElementById('openSecretModalBtn').textContent = tr.apiSecretBtn;
    document.getElementById('forceReloadBtn').textContent = tr.forceRestartBtn;
    document.getElementById('saveSettingsBtn').textContent = tr.saveApplyBtn;
    renderNavBar();
    syncScreenHeader();
  }

  function renderNavBar() {
    const tr = t();
    const track = document.getElementById('navTrack');
    track.replaceChildren();
    const tabs = ['configuration', 'groups', 'route', 'logcat', 'settings'];
    for (const key of tabs) {
      const btn = el('button', {
        className: 'nav-item' + (activeScreen === key ? ' nav-item--active' : ''),
        onClick: () => switchScreen(key)
      }, [
        makeIconSvg(key, 21),
        el('span', { className: 'nav-label', text: tr.nav[key] })
      ]);
      track.appendChild(btn);
    }
  }

  function syncScreenHeader() {
    const tr = t();
    document.getElementById('screenTitle').textContent = tr.nav[activeScreen] || 'Owenclave';
    const topBtn = document.getElementById('topPrimaryActionBtn');
    if (activeScreen === 'configuration') {
      topBtn.style.display = 'inline-flex';
      topBtn.textContent = tr.addImport;
      topBtn.onclick = () => openModal('importModal');
    } else if (activeScreen === 'groups') {
      topBtn.style.display = 'inline-flex';
      topBtn.textContent = tr.addSub;
      topBtn.onclick = () => openModal('subModal');
    } else if (activeScreen === 'route') {
      topBtn.style.display = 'inline-flex';
      topBtn.textContent = tr.addRule;
      topBtn.onclick = () => openModal('ruleModal');
    } else {
      topBtn.style.display = 'none';
    }
  }

  function switchScreen(screen) {
    activeScreen = screen;
    localStorage.setItem('owenclave_screen', screen);
    ['configuration', 'groups', 'route', 'logcat', 'settings'].forEach(k => {
      document.getElementById('screen-' + k).style.display = (k === screen) ? 'block' : 'none';
    });
    renderNavBar();
    syncScreenHeader();
    if (state) render();
  }

  /* ── API Helper ── */
  async function api(path, options = {}) {
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const secret = getSecret();
    if (secret) headers['Authorization'] = 'Bearer ' + secret;
    const res = await fetch(path, Object.assign({}, options, { headers }));
    if (res.status === 401) {
      openModal('authModal');
      throw new Error('Authentication required');
    }
    const text = await res.text();
    const data = text ? JSON.parse(text) : {};
    if (!res.ok) throw new Error(data.message || data.error || ('HTTP ' + res.status));
    return data;
  }

  async function loadState() {
    try {
      state = await api('/api/state');
      closeModal('authModal');
      render();
    } catch (e) {
      if (e.message !== 'Authentication required') toast(e.message);
    }
  }

  function formatUptime(startedAt) {
    if (!startedAt || startedAt <= 0) return '';
    const sec = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    const pad = (n) => (n < 10 ? '0' + n : String(n));
    return h > 0 ? (pad(h) + ':' + pad(m) + ':' + pad(s)) : (pad(m) + ':' + pad(s));
  }

  /* Resolves which server (or DIRECT bypass) is effectively carrying traffic right now */
  function resolveEffectiveCarrier() {
    if (!state) return { name: '-', mode: 'idle', profile: null, bypassFailover: false };
    const cfg = state.configs || {};
    if (cfg.mode === 'direct') {
      return { name: 'DIRECT', mode: 'direct', profile: null, bypassFailover: false };
    }
    if (state.inFallback) {
      return { name: 'DIRECT (Bypass)', mode: 'bypass', profile: null, bypassFailover: true };
    }
    const profiles = state.profiles || [];
    const byName = new Map(profiles.map(p => [p.name, p]));
    const groups = state.groups || [];
    const byGroup = new Map(groups.map(g => [g.name, g]));

    const autoGroup = groups.find(g => g.type === 'Fallback' || g.type === 'URLTest' || g.type === 'LoadBalance');
    if (autoGroup && autoGroup.now) {
      if (autoGroup.now === 'DIRECT') {
        return { name: 'DIRECT (Bypass)', mode: 'bypass', profile: null, bypassFailover: true };
      }
      const p = byName.get(autoGroup.now);
      if (p) return { name: p.name, mode: 'proxy', profile: p, bypassFailover: false, groupName: autoGroup.name };
    }

    // Walk nested groups starting from PROXY or first group
    let cur = (byGroup.get('PROXY') || groups[0] || {}).now;
    let guard = 0;
    while (cur && byGroup.has(cur) && guard++ < 10) {
      cur = byGroup.get(cur).now;
    }
    if (cur === 'DIRECT') {
      return { name: 'DIRECT', mode: 'direct', profile: null, bypassFailover: false };
    }
    const found = (cur && byName.get(cur)) || profiles.find(p => p.selected) || profiles[0] || null;
    if (found) {
      if (found.health === 'dead' && cfg['fallback-to-direct']) {
        return { name: 'DIRECT (Bypass)', mode: 'bypass', profile: null, bypassFailover: true };
      }
      return { name: found.name, mode: 'proxy', profile: found, bypassFailover: false };
    }
    return { name: 'DIRECT', mode: 'direct', profile: null, bypassFailover: false };
  }

  /* ── Main Render ── */
  function render() {
    if (!state) return;
    const tr = t();
    const cfg = state.configs || {};
    const profiles = state.profiles || [];
    const profilesByName = new Map(profiles.map(p => [p.name, p]));

    // Subtitle listener info
    const parts = ['v' + state.version, 'mixed ' + cfg['bind-address'] + ':' + cfg['mixed-port']];
    if (cfg['tproxy-port'] > 0) parts.push('tproxy :' + cfg['tproxy-port']);
    if (cfg['interface-name']) parts.push(cfg['interface-name']);
    document.getElementById('listenerSubtitle').textContent = parts.join(' · ');

    // Segmented mode switch
    document.querySelectorAll('#modeSwitch .seg-btn').forEach(btn => {
      btn.classList.toggle('seg-btn--active', btn.dataset.mode === cfg.mode);
    });

    // Bottom bar status & Power FAB
    const bar = document.getElementById('unifiedBar');
    bar.classList.toggle('unified-bar--testing', !!batchProgress);
    const barSlot = document.getElementById('barStatusSlot');
    barSlot.replaceChildren();
    if (batchProgress) {
      barSlot.appendChild(el('span', {
        className: 'mono',
        style: 'font-size:12.5px;font-weight:700',
        text: batchProgress.done + '/' + batchProgress.total
      }));
    } else {
      const pw = el('div', { className: 'pulse-wrap' + (state.running ? ' pulse-wrap--on' : '') }, [
        el('span', { className: 'pulse-ring' }),
        el('span', { className: 'pulse-dot' })
      ]);
      barSlot.appendChild(pw);
      const upStr = state.running ? formatUptime(state.startedAt) : '';
      if (upStr) {
        barSlot.appendChild(el('span', {
          className: 'mono',
          id: 'barUptimeLabel',
          style: 'font-size:12px;color:var(--on-surface-variant)',
          text: upStr
        }));
      }
    }

    const powerFab = document.getElementById('powerTestFab');
    powerFab.className = 'power-fab' + (state.running ? '' : ' power-fab--idle');
    powerFab.replaceChildren(makeIconSvg(state.running ? 'bolt' : 'power', 24));

    // Hero card
    const carrier = resolveEffectiveCarrier();
    const hero = document.getElementById('heroCard');
    hero.className = 'hero-card' +
      (carrier.bypassFailover ? ' hero-card--bypass' : (state.running && carrier.mode === 'proxy' ? ' hero-card--connected' : ''));

    const heroIconSlot = document.getElementById('heroIconSlot');
    heroIconSlot.replaceChildren(createShapedIcon(carrier.name, {
      large: true,
      spin: state.running && carrier.mode === 'proxy',
      iconKey: carrier.bypassFailover ? 'shield' : (carrier.mode === 'direct' ? 'public' : 'bolt'),
      bg: state.running && carrier.mode === 'proxy' ? 'var(--primary)' : 'var(--secondary-container)',
      fg: state.running && carrier.mode === 'proxy' ? 'var(--on-primary)' : 'var(--on-secondary-container)'
    }));

    document.getElementById('heroCarrierName').textContent =
      carrier.bypassFailover ? tr.bypassActiveTitle : carrier.name;

    const heroSub = document.getElementById('heroSubline');
    heroSub.replaceChildren();
    if (carrier.bypassFailover) {
      heroSub.appendChild(el('span', { text: tr.bypassActiveSub }));
    } else if (carrier.profile) {
      heroSub.appendChild(el('span', { text: carrier.profile.protocol }));
      heroSub.appendChild(el('span', { className: 'mono', text: carrier.profile.address }));
    } else {
      heroSub.appendChild(el('span', { text: tr.directModeSub }));
    }

    const heroPills = document.getElementById('heroPills');
    heroPills.replaceChildren();
    if (carrier.profile && carrier.profile.delay) {
      heroPills.appendChild(el('span', { className: 'pill pill--ok mono', text: carrier.profile.delay + ' ms' }));
    }
    const aliveNodes = profiles.filter(p => p.health === 'alive').length;
    if (profiles.length > 0) {
      heroPills.appendChild(el('span', {
        className: 'pill mono',
        text: aliveNodes + '/' + profiles.length + ' ' + tr.aliveCount
      }));
    }
    if (cfg['fallback-to-direct']) {
      heroPills.appendChild(el('span', { className: 'pill', text: tr.failoverProtected }));
    }

    renderProfilesSection(profiles, carrier);
    renderGroupsAndSubsSection(profilesByName);
    renderRulesSection();
    renderLogcatSection();
    renderSettingsInputs(cfg);
  }

  function renderProfilesSection(profiles, carrier) {
    const tr = t();
    document.getElementById('profilesSectionTitle').textContent = tr.servers + ' (' + profiles.length + ')';

    // Filter chips
    const strip = document.getElementById('profileFilterStrip');
    strip.replaceChildren();
    const filters = [
      { id: 'all', label: tr.filterAll },
      { id: 'alive', label: tr.filterAlive },
      { id: 'manual', label: tr.filterManual }
    ];
    for (const s of (state.subscriptions || [])) {
      filters.push({ id: 'sub:' + s.id, label: s.name });
    }
    for (const f of filters) {
      strip.appendChild(el('button', {
        className: 'filter-chip' + (profileFilter === f.id ? ' filter-chip--active' : ''),
        text: f.label,
        onClick: () => { profileFilter = f.id; renderProfilesSection(profiles, carrier); }
      }));
    }

    const filtered = profiles.filter(p => {
      if (profileFilter === 'alive') return p.health === 'alive';
      if (profileFilter === 'manual') return !p.subscriptionId;
      if (profileFilter.startsWith('sub:')) return p.subscriptionId === profileFilter.slice(4);
      return true;
    });

    const list = document.getElementById('profileCardsList');
    list.replaceChildren();
    if (filtered.length === 0) {
      list.appendChild(el('div', { className: 'empty-state' }, [
        createShapedIcon('empty', { large: true, iconKey: 'cloud' }),
        el('div', { text: tr.noProfiles })
      ]));
      return;
    }

    for (const p of filtered) {
      const isCarrier = carrier.profile && carrier.profile.id === p.id;
      const isSelected = !!p.selected || isCarrier;
      const isPinging = pingingIds.has(p.id);

      const tags = el('div', { className: 'profile-tags' }, [
        el('span', { className: 'proto-label', text: p.protocol })
      ]);
      if (isPinging) {
        tags.appendChild(el('span', { className: 'ping-tag mono', text: '...' }));
      } else if (p.health === 'dead') {
        tags.appendChild(el('span', { className: 'ping-tag ping-tag--dead', text: tr.offline }));
      } else if (p.delay) {
        tags.appendChild(el('span', { className: 'ping-tag ping-tag--alive mono', text: p.delay + ' ms' }));
      }

      const btnClass = 'icon-btn' + (isSelected ? ' icon-btn--on-primary' : '');
      const actions = el('div', { className: 'profile-actions' }, [
        el('button', {
          className: btnClass,
          title: 'Ping',
          onClick: async (ev) => {
            ev.stopPropagation();
            pingingIds.add(p.id);
            renderProfilesSection(profiles, carrier);
            try {
              const res = await api('/proxies/' + encodeURIComponent(p.name) + '/delay?timeout=5000');
              toast(p.name + ': ' + res.delay + ' ms');
            } catch (err) {
              toast(p.name + ': ' + err.message);
            } finally {
              pingingIds.delete(p.id);
              await loadState();
            }
          }
        }, [makeIconSvg('ping', 18)]),
        p.shareUri ? el('button', {
          className: btnClass,
          title: 'Share URI',
          onClick: async (ev) => {
            ev.stopPropagation();
            await navigator.clipboard.writeText(p.shareUri);
            toast(tr.copiedUri);
          }
        }, [makeIconSvg('share', 18)]) : null,
        p.removable ? el('button', {
          className: btnClass + ' icon-btn--danger',
          title: 'Delete',
          onClick: async (ev) => {
            ev.stopPropagation();
            await api('/api/profiles/' + encodeURIComponent(p.id), { method: 'DELETE' });
            await loadState();
          }
        }, [makeIconSvg('trash', 18)]) : null
      ]);

      const card = el('div', {
        className: 'profile-card' + (isSelected ? ' profile-card--selected' : ''),
        onClick: async () => {
          try {
            await api('/api/profiles/select', { method: 'POST', body: JSON.stringify({ id: p.id }) });
            await loadState();
          } catch (err) { toast(err.message); }
        }
      }, [
        createShapedIcon(p.name, {
          spin: state.running && isCarrier,
          bg: isSelected ? 'var(--primary)' : 'var(--secondary-container)',
          fg: isSelected ? 'var(--on-primary)' : 'var(--on-secondary-container)'
        }),
        el('div', { className: 'profile-body' }, [
          el('div', { className: 'profile-name round-type', text: p.name }),
          el('div', { className: 'profile-addr mono', text: p.address }),
          tags
        ]),
        actions
      ]);
      list.appendChild(card);
    }
  }

  function renderGroupsAndSubsSection(profilesByName) {
    const tr = t();
    const subsList = document.getElementById('subsCardsList');
    subsList.replaceChildren();
    const subs = state.subscriptions || [];
    if (subs.length === 0) {
      subsList.appendChild(el('div', { className: 'empty-state' }, [el('div', { text: tr.noSubs })]));
    } else {
      for (const s of subs) {
        const isUpdating = updatingSubIds.has(s.id);
        const updatedText = s.lastUpdated > 0 ? new Date(s.lastUpdated).toLocaleString() : tr.never;
        const cardChildren = [];
        if (isUpdating) cardChildren.push(el('div', { className: 'sub-progress' }));
        cardChildren.push(el('div', { className: 'sub-main' }, [
          createShapedIcon(s.name, {
            iconKey: 'refresh',
            bg: 'var(--tertiary-container)',
            fg: 'var(--on-tertiary-container)'
          }),
          el('div', { className: 'profile-body' }, [
            el('div', { className: 'profile-name round-type', text: s.name }),
            el('div', { className: 'profile-addr mono', text: s.url }),
            el('div', { className: 'profile-tags' }, [
              el('span', { className: 'proto-label', text: s.profileCount + ' servers' }),
              el('span', { className: 'proto-label', text: tr.updatedAt + updatedText })
            ])
          ]),
          el('div', { className: 'profile-actions' }, [
            el('button', {
              className: 'icon-btn',
              title: 'Refresh',
              onClick: async () => {
                updatingSubIds.add(s.id);
                renderGroupsAndSubsSection(profilesByName);
                try {
                  await api('/api/subscriptions/' + encodeURIComponent(s.id) + '/refresh', { method: 'POST' });
                  toast(s.name + ' OK');
                } catch (err) {
                  toast(err.message);
                } finally {
                  updatingSubIds.delete(s.id);
                  await loadState();
                }
              }
            }, [makeIconSvg('refresh', 18)]),
            el('button', {
              className: 'icon-btn icon-btn--danger',
              title: 'Delete',
              onClick: async () => {
                await api('/api/subscriptions/' + encodeURIComponent(s.id), { method: 'DELETE' });
                await loadState();
              }
            }, [makeIconSvg('trash', 18)])
          ])
        ]));
        if (s.lastError) {
          cardChildren.push(el('div', { className: 'sub-error-note', text: tr.keptSessionNote + s.lastError }));
        }
        subsList.appendChild(el('div', { className: 'sub-card' }, cardChildren));
      }
    }

    // Mihomo Proxy Groups in M3E grouped stack
    const gStack = document.getElementById('groupsStack');
    gStack.replaceChildren();
    for (const g of (state.groups || [])) {
      const choicesWrap = el('div', { className: 'choice-grid' });
      for (const ch of (g.all || [])) {
        const node = profilesByName.get(ch);
        const pillKids = [];
        if (node) {
          pillKids.push(el('span', {
            className: 'health-dot' +
              (node.health === 'alive' ? ' health-dot--alive' : (node.health === 'dead' ? ' health-dot--dead' : ''))
          }));
        }
        pillKids.push(el('span', { text: ch }));
        if (node && node.delay && node.health !== 'dead') {
          pillKids.push(el('span', { className: 'mono', style: 'font-size:11.5px;opacity:0.75', text: node.delay + 'ms' }));
        }
        choicesWrap.appendChild(el('button', {
          className: 'choice-pill' + (g.now === ch ? ' choice-pill--active' : ''),
          onClick: async () => {
            try {
              await api('/proxies/' + encodeURIComponent(g.name), {
                method: 'PUT',
                body: JSON.stringify({ name: ch })
              });
              await loadState();
            } catch (err) { toast(err.message); }
          }
        }, pillKids));
      }
      gStack.appendChild(el('div', { className: 'm3-stack-item' }, [
        el('div', { className: 'group-head' }, [
          el('span', { className: 'group-name round-type', text: g.name }),
          el('span', { className: 'group-meta', text: g.type + ' · ' + g.now })
        ]),
        choicesWrap
      ]));
    }
  }

  function renderRulesSection() {
    if (!state) return;
    const tr = t();
    const allRules = state.rules || [];
    const strip = document.getElementById('ruleFilterStrip');
    strip.replaceChildren();
    const kinds = [
      { id: 'all', label: tr.filterAll + ' (' + allRules.length + ')' },
      { id: 'user', label: tr.filterUserRules },
      { id: 'proxy', label: 'Proxy' },
      { id: 'direct', label: 'Direct / Bypass' },
      { id: 'reject', label: 'Reject' }
    ];
    for (const k of kinds) {
      strip.appendChild(el('button', {
        className: 'filter-chip' + (ruleFilterKind === k.id ? ' filter-chip--active' : ''),
        text: k.label,
        onClick: () => { ruleFilterKind = k.id; renderRulesSection(); }
      }));
    }

    const q = (document.getElementById('ruleSearchInput').value || '').trim().toLowerCase();
    const filtered = allRules.filter(r => {
      const targetUpper = (r.proxy || '').toUpperCase();
      const isDirect = targetUpper === 'DIRECT' || targetUpper.includes('БЕЗ VPN') || targetUpper.includes('BYPASS');
      const isReject = targetUpper === 'REJECT' || targetUpper === 'BLOCK';
      if (ruleFilterKind === 'user' && !r.userDefined) return false;
      if (ruleFilterKind === 'direct' && !isDirect) return false;
      if (ruleFilterKind === 'reject' && !isReject) return false;
      if (ruleFilterKind === 'proxy' && (isDirect || isReject)) return false;
      if (!q) return true;
      return (r.type || '').toLowerCase().includes(q) ||
        (r.payload || '').toLowerCase().includes(q) ||
        (r.proxy || '').toLowerCase().includes(q);
    });

    const stack = document.getElementById('rulesStack');
    stack.replaceChildren();
    if (filtered.length === 0) {
      stack.appendChild(el('div', { className: 'empty-state' }, [el('div', { text: tr.noRules })]));
      return;
    }

    for (const r of filtered.slice(0, 350)) {
      const targetUpper = (r.proxy || '').toUpperCase();
      const isDirect = targetUpper === 'DIRECT' || targetUpper.includes('БЕЗ VPN') || targetUpper.includes('BYPASS');
      const isReject = targetUpper === 'REJECT' || targetUpper === 'BLOCK';
      const pillClass = 'pill ' + (isDirect ? 'pill--ok' : (isReject ? 'pill--dead' : 'pill--primary'));

      stack.appendChild(el('div', { className: 'm3-stack-item rule-row' }, [
        el('div', { className: 'rule-left' }, [
          el('span', { className: 'rule-type mono', text: r.type }),
          el('span', { className: 'rule-payload mono', text: r.payload || '*' })
        ]),
        el('div', { style: 'display:flex;align-items:center;gap:8px;flex-shrink:0' }, [
          el('span', { className: pillClass, text: r.proxy }),
          r.userDefined ? el('button', {
            className: 'icon-btn icon-btn--danger',
            onClick: async () => {
              await api('/api/rules/' + encodeURIComponent(r.id), { method: 'DELETE' });
              await loadState();
            }
          }, [makeIconSvg('trash', 17)]) : null
        ])
      ]));
    }
  }

  function renderLogcatSection() {
    if (!state) return;
    const strip = document.getElementById('logLevelStrip');
    strip.replaceChildren();
    for (const lvl of ['all', 'info', 'warning', 'error']) {
      strip.appendChild(el('button', {
        className: 'filter-chip' + (logFilterLevel === lvl ? ' filter-chip--active' : ''),
        text: lvl === 'all' ? t().filterAll : lvl,
        onClick: () => { logFilterLevel = lvl; renderLogcatSection(); }
      }));
    }

    const q = (document.getElementById('logSearchInput').value || '').trim().toLowerCase();
    const box = document.getElementById('logcatBox');
    const wasNearBottom = (box.scrollHeight - box.scrollTop - box.clientHeight) < 60;
    box.replaceChildren();
    const logs = (state.logs || []).filter(item => {
      if (logFilterLevel !== 'all' && item.type !== logFilterLevel) return false;
      if (q && !(item.payload || '').toLowerCase().includes(q)) return false;
      return true;
    });
    for (const item of logs) {
      const shortTime = (item.time || '').replace(/^.*T/, '').replace(/Z$/, '').slice(0, 12);
      box.appendChild(el('div', { className: 'log-entry log-entry--' + (item.type || 'info') }, [
        el('span', { className: 'log-ts', text: shortTime }),
        el('span', { text: item.payload })
      ]));
    }
    if (wasNearBottom) box.scrollTop = box.scrollHeight;
  }

  function renderSettingsInputs(cfg) {
    if (document.activeElement && document.activeElement.closest('#screen-settings')) return;
    document.getElementById('cfgFallbackDirect').checked = !!cfg['fallback-to-direct'];
    document.getElementById('cfgHealthInterval').value = cfg['health-check-interval'] || 15;
    document.getElementById('cfgMixedPort').value = cfg['mixed-port'] || 0;
    document.getElementById('cfgTproxyPort').value = cfg['tproxy-port'] || 0;
    document.getElementById('cfgBindAddress').value = cfg['bind-address'] || '0.0.0.0';
    document.getElementById('cfgInterface').value = cfg['interface-name'] || '';
    document.getElementById('cfgAllowLan').checked = !!cfg['allow-lan'];
    document.getElementById('cfgIpv6').checked = !!cfg['ipv6'];
    document.getElementById('cfgLogLevel').value = cfg['log-level'] || 'info';
  }

  /* ── Event Bindings ── */
  document.querySelectorAll('[data-close]').forEach(btn => {
    btn.addEventListener('click', () => closeModal(btn.dataset.close));
  });
  document.querySelectorAll('.scrim').forEach(sc => {
    sc.addEventListener('click', (e) => { if (e.target === sc) closeModal(sc.id); });
  });

  document.getElementById('brandLogoSlot').addEventListener('click', () => {
    const order = ['split', 'flower', 'cookie', 'sunny', 'gem'];
    uiPrefs.logo = order[(order.indexOf(uiPrefs.logo) + 1) % order.length];
    localStorage.setItem('owenclave_logo', uiPrefs.logo);
    applyAppearance();
  });

  document.querySelectorAll('#modeSwitch .seg-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        await api('/configs', { method: 'PATCH', body: JSON.stringify({ mode: btn.dataset.mode }) });
        await loadState();
      } catch (e) { toast(e.message); }
    });
  });

  document.querySelectorAll('#nightModeGroup .seg-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      uiPrefs.night = btn.dataset.night;
      localStorage.setItem('owenclave_night', uiPrefs.night);
      applyAppearance();
      render();
    });
  });

  document.querySelectorAll('#logoStyleGroup .seg-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      uiPrefs.logo = btn.dataset.logo;
      localStorage.setItem('owenclave_logo', uiPrefs.logo);
      applyAppearance();
    });
  });

  document.querySelectorAll('#langGroup .seg-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      uiPrefs.lang = btn.dataset.lang;
      localStorage.setItem('owenclave_lang', uiPrefs.lang);
      applyAppearance();
      render();
    });
  });

  // Import modal tabs
  document.querySelectorAll('#importModeTabs .seg-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      importTabMode = btn.dataset.imode;
      document.querySelectorAll('#importModeTabs .seg-btn').forEach(b =>
        b.classList.toggle('seg-btn--active', b === btn)
      );
      document.getElementById('importPaneLinks').style.display = (importTabMode !== 'http') ? 'block' : 'none';
      document.getElementById('importPaneHttp').style.display = (importTabMode === 'http') ? 'block' : 'none';
    });
  });

  document.getElementById('httpProtoSelect').addEventListener('change', (e) => {
    const tls = e.target.value === 'https';
    document.getElementById('httpsTlsRow').style.display = tls ? 'flex' : 'none';
    const portEl = document.getElementById('httpPort');
    if (tls && portEl.value === '8080') portEl.value = '443';
    if (!tls && portEl.value === '443') portEl.value = '8080';
  });

  document.getElementById('submitImportBtn').addEventListener('click', async () => {
    try {
      if (importTabMode === 'http') {
        const tls = document.getElementById('httpProtoSelect').value === 'https';
        await api('/api/profiles/http', {
          method: 'POST',
          body: JSON.stringify({
            tls,
            name: document.getElementById('httpName').value,
            host: document.getElementById('httpHost').value,
            port: Number(document.getElementById('httpPort').value),
            username: document.getElementById('httpUser').value,
            password: document.getElementById('httpPass').value,
            sni: document.getElementById('httpSni').value,
            allowInsecure: document.getElementById('httpInsecure').checked,
            udpOverTcp: document.getElementById('httpUot').checked
          })
        });
        document.getElementById('httpName').value = '';
        document.getElementById('httpHost').value = '';
        document.getElementById('httpPass').value = '';
      } else {
        const text = document.getElementById('importText').value;
        await api('/api/profiles/import', {
          method: 'POST',
          body: JSON.stringify({ text, raw: importTabMode === 'raw' })
        });
        document.getElementById('importText').value = '';
      }
      closeModal('importModal');
      await loadState();
    } catch (e) { toast(e.message); }
  });

  document.getElementById('openAddSubBtn').addEventListener('click', () => openModal('subModal'));

  document.getElementById('submitSubBtn').addEventListener('click', async () => {
    try {
      await api('/api/subscriptions', {
        method: 'POST',
        body: JSON.stringify({
          name: document.getElementById('subName').value,
          url: document.getElementById('subUrl').value,
          userAgent: document.getElementById('subUa').value,
          sendHwid: document.getElementById('subHwid').checked
        })
      });
      document.getElementById('subName').value = '';
      document.getElementById('subUrl').value = '';
      closeModal('subModal');
      await loadState();
    } catch (e) { toast(e.message); }
  });

  document.getElementById('refreshAllSubsBtn').addEventListener('click', async () => {
    try {
      await api('/api/subscriptions/refresh-all', { method: 'POST', body: '{}' });
      toast('OK');
      await loadState();
    } catch (e) { toast(e.message); }
  });

  document.getElementById('submitRuleBtn').addEventListener('click', async () => {
    try {
      await api('/api/rules', {
        method: 'POST',
        body: JSON.stringify({
          name: document.getElementById('ruleName').value,
          domains: document.getElementById('ruleDomains').value,
          ip: document.getElementById('ruleIp').value,
          port: document.getElementById('rulePort').value,
          network: document.getElementById('ruleNetwork').value,
          target: document.getElementById('ruleTarget').value
        })
      });
      document.getElementById('ruleName').value = '';
      document.getElementById('ruleDomains').value = '';
      document.getElementById('ruleIp').value = '';
      document.getElementById('rulePort').value = '';
      closeModal('ruleModal');
      await loadState();
    } catch (e) { toast(e.message); }
  });

  document.getElementById('ruleSearchInput').addEventListener('input', renderRulesSection);
  document.getElementById('logSearchInput').addEventListener('input', renderLogcatSection);
  document.getElementById('refreshLogsBtn').addEventListener('click', loadState);
  document.getElementById('copyLogsBtn').addEventListener('click', async () => {
    const lines = ((state && state.logs) || []).map(l => '[' + l.time + '] [' + l.type + '] ' + l.payload).join('\n');
    await navigator.clipboard.writeText(lines);
    toast(t().copiedLogs);
  });

  // Power / Bolt FAB & Batch Ping
  async function runConnectionCheck() {
    if (!state) return;
    const profiles = state.profiles || [];
    if (profiles.length === 0) return;
    batchProgress = { done: 0, total: profiles.length };
    render();
    try {
      for (const p of profiles) {
        try {
          await api('/proxies/' + encodeURIComponent(p.name) + '/delay?timeout=5000');
        } catch (_) {}
        batchProgress.done += 1;
        render();
      }
    } finally {
      batchProgress = null;
      await loadState();
    }
  }

  document.getElementById('powerTestFab').addEventListener('click', runConnectionCheck);
  document.getElementById('batchPingBtn').addEventListener('click', runConnectionCheck);

  document.getElementById('saveSettingsBtn').addEventListener('click', async () => {
    try {
      await api('/configs', {
        method: 'PATCH',
        body: JSON.stringify({
          'fallback-to-direct': document.getElementById('cfgFallbackDirect').checked,
          'health-check-interval': Number(document.getElementById('cfgHealthInterval').value),
          'mixed-port': Number(document.getElementById('cfgMixedPort').value),
          'tproxy-port': Number(document.getElementById('cfgTproxyPort').value),
          'bind-address': document.getElementById('cfgBindAddress').value,
          'interface-name': document.getElementById('cfgInterface').value,
          'allow-lan': document.getElementById('cfgAllowLan').checked,
          'ipv6': document.getElementById('cfgIpv6').checked,
          'log-level': document.getElementById('cfgLogLevel').value
        })
      });
      toast(t().savedSettings);
      await loadState();
    } catch (e) { toast(e.message); }
  });

  document.getElementById('openSecretModalBtn').addEventListener('click', () => {
    document.getElementById('secretInput').value = getSecret();
    openModal('authModal');
  });

  document.getElementById('saveSecretBtn').addEventListener('click', async () => {
    setSecret(document.getElementById('secretInput').value.trim());
    await loadState();
  });

  document.getElementById('forceReloadBtn').addEventListener('click', async () => {
    try {
      await api('/api/reload', { method: 'POST', body: '{}' });
      toast(t().reloadedCore);
      await loadState();
    } catch (e) { toast(e.message); }
  });

  // Live uptime ticker every 1s
  setInterval(() => {
    if (state && state.running && state.startedAt && !batchProgress) {
      const lbl = document.getElementById('barUptimeLabel');
      if (lbl) lbl.textContent = formatUptime(state.startedAt);
    }
  }, 1000);

  applyAppearance();
  switchScreen(activeScreen);
  loadState();
  setInterval(() => { if (document.visibilityState === 'visible') loadState(); }, 6000);
})();
</script>
</body>
</html>
""".trimIndent()

}
