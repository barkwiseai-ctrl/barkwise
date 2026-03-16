#!/usr/bin/env python3
"""Generate dog-forward launcher variants in the current BarkWise visual style."""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

import numpy as np


SIZE = 512
SCALE = 2
WORK = SIZE * SCALE

OUT_DIR = Path(__file__).resolve().parent / "v8_doggy_launcher_variants"

BG = (143, 191, 163, 255)  # barkwise_eucalyptus
FUR = (244, 248, 245, 255)
EAR_LIGHT = (230, 236, 232, 255)
DETAIL = (31, 47, 40, 255)
TONGUE = (240, 142, 132, 255)


VARIANTS = [
    # beagle-ish floppy
    {"name": "doggy_01_beagle", "ear_drop": 0.0, "ear_width": 1.0, "muzzle_len": 1.0, "eye_y": 0.0, "nose_scale": 1.0},
    # cocker spaniel longer ear
    {"name": "doggy_02_cocker", "ear_drop": 0.22, "ear_width": 1.18, "muzzle_len": 1.05, "eye_y": -0.02, "nose_scale": 0.95},
    # labrador broader face
    {"name": "doggy_03_labrador", "ear_drop": 0.10, "ear_width": 1.05, "muzzle_len": 1.12, "eye_y": -0.01, "nose_scale": 1.08},
    # golden retriever soft face
    {"name": "doggy_04_golden", "ear_drop": 0.14, "ear_width": 1.12, "muzzle_len": 1.1, "eye_y": -0.02, "nose_scale": 1.02},
    # dachshund longer snout
    {"name": "doggy_05_dachshund", "ear_drop": 0.08, "ear_width": 0.95, "muzzle_len": 1.25, "eye_y": 0.0, "nose_scale": 0.96},
    # border collie mix (slimmer)
    {"name": "doggy_06_collie", "ear_drop": -0.03, "ear_width": 0.92, "muzzle_len": 1.0, "eye_y": 0.03, "nose_scale": 0.9},
    # puppy compact
    {"name": "doggy_07_puppy", "ear_drop": 0.05, "ear_width": 1.05, "muzzle_len": 0.9, "eye_y": 0.03, "nose_scale": 1.08},
    # spaniel mix
    {"name": "doggy_08_spaniel_mix", "ear_drop": 0.18, "ear_width": 1.2, "muzzle_len": 1.0, "eye_y": -0.01, "nose_scale": 0.92},
]


def _save_png(path: Path, rgba: np.ndarray) -> None:
    h, w, c = rgba.shape
    if c != 4:
        raise ValueError("RGBA expected")
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


def _draw_variant(cfg: dict[str, float | str]) -> np.ndarray:
    canvas = np.full((WORK, WORK, 4), BG, dtype=np.uint8)
    yy, xx = np.mgrid[0:WORK, 0:WORK]

    # Keep the same "cropped-right-face" composition as the current BarkWise icon
    cx = WORK * 0.78
    cy = WORK * 0.58

    # Left floppy ear (readable dog cue)
    ear_cx = WORK * 0.44
    ear_cy = WORK * (0.44 + 0.10 * float(cfg["ear_drop"]))
    ear_rx = WORK * (0.10 * float(cfg["ear_width"]))
    ear_ry = WORK * (0.18 * (0.90 + float(cfg["ear_drop"])))
    _blend(canvas, _ellipse(xx, yy, ear_cx, ear_cy, ear_rx, ear_ry), FUR)
    _blend(canvas, _ellipse(xx, yy, ear_cx + 14, ear_cy + 16, ear_rx * 0.56, ear_ry * 0.58), EAR_LIGHT)

    # Main head (partially off-canvas on right)
    _blend(canvas, _ellipse(xx, yy, cx, cy, WORK * 0.30, WORK * 0.36), FUR)

    # Muzzle: elongated downward oval + lower chin (dog-like, not pig-like)
    muzzle_len = float(cfg["muzzle_len"])
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.62, WORK * 0.12 * muzzle_len, WORK * 0.095), (237, 241, 238, 255))
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.695, WORK * 0.07 * muzzle_len, WORK * 0.055), (233, 238, 234, 255))

    # Eyes
    eye_y = WORK * (0.53 + 0.02 * float(cfg["eye_y"]))
    _blend(canvas, _circle(xx, yy, WORK * 0.56, eye_y, WORK * 0.016), DETAIL)
    _blend(canvas, _circle(xx, yy, WORK * 0.71, eye_y, WORK * 0.016), DETAIL)
    _blend(canvas, _circle(xx, yy, WORK * 0.553, eye_y - WORK * 0.003, WORK * 0.004), (255, 255, 255, 235))
    _blend(canvas, _circle(xx, yy, WORK * 0.703, eye_y - WORK * 0.003, WORK * 0.004), (255, 255, 255, 235))

    # Nose: inverted triangle (canine read), with short philtrum
    nose_scale = float(cfg["nose_scale"])
    nose = _triangle(
        xx,
        yy,
        (WORK * 0.63, WORK * 0.61),
        (WORK * (0.602 - 0.01 * (nose_scale - 1.0)), WORK * 0.64),
        (WORK * (0.658 + 0.01 * (nose_scale - 1.0)), WORK * 0.64),
    )
    _blend(canvas, nose, DETAIL)
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.653, WORK * 0.0045, WORK * 0.012), DETAIL)

    # Mouth curve + tiny tongue
    _blend(canvas, _ellipse(xx, yy, WORK * 0.607, WORK * 0.682, WORK * 0.022, WORK * 0.0075), DETAIL)
    _blend(canvas, _ellipse(xx, yy, WORK * 0.653, WORK * 0.682, WORK * 0.022, WORK * 0.0075), DETAIL)
    _blend(canvas, _ellipse(xx, yy, WORK * 0.63, WORK * 0.705, WORK * 0.017, WORK * 0.014), TONGUE)

    # Soft cheek cut to make head shape less circular
    _blend(canvas, _ellipse(xx, yy, WORK * 0.79, WORK * 0.72, WORK * 0.18, WORK * 0.10), FUR)

    # Supersample downscale
    result = canvas.reshape(SIZE, SCALE, SIZE, SCALE, 4).mean(axis=(1, 3))
    return np.clip(result, 0, 255).astype(np.uint8)


def _contact_sheet(images: list[np.ndarray]) -> np.ndarray:
    cols = 4
    rows = 2
    gap = 24
    border = 24
    w = border * 2 + cols * SIZE + (cols - 1) * gap
    h = border * 2 + rows * SIZE + (rows - 1) * gap
    sheet = np.full((h, w, 4), (244, 246, 247, 255), dtype=np.uint8)
    for i, img in enumerate(images):
        r = i // cols
        c = i % cols
        x = border + c * (SIZE + gap)
        y = border + r * (SIZE + gap)
        sheet[y : y + SIZE, x : x + SIZE] = img
    return sheet


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    images = []
    for cfg in VARIANTS:
        img = _draw_variant(cfg)
        images.append(img)
        _save_png(OUT_DIR / f"icon_{cfg['name']}.png", img)
    _save_png(OUT_DIR / "icon_contact_sheet_v8_doggy.png", _contact_sheet(images))
    print(f"Generated v8 variants in: {OUT_DIR}")


if __name__ == "__main__":
    main()
