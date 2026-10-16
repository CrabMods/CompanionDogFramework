# Companion Dog Framework

Research software for interactive memorial lifecycle design, combining
a generic game-companion implementation with a retrospective HCI design case.

**Research source repository.** The paper has a separate public archive. The
generic implementation and generated appearance are later technical material;
they are distinct from the original collaboration and personal likeness.

## Paper

**Interpreting Memorial Requirements as Lifecycle Rules: A Design Case of
AI-Assisted Construction**

Alex Chengyu Li, Independent Researcher, Tokyo, Japan.

- [English manuscript, version 2.0](docs/memorial-lifecycle-rules-v2.0.pdf)
- [Public paper archive and version history](https://doi.org/10.5281/zenodo.23320935)
- [Research scope and evidence map](docs/RESEARCH.md)
- [Changes and release identities](CHANGELOG.md)

The paper asks how memorial requirements become revisable lifecycle rules.
Its central case follows the revision from compulsory recovery after any injury
to ordinary healing plus protected return after lethal danger. Administrative
removal and archival recovery provide a second contrast: the same quiet
disappearance can retain different possibilities for later recovery. The
analysis distinguishes presentation, interruption of companionship, and retained
recovery information.

The later generic implementation supports technical inspection of these rules
apart from the personal appearance. Version 2.0 adds a recorded four-view client
figure, a lifecycle rule table, source correspondence and scoped scenario
observations. The working-paper design case reports implemented lifecycle
contrasts within one collaboration. Owner experience and comparative creation
effort are topics for further research.

## Implementation

Minecraft **1.21.1**, NeoForge **21.1.84**, Java **21**. Application version:
**0.1.0**. Research tags, such as `research-v2.0`, version the paper/implementation
association without renumbering the mod.

| Area | Implemented behavior |
| --- | --- |
| Companionship | Player binding, collar recall, bone interaction menu, feeding and procedural actions |
| Injury and rest | Ordinary damage with delayed healing; protected return and recovery after lethal danger; owner rest release after safe arrival |
| Administration | Quiet removal with recall-link clearing; a separate archive/conditional-restoration route |
| Fetch | Shared toy claims and retrieval; distinct bouncing-ball and flying-disc physics |
| Appearance | Resource-defined rig and textures, rebuilt on client resource reload |

The framework uses its own entity, item and saved-data identifiers. It does not
provide migration from other companion mods or per-dog breed selection. A client
appearance pack affects all framework dogs on that client.

## Appearance and Provenance

The grey-and-white default dog, entity textures and toy/item assets are generated
by [the included program](tools/generate_default_dog.py). It defines geometry,
joint positions, UVs and pixels without consuming a photograph, reference image,
downloaded model or texture. The included source specifies a reproducible
asset-construction path.

Personal photographs, predecessor artwork, identifying case material, raw
dialogue and predecessor Git history are excluded. The original private project
is not modified by this research archive.

- [Source and asset provenance](docs/PROVENANCE.md)
- [Appearance contract](docs/APPEARANCE_CONTRACT.md)
- [Asset-generation brief](docs/ASSET_GENERATION_BRIEF.md)
- [Empty external-pack template](appearance-pack-template/)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

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
