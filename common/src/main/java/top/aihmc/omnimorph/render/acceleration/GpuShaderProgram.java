package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

import java.util.regex.Pattern;
import java.util.Optional;

/** Adds model transforms to the active vanilla entity source; lighting/fog/material code remains upstream-owned. */
final class GpuShaderProgram {
    static final String UNIFORM = "OmniMorphPose";
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern VERSION = Pattern.compile("^[ \\t]*#version[ \\t]+(\\d+)[^\\n]*\\n", Pattern.MULTILINE);
    static final String STORAGE_DATA = "OmniMorphData";
    static final String STORAGE_OFFSETS = "OmniMorphOffsets";
    private static final String UNIFORM_BLOCK = """
            layout(std140) uniform OmniMorphPose {
                OmniPart OmniParts[OMNI_PARTS];
                vec4 OmniTint;
                ivec4 OmniOverlayLight;
                vec4 OmniOptions;
            };
            """;
    private static final String STORAGE_BLOCK = """
            uniform usamplerBuffer OmniMorphData;
            layout(std140) uniform OmniMorphOffsets {
                ivec4 OmniBase;
            };
            vec4 omniFloats(int texel) {
                return uintBitsToFloat(texelFetch(OmniMorphData, texel));
            }
            OmniPart omniPart(int part) {
                int base = OmniBase.x + 3 + part * 10;
                return OmniPart(mat4(omniFloats(base), omniFloats(base + 1),
                    omniFloats(base + 2), omniFloats(base + 3)),
                    omniFloats(base + 4), omniFloats(base + 5), omniFloats(base + 6),
                    omniFloats(base + 7), omniFloats(base + 8), omniFloats(base + 9));
            }
            #define OmniTint omniFloats(OmniBase.x)
            #define OmniOverlayLight ivec4(texelFetch(OmniMorphData, OmniBase.x + 1))
            #define OmniOptions omniFloats(OmniBase.x + 2)
            """;
    private static final String STORAGE_REMAP_BLOCK = """
            int omniRemap(int batch) {
                return int(texelFetch(OmniMorphData, OmniBase.y + batch / 4)[batch % 4]);
            }
            """;
    private static final String MODEL_BATCH_BLOCK = """
            struct OmniBatch {
                vec4 tint;
                ivec4 overlayLight;
                vec4 options;
            };
            OmniBatch omniBatchInfo(int batch) {
                int base = OmniBase.z + batch * 3;
                return OmniBatch(omniFloats(base), ivec4(texelFetch(OmniMorphData, base + 1)),
                    omniFloats(base + 2));
            }
            """;
    // Each vertex names its batch in the high 16 bits of OmniJoints.x; the batch index is also its root part.
    private static final String[][] MODEL_REPLACEMENTS = {
            {"""
                vec3 point = OmniPosition;
            """, """
                int omniBatch = OmniJoints.x >> 16;
                OmniBatch omniInfo = omniBatchInfo(omniBatch);
                ivec4 omniJoints = ivec4(OmniJoints.x & 0xFFFF, OmniJoints.y, OmniJoints.z, OmniJoints.w);
                vec3 point = OmniPosition;
            """},
            {"OmniParts[OmniJoints[influence]]", "OmniParts[omniJoints[influence]]"},
            {"OmniParts[0]", "OmniParts[omniBatch]"},
            {"OmniOptions", "omniInfo.options"},
            {"OmniTint", "omniInfo.tint"},
            {"OmniOverlayLight", "omniInfo.overlayLight"}
    };
    private static final String UNIFORM_REMAP_READ = """
                int packedRemap = OmniRemap[globalPart / 16][(globalPart / 4) % 4];
                int part = (packedRemap >> ((globalPart % 4) * 8)) & 255;
            """;
    // Storage groups have no 64-part palette: each batch maps straight to its global part index.
    private static final String STORAGE_REMAP_READ = """
                int part = omniRemap(globalPart);
            """;
    private static final String DECLARATIONS = """
            in vec3 OmniPosition;
            in vec3 OmniNormal;
            in vec2 OmniUV;
            in vec4 OmniWeights;
            in ivec4 OmniJoints;
            struct OmniPart {
                mat4 position;
                vec4 normal0;
                vec4 normal1;
                vec4 normal2;
                vec4 uv0;
                vec4 uv1;
                vec4 uv2;
            };
            layout(std140) uniform OmniMorphPose {
                OmniPart OmniParts[OMNI_PARTS];
                vec4 OmniTint;
                ivec4 OmniOverlayLight;
                vec4 OmniOptions;
            };
            """;
    private static final String MAIN_BODY = """

            void main() {
                vec3 point = OmniPosition;
                if (OmniOptions.z != 0.0) {
                    vec3 blended = vec3(0.0);
                    float total = 0.0;
                    for (int influence = 0; influence < 4; ++influence) {
                        float weight = OmniWeights[influence];
                        if (weight > 0.0) {
                            blended += (OmniParts[OmniJoints[influence]].position * vec4(point, 1.0)).xyz * weight;
                            total += weight;
                        }
                    }
                    if (total > 0.0) {
                        point = blended * (1.0 / total);
                    }
                }
                vec4 transformed = OmniParts[0].position * vec4(point, 1.0);
                if (OmniOptions.w != 0.0) {
                    transformed.w = 1.0;
                }
                if (OmniOptions.x != 0.0) {
                    transformed += normalize(transformed) * OmniOptions.x;
                }
                Position = transformed.xyz;
                mat3 normalMatrix = mat3(OmniParts[0].normal0.xyz, OmniParts[0].normal1.xyz, OmniParts[0].normal2.xyz);
                vec3 direction = normalMatrix * OmniNormal;
                if (OmniOptions.y != 0.0) {
                    direction = normalize(direction);
                }
                // The original entity VertexConsumer stores normalized signed-byte normals.
                Normal = vec3(ivec3(clamp(direction, -1.0, 1.0) * 127.0)) / 127.0;
                mat3 uvMatrix = mat3(OmniParts[0].uv0.xyz, OmniParts[0].uv1.xyz, OmniParts[0].uv2.xyz);
                UV0 = (uvMatrix * vec3(OmniUV, 1.0)).xy;
                Color = OmniTint;
                UV1 = OmniOverlayLight.xy;
                UV2 = OmniOverlayLight.zw;
                omnimorph_original_main();
            }
            """;

