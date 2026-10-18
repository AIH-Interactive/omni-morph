# Regenerate wardrobe and library action sprites from their pixel SVG sources.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'common/src/main/resources/assets/omni-morph/textures/gui'
$icons = @{
    'wardrobe_visibility' = @('wardrobe-hidden.svg', 'wardrobe-visible.svg')
    'wardrobe_appeal' = @('wardrobe-appeal.svg')
    'library_grants' = @('library-grants.svg')
    'library_authorization' = @('library-auth-free.svg', 'library-auth-required.svg')
    'library_use' = @('library-use.svg')
    'library_favorite' = @('library-favorite-off.svg', 'library-favorite-on.svg')
    'library_visibility' = @('wardrobe-hidden.svg', 'wardrobe-visible.svg')
    'library_delete' = @('library-delete.svg')
    'library_restore' = @('library-restore.svg')
    'library_edit' = @('library-edit.svg')
    'library_unblock' = @('library-unblock.svg')
}
$colors = @('#777777', '#ffffff', '#55ffff')
foreach ($icon in $icons.GetEnumerator()) {
    $bitmap = [System.Drawing.Bitmap]::new(72, 24 * $icon.Value.Count)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        for ($row = 0; $row -lt $icon.Value.Count; $row++) {
            [xml]$source = Get-Content -LiteralPath (Join-Path $PSScriptRoot "art/$($icon.Value[$row])") -Raw
            for ($column = 0; $column -lt $colors.Count; $column++) {
                $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($colors[$column]))
                try {
                    # These pixel-art SVGs contain only axis-aligned rectangles.
                    foreach ($rectangle in $source.svg.rect) {
                        $graphics.FillRectangle($brush, 24 * $column + [int]$rectangle.x,
                            24 * $row + [int]$rectangle.y, [int]$rectangle.width, [int]$rectangle.height)
                    }
                } finally {
                    $brush.Dispose()
                }
            }
        }
        $bitmap.Save((Join-Path $outputDirectory "$($icon.Key).png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}
