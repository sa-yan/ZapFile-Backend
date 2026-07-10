package com.sayan.zapfile.friend;

import com.sayan.zapfile.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A friendship between two users. requester is who initiated it;
 * addressee is who received the request.
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}),
        indexes = {
                @Index(name = "idx_friendships_requester_id", columnList = "requester_id"),
                @Index(name = "idx_friendships_addressee_id", columnList = "addressee_id")
        })
public class Friendship {

    public enum Status {
        PENDING, ACCEPTED, BLOCKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id")
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Friendship() {
    }

    public Friendship(User requester, User addressee, Status status) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public User getAddressee() {
        return addressee;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The user on the other side of this friendship from {@code userId}. */
    public User otherThan(String userId) {
        return requester.getId().equals(userId) ? addressee : requester;
    }
}
