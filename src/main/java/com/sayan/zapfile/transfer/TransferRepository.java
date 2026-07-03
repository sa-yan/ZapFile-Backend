package com.sayan.zapfile.transfer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    @Query("""
            select t from Transfer t
            where t.sender.id = :userId or t.receiver.id = :userId
            order by t.createdAt desc
            """)
    List<Transfer> findAllInvolvingUser(@Param("userId") String userId);

    List<Transfer> findByBatchId(String batchId);
}
