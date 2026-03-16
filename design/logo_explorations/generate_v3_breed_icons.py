#!/usr/bin/env python3
"""Generate v3 breed icon explorations without external image dependencies."""

from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path


ICON_SIZE = 256
BREEDS = ["schnauzer", "labrador", "corgi", "shiba", "border_collie"]
OUT_DIR = Path(__file__).resolve().parent / "v3_breed_icons"

PALETTES = [
    {
        "bg": (239, 246, 255, 255),
        "plate": (250, 252, 255, 255),
        "ring": (44, 108, 242, 255),
        "accent": (25, 76, 210, 255),
    },
    {
        "bg": (246, 241, 255, 255),
        "plate": (252, 249, 255, 255),
        "ring": (111, 72, 217, 255),
        "accent": (82, 51, 180, 255),
    },
    {
        "bg": (237, 248, 244, 255),
        "plate": (250, 253, 251, 255),
        "ring": (30, 158, 122, 255),
        "accent": (24, 119, 94, 255),
    },
    {
        "bg": (250, 243, 233, 255),
        "plate": (255, 251, 247, 255),
        "ring": (228, 127, 49, 255),
        "accent": (183, 95, 30, 255),
    },
    {
        "bg": (242, 246, 251, 255),
        "plate": (249, 252, 255, 255),
        "ring": (90, 107, 134, 255),
        "accent": (56, 69, 92, 255),
    },
]

BREED_STYLE = {
    "schnauzer": {
        "head": (200, 208, 220, 255),
        "ear": (126, 136, 149, 255),
        "muzzle": (233, 237, 243, 255),
        "mask": (148, 158, 174, 255),
    },
    "labrador": {
        "head": (220, 186, 127, 255),
        "ear": (181, 147, 95, 255),
        "muzzle": (242, 226, 194, 255),
        "mask": (190, 156, 103, 255),
    },
    "corgi": {
        "head": (241, 161, 91, 255),
        "ear": (222, 133, 63, 255),
        "muzzle": (251, 238, 216, 255),
        "mask": (230, 143, 73, 255),
    },
    "shiba": {
        "head": (229, 124, 58, 255),
        "ear": (194, 95, 36, 255),
        "muzzle": (243, 223, 190, 255),
        "mask": (212, 108, 41, 255),
    },
    "border_collie": {
        "head": (219, 224, 231, 255),
        "ear": (32, 39, 53, 255),
        "muzzle": (244, 247, 251, 255),
        "mask": (167, 178, 194, 255),
    },
}


def _new_canvas(width: int, height: int, rgba: tuple[int, int, int, int]) -> bytearray:
    return bytearray(bytes(rgba) * width * height)


def _clamp(v: int, low: int, high: int) -> int:
    return low if v < low else high if v > high else v


def _fill_span(
    pixels: bytearray, width: int, height: int, y: int, x0: int, x1: int, rgba: tuple[int, int, int, int]
) -> None:
    if y < 0 or y >= height:
        return
    left = _clamp(min(x0, x1), 0, width - 1)
    right = _clamp(max(x0, x1), 0, width - 1)
    if right < left:
        return
    idx = (y * width + left) * 4
    pixels[idx : idx + (right - left + 1) * 4] = bytes(rgba) * (right - left + 1)


def _draw_filled_circle(
    pixels: bytearray, width: int, height: int, cx: int, cy: int, radius: int, rgba: tuple[int, int, int, int]
) -> None:
    r2 = radius * radius
    for y in range(cy - radius, cy + radius + 1):
        dy = y - cy
        inside = r2 - dy * dy
        if inside < 0:
            continue
        dx = int(math.sqrt(inside))
        _fill_span(pixels, width, height, y, cx - dx, cx + dx, rgba)


def _draw_filled_ellipse(
    pixels: bytearray,
    width: int,
    height: int,
    cx: int,
    cy: int,
    rx: int,
    ry: int,
    rgba: tuple[int, int, int, int],
) -> None:
    if rx <= 0 or ry <= 0:
        return
    rx2 = rx * rx
    ry2 = ry * ry
    for y in range(cy - ry, cy + ry + 1):
        dy = y - cy
        inside = 1.0 - (dy * dy) / ry2
        if inside < 0:
            continue
        dx = int(rx * math.sqrt(inside))
        _fill_span(pixels, width, height, y, cx - dx, cx + dx, rgba)


