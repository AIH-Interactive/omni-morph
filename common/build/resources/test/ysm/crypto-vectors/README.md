# YSM fixed crypto vectors

These are synthetic, redistributable fixtures, not decrypted user models.
They were generated once outside production code with JDK AES/zlib, a separate
packing implementation, and the original Sparkle-Morpher CityHash/XChaCha/MT
primitives at commit `fbee58d985cb730d19dd65ea796d5f8ac6f003dc`.
The reference attribution is shipped in `licenses/YSM-Sparkle-Morpher.txt`.
No production model writer was added.

Crypto 1 and 2 contain the fixed geometry string, a one-pixel PNG, and
`functions/event.molang` with `return 0.2;`, all asserted as exact bytes by
`YsmCryptoSmokeTest`. Crypto 3 wraps a deterministic 20,013-byte payload with
113 padding bytes and spans multiple cipher segments. The expected payload
starts with a format DWORD, but its remainder is arbitrary test bytes: it is
**not** a valid binary model or evidence of format-reader coverage.

The compressed fixture uses Airlift's separate Java Zstd compressor before
applying the reference YSM block-header transformation. Its frame has a real
compressed block, and its expected output matches the raw-frame vector.
The malformed padding/Zstd fixtures have valid outer integrity hashes, so they
exercise inner validation rather than failing at the outer checksum.

| File | SHA-256 |
| --- | --- |
| cityhash64-vectors.txt | d688d8f19ccb69cbea3daf5796977a35bd86244343ef934eb35dc8882c63c2cf |
| crypto1.ysm | b3fca14b434072ecca6f48a6382e20e2f56df71c1a3ce4e2fab3608c6e963b30 |
| crypto2.ysm | b039be7f28684255438d1857c386044da7591dde6c8a63d0a682ec4b623ef060 |
| crypto3-bad-padding.ysm | f6b7d6352f3e75de4af26250296d553ea89055859fe109ae3c27496e428b59b1 |
| crypto3-bad-zstd.ysm | 56d0412e8501c8de9b17f712596fd3137c11a4bde611851b86f9231e4f9b80f8 |
| crypto3-compressed.ysm | 963f4f119ce7f1725513254b16edabd87ea53f1f7cd976c81931f23018ff10fa |
| crypto3.ysm | d3e8f6674b266951b664b01ff3644996f9214fa8444957fc85c841e910fb8ee5 |
| v3-compressed-frame.bin | c67452a6db72b0afc4288648f081d9cfbdc6bf62d3beed04573f24bc3466367b |
| v3-expected.bin | b0a08594058c6dafad2dfb9c60c03089f6051dd3388467ac430a6424d9722184 |
| v3-raw-frame.bin | e2cb3f4e7e731c9b9b8ca2b8afa9cb2ca927e940bc8fb07ad5c94073fcadfbae |
