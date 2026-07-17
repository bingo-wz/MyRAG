package com.wangzhi.knowledgebase.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(name = "app.security.file-scan.provider", havingValue = "clamav")
public class ClamAvFileSecurityScanner implements FileSecurityScanner {

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final long maxBytes;

    public ClamAvFileSecurityScanner(
            @Value("${app.security.file-scan.clamav.host:clamav}") String host,
            @Value("${app.security.file-scan.clamav.port:3310}") int port,
            @Value("${app.security.file-scan.clamav.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.security.file-scan.clamav.read-timeout-ms:30000}") int readTimeoutMs,
            @Value("${app.security.file-scan.max-bytes:20971520}") long maxBytes) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxBytes = maxBytes;
    }

    @Override
    public void scan(InputStream input, String originalName) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM_COMMAND);
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("文件超过病毒扫描大小限制：" + originalName);
                }
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
            output.writeInt(0);
            output.flush();
            String response = readResponse(socket.getInputStream());
            if (response.endsWith("OK")) {
                return;
            }
            if (response.contains("FOUND")) {
                throw new IllegalArgumentException("文件安全扫描未通过：" + originalName);
            }
            throw new IOException("ClamAV 返回异常：" + response);
        }
    }

    private String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int value; output.size() < 2048 && (value = input.read()) >= 0;) {
            if (value == 0 || value == '\n') {
                break;
            }
            output.write(value);
        }
        return output.toString(StandardCharsets.UTF_8).trim();
    }

    @Override
    public String provider() {
        return "clamav";
    }

    @Override
    public boolean available() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            // 健康检查应快速失败，避免 readiness 被完整扫描超时长期阻塞。
            socket.setSoTimeout(Math.min(readTimeoutMs, 2000));
            socket.getOutputStream().write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return "PONG".equals(readResponse(socket.getInputStream()));
        } catch (IOException exception) {
            return false;
        }
    }
}
