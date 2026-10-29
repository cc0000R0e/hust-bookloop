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
    @Column(nullable = false, length = 30) private String nickname;
    @Column(nullable = false, length = 16) private String role;
    @Column(name = "default_contact_type", length = 16) private String defaultContactType;
    @Column(name = "default_contact_value", length = 100) private String defaultContactValue;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected AppUser() {}
    public AppUser(String username, String passwordHash, String nickname) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = "USER";
    }
    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public String getRole() { return role; }
}
