package com.sayan.zapfile.presence;

import com.sayan.zapfile.friend.FriendshipRepository;
import com.sayan.zapfile.friend.Friendship.Status;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresenceService {

    private final WsSessionRegistry registry;
    private final FriendshipRepository friendshipRepository;

    public PresenceService(WsSessionRegistry registry, FriendshipRepository friendshipRepository) {
        this.registry = registry;
        this.friendshipRepository = friendshipRepository;
    }

    public record FriendPresence(String userId, String displayName, boolean online) {
    }

    @Transactional(readOnly = true)
    public List<FriendPresence> friendPresence(String userId) {
        return friendshipRepository.findAllByUserAndStatus(userId, Status.ACCEPTED).stream()
                .map(f -> f.otherThan(userId))
                .map(friend -> new FriendPresence(friend.getId(), friend.getDisplayName(),
                        registry.isUserOnline(friend.getId())))
                .toList();
    }

    /** Tells all online devices of the user's friends that this user went on/offline. */
    @Transactional(readOnly = true)
    public void broadcastPresence(String userId, boolean online) {
        Map<String, Object> data = Map.of("userId", userId, "online", online);
        friendshipRepository.findAllByUserAndStatus(userId, Status.ACCEPTED).stream()
                .map(f -> f.otherThan(userId).getId())
                .forEach(friendId -> registry.sendToUser(friendId, "presence.update", data));
    }
}
