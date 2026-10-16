# Companion Dog Framework

A Minecraft dog companion with replaceable artwork, procedural interactions,
ball and disc fetch, and protected recovery. This repository brings together
the generic mod source and a human-computer interaction (HCI) design-case paper
on interactive memorial lifecycle design.

![The generic grey-and-white companion shown from the rear, both sides and front in Minecraft](docs/companion-four-views.png)

*The bundled generic dog, captured from four directions by the game client.
This is the standing view used in the paper.*

| For Players and Mod Makers | For Researchers |
| --- | --- |
| [Companion controls](#meet-your-companion), [appearance authoring](#make-your-own-dog) and [code guide](#code-guide) | [Research question and paper](#research), [evidence scope](docs/RESEARCH.md) and [citation](#citation-and-rights) |

## Gameplay Showcase

[![Watch the gameplay video: the bone menu and a companion offering its paw](docs/companion-gameplay-poster.png)](https://github.com/crabsatellite/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4)

**[Watch the 58-second gameplay video](https://github.com/crabsatellite/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4)**
(720p MP4, silent, English captions).

Welcome your companion, open the bone menu, pet, give paw, play bow, feed,
fetch a tennis ball, retrieve a flying disc and settle down to sleep.
The [scene guide](docs/GAMEPLAY_SHOWCASE.md) includes timestamps and recording details.

## Players and Mod Makers

Minecraft **1.21.1**, NeoForge **21.1.84**, Java **21**. Application version:
**0.1.0**. Research tags, such as `research-v2.0`, version the paper/implementation
association without renumbering the mod.

### Meet Your Companion

The mod belongs on both the server and connecting clients. A player receives a
collar on first joining a world. Right-click with it in a safe location to welcome
the bound companion; later uses call that same dog back to you. A replacement
collar can be crafted with leather, string and a gold nugget.

| Interaction | What to do |
| --- | --- |
| Commands and tricks | Hold a bone and right-click your dog to open its menu: follow, sit, lie down, sleep, give paw, play bow and more |
| Petting and feeding | Right-click with an empty hand to pet; offer food accepted by the vanilla wolf-food tag to feed |
| Home | Sneak-right-click with the collar, or choose **Set home here** from the bone menu |
| Ball and disc | Right-click with a tennis ball or flying disc to throw it for retrieval; the ball bounces and the disc uses drag/lift |
| Recovery | Ordinary injuries heal over time; lethal danger initiates protected return and rest. The owner can choose **End rest** after safe arrival |

Craft two tennis balls from a slime ball and lime or yellow wool. A flying disc
uses two paper, a slime ball and cyan dye. The owner can also retrieve a held
toy by right-clicking the dog with an empty hand.

| Give Paw | Play Bow |
| --- | --- |
| ![Four-direction client capture of the dog offering a paw](docs/companion-give-paw.png) | ![Four-direction client capture of the dog in a play bow](docs/companion-play-bow.png) |

*Sampled moments from the existing client captures, not animated previews.
The [known animation limits](#verification-and-known-limits) remain documented.*

### Make Your Own Dog

Appearance is defined in a client resource pack. The server retains ownership,
recall, fetch and recovery behavior while the client loads the chosen geometry
and textures. A selected pack applies to all framework dogs on that client.

1. Start with the [appearance contract](docs/APPEARANCE_CONTRACT.md) and the
   [empty pack template](appearance-pack-template/). The template intentionally
   renders no dog until its geometry and textures are filled in.
2. For a skin change, retain the bundled geometry, UV layout, joint pivots and
   mouth sockets. Replace the normal texture and optional blink, happy, sleep
   and collar textures.
3. For a different shape, author the 19-bone rig in
   `assets/companiondog/appearance/dog.json`. Tune pivots, geometry, motion scale
   and the ball/disc sockets. The existing animation driver provides 5 poses
   and 18 one-shot actions; new proportions need checks across those movements.
4. Enable the pack above the default resources and reload. Inspect poses,
   ground contact, ears, tail, jaw/tongue and toy contact. The hitbox remains
   **0.55 x 0.8 blocks**. Larger breeds need a separate server-side dimension
   policy.

The [included asset generator](tools/generate_default_dog.py) specifies the
default cubes, joint positions, UVs, texture pixels and toy models. The
[asset-authoring brief](docs/ASSET_GENERATION_BRIEF.md) gives a handoff checklist
for another artist or tool. For Blockbench work, use the fixed hierarchy and
coordinate conventions in the contract; the runtime interface is the documented
JSON and PNG files.

### Code Guide

| Area | Implemented behavior |
| --- | --- |
| Companionship | Player binding, collar recall, bone interaction menu, feeding and procedural actions |
| Injury and rest | Ordinary damage with delayed healing; protected return and recovery after lethal danger; owner rest release after safe arrival |
| Administration | Quiet removal with recall-link clearing; a separate archive/conditional-restoration route |
| Fetch | Shared toy claims and retrieval; distinct bouncing-ball and flying-disc physics |
| Appearance | Resource-defined rig and textures, rebuilt on client resource reload |

Under [`src/main/java/com/crabmods/companiondog/`](src/main/java/com/crabmods/companiondog/):

- `entity/CompanionDogEntity.java` handles interaction and lifecycle behavior;
  `CompanionDogGoals.java` handles companion and fetch goals.
- `entity/FetchToyEntity.java`, `TennisBallEntity.java` and `FrisbeeEntity.java`
  share retrieval support while keeping their flight physics separate.
- `world/CompanionDogBindings.java` and `CompanionDogSafety.java` manage saved
  identity, recall, recovery records and safe placement.
- `client/model/CompanionDogModel.java` supplies procedural animation;
  `DogAppearance.java` and `DogAppearanceDefinition.java` load the resource rig.
- `network/` carries client/server commands and arrival feedback.

The framework uses its own entity, item and saved-data identifiers. It does not
provide migration from other companion mods or per-dog breed selection.

- [Source and asset provenance](docs/PROVENANCE.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Research

**Interpreting Memorial Requirements as Lifecycle Rules: A Design Case of
AI-Assisted Construction**

Alex Chengyu Li, Independent Researcher, Tokyo, Japan.

- [English manuscript, version 2.0](docs/memorial-lifecycle-rules-v2.0.pdf)
- [Published version 2.0](https://doi.org/10.5281/zenodo.23417464)
- [Public paper archive and version history](https://doi.org/10.5281/zenodo.23320935)
- [Research scope and evidence map](docs/RESEARCH.md)
- [Changes and release identities](CHANGELOG.md)

### Question and Contribution

How do memorial requirements become revisable lifecycle rules? The retrospective
design case examines a maker's collaboration with a bereaved pet owner through
implemented choices about injury, recovery, removal and restoration.

Its central revision moves from compulsory recovery after every injury to
ordinary healing plus protected return after lethal danger. Administrative
removal and archival recovery provide another contrast: the same quiet
disappearance can retain different possibilities for later recovery. The
analysis distinguishes presentation, interruption of companionship and retained
recovery information.

### How the Code Supports the Paper

The generic implementation is later technical material from the same project.
It makes the retained rules inspectable separately from the historical case's
personal likeness. Version 2.0 adds the four-view client figure above, a lifecycle
rule table, source correspondence and scoped scenario observations.

| Research material | Where to look |
| --- | --- |
| Design sequence and lifecycle analysis | Sections 3-4 and Table 1 of the [English paper](docs/memorial-lifecycle-rules-v2.0.pdf) |
| Generic representation and rule table | Section 5, Figure 1 and Table 2 of the paper |
| Implemented rule paths and evidence scope | [Research overview](docs/RESEARCH.md) and the code guide above |
| Appearance replacement and recorded checks | [Appearance contract](docs/APPEARANCE_CONTRACT.md) and [technical report](docs/RESOURCE_RELOAD_TEST_REPORT.md) |

The working-paper design case reports implemented lifecycle contrasts within
one collaboration. Owner experience and comparative creation effort are topics
for further research. Personal photographs, predecessor artwork, identifying
case material, raw dialogue and predecessor Git history remain excluded.

## Verification and Known Limits

The recorded 13 October 2026 checks support the following bounded results:

| Check | Recorded result |
| --- | --- |
| Server behavior | 52 required GameTests passed |
| JVM appearance contracts | 15 tests passed |
| Static generated assets | 10 tests passed |
| Client resource replacement | 10-scenario reload lane passed |
| Full animation geometry | FAIL retained: ear/tail ground penetration in some movements |

The full client collected 98 frames; that count does not represent 98 accepted
scenes. The maximum recorded decoration penetration was about 3.87 model pixels.
See the [dated technical report](docs/RESOURCE_RELOAD_TEST_REPORT.md). Multiplayer
behavior and compatibility with other breeds require their own acceptance tests.
Archive/restoration support
in the paper is source-based; the selected injury/rest/removal scenarios have
separate recorded test support.

### Build and Check

The canonical launchers require an authorized local installation of the private
managed testing infrastructure. Its installed code and agent instructions are
excluded from this repository and from the production JAR. Building through
these launchers requires that installation. The Python generator/static checks
run independently of it.

```powershell
python -B tools/generate_default_dog.py --check
python -B tools/test_default_dog.py
./mod-build.ps1 build
pwsh -NoProfile -NonInteractive -File tools/run-gametests.ps1
pwsh -NoProfile -NonInteractive -File tools/run-visual-tests.ps1 -ReloadOnly
```

All automated game tests must use the existing hidden, input-isolated, muted
adapters. The full visual lane intentionally remains nonzero until its geometry
finding is repaired and retested.

## Citation and Rights

For the research argument, cite the paper archive:

> Li, Alex Chengyu. 2026. *Interpreting Memorial Requirements as Lifecycle Rules:
> A Design Case of AI-Assisted Construction*. Working paper.
> https://doi.org/10.5281/zenodo.23320935

Machine-readable metadata is in [CITATION.cff](CITATION.cff). Use an exact paper
version DOI when citing a particular public edition; the Concept DOI identifies
the evolving paper archive.

Application code and the generator are **MIT** under [LICENSE](LICENSE).
Generated game assets are **CC BY 4.0** under
[LICENSE-ASSETS.md](LICENSE-ASSETS.md). The paper and listed author-written
research documentation are **CC BY 4.0** under
[LICENSE-PAPER.md](LICENSE-PAPER.md). Third-party components retain their own
terms. [The license scope](LICENSE-SCOPE.md) explicitly covers the author-owned
files at the implementation snapshot cited by the paper.
