#!/usr/bin/env python3
"""Procedural source of the framework's bundled default appearance, "Frost".

Frost is a grey-and-white, husky-style pup: tall prick ears, ice-blue eyes, a
face mask with a widow's peak, a neck ruff and a bushy sickle tail. Every pixel
and every cube is produced by this file. No photograph, reference image, model
or texture is read, and only the Python standard library is used, so the output
is reproducible on any machine.

Running the script rewrites:
  * assets/companiondog/appearance/dog.json   (19-bone rig, sockets, textures)
  * assets/companiondog/textures/entity/dog/   (normal/blink/happy/sleep + collar mask)
  * assets/companiondog/textures/item/         (collar, tennis ball, flying disc)
  * assets/companiondog/models/item/           (item models for those three)

``--check`` regenerates in memory and fails if any committed file differs.
PNGs are compared by decoded pixels, JSON by exact text.

Coordinates are model pixels with Y pointing down, the dog facing -Z and the
ground at Y = 0. Pivots are absolute here; dog.json stores them parent-local
with the root pivot at Y = 24, the Minecraft entity ground level.
"""

from __future__ import annotations

import argparse
import json
import math
import struct
import sys
import zlib
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/companiondog"
NAMESPACE = "companiondog"

UV_W = UV_H = 64  # box-UV space shared by every cube
DENSITY = 2       # texels per model pixel, so entity PNGs are 128 x 128
SCALE = 0.7       # rendered dog size; the 0.55 x 0.8 hitbox is fixed server-side


# ---------------------------------------------------------------- colors

def rgb(value: str, alpha: int = 255) -> tuple[int, int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha


CLEAR = (0, 0, 0, 0)
COAT = rgb("737b86")
COAT_LIGHT = rgb("959ca6")
COAT_DARK = rgb("5a616b")
COAT_DEEP = rgb("474d56")
WHITE = rgb("f3f4f6")
WHITE_SHADE = rgb("dcdfe4")
EYE_RIM = rgb("262a31")
IRIS_LIGHT = rgb("bfe8ff")
IRIS = rgb("6fc0ef")
IRIS_DARK = rgb("3d88c2")
PUPIL = rgb("1b2733")
NOSE = rgb("25282d")
NOSE_DARK = rgb("15171a")
NOSE_SHINE = rgb("5d636b")
LIP = rgb("3c3438")
MOUTH = rgb("5b3c44")
TONGUE = rgb("f08b98")
TONGUE_DARK = rgb("d16a7a")
EAR_INNER = rgb("dcbcbf")
PAD = rgb("3f4148")
PAD_GAP = rgb("62656d")


def mix(a, b, t: float):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(color, amount: float):
    """Positive brightens toward white, negative darkens toward black."""
    if color[3] == 0:
        return color
    target = (255, 255, 255, color[3]) if amount > 0 else (0, 0, 0, color[3])
    return mix(color, target, abs(amount))


def hash01(*values: int) -> float:
    """Deterministic value in [-1, 1] for integer keys (no RNG state)."""
    h = 0x811C9DC5
    for value in values:
        h ^= value & 0xFFFFFFFF
        h = (h * 0x01000193) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 0x5BD1E995) & 0xFFFFFFFF
        h ^= h >> 15
    return (h & 0xFFFF) / 32767.5 - 1.0


def fur(color, key: int, i: int, j: int, strength: float = 0.035):
    return shade(color, hash01(key, i, j) * strength)


# ---------------------------------------------------------------- PNG io

