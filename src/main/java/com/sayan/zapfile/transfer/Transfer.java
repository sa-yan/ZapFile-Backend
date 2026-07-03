package com.sayan.zapfile.transfer;

import com.sayan.zapfile.device.Device;
import com.sayan.zapfile.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Metadata for one file transfer between two devices. The file bytes flow
 * peer-to-peer between the devices — this row only tracks negotiation and
 * outcome, and is what powers the history screen.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    public enum Status {
        OFFERED, ACCEPTED, IN_PROGRESS, COMPLETED, DECLINED, CANCELLED, FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == DECLINED || this == CANCELLED || this == FAILED;
        }
    }

    /** How the bytes actually moved: direct WebRTC, or streamed through the server relay. */
    public enum Mode {
        P2P, RELAY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Groups the files offered together in one send action. */
    @Column(nullable = false, updatable = false)
    private String batchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_device_id")
    private Device senderDevice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    /** Set when the receiver accepts, identifying which of their devices takes the file. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_device_id")
    private Device receiverDevice;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private long fileSize;

    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OFFERED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.P2P;

    /** Last byte offset reported by the devices; lets a resumed transfer continue from here. */
    @Column(nullable = false)
    private long bytesTransferred = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Transfer() {
    }

    public Transfer(String batchId, User sender, Device senderDevice, User receiver,
                    String fileName, long fileSize, String mimeType) {
        this.batchId = batchId;
        this.sender = sender;
        this.senderDevice = senderDevice;
        this.receiver = receiver;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }

    public String getId() {
        return id;
    }

    public String getBatchId() {
        return batchId;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public void setBytesTransferred(long bytesTransferred) {
        this.bytesTransferred = bytesTransferred;
    }

    public User getSender() {
        return sender;
    }

    public Device getSenderDevice() {
        return senderDevice;
    }

    public User getReceiver() {
        return receiver;
    }

    public Device getReceiverDevice() {
        return receiverDevice;
    }

    public void setReceiverDevice(Device receiverDevice) {
        this.receiverDevice = receiverDevice;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean involvesUser(String userId) {
        return sender.getId().equals(userId) || receiver.getId().equals(userId);
    }

    public boolean involvesDevice(String deviceId) {
        return senderDevice.getId().equals(deviceId)
                || (receiverDevice != null && receiverDevice.getId().equals(deviceId));
    }

    /** The device on the other side of the transfer from {@code deviceId}, or null if not yet known. */
    public Device deviceOtherThan(String deviceId) {
        if (senderDevice.getId().equals(deviceId)) {
            return receiverDevice;
        }
        return senderDevice;
    }
}
