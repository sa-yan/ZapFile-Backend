package com.sayan.zapfile.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(String id, String email, String displayName, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 50) String displayName) {
    }
}
