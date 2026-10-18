# Gameplay Showcase

[Watch the 58-second gameplay video](https://github.com/CrabMods/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4)

[![Companion dog offering a paw through the bone interaction menu](companion-gameplay-poster.png)](https://github.com/CrabMods/CompanionDogFramework/releases/download/research-v2.0/companiondog-gameplay-showcase.mp4)

## Scene Guide

| Time | Interaction |
| --- | --- |
| 0:00 | Use the collar to welcome the bound companion |
| 0:04.5 | Right-click with a bone and choose Sit |
| 0:09.3 | Pet with an empty hand |
| 0:13.5 | Choose Give paw |
| 0:18.3 | Choose Play bow |
| 0:22.8 | Offer food |
| 0:27 | Throw a tennis ball; watch the chase and return |
| 0:39 | Throw a flying disc; watch its flight and retrieval |
| 0:53 | Choose Sleep |

Recorded on 16 October 2026 with the generic application version 0.1.0,
Minecraft 1.21.1 and NeoForge 21.1.84. The video is 1280 x 720, H.264 MP4,
with English captions and no audio track. It contains no music or personal-case
photographs or likeness.

## Capture and Scope

All nine scenes use one game client and one integrated-server world. Item use
and menu selection exercise the normal client/server interaction paths. The
capture records the ball and disc as projectiles, then in the dog's mouth and
finally returned to the owner's inventory. Actions are live gameplay animation;
the standing/action image gallery elsewhere in the README remains a set of
separate still captures.

Scene transitions omit fixture resets. The camera follows the dog during fetch.
The client ran hidden, unfocused, input-isolated and muted; test configuration
was restored afterwards. This is a single-player gameplay demonstration. The
dated [technical report](RESOURCE_RELOAD_TEST_REPORT.md) retains the separate
animation-geometry findings and the [research overview](RESEARCH.md) describes
the paper's evidence scope.

The project-owned recorder is
[`CompanionShowcase.java`](../src/visualTest/java/com/crabmods/companiondog/visualtest/CompanionShowcase.java).
It belongs to the test source set, not the production mod. Using the existing
authorized test installation described in the README:

```powershell
pwsh -NoProfile -NonInteractive -File tools/run-visual-tests.ps1 -Showcase
python -B tools/encode_showcase.py build/visual-tests/<capture-directory>
```

Only the finished MP4 and the poster are presentation media. Logs, raw frames,
capture receipts, private materials and testing infrastructure are not release
attachments. The original research tag and paper bytes are preserved.

## Reuse

The author's footage, captions and poster use CC BY 4.0 under
[LICENSE-PAPER.md](../LICENSE-PAPER.md). Attribute Alex Chengyu Li / Companion
Dog Framework and indicate modifications. Minecraft and other upstream rights
are unchanged. See [third-party notices](../THIRD_PARTY_NOTICES.md).
