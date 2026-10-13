#!/usr/bin/env python3
"""Static contracts for the generated default appearance. Run: python tools/test_default_dog.py"""

from __future__ import annotations

import itertools
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_default_dog as gen  # noqa: E402

# Bones whose rest transform has no rotation all the way up, so absolute rest coordinates are exact.
UNROTATED = {"root", "body", "head", "jaw", "tongue"} | {
    f"{side}_{end}_{part}" for side in ("right", "left") for end in ("front", "hind") for part in ("leg", "paw")}

AXIS = {"top": (1, 0), "bottom": (1, 1), "right": (0, 0), "left": (0, 1), "front": (2, 0), "back": (2, 1)}


def rig():
    bones = gen.build_rig()
    gen.pack_uvs(bones)
    return bones


def faces_of(origin, end):
    for face, (axis, positive) in AXIS.items():
        plane = (end if positive else origin)[axis]
        others = [i for i in range(3) if i != axis]
        yield face, axis, positive, plane, tuple((origin[i], end[i]) for i in others)


def overlap_area(a, b):
    area = 1.0
    for (lo, hi), (olo, ohi) in zip(a, b):
        area *= max(0.0, min(hi, ohi) - max(lo, olo))
    return area


class GeneratorTests(unittest.TestCase):
    def test_committed_files_match_generator(self):
        self.assertEqual(gen.check(), [])

    def test_uv_regions_fit_and_never_overlap(self):
        cubes = [cube for bone in rig() for cube in bone.cubes]
        rects = []
        for cube in cubes:
            for face, (u, v, w, h) in gen.face_regions(cube).items():
                self.assertGreaterEqual(u, 0)
                self.assertGreaterEqual(v, 0)
                self.assertLessEqual(u + w, gen.UV_W, cube.name)
                self.assertLessEqual(v + h, gen.UV_H, cube.name)
                self.assertEqual((w * gen.DENSITY) % 1, 0, (cube.name, face))
                self.assertEqual((h * gen.DENSITY) % 1, 0, (cube.name, face))
                rects.append(((u, u + w), (v, v + h), f"{cube.name}.{face}"))
        for a, b in itertools.combinations(rects, 2):
            self.assertEqual(overlap_area(a[:2], b[:2]), 0, (a[2], b[2]))

    def test_coat_faces_are_fully_opaque_and_collar_band_is_exclusive(self):
        bones = rig()
        collar = gen.paint_collar(bones)
        for expression in gen.EXPRESSIONS:
            coat = gen.paint_entity(bones, expression)
            for cube in (c for bone in bones for c in bone.cubes):
                for face, (u, v, w, h) in gen.face_regions(cube).items():
                    for y in range(round(v * gen.DENSITY), round((v + h) * gen.DENSITY)):
                        for x in range(round(u * gen.DENSITY), round((u + w) * gen.DENSITY)):
                            coat_alpha, collar_alpha = coat.get(x, y)[3], collar.get(x, y)[3]
                            if cube.material == "collar":
                                self.assertEqual((coat_alpha, collar_alpha), (0, 255), (expression, cube.name, face))
                            else:
                                self.assertEqual((coat_alpha, collar_alpha), (255, 0), (expression, cube.name, face))

    def test_expressions_only_change_the_eyes(self):
        bones = rig()
        skull = next(c for b in bones for c in b.cubes if c.name == "skull")
        u, v, w, h = gen.face_regions(skull)["front"]
        d = gen.DENSITY
        eye_rows = range(round(v * d) + gen.EYE_ROWS[0], round(v * d) + gen.EYE_ROWS[1] + 1)
        eye_cols = {round(u * d) + c for cols in gen.EYE_COLUMNS.values() for c in cols} | {
            round(u * d) + 1, round(u * d) + 12}
        normal = gen.paint_entity(bones, "normal")
        for expression in ("blink", "happy", "sleep"):
            other = gen.paint_entity(bones, expression)
            changed = {(i % other.width, i // other.width) for i in range(other.width * other.height)
                       if normal.data[i * 4:i * 4 + 4] != other.data[i * 4:i * 4 + 4]}
            self.assertTrue(changed, expression)
            self.assertTrue(all(y in eye_rows and x in eye_cols for x, y in changed), expression)

    def test_no_coincident_same_facing_faces_at_rest(self):
        bones = {bone.name: bone for bone in rig()}
        cubes = [(name, cube) for name in UNROTATED for cube in bones[name].cubes]
        for (na, a), (nb, b) in itertools.combinations(cubes, 2):
            for fa, fb in itertools.product(faces_of(a.origin, a.end), faces_of(b.origin, b.end)):
                if fa[0] == fb[0] and abs(fa[3] - fb[3]) < 1e-6:
                    self.assertEqual(overlap_area(fa[4], fb[4]), 0, (a.name, b.name, fa[0]))

    def test_rig_is_mirror_symmetric(self):
        bones = {bone.name: bone for bone in rig()}
        for name, bone in bones.items():
            if not name.startswith("right_"):
                continue
            mirror = bones["left_" + name[len("right_"):]]
            self.assertEqual(bone.pivot, (-mirror.pivot[0],) + mirror.pivot[1:])
            self.assertEqual(bone.rotation, (mirror.rotation[0], -mirror.rotation[1], -mirror.rotation[2]))
            for a, b in zip(bone.cubes, mirror.cubes):
                self.assertEqual(a.size, b.size)
                self.assertEqual(a.origin[1:], b.origin[1:])
                self.assertAlmostEqual(a.origin[0], -(b.origin[0] + b.size[0]))

    def test_appearance_matches_framework_contract(self):
        data = gen.appearance(rig())
        self.assertEqual(set(data["bones"]), set(gen.RIG_ORDER))
        self.assertEqual(set(data["sockets"]), {"default", "tennis_ball", "frisbee"})
        self.assertEqual(data["bones"]["root"]["pivot"], [0, 24, 0])
        self.assertLessEqual(sum(len(b["cubes"]) for b in data["bones"].values()), 512)
        self.assertLess(len(gen.dumps(data)), 262144)


class ItemModelTests(unittest.TestCase):
    def assert_no_coplanar_overlap(self, model):
        planes = {}
        axes = {"up": (1, 1), "down": (1, 0), "east": (0, 1), "west": (0, 0), "south": (2, 1), "north": (2, 0)}
        for element in model["elements"]:
            a, b = element["from"], element["to"]
            self.assertTrue(all(0 <= lo < hi <= 16 for lo, hi in zip(a, b)), element)
            for side, face in element["faces"].items():
                self.assertTrue(all(0 <= value <= 16 for value in face["uv"]), face)
                axis, positive = axes[side]
                plane = (b if positive else a)[axis]
                rect = tuple((a[i], b[i]) for i in range(3) if i != axis)
                bucket = planes.setdefault((side, plane), [])
                for other in bucket:
                    self.assertEqual(overlap_area(rect, other), 0, (side, plane, rect, other))
                bucket.append(rect)

    def test_disc_is_volumetric_with_raised_rim_and_hollow_underside(self):
        model = gen.frisbee_model()
        self.assert_no_coplanar_overlap(model)
        tops = [e for e in model["elements"] if "up" in e["faces"]]
        center = next(e for e in tops if e["from"][0] <= 8 < e["to"][0] and e["from"][2] <= 8 < e["to"][2])
        self.assertGreater(max(e["to"][1] for e in tops), center["to"][1])
        self.assertLess(min(e["from"][1] for e in tops), center["from"][1])

    def test_ball_is_closed_and_centered(self):
        model = gen.ball_model()
        self.assert_no_coplanar_overlap(model)
        lows = [min(e["from"][i] for e in model["elements"]) for i in range(3)]
        highs = [max(e["to"][i] for e in model["elements"]) for i in range(3)]
        self.assertEqual(lows, [4, 4, 4])
        self.assertEqual(highs, [12, 12, 12])
        self.assertEqual(model["display"]["ground"]["translation"], [0, 0, 0])

    def test_item_models_reference_generated_textures(self):
        outputs = gen.build_outputs()
        for name in ("companiondog_collar", "tennis_ball", "frisbee"):
            model = json.loads(outputs[f"models/item/{name}.json"])
            for location in model["textures"].values():
                namespace, path = location.split(":")
                self.assertEqual(namespace, gen.NAMESPACE)
                self.assertIn(f"textures/{path}.png", outputs)


if __name__ == "__main__":
    unittest.main()
