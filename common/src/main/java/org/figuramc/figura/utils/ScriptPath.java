package org.figuramc.figura.utils;

import java.util.ArrayList;
import java.util.List;

/** Avatar module identifiers only; never resolves or opens a host filesystem path. */
public final class ScriptPath {
    private final String path;

    private ScriptPath(String path) {
        this.path = path;
    }

    public static ScriptPath parse(String name) {
        if (name.isEmpty()) {
            return new ScriptPath("/");
        }
        String path = name.startsWith(".") ? name : "/" + name;
        return new ScriptPath(path.replace('\\', '/').replaceAll("[.]([^./])", "/$1"));
    }

    /** Lua source names are already slash-separated, rather than author-facing dot notation. */
    public static ScriptPath sourceDirectory(String source) {
        return new ScriptPath("/" + source).getParent();
    }

    public boolean isAbsolute() {
        return path.startsWith("/") || !path.startsWith(".");
    }

    public ScriptPath resolve(ScriptPath other) {
        return other.isAbsolute() ? other : new ScriptPath(path + "/" + other.path);
    }

    public ScriptPath normalize() {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty() && !parts.get(parts.size() - 1).equals("..")) {
                    parts.remove(parts.size() - 1);
                } else if (!isAbsolute()) {
                    parts.add(part);
                }
            } else {
                parts.add(part);
            }
        }
        return new ScriptPath((isAbsolute() ? "/" : "") + String.join("/", parts));
    }

    public String safeString() {
        String normalized = normalize().path;
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    public ScriptPath getParent() {
        String normalized = normalize().path;
        int separator = normalized.lastIndexOf('/');
        return new ScriptPath(separator <= 0 ? "/" : normalized.substring(0, separator));
    }

    public ScriptPath getFileName() {
        String normalized = normalize().path;
        return new ScriptPath(normalized.substring(normalized.lastIndexOf('/') + 1));
    }

    public boolean startsWith(ScriptPath parent) {
        String name = safeString();
        String prefix = parent.safeString();
        return prefix.isEmpty() || name.equals(prefix) || name.startsWith(prefix + "/");
    }

    public int getNameCount() {
        String name = safeString();
        return name.isEmpty() ? 0 : name.split("/").length;
    }
}
