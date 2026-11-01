# M0 dependency and API lock

Target: Minecraft 1.20.1, Mojang official mappings, Java 17 (major 61).
Build JVM: existing JDK 21. Gradle: repository wrapper 9.4.1.
Plugins: Architectury 3.4.164, Loom 1.14.476, Shadow 9.0.0-beta12.
MixinExtras common/Forge: 0.3.6. Fabric Loader 0.16.14: common compile annotation support only.
MCP: de.oceanlabs.mcp:mcp_config:1.20.1-20230612.114412.

Forge API baseline: net.minecraftforge:forge:1.20.1-47.1.3.
Historical NeoForge verification target: net.neoforged:forge:1.20.1-47.1.106.
Support claims are limited to these exact loaders. Historical NeoForge uses FancyModLoader 47.2.2.

Implementation strategy: retain Architectury/Loom, one shared forge/ implementation;
select loader coordinate by a Gradle property, generate separate formal mapped build variants
and independently validate them. Do not claim one shared final JAR unless M4 proves it.
No Fabric output and no modern NeoForge API compilation in the target build.

The toolchain-probe is configuration-only evidence, not application compilation or loader execution.
Run from repository root with JDK21:

    .\gradlew.bat -p tools/porting/toolchain-probe help --console=plain
    .\gradlew.bat -p tools/porting/toolchain-probe configureLaunch -PloaderCoordinate=net.neoforged:forge:1.20.1-47.1.106 --console=plain

Use the project's mandatory PowerShell Tee-Object and immediate exit-code checks around every build.
Never run runClient/runServer as part of this probe.

## Optional integrations

Exact downloaded metadata and hashes: optional-dependencies.json; base class major scan:
optional-class-inspection.json. A maximum major61 scan proves bytecode compatibility only,
not runtime linkage or successful mod operation.

GeckoLib Forge4.8.4 requires Forge47.1+, both locked loaders satisfy the descriptor range.
ImmediatelyFast Forge1.5.5+1.20.4 explicitly lists MC1.20.1 and requires Forge46+.
Iris1.7.6+1.20.1 and LambDynamicLights4.4.0+1.20.1 are Fabric/Quilt only.
Do not introduce Oculus or make these Fabric-only mods required on Forge.
Keep no-mod fallback semantics: vanilla/no-shader and built-in particle lights.
The current Lamb behavior API is newer than the target API and needs a target binding,
without adding a Fabric output or disabling core particle illumination.

## Confirmed migration gaps

Both patched BufferSource classes agree: builder/fixedBuffers/lastState/startedBuffers,
private getBuilderRaw(RenderType), endBatch(RenderType). No vertices field or two-argument endBatch.
Both userdev patches extend VertexConsumer with IForgeVertexConsumer and alpha putBulkData.
Vanilla MCP confirms vertex/color/uv/overlayCoords/uv2/normal/endVertex, defaultColor/unsetDefaultColor.
GPU queue, retained consumers and sorted Java fallback must implement this lifecycle.

minecraft-dev AW validation: 15 entries, four target errors: ItemStackComponentizationFix class,
fixItemStack method, ItemStackData.tag, Player.currentImpulseContextResetGraceTime.
The validation is source-based and does not prove production AT or Mixin application.

Additional source/compiler audit areas: Java21 List accessors (preserve Deque/Pair APIs),
DataComponents to NBT, DeltaTracker to partial ticks, ByteBufferBuilder/MeshData to BufferBuilder,
resource packs/sound/entities/input, GUI and particle signatures, platform @ExpectPlatform bridges,
chat completion locals, loader ID omni_morph versus resource namespace omni-morph,
JNI/ImageIO service merging, Forge SRG refmaps and AW-to-AT conversion.

M1 must compile all selected main/test sources and run existing non-graphical regressions;
M2 validates render and Mixin contracts; M3 validates complete functional smoke tasks;
M4 validates formal output, native packaging and remote CI. No source exclusion or relaxed test assertions.
