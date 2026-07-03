package com.sayan.zapfile.transfer;

import com.sayan.zapfile.common.ApiException;
import com.sayan.zapfile.device.Device;
import com.sayan.zapfile.device.DeviceRepository;
import com.sayan.zapfile.friend.FriendshipRepository;
import com.sayan.zapfile.friend.Friendship;
import com.sayan.zapfile.notification.PushNotifier;
import com.sayan.zapfile.presence.WsSessionRegistry;
import com.sayan.zapfile.transfer.Transfer.Mode;
import com.sayan.zapfile.transfer.Transfer.Status;
import com.sayan.zapfile.transfer.TransferDtos.BatchResponse;
import com.sayan.zapfile.transfer.TransferDtos.CreateTransferRequest;
import com.sayan.zapfile.transfer.TransferDtos.TransferResponse;
import com.sayan.zapfile.user.User;
import com.sayan.zapfile.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final WsSessionRegistry registry;
    private final PushNotifier pushNotifier;

    public TransferService(TransferRepository transferRepository,
                           DeviceRepository deviceRepository,
                           UserRepository userRepository,
                           FriendshipRepository friendshipRepository,
                           WsSessionRegistry registry,
                           PushNotifier pushNotifier) {
        this.transferRepository = transferRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.registry = registry;
        this.pushNotifier = pushNotifier;
    }

    @Transactional
    public BatchResponse createOffer(User sender, CreateTransferRequest request) {
        Device senderDevice = deviceRepository.findByIdAndUserId(request.senderDeviceId(), sender.getId())
                .orElseThrow(() -> ApiException.notFound("Sender device not found"));
        User receiver = userRepository.findById(request.receiverUserId())
                .orElseThrow(() -> ApiException.notFound("Receiver not found"));

        // sending to yourself (own devices) is always allowed; anyone else must be an accepted friend
        if (!receiver.getId().equals(sender.getId())) {
            boolean friends = friendshipRepository.findBetween(sender.getId(), receiver.getId())
                    .map(f -> f.getStatus() == Friendship.Status.ACCEPTED)
                    .orElse(false);
            if (!friends) {
                throw ApiException.forbidden("You can only send files to accepted friends");
            }
        }

        String batchId = UUID.randomUUID().toString();
        List<Transfer> transfers = request.files().stream()
                .map(f -> transferRepository.save(new Transfer(
                        batchId, sender, senderDevice, receiver,
                        f.fileName().trim(), f.fileSize(), f.mimeType())))
                .toList();

        BatchResponse response = BatchResponse.from(batchId, transfers);
        boolean delivered = registry.sendToUser(receiver.getId(), "transfer.offer", response);
        if (!delivered) {
            // no device online — wake them up via push
            deviceRepository.findByUserId(receiver.getId())
                    .forEach(device -> pushNotifier.notifyTransferOffer(device, transfers));
        }
        return response;
    }

    @Transactional
    public BatchResponse acceptBatch(User user, String batchId, String receiverDeviceId) {
        List<Transfer> transfers = requireBatch(batchId);
        if (!transfers.get(0).getReceiver().getId().equals(user.getId())) {
            throw ApiException.forbidden("Only the receiver can accept this batch");
        }
        Device receiverDevice = deviceRepository.findByIdAndUserId(receiverDeviceId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Receiver device not found"));
        List<Transfer> offered = transfers.stream().filter(t -> t.getStatus() == Status.OFFERED).toList();
        if (offered.isEmpty()) {
            throw ApiException.conflict("No offered transfers left in this batch");
        }
        offered.forEach(t -> {
            t.setReceiverDevice(receiverDevice);
            t.setStatus(Status.ACCEPTED);
        });
        transferRepository.saveAll(offered);

        BatchResponse response = BatchResponse.from(batchId, transfers);
        registry.sendToDevice(transfers.get(0).getSenderDevice().getId(), "transfer.accepted", response);
        return response;
    }

    @Transactional
    public BatchResponse declineBatch(User user, String batchId) {
        List<Transfer> transfers = requireBatch(batchId);
        if (!transfers.get(0).getReceiver().getId().equals(user.getId())) {
            throw ApiException.forbidden("Only the receiver can decline this batch");
        }
        List<Transfer> offered = transfers.stream().filter(t -> t.getStatus() == Status.OFFERED).toList();
        if (offered.isEmpty()) {
            throw ApiException.conflict("No offered transfers left in this batch");
        }
        offered.forEach(t -> t.setStatus(Status.DECLINED));
        transferRepository.saveAll(offered);

        BatchResponse response = BatchResponse.from(batchId, transfers);
        registry.sendToDevice(transfers.get(0).getSenderDevice().getId(), "transfer.declined", response);
        return response;
    }

    @Transactional
    public BatchResponse cancelBatch(User user, String batchId) {
        List<Transfer> transfers = requireBatch(batchId);
        if (!transfers.get(0).involvesUser(user.getId())) {
            throw ApiException.forbidden("You are not part of this batch");
        }
        List<Transfer> active = transfers.stream().filter(t -> !t.getStatus().isTerminal()).toList();
        if (active.isEmpty()) {
            throw ApiException.conflict("All transfers in this batch are already finished");
        }
        active.forEach(t -> t.setStatus(Status.CANCELLED));
        transferRepository.saveAll(active);

        BatchResponse response = BatchResponse.from(batchId, transfers);
        notifyBoth(transfers.get(0), "transfer.cancelled", response);
        return response;
    }

    @Transactional
    public TransferResponse accept(User user, String transferId, String receiverDeviceId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.getReceiver().getId().equals(user.getId())) {
            throw ApiException.forbidden("Only the receiver can accept this transfer");
        }
        requireStatus(transfer, Status.OFFERED);
        Device receiverDevice = deviceRepository.findByIdAndUserId(receiverDeviceId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Receiver device not found"));
        transfer.setReceiverDevice(receiverDevice);
        transfer.setStatus(Status.ACCEPTED);
        transferRepository.save(transfer);

        TransferResponse response = TransferResponse.from(transfer);
        registry.sendToDevice(transfer.getSenderDevice().getId(), "transfer.accepted", response);
        return response;
    }

    @Transactional
    public TransferResponse decline(User user, String transferId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.getReceiver().getId().equals(user.getId())) {
            throw ApiException.forbidden("Only the receiver can decline this transfer");
        }
        requireStatus(transfer, Status.OFFERED);
        return finish(transfer, Status.DECLINED, "transfer.declined");
    }

    @Transactional
    public TransferResponse cancel(User user, String transferId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.involvesUser(user.getId())) {
            throw ApiException.forbidden("You are not part of this transfer");
        }
        if (transfer.getStatus().isTerminal()) {
            throw ApiException.conflict("Transfer is already " + transfer.getStatus());
        }
        return finish(transfer, Status.CANCELLED, "transfer.cancelled");
    }

    @Transactional
    public TransferResponse complete(User user, String transferId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.involvesUser(user.getId())) {
            throw ApiException.forbidden("You are not part of this transfer");
        }
        if (transfer.getStatus() != Status.ACCEPTED && transfer.getStatus() != Status.IN_PROGRESS) {
            throw ApiException.conflict("Transfer is " + transfer.getStatus() + ", cannot complete");
        }
        return finish(transfer, Status.COMPLETED, "transfer.completed");
    }

    @Transactional
    public TransferResponse fail(User user, String transferId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.involvesUser(user.getId())) {
            throw ApiException.forbidden("You are not part of this transfer");
        }
        if (transfer.getStatus().isTerminal()) {
            throw ApiException.conflict("Transfer is already " + transfer.getStatus());
        }
        return finish(transfer, Status.FAILED, "transfer.failed");
    }

    /**
     * Re-arms a FAILED transfer so the devices can reconnect and continue
     * from {@code bytesTransferred}. The receiver device chosen at accept
     * time is kept.
     */
    @Transactional
    public TransferResponse resume(User user, String transferId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.involvesUser(user.getId())) {
            throw ApiException.forbidden("You are not part of this transfer");
        }
        requireStatus(transfer, Status.FAILED);
        if (transfer.getReceiverDevice() == null) {
            throw ApiException.conflict("Transfer was never accepted; offer it again instead");
        }
        transfer.setStatus(Status.ACCEPTED);
        transferRepository.save(transfer);

        TransferResponse response = TransferResponse.from(transfer);
        notifyBoth(transfer, "transfer.resumed", response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> history(User user) {
        return transferRepository.findAllInvolvingUser(user.getId()).stream()
                .map(TransferResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(User user, String transferId) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.involvesUser(user.getId())) {
            throw ApiException.forbidden("You are not part of this transfer");
        }
        return TransferResponse.from(transfer);
    }

    /** Called by the WebSocket handler for each relayed progress message. */
    @Transactional
    public void markProgress(String transferId, long bytesTransferred) {
        transferRepository.findById(transferId).ifPresent(t -> {
            boolean changed = false;
            if (t.getStatus() == Status.ACCEPTED) {
                t.setStatus(Status.IN_PROGRESS);
                changed = true;
            }
            if (bytesTransferred > t.getBytesTransferred()) {
                t.setBytesTransferred(bytesTransferred);
                changed = true;
            }
            if (changed) {
                transferRepository.save(t);
            }
        });
    }

    /** Called when a device opens a relay connection: bytes will flow through the server. */
    @Transactional
    public void markRelayStarted(String transferId) {
        transferRepository.findById(transferId).ifPresent(t -> {
            t.setMode(Mode.RELAY);
            if (t.getStatus() == Status.ACCEPTED) {
                t.setStatus(Status.IN_PROGRESS);
            }
            transferRepository.save(t);
        });
    }

    private TransferResponse finish(Transfer transfer, Status status, String eventType) {
        transfer.setStatus(status);
        transferRepository.save(transfer);
        TransferResponse response = TransferResponse.from(transfer);
        notifyBoth(transfer, eventType, response);
        return response;
    }

    private void notifyBoth(Transfer transfer, String eventType, Object payload) {
        // notify both sides; the caller's own other devices also stay in sync
        registry.sendToUser(transfer.getSender().getId(), eventType, payload);
        if (!transfer.getReceiver().getId().equals(transfer.getSender().getId())) {
            registry.sendToUser(transfer.getReceiver().getId(), eventType, payload);
        }
    }

    private Transfer requireTransfer(String transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> ApiException.notFound("Transfer not found"));
    }

    private List<Transfer> requireBatch(String batchId) {
        List<Transfer> transfers = transferRepository.findByBatchId(batchId);
        if (transfers.isEmpty()) {
            throw ApiException.notFound("Batch not found");
        }
        return transfers;
    }

    private void requireStatus(Transfer transfer, Status expected) {
        if (transfer.getStatus() != expected) {
            throw ApiException.conflict("Transfer is " + transfer.getStatus() + ", expected " + expected);
        }
    }
}