def _triangle_area(ax: int, ay: int, bx: int, by: int, cx: int, cy: int) -> int:
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)


def _draw_triangle(
    pixels: bytearray,
    width: int,
    height: int,
    p1: tuple[int, int],
    p2: tuple[int, int],
    p3: tuple[int, int],
    rgba: tuple[int, int, int, int],
) -> None:
    (x1, y1), (x2, y2), (x3, y3) = p1, p2, p3
    min_x = max(0, min(x1, x2, x3))
    max_x = min(width - 1, max(x1, x2, x3))
    min_y = max(0, min(y1, y2, y3))
    max_y = min(height - 1, max(y1, y2, y3))
    area = _triangle_area(x1, y1, x2, y2, x3, y3)
    if area == 0:
        return
    color_bytes = bytes(rgba)
    for y in range(min_y, max_y + 1):
        for x in range(min_x, max_x + 1):
            w1 = _triangle_area(x2, y2, x3, y3, x, y)
            w2 = _triangle_area(x3, y3, x1, y1, x, y)
            w3 = _triangle_area(x1, y1, x2, y2, x, y)
            if (w1 >= 0 and w2 >= 0 and w3 >= 0 and area > 0) or (w1 <= 0 and w2 <= 0 and w3 <= 0 and area < 0):
                idx = (y * width + x) * 4
                pixels[idx : idx + 4] = color_bytes


def _draw_diamond(
    pixels: bytearray,
    width: int,
    height: int,
    cx: int,
    cy: int,
    rx: int,
    ry: int,
    rgba: tuple[int, int, int, int],
) -> None:
    _draw_triangle(pixels, width, height, (cx, cy - ry), (cx + rx, cy), (cx, cy), rgba)
    _draw_triangle(pixels, width, height, (cx + rx, cy), (cx, cy + ry), (cx, cy), rgba)
    _draw_triangle(pixels, width, height, (cx, cy + ry), (cx - rx, cy), (cx, cy), rgba)
    _draw_triangle(pixels, width, height, (cx - rx, cy), (cx, cy - ry), (cx, cy), rgba)


def _draw_eye(
    pixels: bytearray, width: int, height: int, cx: int, cy: int, iris: tuple[int, int, int, int], variant: int
) -> None:
    _draw_filled_circle(pixels, width, height, cx, cy, 5, iris)
    if variant in (0, 2, 4):
        _draw_filled_circle(pixels, width, height, cx - 1, cy - 1, 1, (255, 255, 255, 255))


