# WebP decode fixtures

The small WebP fixtures are copied unchanged from the TwelveMonkeys ImageIO
`twelvemonkeys-3.15.0` tag, under its BSD 3-Clause license (included here):

https://github.com/haraldk/TwelveMonkeys/tree/twelvemonkeys-3.15.0/imageio/imageio-webp/src/test/resources/webp

`YsmBinaryResourcesSmokeTest` verifies the fixed 1×1 and 1×13 image dimensions.
Synthetic RGBA/PNG checks separately assert exact colors and partial alpha.
The optional local corpus tests original encoded textures and subtextures
without copying user assets into this directory.
