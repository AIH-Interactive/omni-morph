package org.figuramc.figura.backend2.protocol;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** One normalized origin supplies every HTTP/WS path and checks live Debug/epoch permission. */
public final class CloudEndpoint {
    private final URI origin;
    private final BooleanSupplier debug;
    private final BooleanSupplier current;

    private CloudEndpoint(URI origin, BooleanSupplier debug, BooleanSupplier current) {
        this.origin = origin;
        this.debug = debug;
        this.current = current;
    }

    public static CloudEndpoint parse(String address, BooleanSupplier debug) {
        return parse(address, debug, () -> true);
    }

    public static CloudEndpoint parse(String address, BooleanSupplier debug, BooleanSupplier current) {
        try {
            if (address == null || address.length() > 1024 || address.isBlank() || address.contains("\\")) {
                throw new IllegalArgumentException();
            }
            String text = address.trim();
            URI input = URI.create(text.contains("://") ? text : "https://" + text);
            String scheme = input.getScheme().toLowerCase(Locale.ROOT);
            scheme = switch (scheme) {
                case "http", "ws" -> "http";
                case "https", "wss" -> "https";
                default -> throw new IllegalArgumentException();
            };
            if (input.getHost() == null || input.getRawUserInfo() != null || input.getRawQuery() != null
                    || input.getRawFragment() != null || input.getPort() == 0 || input.getPort() > 65535
                    || !input.getRawPath().isEmpty() && !input.getRawPath().equals("/")) {
                throw new IllegalArgumentException();
            }
            String authority = input.getRawAuthority();
            if (authority.endsWith(":")) {
                throw new IllegalArgumentException();
            }
            int port = input.getPort();
            if (port == (scheme.equals("https") ? 443 : 80)) {
                port = -1;
            }
            URI origin = new URI(scheme, null, input.getHost().toLowerCase(Locale.ROOT), port, "", null, null);
            return new CloudEndpoint(origin, debug, current);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("backend.endpoint.invalid");
        }
    }

    public static boolean valid(String address, boolean debug) {
        try {
            parse(address, () -> debug).requireAllowed();
            return true;
        } catch (Exception rejected) {
            return false;
        }
    }

    public URI origin() {
        return origin;
    }

    public boolean plaintext() {
        return origin.getScheme().equals("http");
    }

    public void requireAllowed() throws IOException {
        if (!current.getAsBoolean()) {
            throw new IOException("backend.endpoint.retired");
        }
        if (plaintext() && !debug.getAsBoolean()) {
            throw new IOException("backend.endpoint.debug_required");
        }
    }

    public URI http(String path) throws IOException {
        requireAllowed();
        URI target = origin.resolve(path);
        requireRequest(target);
        return target;
    }

    public URI websocket(String path) throws IOException {
        URI target = http(path);
        return URI.create(target.toString().replaceFirst("^http", "ws"));
    }

    public void requireRequest(URI target) throws IOException {
        requireAllowed();
        if (!origin.getScheme().equals(target.getScheme()) || !origin.getRawAuthority().equals(target.getRawAuthority())
                || target.getRawUserInfo() != null || target.getRawFragment() != null) {
            throw new IOException("backend.endpoint.origin_mismatch");
        }
    }

    public String diagnostic() {
        return origin.toString();
    }
}
