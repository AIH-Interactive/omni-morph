# Validate bundled interface translations without downloading runtime assets.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$languageDirectory = Join-Path $projectRoot 'common/src/main/resources/assets/omni-morph/lang'
$languages = @{}
foreach ($locale in @('en_us', 'zh_cn')) {
    $json = [System.IO.File]::ReadAllText((Join-Path $languageDirectory "$locale.json"))
    $document = [System.Text.Json.JsonDocument]::Parse($json)
    try {
        $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $document.RootElement.EnumerateObject()) {
            if (-not $keys.Add($property.Name)) {
                throw "Duplicate translation in ${locale}: $($property.Name)"
            }
        }
    } finally {
        $document.Dispose()
    }
    $languages[$locale] = ConvertFrom-Json -InputObject $json -AsHashtable
}

$required = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($key in $languages.en_us.Keys) {
    # Lua API reference prose is separate from interface fields and tooltips.
    if (-not $key.StartsWith('omni-morph.docs.')) {
        [void]$required.Add($key)
    }
}
$sourceDirectory = Join-Path $projectRoot 'common/src/main/java'
foreach ($file in Get-ChildItem -LiteralPath $sourceDirectory -Recurse -Filter '*.java') {
    $source = [System.IO.File]::ReadAllText($file.FullName)
    foreach ($match in [regex]::Matches($source, 'FiguraText\.of\("([a-z0-9_.]+)"\s*[,)]')) {
        $key = 'omni-morph.' + $match.Groups[1].Value
        if (-not $key.StartsWith('omni-morph.docs.')) {
            [void]$required.Add($key)
        }
    }
}
foreach ($value in @('active', 'blocked', 'deleted', 'figura', 'ysm_plain', 'ysm_encrypted',
        'full_model', 'decorative_parts')) {
    [void]$required.Add("omni-morph.gui.library.value.$value")
}
foreach ($value in @('toggle', 'slider', 'enum', 'color', 'text', 'number', 'keybind', 'button',
        'label', 'separator')) {
    [void]$required.Add("omni-morph.gui.controls.type.$value")
}
$configs = [System.IO.File]::ReadAllText((Join-Path $sourceDirectory 'top/aihmc/omnimorph/config/Configs.java'))
foreach ($match in [regex]::Matches($configs, 'new ConfigType\.([A-Za-z]+)\("([a-z0-9_]+)"')) {
    $key = 'omni-morph.config.' + $match.Groups[2].Value
    [void]$required.Add($key)
    if ($match.Groups[1].Value -ne 'Category') {
        # Some settings deliberately compose several tooltip lines instead.
        if (-not $languages.en_us.ContainsKey($key + '.tooltip.1')) {
            [void]$required.Add($key + '.tooltip')
        }
    }
}

foreach ($key in $required) {
    foreach ($locale in @('en_us', 'zh_cn')) {
        if (-not $languages[$locale].ContainsKey($key)) {
            throw "Missing translation in ${locale}: $key"
        }
    }
    $english = @([regex]::Matches($languages.en_us[$key], '(?<!%)%(?:\d+\$)?[sdif]') |
        ForEach-Object Value)
    $chinese = @([regex]::Matches($languages.zh_cn[$key], '(?<!%)%(?:\d+\$)?[sdif]') |
        ForEach-Object Value)
    if (($english -join ',') -ne ($chinese -join ',')) {
        throw "Translation format placeholders differ: $key"
    }
}
Write-Output "PASS UI translations: $($required.Count) keys, unique JSON keys, both locales and matching placeholders"
exit 0
