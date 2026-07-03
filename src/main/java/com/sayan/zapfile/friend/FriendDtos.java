package com.sayan.zapfile.friend;

import com.sayan.zapfile.friend.Friendship.Status;
import com.sayan.zapfile.user.User;
import jakarta.validation.constraints.Email;
import java.time.Instant;

public final class FriendDtos {

    private FriendDtos() {
    }

    /** Exactly one of {@code code} or {@code email} must be provided. */
    public record FriendRequestRequest(String code, @Email String email) {
    }

    public record PairingCodeResponse(String code, Instant expiresAt) {
    }

    public record FriendResponse(String friendshipId, String userId, String displayName, Status status,
                                 Instant since) {
        public static FriendResponse from(Friendship friendship, String viewerUserId) {
            User other = friendship.otherThan(viewerUserId);
            return new FriendResponse(friendship.getId(), other.getId(), other.getDisplayName(),
                    friendship.getStatus(), friendship.getCreatedAt());
        }
    }
}
