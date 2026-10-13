#!/usr/bin/env python3
"""Offline rest-pose preview of a dog appearance (dog.json + textures), standard library only.

This is an authoring aid, not visual acceptance: it draws the rig at rest (plus any
--rotate overrides) with flat lighting. Poses and actions come from the Java animation
driver and must be checked in the hidden Minecraft client (tools/run-visual-tests.ps1).

    python tools/preview_dog.py build/preview.png
    python tools/preview_dog.py out.png --assets path/to/pack --texture happy --rotate jaw:25,0,0
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_default_dog import Canvas, decode_png  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
PARENTS = {"root": None, "body": "root", "head": "body", "jaw": "head", "tongue": "jaw",
           "tail": "body", "tail_tip": "tail"}
for _side in ("right", "left"):
    PARENTS[f"{_side}_ear"] = "head"
    PARENTS[f"{_side}_ear_tip"] = f"{_side}_ear"
    for _end in ("front", "hind"):
        PARENTS[f"{_side}_{_end}_leg"] = "body"
        PARENTS[f"{_side}_{_end}_paw"] = f"{_side}_{_end}_leg"


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def rotation_zyx(x_deg, y_deg, z_deg):
    """Matches ModelPart: Quaternionf.rotationZYX(z, y, x) == Rz * Ry * Rx."""
    x, y, z = (math.radians(a) for a in (x_deg, y_deg, z_deg))
    rx = [[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]]
    ry = [[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]]
    rz = [[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]]
    return matmul(rz, matmul(ry, rx))


def load_texture(assets: list[Path], location: str):
    namespace, path = location.split(":", 1)
    for base in assets:
        candidate = base / "assets" / namespace / path
        if candidate.is_file():
            return decode_png(candidate.read_bytes())
    raise SystemExit(f"texture not found: {location}")


def cube_faces(origin, size, uv):
    """Quads as (corners, uv corners) following ModelPart.Cube's box-UV layout."""
    x0, y0, z0 = origin
    w, h, d = size
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    u, v = uv
    def quad(points, u0, v0, u1, v1):  # points ordered: (u0,v0) (u1,v0) (u1,v1) (u0,v1)
        return points, [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    return [
        quad([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], u + d, v, u + d + w, v + d),          # top
        quad([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], u + d + w, v, u + d + 2 * w, v + d),  # bottom
        quad([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], u, v + d, u + d, v + d + h),          # west
        quad([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], u + d, v + d, u + d + w, v + d + h),  # north
        quad([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], u + d + w, v + d, u + 2 * d + w, v + d + h),  # east
        quad([(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),  # south
    ]


def world_quads(definition: dict, overrides: dict[str, tuple[float, float, float]]):
    bones = definition["bones"]
    frames = {}
    quads = []
    for name, parent in PARENTS.items():
        bone = bones[name]
        rotation = [bone["rotation"][i] + overrides.get(name, (0, 0, 0))[i] for i in range(3)]
        local = rotation_zyx(*rotation)
        if parent is None:
            matrix, offset = local, list(bone["pivot"])
        else:
            pm, po = frames[parent]
            matrix, offset = matmul(pm, local), [po[i] + apply(pm, bone["pivot"])[i] for i in range(3)]
        frames[name] = (matrix, offset)
        for cube in bone["cubes"]:
            for points, uvs in cube_faces(cube["origin"], cube["size"], cube["uv"]):
                world = [[offset[i] + apply(matrix, p)[i] for i in range(3)] for p in points]
                quads.append((world, uvs))
    return quads


def render(quads, texture, uv_size, yaw, pitch, scale, canvas: Canvas, ox, oy, zbuffer):
    tw, th, pixels = texture
    sx, sy = tw / uv_size[0], th / uv_size[1]
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    light = [0.35, -0.75, -0.55]
    norm = math.sqrt(sum(c * c for c in light))
    light = [c / norm for c in light]

    def view(p):
        x, y, z = p[0], p[1] - 24, p[2]
        x, z = x * cy + z * sy_, -x * sy_ + z * cy      # yaw about the vertical axis
        y, z = y * cp - z * sp, y * sp + z * cp         # pitch the camera
        return x, y, z

    for world, uvs in quads:
        pts = [view(p) for p in world]
        e1 = [pts[1][i] - pts[0][i] for i in range(3)]
        e2 = [pts[3][i] - pts[0][i] for i in range(3)]
        n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
        length = math.sqrt(sum(c * c for c in n)) or 1
        brightness = 0.62 + 0.38 * abs(sum(n[i] / length * light[i] for i in range(3)))
        screen = [(ox + p[0] * scale, oy + p[1] * scale, p[2]) for p in pts]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (screen[k] for k in tri)
            ua, ub, uc = (uvs[k] for k in tri)
            area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if abs(area) < 1e-9:
                continue
            for py in range(max(0, int(min(a[1], b[1], c[1]))), min(canvas.height, int(max(a[1], b[1], c[1])) + 1)):
                for px in range(max(0, int(min(a[0], b[0], c[0]))), min(canvas.width, int(max(a[0], b[0], c[0])) + 1)):
                    qx, qy = px + 0.5, py + 0.5
                    w0 = ((b[0] - qx) * (c[1] - qy) - (b[1] - qy) * (c[0] - qx)) / area
                    w1 = ((c[0] - qx) * (a[1] - qy) - (c[1] - qy) * (a[0] - qx)) / area
                    w2 = 1 - w0 - w1
                    if min(w0, w1, w2) < -1e-6:
                        continue
                    depth = w0 * a[2] + w1 * b[2] + w2 * c[2]
                    index = py * canvas.width + px
                    if depth >= zbuffer[index]:
                        continue
                    u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
                    v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
                    tx = min(tw - 1, max(0, int(u * sx)))
                    ty = min(th - 1, max(0, int(v * sy)))
                    offset = (ty * tw + tx) * 4
                    if pixels[offset + 3] < 128:
                        continue
                    zbuffer[index] = depth
                    canvas.put(px, py, tuple(min(255, int(pixels[offset + k] * brightness)) for k in range(3)) + (255,))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Render a rest-pose contact sheet of a dog appearance.")
    parser.add_argument("output", type=Path)
    parser.add_argument("--assets", type=Path, action="append",
                        help="resource root(s) containing assets/<namespace>/...; first match wins")
    parser.add_argument("--texture", default="normal", help="normal, blink, happy or sleep")
    parser.add_argument("--collar", action="store_true", help="overlay the collar mask in a dye color")
    parser.add_argument("--rotate", action="append", default=[], metavar="BONE:X,Y,Z",
                        help="extra rotation in degrees added to a bone, e.g. jaw:25,0,0")
    parser.add_argument("--scale", type=float, default=11.0, help="screen pixels per model pixel")
    args = parser.parse_args(argv)
    assets = args.assets or [ROOT / "src/main/resources"]
    definition = None
    for base in assets:
        candidate = base / "assets/companiondog/appearance/dog.json"
        if candidate.is_file():
            definition = json.loads(candidate.read_text(encoding="utf-8"))
            break
    if definition is None:
        raise SystemExit("dog.json not found under --assets")
    overrides = {}
    for item in args.rotate:
        bone, values = item.split(":")
        overrides[bone] = tuple(float(v) for v in values.split(","))
    textures = definition["textures"]
    texture = load_texture(assets, textures.get(args.texture, textures["normal"]))
    collar = load_texture(assets, textures["collar"]) if args.collar and "collar" in textures else None
    quads = world_quads(definition, overrides)

    views = [("front 3/4", 35, 12), ("left side", 90, 0), ("front", 0, 0), ("rear 3/4", 215, 12), ("top", 0, 70)]
    cell_w, cell_h, s = int(30 * args.scale), int(32 * args.scale), args.scale
    canvas = Canvas(cell_w * len(views), cell_h, (206, 218, 196, 255))
    for index, (_, yaw, pitch) in enumerate(views):
        zbuffer = [math.inf] * (canvas.width * canvas.height)
        ox, oy = cell_w * index + cell_w / 2, cell_h * 0.8
        for x in range(cell_w * index + 4, cell_w * (index + 1) - 4):  # ground line
            canvas.put(x, int(oy), (150, 160, 140, 255))
        render(quads, texture, definition["texture_size"], yaw, pitch, s, canvas, ox, oy, zbuffer)
        if collar is not None:
            tinted = (collar[0], collar[1], bytes(
                int(c * f) if k % 4 != 3 else c
                for k, (c, f) in enumerate(zip(collar[2], [0.24, 0.47, 0.86, 1] * (len(collar[2]) // 4)))))
            # The coat pass leaves the collar band transparent, so a depth-buffer copy suffices.
            render(quads, tinted, definition["texture_size"], yaw, pitch, s, canvas, ox, oy, list(zbuffer))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(canvas.png())
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
