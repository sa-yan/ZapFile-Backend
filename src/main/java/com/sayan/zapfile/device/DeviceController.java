package com.sayan.zapfile.device;

import com.sayan.zapfile.common.ApiException;
import com.sayan.zapfile.device.DeviceDtos.DeviceResponse;
import com.sayan.zapfile.device.DeviceDtos.RegisterDeviceRequest;
import com.sayan.zapfile.device.DeviceDtos.UpdateDeviceRequest;
import com.sayan.zapfile.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse register(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody RegisterDeviceRequest request) {
        Device device = new Device(user, request.deviceName().trim(), request.platform(), request.fcmToken());
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @GetMapping
    public List<DeviceResponse> myDevices(@AuthenticationPrincipal User user) {
        return deviceRepository.findByUserId(user.getId()).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public DeviceResponse update(@AuthenticationPrincipal User user,
                                 @PathVariable String id,
                                 @Valid @RequestBody UpdateDeviceRequest request) {
        Device device = deviceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Device not found"));
        if (request.deviceName() != null && !request.deviceName().isBlank()) {
            device.setDeviceName(request.deviceName().trim());
        }
        if (request.fcmToken() != null) {
            device.setFcmToken(request.fcmToken());
        }
        device.touch();
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable String id) {
        Device device = deviceRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Device not found"));
        deviceRepository.delete(device);
    }
}
