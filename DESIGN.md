---
name: KGG MP3
description: A restrained macOS-inspired local audio workspace on Windows Swing.
colors:
  workspace: "#F5F5F7"
  surface: "#FFFFFF"
  sidebar: "#ECECEF"
  text: "#1D1D1F"
  muted: "#626269"
  primary: "#0064D2"
  primary-hover: "#0056B7"
  focus: "#007AFF"
  navigation-selected: "#D8E7FB"
  navigation-text: "#005BBF"
  table-selected: "#E6F0FF"
  table-subtle: "#FAFAFC"
  progress-track: "#E6EFFB"
  sidebar-divider: "#DDDDDF"
  table-border: "#ECEEF3"
  import-border: "#D9E2EF"
  success: "#22A06B"
  success-text: "#167451"
  warning: "#B54708"
typography:
  display:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "28pt"
    fontWeight: 700
  headline:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "18pt"
    fontWeight: 700
  title:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "15pt"
    fontWeight: 700
  body:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "13pt"
    fontWeight: 400
  label:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "12pt"
    fontWeight: 400
  metadata:
    fontFamily: "Microsoft YaHei UI"
    fontSize: "11pt"
    fontWeight: 400
rounded:
  panel: "8px"
  navigation: "6px"
  control-default: "6px"
spacing:
  compact: "6px"
  small: "8px"
  label-gap: "12px"
  card-vertical: "14px"
  card-horizontal: "16px"
  section: "18px"
  action-horizontal: "20px"
  bottom-inset: "24px"
  content-horizontal: "28px"
  top-inset: "30px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    padding: "9px 20px"
    width: "150px"
    height: "40px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
    padding: "14px 16px"
  import-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
    padding: "18px 20px"
    height: "180px"
  navigation-selected:
    backgroundColor: "{colors.navigation-selected}"
    textColor: "{colors.navigation-text}"
    rounded: "{rounded.navigation}"
  output-field:
    typography: "{typography.body}"
    height: "34px"
  queue-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    height: "48px"
  queue-row-selected:
    backgroundColor: "{colors.table-selected}"
    textColor: "{colors.text}"
---

# Design System: KGG MP3

## Overview

**Creative North Star: "A Quiet Music Workspace"**

A restrained macOS-inspired visual world expresses the user's pinned Apple-like direction: pale neutrals, white work areas, charcoal Chinese typography, and precise blue actions. This is a Windows desktop application implemented in Java Swing with FlatLaf, not a macOS application, browser surface, iOS app, or Android app. Windows window management stays intact.

Calm comes from tonal separation and generous spacing around task-dense controls. The real conversion queue remains central; styling must preserve local processing, source preservation, and the existing conversion workflow. This document records code evidence from MainFrame.java and GkkMp3App.java. Finish review covered empty and waiting queues at window widths 1200 and 1080; processing, completion, and failure layouts are code-observed rather than visually verified.

**Key Characteristics:**

- Quiet neutral surfaces with blue reserved for actions, selection, and progress.
- Readable Chinese system typography and a real, scrollable queue table.
- Rounded custom panels without painted shadows or decorative window chrome.
- Local-first desktop behavior and preserved Windows affordances.

## Colors

Cool pale neutrals provide the workspace; distinct blues distinguish action, selection, and keyboard focus.

### Primary

- Action Blue (`primary`) fills the conversion action, import arrow, and progress value; Deep Action Blue (`primary-hover`) is its enabled hover state.
- Focus Blue (`focus`) is the FlatLaf keyboard-focus color and default-button fallback, not the manually styled conversion button fill.
- Pale Selection Blue (`navigation-selected`, `table-selected`) identifies sidebar context and selected table rows; Navigation Blue (`navigation-text`) retains readable selected text.
- Progress Mist (`progress-track`) separates completed and remaining conversion progress.

### Neutral

- Cool Workspace (`workspace`) surrounds white work surfaces (`surface`).
- Sidebar Silver (`sidebar`) separates supporting controls from the main task.
- Charcoal (`text`) carries primary labels; Slate (`muted`) carries descriptions and supporting labels.
- Quiet Table (`table-subtle`) is the table-header and alternating-row surface.
- Sidebar Divider, Table Border, and Import Border are structural strokes, not decorative outlines around every element.

### Semantic states

- Ready Green (`success`) marks the engine status dot; Dark Ready Green (`success-text`) carries its readable text.
- Engine Warning (`warning`) marks missing-engine status. Text explains the condition so color is not the only signal.

**The Action Separation Rule.** Do not conflate primary action blue, keyboard focus blue, and pale selected surfaces; their roles are distinct in the implementation.

The sidecar's eight-step OKLCH ramps are synthesized swatch previews, not implemented palette tokens. It contains native source descriptions rather than fabricated HTML/CSS versions of Swing components; a web live panel cannot render these as canonical native controls.

## Typography

Microsoft YaHei UI is the single requested family, installed through Swing's UIManager. The implementation uses Java's platform font substitution when that family is unavailable; no explicit fallback stack is defined. Font sizes are Java font points, represented as point values in the portable frontmatter; they are not CSS rendering instructions or physical screen pixels.

- Display is the main window heading.
- Headline is the import-area heading.
- Title is the queue heading.
- Body is the global control and table text.
- Label is supporting hints and settings labels; engine status makes this size bold.
- Metadata is the version and conversion summary.
- The sidebar wordmark uses bold type at 20 logical units; selected navigation uses bold type at 14, and the empty-queue headline uses plain type at 14.

