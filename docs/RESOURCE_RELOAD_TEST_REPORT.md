# Resource Reload Test, 13 October 2026

## Decision

Resource-location and resource-pack switching: **PASS** for the tested matrix.
Full visual acceptance of the current bundled appearance: **FAIL**. The
default rig's ears and tail can penetrate the ground during animation. No
production behavior, action curves, geometry or texture files were changed.

This test uses the actual current local resources, including the generated
default dog. README.md and APPEARANCE_CONTRACT.md still describe an empty
production appearance; that documentation is stale relative to these bytes.
No rejected/archived artwork was imported for this test.

## Executed Checks

| Lane | Actual result | Evidence |
| --- | --- | --- |
| Canonical Build | PASS; managed infrastructure v0.1.14, generated assets and repository/JAR boundary intact | build/resource-reload-build-20261013.log |
| JVM appearance contracts | 15 tests, zero failures/errors; unchanged compiled tests reused by Build | build/test-results/test/TEST-*.xml |
| Static generated assets | 10 tests passed | canonical Build log |
| Server behavior | All 52 required GameTests passed in one process/world | build/minecraft-mod-testing/gametest-9cb2b1bd-c4ef-492a-a3bf-d98cacc863bf.json |
| Reload-only client | 10 scenarios passed, one hidden client and one world, zero cleanup/isolation failures | build/visual-tests/20261013T063524Z/client-receipt.json |
| Full client collection | 98 frames collected; global FAIL retained for geometry, not upgraded by frame visibility | build/visual-tests/20261013T063848Z/client-receipt.json |
| Final readback | PASS for current inputs, PNG hashes, config restoration, scoped reload result and retained geometry failure | build/resource-reload-verification-20261013.json |

The reload-only process took about 64 seconds including launch; its in-client
scenario interval was about 39 seconds. The full collection took about 80
seconds including launch. Every client remained hidden, unfocused, windowed,
cursor-free, input/clipboard-isolated and device-muted, with runtime assertions.
No production client/server was started, replaced or pushed. No multiplayer
acceptance is claimed by an integrated-server render fixture or by GameTests.

## Resource Matrix

The first frame verifies the bundled appearance. Subsequent cases use the real
Minecraft pack repository and asynchronous resource reload on the same entity
and in the same world:

1. Directory pack: a different texture namespace/path and diagnostic joint
   offset both reach the rebuilt entity renderer.
2. ZIP pack: the same contract works for a ZIP and a second texture path.
3. Two packs: the later/higher-priority pack wins for both geometry and texture.
4. Blank rig: no stale dog geometry or texture remains; the rendered subject
   pixel count is zero.
5. Invalid JSON: logged rejection, empty rendering, no crash.
6. Missing referenced texture: logged rejection, empty rendering, no crash.
7. Invalid PNG: logged rejection, empty rendering, no crash.
8. Incompatible texture resolution: logged rejection, empty rendering, no crash.
9. Remove overrides: the default texture and joint pose return, with visible
   pixels and a newly created renderer.

Each successful reload checks the actual renderer identity, baked cube count,
initial joint position and chosen texture location, followed by screenshot
pixel observations. Fixtures copy the existing generated PNG unchanged; no
new art is substituted. Pack definitions, selection observations, screenshots
and source manifests are retained in the run outputs.

Expected rejection logs during invalid cases are not application crashes.
Vanilla TextureManager also warns about formerly loaded texture paths that are
absent after removing their pack. The new renderer and current screenshots do
not use those stale paths. No persistent missing-texture image was observed
in the inspected successful frames.

## Open Visual Defect

The actual model audit sampled **13,029** valid pose/action/progress/look
combinations. At STAND / ROLL_OVER / 49 percent / negative look, the maximum
decoration penetration is **3.869217 model pixels**. The left ear tip reaches
that depth; the right ear tip reaches 3.7818832 pixels, and the tail tip reaches
0.6484966 pixels across the sample set. Body/limb support penetration is only
floating-point noise (about 0.0000038 pixels).

Locator: src/main/java/com/crabmods/companiondog/client/model/CompanionDogModel.java,
ground correction and lowestSupportPoint(). Ground support intentionally
excludes ears/tail; their animation can consequently extend below that support
plane. This is an asset/animation compatibility issue, not a resource reload
failure. Fixing it requires an explicit visual repair and a renewed full lane;
standing-frame success does not clear it.

The full run preserves FAIL while collecting all 89 original appearance frames
and the 9 reload frames. Mouth-ball/disc and tongue closeups were also inspected.
Visibility and finite sample checks do not certify all possible proportions,
inter-part collisions, Z-fighting during every transition or subjective quality.
The 98 completed screenshot rows are not 98 accepted visual scenes.

## Test Changes And Preservation

- Added test-source-only ResourceReloadSuite.java; nothing from visualTest is
  shipped in the production JAR.
- Extended VisualHarness with nine reload cases and an explicitly scoped
  ReloadOnly lane. The full lane still fails on the geometry defect.
- Moved runtime preparation under the managed lease/process guard. Existing
  saves are no longer recursively cleared; new worlds use session UUIDs.
- Restored fml.toml, options.txt, any pre-existing appearance-under-test.zip,
  and the launcher Java environment; removed only session-owned fixture packs
  after checked absolute-path boundaries. Restoration JSON reads back PASS.
- Rehashed all production source/resource files against the first run's input
  inventory: zero changes. All test runtimes exited; no test-process remainder
  was found in the final project-scoped process check.

Earlier failed attempts remain evidence: one test compilation attempted private
ModelPart cube access and was repaired using the public visitor; the first full
run stopped at the true geometry defect; the first resolution fixture was
actually a valid integer-scale texture and was corrected to an incompatible
grid with a pre-launch rejection assertion. None is reported as a production
resource-loader failure or silently converted into PASS.

## Reproduction

```powershell
./mod-build.ps1 build
pwsh -NoProfile -NonInteractive -File tools/run-visual-tests.ps1 -ReloadOnly
pwsh -NoProfile -NonInteractive -File tools/run-visual-tests.ps1
pwsh -NoProfile -NonInteractive -File tools/run-gametests.ps1
```

The full appearance command currently exits nonzero as intended. All tests
must keep the existing hidden/isolation adapters; no visible fallback is valid.
An optional PackDirectory can select a concrete future asset pack, but this run
does not certify another agent's future pack or different breeds.