def encode_png(width: int, height: int, pixels: bytes) -> bytes:
    rows = b"".join(b"\x00" + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def decode_png(data: bytes) -> tuple[int, int, bytes]:
    """Decodes 8-bit RGB/RGBA PNGs into RGBA bytes; enough to compare our outputs."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, width, height, channels = 8, b"", 0, 0, 0
    while pos < len(data):
        length, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", body[:10])
            if depth != 8 or color not in (2, 6) or body[12] != 0:
                raise ValueError("unsupported PNG layout")
            channels = 4 if color == 6 else 3
        elif tag == b"IDAT":
            idat += body
    raw, stride = zlib.decompress(idat), width * channels
    out, previous, pos = bytearray(), bytearray(stride), 0
    for _ in range(height):
        kind, line = raw[pos], bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for x in range(stride):
            left = line[x - channels] if x >= channels else 0
            up = previous[x]
            corner = previous[x - channels] if x >= channels else 0
            if kind == 1:
                line[x] = (line[x] + left) & 255
            elif kind == 2:
                line[x] = (line[x] + up) & 255
            elif kind == 3:
                line[x] = (line[x] + (left + up) // 2) & 255
            elif kind == 4:
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        previous = line
        if channels == 3:
            for x in range(width):
                out += line[x * 3:x * 3 + 3] + b"\xff"
        else:
            out += line
    return width, height, bytes(out)


class Canvas:
    def __init__(self, width: int, height: int, fill=CLEAR):
        self.width, self.height = width, height
        self.data = bytearray(bytes(fill) * (width * height))

    def put(self, x: int, y: int, color) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            index = (y * self.width + x) * 4
            self.data[index:index + 4] = bytes(color)

    def get(self, x: int, y: int):
        index = (y * self.width + x) * 4
        return tuple(self.data[index:index + 4])

    def png(self) -> bytes:
        return encode_png(self.width, self.height, bytes(self.data))


# ---------------------------------------------------------------- geometry

@dataclass
class Cube:
    name: str
    material: str
    origin: tuple[float, float, float]  # absolute minimum corner
    size: tuple[float, float, float]
    uv: tuple[int, int] = (0, 0)

    @property
    def end(self):
        return tuple(self.origin[i] + self.size[i] for i in range(3))


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple[float, float, float]  # absolute
    rotation: tuple[float, float, float] = (0.0, 0.0, 0.0)  # degrees, applied Z then Y then X
    cubes: list[Cube] = field(default_factory=list)


RIG_ORDER = ["root", "body", "head", "jaw", "tongue", "tail", "tail_tip",
             "right_ear", "right_ear_tip", "right_front_leg", "right_front_paw",
             "right_hind_leg", "right_hind_paw", "left_ear", "left_ear_tip",
             "left_front_leg", "left_front_paw", "left_hind_leg", "left_hind_paw"]


def build_rig() -> list[Bone]:
    bones = [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, -10, 0.5), cubes=[
            Cube("torso", "torso", (-3, -13, -4.5), (6, 6.5, 10)),
            # Narrower than the skull so their sides never share a plane where they meet.
            Cube("ruff", "ruff", (-3.25, -14, -5.5), (6.5, 6.5, 4)),
            # Painted only by the dyed collar layer; transparent in the coat textures.
            Cube("collar", "collar", (-3.5, -12.75, -5.75), (7, 1.5, 4.5)),
        ]),
        Bone("head", "body", (0, -13.5, -5), cubes=[
            Cube("skull", "skull", (-3.5, -19.5, -9.5), (7, 6, 5)),
            Cube("cheeks", "cheeks", (-4, -16, -7.5), (8, 3, 2.5)),
            Cube("muzzle", "muzzle", (-2, -16.5, -12.5), (4, 2.5, 3)),
            # Its top stays a quarter pixel above the muzzle so the two never share a plane.
            Cube("nose", "nose", (-1, -16.75, -13), (2, 1.5, 1)),
        ]),
        Bone("jaw", "head", (0, -14, -9.5), cubes=[
            Cube("jaw", "jaw", (-1.5, -14, -12), (3, 1, 2.5)),
        ]),
        # Half sunk into the lower jaw, so it never shares the muzzle's underside plane.
        Bone("tongue", "jaw", (0, -14, -10), cubes=[
            Cube("tongue", "tongue", (-1, -14.25, -13), (2, 0.5, 3)),
        ]),
        # A sickle tail: the base rises from the rump and the bushy tip curls over the back.
        Bone("tail", "body", (0, -12, 5.5), (62, 0, 0), cubes=[
            Cube("tail_base", "tail_base", (-1.25, -13.25, 5), (2.5, 2.5, 4)),
        ]),
        Bone("tail_tip", "tail", (0, -12, 9), (48, 0, 0), cubes=[
            Cube("tail_tip", "tail_tip", (-1.5, -13.5, 8.5), (3, 3, 3.5)),
        ]),
    ]
    for side, sign in (("right", -1), ("left", 1)):
        # Wide-set triangular prick ears, stepped 3 -> 2 -> 1 pixels; the base sinks into the skull.
        ear_x = 2.25 * sign
        bones.append(Bone(f"{side}_ear", "head", (ear_x, -19.5, -7), (0, 0, 9 * sign), cubes=[
            Cube(f"{side}_ear_base", "ear_base", (ear_x - 1.5, -21, -7.75), (3, 2, 1.5)),
        ]))
        bones.append(Bone(f"{side}_ear_tip", f"{side}_ear", (ear_x, -21, -7), cubes=[
            Cube(f"{side}_ear_tip", "ear_tip", (ear_x - 1, -22.5, -7.5), (2, 1.5, 1)),
            Cube(f"{side}_ear_point", "ear_point", (ear_x - 0.5, -23.5, -7.5), (1, 1, 1)),
        ]))
        leg_x = 1.5 * sign
        bones.append(Bone(f"{side}_front_leg", "body", (leg_x, -8, -3), cubes=[
            Cube(f"{side}_front_leg", "front_leg", (leg_x - 1.25, -8, -4.25), (2.5, 5, 2.5)),
        ]))
        bones.append(Bone(f"{side}_front_paw", f"{side}_front_leg", (leg_x, -3, -3), cubes=[
            Cube(f"{side}_front_shank", "shank", (leg_x - 1.25, -3, -4.25), (2.5, 1.5, 2.5)),
            Cube(f"{side}_front_foot", "foot", (leg_x - 1.25, -1.5, -4.75), (2.5, 1.5, 3)),
        ]))
        # Haunches sit a quarter pixel outside the torso so their sides never share its plane.
        bones.append(Bone(f"{side}_hind_leg", "body", (leg_x, -9, 3), cubes=[
            Cube(f"{side}_hind_thigh", "thigh", (leg_x + 0.25 * sign - 1.5, -10, 1), (3, 5.5, 4)),
        ]))
        bones.append(Bone(f"{side}_hind_paw", f"{side}_hind_leg", (leg_x, -4.5, 4), cubes=[
            Cube(f"{side}_hind_shank", "shank", (leg_x - 1.25, -4.5, 3), (2.5, 3, 2)),
            Cube(f"{side}_hind_foot", "foot", (leg_x - 1.25, -1.5, 2.5), (2.5, 1.5, 3)),
        ]))
    by_name = {bone.name: bone for bone in bones}
    return [by_name[name] for name in RIG_ORDER]


def region_size(cube: Cube) -> tuple[int, int]:
    w, h, d = cube.size
    return math.ceil(2 * (w + d)), math.ceil(d + h)


def pack_uvs(bones: list[Bone]) -> None:
    """Deterministic shelf packing on the 64 x 64 box-UV grid, tallest first."""
    cubes = [cube for bone in bones for cube in bone.cubes]
    order = sorted(cubes, key=lambda c: (-region_size(c)[1], -region_size(c)[0], c.name))
    x = y = shelf = 0
    for cube in order:
        w, h = region_size(cube)
        if x + w > UV_W:
            x, y, shelf = 0, y + shelf, 0
        cube.uv = (x, y)
        x += w
        shelf = max(shelf, h)
    if y + shelf > UV_H:
        raise SystemExit(f"UV atlas overflow: {y + shelf} > {UV_H}")


FACES = ("top", "bottom", "right", "front", "left", "back")


def face_regions(cube: Cube) -> dict[str, tuple[float, float, float, float]]:
    """Minecraft box-UV layout, named visually: top is the cube's minimum-Y face."""
    u, v = cube.uv
    w, h, d = cube.size
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def surface_point(cube: Cube, face: str, s: float, t: float) -> tuple[float, float, float]:
    """Absolute point for texture fractions (s across, t down) of a face, matching ModelPart.Cube."""
    (x0, y0, z0), (x1, y1, z1) = cube.origin, cube.end
    w, h, d = cube.size
    if face == "top":
        return x0 + s * w, y0, z1 - t * d
    if face == "bottom":
        return x0 + s * w, y1, z1 - t * d
    if face == "right":
        return x0, y0 + t * h, z1 - s * d
    if face == "front":
        return x0 + s * w, y0 + t * h, z0
    if face == "left":
        return x1, y0 + t * h, z0 + s * d
    if face == "back":
        return x1 - s * w, y0 + t * h, z1
    raise ValueError(face)


# ---------------------------------------------------------------- face art

# 14 x 12 texels on the skull front; column 0 is the dog's right (viewer's left).
FACE_BASE = [
    "GGGGGGGGGGGGGG",
    "GGGGGGDDGGGGGG",
    "GGGWWGDDGWWGGG",
    "GWEEEGDDGEEEWG",
    "WWEEEWGGWEEEWW",
    "WWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWW",
    "SWWWWWWWWWWWWS",
    "SSWWWWWWWWWWSS",
]
EYE_ROWS = (3, 4)
MASK_TOP = -18.0  # the white mask starts at face row 4 and wraps onto the skull sides
EYE_COLUMNS = {"right": (2, 3, 4), "left": (9, 10, 11)}
# Two rows per eye, written for the dog's right eye; the left eye is mirrored.
EXPRESSIONS = {
    "normal": ("kbp", "kiI"),
    "blink": ("SSS", "kkk"),
    "happy": ("WkW", "kWk"),
    "sleep": ("WWW", "kkk"),
}
FACE_COLORS = {
    "G": COAT, "D": COAT_DARK, "W": WHITE, "S": WHITE_SHADE, "k": EYE_RIM,
    "b": IRIS_LIGHT, "i": IRIS, "I": IRIS_DARK, "p": PUPIL,
}

MUZZLE_FRONT = [  # 8 x 5; the nose cube sits in front of the top middle
    "WWWWWWWW",
    "WWWWWWWW",
    "WWWSSWWW",
    "WSWWWWSW",
    "SWWWWWWS",
]
NOSE_FRONT = ["NNNN", "nNNn", "NNNN"]
EAR_BASE_FRONT = ["GWPPWG", "GWPPWG", "GWWWWG", "GWWWWG"]
EAR_TIP_FRONT = ["DGGD", "DPPD", "DPPD"]  # row 0 is the top; the inner ear continues from the base


def face_symbol(expression: str, col: int, row: int) -> str:
    if row in EYE_ROWS:
        for side, cols in EYE_COLUMNS.items():
            if col in cols:
                pattern = EXPRESSIONS[expression][row - EYE_ROWS[0]]
                index = cols.index(col) if side == "right" else 2 - cols.index(col)
                symbol = pattern[index]
                return "W" if symbol == "E" else symbol
        if expression == "sleep" and row == EYE_ROWS[1] and col in (1, 12):
            return "k"
    symbol = FACE_BASE[row][col]
    return "W" if symbol == "E" else symbol


def grid(rows: list[str], i: int, j: int, colors: dict, width: int, height: int):
    return colors[rows[j * len(rows) // height][i * len(rows[0]) // width]]


# ---------------------------------------------------------------- materials

def coat_line(z: float) -> float:
    """Y below which the flank turns white; rises a little toward the hind legs."""
    return -9.2 - 0.18 * (z + 4.5)


def paint(material: str, face: str, p, i: int, j: int, tw: int, th: int, expression: str):
    x, y, z = p
    key = sum(ord(c) * (n + 1) for n, c in enumerate(material)) * 7 + FACES.index(face)

    if material == "collar":
        return CLEAR

    if material == "torso":
        if face == "bottom":
            return fur(WHITE_SHADE, key, i, j, 0.02)
        if face == "top":
            spine = abs(x) < 1.25
            return fur(COAT_DEEP if spine else COAT_DARK, key, i, j)
        if face == "back":
            if y > -10.5:
                return fur(WHITE, key, i, j, 0.02)
            return fur(COAT_DARK if abs(x) < 1.5 else COAT, key, i, j)
        if face in ("right", "left"):
            if y > coat_line(z):
                return fur(WHITE, key, i, j, 0.02)
            top = (y + 13) / 3.5  # 0 at the back line
            return fur(mix(COAT_DARK, COAT, top), key, i, j)
        if face == "front" and y > -10.0:  # chest below the ruff
            return fur(WHITE, key, i, j, 0.02)
        return fur(COAT, key, i, j)

    if material == "ruff":
        if face == "front":
            if y < -12.5 and abs(x) > 2.0:
                return fur(COAT, key, i, j)
            return fur(WHITE, key, i, j, 0.02)
        if face in ("right", "left"):
            if y > -11.5 and z < -3.0 or y > coat_line(z):
                return fur(WHITE, key, i, j, 0.02)
            return fur(COAT, key, i, j)
        if face == "bottom":
            return fur(WHITE_SHADE, key, i, j, 0.02)
        return fur(COAT, key, i, j)

    if material == "skull":
        if face == "front":
            col, row = i * 14 // tw, j * 12 // th
            symbol = face_symbol(expression, col, row)
            color = FACE_COLORS[symbol]
            return color if symbol in "kbiIp" else fur(color, key, i, j, 0.025)
        if face == "top":
            peak = abs(x) < 0.75 and z < -7.0
            return fur(COAT_DARK if peak else COAT, key, i, j)
        if face in ("right", "left"):
            if y > MASK_TOP + 0.5 and z < -6.0 - max(0.0, -16.0 - y) * 1.5:
                return fur(WHITE, key, i, j, 0.02)
            return fur(COAT, key, i, j)
        if face == "bottom":
            return fur(WHITE_SHADE, key, i, j, 0.02)
        return fur(COAT, key, i, j)

    if material == "cheeks":
        if face == "top":
            return fur(mix(COAT, WHITE, 0.3), key, i, j)
        if face == "back":
            return fur(COAT_LIGHT if y > -14.5 else COAT, key, i, j)
        if face == "bottom":
            return fur(WHITE_SHADE, key, i, j, 0.02)
        return fur(WHITE, key, i, j, 0.02)

    if material == "muzzle":
        if face == "front":
            return fur(grid(MUZZLE_FRONT, i, j, FACE_COLORS, tw, th), key, i, j, 0.02)
        if face == "top":
            bridge = abs(x) < 0.75 and z > -11.0
            return fur(mix(WHITE, COAT_LIGHT, 0.55) if bridge else WHITE, key, i, j, 0.02)
        if face == "bottom":
            return MOUTH if abs(x) < 1.5 else WHITE_SHADE
        return fur(WHITE, key, i, j, 0.02)

    if material == "nose":
        if face == "front":
            return grid(NOSE_FRONT, i, j, {"N": NOSE, "n": NOSE_DARK}, tw, th)
        if face == "top":
            return NOSE_SHINE if z < -12.4 and abs(x) < 0.6 else NOSE
        return NOSE

    if material == "jaw":
        if face == "top" or face == "back":
            return MOUTH
        if face == "front":
            return LIP if j < th // 2 else fur(WHITE, key, i, j, 0.02)
        if face == "bottom":
            return fur(WHITE_SHADE, key, i, j, 0.02)
        return fur(WHITE, key, i, j, 0.02)

    if material == "tongue":
        if face == "top" and tw > 2 and i in (tw // 2 - 1, tw // 2):
            return TONGUE_DARK
        return TONGUE

    if material == "ear_base":
        if face == "front":
            return fur(grid(EAR_BASE_FRONT, i, j, {"G": COAT_DARK, "W": WHITE, "P": EAR_INNER}, tw, th), key, i, j, 0.02)
        return fur(COAT_DARK if face == "back" else COAT, key, i, j)
    if material == "ear_tip":
        if face == "front":
            return grid(EAR_TIP_FRONT, i, j, {"D": COAT_DEEP, "G": COAT_DARK, "W": WHITE, "P": EAR_INNER}, tw, th)
        return fur(COAT_DARK, key, i, j)
    if material == "ear_point":
        return fur(COAT_DEEP, key, i, j)

    if material == "tail_base":
        if face == "bottom":
            return fur(WHITE, key, i, j, 0.02)
        if face == "top":
            return fur(COAT_DARK, key, i, j)
        if face in ("right", "left") and y > -12.4:
            return fur(mix(COAT, WHITE, 0.6), key, i, j)
        return fur(COAT, key, i, j)
    if material == "tail_tip":
        if face in ("back", "bottom") or z > 11.0:
            return fur(WHITE, key, i, j, 0.02)
        if face == "top":
            return fur(COAT_DARK, key, i, j)
        if face in ("right", "left") and y > -12.0:
            return fur(mix(COAT, WHITE, 0.7), key, i, j)
        return fur(COAT, key, i, j)

    if material == "front_leg":
        outer = (face == "right" and x < 0) or (face == "left" and x > 0)
        if (outer or face == "back") and y < -5.5:
            return fur(mix(COAT, WHITE, max(0.0, (y + 7.5) / 2.0)), key, i, j)
        return fur(WHITE, key, i, j, 0.02)

    if material == "thigh":
        outer = (face == "right" and x < 0) or (face == "left" and x > 0)
        if face == "top" or ((outer or face == "back") and y < -6.5):
            return fur(COAT if y > -9.0 else COAT_DARK, key, i, j)
        return fur(WHITE, key, i, j, 0.02)

    if material == "shank":
        return fur(WHITE, key, i, j, 0.02)

    if material == "foot":
        toe_gap = i in (1, 3)  # feet are 5 texels wide: three toes split by two grooves
        if face == "bottom":
            return PAD_GAP if toe_gap and j >= th - 2 else PAD
        if (face == "front" and j >= 1 or face == "top" and j >= th - 2) and toe_gap:
            return WHITE_SHADE
        return fur(WHITE, key, i, j, 0.02)

    raise ValueError(f"unknown material {material}")


FACE_LIGHT = {"top": 0.05, "bottom": -0.10, "front": 0.0, "back": -0.04, "right": -0.02, "left": -0.02}


def paint_entity(bones: list[Bone], expression: str) -> Canvas:
    canvas = Canvas(UV_W * DENSITY, UV_H * DENSITY)
    for bone in bones:
        for cube in bone.cubes:
            for face, (fu, fv, fw, fh) in face_regions(cube).items():
                tw, th = round(fw * DENSITY), round(fh * DENSITY)
                for j in range(th):
                    for i in range(tw):
                        point = surface_point(cube, face, (i + 0.5) / tw, (j + 0.5) / th)
                        color = paint(cube.material, face, point, i, j, tw, th, expression)
                        if color[3] and cube.material not in ("nose", "tongue"):
                            color = shade(color, FACE_LIGHT[face])
                        canvas.put(round(fu * DENSITY) + i, round(fv * DENSITY) + j, color)
    return canvas


def paint_collar(bones: list[Bone]) -> Canvas:
    """Greyscale mask multiplied by the dye color; only the collar band is opaque."""
    canvas = Canvas(UV_W * DENSITY, UV_H * DENSITY)
    for bone in bones:
        for cube in bone.cubes:
            if cube.material != "collar":
                continue
            for face, (fu, fv, fw, fh) in face_regions(cube).items():
                tw, th = round(fw * DENSITY), round(fh * DENSITY)
                for j in range(th):
                    for i in range(tw):
                        value = 238
                        if face not in ("top", "bottom"):
                            value = 250 if j == 0 else 196 if j == th - 1 else 232
                            if face == "front" and abs(i - (tw - 1) / 2) <= 1.0:
                                value = 255 if j == 1 else 214  # buckle
                        canvas.put(round(fu * DENSITY) + i, round(fv * DENSITY) + j, (value, value, value, 255))
    return canvas


# ---------------------------------------------------------------- appearance file

def number(value: float):
    value = round(value, 4)
    return int(value) if value == int(value) else value


def vector(values) -> list:
    return [number(v) for v in values]


def appearance(bones: list[Bone]) -> dict:
    by_name = {bone.name: bone for bone in bones}
    out = {}
    for bone in bones:
        parent = by_name[bone.parent].pivot if bone.parent else (0, -24, 0)
        pivot = tuple(bone.pivot[i] - parent[i] for i in range(3))
        cubes = []
        for cube in bone.cubes:
            local = tuple(cube.origin[i] - bone.pivot[i] for i in range(3))
            cubes.append({"origin": vector(local), "size": vector(cube.size), "uv": list(cube.uv)})
        out[bone.name] = {"pivot": vector(pivot), "rotation": vector(bone.rotation), "cubes": cubes}
    texture = f"{NAMESPACE}:textures/entity/dog/%s.png"
    return {
        "schema_version": 1,
        "texture_size": [UV_W, UV_H],
        "scale": SCALE,
        "motion_scale": 1,
        "textures": {name: texture % name for name in ("normal", "blink", "happy", "sleep", "collar")},
        # Offsets are jaw-local model pixels; socket scale is divided by the dog scale.
        "sockets": {
            "default": {"offset": [0, 0.4, -1.9], "rotation": [90, 0, 0], "scale": SCALE},
            "tennis_ball": {"offset": [0, -0.55, -1.9], "rotation": [0, 0, 0], "scale": 1},
            "frisbee": {"offset": [0, -0.35, -7.4], "rotation": [172, 0, 0], "scale": 1},
        },
        "bones": out,
    }


def dumps(value, indent: int = 0) -> str:
    """JSON with short numeric arrays and cube objects kept on one line."""
    pad = "  " * indent
    if isinstance(value, dict):
        if not value:
            return "{}"
        if all(not isinstance(v, (dict, list)) or (isinstance(v, list) and all(not isinstance(x, (dict, list)) for x in v))
               for v in value.values()) and len(value) <= 4 and indent >= 3:
            return "{" + ", ".join(f"{json.dumps(k)}: {dumps(v)}" for k, v in value.items()) + "}"
        items = [f"{pad}  {json.dumps(k)}: {dumps(v, indent + 1)}" for k, v in value.items()]
        return "{\n" + ",\n".join(items) + f"\n{pad}}}"
    if isinstance(value, list):
        if all(not isinstance(v, (dict, list)) for v in value):
            return "[" + ", ".join(json.dumps(v) for v in value) + "]"
        if not value:
            return "[]"
        items = [f"{pad}  {dumps(v, indent + 1)}" for v in value]
        return "[\n" + ",\n".join(items) + f"\n{pad}]"
    return json.dumps(value)


# ---------------------------------------------------------------- items

def draw_disc(canvas: Canvas, cx: float, cy: float, radius: float, color) -> None:
    for y in range(canvas.height):
        for x in range(canvas.width):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= radius:
                canvas.put(x, y, color)


def item_collar() -> Canvas:
    """A blue nylon collar seen at an angle, with a silver buckle and round tag."""
    canvas = Canvas(16, 16)
    band, band_dark, band_light = rgb("3d6fd6"), rgb("2a4c9a"), rgb("78a2f0")
    silver, silver_dark = rgb("d9dde3"), rgb("8d949e")
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.6, (y + 0.5 - 7.2) / 4.6
            r = math.hypot(dx, dy)
            if 0.72 <= r <= 1.0:
                color = band_dark if r > 0.9 or (dy < 0 and r < 0.8) else band
                if dy > 0.1 and 0.78 < r < 0.88:
                    color = band_light
                canvas.put(x, y, color)
    for x, y in ((2, 7), (3, 7), (2, 8), (3, 8)):
        canvas.put(x, y, silver)
    canvas.put(2, 9, silver_dark)
    draw_disc(canvas, 8.0, 13.6, 2.1, silver_dark)
    draw_disc(canvas, 8.0, 13.4, 1.5, silver)
    canvas.put(7, 11, silver_dark)
    canvas.put(8, 11, silver_dark)
    return canvas


BALL_FUZZ, BALL_FUZZ_DARK, BALL_SEAM = rgb("cfe23a"), rgb("a9bf22"), rgb("f7f8ec")


def item_ball() -> Canvas:
    """16 x 16 felt with an S-shaped seam; every ball face samples the middle of it."""
    canvas = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            curve = 8 + 2.2 * math.sin((x + 0.5 - 8) * 0.75)
            seam = abs(y + 0.5 - curve) < 0.6
            base = BALL_FUZZ_DARK if hash01(31, x, y) > 0.55 else BALL_FUZZ
            canvas.put(x, y, BALL_SEAM if seam else shade(base, hash01(17, x, y) * 0.05))
    return canvas


def ball_model() -> dict:
    """A 6-pixel core with a 4 x 4 cap on each side: an 8-pixel rounded voxel ball."""
    def face(u0, v0, u1, v1):
        return {"uv": [u0, v0, u1, v1], "texture": "#ball"}

    elements = [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": {
        side: face(5, 5, 11, 11) for side in ("north", "south", "east", "west", "up", "down")}}]
    caps = {
        "west": ([4, 6, 6], [5, 10, 10]), "east": ([11, 6, 6], [12, 10, 10]),
        "down": ([6, 4, 6], [10, 5, 10]), "up": ([6, 11, 6], [10, 12, 10]),
        "north": ([6, 6, 4], [10, 10, 5]), "south": ([6, 6, 11], [10, 10, 12]),
    }
    opposite = {"west": "east", "east": "west", "up": "down", "down": "up", "north": "south", "south": "north"}
    for side, (start, end) in caps.items():
        faces = {s: face(6, 6, 10, 10) if s == side else face(6, 5, 10, 6)
                 for s in ("north", "south", "east", "west", "up", "down") if s != opposite[side]}
        elements.append({"from": start, "to": end, "faces": faces})
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NAMESPACE}:item/tennis_ball", "ball": f"{NAMESPACE}:item/tennis_ball"},
        "display": {
            "ground": {"translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.72, 0.72, 0.72]},
            "fixed": {"scale": [0.75, 0.75, 0.75]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]},
        },
        "elements": elements,
    }


