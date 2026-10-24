package org.figuramc.figura.backend2;

import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;
import org.figuramc.figura.config.Configs;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

public class AuthHandler {
    public static void auth(boolean reAuth) {
        long epoch = NetworkStuff.connectionEpoch();
        CloudEndpoint endpoint = HttpAPI.endpoint(epoch);
        CloudProtocol.Kind kind = Configs.CLOUD_PROTOCOL.kind();
        NetworkStuff.async(() -> {
            if (epoch != NetworkStuff.connectionEpoch() || !reAuth && NetworkStuff.isConnected()) {
                return;
            }
            CloudHttp transport = new CloudHttp(NetworkStuff.client, endpoint);
            if (!NetworkStuff.registerAuthentication(epoch, transport)) {
                return;
            }
            try {
                endpoint.requireAllowed();
                FiguraMod.LOGGER.info("Authenticating with {} server", FiguraMod.MOD_NAME);
                NetworkStuff.backendStatus = 2;
                Minecraft minecraft = Minecraft.getInstance();
                User user = minecraft.getUser();
                if (kind == CloudProtocol.Kind.PROTOBUF) {
                    ProtobufCloudProtocol.probe(endpoint, transport);
                }
                endpoint.requireAllowed();
                String username = URLEncoder.encode(user.getName(), StandardCharsets.UTF_8);
                String serverID = request(transport, HttpRequest.newBuilder(
                        endpoint.http("/api//auth/id?username=" + username)).build());
                endpoint.requireAllowed();
                try {
                    minecraft.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverID);
                } catch (InvalidCredentialsException exception) {
                    FiguraMod.LOGGER.info("Mojang authentication failed; using backend offline mode");
                } catch (AuthenticationUnavailableException exception) {
                    FiguraMod.LOGGER.info("Mojang authentication unavailable; using backend offline mode");
                }
                endpoint.requireAllowed();
                String challenge = URLEncoder.encode(serverID, StandardCharsets.UTF_8);
                String token = request(transport, HttpRequest.newBuilder(
                        endpoint.http("/api//auth/verify?id=" + challenge)).build());
                endpoint.requireAllowed();
                NetworkStuff.authSuccess(token, epoch, endpoint);
            } catch (AuthenticationUnavailableException exception) {
                NetworkStuff.authFail(Component.translatable("disconnect.loginFailedInfo.serversUnavailable").getString(), epoch);
            } catch (InvalidCredentialsException exception) {
                NetworkStuff.authFail(Component.translatable("disconnect.loginFailedInfo.invalidSession").getString(), epoch);
            } catch (InsufficientPrivilegesException exception) {
                NetworkStuff.authFail(Component.translatable("disconnect.loginFailedInfo.insufficientPrivileges").getString(), epoch);
            } catch (UserBannedException exception) {
                NetworkStuff.authFail(Component.translatable("disconnect.loginFailedInfo.userBanned").getString(), epoch);
            } catch (Exception exception) {
                String key = exception instanceof ProtobufCloudProtocol.Unsupported ? "backend.protobuf.unsupported"
                        : exception.getMessage() != null && exception.getMessage().startsWith("backend.endpoint.")
                                ? exception.getMessage() : "backend.auth.failed";
                NetworkStuff.authFail(key, epoch);
            } finally {
                NetworkStuff.finishAuthentication(transport);
            }
        });
    }

    private static String request(CloudHttp transport, HttpRequest request) throws Exception {
        var response = transport.send(request);
        try (var body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("backend.auth.failed");
            }
            String result = new String(CloudHttp.readBounded(body, 4096), StandardCharsets.UTF_8);
            if (result.isBlank()) {
                throw new IOException("backend.auth.failed");
            }
            return result;
        } finally {
            transport.release(response.body());
        }
    }
}
