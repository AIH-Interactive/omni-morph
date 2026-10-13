# Synthetic binary file vectors

These vectors contain only authored synthetic test data. They contain no user model,
reference asset, encrypted key, or production model exporter. They follow the repository
license. The displayed CC0 metadata inside the fixture is a parser test value.

`generate.py` is a standalone Python byte packer. It does not import the Java reader,
mapper or serializer. Its field order was checked against these local reference sources:

- `refs/YSMParser/YSMParser/parsers/v3/YSMParserV3.cpp`, SHA-256
  `B11CDDECC340D065A47AF60CFB5B95D83F5D947314F1FBD03F9AC601EDE86B50`.
- `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/resource/YSMBinaryDeserializer.java`,
  SHA-256 `A9CCDAF09BDE7439BD00A6BC362C0A2955B407AD4786D0BA9E641E823D4A28EB`.

Committed `format-*.bin` files are fixed inputs; `manifest.json` freezes their byte
lengths and SHA-256 digests. Tests read them directly and do not regenerate them.
To intentionally regenerate them, run `python generate.py` using an available Python
runtime and review all byte/digest changes.

Versions: 1, 3, 4, 5, 9, 10, 14, 15, 16, 23, 24, 25, 26, 27, 31, 32.
Each has a nonempty baked quad, bone transform, RGBA texture, pre/post animation
keyframe and timeline. Versions above 9 also have a nonempty controller and sound
event. Modern versions include sound/function/language data, a vehicle with two
animation files, and a projectile from version 26. Version 27 adds match identifier
aliases and controller sound effects; version 32 adds merge_multiline_expr.
Footer versions 9/24/27 cover the presence and field-layout changes.

`YsmBinaryFormatSmokeTest` checks independent literal expectations, mapped resource
byte equivalence, malformed/truncated inputs, and the explicit no-DWORD layout made
by dropping exactly four format bytes. This does not claim interoperability with any
runtime cache/footer protocol. No encrypted file writer is provided.
