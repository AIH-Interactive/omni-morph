package top.aihmc.omnimorph.backend2.protocol;

public final class CloudReconnectSmokeTest {
    public static void main(String[] args) {
        var retry = new CloudReconnect();
        retry.failed();
        require(!advance(retry, 6000), "no retry before a connection was requested");
        retry.start();
        require(!advance(retry, 6000), "an in-progress connection has no duplicate attempt");
        for (int delay : new int[]{100, 200, 400, 800, 1200, 1200}) {
            retry.failed();
            require(!advance(retry, delay - 1), "retry waits for its backoff");
            require(retry.tick(false), "retry becomes due exactly once");
            require(!advance(retry, 2000), "no concurrent second attempt while awaiting its result");
        }
        retry.connected();
        retry.failed();
        for (int tick = 0; tick < 200; tick++) {
            require(!retry.tick(true), "authentication/connection in progress suspends retries");
        }
        require(!advance(retry, 99) && retry.tick(false), "successful connection resets backoff");
        retry.failed();
        retry.stop();
        require(!advance(retry, 6000), "manual disconnect cancels a scheduled retry");
        retry.start();
        retry.failed();
        require(!advance(retry, 99) && retry.tick(false), "new endpoint starts a fresh retry sequence");
        System.out.println("PASS cloud reconnect: bounded backoff, one in-flight attempt, recovery reset and cancellation");
    }

    private static boolean advance(CloudReconnect retry, int ticks) {
        boolean due = false;
        for (int tick = 0; tick < ticks; tick++) {
            due |= retry.tick(false);
        }
        return due;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
