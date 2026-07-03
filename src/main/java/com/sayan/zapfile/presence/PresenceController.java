package com.sayan.zapfile.presence;

import com.sayan.zapfile.presence.PresenceService.FriendPresence;
import com.sayan.zapfile.user.User;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/friends")
    public List<FriendPresence> friends(@AuthenticationPrincipal User user) {
        return presenceService.friendPresence(user.getId());
    }
}
