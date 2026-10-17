package top.aihmc.omnimorph.backend2.websocket;

import com.neovisionaries.ws.client.WebSocket;

import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncPackets;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.utils.ColorUtils;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.TextUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class S2CMessageHandler {

    public static final byte
        AUTH = 0,
        PING = 1,
        EVENT = 2,
        TOAST = 3,
        CHAT = 4,
        NOTICE = 5;

    public static void handle(ByteBuffer bytes) {
        handle(bytes, null);
    }

    public static void handle(ByteBuffer bytes, WebSocket source) {
        if (!bytes.hasRemaining())
            return;

        byte b = bytes.get();
        if (NetworkStuff.debug)
            FiguraMod.debug("Got ws message of type: " + b);

        switch (b) {
            case AUTH -> auth();
            case PING -> ping(bytes, source);
            case EVENT -> event(bytes);
            case TOAST -> toast(bytes);
            case CHAT -> chat(bytes);
            case NOTICE -> notice(bytes);
        }
    }

    private static void auth() {
        FiguraMod.LOGGER.info("Connected to " + FiguraMod.MOD_NAME + " ws backend");
        NetworkStuff.backendStatus = 3;
        NetworkStuff.subscribeAll();
        if (Configs.CONNECTION_TOASTS.value)
            FiguraToast.sendToast(FiguraText.of("backend.connected"));
    }

    private static void ping(ByteBuffer bytes, WebSocket source) {
        if (bytes.remaining() < 21) {
            return;
        }
        UUID uuid = new UUID(bytes.getLong(), bytes.getLong());

        int id = bytes.getInt();
        bytes.get(); // sync value is ignored

        boolean ysmPacket = YsmSyncPackets.recognizes(id, bytes);
        Avatar avatar = ysmPacket ? null : AvatarManager.getLoadedAvatar(uuid);
        if (ysmPacket ? bytes.remaining() > YsmSyncPackets.MAX_PACKET_BYTES : avatar == null) {
            return;
        }

        byte[] data = new byte[bytes.remaining()];
        bytes.get(data);

        if (!NetworkStuff.receiveYsmPing(source, uuid, id, data)) {
            avatar.runPing(id, data);
        }
        NetworkStuff.pingsReceived++;
        if (NetworkStuff.lastPing == 0) NetworkStuff.lastPing = FiguraMod.ticks;
    }

    private static void event(ByteBuffer bytes) {
        UUID uuid = new UUID(bytes.getLong(), bytes.getLong());
        AvatarManager.reloadAvatar(uuid);
    }

    private static void toast(ByteBuffer bytes) {
        byte type = bytes.get();
        String[] str = StandardCharsets.UTF_8.decode(bytes).toString().split("\0", 2);
        FiguraToast.sendToast(str[0], str.length > 1 ? str[1] : "", FiguraToast.ToastType.values()[type]);
    }

    private static void chat(ByteBuffer bytes) {
        String message = StandardCharsets.UTF_8.decode(bytes).toString();
        FiguraMod.sendChatMessage(Component.empty().append(Component.literal("-- " + FiguraMod.MOD_NAME + " backend message --\n\n").withStyle(ColorUtils.Colors.SOFT_BLUE.style)).append(TextUtils.tryParseJson(message)));
    }

    private static void notice(ByteBuffer bytes) {
        if (!Configs.CONNECTION_TOASTS.value)
            return;

        byte type = bytes.get();
        switch (type) {
            case 0 -> FiguraToast.sendToast(FiguraText.of("backend.warning"), FiguraText.of("backend.ping_size"), FiguraToast.ToastType.ERROR);
            case 1 -> FiguraToast.sendToast(FiguraText.of("backend.warning"), FiguraText.of("backend.ping_rate"), FiguraToast.ToastType.ERROR);
        }
    }
}
