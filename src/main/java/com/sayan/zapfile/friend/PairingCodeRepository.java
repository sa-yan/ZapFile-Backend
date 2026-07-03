package com.sayan.zapfile.friend;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PairingCodeRepository extends JpaRepository<PairingCode, String> {

    Optional<PairingCode> findByCodeAndUsedFalse(String code);
}
