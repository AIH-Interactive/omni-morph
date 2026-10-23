// Authoring-time SVG export only; Sharp is never a mod/runtime dependency.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');

async function main() {
    const root = path.resolve(__dirname, '..');
    const output = path.join(root, 'common/src/main/resources/assets/omni-morph/cosmetic_templates/textures');
    await fs.mkdir(output, { recursive: true });
    for (const [name, width, height] of [
        ['sakura_petal', 32, 32],
        ['twinkle_star_sheet', 128, 64],
        ['cosmetic_base', 32, 32],
    ]) {
        const source = path.join(root, 'art/cosmetic_templates', `${name}.svg`);
        const target = path.join(output, `${name}.png`);
        await sharp(source).resize(width, height).ensureAlpha().png().toFile(target);
        const image = await sharp(target).metadata();
        if (image.width !== width || image.height !== height || image.channels !== 4) {
            throw new Error(`Invalid RGBA export: ${name}`);
        }
        console.log(`Exported ${name}.png: ${width}x${height} RGBA`);
    }
    const icon = path.join(root, 'common/src/main/resources/assets/omni-morph/textures/gui/cosmetic_settings.png');
    await sharp(path.join(root, 'art/cosmetic_templates/cosmetic_settings.svg'))
        .resize(72, 24).ensureAlpha().png().toFile(icon);
    const iconMetadata = await sharp(icon).metadata();
    if (iconMetadata.width !== 72 || iconMetadata.height !== 24 || iconMetadata.channels !== 4) {
        throw new Error('Invalid three-state cosmetic settings icon');
    }
    console.log('Exported cosmetic_settings.png: 72x24 RGBA, three 24x24 states');
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
