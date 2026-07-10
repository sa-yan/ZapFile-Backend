package com.sayan.zapfile.device;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, String> {

    List<Device> findByUserId(String userId);

    Optional<Device> findByIdAndUserId(String id, String userId);

    @Modifying
    @Query("delete from Device d where d.user.id = :userId")
    void deleteAllByUser(@Param("userId") String userId);
}
