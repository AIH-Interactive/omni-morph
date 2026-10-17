package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.resources.Identifier;

import java.util.regex.Pattern;
import java.util.Optional;

/** Adds model transforms to the active vanilla entity source; lighting/fog/material code remains upstream-owned. */
final class GpuShaderProgram {
    static final String UNIFORM = "OmniMorphPose";
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
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
                OmniPart OmniParts[64];
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
        return wrapEntitySource(original, DECLARATIONS, MAIN_BODY);
    }

    static String wrapRigidEntitySource(String original) {
        String declarations = DECLARATIONS.replace("vec4 OmniOptions;", "vec4 OmniOptions;\n    ivec4 OmniRemap[256];");
        return wrapEntitySource(original, declarations, RIGID_MAIN_BODY);
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
        var builder = RenderPipeline.builder().withLocation(location).withVertexShader(location)
                .withFragmentShader(original.getFragmentShader()).withCull(original.isCull())
                .withPolygonMode(original.getPolygonMode()).withColorTargetState(original.getColorTargetState())
                .withDepthStencilState(Optional.ofNullable(original.getDepthStencilState()))
                .withVertexFormat(Format.VALUE, VertexFormat.Mode.TRIANGLES);
        original.getShaderDefines().flags().forEach(builder::withShaderDefine);
        original.getShaderDefines().values().forEach((name, value) -> {
            try {
                builder.withShaderDefine(name, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                builder.withShaderDefine(name, Float.parseFloat(value));
            }
        });
        original.getSamplers().forEach(builder::withSampler);
        original.getUniforms().forEach(uniform -> builder.withUniform(uniform.name(), uniform.type()));
        builder.withUniform(UNIFORM, UniformType.UNIFORM_BUFFER);
        return builder.build();
    }

    static VertexFormat format() {
        return Format.VALUE;
    }

    /** Loaded only during enabled GPU probing. Unavailable attribute slots cause a controlled probe failure. */
    private static final class Format {
        static final VertexFormat VALUE = create();

        private static VertexFormat create() {
            synchronized (VertexFormatElement.class) {
                int[] free = new int[3];
                int found = 0;
                for (int id = 0; id < VertexFormatElement.MAX_COUNT && found < free.length; id++) {
                    if (VertexFormatElement.byId(id) == null) {
                        free[found++] = id;
                    }
                }
                if (found != free.length) {
                    throw new IllegalStateException("Insufficient graphics vertex attribute slots");
                }
                VertexFormatElement normal = VertexFormatElement.register(free[0], 0, VertexFormatElement.Type.FLOAT, false, 3);
                VertexFormatElement weights = VertexFormatElement.register(free[1], 0, VertexFormatElement.Type.FLOAT, false, 4);
                VertexFormatElement joints = VertexFormatElement.register(free[2], 0, VertexFormatElement.Type.INT, false, 4);
                return VertexFormat.builder().add("OmniPosition", VertexFormatElement.POSITION)
                        .add("OmniNormal", normal).add("OmniUV", VertexFormatElement.UV0)
                        .add("OmniWeights", weights).add("OmniJoints", joints).build();
            }
        }
    }
}
