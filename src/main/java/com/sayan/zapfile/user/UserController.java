package com.sayan.zapfile.user;

import com.sayan.zapfile.user.UserDtos.UpdateProfileRequest;
import com.sayan.zapfile.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }

    @PatchMapping("/me")
    public UserResponse update(@AuthenticationPrincipal User user,
                               @Valid @RequestBody UpdateProfileRequest request) {
        user.setDisplayName(request.displayName());
        return UserResponse.from(userRepository.save(user));
    }
}
