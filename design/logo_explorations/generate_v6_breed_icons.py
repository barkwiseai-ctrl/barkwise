#!/usr/bin/env python3
"""Generate v6 breed icon explorations (minimal silhouette direction)."""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

import numpy as np


BREEDS = ["schnauzer", "labrador", "corgi", "shiba", "border_collie"]
VARIANTS = 5

SIZE = 512
SCALE = 2
WORK = SIZE * SCALE

OUT_DIR = Path(__file__).resolve().parent / "v6_breed_icons"

PALETTES = [
    {"bg0": np.array([8, 14, 28], dtype=np.float32), "bg1": np.array([12, 28, 54], dtype=np.float32), "ring": (66, 214, 203, 255)},
    {"bg0": np.array([18, 16, 42], dtype=np.float32), "bg1": np.array([38, 44, 84], dtype=np.float32), "ring": (119, 147, 255, 255)},
    {"bg0": np.array([32, 16, 24], dtype=np.float32), "bg1": np.array([56, 26, 38], dtype=np.float32), "ring": (240, 143, 96, 255)},
    {"bg0": np.array([14, 24, 20], dtype=np.float32), "bg1": np.array([20, 46, 38], dtype=np.float32), "ring": (110, 206, 152, 255)},
    {"bg0": np.array([16, 20, 30], dtype=np.float32), "bg1": np.array([34, 44, 58], dtype=np.float32), "ring": (145, 161, 184, 255)},
]


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
    payload = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
    path.write_bytes(payload)


def _blend(canvas: np.ndarray, mask: np.ndarray, color: tuple[int, int, int, int]) -> None:
    if not np.any(mask):
        return
    dst = canvas[mask]
    src_rgb = np.array(color[:3], dtype=np.float32)
    src_a = color[3] / 255.0
    dst_rgb = dst[:, :3].astype(np.float32)
    dst_a = dst[:, 3].astype(np.float32) / 255.0
    out_a = src_a + dst_a * (1.0 - src_a)
    safe = np.where(out_a == 0.0, 1.0, out_a)
    out_rgb = (src_rgb * src_a + dst_rgb * dst_a[:, None] * (1.0 - src_a)) / safe[:, None]
    dst[:, :3] = np.clip(out_rgb, 0, 255).astype(np.uint8)
    dst[:, 3] = np.clip(out_a * 255.0, 0, 255).astype(np.uint8)
    canvas[mask] = dst


def _circle(xx: np.ndarray, yy: np.ndarray, cx: float, cy: float, r: float) -> np.ndarray:
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def _ellipse(xx: np.ndarray, yy: np.ndarray, cx: float, cy: float, rx: float, ry: float) -> np.ndarray:
    return ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0


