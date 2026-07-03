package com.sayan.zapfile.friend;

import com.sayan.zapfile.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A short-lived code a user shows to a friend; redeeming it creates an
 * immediately-accepted friendship (physically sharing the code proves intent).
 */
@Entity
@Table(name = "pairing_codes")
public class PairingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    protected PairingCode() {
    }

    public PairingCode(String code, User owner, Instant expiresAt) {
        this.code = code;
        this.owner = owner;
        this.expiresAt = expiresAt;
    }

    public String getCode() {
        return code;
    }

    public User getOwner() {
        return owner;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void markUsed() {
        this.used = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
