package cn.edu.hust.bookloop.account;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40) private String username;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false, length = 16) private String role;
    @Column(name = "default_contact_type", length = 16) private String defaultContactType;
    @Column(name = "default_contact_value", length = 100) private String defaultContactValue;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @Column(name = "major_id") private Long majorId;
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }

    protected AppUser() {}
    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = "USER";
    }
    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDefaultContactType() { return defaultContactType; }
    public String getDefaultContactValue() { return defaultContactValue; }
    public void updateContact(String type, String value) {
        this.defaultContactType = type;
        this.defaultContactValue = value;
    }
    public void changePassword(String hash) { this.passwordHash = hash; }
    public String getRole() { return role; }
}
