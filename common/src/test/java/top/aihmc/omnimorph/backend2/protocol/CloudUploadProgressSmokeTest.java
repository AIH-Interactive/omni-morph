package top.aihmc.omnimorph.backend2.protocol;

import top.aihmc.omnimorph.avatar.AvatarLoadProgress;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

public final class CloudUploadProgressSmokeTest {
    public static void main(String[] args) throws Exception {
        byte[] bytes = new byte[200_000];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) index;
        }
        List<Long> sent = new ArrayList<>();
        HttpRequest.BodyPublisher body = CloudHttp.uploadBody(bytes, sent::add);
        require(body.contentLength() == bytes.length, "raw upload retains its exact Content-Length");
        for (int attempt = 0; attempt < 2; attempt++) {
            sent.clear();
            require(Arrays.equals(consume(body), bytes), "body publisher preserves raw bytes on every subscription");
            require(sent.size() > 2 && sent.getFirst() == 0 && sent.getLast() == bytes.length,
                    "progress starts at zero, advances in chunks, and ends at the actual byte count");
            long previous = -1;
            for (long count : sent) {
                require(count >= previous && count <= bytes.length, "byte counts are monotonic within one HTTP attempt");
                previous = count;
            }
        }
        var progress = new AvatarLoadProgress();
        long old = progress.begin(AvatarLoadProgress.Stage.COMPRESSING);
        progress.uploaded(old, 25, 100);
        require(progress.snapshot().stage() == AvatarLoadProgress.Stage.UPLOADING
                && progress.snapshot().fraction() == 0.25, "upload percentage uses real body byte counts");
        progress.stage(old, AvatarLoadProgress.Stage.EQUIPPING);
        require(progress.snapshot().fraction() < 0, "waiting for commit/equipment has no invented percentage");
        long latest = progress.begin(AvatarLoadProgress.Stage.COMPRESSING);
        progress.uploaded(old, 100, 100);
        progress.finish(old);
        require(progress.snapshot().request() == latest, "old upload callbacks cannot hide a newer upload");
        progress.clear();
        progress.uploaded(latest, 100, 100);
        require(progress.snapshot() == null, "cancel/disconnect cannot revive an upload bar");
        System.out.println("PASS upload progress: exact raw body, chunk byte counts, retry reset, stage transitions and stale cancellation");
    }

    private static byte[] consume(HttpRequest.BodyPublisher body) throws Exception {
        var result = new CompletableFuture<byte[]>();
        body.subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                output.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable failure) {
                result.completeExceptionally(failure);
            }

            @Override
            public void onComplete() {
                result.complete(output.toByteArray());
            }
        });
        return result.get(5, TimeUnit.SECONDS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
