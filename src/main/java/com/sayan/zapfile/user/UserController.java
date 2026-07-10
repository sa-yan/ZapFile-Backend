package com.sayan.zapfile.user;

import com.sayan.zapfile.user.UserDtos.DeleteAccountRequest;
import com.sayan.zapfile.user.UserDtos.UpdateProfileRequest;
import com.sayan.zapfile.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
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

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal User user,
                              @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(user, request.password());
    }
}
