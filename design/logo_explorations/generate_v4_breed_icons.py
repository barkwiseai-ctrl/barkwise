#!/usr/bin/env python3
"""Generate a cleaner v4 breed icon exploration set."""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

import numpy as np


BREEDS = ["schnauzer", "labrador", "corgi", "shiba", "border_collie"]
VARIANTS = 5

FINAL_SIZE = 512
SCALE = 2
WORK_SIZE = FINAL_SIZE * SCALE

OUT_DIR = Path(__file__).resolve().parent / "v4_breed_icons"

PALETTES = [
    {
        "bg0": np.array([8, 16, 34], dtype=np.float32),
        "bg1": np.array([14, 32, 58], dtype=np.float32),
        "ring": (66, 214, 203, 255),
        "accent": (39, 183, 169, 255),
    },
    {
        "bg0": np.array([16, 20, 44], dtype=np.float32),
        "bg1": np.array([38, 40, 82], dtype=np.float32),
        "ring": (110, 142, 255, 255),
        "accent": (89, 112, 214, 255),
    },
    {
        "bg0": np.array([32, 18, 28], dtype=np.float32),
        "bg1": np.array([56, 28, 44], dtype=np.float32),
        "ring": (240, 143, 96, 255),
        "accent": (214, 108, 62, 255),
    },
    {
        "bg0": np.array([14, 30, 28], dtype=np.float32),
        "bg1": np.array([22, 54, 48], dtype=np.float32),
        "ring": (104, 204, 146, 255),
        "accent": (74, 164, 112, 255),
    },
    {
        "bg0": np.array([20, 24, 32], dtype=np.float32),
        "bg1": np.array([36, 44, 58], dtype=np.float32),
        "ring": (132, 151, 178, 255),
        "accent": (96, 114, 140, 255),
    },
]

BREED_COLORS = {
    "schnauzer": {
        "fur": (198, 207, 222, 255),
        "ear": (124, 136, 153, 255),
        "muzzle": (236, 240, 246, 255),
        "mark": (152, 162, 181, 255),
    },
    "labrador": {
        "fur": (221, 186, 129, 255),
        "ear": (177, 143, 96, 255),
        "muzzle": (244, 226, 192, 255),
        "mark": (192, 159, 107, 255),
    },
    "corgi": {
        "fur": (241, 157, 88, 255),
        "ear": (216, 126, 58, 255),
        "muzzle": (252, 236, 212, 255),
        "mark": (232, 143, 73, 255),
    },
    "shiba": {
        "fur": (228, 121, 53, 255),
        "ear": (193, 93, 33, 255),
        "muzzle": (244, 222, 188, 255),
        "mark": (212, 108, 45, 255),
    },
    "border_collie": {
        "fur": (223, 228, 236, 255),
        "ear": (33, 41, 58, 255),
        "muzzle": (245, 248, 252, 255),
        "mark": (169, 178, 194, 255),
    },
}


