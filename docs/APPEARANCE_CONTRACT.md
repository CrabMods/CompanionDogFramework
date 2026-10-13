# Appearance Contract v1

## Resource Pack Boundary

The framework reads `assets/companiondog/appearance/dog.json` from active client
resource packs at renderer reload. The production JAR currently contains the
generated default definition and textures. A higher-priority pack overrides
them. Removing that override restores the bundled default; a selected malformed
or incomplete definition yields an empty rig and a logged error rather than
silently showing the default. Missing definitions yield an empty rig. Dedicated
servers do not render or need the client appearance pack.

The empty template declares `pack_format: 34` for Minecraft 1.21.1. Its textures,
sockets and every cube list are intentionally empty; zero pivots are placeholders,
not a finished skeleton. Copy the template to a separate asset-authoring project.

## Skeleton

All these bones must exist, even when their cube lists are empty:

```text
root
  body
    head
      jaw
        tongue
      right_ear
        right_ear_tip
      left_ear
        left_ear_tip
    tail
      tail_tip
    right_front_leg
      right_front_paw
    left_front_leg
      left_front_paw
    right_hind_leg
      right_hind_paw
    left_hind_leg
      left_hind_paw
```

The parent hierarchy is fixed in code. `bones` must contain exactly these 19
keys. Each entry has `pivot: [x,y,z]`, `rotation: [x,y,z]` and `cubes: []`.
Pivots are parent-local offsets in model pixels, rotations are degrees.
Cube origins are bone-local coordinates; sizes are strictly positive pixels.
Y points down, the dog faces negative Z, and the resting ground is Y=24.
Body rest orientation is horizontal. Each leg runs downward at rest; paws,
ears and tail segments pivot at their joint, not their geometric center.

A cube has `origin`, `size`, `uv: [u,v]` and optional `mirror: false`.
Box UV layout follows Minecraft CubeListBuilder; v=0 starts at the texture top.
Use real volume rather than coplanar overlapping surfaces. The reader permits
at most 512 cubes, 256 KiB of JSON, 4096-pixel texture dimensions, and bounded
pivots/rotations/scales. Unknown fields are rejected. PNG dimensions may be a
uniform integer multiple from 1 through 8 of the declared UV grid.

`scale` controls rendered dog size. `motion_scale` scales positional animation
offsets only. Rotational action curves and durations are preserved.
Both fields accept 0.05 through 8; these are parser bounds, not visual approval.
The entity hitbox stays 0.55 x 0.8 blocks. Size-changing breeds require a reviewed
server-side dimension policy; do not hide oversized dogs behind render scaling.

## Textures And Attachments

`textures.normal` is required when any cubes exist. `blink`, `happy`, `sleep`
fall back to `normal`. `collar` is an optional aligned transparent color mask.
Resource locations look like `dogpack:textures/entity/dog/normal.png`; files
must exist in active packs. PNGs are decoded and must match `texture_size`, with
an 8 MiB file limit per texture. The pack author owns UV consistency.

`sockets` must contain `default`, `tennis_ball`, and `frisbee` for visible geometry.
Each has `offset: [x,y,z]` in pixels, `rotation: [x,y,z]` in degrees, and `scale`.
They are evaluated after the root/body/head/jaw animation transform chain.
Item scale cancels the dog's render scale so toys retain their world size.
Tune these sockets for the new muzzle and the new item models; old mouth offsets
are not embedded. The disc renderer keeps a world-oriented disc transform.

Override item models at `assets/companiondog/models/item/companiondog_collar.json`,
`tennis_ball.json` and `frisbee.json`. Add all associated textures in the external
pack. Only the audited generator's default outputs are bundled in this snapshot;
independently authored packs remain separate from that allowlist.

## Reuse And Acceptance

The animation driver retains 5 poses and 18 one-shot actions. Different textures
on the same rig are reusable directly. Different proportions also need appropriate
pivots, motion scale and socket calibration. No arbitrary breed geometry is
automatically guaranteed collision-free by sharing bone names.

Verify standing/walking, sitting/lying/sleeping/belly-up, every action, ground
support, tongue/jaw separation, ear/tail clipping, ball/disc mouth contact and
resource reload. Missing textures and invalid input must return to empty rendering.
Client verification must use a hidden, muted, input-isolated test adapter; no
visible fallback. Blank-pack success is not finished-art visual acceptance.
