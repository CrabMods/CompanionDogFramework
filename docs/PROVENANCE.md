# Source And Asset Provenance

Audit date: 13 October 2026. Scope: the current generic source/asset snapshot,
not the identifying private predecessor or every similar work on the internet.

## Code

The generic application retains the author's companion behavior, procedural
animation curves and recovery/removal logic with new identifiers and a separate
resource-defined rig interface. Source is included, rather than only compiled
classes. Runtime behavior is programmed Java behavior; no online AI service,
HTTP client, telemetry or paid external service call was found in the application
source scan. Minecraft's own multiplayer packets, game services and dependency
downloads are different from an application AI backend.

The procedural animation driver specifies transforms and action timings in
CompanionDogModel.java. The repository contains no imported animation timeline,
third-party model/animation package or binary character asset. The audit does
not assert that every sine curve, common movement or numeric value is unique
across all existing mods. The supported claim is that the included source and
retained local development support author-directed, AI-assisted construction;
no copied external character-animation carrier was found in this snapshot.

## Default Appearance

`tools/generate_default_dog.py` defines the current geometry, joint positions,
UV allocation, per-pixel paint and toy item geometry. It uses Python's standard
library. Its `build_outputs()` constructs all 12 bundled appearance/item output
files without consuming a photograph, reference image, model or downloaded
texture. The file reads in `check()` compare existing outputs after generation;
they are not source inputs for the drawing/geometry algorithm.

The audit executed `python -B tools/generate_default_dog.py --check`: all
12 outputs match, with decoded-pixel comparison for PNG and normalized line
endings for JSON. The repository/JAR boundary audit also passed and found no
predecessor case names in the separately scoped identifier check.

These checks establish the reproducible production path and inspected import
boundary of the supplied assets. Their reuse terms are specified in
LICENSE-ASSETS.md, with third-party rights preserved.

## Rights And Dependencies

The 13 October snapshot was a private All Rights Reserved backup. On 16 October
2026, the author explicitly authorized public repository access and selected
MIT for application code/the generator and CC BY 4.0 for generated assets and
the paper. Current scope is recorded in LICENSE, LICENSE-ASSETS.md and
LICENSE-PAPER.md. LICENSE-SCOPE.md expressly includes the cited implementation
snapshot in the author-owned grant; upstream rights are unchanged. Publication status is established
by the actual repository/release record, not by this provenance statement.

Minecraft, NeoForge, ModDevGradle, Gson, JUnit and the Gradle wrapper are not
claimed as original application work. Dependencies are resolved by their
upstream toolchain; the game client/server and dependency caches are not
distributed with this source snapshot. The spawn egg refers to a vanilla
template rather than copying its texture. See THIRD_PARTY_NOTICES.md.

The audit consulted the official Minecraft EULA and the Japanese Agency for
Cultural Affairs' AI/copyright overview. Distribution of an original mod is
different from redistributing a modified game. AI generation does not by itself
settle whether use infringes another work or whether the resulting expression
is protectable. Relevant official sources:

- https://www.minecraft.net/en-us/eula
- https://github.com/neoforged/neoforge
- https://www.bunka.go.jp/english/policy/copyright/pdf/94055801_01.pdf

These links support the rights boundaries; they are not an individual legal
clearance for this particular asset set.

## Research Use

The generic appearance is a later research/demo asset, not a reconstruction of
the private case's original likeness. Do not retroactively call this snapshot
the exact artifact used in the earlier owner collaboration. Use separate
identities for the historical case, the generic code snapshot and future tests.

The current 52 server tests and resource-reload observations support their
recorded contracts. The full visual audit still reports ear/tail ground
penetration. User acceptance of the current appearance is retained alongside
that failure, not substituted for it. The private build-infrastructure dependency
also remains an external reproducibility gap; no anonymous build is claimed.
