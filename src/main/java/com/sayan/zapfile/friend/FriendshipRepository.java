package com.sayan.zapfile.friend;

import com.sayan.zapfile.friend.Friendship.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    @Query("""
            select f from Friendship f
            where (f.requester.id = :userId or f.addressee.id = :userId)
              and f.status = :status
            """)
    List<Friendship> findAllByUserAndStatus(@Param("userId") String userId, @Param("status") Status status);

    List<Friendship> findByAddresseeIdAndStatus(String addresseeId, Status status);

    @Query("""
            select f from Friendship f
            where (f.requester.id = :a and f.addressee.id = :b)
               or (f.requester.id = :b and f.addressee.id = :a)
            """)
    Optional<Friendship> findBetween(@Param("a") String userA, @Param("b") String userB);

    @Modifying
    @Query("delete from Friendship f where f.requester.id = :userId or f.addressee.id = :userId")
    void deleteAllInvolvingUser(@Param("userId") String userId);
}