    private static final String RIGID_MAIN_BODY = """

            void main() {
                int globalPart = OmniJoints.x;
                int packedRemap = OmniRemap[globalPart / 16][(globalPart / 4) % 4];
                int part = (packedRemap >> ((globalPart % 4) * 8)) & 255;
                vec4 transformed = OmniParts[part].position * vec4(OmniPosition, 1.0);
                if (OmniParts[part].normal1.w != 0.0) {
                    transformed.w = 1.0;
                }
                float offset = OmniParts[part].normal2.w;
                if (offset != 0.0) {
                    transformed += normalize(transformed) * offset;
                }
                Position = transformed.xyz;
                mat3 normalMatrix = mat3(OmniParts[part].normal0.xyz,
                    OmniParts[part].normal1.xyz, OmniParts[part].normal2.xyz);
                vec3 direction = normalMatrix * OmniNormal;
                if (OmniParts[part].normal0.w != 0.0) {
                    direction = normalize(direction);
                }
                Normal = vec3(ivec3(clamp(direction, -1.0, 1.0) * 127.0)) / 127.0;
                mat3 uvMatrix = mat3(OmniParts[part].uv0.xyz,
                    OmniParts[part].uv1.xyz, OmniParts[part].uv2.xyz);
                UV0 = (uvMatrix * vec3(OmniUV, 1.0)).xy;
                Color = OmniTint;
                UV1 = OmniOverlayLight.xy;
                UV2 = OmniOverlayLight.zw;
                omnimorph_original_main();
            }
            """;

    private GpuShaderProgram() {
    }

    static boolean supports(RenderPipeline pipeline) {
        return pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/entity"))
                && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/entity"));
    }

    static String wrapEntitySource(String original) {
        return wrapEntitySource(original, GpuBatchData.MAX_PARTS);
    }

    /** {@code parts} sizes the pose block; it must be one of {@link GpuBatchData#PART_TIERS}. */
    static String wrapEntitySource(String original, int parts) {
        return wrapEntitySource(original, declarations(parts), MAIN_BODY);
    }

    static String wrapRigidEntitySource(String original) {
        return wrapRigidEntitySource(original, GpuBatchData.MAX_PARTS);
    }

    static String wrapRigidEntitySource(String original, int parts) {
        String declarations = declarations(parts)
                .replace("vec4 OmniOptions;", "vec4 OmniOptions;\n    ivec4 OmniRemap[256];");
        return wrapEntitySource(original, declarations, RIGID_MAIN_BODY);
    }

    /** Native texel data keeps the existing header/part bytes and reads them through explicit arena offsets. */
    static String wrapStorageEntitySource(String original) {
        return wrapEntitySource(enableStorage(original), storageDeclarations(false), storageBody(MAIN_BODY));
    }

    static String wrapStorageRigidEntitySource(String original) {
        String body = RIGID_MAIN_BODY.replace(UNIFORM_REMAP_READ, STORAGE_REMAP_READ);
        if (body.equals(RIGID_MAIN_BODY)) {
            throw new IllegalStateException("Rigid remap read changed; storage variant must be updated");
        }
        return wrapEntitySource(enableStorage(original), storageDeclarations(true), storageBody(body));
    }

    /**
     * R4-3 model variant: {@link #MAIN_BODY} with the root part, options, tint and overlay/light taken per batch.
     * It is derived mechanically so the skinning, normal and UV math stays identical to ordinary batches.
     */
    static String wrapStorageModelEntitySource(String original) {
        String body = MAIN_BODY;
        for (String[] replacement : MODEL_REPLACEMENTS) {
            String replaced = body.replace(replacement[0], replacement[1]);
            if (replaced.equals(body)) {
                throw new IllegalStateException("Ordinary pose body changed; model variant must be updated: " + replacement[0]);
            }
            body = replaced;
        }
        return wrapEntitySource(enableStorage(original), storageDeclarations(false) + MODEL_BATCH_BLOCK, storageBody(body));
    }

