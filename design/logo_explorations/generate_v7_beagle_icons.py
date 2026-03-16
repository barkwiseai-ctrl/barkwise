#!/usr/bin/env python3
"""Generate v7 icon explorations using a floppy-eared beagle profile."""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

import numpy as np


SIZE = 512
SCALE = 2
WORK = SIZE * SCALE
VARIANTS = 5

OUT_DIR = Path(__file__).resolve().parent / "v7_beagle_icons"

PALETTES = [
    {"bg0": np.array([7, 16, 33], dtype=np.float32), "bg1": np.array([15, 33, 60], dtype=np.float32), "ring": (66, 214, 203, 255)},
    {"bg0": np.array([14, 18, 42], dtype=np.float32), "bg1": np.array([32, 40, 78], dtype=np.float32), "ring": (117, 147, 255, 255)},
    {"bg0": np.array([28, 16, 24], dtype=np.float32), "bg1": np.array([54, 28, 40], dtype=np.float32), "ring": (241, 145, 98, 255)},
    {"bg0": np.array([12, 24, 20], dtype=np.float32), "bg1": np.array([22, 48, 40], dtype=np.float32), "ring": (110, 206, 152, 255)},
    {"bg0": np.array([16, 21, 32], dtype=np.float32), "bg1": np.array([36, 45, 60], dtype=np.float32), "ring": (145, 161, 184, 255)},
]

BEAGLE = {
    "head": (243, 233, 217, 255),
    "ear": (143, 92, 56, 255),
    "patch": (171, 115, 72, 255),
    "cut": (17, 28, 48, 255),
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
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", ihdr) + _chunk(b"IDAT", idat) + _chunk(b"IEND", b""))


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
    cx, cy = WORK * 0.5, WORK * 0.5
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    t = np.clip(d / (WORK * 0.88), 0.0, 1.0)
    rgb = palette["bg0"] * (1.0 - t[..., None]) + palette["bg1"] * t[..., None]
    alpha = np.full((WORK, WORK, 1), 255, dtype=np.uint8)
    return np.concatenate([rgb.astype(np.uint8), alpha], axis=2)


def _draw_beagle(canvas: np.ndarray, variant: int) -> None:
    yy, xx = np.mgrid[0:WORK, 0:WORK]
    cx, cy = WORK * 0.5, WORK * 0.53

    # ears (long + floppy)
    _blend(canvas, _ellipse(xx, yy, cx - 154, cy + 10, 64, 138), BEAGLE["ear"])
    _blend(canvas, _ellipse(xx, yy, cx + 154, cy + 10, 64, 138), BEAGLE["ear"])

    # main skull
    _blend(canvas, _ellipse(xx, yy, cx, cy - 10, 170, 148), BEAGLE["head"])

    # forehead patch to read as beagle
    if variant in (0, 2, 4):
        _blend(canvas, _ellipse(xx, yy, cx + 44, cy - 54, 68, 56), BEAGLE["patch"])
    else:
        _blend(canvas, _ellipse(xx, yy, cx - 44, cy - 54, 68, 56), BEAGLE["patch"])

    # muzzle (long dog snout, not pig snout)
    _blend(canvas, _ellipse(xx, yy, cx, cy + 62, 116, 74), (250, 242, 228, 255))
    _blend(canvas, _ellipse(xx, yy, cx, cy + 102, 72, 42), (248, 236, 218, 255))

    # eye sockets and eyes
    _blend(canvas, _ellipse(xx, yy, cx - 58, cy - 14, 24, 14), (228, 214, 196, 180))
    _blend(canvas, _ellipse(xx, yy, cx + 58, cy - 14, 24, 14), (228, 214, 196, 180))
    _blend(canvas, _ellipse(xx, yy, cx - 58, cy - 12, 11, 8), (31, 35, 44, 255))
    _blend(canvas, _ellipse(xx, yy, cx + 58, cy - 12, 11, 8), (31, 35, 44, 255))
    _blend(canvas, _circle(xx, yy, cx - 62, cy - 15, 3), (255, 255, 255, 235))
    _blend(canvas, _circle(xx, yy, cx + 54, cy - 15, 3), (255, 255, 255, 235))

    # nose and mouth
    _blend(canvas, _ellipse(xx, yy, cx, cy + 46, 30, 22), (34, 35, 42, 255))
    _blend(canvas, _ellipse(xx, yy, cx - 34, cy + 96, 24, 7), (58, 56, 58, 210))
    _blend(canvas, _ellipse(xx, yy, cx + 34, cy + 96, 24, 7), (58, 56, 58, 210))

    # chin point to emphasize canine jawline
    _blend(canvas, _triangle(xx, yy, (cx, cy + 92), (cx - 22, cy + 130), (cx + 22, cy + 130)), (245, 233, 214, 255))

    # tiny collar
    collar_colors = [(76, 195, 224, 240), (132, 134, 240, 240), (244, 136, 98, 240), (103, 195, 141, 240), (138, 154, 176, 240)]
    _blend(canvas, _ellipse(xx, yy, cx, cy + 152, 104, 15), collar_colors[variant])


def _icon(variant: int) -> np.ndarray:
    palette = PALETTES[variant]
    canvas = _background(palette)
    yy, xx = np.mgrid[0:WORK, 0:WORK]
    cx, cy = WORK * 0.5, WORK * 0.5

    outer = _circle(xx, yy, cx, cy, WORK * 0.41)
    inner = _circle(xx, yy, cx, cy, WORK * 0.365)
    _blend(canvas, outer & (~inner), palette["ring"])
    _blend(canvas, inner, (250, 252, 255, 255))
    _blend(canvas, _ellipse(xx, yy, cx, cy + 40, 318, 264), (255, 255, 255, 20))

    _draw_beagle(canvas, variant)

    # signature accent
    accents = [
        _circle(xx, yy, cx + 152, cy - 148, 17),
        _triangle(xx, yy, (cx + 144, cy - 146), (cx + 176, cy - 164), (cx + 160, cy - 126)),
        _circle(xx, yy, cx - 124, cy + 166, 10) | _circle(xx, yy, cx - 96, cy + 180, 10),
        _ellipse(xx, yy, cx + 0, cy + 190, 30, 12),
        _circle(xx, yy, cx - 152, cy - 152, 10),
    ]
    _blend(canvas, accents[variant], palette["ring"])

    out = canvas.reshape(SIZE, SCALE, SIZE, SCALE, 4).mean(axis=(1, 3))
    return np.clip(out, 0, 255).astype(np.uint8)


def _contact_sheet() -> np.ndarray:
    gap = 24
    border = 24
    w = border * 2 + VARIANTS * SIZE + (VARIANTS - 1) * gap
    h = border * 2 + SIZE
    sheet = np.full((h, w, 4), (246, 249, 254, 255), dtype=np.uint8)
    for c in range(VARIANTS):
        img = _icon(c)
        x = border + c * (SIZE + gap)
        y = border
        sheet[y : y + SIZE, x : x + SIZE] = img
    return sheet


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for i in range(VARIANTS):
        _save_png(OUT_DIR / f"icon_beagle_{i + 1:02d}.png", _icon(i))
    _save_png(OUT_DIR / "icon_contact_sheet_v7_beagle.png", _contact_sheet())
    print(f"Generated v7 beagle icons: {OUT_DIR}")


if __name__ == "__main__":
    main()
