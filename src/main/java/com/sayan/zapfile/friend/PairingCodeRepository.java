package com.sayan.zapfile.friend;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PairingCodeRepository extends JpaRepository<PairingCode, String> {

    Optional<PairingCode> findByCodeAndUsedFalse(String code);

    @Modifying
    @Query("delete from PairingCode p where p.owner.id = :userId")
    void deleteAllByOwner(@Param("userId") String userId);
}
