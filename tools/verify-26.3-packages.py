"""Verify local 26.3 loader deliveries against their production base JARs."""

import hashlib
import io
import json
from pathlib import Path
import tomllib
import zipfile


ROOT = Path(__file__).resolve().parent.parent


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def verify(loader):
    name = f"omni-morph-1.0.0-alpha.7+26.3-{loader}-mc"
    base_path = ROOT / loader / "build/libs" / f"{name}.jar"
    delivery_path = ROOT / "build/local-native" / f"{name}-windows-local.jar"
    test_root = ROOT / "common/src/test/java"
    test_classes = {
        path.relative_to(test_root).with_suffix("").as_posix()
        for path in test_root.rglob("*.java")
    }
    # Offline fixtures intentionally shadow a few loader implementations by FQN.
    # Those names legitimately belong to production; reject test-only classes.
    production_classes = set()
    for module in ("common", loader):
        source_root = ROOT / module / "src/main/java"
        production_classes.update(
            path.relative_to(source_root).with_suffix("").as_posix()
            for path in source_root.rglob("*.java")
        )
    test_classes -= production_classes
    with zipfile.ZipFile(base_path) as base, zipfile.ZipFile(delivery_path) as jar:
        names = jar.namelist()
        require(len(names) == len(set(names)), f"{loader}: duplicate ZIP entries")
        require(jar.testzip() is None, f"{loader}: corrupt ZIP entry")
        for entry in base.infolist():
            if not entry.is_dir():
                require(base.read(entry.filename) == jar.read(entry.filename),
                        f"{loader}: changed base entry {entry.filename}")
        for entry in names:
            if entry.endswith(".class"):
                outer = entry.removesuffix(".class").split("$", 1)[0]
                require(outer not in test_classes, f"{loader}: packaged test class {entry}")
        configs = ["figura-common.mixins.json", "omni-morph-common.mixins.json",
                   "omni-morph.mixins.json"]
        for config in configs:
            data = json.loads(jar.read(config))
            for side in ("mixins", "client", "server"):
                for mixin in data.get(side, []):
                    path = (data["package"] + "." + mixin).replace(".", "/") + ".class"
                    require(path in names, f"{loader}: missing configured mixin {path}")
        if loader == "fabric":
            metadata = json.loads(jar.read("fabric.mod.json"))
            require(metadata["id"] == "omni-morph", "Fabric ID")
            require(metadata["depends"]["minecraft"] == "26.3", "Fabric Minecraft version")
            require(metadata["depends"]["java"] == ">=25", "Fabric Java version")
            dependencies = [item["file"] for item in metadata["jars"]]
            require(metadata["accessWidener"] == "omni-morph.accesswidener", "Fabric AW")
            jar.read(metadata["accessWidener"])
            version = metadata["version"]
        else:
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            require(metadata["mods"][0]["modId"] == "omni_morph", "NeoForge ID")
            dependencies = [item["path"] for item in
                            json.loads(jar.read("META-INF/jarjar/metadata.json"))["jars"]]
            declared = {item["modId"]: item["versionRange"]
                        for item in metadata["dependencies"]["omni_morph"]}
            require(declared["minecraft"] == "[26.3]", "NeoForge Minecraft version")
            require(declared["neoforge"] == "[26.3.0.101,26.4)", "NeoForge version range")
            jar.read("META-INF/accesstransformer.cfg")
            version = metadata["mods"][0]["version"]
        require(version.startswith("1.0.0-alpha.7+26.3-"), f"{loader}: expanded version")
        require(dependencies, f"{loader}: no embedded dependencies")
        for dependency in dependencies:
            require(dependency in names, f"{loader}: missing dependency {dependency}")
            with zipfile.ZipFile(io.BytesIO(jar.read(dependency))) as nested:
                require(nested.testzip() is None, f"{loader}: corrupt dependency {dependency}")
        require(jar.read("licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt"),
                f"{loader}: project license")
        require(jar.read("credits/OmniMorph.txt"), f"{loader}: credits")
        solid_shader = "assets/omni-morph/shaders/core/solid.vsh"
        require(jar.read(solid_shader) == (ROOT / "common/src/main/resources" / solid_shader).read_bytes(),
                f"{loader}: solid vertex shader resource")
        require(jar.read("natives/NOTICE.txt") == (ROOT / "native/NOTICE.txt").read_bytes(),
                f"{loader}: native notice")
        manifest = json.loads(jar.read("natives/manifest.json"))
        require(manifest["abi"] == 2 and manifest["buildKind"] == "local", "Local ABI2 manifest")
        require(len(manifest["libraries"]) == 1, "Exactly one actual local native platform")
        native = manifest["libraries"][0]
        require(native["platform"] == "windows-x86_64" and native["abi"] == 2, "Native platform/ABI")
        require(sha256(jar.read(native["path"])) == native["sha256"], "Native DLL hash")
        require(native["sourceCommit"].startswith(version.rsplit("-", 1)[1]), "Version/source commit")
        expected_additions = {native["path"], "natives/manifest.json", "natives/NOTICE.txt"}
        require(set(names) - set(base.namelist()) == expected_additions,
                f"{loader}: unexpected delivery additions")
    return {"loader": loader, "path": str(delivery_path), "version": version,
            "sha256": sha256(delivery_path.read_bytes()), "native": native,
            "embeddedDependencies": dependencies, "status": "PASS"}


def main():
    results = [verify(loader) for loader in ("fabric", "neoforge")]
    output = ROOT / "build/local-native"
    (output / "26.3-package-manifest.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(
        "".join(f"{item['sha256']}  {Path(item['path']).name}\n" for item in results), encoding="utf-8")
    for item in results:
        print(f"PASS {item['loader']} package integrity: {item['sha256']}")


if __name__ == "__main__":
    main()
