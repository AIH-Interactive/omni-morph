"""Choose reproducible, stratified random inputs from the real preview model pool."""

import argparse
import hashlib
import json
import random
import secrets
import zipfile
from pathlib import Path, PurePosixPath


def kind(path):
    if path.is_dir():
        if (path / "ysm.json").is_file() or (path / "main.json").is_file():
            return "ysm-folder"
        if any(path.rglob("*.bbmodel")):
            return "figura"
        return None
    with path.open("rb") as source:
        header = source.read(11)
    if header.startswith(b"\xef\xbb\xbf"):
        header = header[3:]
    if header.startswith(b"YSGP"):
        if int.from_bytes(header[4:8], "big") in (1, 2):
            return "ysm-legacy"
        return "ysm-encrypted"
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            names = {PurePosixPath(name).name.lower() for name in archive.namelist()}
        if "ysm.json" in names or "main.json" in names:
            return "ysm-package"
        if any(name.endswith(".bbmodel") for name in names):
            return "figura"
    return None


def fingerprint(path):
    digest = hashlib.sha256()
    files = sorted(path.rglob("*")) if path.is_dir() else [path]
    for file in files:
        if not file.is_file():
            continue
        if path.is_dir():
            digest.update(file.relative_to(path).as_posix().encode("utf-8"))
            digest.update(b"\0")
        with file.open("rb") as source:
            for block in iter(lambda: source.read(1048576), b""):
                digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("refs/avatars"))
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/preview/benchmark/random-sample.json"),
    )
    args = parser.parse_args()
    pools = {
        name: []
        for name in ("ysm-folder", "ysm-encrypted", "figura", "ysm-legacy", "ysm-package")
    }
    for path in sorted(args.root.iterdir(), key=lambda entry: entry.name.casefold()):
        category = kind(path)
        if category:
            pools[category].append(path.absolute())
    seed = args.seed if args.seed is not None else secrets.randbits(63)
    rng = random.Random(seed)
    selected = []
    for category, pool in pools.items():
        if pool:
            path = rng.choice(pool)
            selected.append(
                {"kind": category, "path": str(path), "sha256": fingerprint(path)}
            )
    manifest = {
        "seed": seed,
        "method": "Uniform choice per input stratum; casefold-sorted paths; no replacement after failure",
        "pools": {
            category: [str(path) for path in pool] for category, pool in pools.items()
        },
        "selected": selected,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("Pool sizes:", {category: len(pool) for category, pool in pools.items()})
    for item in selected:
        print(json.dumps(item, ensure_ascii=True))
    print("Sample manifest:", args.output.absolute())


if __name__ == "__main__":
    main()
