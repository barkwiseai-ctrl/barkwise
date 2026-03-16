#!/usr/bin/env python3
"""Generate v9 doggy variants with clearer floppy-ear canine proportions."""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

import numpy as np


SIZE = 512
SCALE = 2
WORK = SIZE * SCALE
OUT_DIR = Path(__file__).resolve().parent / "v9_doggy_launcher_variants"

BG = (143, 191, 163, 255)
FUR = (244, 248, 245, 255)
FUR_SHADE = (232, 238, 234, 255)
DETAIL = (31, 47, 40, 255)
MOUTH = (58, 66, 62, 220)
TONGUE = (240, 142, 132, 235)

VARIANTS = [
    {"name": "doggy_01_beagle", "ear_len": 1.00, "ear_tilt": 0.06, "muzzle": 1.04, "face": 1.00},
    {"name": "doggy_02_cocker", "ear_len": 1.18, "ear_tilt": 0.02, "muzzle": 1.00, "face": 0.98},
    {"name": "doggy_03_labrador", "ear_len": 0.92, "ear_tilt": 0.08, "muzzle": 1.12, "face": 1.08},
    {"name": "doggy_04_golden", "ear_len": 1.06, "ear_tilt": 0.03, "muzzle": 1.06, "face": 1.03},
    {"name": "doggy_05_dachshund", "ear_len": 0.95, "ear_tilt": 0.10, "muzzle": 1.22, "face": 0.96},
    {"name": "doggy_06_spaniel", "ear_len": 1.24, "ear_tilt": -0.01, "muzzle": 1.02, "face": 0.98},
    {"name": "doggy_07_puppy", "ear_len": 0.90, "ear_tilt": 0.08, "muzzle": 0.95, "face": 0.95},
    {"name": "doggy_08_retriever_mix", "ear_len": 1.02, "ear_tilt": 0.05, "muzzle": 1.10, "face": 1.01},
]


def _save_png(path: Path, rgba: np.ndarray) -> None:
    h, w, c = rgba.shape
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
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))


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


def _ellipse(xx: np.ndarray, yy: np.ndarray, cx: float, cy: float, rx: float, ry: float) -> np.ndarray:
    return ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0


def _circle(xx: np.ndarray, yy: np.ndarray, cx: float, cy: float, r: float) -> np.ndarray:
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def _triangle(xx: np.ndarray, yy: np.ndarray, p1, p2, p3) -> np.ndarray:
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


def _draw(cfg: dict[str, float | str]) -> np.ndarray:
    canvas = np.full((WORK, WORK, 4), BG, dtype=np.uint8)
    yy, xx = np.mgrid[0:WORK, 0:WORK]

    # Maintain current BarkWise composition: subject pushed right and partially cropped.
    hx = WORK * 0.79
    hy = WORK * 0.58

    face_scale = float(cfg["face"])
    _blend(canvas, _ellipse(xx, yy, hx, hy, WORK * 0.29 * face_scale, WORK * 0.36 * face_scale), FUR)

    # Floppy hanging ear: vertical drop + lower bulb gives strong dog cue.
    ear_len = float(cfg["ear_len"])
    ear_tilt = float(cfg["ear_tilt"])
    ex = WORK * (0.43 - 0.03 * ear_tilt)
    ey = WORK * (0.43 + 0.03 * ear_tilt)
    _blend(canvas, _ellipse(xx, yy, ex, ey, WORK * 0.085, WORK * (0.18 * ear_len)), FUR)
    _blend(canvas, _ellipse(xx, yy, ex + WORK * 0.018, ey + WORK * (0.06 * ear_len), WORK * 0.048, WORK * (0.10 * ear_len)), FUR_SHADE)

    # Muzzle and jaw
    muzzle = float(cfg["muzzle"])
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.62, WORK * (0.13 * muzzle), WORK * 0.085), FUR_SHADE)
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.695, WORK * (0.08 * muzzle), WORK * 0.055), (236, 241, 237, 255))
    _blend(canvas, _ellipse(xx, yy, WORK * 0.70, WORK * 0.73, WORK * 0.14, WORK * 0.09), FUR)

    # Eyes
    _blend(canvas, _circle(xx, yy, WORK * 0.56, WORK * 0.535, WORK * 0.0145), DETAIL)
    _blend(canvas, _circle(xx, yy, WORK * 0.708, WORK * 0.535, WORK * 0.0145), DETAIL)
    _blend(canvas, _circle(xx, yy, WORK * 0.556, WORK * 0.531, WORK * 0.0042), (255, 255, 255, 235))
    _blend(canvas, _circle(xx, yy, WORK * 0.704, WORK * 0.531, WORK * 0.0042), (255, 255, 255, 235))

    # Dog nose (triangle) + philtrum
    _blend(
        canvas,
        _triangle(
            xx,
            yy,
            (WORK * 0.63, WORK * 0.608),
            (WORK * 0.602, WORK * 0.637),
            (WORK * 0.658, WORK * 0.637),
        ),
        DETAIL,
    )
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.651, WORK * 0.0042, WORK * 0.012), DETAIL)

    # Mouth + tongue
    _blend(canvas, _ellipse(xx, yy, WORK * 0.605, WORK * 0.681, WORK * 0.021, WORK * 0.007), MOUTH)
    _blend(canvas, _ellipse(xx, yy, WORK * 0.655, WORK * 0.681, WORK * 0.021, WORK * 0.007), MOUTH)
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.705, WORK * 0.015, WORK * 0.013), TONGUE)

    # Small top-left ear nub retained from old icon language.
    _blend(canvas, _ellipse(xx, yy, WORK * 0.355, WORK * 0.325, WORK * 0.052, WORK * 0.075), FUR)

    # Downscale with supersampling
    out = canvas.reshape(SIZE, SCALE, SIZE, SCALE, 4).mean(axis=(1, 3))
    return np.clip(out, 0, 255).astype(np.uint8)


def _sheet(images: list[np.ndarray]) -> np.ndarray:
    cols = 4
    rows = 2
    gap = 24
    border = 24
    w = border * 2 + cols * SIZE + (cols - 1) * gap
    h = border * 2 + rows * SIZE + (rows - 1) * gap
    sheet = np.full((h, w, 4), (244, 246, 247, 255), dtype=np.uint8)
    for i, img in enumerate(images):
        r, c = divmod(i, cols)
        x = border + c * (SIZE + gap)
        y = border + r * (SIZE + gap)
        sheet[y : y + SIZE, x : x + SIZE] = img
    return sheet


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    imgs = []
    for cfg in VARIANTS:
        img = _draw(cfg)
        imgs.append(img)
        _save_png(OUT_DIR / f"icon_{cfg['name']}.png", img)
    _save_png(OUT_DIR / "icon_contact_sheet_v9_doggy.png", _sheet(imgs))
    print(f"Generated v9 variants in: {OUT_DIR}")


if __name__ == "__main__":
    main()
