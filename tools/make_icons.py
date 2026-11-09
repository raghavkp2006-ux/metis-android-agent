#!/usr/bin/env python3
"""
Generate Android adaptive launcher icons and legacy density mipmaps for METIS.
Extracts the mascot from assets/branding/metis_logo_source.png, crops out the wordmark,
mats the alpha against the sampled plum background, and scales the mascot into the 66dp
safe zone of a 108dp adaptive canvas.
"""

import os
import sys
from PIL import Image, ImageDraw
import numpy as np

COLOR_PLUM_HEX = "#2A1A25"
COLOR_PLUM_RGB = (42, 26, 37)
COLOR_LIME_HEX = "#A5B717"
COLOR_LIME_RGB = (165, 183, 23)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

def extract_mascot_image(source_path: str) -> Image.Image:
    """Extract mascot with clean alpha, cropping out wordmark and background."""
    img = Image.open(source_path).convert("RGB")
    arr = np.array(img, dtype=float)

    B = np.array(COLOR_PLUM_RGB, dtype=float)
    L = np.array(COLOR_LIME_RGB, dtype=float)

    v = L - B
    v_norm_sq = np.dot(v, v)

    C_minus_B = arr - B
    alpha = np.sum(C_minus_B * v, axis=2) / v_norm_sq
    alpha = np.clip(alpha, 0.0, 1.0)

    # Clean low alpha noise from jpeg compression
    alpha[alpha < 0.12] = 0.0

    # Crop out wordmark at y >= 580
    alpha[580:, :] = 0.0

    # Build clean RGBA image using the exact brand lime
    rgba = np.zeros((arr.shape[0], arr.shape[1], 4), dtype=np.uint8)
    rgba[:, :, 0] = COLOR_LIME_RGB[0]
    rgba[:, :, 1] = COLOR_LIME_RGB[1]
    rgba[:, :, 2] = COLOR_LIME_RGB[2]
    rgba[:, :, 3] = np.uint8(alpha * 255)

    mascot_img = Image.fromarray(rgba, "RGBA")
    bbox = mascot_img.getbbox()
    if not bbox:
        raise RuntimeError("Failed to detect mascot foreground in source image.")
    return mascot_img.crop(bbox)

def make_adaptive_layer(mascot: Image.Image, canvas_size: int, safe_zone_size: int, monochrome: bool = False) -> Image.Image:
    """Create a 108dp canvas with the mascot centered within safe_zone_size (66dp target)."""
    # Scale mascot to fit safe zone
    mw, mh = mascot.size
    aspect = mw / mh
    if aspect > 1.0:
        target_w = safe_zone_size
        target_h = int(round(safe_zone_size / aspect))
    else:
        target_h = safe_zone_size
        target_w = int(round(safe_zone_size * aspect))

    scaled = mascot.resize((target_w, target_h), Image.Resampling.LANCZOS)
    if monochrome:
        # Monochrome layer for Android 13+ themed icon: white silhouette with alpha
        s_arr = np.array(scaled)
        s_arr[:, :, :3] = 255  # White
        scaled = Image.fromarray(s_arr, "RGBA")

    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    pos_x = (canvas_size - target_w) // 2
    pos_y = (canvas_size - target_h) // 2
    canvas.paste(scaled, (pos_x, pos_y), scaled)
    return canvas

def make_legacy_icon(mascot: Image.Image, size: int, round_mask: bool = False) -> Image.Image:
    """Create legacy composite icon with solid plum background."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    # Scale mascot to ~68% of icon size
    target_h = int(round(size * 0.64))
    aspect = mascot.size[0] / mascot.size[1]
    target_w = int(round(target_h * aspect))
    scaled = mascot.resize((target_w, target_h), Image.Resampling.LANCZOS)

    pos_x = (size - target_w) // 2
    pos_y = (size - target_h) // 2

    if round_mask:
        draw.ellipse([(0, 0), (size - 1, size - 1)], fill=COLOR_PLUM_RGB)
    else:
        # Rounded rectangle for squircle/box legacy icon
        corner_r = int(round(size * 0.2))
        draw.rounded_rectangle([(0, 0), (size - 1, size - 1)], radius=corner_r, fill=COLOR_PLUM_RGB)

    canvas.paste(scaled, (pos_x, pos_y), scaled)

    if round_mask:
        # Mask out any anti-aliased spill
        mask = Image.new("L", (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([(0, 0), (size - 1, size - 1)], fill=255)
        canvas.putalpha(mask)

    return canvas

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    source_path = os.path.join(repo_root, "assets", "branding", "metis_logo_source.png")
    res_dir = os.path.join(repo_root, "app", "src", "main", "res")

    if not os.path.exists(source_path):
        print(f"Error: Source artwork not found at {source_path}", file=sys.stderr)
        sys.exit(1)

    print(f"Extracting mascot from {source_path}...")
    mascot = extract_mascot_image(source_path)
    print(f"Mascot cropped bounds: {mascot.size[0]}x{mascot.size[1]}")

    # 1. Colors resource
    values_dir = os.path.join(res_dir, "values")
    os.makedirs(values_dir, exist_ok=True)
    colors_xml_path = os.path.join(values_dir, "ic_launcher_colors.xml")
    with open(colors_xml_path, "w", encoding="utf-8") as f:
        f.write(f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{COLOR_PLUM_HEX}</color>
</resources>
""")
    print(f"Wrote {colors_xml_path}")

    # 2. Adaptive icon XMLs
    anydpi_dir = os.path.join(res_dir, "mipmap-anydpi")
    os.makedirs(anydpi_dir, exist_ok=True)
    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""
    for name in ["ic_launcher.xml", "ic_launcher_round.xml"]:
        xml_path = os.path.join(anydpi_dir, name)
        with open(xml_path, "w", encoding="utf-8") as f:
            f.write(adaptive_xml)
        print(f"Wrote {xml_path}")

    # 3. Density folders
    for density, scale in DENSITIES.items():
        density_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(density_dir, exist_ok=True)

        canvas_size = int(round(108 * scale))
        # 64dp inside 66dp safe zone
        safe_size = int(round(64 * scale))
        legacy_size = int(round(48 * scale))

        # Adaptive foreground
        fg = make_adaptive_layer(mascot, canvas_size, safe_size, monochrome=False)
        fg_path = os.path.join(density_dir, "ic_launcher_foreground.png")
        fg.save(fg_path)

        # Adaptive monochrome (Android 13+ themed icon)
        mono = make_adaptive_layer(mascot, canvas_size, safe_size, monochrome=True)
        mono_path = os.path.join(density_dir, "ic_launcher_monochrome.png")
        mono.save(mono_path)

        # Legacy icon
        legacy = make_legacy_icon(mascot, legacy_size, round_mask=False)
        legacy_path = os.path.join(density_dir, "ic_launcher.png")
        legacy.save(legacy_path)

        # Legacy round icon
        legacy_round = make_legacy_icon(mascot, legacy_size, round_mask=True)
        legacy_round_path = os.path.join(density_dir, "ic_launcher_round.png")
        legacy_round.save(legacy_round_path)

        print(f"[{density}] 108dp={canvas_size}px, safe={safe_size}px, legacy={legacy_size}px -> {density_dir}")

    print("Icon generation completed successfully.")

if __name__ == "__main__":
    main()

