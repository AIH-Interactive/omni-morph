package top.aihmc.omnimorph.backend2.library;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Windows owner-only ACLs are mandatory; unsupported filesystems disable persistent login. */
public final class LibraryAdminStore {
    public static final class Saved {
        final String refreshToken;
        final UUID operation;

        Saved(String refreshToken, UUID operation) {
            this.refreshToken = refreshToken;
            this.operation = operation;
        }
    }

    private final Path directory;
    private final Path file;
    private final UserPrincipal owner;
    private final List<AclEntry> acl;

    public LibraryAdminStore(Path root, LibraryClient client) throws Exception {
        directory = root.resolve("model-library-admin");
        owner = root.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
        String scope = client.origin() + "\n" + client.session().instance() + "\n" + client.session().player();
        String key = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(scope.getBytes(StandardCharsets.UTF_8)));
        file = directory.resolve(key + ".json");
    }

    private FileAttribute<List<AclEntry>> attribute() {
        return new FileAttribute<>() {
            public String name() {
                return "acl:acl";
            }

            public List<AclEntry> value() {
                return acl;
            }
        };
    }

    private void verify(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("INSECURE_ADMIN_STORE");
        }
        var view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null || !view.getOwner().equals(owner)) {
            throw new IOException("INSECURE_ADMIN_STORE");
        }
        for (var entry : view.getAcl()) {
            if (entry.type() == AclEntryType.ALLOW && !entry.principal().equals(owner)) {
                throw new IOException("INSECURE_ADMIN_STORE");
            }
        }
        if (view.getAcl().stream().noneMatch(entry -> entry.type() == AclEntryType.ALLOW
                && entry.principal().equals(owner) && entry.permissions().contains(AclEntryPermission.WRITE_DATA))) {
            throw new IOException("INSECURE_ADMIN_STORE");
        }
    }

    public boolean available() {
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(directory, attribute());
            }
            verify(directory);
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    public Saved read() throws IOException {
        if (!available() || !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        verify(file);
        if (Files.size(file) > 4096) {
            throw new IOException("INVALID_ADMIN_STORE");
        }
        try {
            Saved saved = new Gson().fromJson(Files.readString(file), Saved.class);
            if (saved == null || saved.operation == null || saved.refreshToken == null
                    || !saved.refreshToken.matches("[A-Za-z0-9_-]{32,256}")) {
                throw new IllegalArgumentException();
            }
            return saved;
        } catch (RuntimeException malformed) {
            throw new IOException("INVALID_ADMIN_STORE");
        }
    }

    public void write(String token) throws IOException {
        if (!available()) {
            throw new IOException("ADMIN_REMEMBER_UNAVAILABLE");
        }
        Path temporary = Files.createTempFile(directory, "rotate-", ".part", attribute());
        try {
            verify(temporary);
            Files.writeString(temporary, new Gson().toJson(new Saved(token, UUID.randomUUID())));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            verify(file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void clear() throws IOException {
        if (available()) {
            Files.deleteIfExists(file);
        }
    }
}
