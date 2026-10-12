---
name: KGG MP3
description: A Liquid Glass-inspired local audio workspace on Windows Swing.
colors:
  surface: "#FFFFFF"
  text: "#1D1D1F"
  muted: "#626269"
  primary: "#0064D2"
  primary-hover: "#0056B7"
  focus: "#007AFF"
  navigation-selected: "#D8E7FB"
  navigation-text: "#005BBF"
  table-selected: "#E6F0FF"
  table-header: "#FAFAFC"
  table-row: "#FFFFFFB4"
  table-row-alternate: "#F7F9FDB4"
  table-divider: "#E5E5EA"
  progress-track: "#E6EFFB"
  success: "#22A06B"
  success-text: "#167451"
  warning: "#B54708"
  backdrop-blue: "#F0F5FF"
  backdrop-violet: "#F4F0F9"
  haze-blue: "#6EAAF464"
  haze-violet: "#A589DF5A"
  haze-white: "#FFFFFFD2"
  glass-top: "#FFFFFFDC"
  glass-bottom: "#F6FAFF9C"
  rim-top: "#FFFFFFFA"
  rim-bottom: "#FFFFFF69"
  rim-inner: "#FFFFFF78"
  pointer-light: "#FFFFFF6E"
  layer-tint: "#1E365A03"
typography:
  display:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "26pt"
    fontWeight: 700
  headline:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "22pt"
    fontWeight: 700
  inspector-title:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "18pt"
    fontWeight: 700
  title:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "15pt"
    fontWeight: 700
  body:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "14pt"
    fontWeight: 400
  label:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "13pt"
    fontWeight: 400
  supporting:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "12pt"
    fontWeight: 400
  metadata:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "11pt"
    fontWeight: 400
rounded:
  glass-panel: "14px"
  navigation: "6px"
  control-default: "6px"
spacing:
  compact: "8px"
  inset: "10px"
  label-gap: "12px"
  icon-gap: "14px"
  toolbar-gap: "16px"
  sidebar-horizontal: "18px"
  inspector-horizontal: "20px"
  panel-bottom: "22px"
  inspector-gap: "24px"
  header-gap: "26px"
  library-gap: "28px"
  section-gap: "30px"
  setting-group: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    padding: "9px 20px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
  import-card:
    textColor: "{colors.text}"
    rounded: "{rounded.glass-panel}"
    padding: "30px 20px 26px"
    height: "230px"
  settings-inspector:
    textColor: "{colors.text}"
    rounded: "{rounded.glass-panel}"
    padding: "24px 20px 22px"
    width: "250px"
  sidebar:
    textColor: "{colors.text}"
    rounded: "{rounded.glass-panel}"
    padding: "30px 18px 24px"
    width: "190px"
  navigation-selected:
    backgroundColor: "{colors.navigation-selected}"
    textColor: "{colors.navigation-text}"
    rounded: "{rounded.navigation}"
  output-field:
    typography: "{typography.body}"
    height: "36px"
  queue-row:
    backgroundColor: "{colors.table-row}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    height: "56px"
  queue-row-selected:
    backgroundColor: "{colors.table-selected}"
    textColor: "{colors.text}"
---

# Design System: KGG MP3

## Overview

**Creative North Star: "Light Through the Music Workspace"**

The user-selected Liquid Glass-inspired world uses procedural pale blue/violet light beneath translucent white panels, layered white rim highlights, and a pointer-responsive specular spot. Dark Chinese typography and restrained blue actions keep conversion controls readable. The rejected flat-light direction is superseded by this material language.

This remains a Windows Java Swing desktop application with FlatMacLightLaf controls and native Windows window management. Glass is drawn inside the app with Java2D alpha gradients and layered shapes: there is no macOS compositor integration, backdrop blur, optical refraction, or transparency to other desktop windows. The source is canonical; supplied previews cover 1200 × 820, 1080 × 820, and the 1080 × 740 minimum. A fresh finish review is handled separately.

**Key Characteristics:**

- Procedural blue/violet light fields and translucent white work panels.
- Thin layered white rims and pointer-responsive highlights.
- Readable Chinese type, a real queue table, and a distinct right-side inspector.
- Native Windows behavior and unchanged local audio conversion.

## Colors

