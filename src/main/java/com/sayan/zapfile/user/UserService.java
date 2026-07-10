package com.sayan.zapfile.user;

import com.sayan.zapfile.common.ApiException;
import com.sayan.zapfile.device.DeviceRepository;
import com.sayan.zapfile.friend.FriendshipRepository;
import com.sayan.zapfile.friend.PairingCodeRepository;
import com.sayan.zapfile.transfer.TransferRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final FriendshipRepository friendshipRepository;
    private final PairingCodeRepository pairingCodeRepository;
    private final TransferRepository transferRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       DeviceRepository deviceRepository,
                       FriendshipRepository friendshipRepository,
                       PairingCodeRepository pairingCodeRepository,
                       TransferRepository transferRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.friendshipRepository = friendshipRepository;
        this.pairingCodeRepository = pairingCodeRepository;
        this.transferRepository = transferRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Deletes the account and every row that references it (Play Store data
     * deletion requirement). Requires the password again so a stolen access
     * token alone cannot destroy the account. 403 (not 401) on a wrong
     * password — the app auto-refreshes tokens on 401 and would retry.
     */
    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.forbidden("Incorrect password");
        }
        // order matters: transfers reference devices and both users
        transferRepository.deleteAllInvolvingUser(user.getId());
        friendshipRepository.deleteAllInvolvingUser(user.getId());
        pairingCodeRepository.deleteAllByOwner(user.getId());
        deviceRepository.deleteAllByUser(user.getId());
        userRepository.deleteById(user.getId());
        // refresh tokens are stateless JWTs; /auth/refresh rejects them once
        // the user row is gone, so outstanding sessions die on next refresh
    }
}
