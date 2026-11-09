# METIS Brand Identity & Color Palette

This document defines the brand visual constants and assets for METIS, derived from the official brand artwork (`assets/branding/metis_logo_source.png`).

## Color Constants

| Identifier | Name | Hex | RGB | Description |
|---|---|---|---|---|
| `METIS_COLOR_PLUM_BACKGROUND` | Dark Plum | `#2A1A25` | `(42, 26, 37)` | Background canvas color sampled directly from the image corner (0,0). Used for adaptive icon background and brand surfaces. |
| `METIS_COLOR_LIME_MASCOT` | Mascot Lime | `#A5B717` | `(165, 183, 23)` | Lime green mascot color sampled from the peak color histogram of the Android-style mascot body and chest mark. |

## Source Assets

- **Master Brand Asset**: `assets/branding/metis_logo_source.png` (1024×1024 px)
  - Features the lime green Android-style mascot with the chest "M" emblem and the "Metis" wordmark centered on a dark plum field.
- **Generator Script**: `tools/make_icons.py`
  - Generates Android adaptive launcher icons (foreground, monochrome, background color resource) across all standard densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) plus `mipmap-anydpi` adaptive XML definitions.

## Adaptive Icon Safe Zone Architecture

Per Android design specifications (minSdk 26+):
- **Canvas Size**: 108dp × 108dp
- **Safe Zone**: 66dp × 66dp circle/square centered at (54dp, 54dp) with 21dp outer margins.
- **Foreground Layer**: Mascot silhouette cropped from wordmark, alpha-matted, and scaled within the 66dp safe zone.
- **Background Layer**: Solid `#2A1A25` (`@color/ic_launcher_background`).
- **Monochrome Layer (Android 13+)**: Solid white (`#FFFFFF`) silhouette retaining exact mascot alpha channel for system material dynamic tinting.

