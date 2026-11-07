"""Resolve local MOD/game dependencies; never launch a Minecraft entrypoint."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parent
MODS = {
    "omp": "omni-morph-1.0.0-alpha.7+26.1.2-fabric-mc.jar",
    "spm": "sparkle-morpher-2.3.2-fa26.1.x.jar",
    "oysm": "openysm-forge-2.6.6.6.jar",
}


def read_settings(path, allow_dialog):
    settings = json.loads(path.read_text(encoding="utf-8-sig")) if path.exists() else {}
    settings.setdefault("librariesDirectory", "D:/Program Files/AIHMC/.minecraft/libraries")
    settings.setdefault("skinPath", str(ROOT / "skin.png"))
    settings.setdefault("skinModel", "auto")
    if settings["skinModel"] not in ("auto", "alex", "steve"):
        raise ValueError("skinModel must be auto, alex, or steve")
    required = ("minecraftDirectory", "fabricVersionDirectory", "forgeVersionDirectory", "librariesDirectory")
    if all(settings.get(key) and Path(settings[key]).is_dir() for key in required):
        return settings
    if not allow_dialog:
        raise ValueError(f"Configure Minecraft paths in {path}")

    import tkinter as tk
    from tkinter import filedialog, messagebox

    window = tk.Tk()
    window.title("预览器 · Minecraft 本机依赖配置")
    variables = {}
    labels = {
        "minecraftDirectory": "Minecraft 根目录（含 libraries）",
        "fabricVersionDirectory": "Fabric 26.1.2 版本目录",
        "forgeVersionDirectory": "Forge 1.20.1 版本目录",
        "librariesDirectory": "Minecraft libraries 依赖目录",
    }
    tk.Label(window, text="仅读取依赖与贴图，不启动游戏。请配置本机安装目录。", padx=16, pady=12).grid(
        row=0, column=0, columnspan=3, sticky="w"
    )
    for row, key in enumerate(required, start=1):
        variables[key] = tk.StringVar(value=settings.get(key, ""))
        tk.Label(window, text=labels[key], padx=12).grid(row=row, column=0, sticky="w")
        tk.Entry(window, textvariable=variables[key], width=64).grid(row=row, column=1, pady=6)

        def browse(name=key):
            selected = filedialog.askdirectory(parent=window, title=labels[name])
            if selected:
                variables[name].set(selected)

        tk.Button(window, text="选择…", command=browse).grid(row=row, column=2, padx=12)

    saved = False

    def save():
        nonlocal saved
        values = {key: variables[key].get().strip() for key in required}
        if not all(Path(value).is_dir() and value for value in values.values()):
            messagebox.showerror("路径无效", "请选择四个有效目录。", parent=window)
            return
        settings.update(values)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(settings, ensure_ascii=False, indent=2), encoding="utf-8")
        saved = True
        window.destroy()

    tk.Button(window, text="保存并继续", command=save, padx=20).grid(row=len(required) + 1, column=1, pady=16)
    window.mainloop()
    if not saved:
        raise RuntimeError("Minecraft configuration cancelled")
    return settings


def enabled(library):
    rules = library.get("rules", [])
    allowed = not rules
    for rule in rules:
        platform = rule.get("os", {})
        if platform.get("name", "windows") != "windows":
            continue
        if platform.get("arch", "x86_64") not in ("x86_64", "amd64"):
            continue
        if rule.get("features"):
            continue
        allowed = rule["action"] == "allow"
    return allowed


def artifact_path(library):
    artifact = library.get("downloads", {}).get("artifact", {})
    if "path" in artifact:
        return artifact["path"]
    group, name, version, *classifier = library["name"].split(":")
    suffix = "-" + classifier[0] if classifier else ""
    return f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}{suffix}.jar"


def nested_jars(data, destination, paths):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.namelist():
            if not entry.endswith(".jar"):
                continue
            binary = archive.read(entry)
            digest = hashlib.sha256(binary).hexdigest()
            target = destination / digest / Path(entry).name
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists():
                target.write_bytes(binary)
            paths.append(target)
            nested_jars(binary, destination, paths)


def prepare(backend, settings):
    version = Path(settings["forgeVersionDirectory" if backend == "oysm" else "fabricVersionDirectory"])
    version_json = version / f"{version.name}.json"
    info = json.loads(version_json.read_text(encoding="utf-8-sig"))
    output = ROOT.parent.parent / "build/preview/jar-host" / backend
    output.mkdir(parents=True, exist_ok=True)
    mod = ROOT / "libs" / MODS[backend]
    paths = [mod]
    nested_jars(mod.read_bytes(), output / "nested", paths)
    libraries = Path(settings["librariesDirectory"])

    if backend == "oysm":
        game_args = info["arguments"]["game"]
        forge_version = game_args[game_args.index("--fml.forgeVersion") + 1]
        forge_root = libraries / f"net/minecraftforge/forge/1.20.1-{forge_version}"
        paths.append(forge_root / f"forge-1.20.1-{forge_version}-client.jar")
        srg = sorted((libraries / "net/minecraft/client").glob("1.20.1-*/*-srg.jar"))
        if len(srg) != 1:
            raise ValueError(f"Expected one installed Minecraft 1.20.1 SRG JAR, found {srg}")
        paths.extend(srg)
        paths.append(forge_root / f"forge-1.20.1-{forge_version}-universal.jar")
    else:
        paths.append(version / f"{version.name}.jar")

    for library in info["libraries"]:
        if enabled(library):
            paths.append(libraries / artifact_path(library))
    paths = list(dict.fromkeys(path.resolve() for path in paths))
    missing = [str(path) for path in paths if not path.is_file()]
    if missing:
        raise FileNotFoundError("Missing installed dependencies:\n" + "\n".join(missing))
    records = [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in paths]
    (output / "dependencies.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    (output / "classpath.txt").write_text(os.pathsep.join(map(str, paths)), encoding="utf-8")
    print(f"Prepared {backend}: {len(paths)} original JAR dependencies; {output}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backend", choices=MODS)
    parser.add_argument("--config", type=Path, default=ROOT / "runtime.local.json")
    parser.add_argument("--no-dialog", action="store_true")
    args = parser.parse_args()
    prepare(args.backend, read_settings(args.config, not args.no_dialog))


if __name__ == "__main__":
    main()
