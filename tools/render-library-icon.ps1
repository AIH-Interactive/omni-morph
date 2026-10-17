$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $PSScriptRoot 'art/model-library.svg'
$targetPath = Join-Path $projectRoot 'common/src/main/resources/assets/omni-morph/textures/gui/library.png'
[xml]$source = Get-Content -LiteralPath $sourcePath -Raw
$bitmap = [System.Drawing.Bitmap]::new([int]$source.svg.width, [int]$source.svg.height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.Clear([System.Drawing.Color]::Transparent)
    # This repository-owned icon contains only axis-aligned SVG rectangles.
    foreach ($rectangle in $source.svg.rect) {
        $color = [System.Drawing.ColorTranslator]::FromHtml([string]$rectangle.fill)
        $brush = [System.Drawing.SolidBrush]::new($color)
        try {
            $graphics.FillRectangle($brush, [int]$rectangle.x, [int]$rectangle.y,
                [int]$rectangle.width, [int]$rectangle.height)
        } finally {
            $brush.Dispose()
        }
    }
    $bitmap.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