Cool white glass transmits procedural blue/violet light while solid text and action colors anchor the task.

### Primary

Action Blue (`primary`) fills the conversion action, import arrow, and progress. Deep Action Blue (`primary-hover`) is its enabled hover state. Focus Blue (`focus`) is the separate FlatLaf keyboard-focus color and default-button fallback. Pale Navigation Selection and Pale Queue Selection identify context without overwhelming text; Navigation Blue colors the selected sidebar label. Progress Mist remains beneath the completed progress.

### Neutral

White Control Surface, Charcoal, and Slate support readable controls. Quiet Table Header, Translucent White Row, Translucent Cool Row, and Queue Top Divider structure the real table. The row alpha values are intentional, not opaque white-card replacements.

### Material and ambient light

Pale Blue Backdrop and Pale Violet Backdrop define the root's diagonal gradient. Blue Haze, Violet Haze, and White Haze define radial fields. White Glass Top and Cool Glass Bottom define the diagonal panel fill; Bright Rim and Fading Rim define the outer highlight, Inner Rim adds a second contour, Pointer Light follows the pointer, and Layer Tint supplies subtle layered depth.

Eight-digit hex values preserve the Java Color alpha byte exactly; alpha is not a separate invented opacity token. The visible result depends on compositing and cannot be reduced to one flat swatch. The sidecar's synthesized OKLCH tonal ramps are non-normative preview strips, not shipped color scales.

### Semantic states

Ready Green marks the status dot; Dark Ready Green carries status text. Engine Warning marks missing-engine state. Text explains readiness, so color is never the only signal.

**The Action Separation Rule.** Keep action, keyboard focus, selection, and ambient blue distinct; ambient light is not an interactive state.

## Typography

Microsoft YaHei UI is the requested family throughout Swing UIManager. Default plain type is 14 points. The frontmatter uses Java font points, not physical screen pixels or a claim of CSS rendering. Java supplies platform font substitution if the requested font is unavailable; no explicit fallback stack exists.

- Display: bold 26-point main heading.
- Headline: bold 22-point import heading.
- Inspector title: bold 18-point settings heading; the sidebar wordmark shares this size.
- Title: bold 15-point queue heading.
- Body: plain 14-point global controls, table, and import supporting text. The conversion button and selected navigation use bold 14-point variants.
- Label: plain 13-point inspector labels and source-folder checkbox.
- Supporting: plain 12-point hints, empty-queue instructions, and summary; readiness uses a bold variant.
- Metadata: plain 11-point sidebar version.

No custom tracking, line-height, web font, or motion-dependent text treatment is implemented.

## Layout

Swing BorderLayout and BoxLayout compose the window. Initial outer size is 1200 × 820; minimum outer size is 1080 × 740. These are logical dimensions including native window decoration. The root has 10-unit insets. A 190-wide sidebar sits left of the content wrapper. The wrapper uses top/right/bottom/left insets 26/28/24/28 and a 26-unit header gap.

The library fills the middle with a 230-high import surface above an expanding queue, separated by 28. A separate 250-wide inspector sits to the right across a 24-unit gutter. Inspector controls are vertically stacked; the primary action is at its bottom, not below the queue. Surface strategy remains in `.impeccable/surfaces/desktop.md`.

Queue rows are 56 high, header 38 high, and cell left/right insets are 12/10. Preferred file/size/state/progress column widths are 230/74/155/88. File and state cell tooltips reveal truncated text. The queue wrapper, table, scroll pane, and viewport are non-opaque; row renderers provide translucent fills. Quality selector is 38 high, path field 36 high, spinner 34 high, primary action 44 high, and cancel 36 high. The primary action prefers width 210 and permits inspector-width expansion.

There are no CSS breakpoints, mobile layout, or desktop sidebar-collapse behaviors. Keep the explicit desktop minimum rather than promising unimplemented smaller-window support.

## Elevation & Depth

The root painter creates a diagonal blue-to-violet background. Three radial fields center at normalized coordinates (0.28, 0.15), (0.79, 0.80), and (0.58, 0.45); their radii scale to 0.50, 0.48, and 0.34 times root width. Each field fades through alpha/3 at fraction 0.58 to transparent at fraction 1.

