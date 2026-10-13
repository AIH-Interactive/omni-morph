"""Generate synthetic file-layout vectors without calling any production writer.

Field order follows YSMParserV3.cpp and the fixed Sparkle-Morpher reader.
This is test data tooling, not a model exporter or runtime cache protocol.
"""

import hashlib
import json
import struct
from pathlib import Path


def varint(value):
    result = bytearray()
    while value >= 128:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def text(value):
    data = value.encode("utf-8")
    return varint(len(data)) + data


def blob(data):
    return varint(len(data)) + data


def floats(*values):
    return struct.pack("<" + "f" * len(values), *values)


def geometry():
    result = varint(1) + text("") + varint(1) + varint(1)
    result += floats(0, 0, 1)
    for x, y, u, v in ((0, 0, 0, 0), (2, 0, 1, 0), (2, 3, 1, 1), (0, 3, 0, 1)):
        result += floats(x, y, 0, u, v)
    result += bytes((7, 8, 9)) + text("root") + bytes((1, 2, 3, 4, 5))
    result += floats(1, 2, 3, 0, 0.5, 0)
    result += text("geometry.fixture") + floats(1, 1, 3, 2)
    result += varint(3) + floats(0, 1, 0) + floats(0.25, 0.75)
    return result + bytes((0, 4, 5, 6))


def animation(version):
    result = varint(1) + text("animation.fixture") + floats(40) + varint(1)
    if version > 9:
        result += bytes((3, 4, 1, 2)) + text("v.weight") + varint(5)
    result += varint(1) + text("root")
    result += varint(1) + floats(10) + varint(0)
    for value in (1, 2, 3):
        result += bytes((1,)) + floats(value)
    result += varint(1)
    for value in (4, 5, 6):
        result += bytes((1,)) + floats(value)
    result += bytes((0, 0))
    result += bytes((1, 1)) + text("v.fixture = 1;") + floats(10)
    if version > 9:
        result += varint(1) + text("fixture.sound") + floats(10)
    return result


def controllers(version, read_name=True):
    result = varint(1)
    if version <= 15:
        result += varint(7)
    else:
        if read_name:
            result += text("fixture.controller")
        result += text("controller-hash")
    result += varint(1) + text("controller.fixture") + text("default") + varint(1)
    result += text("default") + varint(1) + text("animation.fixture") + text("1")
    result += varint(1) + text("default") + text("0")
    result += varint(1) + text("v.entry = 1;") + varint(1) + text("v.exit = 1;")
    result += varint(1) + floats(0.25) + varint(1)
    if version > 26:
        result += varint(1) + text("fixture.sound")
    return result


PIXEL = bytes((12, 34, 56, 255))


def textures(version):
    result = varint(1) + text("body")
    if version < 4:
        result += varint(1)
    if version > 15:
        result += text("texture-hash")
    result += blob(PIXEL) + bytes((1, 1))
    if version > 15:
        result += varint(0xFFFFFFFF) + varint(1)
    if version >= 4:
        result += varint(0)
    return result


def properties(version):
    result = text("properties-hash") + varint(1)
    if version <= 15:
        result += varint(0)
    for value in ("Fixture", "Synthetic", "CC0", "Synthetic test data"):
        result += text(value)
    result += bytes((0, 0)) + floats(0.7, 0.8)
    result += varint(1) + text("animation.fixture") + text("Fixture action")
    if version > 9:
        result += bytes((0, 0))
    result += text("body") + text("animation.fixture") + varint(1)
    if version > 4:
        result += varint(1)
    if version >= 15:
        result += bytes((1, 1))
    if version > 15:
        result += varint(1)
        if version >= 32:
            result += varint(1)
        result += text("") + text("") + bytes((0, 0))
    return result


def subentity(version, identifier):
    result = text(identifier) if version <= 26 else b""
    result += varint(2)
    for index in range(2):
        result += text(f"sub-animation-{index}") + animation(version)
    result += controllers(version, read_name=False)
    result += text("sub-texture-hash") + blob(PIXEL) + bytes((1, 1))
    result += varint(0xFFFFFFFF) + bytes((1, 0))
    result += text("sub-geometry-hash") + geometry()
    if version > 26:
        result += varint(2) + text(identifier) + text(identifier + "_alias")
    return result


def payload(version):
    if version < 4:
        result = bytes((0, 1, 1, 1)) + geometry()
        result += bytes((1, 1, 1)) + animation(version) + textures(version)
        result += bytes((1, 1)) + text("geometry-hash")
        result += bytes((1, 1)) + text("animation-hash")
        result += varint(1) + text("body") + text("texture-hash") + text("properties-hash")
    elif version <= 15:
        result = bytes((0, 1, 1, 1)) + geometry()
        result += bytes((1, 1, 1)) + animation(version)
        if version > 9:
            result += controllers(version) + varint(0)
        result += textures(version)
        if version > 9:
            result += bytes((0, 0))
        result += varint(0)
        result += bytes((1, 1)) + text("geometry-hash")
        result += bytes((1, 1)) + text("animation-hash")
        result += varint(1) + text("body") + text("texture-hash") + varint(0)
        result += properties(version)
    else:
        result = varint(1) + text("fixture.sound") + text("sound-hash") + blob(b"synthetic-sound")
        result += varint(1) + text("fixture.txt") + text("function-hash") + blob(b"v.fixture = 1;")
        result += varint(1) + text("en_us") + text("language-hash")
        result += varint(1) + text("fixture.label") + text("Fixture label")
        result += varint(1) + subentity(version, "minecraft:boat")
        if version < 26:
            result += varint(0)
        else:
            result += varint(1) + subentity(version, "minecraft:arrow")
        result += bytes((1, 1, 1)) + text("animation-hash")
        result += animation(version) + controllers(version) + textures(version)
        result += bytes((1, 1)) + text("geometry-hash") + geometry() + properties(version)
    if version >= 9:
        if version > 26:
            result += varint(version)
        result += varint(3) + text("fixture-random") + varint(123456789) + text("fixture-extra")
        if version >= 24:
            result += varint(7)
    return struct.pack("<I", version) + result


def main():
    root = Path(__file__).resolve().parent
    manifest = {}
    for version in (1, 3, 4, 5, 9, 10, 14, 15, 16, 23, 24, 25, 26, 27, 31, 32):
        data = payload(version)
        filename = f"format-{version}.bin"
        (root / filename).write_bytes(data)
        manifest[filename] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(manifest)} fixed synthetic binary vectors.")


if __name__ == "__main__":
    main()
