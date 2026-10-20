package top.aihmc.omnimorph.model.cache;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Local cache wrapping only. It does not confer permission or protect against client reverse engineering. */
public final class ModelCacheCodec {
    private static final byte[] HEADER = {0x4f, 0x4d, 0x43, 1};
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path keyFile;

    public ModelCacheCodec(Path cacheRoot) {
        keyFile = cacheRoot.toAbsolutePath().normalize().resolve("model-cache.key");
    }

    private synchronized byte[] key() throws IOException {
        if (Files.isSymbolicLink(keyFile) || Files.isSymbolicLink(keyFile.getParent())) {
            throw new IOException("UNSAFE_CACHE_KEY");
        }
        Files.createDirectories(keyFile.getParent());
        synchronized (ModelCacheCodec.class) {
            if (!Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
                byte[] generated = new byte[32];
                RANDOM.nextBytes(generated);
                Files.write(keyFile, generated, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            if (Files.size(keyFile) != 32) {
                throw new IOException("INVALID_CACHE_KEY");
            }
            return Files.readAllBytes(keyFile);
        }
    }

    /** Caller must close the returned stream before publishing the file. No plaintext temporary file. */
    public OutputStream output(Path target) throws IOException {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(HEADER);
            OutputStream output = Files.newOutputStream(target);
            try {
                output.write(HEADER);
                output.write(nonce);
                return new DeflaterOutputStream(new CipherOutputStream(output, cipher));
            } catch (IOException failure) {
                output.close();
                throw failure;
            }
        } catch (GeneralSecurityException failure) {
            throw new IOException("CACHE_ENCRYPTION_FAILED", failure);
        }
    }

    public void write(Path target, byte[] bytes) throws IOException {
        try (OutputStream output = output(target)) {
            output.write(bytes);
        }
    }

    /** Authenticate the whole container before inflating, with independent disk and decoded budgets. */
    public byte[] read(Path source, long limit) throws IOException {
        if (limit < 0 || limit > Integer.MAX_VALUE - 65536L || Files.isSymbolicLink(source)
                || Files.size(source) > limit + 65536L) {
            throw new IOException("CACHE_BYTE_LIMIT");
        }
        byte[] container;
        try (var input = Files.newInputStream(source)) {
            container = input.readNBytes((int) limit + 65537);
        }
        if (container.length < 32 || container.length > limit + 65536L
                || !Arrays.equals(HEADER, Arrays.copyOf(container, 4))) {
            throw new IOException("UNSUPPORTED_CACHE_CONTAINER");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, Arrays.copyOfRange(container, 4, 16)));
            cipher.updateAAD(HEADER);
            byte[] compressed = cipher.doFinal(container, 16, container.length - 16);
            try (var input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if ((long) output.size() + count > limit) {
                        throw new IOException("CACHE_DECODE_LIMIT");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } catch (GeneralSecurityException failure) {
            throw new IOException("CACHE_AUTHENTICATION_FAILED", failure);
        }
    }
}
