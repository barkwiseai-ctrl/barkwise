#!/usr/bin/env python3
"""Generate v5 breed icon explorations (brand-led monogram direction)."""

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

OUT_DIR = Path(__file__).resolve().parent / "v5_breed_icons"

PALETTES = [
    {
        "bg0": np.array([8, 14, 28], dtype=np.float32),
        "bg1": np.array([14, 24, 42], dtype=np.float32),
        "ring": (58, 211, 196, 255),
        "fill": (243, 247, 255, 255),
        "accent": (58, 211, 196, 255),
    },
    {
        "bg0": np.array([14, 16, 36], dtype=np.float32),
        "bg1": np.array([31, 35, 66], dtype=np.float32),
        "ring": (118, 147, 255, 255),
        "fill": (245, 248, 255, 255),
        "accent": (118, 147, 255, 255),
    },
    {
        "bg0": np.array([28, 16, 24], dtype=np.float32),
        "bg1": np.array([52, 26, 38], dtype=np.float32),
        "ring": (239, 137, 96, 255),
        "fill": (255, 246, 241, 255),
        "accent": (239, 137, 96, 255),
    },
    {
        "bg0": np.array([12, 24, 20], dtype=np.float32),
        "bg1": np.array([20, 43, 36], dtype=np.float32),
        "ring": (110, 206, 152, 255),
        "fill": (244, 252, 247, 255),
        "accent": (110, 206, 152, 255),
    },
    {
        "bg0": np.array([16, 20, 28], dtype=np.float32),
        "bg1": np.array([34, 40, 52], dtype=np.float32),
        "ring": (145, 161, 184, 255),
        "fill": (246, 249, 254, 255),
        "accent": (145, 161, 184, 255),
    },
]

BREED_GLYPH = {
    "schnauzer": {
        "head": (200, 210, 224, 255),
        "ear": (126, 136, 152, 255),
        "mark": (150, 161, 179, 255),
    },
    "labrador": {
        "head": (222, 188, 132, 255),
        "ear": (176, 143, 98, 255),
        "mark": (196, 161, 110, 255),
    },
    "corgi": {
        "head": (241, 159, 90, 255),
        "ear": (216, 127, 58, 255),
        "mark": (232, 143, 74, 255),
    },
    "shiba": {
        "head": (229, 122, 52, 255),
        "ear": (192, 94, 34, 255),
        "mark": (211, 109, 46, 255),
    },
    "border_collie": {
        "head": (224, 229, 237, 255),
        "ear": (34, 42, 58, 255),
        "mark": (169, 179, 195, 255),
    },
}


