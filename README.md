# Companion Dog Framework

A Minecraft companion dog for petting, tricks and games of fetch. Customize its
appearance with a resource pack, or use the source to build your own companion
mod. The accompanying paper explores how personal memorial wishes become game
behavior.

![Front, rear and side views of the grey-and-white companion](docs/companion-four-views.png)

| Players and Mod Makers | Researchers |
| --- | --- |
| [Meet your companion](#meet-your-companion), [make your own dog](#make-your-own-dog), [explore the code](#code-guide) | [Read the paper](#research), [research overview](docs/RESEARCH.md), [citation](#citation-and-rights) |

> **Full player guide:** For advanced features, complete recipes, detailed effects, configuration and compatibility, visit the [official Companion Dog guide](https://crabmods.com/mods/companion).

**Minecraft 1.21.1 · NeoForge · Java 21.** The current public release contains research and showcase assets, not an installable JAR. Build from source to play; the build requires a separately installed managed test runner.

## Gameplay Showcase

[![Watch the gameplay demo: the companion offers a paw through the bone menu](docs/companion-gameplay-poster.png)](https://github.com/CrabMods/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4)

**[Watch the gameplay demo](https://github.com/CrabMods/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4)**
with English captions. The video is silent.

Welcome your companion, pet, give paw, play bow, feed, fetch a tennis ball,
retrieve a flying disc and settle down to sleep.
[Scene guide](docs/GAMEPLAY_SHOWCASE.md).

## Players and Mod Makers

For **Minecraft 1.21.1**, **NeoForge** and **Java 21**.

### Meet Your Companion

Install the mod on the server and connecting clients. Your collar arrives when
you first join a world. Right-click with it in a safe place to welcome your dog;
use it again to call your companion back to you. A replacement collar can be
crafted with leather, string and a gold nugget.

| Interaction | What to do |
| --- | --- |
| Commands and tricks | As the owner, hold a bone in your main hand and right-click your dog for follow, sit, lie down, sleep, give paw, play bow and other commands |
| Petting | Right-click with an empty hand |
| Feeding | Offer food accepted by vanilla wolves |
| Home | Sneak-right-click with the collar, or choose **Set home here** in the bone menu |
| Fetch | Throw a tennis ball or flying disc; your dog brings it back. An empty-hand right-click also takes a held toy |
| Recovery | Ordinary injuries heal over time. Lethal danger sends your companion home to rest; choose **End rest** after arrival to resume companionship |

The ball bounces; the disc glides. Both share the same retrieval behavior.
[Collar and toy recipes](src/main/resources/data/companiondog/recipe/).

| Give Paw | Play Bow |
| --- | --- |
| ![The companion offering a paw](docs/companion-give-paw.png) | ![The companion in a play bow](docs/companion-play-bow.png) |

### Make Your Own Dog

Geometry and textures live in a client resource pack. Ownership, recall, fetch and recovery stay in the behavior code. The selected appearance applies to all framework dogs on that client.

Start with the [appearance contract](docs/APPEARANCE_CONTRACT.md) and [pack template](appearance-pack-template/). Replace the skin or adapt the rig while keeping the documented bone hierarchy and toy attachment points. The official guide covers the authoring workflow and in-game checks.

### Code Guide

Explore [`src/main/java/com/crabmods/companiondog/`](src/main/java/com/crabmods/companiondog/) for interactions, recall, toy physics and animation.

### Build

Build with `./mod-build.ps1 build` after installing the required managed test runner.

The ears and tail can currently clip into the ground during some animations. See the [technical report](docs/RESOURCE_RELOAD_TEST_REPORT.md) for remaining issues.

## Research

**[Interpreting Memorial Requirements as Lifecycle Rules: A Design Case of AI-Assisted Construction](docs/memorial-lifecycle-rules-v2.0.pdf)**

How should a digital companion respond to injury, absence and return? The accompanying design case examines how memorial requirements become inspectable companion lifecycle rules.

[Research overview](docs/RESEARCH.md) · [Citation](CITATION.cff) · [Source and asset provenance](docs/PROVENANCE.md) · [Changes](CHANGELOG.md)

## Citation and Rights

Code and generator: [MIT](LICENSE). Game assets: [CC BY 4.0](LICENSE-ASSETS.md). Paper, documentation and showcase footage: [CC BY 4.0](LICENSE-PAPER.md). See [license scope](LICENSE-SCOPE.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

## More details

For more advanced features, full recipes, effect values, configuration and compatibility help, see the **[official player guide](https://crabmods.com/mods/companion)**.

[Downloads and release notes](https://github.com/CrabMods/CompanionDogFramework/releases) · [Report a bug](https://github.com/CrabMods/CompanionDogFramework/issues)
