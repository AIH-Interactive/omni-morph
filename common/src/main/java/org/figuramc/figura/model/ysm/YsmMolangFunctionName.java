package org.figuramc.figura.model.ysm;

public record YsmMolangFunctionName(String fileName, String functionName, String eventName, boolean malformed) {
    public static YsmMolangFunctionName parse(String path) {
        String fileName = fileName(path);
        String stem = fileName.endsWith(".molang") ? fileName.substring(0, fileName.length() - ".molang".length()) : fileName;
        int at = stem.indexOf('@');
        if (at < 0)
            return new YsmMolangFunctionName(fileName, stem, "", false);
        String functionName = stem.substring(0, at);
        String eventName = stem.substring(at + 1);
        boolean malformed = eventName.isBlank() || eventName.indexOf('@') >= 0;
        return new YsmMolangFunctionName(fileName, functionName, eventName, malformed);
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank())
            return "";
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