    private static String storageBody(String body) {
        String result = body.replace("OmniParts[OmniJoints[influence]]", "omniPart(OmniJoints[influence])")
                .replace("OmniParts[omniJoints[influence]]", "omniPart(omniJoints[influence])")
                .replace("OmniParts[0]", "omniPart(0)")
                .replace("OmniParts[part]", "omniPart(part)")
                .replace("OmniParts[omniBatch]", "omniPart(omniBatch)");
        if (result.contains("OmniParts[")) {
            throw new IllegalStateException("Unconverted pose array access in native texel shader");
        }
        return result;
    }

    private static String storageDeclarations(boolean grouped) {
        String declarations = DECLARATIONS.replace(UNIFORM_BLOCK, STORAGE_BLOCK);
        if (declarations.equals(DECLARATIONS)) {
            throw new IllegalStateException("Pose uniform block changed; storage variant must be updated");
        }
        return grouped ? declarations + STORAGE_REMAP_BLOCK : declarations;
    }

    /** Texel buffers are part of the native shader contract; no backend-specific extension is injected. */
    static String enableStorage(String source) {
        var version = VERSION.matcher(source);
        if (!version.find()) {
            throw new IllegalArgumentException("Entity vertex shader has no #version directive");
        }
        return source;
    }

    private static String declarations(int parts) {
        if (GpuBatchData.tierFor(parts) != parts) {
            throw new IllegalArgumentException("Unsupported pose block variant: " + parts);
        }
        return DECLARATIONS.replace("OMNI_PARTS", Integer.toString(parts));
    }

    private static String wrapEntitySource(String original, String declarations, String body) {
        if (original == null) {
            throw new IllegalArgumentException("Missing active entity vertex shader");
        }
        String source = original;
        String[][] inputs = {{"vec3", "Position"}, {"vec3", "Normal"}, {"vec2", "UV0"},
                {"vec4", "Color"}, {"ivec2", "UV1"}, {"ivec2", "UV2"}};
        for (String[] input : inputs) {
            Pattern declaration = Pattern.compile("\\bin\\s+" + input[0] + "\\s+" + input[1] + "\\s*;");
            var match = declaration.matcher(source);
            if (match.results().count() != 1) {
                throw new IllegalArgumentException("Entity vertex input contract changed: " + input[1]);
            }
            source = declaration.matcher(source).replaceFirst(input[0] + " " + input[1] + ";");
        }
        var main = MAIN.matcher(source);
        if (main.results().count() != 1) {
            throw new IllegalArgumentException("Entity vertex entry point contract changed");
        }
        source = MAIN.matcher(source).replaceFirst(declarations + "\nvoid omnimorph_original_main()");
        return source + body;
    }

    static RenderPipeline pipeline(RenderPipeline original, Identifier location) {
        return pipeline(original, location, true);
    }

    /** Storage variants must not declare the pose uniform: Blaze3D skips draws whose declared uniforms are unbound. */
    static RenderPipeline pipeline(RenderPipeline original, Identifier location, boolean uniformPose) {
        var builder = RenderPipeline.builder().withLocation(location).withVertexShader(location)
                .withFragmentShader(original.getFragmentShader()).withCull(original.isCull())
                .withPolygonMode(original.getPolygonMode()).withColorTargetState(original.getColorTargetState())
                .withDepthStencilState(Optional.ofNullable(original.getDepthStencilState()))
                .withVertexBinding(0, Format.VALUE)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        original.getShaderDefines().flags().forEach(builder::withShaderDefine);
        original.getShaderDefines().values().forEach((name, value) -> {
            try {
                builder.withShaderDefine(name, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                builder.withShaderDefine(name, Float.parseFloat(value));
            }
        });
        original.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        if (uniformPose) {
            builder.withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform(UNIFORM, UniformType.UNIFORM_BUFFER).build());
        } else {
            builder.withBindGroupLayout(storageLayout());
        }
        return builder.build();
    }

    static BindGroupLayout storageLayout() {
        return BindGroupLayout.builder()
                .withUniform(STORAGE_DATA, UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
                .withUniform(STORAGE_OFFSETS, UniformType.UNIFORM_BUFFER).build();
    }

    static VertexFormat format() {
        return Format.VALUE;
    }

    /** Vertex formats now describe their own named attributes without a global attribute registry. */
    private static final class Format {
        static final VertexFormat VALUE = create();

        private static VertexFormat create() {
            return VertexFormat.builder(0)
                    .addAttribute("OmniPosition", GpuFormat.RGB32_FLOAT)
                    .addAttribute("OmniNormal", GpuFormat.RGB32_FLOAT)
                    .addAttribute("OmniUV", GpuFormat.RG32_FLOAT)
                    .addAttribute("OmniWeights", GpuFormat.RGBA32_FLOAT)
                    .addAttribute("OmniJoints", GpuFormat.RGBA32_SINT)
                    .build();
        }
    }
}