def _draw_breed_face(pixels: bytearray, width: int, height: int, breed: str, variant: int) -> None:
    style = BREED_STYLE[breed]
    cx = width // 2
    cy = height // 2 + 2

    if breed == "labrador":
        _draw_filled_ellipse(pixels, width, height, cx - 48, cy - 4, 20, 36, style["ear"])
        _draw_filled_ellipse(pixels, width, height, cx + 48, cy - 4, 20, 36, style["ear"])
    else:
        _draw_triangle(pixels, width, height, (cx - 46, cy - 54), (cx - 14, cy - 38), (cx - 32, cy - 92), style["ear"])
        _draw_triangle(pixels, width, height, (cx + 46, cy - 54), (cx + 14, cy - 38), (cx + 32, cy - 92), style["ear"])

    head_rx = 52 if breed != "corgi" else 58
    head_ry = 50 if breed != "corgi" else 46
    _draw_filled_ellipse(pixels, width, height, cx, cy - 4, head_rx, head_ry, style["head"])

    if breed == "shiba":
        _draw_triangle(pixels, width, height, (cx, cy - 44), (cx - 34, cy + 2), (cx + 34, cy + 2), style["muzzle"])
    else:
        _draw_filled_ellipse(pixels, width, height, cx, cy + 12, 34, 26, style["muzzle"])

    if breed == "border_collie":
        _draw_triangle(pixels, width, height, (cx - 40, cy - 42), (cx - 4, cy - 6), (cx - 36, cy + 24), style["ear"])
    elif breed == "schnauzer":
        _draw_triangle(pixels, width, height, (cx - 18, cy - 12), (cx - 2, cy + 18), (cx - 34, cy + 16), style["mask"])
        _draw_triangle(pixels, width, height, (cx + 18, cy - 12), (cx + 2, cy + 18), (cx + 34, cy + 16), style["mask"])
    elif breed == "corgi":
        _draw_filled_ellipse(pixels, width, height, cx, cy - 8, 28, 20, style["mask"])

    eye_y = cy - 16
    eye_dx = 20 if breed != "corgi" else 24
    _draw_eye(pixels, width, height, cx - eye_dx, eye_y, (34, 36, 42, 255), variant)
    _draw_eye(pixels, width, height, cx + eye_dx, eye_y, (34, 36, 42, 255), variant)

    _draw_filled_ellipse(pixels, width, height, cx, cy + 2, 8, 6, (52, 44, 43, 255))
    _draw_filled_ellipse(pixels, width, height, cx - 8, cy + 14, 6, 2, (60, 56, 56, 255))
    _draw_filled_ellipse(pixels, width, height, cx + 8, cy + 14, 6, 2, (60, 56, 56, 255))

    if breed == "schnauzer":
        _draw_filled_ellipse(pixels, width, height, cx, cy + 24, 23, 10, (226, 231, 239, 255))
        _draw_triangle(pixels, width, height, (cx - 22, cy - 24), (cx - 2, cy - 14), (cx - 22, cy - 10), style["ear"])
        _draw_triangle(pixels, width, height, (cx + 22, cy - 24), (cx + 2, cy - 14), (cx + 22, cy - 10), style["ear"])


def _draw_variant_accents(
    pixels: bytearray,
    width: int,
    height: int,
    variant: int,
    accent: tuple[int, int, int, int],
) -> None:
    cx = width // 2
    cy = height // 2
    if variant == 0:
        _draw_filled_circle(pixels, width, height, cx + 68, cy - 66, 5, accent)
    elif variant == 1:
        _draw_diamond(pixels, width, height, cx + 68, cy - 64, 8, 8, accent)
    elif variant == 2:
        _draw_filled_circle(pixels, width, height, cx - 52, cy + 72, 5, accent)
        _draw_filled_circle(pixels, width, height, cx + 0, cy + 78, 5, accent)
        _draw_filled_circle(pixels, width, height, cx + 52, cy + 72, 5, accent)
    elif variant == 3:
        _draw_triangle(pixels, width, height, (cx - 68, cy - 58), (cx - 52, cy - 66), (cx - 60, cy - 42), accent)
        _draw_triangle(pixels, width, height, (cx + 68, cy - 58), (cx + 52, cy - 66), (cx + 60, cy - 42), accent)
    elif variant == 4:
        _draw_filled_ellipse(pixels, width, height, cx, cy + 70, 14, 6, accent)


def _icon_png(name: str, palette_idx: int) -> bytearray:
    pal = PALETTES[palette_idx]
    pixels = _new_canvas(ICON_SIZE, ICON_SIZE, pal["bg"])

    cx = ICON_SIZE // 2
    cy = ICON_SIZE // 2
    _draw_filled_circle(pixels, ICON_SIZE, ICON_SIZE, cx, cy, 88, pal["ring"])
    _draw_filled_circle(pixels, ICON_SIZE, ICON_SIZE, cx, cy, 82, pal["plate"])

    if palette_idx in (1, 3):
        _draw_filled_circle(pixels, ICON_SIZE, ICON_SIZE, cx, cy - 20, 18, pal["bg"])
        _draw_filled_circle(pixels, ICON_SIZE, ICON_SIZE, cx, cy - 20, 14, pal["plate"])
    if palette_idx == 4:
        _draw_triangle(pixels, ICON_SIZE, ICON_SIZE, (cx - 70, cy + 50), (cx + 70, cy + 50), (cx, cy + 82), pal["bg"])

    _draw_breed_face(pixels, ICON_SIZE, ICON_SIZE, name, palette_idx)
    _draw_variant_accents(pixels, ICON_SIZE, ICON_SIZE, palette_idx, pal["accent"])
    return pixels