def _save_png(path: Path, rgba: np.ndarray) -> None:
    h, w, c = rgba.shape
    if c != 4:
        raise ValueError("Expected RGBA image")
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(rgba[y].astype(np.uint8).tobytes())

    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag)
        crc = zlib.crc32(data, crc)
        return struct.pack("!I", len(data)) + tag + data + struct.pack("!I", crc & 0xFFFFFFFF)

    ihdr = struct.pack("!IIBBBBB", w, h, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
    path.write_bytes(png)


def _blend(canvas: np.ndarray, mask: np.ndarray, color: tuple[int, int, int, int]) -> None:
    if not np.any(mask):
        return
    dst = canvas[mask]
    src_rgb = np.array(color[:3], dtype=np.float32)
    src_a = float(color[3]) / 255.0
    dst_rgb = dst[:, :3].astype(np.float32)
    dst_a = dst[:, 3].astype(np.float32) / 255.0

    out_a = src_a + dst_a * (1.0 - src_a)
    safe = np.where(out_a == 0, 1.0, out_a)
    out_rgb = (src_rgb * src_a + dst_rgb * dst_a[:, None] * (1.0 - src_a)) / safe[:, None]

    dst[:, :3] = np.clip(out_rgb, 0, 255).astype(np.uint8)
    dst[:, 3] = np.clip(out_a * 255.0, 0, 255).astype(np.uint8)
    canvas[mask] = dst


def _circle_mask(xx: np.ndarray, yy: np.ndarray, cx: float, cy: float, r: float) -> np.ndarray:
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def _ellipse_mask(xx: np.ndarray, yy: np.ndarray, cx: float, cy: float, rx: float, ry: float) -> np.ndarray:
    return ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0


def _triangle_mask(
    xx: np.ndarray,
    yy: np.ndarray,
    p1: tuple[float, float],
    p2: tuple[float, float],
    p3: tuple[float, float],
) -> np.ndarray:
    x1, y1 = p1
    x2, y2 = p2
    x3, y3 = p3

    d = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
    if d == 0:
        return np.zeros_like(xx, dtype=bool)

    a = ((y2 - y3) * (xx - x3) + (x3 - x2) * (yy - y3)) / d
    b = ((y3 - y1) * (xx - x3) + (x1 - x3) * (yy - y3)) / d
    c = 1.0 - a - b
    return (a >= 0) & (b >= 0) & (c >= 0)


def _background(palette: dict[str, object], size: int) -> np.ndarray:
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    cx = size / 2.0
    cy = size / 2.0
    dist = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    t = np.clip(dist / (size * 0.78), 0.0, 1.0)

    bg0 = palette["bg0"]
    bg1 = palette["bg1"]
    rgb = (bg0 * (1.0 - t[..., None]) + bg1 * t[..., None]).astype(np.uint8)
    alpha = np.full((size, size, 1), 255, dtype=np.uint8)
    return np.concatenate([rgb, alpha], axis=2)


def _draw_face(canvas: np.ndarray, breed: str, variant: int) -> None:
    h, w, _ = canvas.shape
    yy, xx = np.mgrid[0:h, 0:w]
    c = BREED_COLORS[breed]
    cx, cy = w * 0.5, h * 0.51

    # Face shadow
    _blend(canvas, _ellipse_mask(xx, yy, cx, cy + 238, 180, 38), (0, 0, 0, 54))

    # Ears
    if breed == "labrador":
        _blend(canvas, _ellipse_mask(xx, yy, cx - 125, cy - 12, 62, 108), c["ear"])
        _blend(canvas, _ellipse_mask(xx, yy, cx + 125, cy - 12, 62, 108), c["ear"])
    else:
        _blend(
            canvas,
            _triangle_mask(xx, yy, (cx - 142, cy - 96), (cx - 48, cy - 54), (cx - 96, cy - 200)),
            c["ear"],
        )
        _blend(
            canvas,
            _triangle_mask(xx, yy, (cx + 142, cy - 96), (cx + 48, cy - 54), (cx + 96, cy - 200)),
            c["ear"],
        )

    # Head and muzzle
    if breed == "corgi":
        _blend(canvas, _ellipse_mask(xx, yy, cx, cy - 18, 176, 148), c["fur"])
    else:
        _blend(canvas, _ellipse_mask(xx, yy, cx, cy - 8, 160, 156), c["fur"])

    if breed == "shiba":
        _blend(canvas, _triangle_mask(xx, yy, (cx, cy - 90), (cx - 108, cy + 52), (cx + 108, cy + 52)), c["muzzle"])
    else:
        _blend(canvas, _ellipse_mask(xx, yy, cx, cy + 38, 104, 80), c["muzzle"])

    # Breed marks
    if breed == "schnauzer":
        _blend(canvas, _ellipse_mask(xx, yy, cx, cy + 76, 74, 36), c["muzzle"])
        _blend(canvas, _triangle_mask(xx, yy, (cx - 84, cy - 30), (cx - 14, cy + 26), (cx - 110, cy + 34)), c["mark"])
        _blend(canvas, _triangle_mask(xx, yy, (cx + 84, cy - 30), (cx + 14, cy + 26), (cx + 110, cy + 34)), c["mark"])
        _blend(canvas, _ellipse_mask(xx, yy, cx - 52, cy - 42, 40, 16), c["mark"])
        _blend(canvas, _ellipse_mask(xx, yy, cx + 52, cy - 42, 40, 16), c["mark"])
    elif breed == "corgi":
        _blend(canvas, _ellipse_mask(xx, yy, cx, cy - 10, 94, 70), c["mark"])
    elif breed == "border_collie":
        _blend(canvas, _triangle_mask(xx, yy, (cx - 132, cy - 84), (cx + 6, cy + 8), (cx - 116, cy + 106)), c["ear"])
        _blend(canvas, _ellipse_mask(xx, yy, cx + 88, cy - 54, 46, 40), c["mark"])
    elif breed == "shiba":
        _blend(canvas, _triangle_mask(xx, yy, (cx - 76, cy - 30), (cx, cy + 58), (cx - 116, cy + 48)), c["mark"])
        _blend(canvas, _triangle_mask(xx, yy, (cx + 76, cy - 30), (cx, cy + 58), (cx + 116, cy + 48)), c["mark"])

    # Eyes + nose + mouth
    _blend(canvas, _circle_mask(xx, yy, cx - 56, cy - 34, 10), (34, 36, 44, 255))
    _blend(canvas, _circle_mask(xx, yy, cx + 56, cy - 34, 10), (34, 36, 44, 255))
    if variant in (0, 1, 3):
        _blend(canvas, _circle_mask(xx, yy, cx - 60, cy - 38, 3), (255, 255, 255, 240))
        _blend(canvas, _circle_mask(xx, yy, cx + 52, cy - 38, 3), (255, 255, 255, 240))
    _blend(canvas, _ellipse_mask(xx, yy, cx, cy + 12, 26, 18), (42, 40, 46, 255))
    _blend(canvas, _ellipse_mask(xx, yy, cx - 24, cy + 44, 18, 6), (56, 50, 54, 210))
    _blend(canvas, _ellipse_mask(xx, yy, cx + 24, cy + 44, 18, 6), (56, 50, 54, 210))

    # Tiny collar accent
    collar = [(86, 182, 229, 235), (141, 126, 248, 235), (240, 125, 91, 235), (82, 176, 128, 235), (109, 128, 152, 235)][
        variant
    ]
    _blend(canvas, _ellipse_mask(xx, yy, cx, cy + 126, 92, 14), collar)


def _draw_icon(breed: str, variant: int) -> np.ndarray:
    palette = PALETTES[variant]
    canvas = _background(palette, WORK_SIZE)
    yy, xx = np.mgrid[0:WORK_SIZE, 0:WORK_SIZE]
    cx, cy = WORK_SIZE * 0.5, WORK_SIZE * 0.5

    # Ring container
    outer = _circle_mask(xx, yy, cx, cy, WORK_SIZE * 0.41)
    inner = _circle_mask(xx, yy, cx, cy, WORK_SIZE * 0.365)
    ring = outer & (~inner)
    _blend(canvas, ring, palette["ring"])

    # Plate
    _blend(canvas, inner, (248, 251, 255, 255))
    _blend(canvas, _circle_mask(xx, yy, cx, cy + WORK_SIZE * 0.01, WORK_SIZE * 0.33), (255, 255, 255, 255))

    # Variant accents on ring
    accent = palette["accent"]
    if variant == 0:
        _blend(canvas, _circle_mask(xx, yy, cx + 145, cy - 142, 16), accent)
    elif variant == 1:
        _blend(canvas, _triangle_mask(xx, yy, (cx + 140, cy - 140), (cx + 170, cy - 156), (cx + 154, cy - 126)), accent)
    elif variant == 2:
        _blend(canvas, _circle_mask(xx, yy, cx - 120, cy + 160, 10), accent)
        _blend(canvas, _circle_mask(xx, yy, cx, cy + 174, 10), accent)
        _blend(canvas, _circle_mask(xx, yy, cx + 120, cy + 160, 10), accent)
    elif variant == 3:
        _blend(canvas, _triangle_mask(xx, yy, (cx - 146, cy - 116), (cx - 122, cy - 140), (cx - 110, cy - 104)), accent)
        _blend(canvas, _triangle_mask(xx, yy, (cx + 146, cy - 116), (cx + 122, cy - 140), (cx + 110, cy - 104)), accent)
    else:
        _blend(canvas, _ellipse_mask(xx, yy, cx, cy + 168, 26, 12), accent)

    _draw_face(canvas, breed, variant)

    # Supersample downscale (box filter)
    img = canvas.reshape(FINAL_SIZE, SCALE, FINAL_SIZE, SCALE, 4).mean(axis=(1, 3))
    return np.clip(img, 0, 255).astype(np.uint8)


def _build_contact_sheet() -> np.ndarray:
    gap = 24
    border = 24
    sheet_w = border * 2 + FINAL_SIZE * VARIANTS + gap * (VARIANTS - 1)
    sheet_h = border * 2 + FINAL_SIZE * len(BREEDS) + gap * (len(BREEDS) - 1)
    sheet = np.full((sheet_h, sheet_w, 4), (247, 250, 255, 255), dtype=np.uint8)
    for r, breed in enumerate(BREEDS):
        for c in range(VARIANTS):
            icon = _draw_icon(breed, c)
            x = border + c * (FINAL_SIZE + gap)
            y = border + r * (FINAL_SIZE + gap)
            sheet[y : y + FINAL_SIZE, x : x + FINAL_SIZE] = icon
    return sheet


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for breed in BREEDS:
        for i in range(VARIANTS):
            icon = _draw_icon(breed, i)
            _save_png(OUT_DIR / f"icon_{breed}_{i + 1:02d}.png", icon)
    contact = _build_contact_sheet()
    _save_png(OUT_DIR / "icon_contact_sheet_v4.png", contact)
    print(f"Generated v4 icon set: {OUT_DIR}")


if __name__ == "__main__":
    main()
