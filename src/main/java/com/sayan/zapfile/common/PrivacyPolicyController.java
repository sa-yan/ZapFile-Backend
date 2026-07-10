package com.sayan.zapfile.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated privacy policy page — linked from the Google Play Store
 * listing, so it must be publicly reachable (permitted in SecurityConfig).
 */
@RestController
public class PrivacyPolicyController {

    private final String html;

    public PrivacyPolicyController() throws IOException {
        try (InputStream in = new ClassPathResource("pages/privacy-policy.html").getInputStream()) {
            this.html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping(value = "/privacy-policy", produces = MediaType.TEXT_HTML_VALUE)
    public String privacyPolicy() {
        return html;
    }
}
