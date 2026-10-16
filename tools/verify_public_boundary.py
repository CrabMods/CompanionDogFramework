#!/usr/bin/env python3
"""Publication boundary check for the repository and, optionally, the built JAR.

* Game images must match tools/generate_default_dog.py. Documentation images must
  match the exact reviewed generic-client captures below. Photos, 3D-editor sources,
  audio and archives are rejected. Documentation images are never allowed in the JAR.
* Text files are scanned for credential signatures.
* Optional restricted terms (for example private names) are searched in paths, file
  contents, Git messages/remotes and JAR entries. Keep such lists outside the repository:

    python tools/verify_public_boundary.py --jar build/libs/<jar> --restricted-terms-file ../private-terms.txt
"""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_default_dog as generator  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SKIP = {"build", ".gradle", "runs", "run", ".git", "local-appearance-packs", "__pycache__", ".idea", ".vscode"}
FORBIDDEN = {".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".tif", ".tiff", ".heic", ".psd", ".kra",
             ".bbmodel", ".blend", ".obj", ".fbx", ".gltf", ".glb", ".ogg", ".wav", ".mp3", ".flac",
             ".mp4", ".mov", ".zip"}
TEXT = {".java", ".json", ".md", ".gradle", ".properties", ".toml", ".ps1", ".mcmeta", ".yaml",
        ".yml", ".py", ".cmd", ".bat", ".txt", ".gitignore", ".gitattributes"}
CREDENTIAL = re.compile(r"(?i)(ghp_[a-z0-9]{20,}|github_pat_[a-z0-9_]{30,}|"
                        r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|AKIA[0-9A-Z]{16})")
UNICODE_ESCAPE = re.compile(r"\\u+([0-9a-fA-F]{4})")

# Exact generic captures; this does not allow arbitrary documentation artwork.
DOCUMENTATION_IMAGES = {
    "docs/companion-four-views.png": "ace7bed8b917dea43e7489c97b36237367c9e234049c985146eca6cd86e83759",
    "docs/companion-give-paw.png": "e3f9ace3a99c7250d926f6adaccd7b2dc22ca2d4aa06293bc6a5f42d0983d8d6",
    "docs/companion-play-bow.png": "a9c5dbd8a9f29f9927fbd0d1e625b37d5e4226db38213384212fa6724eae5b70",
    "docs/companion-gameplay-poster.png": "1daf835afe27449a331cd3e913fdb5a6d40e212fb1ad20408705ea8413591050",
}


def decoded(text: str) -> str:
    return UNICODE_ESCAPE.sub(lambda m: chr(int(m.group(1), 16)), text)


def contains(text: str, terms: list[str]) -> list[str]:
    lowered = decoded(text).casefold()
    return [term for term in terms if term.casefold() in lowered]


def repository_files():
    for path in sorted(ROOT.rglob("*")):
        relative = path.relative_to(ROOT)
        if path.is_file() and relative.parts[0] not in SKIP and "__pycache__" not in relative.parts:
            yield path, relative.as_posix()


def check_repository(terms: list[str]) -> list[str]:
    failures = []
    generated = set(generator.generated_paths())
    seen_documentation = set()
    for path, relative in repository_files():
        suffix = path.suffix.lower() or path.name.lower()
        if contains(relative, terms):
            failures.append(f"restricted term in path: {relative}")
        if suffix in FORBIDDEN:
            failures.append(f"artwork/media must not be committed: {relative}")
        if suffix == ".png" and relative not in generated:
            expected = DOCUMENTATION_IMAGES.get(relative)
            if expected is None:
                failures.append(f"image is not a generator output or reviewed documentation capture: {relative}")
            else:
                seen_documentation.add(relative)
                if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                    failures.append(f"documentation image differs from reviewed capture: {relative}")
        if suffix in TEXT:
            text = path.read_text(encoding="utf-8", errors="replace")
            if CREDENTIAL.search(text):
                failures.append(f"credential signature in {relative}")
            if contains(text, terms):
                failures.append(f"restricted term in {relative}")
    failures += [f"documentation image missing: {relative}"
                 for relative in sorted(set(DOCUMENTATION_IMAGES) - seen_documentation)]
    failures += [f"generated asset drift: {problem}" for problem in generator.check()]
    if (ROOT / ".git").exists() and terms:
        history = subprocess.run(["git", "-C", str(ROOT), "log", "--all", "--format=%an%n%ae%n%B"],
                                 capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
        remotes = subprocess.run(["git", "-C", str(ROOT), "remote", "-v"],
                                 capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
        if contains(history + remotes, terms):
            failures.append("restricted term in Git history or remotes")
    return failures


def check_jar(jar: Path, terms: list[str]) -> list[str]:
    failures = []
    expected = {}
    for relative in generator.generated_paths():
        if relative.endswith(".png"):
            entry = relative.split("src/main/resources/", 1)[1]
            expected[entry] = (ROOT / relative).read_bytes()
    seen = set()
    with zipfile.ZipFile(jar) as archive:
        for info in archive.infolist():
            name = info.filename
            if info.is_dir():
                continue
            data = archive.read(info)
            if contains(name, terms):
                failures.append(f"restricted term in JAR path: {name}")
            if re.search(r"Tests(\$.*)?\.class$|/visualtest/|ModelGeometry", name):
                failures.append(f"test or legacy class in JAR: {name}")
            suffix = Path(name).suffix.lower()
            if suffix in FORBIDDEN:
                failures.append(f"artwork/media in JAR: {name}")
            if suffix == ".png":
                if name not in expected:
                    failures.append(f"unexpected image in JAR: {name}")
                elif expected[name] != data:
                    failures.append(f"JAR image differs from repository: {name}")
                seen.add(name)
            if terms and contains(data.decode("utf-8", errors="replace"), terms):
                failures.append(f"restricted term in JAR entry: {name}")
    failures += [f"generated image missing from JAR: {name}" for name in sorted(set(expected) - seen)]
    return failures


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--restricted-term", action="append", default=[])
    parser.add_argument("--restricted-terms-file", type=Path,
                        help="UTF-8 file with one term per line; keep it outside the repository")
    args = parser.parse_args(argv)
    terms = [t for t in args.restricted_term if t.strip()]
    if args.restricted_terms_file:
        terms += [line.strip() for line in args.restricted_terms_file.read_text(encoding="utf-8").splitlines()
                  if line.strip() and not line.startswith("#")]
    failures = check_repository(terms)
    if args.jar:
        failures += check_jar(args.jar, terms)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        return 1
    scope = "repository and JAR" if args.jar else "repository"
    print(f"Public boundary check passed for {scope}; restricted-term count={len(terms)}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