DISC_RIM, DISC_RIM_DARK = rgb("e8602e"), rgb("b84520")
DISC_PLATE, DISC_RING, DISC_CENTER = rgb("ff8a55"), rgb("fff1e2"), rgb("ffc23d")
DISC_UNDER, DISC_UNDER_RIM, DISC_WALL_INNER = rgb("c9532a"), rgb("a8411d"), rgb("8f3618")


def disc_profile(x: int, z: int):
    """(bottom, top, side swatch) for the 1 x 1 cell at item pixel (x, z), or None outside."""
    radius = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
    if radius > 6.0:
        return None
    if radius > 5.0:
        return 7.0, 9.0, 0   # thick rolled rim
    return 8.0, 8.75, 1      # thin flight plate with a hollow underside


def item_frisbee() -> Canvas:
    """32 x 32: top view and underside side by side (one texel per model pixel), wall swatches below.

    Item-model UVs always span 0..16 across the texture, so one texel is half a UV unit here.
    """
    canvas = Canvas(32, 32)
    for z in range(16):
        for x in range(16):
            radius = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
            if radius > 6.0:
                top = under = DISC_RIM_DARK
            elif radius > 5.0:
                top = DISC_RIM if radius < 5.6 else DISC_RIM_DARK
                under = DISC_UNDER_RIM
            elif 3.3 < radius < 4.2:
                top, under = DISC_RING, DISC_UNDER
            elif radius < 1.6:
                top, under = DISC_CENTER, DISC_UNDER
            else:
                top, under = DISC_PLATE, DISC_UNDER
            canvas.put(x, z, shade(top, hash01(5, x, z) * 0.03))
            canvas.put(16 + x, z, under)
    for y in range(16, 18):
        for x in range(4):
            canvas.put(x, y, DISC_RIM_DARK if x < 2 else DISC_WALL_INNER)
    return canvas


