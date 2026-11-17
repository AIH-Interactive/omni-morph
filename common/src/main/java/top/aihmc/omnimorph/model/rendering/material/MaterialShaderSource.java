package top.aihmc.omnimorph.model.rendering.material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Supplies explicit stage interfaces required by Renderpearl's SPIR-V compiler. */
final class MaterialShaderSource {
    private static final Pattern INTERFACE = Pattern.compile(
            "(?m)^(\\h*)(layout\\s*\\([^;{}\\n]*\\)\\s*)?"
                    + "((?:(?:flat|smooth|noperspective|centroid|sample|invariant|precise)\\s+)*)"
                    + "(in|out)\\s+(\\w+)\\s+(\\w+)(\\s*\\[\\s*(\\d+)\\s*\\])?\\s*;");
    private static final Pattern LOCATION = Pattern.compile("location\\s*=\\s*(\\d+)");
    private static final Pattern DYNAMIC_TRANSFORMS = Pattern.compile(
            "(?s)layout\\s*\\(\\s*std140\\s*\\)\\s*uniform\\s+DynamicTransforms\\s*\\{.*?}\\s*;");
    private static final String NATIVE_TRANSFORMS = """
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
            };
            """;

    record Pair(String vertex, String fragment) {
    }

    private MaterialShaderSource() {
    }

    static Pair prepare(String vertex, String fragment) {
        var attributes = new LinkedHashMap<String, Integer>();
        var varyings = new LinkedHashMap<String, Integer>();
        var outputs = new LinkedHashMap<String, Integer>();
        collect(vertex, "in", attributes);
        collect(vertex, "out", varyings);
        collect(fragment, "in", varyings);
        collect(fragment, "out", outputs);
        return new Pair(qualify(vertex, attributes, varyings), qualify(fragment, varyings, outputs));
    }

    private static void collect(String source, String direction, Map<String, Integer> locations) {
        var declarations = INTERFACE.matcher(source);
        while (declarations.find()) {
            if (!declarations.group(4).equals(direction)) {
                continue;
            }
            var explicit = LOCATION.matcher(declarations.group(2) == null ? "" : declarations.group(2));
            if (explicit.find()) {
                locations.put(declarations.group(6), Integer.parseInt(explicit.group(1)));
            }
        }
        declarations.reset();
        while (declarations.find()) {
            if (!declarations.group(4).equals(direction) || locations.containsKey(declarations.group(6))) {
                continue;
            }
            int location = 0;
            while (locations.containsValue(location)) {
                location++;
            }
            locations.put(declarations.group(6), location);
        }
    }

    private static String qualify(String source, Map<String, Integer> inputs, Map<String, Integer> outputs) {
        var declarations = INTERFACE.matcher(source);
        source = declarations.replaceAll(match -> {
            if (match.group(2) != null && LOCATION.matcher(match.group(2)).find()) {
                return java.util.regex.Matcher.quoteReplacement(match.group());
            }
            var locations = match.group(4).equals("in") ? inputs : outputs;
            String declaration = match.group(1) + "layout(location = " + locations.get(match.group(6)) + ") "
                    + match.group().substring(match.group(1).length());
            return java.util.regex.Matcher.quoteReplacement(declaration);
        });
        source = source.replace("#moj_import", "#include");
        source = DYNAMIC_TRANSFORMS.matcher(source).replaceAll(NATIVE_TRANSFORMS);
        return source.replaceFirst("(?m)^(#version[^\\n]*\\n)",
                "$1#extension GL_ARB_separate_shader_objects : require\n");
    }
}
