package com.sayan.zapfile.friend;

import com.sayan.zapfile.common.ApiException;
import com.sayan.zapfile.friend.FriendDtos.FriendRequestRequest;
import com.sayan.zapfile.friend.FriendDtos.FriendResponse;
import com.sayan.zapfile.friend.FriendDtos.PairingCodeResponse;
import com.sayan.zapfile.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public List<FriendResponse> friends(@AuthenticationPrincipal User user) {
        return friendService.listFriends(user);
    }

    @PostMapping("/code")
    @ResponseStatus(HttpStatus.CREATED)
    public PairingCodeResponse generateCode(@AuthenticationPrincipal User user) {
        return friendService.generatePairingCode(user);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendResponse sendRequest(@AuthenticationPrincipal User user,
                                      @Valid @RequestBody FriendRequestRequest request) {
        boolean hasCode = request.code() != null && !request.code().isBlank();
        boolean hasEmail = request.email() != null && !request.email().isBlank();
        if (hasCode == hasEmail) {
            throw ApiException.badRequest("Provide exactly one of 'code' or 'email'");
        }
        return hasCode
                ? friendService.redeemPairingCode(user, request.code())
                : friendService.requestByEmail(user, request.email());
    }

    @GetMapping("/requests")
    public List<FriendResponse> incomingRequests(@AuthenticationPrincipal User user) {
        return friendService.listIncomingRequests(user);
    }

    @PostMapping("/requests/{id}/accept")
    public FriendResponse accept(@AuthenticationPrincipal User user, @PathVariable String id) {
        return friendService.respond(user, id, true);
    }

    @PostMapping("/requests/{id}/decline")
    public FriendResponse decline(@AuthenticationPrincipal User user, @PathVariable String id) {
        return friendService.respond(user, id, false);
    }

    @DeleteMapping("/{friendshipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal User user, @PathVariable String friendshipId) {
        friendService.removeFriend(user, friendshipId);
    }
}
