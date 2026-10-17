"""Crop supplied artwork and rebuild the client branding textures (requires Pillow)."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageOps


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "common/src/main/resources"
ASSETS = RESOURCES / "assets/omni-morph"


def artwork(name):
    image = Image.open(ROOT / name).convert("RGBA")
    mask = image.getchannel("A").point(lambda alpha: 255 if alpha >= 64 else 0)
    # Remove disconnected extraction noise, retaining the face and decorative stars.
    pixels = bytearray(mask.tobytes())
    width, height = image.size
    components = []
    for start in range(len(pixels)):
        if not pixels[start]:
            continue
        pixels[start] = 0
        pending = [start]
        component = []
        while pending:
            index = pending.pop()
            component.append(index)
            x, y = index % width, index // width
            neighbors = []
            if x:
                neighbors.append(index - 1)
            if x + 1 < width:
                neighbors.append(index + 1)
            if y:
                neighbors.append(index - width)
            if y + 1 < height:
                neighbors.append(index + width)
            for neighbor in neighbors:
                if pixels[neighbor]:
                    pixels[neighbor] = 0
                    pending.append(neighbor)
        components.append(component)
    minimum = max(map(len, components)) * 0.003
    retained = bytearray(width * height)
    for component in components:
        if len(component) >= minimum:
            for index in component:
                retained[index] = 255
    clean_mask = Image.frombytes("L", image.size, bytes(retained))
    # Preserve the original alpha within meaningful connected components.
    image.putalpha(
        Image.composite(image.getchannel("A"), Image.new("L", image.size), clean_mask)
    )
    bounds = clean_mask.getbbox()
    print(f"{name}: {image.size} -> {bounds}")
    return image.crop(bounds)


def fitted(image, size, padding=0):
    resized = ImageOps.contain(
        image, (size - padding * 2,) * 2, Image.Resampling.LANCZOS
    )
    result = Image.new("RGBA", (size, size))
    result.alpha_composite(
        resized, ((size - resized.width) // 2, (size - resized.height) // 2)
    )
    return result


def grayscale(image):
    result = ImageOps.grayscale(image).convert("RGBA")
    result.putalpha(image.getchannel("A"))
    return result


def save(image, relative):
    image.save(RESOURCES / relative, optimize=True)


def main():
    logo = artwork("ingame_logo.png")
    icon = artwork("ingame_icon.png")
    mod_icon = fitted(logo, 256, 4)
    save(mod_icon, "icon.png")
    save(mod_icon, "assets/omni-morph/icon.png")
    save(fitted(logo, 64, 1), "assets/omni-morph/textures/gui/unknown_icon.png")
    save(fitted(logo, 64, 1), "assets/omni-morph/textures/avatar_wizard/icon.png")
    save(fitted(icon, 32), "assets/omni-morph/textures/font/omnimorph.png")

    pause = Image.new("RGBA", (60, 20))
    pause_icon = fitted(logo, 20, 1)
    pause_states = (
        grayscale(pause_icon),
        pause_icon,
        ImageEnhance.Brightness(pause_icon).enhance(1.2),
    )
    for column, variant in enumerate(pause_states):
        pause.alpha_composite(variant, (column * 20, 0))
    save(pause, "assets/omni-morph/textures/gui/icon.png")

    switch = Image.new("RGBA", (60, 40))
    filter_icon = fitted(icon, 20, 1)
    for row in range(2):
        for column in range(3):
            variant = grayscale(filter_icon) if column == 0 else filter_icon
            if column == 2:
                variant = ImageEnhance.Brightness(variant).enhance(1.2)
            switch.alpha_composite(variant, (column * 20, row * 20))
            draw = ImageDraw.Draw(switch)
            x, y = column * 20, row * 20
            if row:
                draw.line(
                    [(x + 12, y + 4), (x + 14, y + 7), (x + 18, y + 1)],
                    fill="#55ff55",
                )
            else:
                draw.line([(x + 13, y + 1), (x + 18, y + 6)], fill="#ff5555")
                draw.line([(x + 18, y + 1), (x + 13, y + 6)], fill="#ff5555")
    save(switch, "assets/omni-morph/textures/gui/show_figura.png")

    for name in ("default", "warning", "error"):
        path = ASSETS / f"textures/gui/toast/{name}.png"
        toast = Image.open(path).convert("RGBA")
        for top in range(0, toast.height, 32):
            background = toast.getpixel((30, top + 16))
            ImageDraw.Draw(toast).rectangle(
                (4, top + 4, 27, top + 27), fill=background
            )
            toast.alpha_composite(fitted(logo, 24), (4, top + 4))
        toast.save(path, optimize=True)


if __name__ == "__main__":
    main()