RoundedPanel enters the glass branch when all base RGB channels exceed 225. It paints five inset rounded layers using Layer Tint, then a diagonal translucent glass fill. A vertical outer rim gradient uses stroke width 1.2; a second white contour sits inset at 3. No sampled-background blur is performed. Blue selected navigation stays in the solid branch because its red channel does not satisfy the threshold.

Mouse movement on a panel updates a clipped radial white highlight with radius 150 logical units and requests repaint. Mouse exit removes the highlight. There is no animation timer, easing, spring interpolation, or guarantee that the effect follows the pointer over child controls. Native controls and dialogs use FlatMacLightLaf's own rendering.

**The Honest Glass Rule.** Describe the implemented translucency and highlights, never unimplemented blur, optical refraction, or macOS compositor effects.

## Shapes

Sidebar, import, and inspector panels use Java2D arc diameter 28, corresponding to corner radius 14. Selected navigation uses arc diameter 12, corresponding to radius 6. The import border repeats the 28-diameter contour. Frontmatter rounded values are corner radii, not Java2D arc diameters.

FlatLaf component/text-component default arcs are 12 and button arc is 14. Buttons also request `JButton.buttonType=roundRect`, so effective button shape is delegated to the theme; do not assert a custom 7-unit button radius. Keyboard focus width is 2. Glass rounding does not change native input, selector, spinner, table, or dialog semantics.

## Components

### Buttons

The main conversion action uses solid Action Blue and white text, bold 14-point type, margin 9/20, and preferred size 210 × 44. Enabled hover uses Deep Action Blue. Mouse exit restores the base blue; no custom transition duration exists.

Secondary controls remain white/charcoal round-rectangles. FlatMacLightLaf owns their borders, hover, pressed, disabled, and focus states. Choose music is 160 × 36; clear is 100 × 32; change-folder maximum height is 32; cancel prefers 210 × 36 and appears only during processing.

### Glass panels

Sidebar, import, and settings inspector share the custom translucent fill and rim painter with distinct padding. The queue is an unboxed transparent work area, not another glass card. Preserve this difference in surface density.

### Settings inspector

Quality, output path, source-folder mode, concurrency, and conversion action form a real right-side inspector. Native JComboBox, JTextField, JCheckBox, and JSpinner controls retain their semantics. Source-folder mode disables separate-output controls; processing disables mutable settings and shows cancel. Blank output resolves to the Music/KGG MP3 folder.

### Navigation

The pale blue selected row is a static context JPanel label, not an interactive multi-route navigation system. No hover or mobile-navigation state exists. Open output is a separate real desktop-folder action.

### Queue and progress

The real JTable shows file, size, state/detail, and progress. Translucent alternate rows, tooltips, solid selected rows, and a thin top divider preserve scanability over the light fields. The JProgressBar displays a percentage above zero and an em dash at zero. Empty copy directs the user to the right-side settings. Completion updates summary text and emits a system beep.

### Import and identity

A Java2D blue stroke arrow (40 × 36) leads file import. Engine dots are 8 × 8. Runtime icons are drawn by `MainFrame.createAppIcons()` at 16/32/48/64/128/256 sizes; the sidebar scales the 64-sized icon to 36 × 36. Shipping `assets/app.ico` and `assets/app.png` originate from `scripts/create-icon.ps1`: procedural project artwork, not AI-generated imagery. The glass backdrop is also procedural Java2D artwork, with no raster asset or third-party image.

## Do's and Don'ts

### Do:

- Do preserve the user-selected Liquid Glass-inspired material rather than reverting to flat-light panels.
- Do keep dark readable Chinese text and clear blue conversion actions above the light fields.
- Do use the actual Java2D alpha, rim, and pointer-light behavior when extending glass panels.
- Do preserve native Windows window management, keyboard focus, visible text status, and local converter safety.
- Do treat Java source and FlatMacLightLaf rendering as canonical.

### Don't:

- Don't describe app-painted glass as native macOS compositor blur, optical refraction, or desktop-window transparency.
- Don't replace native Windows chrome with fake Apple traffic-light controls.
- Don't invent web breakpoints, timed spring animation, or unsupported native platform metadata.
- Don't use glass decoration to obscure filenames, state detail, settings, or the primary action.
- Don't claim GPU MP3 encoding, audio uploads, or AI-generated provenance for procedural project icons.
