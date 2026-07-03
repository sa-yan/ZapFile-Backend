package com.sayan.zapfile.notification;

import com.sayan.zapfile.device.Device;
import com.sayan.zapfile.transfer.Transfer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingPushNotifier implements PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotifier.class);

    @Override
    public void notifyTransferOffer(Device target, List<Transfer> batch) {
        log.info("PUSH (stub): batch {} ({} file(s)) for device {} (fcmToken={})",
                batch.isEmpty() ? "?" : batch.get(0).getBatchId(), batch.size(),
                target.getId(), target.getFcmToken() == null ? "none" : "present");
    }
}