def frisbee_model() -> dict:
    cells = {(x, z): disc_profile(x, z) for x in range(16) for z in range(16)}
    cells = {k: v for k, v in cells.items() if v}
    elements = []
    for z in range(16):  # merge each row into runs that share one profile
        x = 0
        while x < 16:
            profile = cells.get((x, z))
            if profile is None:
                x += 1
                continue
            end = x + 1
            while cells.get((end, z)) == profile:
                end += 1
            elements.append({"from": [x, profile[0], z], "to": [end, profile[1], z + 1], "faces": {
                "up": {"uv": [x / 2, z / 2, end / 2, (z + 1) / 2], "texture": "#disc"},
                "down": {"uv": [8 + x / 2, z / 2, 8 + end / 2, (z + 1) / 2], "texture": "#disc"},
            }})
            x = end
    swatch = {0: [0, 8, 1, 9], 1: [1, 8, 2, 9]}
    for (x, z), (bottom, top, kind) in sorted(cells.items()):
        for side, dx, dz in (("north", 0, -1), ("south", 0, 1), ("west", -1, 0), ("east", 1, 0)):
            neighbor = cells.get((x + dx, z + dz))
            spans = [(bottom, top)] if neighbor is None else [
                (bottom, min(top, neighbor[0])), (max(bottom, neighbor[1]), top)]
            for low, high in spans:
                if high - low <= 0:
                    continue
                # Walls facing the hollow underside use the darker inner swatch.
                inner = neighbor is not None and high <= neighbor[0]
                elements.append({"from": [x, low, z], "to": [x + 1, high, z + 1], "faces": {
                    side: {"uv": swatch[1 if inner else 0], "texture": "#disc"}}})
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": f"{NAMESPACE}:item/frisbee", "disc": f"{NAMESPACE}:item/frisbee"},
        "display": {
            "ground": {"translation": [0, 0, 0], "scale": [0.65, 0.65, 0.65]},
            "gui": {"rotation": [35, 225, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
            "fixed": {"rotation": [90, 0, 0], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 25, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        },
        "elements": elements,
    }


def collar_model() -> dict:
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NAMESPACE}:item/companiondog_collar"}}


# ---------------------------------------------------------------- outputs

def build_outputs() -> dict[str, bytes | Canvas]:
    bones = build_rig()
    pack_uvs(bones)
    outputs: dict[str, bytes | Canvas] = {
        "appearance/dog.json": (dumps(appearance(bones)) + "\n").encode(),
        "models/item/companiondog_collar.json": (json.dumps(collar_model(), indent=2) + "\n").encode(),
        "models/item/tennis_ball.json": (json.dumps(ball_model(), indent=2) + "\n").encode(),
        "models/item/frisbee.json": (json.dumps(frisbee_model(), indent=2) + "\n").encode(),
        "textures/entity/dog/collar.png": paint_collar(bones),
        "textures/item/companiondog_collar.png": item_collar(),
        "textures/item/tennis_ball.png": item_ball(),
        "textures/item/frisbee.png": item_frisbee(),
    }
    for expression in EXPRESSIONS:
        outputs[f"textures/entity/dog/{expression}.png"] = paint_entity(bones, expression)
    return outputs


def generated_paths() -> list[str]:
    """Repository-relative paths owned by this generator."""
    return sorted(str((ASSETS / name).relative_to(ROOT)).replace("\\", "/") for name in build_outputs())


def check() -> list[str]:
    problems = []
    for name, content in build_outputs().items():
        path = ASSETS / name
        if not path.is_file():
            problems.append(f"missing {name}")
            continue
        if isinstance(content, Canvas):
            try:
                width, height, pixels = decode_png(path.read_bytes())
            except (ValueError, zlib.error, struct.error) as error:
                problems.append(f"unreadable {name}: {error}")
                continue
            if (width, height, pixels) != (content.width, content.height, bytes(content.data)):
                problems.append(f"pixels differ from generator: {name}")
        elif path.read_bytes().replace(b"\r\n", b"\n") != content:
            problems.append(f"content differs from generator: {name}")
    return problems


def write() -> None:
    for name, content in build_outputs().items():
        path = ASSETS / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content.png() if isinstance(content, Canvas) else content)
        print(f"wrote {path.relative_to(ROOT)}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify committed outputs instead of writing")
    args = parser.parse_args(argv)
    if args.check:
        problems = check()
        for problem in problems:
            print(problem, file=sys.stderr)
        if problems:
            print("Run: python tools/generate_default_dog.py", file=sys.stderr)
            return 1
        print(f"Generated assets match ({len(build_outputs())} files).")
        return 0
    write()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
