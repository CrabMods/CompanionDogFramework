# Brief For An Independent Asset Generator

Create original dog artwork in a separate resource pack for Minecraft 1.21.1.
Use `appearance-pack-template/` and `docs/APPEARANCE_CONTRACT.md` as the exact
interface. Do not edit behavior, networking, lifecycle or animation Java files.
Do not use private reference files or clone another author's model/texture.

Fill the 19 named bones with parent-local pivots, degree rotations and local
cube geometry. Include normal texture, optionally blink/happy/sleep/collar,
and three mouth sockets for default items, tennis ball and disc. Author matching
collar/ball/disc item models and textures. All art stays in this separate pack.

Use the framework's horizontal quadruped rest orientation and downward legs.
Keep the visual footprint consistent with the fixed 0.55 x 0.8 block hitbox.
For a skin-only variation, keep geometry, UV, pivots and sockets unchanged.
For different proportions, tune these fields and check every pose/action.

Deliver the resource pack plus a provenance/license note for each input.
Provide rendered multi-view pose/action sheets and note unresolved clipping.
These are authoring aids; a real hidden-client render test is still required.
The framework supports one globally selected appearance pack, not simultaneous
per-entity breed assets. Do not imply unimplemented selection behavior.
