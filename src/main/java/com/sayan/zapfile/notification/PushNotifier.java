package com.sayan.zapfile.notification;

import com.sayan.zapfile.device.Device;
import com.sayan.zapfile.transfer.Transfer;
import java.util.List;

/**
 * Wakes a device that has no open WebSocket connection. The dev
 * implementation just logs; swap in an FCM-backed implementation
 * (firebase-admin + a service account) without touching callers.
 */
public interface PushNotifier {

    void notifyTransferOffer(Device target, List<Transfer> batch);
}
