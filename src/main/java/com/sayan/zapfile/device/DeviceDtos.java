package com.sayan.zapfile.device;

import com.sayan.zapfile.device.Device.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record RegisterDeviceRequest(
            @NotBlank @Size(max = 100) String deviceName,
            @NotNull Platform platform,
            @Size(max = 512) String fcmToken) {
    }

    public record UpdateDeviceRequest(
            @Size(max = 100) String deviceName,
            @Size(max = 512) String fcmToken) {
    }

    public record DeviceResponse(
            String id, String deviceName, Platform platform, Instant createdAt, Instant lastSeenAt) {
        public static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.getDeviceName(), device.getPlatform(),
                    device.getCreatedAt(), device.getLastSeenAt());
        }
    }
}
