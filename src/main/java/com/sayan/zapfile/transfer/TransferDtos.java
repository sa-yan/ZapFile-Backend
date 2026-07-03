package com.sayan.zapfile.transfer;

import com.sayan.zapfile.transfer.Transfer.Mode;
import com.sayan.zapfile.transfer.Transfer.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record FileMeta(
            @NotBlank @Size(max = 255) String fileName,
            @Min(1) long fileSize,
            @Size(max = 100) String mimeType) {
    }

    public record CreateTransferRequest(
            @NotBlank String senderDeviceId,
            @NotBlank String receiverUserId,
            @NotEmpty @Size(max = 100) @Valid List<FileMeta> files) {
    }

    public record AcceptTransferRequest(@NotBlank String receiverDeviceId) {
    }

    /** One send action: all files offered together, grouped by batchId. */
    public record BatchResponse(String batchId, List<TransferResponse> transfers) {
        public static BatchResponse from(String batchId, List<Transfer> transfers) {
            return new BatchResponse(batchId, transfers.stream().map(TransferResponse::from).toList());
        }
    }

    public record TransferResponse(
            String id,
            String batchId,
            String senderUserId,
            String senderDisplayName,
            String senderDeviceId,
            String receiverUserId,
            String receiverDisplayName,
            String receiverDeviceId,
            String fileName,
            long fileSize,
            String mimeType,
            Status status,
            Mode mode,
            long bytesTransferred,
            Instant createdAt,
            Instant updatedAt) {

        public static TransferResponse from(Transfer t) {
            return new TransferResponse(
                    t.getId(),
                    t.getBatchId(),
                    t.getSender().getId(),
                    t.getSender().getDisplayName(),
                    t.getSenderDevice().getId(),
                    t.getReceiver().getId(),
                    t.getReceiver().getDisplayName(),
                    t.getReceiverDevice() == null ? null : t.getReceiverDevice().getId(),
                    t.getFileName(),
                    t.getFileSize(),
                    t.getMimeType(),
                    t.getStatus(),
                    t.getMode(),
                    t.getBytesTransferred(),
                    t.getCreatedAt(),
                    t.getUpdatedAt());
        }
    }
}
