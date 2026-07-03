package com.sayan.zapfile.device;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, String> {

    List<Device> findByUserId(String userId);

    Optional<Device> findByIdAndUserId(String id, String userId);
}
