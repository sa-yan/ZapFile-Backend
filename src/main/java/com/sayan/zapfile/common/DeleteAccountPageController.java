package com.sayan.zapfile.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated account-deletion instructions page — Google Play requires
 * a public web URL for this, linked from the store listing (permitted in
 * SecurityConfig).
 */
@RestController
public class DeleteAccountPageController {

    private final String html;

    public DeleteAccountPageController() throws IOException {
        try (InputStream in = new ClassPathResource("pages/delete-account.html").getInputStream()) {
            this.html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping(value = "/delete-account", produces = MediaType.TEXT_HTML_VALUE)
    public String deleteAccount() {
        return html;
    }
}
