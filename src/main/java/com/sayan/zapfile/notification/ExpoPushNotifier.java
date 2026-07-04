package com.sayan.zapfile.notification;

import com.sayan.zapfile.device.Device;
import com.sayan.zapfile.transfer.Transfer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Wakes offline devices through Expo's push service. The app stores an Expo
 * push token ("ExponentPushToken[...]") in the device's fcmToken column;
 * Expo forwards to FCM/APNs using the credentials configured in EAS.
 * No Firebase server key is needed here.
 */
@Component
@Primary
public class ExpoPushNotifier implements PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushNotifier.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient restClient = RestClient.create();

    @Override
    public void notifyTransferOffer(Device target, List<Transfer> batch) {
        String token = target.getFcmToken();
        if (token == null || !token.startsWith("ExponentPushToken")) {
            log.debug("Device {} has no Expo push token; skipping push", target.getId());
            return;
        }
        String sender = batch.get(0).getSender().getDisplayName();
        String body = batch.size() == 1
                ? batch.get(0).getFileName()
                : batch.size() + " files";
        Map<String, Object> message = Map.of(
                "to", token,
                "title", sender + " wants to send you files",
                "body", body,
                "channelId", "transfers",
                "priority", "high",
                "data", Map.of("batchId", batch.get(0).getBatchId()));

        // fire and forget: the offer request must not block on Expo
        CompletableFuture.runAsync(() -> {
            try {
                String response = restClient.post()
                        .uri(EXPO_PUSH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(message)
                        .retrieve()
                        .body(String.class);
                log.info("Push sent to device {}: {}", target.getId(), response);
            } catch (Exception e) {
                log.warn("Push to device {} failed: {}", target.getId(), e.getMessage());
            }
        });
    }
}
