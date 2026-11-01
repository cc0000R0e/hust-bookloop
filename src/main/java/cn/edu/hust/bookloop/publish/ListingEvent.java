package cn.edu.hust.bookloop.publish;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "listing_event")
public class ListingEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "listing_id", nullable = false) private Long listingId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(name = "actor_role", nullable = false, length = 16) private String actorRole;
    @Column(name = "action_code", nullable = false, length = 24) private String actionCode;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "json") private String beforeSnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", nullable = false, columnDefinition = "json") private String afterSnapshot;
    @Column(length = 500) private String reason;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected ListingEvent() {}
    public ListingEvent(Long listingId, Long actorId, String role, String snapshot) {
        this.listingId = listingId; this.actorId = actorId; this.actorRole = role;
        this.actionCode = "PUBLISH"; this.afterSnapshot = snapshot; this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public ListingEvent(Long listingId, Long actorId, String role, String action,
                        String beforeSnapshot, String afterSnapshot, String reason) {
        this.listingId = listingId;
        this.actorId = actorId;
        this.actorRole = role;
        this.actionCode = action;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.reason = reason;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
