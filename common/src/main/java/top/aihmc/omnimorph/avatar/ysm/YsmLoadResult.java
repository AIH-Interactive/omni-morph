package top.aihmc.omnimorph.avatar.ysm;

import java.util.List;

public record YsmLoadResult(Status status, YsmSourceIdentity identity, List<String> diagnostics) {
    public enum Status {
        PUBLISHED, CANCELLED, FAILED
    }

    public YsmLoadResult {
        diagnostics = List.copyOf(diagnostics);
    }
}
