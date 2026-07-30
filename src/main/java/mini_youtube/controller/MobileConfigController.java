package mini_youtube.controller;

import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/mobileconfig")
public class MobileConfigController {

    @GetMapping
    public ResponseEntity<byte[]> getMobileConfig(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
            if (host == null || host.isBlank()) {
                host = request.getServerName() + ":" + request.getServerPort();
            }
        }

        String baseUrl = scheme + "://" + host;

        String iconBase64 = "";
        try {
            ClassPathResource imgFile = new ClassPathResource("static/apple-touch-icon.png");
            if (imgFile.exists()) {
                try (InputStream is = imgFile.getInputStream()) {
                    byte[] bytes = is.readAllBytes();
                    iconBase64 = Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                "<plist version=\"1.0\">\n" +
                "<dict>\n" +
                "    <key>PayloadContent</key>\n" +
                "    <array>\n" +
                "        <dict>\n" +
                "            <key>FullScreen</key>\n" +
                "            <true/>\n" +
                (iconBase64.isEmpty() ? "" : "            <key>Icon</key>\n            <data>\n" + iconBase64 + "\n            </data>\n") +
                "            <key>IsRemovable</key>\n" +
                "            <true/>\n" +
                "            <key>Label</key>\n" +
                "            <string>MiniTube</string>\n" +
                "            <key>PayloadDescription</key>\n" +
                "            <string>MiniTube 影音庫獨立 WebApp 入口</string>\n" +
                "            <key>PayloadDisplayName</key>\n" +
                "            <string>MiniTube WebClip</string>\n" +
                "            <key>PayloadIdentifier</key>\n" +
                "            <string>com.minitube.webclip</string>\n" +
                "            <key>PayloadType</key>\n" +
                "            <string>com.apple.webClip.managed</string>\n" +
                "            <key>PayloadUUID</key>\n" +
                "            <string>" + UUID.randomUUID().toString() + "</string>\n" +
                "            <key>PayloadVersion</key>\n" +
                "            <integer>1</integer>\n" +
                "            <key>Precomposed</key>\n" +
                "            <true/>\n" +
                "            <key>URL</key>\n" +
                "            <string>" + baseUrl + "</string>\n" +
                "        </dict>\n" +
                "    </array>\n" +
                "    <key>PayloadDisplayName</key>\n" +
                "    <string>MiniTube App 描述檔</string>\n" +
                "    <key>PayloadIdentifier</key>\n" +
                "    <string>com.minitube.profile</string>\n" +
                "    <key>PayloadRemovalDisallowed</key>\n" +
                "    <false/>\n" +
                "    <key>PayloadType</key>\n" +
                "    <string>Configuration</string>\n" +
                "    <key>PayloadUUID</key>\n" +
                "    <string>" + UUID.randomUUID().toString() + "</string>\n" +
                "    <key>PayloadVersion</key>\n" +
                "    <integer>1</integer>\n" +
                "</dict>\n" +
                "</plist>";

        byte[] body = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"MiniTube.mobileconfig\"")
                .contentType(MediaType.parseMediaType("application/x-apple-aspen-config"))
                .body(body);
    }
}