def _triangle(
    xx: np.ndarray, yy: np.ndarray, p1: tuple[float, float], p2: tuple[float, float], p3: tuple[float, float]
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


def _background(palette: dict[str, object]) -> np.ndarray:
    yy, xx = np.mgrid[0:WORK, 0:WORK].astype(np.float32)
    cx, cy = WORK * 0.5, WORK * 0.52
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    t = np.clip(d / (WORK * 0.9), 0.0, 1.0)
    rgb = palette["bg0"] * (1.0 - t[..., None]) + palette["bg1"] * t[..., None]
    alpha = np.full((WORK, WORK, 1), 255, dtype=np.uint8)
    return np.concatenate([rgb.astype(np.uint8), alpha], axis=2)


def _silhouette_mask(xx: np.ndarray, yy: np.ndarray, breed: str) -> np.ndarray:
    cx, cy = WORK * 0.5, WORK * 0.52
    mask = _ellipse(xx, yy, cx, cy, 160, 154)

    if breed == "labrador":
        mask |= _ellipse(xx, yy, cx - 126, cy - 18, 62, 104)
        mask |= _ellipse(xx, yy, cx + 126, cy - 18, 62, 104)
        mask |= _ellipse(xx, yy, cx, cy + 46, 104, 72)
    else:
        mask |= _triangle(xx, yy, (cx - 150, cy - 90), (cx - 56, cy - 44), (cx - 100, cy - 224))
        mask |= _triangle(xx, yy, (cx + 150, cy - 90), (cx + 56, cy - 44), (cx + 100, cy - 224))

    if breed == "schnauzer":
        mask |= _ellipse(xx, yy, cx, cy + 96, 82, 44)
    elif breed == "corgi":
        mask |= _ellipse(xx, yy, cx, cy + 32, 122, 86)
    elif breed == "shiba":
        mask |= _triangle(xx, yy, (cx, cy - 72), (cx - 120, cy + 88), (cx + 120, cy + 88))
    elif breed == "border_collie":
        mask |= _triangle(xx, yy, (cx - 146, cy - 76), (cx + 12, cy + 20), (cx - 132, cy + 126))

    return mask


def _cut_features(canvas: np.ndarray, xx: np.ndarray, yy: np.ndarray, breed: str, bg_color: tuple[int, int, int, int]) -> None:
    cx, cy = WORK * 0.5, WORK * 0.52
    # eye slits
    _blend(canvas, _ellipse(xx, yy, cx - 56, cy - 38, 16, 7), bg_color)
    _blend(canvas, _ellipse(xx, yy, cx + 56, cy - 38, 16, 7), bg_color)
    # nose and mouth cuts
    _blend(canvas, _ellipse(xx, yy, cx, cy + 20, 20, 12), bg_color)
    _blend(canvas, _ellipse(xx, yy, cx - 30, cy + 56, 18, 6), bg_color)
    _blend(canvas, _ellipse(xx, yy, cx + 30, cy + 56, 18, 6), bg_color)

    if breed == "schnauzer":
        _blend(canvas, _ellipse(xx, yy, cx - 54, cy - 56, 40, 12), bg_color)
        _blend(canvas, _ellipse(xx, yy, cx + 54, cy - 56, 40, 12), bg_color)
    elif breed == "border_collie":
        _blend(canvas, _triangle(xx, yy, (cx - 134, cy - 74), (cx + 8, cy + 22), (cx - 120, cy + 120)), bg_color)
    elif breed == "shiba":
        _blend(canvas, _triangle(xx, yy, (cx, cy - 12), (cx - 78, cy + 76), (cx + 78, cy + 76)), bg_color)


def _icon(breed: str, variant: int) -> np.ndarray:
    palette = PALETTES[variant]
    canvas = _background(palette)
    yy, xx = np.mgrid[0:WORK, 0:WORK]
    cx, cy = WORK * 0.5, WORK * 0.5

    outer = _circle(xx, yy, cx, cy, WORK * 0.41)
    inner = _circle(xx, yy, cx, cy, WORK * 0.36)
    ring = outer & (~inner)
    _blend(canvas, ring, palette["ring"])
    _blend(canvas, inner, (16, 24, 42, 255))

    # soft plate glow
    _blend(canvas, _ellipse(xx, yy, cx, cy + 30, 340, 280), (255, 255, 255, 22))

    # silhouette logo
    silhouette = _silhouette_mask(xx, yy, breed)
    _blend(canvas, silhouette, (248, 251, 255, 255))
    _cut_features(canvas, xx, yy, breed, (16, 24, 42, 255))

    # signature accent per variant
    if variant == 0:
        _blend(canvas, _circle(xx, yy, cx + 154, cy - 148, 18), palette["ring"])
    elif variant == 1:
        _blend(canvas, _triangle(xx, yy, (cx + 142, cy - 144), (cx + 176, cy - 162), (cx + 160, cy - 126)), palette["ring"])
    elif variant == 2:
        _blend(canvas, _circle(xx, yy, cx - 130, cy + 170, 10), palette["ring"])
        _blend(canvas, _circle(xx, yy, cx - 100, cy + 184, 10), palette["ring"])
    elif variant == 3:
        _blend(canvas, _ellipse(xx, yy, cx + 0, cy + 192, 30, 12), palette["ring"])
    else:
        _blend(canvas, _circle(xx, yy, cx - 154, cy - 154, 10), palette["ring"])

    out = canvas.reshape(SIZE, SCALE, SIZE, SCALE, 4).mean(axis=(1, 3))
    return np.clip(out, 0, 255).astype(np.uint8)


def _contact_sheet() -> np.ndarray:
    gap = 24
    border = 24
    w = border * 2 + VARIANTS * SIZE + (VARIANTS - 1) * gap
    h = border * 2 + len(BREEDS) * SIZE + (len(BREEDS) - 1) * gap
    sheet = np.full((h, w, 4), (246, 249, 254, 255), dtype=np.uint8)
    for r, breed in enumerate(BREEDS):
        for c in range(VARIANTS):
            img = _icon(breed, c)
            x = border + c * (SIZE + gap)
            y = border + r * (SIZE + gap)
            sheet[y : y + SIZE, x : x + SIZE] = img
    return sheet


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for breed in BREEDS:
        for i in range(VARIANTS):
            _save_png(OUT_DIR / f"icon_{breed}_{i + 1:02d}.png", _icon(breed, i))
    _save_png(OUT_DIR / "icon_contact_sheet_v6.png", _contact_sheet())
    print(f"Generated v6 icon set: {OUT_DIR}")


if __name__ == "__main__":
    main()
