package com.sayan.zapfile.transfer;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    /**
     * History query. JOIN FETCH pulls the four associations that
     * TransferResponse.from reads in one round trip instead of four
     * lazy loads per row; the Pageable caps how far back we go.
     */
    @Query("""
            select t from Transfer t
            join fetch t.sender
            join fetch t.senderDevice
            join fetch t.receiver
            left join fetch t.receiverDevice
            where t.sender.id = :userId or t.receiver.id = :userId
            order by t.createdAt desc
            """)
    List<Transfer> findAllInvolvingUser(@Param("userId") String userId, Pageable pageable);

    List<Transfer> findByBatchId(String batchId);

    @Modifying
    @Query("delete from Transfer t where t.sender.id = :userId or t.receiver.id = :userId")
    void deleteAllInvolvingUser(@Param("userId") String userId);
}