def _png_chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag)
    crc = zlib.crc32(data, crc)
    return struct.pack("!I", len(data)) + tag + data + struct.pack("!I", crc & 0xFFFFFFFF)


def _write_png(path: Path, width: int, height: int, pixels: bytearray) -> None:
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        start = y * stride
        raw.extend(pixels[start : start + stride])

    ihdr = struct.pack("!IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    png = b"\x89PNG\r\n\x1a\n" + _png_chunk(b"IHDR", ihdr) + _png_chunk(b"IDAT", idat) + _png_chunk(b"IEND", b"")
    path.write_bytes(png)


def _build_contact_sheet() -> None:
    cols = len(PALETTES)
    rows = len(BREEDS)
    gap = 18
    border = 18
    sheet_w = border * 2 + cols * ICON_SIZE + (cols - 1) * gap
    sheet_h = border * 2 + rows * ICON_SIZE + (rows - 1) * gap
    sheet = _new_canvas(sheet_w, sheet_h, (250, 252, 255, 255))

    for row, breed in enumerate(BREEDS):
        for col in range(cols):
            icon_path = OUT_DIR / f"icon_{breed}_{col + 1:02d}.png"
            icon_data = _read_png_rgba(icon_path)
            dst_x = border + col * (ICON_SIZE + gap)
            dst_y = border + row * (ICON_SIZE + gap)
            _paste_rgba(sheet, sheet_w, sheet_h, icon_data, ICON_SIZE, ICON_SIZE, dst_x, dst_y)

    _write_png(OUT_DIR / "icon_contact_sheet_v3.png", sheet_w, sheet_h, sheet)


def _read_png_rgba(path: Path) -> bytearray:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"Not PNG: {path}")
    pos = 8
    width = height = None
    raw_idat = bytearray()
    while pos < len(data):
        chunk_len = struct.unpack("!I", data[pos : pos + 4])[0]
        tag = data[pos + 4 : pos + 8]
        chunk_data = data[pos + 8 : pos + 8 + chunk_len]
        pos += 12 + chunk_len
        if tag == b"IHDR":
            width, height, bit_depth, color_type, *_ = struct.unpack("!IIBBBBB", chunk_data)
            if bit_depth != 8 or color_type != 6:
                raise ValueError(f"Unsupported PNG format in {path}")
        elif tag == b"IDAT":
            raw_idat.extend(chunk_data)
        elif tag == b"IEND":
            break
    if width != ICON_SIZE or height != ICON_SIZE:
        raise ValueError(f"Unexpected icon size {width}x{height} in {path}")
    decompressed = zlib.decompress(bytes(raw_idat))
    result = bytearray()
    stride = ICON_SIZE * 4
    idx = 0
    for _ in range(ICON_SIZE):
        filter_type = decompressed[idx]
        if filter_type != 0:
            raise ValueError(f"Unsupported filter type {filter_type} in {path}")
        idx += 1
        result.extend(decompressed[idx : idx + stride])
        idx += stride
    return result


def _paste_rgba(
    dst: bytearray,
    dst_w: int,
    dst_h: int,
    src: bytearray,
    src_w: int,
    src_h: int,
    x: int,
    y: int,
) -> None:
    for sy in range(src_h):
        dy = y + sy
        if dy < 0 or dy >= dst_h:
            continue
        for sx in range(src_w):
            dx = x + sx
            if dx < 0 or dx >= dst_w:
                continue
            s_idx = (sy * src_w + sx) * 4
            d_idx = (dy * dst_w + dx) * 4
            dst[d_idx : d_idx + 4] = src[s_idx : s_idx + 4]


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for breed in BREEDS:
        for idx in range(len(PALETTES)):
            icon_pixels = _icon_png(breed, idx)
            _write_png(OUT_DIR / f"icon_{breed}_{idx + 1:02d}.png", ICON_SIZE, ICON_SIZE, icon_pixels)
    _build_contact_sheet()
    print(f"Generated v3 icons in: {OUT_DIR}")


if __name__ == "__main__":
    main()
