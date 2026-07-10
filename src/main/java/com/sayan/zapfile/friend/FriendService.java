package com.sayan.zapfile.friend;

import com.sayan.zapfile.common.ApiException;
import com.sayan.zapfile.common.RateLimiter;
import com.sayan.zapfile.friend.FriendDtos.FriendResponse;
import com.sayan.zapfile.friend.FriendDtos.PairingCodeResponse;
import com.sayan.zapfile.friend.Friendship.Status;
import com.sayan.zapfile.user.User;
import com.sayan.zapfile.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 6;
    private static final int REDEEM_ATTEMPTS_PER_MINUTE = 10;

    private final FriendshipRepository friendshipRepository;
    private final PairingCodeRepository pairingCodeRepository;
    private final UserRepository userRepository;
    private final Duration codeExpiry;
    private final SecureRandom random = new SecureRandom();

    /** Per-user cap on redemption attempts so short codes can't be brute-forced. */
    private final RateLimiter redeemLimiter =
            new RateLimiter(REDEEM_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));

    public FriendService(FriendshipRepository friendshipRepository,
                         PairingCodeRepository pairingCodeRepository,
                         UserRepository userRepository,
                         @Value("${zapfile.pairing-code.expiry-minutes}") long codeExpiryMinutes) {
        this.friendshipRepository = friendshipRepository;
        this.pairingCodeRepository = pairingCodeRepository;
        this.userRepository = userRepository;
        this.codeExpiry = Duration.ofMinutes(codeExpiryMinutes);
    }

    public List<FriendResponse> listFriends(User user) {
        return friendshipRepository.findAllByUserAndStatus(user.getId(), Status.ACCEPTED).stream()
                .map(f -> FriendResponse.from(f, user.getId()))
                .toList();
    }

    public List<FriendResponse> listIncomingRequests(User user) {
        return friendshipRepository.findByAddresseeIdAndStatus(user.getId(), Status.PENDING).stream()
                .map(f -> FriendResponse.from(f, user.getId()))
                .toList();
    }

    @Transactional
    public PairingCodeResponse generatePairingCode(User user) {
        String code;
        do {
            code = randomCode();
        } while (pairingCodeRepository.findByCodeAndUsedFalse(code).isPresent());
        PairingCode pairingCode = new PairingCode(code, user, Instant.now().plus(codeExpiry));
        pairingCodeRepository.save(pairingCode);
        return new PairingCodeResponse(pairingCode.getCode(), pairingCode.getExpiresAt());
    }

    /** Redeeming a valid code creates an immediately-accepted friendship. */
    @Transactional
    public FriendResponse redeemPairingCode(User user, String code) {
        if (!redeemLimiter.tryAcquire(user.getId())) {
            throw ApiException.tooManyRequests("Too many pairing attempts; try again in a minute");
        }
        PairingCode pairingCode = pairingCodeRepository.findByCodeAndUsedFalse(code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Invalid pairing code"));
        if (pairingCode.isExpired()) {
            throw ApiException.badRequest("This pairing code has expired");
        }
        User owner = pairingCode.getOwner();
        if (owner.getId().equals(user.getId())) {
            throw ApiException.badRequest("You cannot redeem your own pairing code");
        }
        pairingCode.markUsed();
        Friendship friendship = upsertFriendship(owner, user, Status.ACCEPTED);
        return FriendResponse.from(friendship, user.getId());
    }

    /** Requesting by email creates a PENDING request the other user must accept. */
    @Transactional
    public FriendResponse requestByEmail(User user, String email) {
        User target = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("No user with that email"));
        if (target.getId().equals(user.getId())) {
            throw ApiException.badRequest("You cannot add yourself");
        }
        Friendship friendship = upsertFriendship(user, target, Status.PENDING);
        return FriendResponse.from(friendship, user.getId());
    }

    @Transactional
    public FriendResponse respond(User user, String friendshipId, boolean accept) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        if (!friendship.getAddressee().getId().equals(user.getId())) {
            throw ApiException.forbidden("Only the recipient can respond to this request");
        }
        if (friendship.getStatus() != Status.PENDING) {
            throw ApiException.conflict("This request has already been handled");
        }
        if (accept) {
            friendship.setStatus(Status.ACCEPTED);
            friendshipRepository.save(friendship);
        } else {
            friendshipRepository.delete(friendship);
        }
        return FriendResponse.from(friendship, user.getId());
    }

    @Transactional
    public void removeFriend(User user, String friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> ApiException.notFound("Friendship not found"));
        boolean involved = friendship.getRequester().getId().equals(user.getId())
                || friendship.getAddressee().getId().equals(user.getId());
        if (!involved) {
            throw ApiException.forbidden("You are not part of this friendship");
        }
        friendshipRepository.delete(friendship);
    }

    private Friendship upsertFriendship(User requester, User addressee, Status status) {
        return friendshipRepository.findBetween(requester.getId(), addressee.getId())
                .map(existing -> {
                    if (existing.getStatus() == Status.BLOCKED) {
                        throw ApiException.forbidden("Cannot add this user");
                    }
                    if (existing.getStatus() == Status.ACCEPTED) {
                        throw ApiException.conflict("You are already friends");
                    }
                    // PENDING: a code redemption upgrades it; a duplicate request conflicts
                    if (status == Status.ACCEPTED) {
                        existing.setStatus(Status.ACCEPTED);
                        return friendshipRepository.save(existing);
                    }
                    throw ApiException.conflict("A friend request is already pending");
                })
                .orElseGet(() -> friendshipRepository.save(new Friendship(requester, addressee, status)));
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
