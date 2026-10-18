# Companion Dog Framework

A Minecraft companion dog for petting, tricks, fetch and owner-controlled home and rest. Resource packs can change its appearance.

[Full guide](https://crabmods.com/mods/companion) · [Gameplay demo](https://github.com/CrabMods/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4) · [Source](https://github.com/CrabMods/CompanionDogFramework)

## Play and customize

- Welcome or recall the same bound dog with its collar.
- Use a bone for the owner's command menu; pet and feed the dog.
- Throw a bouncing tennis ball or gliding flying disc for fetch.
- Set a home and control rest after protected recovery.
- Replace geometry and textures through a client resource pack.

Targets Minecraft 1.21.1, NeoForge and Java 21. Install on the server and clients. The published release currently contains research and showcase assets, with no installable JAR; build from source using `./mod-build.ps1 build`. The build requires a separately installed managed test runner.

The first world login gives a collar (dropped nearby if inventory is full). Right-click with it in a safe place, then use a bone on your dog. A replacement collar uses leather, string and a gold nugget.

[Controls, recipes and recovery](https://crabmods.com/mods/companion) · [Appearance contract](docs/APPEARANCE_CONTRACT.md) · [Pack template](appearance-pack-template/) · [Source code](src/main/java/com/crabmods/companiondog/) · [Known animation limits](docs/RESOURCE_RELOAD_TEST_REPORT.md)

## Research and rights

The accompanying design case examines how memorial requirements become companion lifecycle rules.

[Paper](docs/memorial-lifecycle-rules-v2.0.pdf) · [Research overview](docs/RESEARCH.md) · [Citation](CITATION.cff) · [Provenance](docs/PROVENANCE.md) · [Changes](CHANGELOG.md)

Code and generator: [MIT](LICENSE). Game assets: [CC BY 4.0](LICENSE-ASSETS.md). Paper, documentation and footage: [CC BY 4.0](LICENSE-PAPER.md). [License scope](LICENSE-SCOPE.md) · [Third-party notices](THIRD_PARTY_NOTICES.md).

## 中文

Minecraft 伙伴狗：抚摸、把戏、网球与飞盘捡回，以及由主人控制的回家和休息。资源包可以替换外观。

目标为 Minecraft 1.21.1 / NeoForge / Java 21，服务端和客户端均需安装。目前发布页仅含研究与演示素材，没有可直接安装的 JAR，需从源码构建。首次进服会收到项圈，用它迎接或召回同一只绑定伙伴；主人主手持骨头右键打开菜单。

[完整指南](https://crabmods.com/mods/companion)包含操作、配方、恢复和外观制作说明；[研究说明](docs/RESEARCH.md)介绍配套设计案例。
