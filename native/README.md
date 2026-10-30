# OmniMorph native acceleration — ABI 2

H2 implements Windows x86_64 numerical transforms. The Java path remains the default;
this directory does not implement GPU drawing. Sources are independently written;
see `NOTICE.txt` for provenance.

## Data contract

- All numeric storage uses native byte order, IEEE 754 float32 and fixed-width int32 arguments.
- A vertex contains nine floats: position xyzw, normal xyz, UV uv (36 bytes).
- The matrix buffer contains 40 floats (160 bytes), column-major: position mat4 (16),
  normal mat3 with each column padded to four floats (12), then similarly padded UV mat3 (12).
- Position uses its explicit w; UV uses implicit homogeneous z=1. Normal uses its own matrix.
- Flags: bit 0 normalizes output normals; bit 1 forces output w=1. Other bits are rejected.
- Kernel: -1 automatic, 0 scalar, 1 SSE2, 2 AVX2. Explicit requests still require actual CPU/OS support.
- Status: 0 success, 1 invalid range/flags/alias/read-only output, 2 unavailable kernel,
  3 non-finite input/matrix/result, 4 allocation failure.
- Buffers must be direct; offsets/strides are bytes. Strides must be at least 36 bytes.
  Count is 0..65536. Zero count accepts empty vertex buffers and requires a valid matrix buffer.
  Arbitrary alignment, padded strides, and odd tails are supported. Capacity is checked before access.
- Input is read-only. Output must not overlap the addressed input or matrix ranges, including
  independently sliced direct buffers. Failed calls do not modify output, even on numeric overflow.
- Degenerate finite matrices are accepted when the result is finite. A normal normalization
  producing NaN/Inf rejects the batch and preserves Java fallback semantics.
- ABI 2 adds `pack`: converts 0..163840 native-order doubles at a byte offset to float32 with
  round-to-nearest (bit-identical to a Java `(float)` cast) using scalar/SSE2/AVX2/NEON kernels.
  Same direct-buffer, capacity, alias and status rules; any non-finite result rejects without writing.
- No JNI references, pointers or graphics objects survive the call. Temporary native output is bounded
  by 65536 * 36 bytes; only a complete validated result is published.

Figura's existing full influence accumulation/normalization remains in Java during packing.
Vanilla PoseStack's private normal normalization policy is likewise preserved in Java; its already
transformed normals use the native identity normal matrix. Actual positions and UV transforms,
and Figura normal transforms, execute in the selected native kernel. Script-query matrices retain
their original Java precision. No weights are truncated.

## Safe dispatch and build

JNI entry points, CPUID/OSXSAVE/XGETBV detection, validation and scalar/SSE2 stay at baseline x64 ISA.
Only `avx2.cpp` receives `/arch:AVX2`. AVX2 requires SSE2, AVX, AVX2, OSXSAVE and XMM/YMM OS state;
FMA is reported independently and these kernels do not use it. `/fp:strict` forbids silent contraction.
The MSVC runtime is linked statically; the script records actual `dumpbin /DEPENDENTS` results.

Close Minecraft first, select JDK 25, then run from the repository root:

```powershell
& ./tools/build-native-windows.ps1 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath build.log -Tail 40
if ($buildExitCode -ne 0) { exit $buildExitCode }
& ./gradlew.bat :fabric:build packageLocalNative nativeSmokeTest --console=plain 2>&1 | Tee-Object -FilePath build.log -Append
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath build.log -Tail 40
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

The native script imports an explicit MSVC x64 developer environment and executes configure/build/CTest
in the foreground with separate step logs. Set `JAVA_HOME` with `usejdk 25`; if auto-discovery fails,
pass `-VisualStudioPath` with the installed Visual Studio root.

`packageLocalNative` copies the existing Fabric JAR using the ZIP API, preserving its entries and
nested dependencies. It rejects duplicate/native entries and hash mismatches, then adds only Windows
plus metadata/NOTICE under `natives/`. Output is `build/local-native/*-windows-local.jar` and never
overwrites the ordinary client symlink target. Plain `:fabric:build` requires no C++ toolchain.

The loader chooses the JVM process architecture, validates controlled resource paths and metadata,
extracts atomically into version/ABI/hash cache directories, verifies SHA-256 again, and loads an
absolute path. It does not search the current directory, model packages or network. `JNI_OnLoad`
checks Java's ABI field; Java separately checks the native handshake. Construction/class initialization
does not load native code. JDK native-access rejection is handled by the activation layer (H3).

`nativeSmokeTest` uses `-Xcheck:jni`, loads the DLL from the packaged JAR, compares forced available
kernels to Java, tests rejected buffers and output sentinels, and replays actual Figura and local YSM
renderers with a Java differential check before their emitted output is consumed. The local corpus
is not distributed in the JAR. Timing CSV separates complete Java/native packing-and-output cost from
prepacked JNI cost; it is a CPU microbenchmark, not game/GPU performance evidence.
