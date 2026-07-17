package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.security.ClamAvFileSecurityScanner;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClamAvFileSecurityScannerTest {

    @Test
    void acceptsCleanFileUsingClamdInstreamProtocol() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> responder = respond(server, "stream: OK\0");
            ClamAvFileSecurityScanner scanner = new ClamAvFileSecurityScanner(
                    "127.0.0.1", server.getLocalPort(), 1000, 1000, 1024);

            scanner.scan(new ByteArrayInputStream("安全内容".getBytes(StandardCharsets.UTF_8)), "safe.txt");
            responder.join();
        }
    }

    @Test
    void rejectsInfectedFile() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> responder = respond(server, "stream: Eicar-Signature FOUND\0");
            ClamAvFileSecurityScanner scanner = new ClamAvFileSecurityScanner(
                    "127.0.0.1", server.getLocalPort(), 1000, 1000, 1024);

            assertThatThrownBy(() -> scanner.scan(
                    new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)), "infected.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("安全扫描未通过");
            responder.join();
        }
    }

    private CompletableFuture<Void> respond(ServerSocket server, String response) {
        return CompletableFuture.runAsync(() -> {
            try (Socket socket = server.accept()) {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                input.readNBytes(10);
                while (true) {
                    int size = input.readInt();
                    if (size == 0) {
                        break;
                    }
                    input.readNBytes(size);
                }
                socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }
}