def _save_png(path: Path, rgba: np.ndarray) -> None:
    h, w, c = rgba.shape
    if c != 4:
        raise ValueError("RGBA expected")
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(rgba[y].astype(np.uint8).tobytes())

    def _chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag)
        crc = zlib.crc32(data, crc)
        return struct.pack("!I", len(data)) + tag + data + struct.pack("!I", crc & 0xFFFFFFFF)

    ihdr = struct.pack("!IIBBBBB", w, h, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    payload = b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", ihdr) + _chunk(b"IDAT", idat) + _chunk(b"IEND", b"")
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


def _rounded_rect(
    xx: np.ndarray,
    yy: np.ndarray,
    x0: float,
    y0: float,
    x1: float,
    y1: float,
    radius: float,
) -> np.ndarray:
    cx0, cx1 = x0 + radius, x1 - radius
    cy0, cy1 = y0 + radius, y1 - radius
    core = (xx >= cx0) & (xx <= cx1) & (yy >= y0) & (yy <= y1)
    side = (xx >= x0) & (xx <= x1) & (yy >= cy0) & (yy <= cy1)
    c1 = _circle(xx, yy, cx0, cy0, radius)
    c2 = _circle(xx, yy, cx1, cy0, radius)
    c3 = _circle(xx, yy, cx0, cy1, radius)
    c4 = _circle(xx, yy, cx1, cy1, radius)
    return core | side | c1 | c2 | c3 | c4


def _background(p: dict[str, object]) -> np.ndarray:
    yy, xx = np.mgrid[0:WORK, 0:WORK].astype(np.float32)
    cx = WORK * 0.5
    cy = WORK * 0.52
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    t = np.clip(d / (WORK * 0.9), 0.0, 1.0)
    rgb = p["bg0"] * (1.0 - t[..., None]) + p["bg1"] * t[..., None]
    a = np.full((WORK, WORK, 1), 255, dtype=np.uint8)
    return np.concatenate([rgb.astype(np.uint8), a], axis=2)


def _draw_b_monogram(canvas: np.ndarray, p: dict[str, object], variant: int) -> None:
    yy, xx = np.mgrid[0:WORK, 0:WORK]
    cx, cy = WORK * 0.50, WORK * 0.54

    # Container
    outer = _circle(xx, yy, cx, cy, WORK * 0.40)
    inner = _circle(xx, yy, cx, cy, WORK * 0.36)
    ring = outer & (~inner)
    _blend(canvas, ring, p["ring"])
    _blend(canvas, inner, (255, 255, 255, 255))

    # Drop shadow for monogram
    _blend(canvas, _ellipse(xx, yy, cx, cy + 230, 190, 45), (18, 24, 34, 36))

    # B body
    b_color = (234, 240, 250, 255) if variant == 4 else (240, 245, 255, 255)
    b_shadow = _rounded_rect(xx, yy, cx - 114, cy - 154, cx - 14, cy + 158, 42)
    _blend(canvas, b_shadow, (14, 24, 40, 20))

    stem = _rounded_rect(xx, yy, cx - 120, cy - 164, cx - 24, cy + 150, 48)
    _blend(canvas, stem, b_color)

    top_bowl = _rounded_rect(xx, yy, cx - 42, cy - 156, cx + 136, cy - 2, 78)
    bot_bowl = _rounded_rect(xx, yy, cx - 42, cy + 0, cx + 136, cy + 156, 78)
    _blend(canvas, top_bowl | bot_bowl, b_color)

    top_cut = _rounded_rect(xx, yy, cx - 14, cy - 108, cx + 82, cy - 28, 40)
    bot_cut = _rounded_rect(xx, yy, cx - 14, cy + 36, cx + 82, cy + 116, 40)
    _blend(canvas, top_cut | bot_cut, tuple((*p["bg0"].astype(np.uint8), 255)))

    # Accent dot
    _blend(canvas, _circle(xx, yy, cx + 154, cy - 136, 24), p["accent"])

    if variant == 1:
        _blend(canvas, _triangle(xx, yy, (cx + 170, cy - 148), (cx + 202, cy - 165), (cx + 186, cy - 132)), p["accent"])
    elif variant == 2:
        _blend(canvas, _circle(xx, yy, cx - 144, cy + 172, 10), p["accent"])
        _blend(canvas, _circle(xx, yy, cx - 114, cy + 188, 10), p["accent"])
    elif variant == 3:
        _blend(canvas, _ellipse(xx, yy, cx + 2, cy + 198, 34, 12), p["accent"])
    elif variant == 4:
        _blend(canvas, _circle(xx, yy, cx - 148, cy - 154, 12), p["accent"])


def _draw_breed_badge(canvas: np.ndarray, breed: str, variant: int) -> None:
    yy, xx = np.mgrid[0:WORK, 0:WORK]
    bx, by = WORK * 0.50, WORK * 0.22
    colors = BREED_GLYPH[breed]

    _blend(canvas, _circle(xx, yy, bx, by + 8, 86), (0, 0, 0, 28))
    _blend(canvas, _circle(xx, yy, bx, by, 82), (255, 255, 255, 246))
    _blend(canvas, _circle(xx, yy, bx, by, 77), (247, 250, 255, 255))

    # Ears and head
    if breed == "labrador":
        _blend(canvas, _ellipse(xx, yy, bx - 52, by + 6, 22, 34), colors["ear"])
        _blend(canvas, _ellipse(xx, yy, bx + 52, by + 6, 22, 34), colors["ear"])
        _blend(canvas, _ellipse(xx, yy, bx, by + 2, 48, 43), colors["head"])
    else:
        _blend(canvas, _triangle(xx, yy, (bx - 62, by - 10), (bx - 26, by + 2), (bx - 44, by - 60)), colors["ear"])
        _blend(canvas, _triangle(xx, yy, (bx + 62, by - 10), (bx + 26, by + 2), (bx + 44, by - 60)), colors["ear"])
        _blend(canvas, _ellipse(xx, yy, bx, by + 4, 50, 44), colors["head"])

    if breed == "border_collie":
        _blend(canvas, _triangle(xx, yy, (bx - 58, by - 6), (bx + 2, by + 20), (bx - 50, by + 44)), colors["ear"])
    elif breed == "schnauzer":
        _blend(canvas, _ellipse(xx, yy, bx - 18, by - 6, 18, 7), colors["mark"])
        _blend(canvas, _ellipse(xx, yy, bx + 18, by - 6, 18, 7), colors["mark"])
    elif breed == "shiba":
        _blend(canvas, _triangle(xx, yy, (bx, by - 10), (bx - 34, by + 34), (bx + 34, by + 34)), (246, 232, 210, 255))
    else:
        _blend(canvas, _ellipse(xx, yy, bx, by + 18, 30, 24), (246, 232, 210, 255))

    _blend(canvas, _circle(xx, yy, bx - 18, by + 2, 5), (36, 39, 48, 255))
    _blend(canvas, _circle(xx, yy, bx + 18, by + 2, 5), (36, 39, 48, 255))
    _blend(canvas, _ellipse(xx, yy, bx, by + 18, 9, 6), (40, 40, 46, 255))

    if variant in (0, 2):
        _blend(canvas, _circle(xx, yy, bx + 30, by - 10, 3), (255, 255, 255, 220))


def _icon(breed: str, variant: int) -> np.ndarray:
    p = PALETTES[variant]
    canvas = _background(p)
    _draw_b_monogram(canvas, p, variant)
    _draw_breed_badge(canvas, breed, variant)
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
    _save_png(OUT_DIR / "icon_contact_sheet_v5.png", _contact_sheet())
    print(f"Generated v5 icon set: {OUT_DIR}")


if __name__ == "__main__":
    main()