No custom line-height, letter-spacing, web font, or monospace system is implemented. Preserve Chinese readability rather than introducing a display-font pairing.

## Layout

The desktop surface uses BorderLayout, BoxLayout, and GridBagLayout rather than CSS breakpoints. The window starts at 1200 × 820 and has a minimum outer size of 1080 × 740. These are Swing logical dimensions; content height excludes native window decoration.

The sidebar holds a fixed preferred width of 188. The main wrapper has top/right/bottom/left insets of 30/28/24/28. The header-to-body gap is 18; stacked content gaps are 16. The import area has preferred height 180, the central queue expands with the window, and settings remain below it. The surface-specific composition is captured in `.impeccable/surfaces/desktop.md`.

Queue rows are 48 high with a 34-high header. Cells have left/right insets of 12/10; preferred column widths are 330/90/210/120 for file, size, state, and progress. File names and state cells expose tooltips for truncated content. The output field, quality selector, spinner, and browse action prefer height 34. Default white cards use 14/16 vertical/horizontal padding; the settings card uses 18 on all sides.

There is no mobile layout or responsive collapse. Keep the tested desktop minimum instead of promising unsupported smaller-window behavior.

## Elevation & Depth

Custom work surfaces are flat: Java2D paints solid rounded fills, not shadows, glass, blur, gradients, or fake translucency. Depth comes from the workspace/sidebar/surface tones, subtle structural strokes, and selection backgrounds. FlatLaf supplies normal control rendering, dialog styling, and focus treatment; those details must not be claimed as individually specified custom tokens.

**The Tonal Depth Rule.** Preserve flat, differentiated work areas; do not add decorative elevation to the queue or import surface.

## Shapes

The custom panel painter passes Java2D arc width and height of 16, which corresponds to an 8-unit corner radius. Selected navigation uses arc 12, corresponding to radius 6. The import outline uses the same arc 16. Frontmatter `rounded` values express radius, not Java2D arc diameter.

FlatLaf defaults set component/text-component arcs to 12 and button arc to 14. Buttons also request `JButton.buttonType=roundRect`, so their effective shape is delegated to FlatLaf's round-rectangle painter and must not be reduced to an asserted 7-unit custom radius. The focus width is 2. Keep native rectangular text fields, selectors, and spinner semantics beneath the gentle rounding.

## Components

### Buttons

Primary action: a blue filled round-rectangle with white text, explicit margin 9/20, and preferred/maximum size 150 × 40. Enabled mouse hover changes to the deeper blue; exit returns to the base blue. No custom transition timing or transform is defined.

Secondary actions: white round-rectangle controls with charcoal text. FlatLaf owns their hover, pressed, border, focus, and disabled rendering. Sizes fit their role: clear is 100 × 32, browse is 62 × 34, and cancel is 150 × 34. Cancel is hidden at rest and appears during conversion; start and settings are disabled while processing.

### Cards / Containers

White flat rounded panels group the import, queue, and settings. The import card alone has its pale blue-gray outline. Do not substitute marketing cards for the live JTable or overlay decorative charts on file state.

### Inputs / Fields

The output path is a real JTextField with a placeholder, tooltip, and adjacent browse control. Quality is a JComboBox and concurrency is a numeric JSpinner. The source-folder checkbox disables custom output controls and the open-output action; running conversion disables mutable settings. Focus and disabled colors are FlatLaf-owned, not invented component tokens.

### Navigation

The sidebar selected-context row uses pale blue with bold blue text. It is a JPanel label, not an interactive multi-route navigation system; no hover or mobile state is implemented. The separate open-output button performs a real desktop-folder action.

### Queue and progress

The table has alternating pale rows, no grid, pale-blue selection, and visible text state/detail. A JProgressBar renders blue progress on a pale track, displays a percentage above zero, and displays an em dash at zero. Empty queue copy teaches the next action. Completion updates summary text and emits the system beep; no custom animation is defined.

### Import and identity icons

The import arrow is a Java2D stroke icon (40 × 36) in action blue. Engine dots are 8 × 8. Runtime window icons are drawn by `MainFrame.createAppIcons()` at 16/32/48/64/128/256 sizes. Shipping `assets/app.ico` and `assets/app.png` originate from the project's `scripts/create-icon.ps1`; they are procedural project artwork, not AI-generated images. Keep those assets' provenance distinct from the runtime Java2D icons.

## Do's and Don'ts

### Do:

- Do preserve the user's Apple-like visual direction while retaining Windows window management.
- Do use the observed palette and Microsoft YaHei UI hierarchy for new Swing controls.
- Do keep visible text status, usable keyboard focus, and file-state detail alongside color cues.
- Do preserve the local converter workflow and source-file safety.
- Do treat Java source as canonical when portable token previews differ from FlatLaf rendering.

### Don't:

- Don't represent this Windows Swing application as web, macOS, iOS, or Android.
- Don't add fake Apple traffic-light window controls or decorative glass to the native frame.
- Don't fabricate CSS breakpoints, custom control-state colors, or animation timings.
- Don't describe GPU detection as GPU MP3 encoding or imply audio uploads.
- Don't label procedural project icons as AI-generated assets.
