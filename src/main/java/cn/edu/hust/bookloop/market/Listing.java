package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

/** Listing aggregate. Edits and lifecycle transitions are guarded by ownership and row version. */
@Entity
@Table(name = "listing")
public class Listing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(length = 100) private String publisher;
    @Column(name = "edition_note", length = 100) private String editionNote;
    @Column(nullable = false, length = 2000) private String description;
    @Column(name = "note_level", nullable = false, length = 16) private String noteLevel;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 16) private String category;
    @Column(length = 100) private String author;
    @Column(name = "course_name", length = 100) private String courseName;
    @Column(name = "condition_code", nullable = false, length = 16) private String conditionCode;
    @Column(name = "transfer_type", nullable = false, length = 16) private String transferType;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal price;
    @Column(name = "lifecycle_status", nullable = false, length = 16) private String lifecycleStatus;
    @Column(name = "admin_blocked", nullable = false) private boolean adminBlocked;
    @Column(name = "admin_reason", length = 500) private String adminReason;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Version
    @ColumnDefault("0")
    @Column(name = "row_version", nullable = false) private long rowVersion;
    // DATETIME stores UTC wall time. Read directly, without java.sql.Timestamp's JVM timezone conversion.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "published_at", nullable = false) private LocalDateTime publishedAt;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "listing")
    @BatchSize(size = 20)
    private List<ListingScope> scopes = new ArrayList<>();
    @ManyToMany
    @JoinTable(name = "listing_tag", joinColumns = @JoinColumn(name = "listing_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 20)
    private List<BookTag> tags = new ArrayList<>();

    protected Listing() {}
    public Listing(Long ownerId, cn.edu.hust.bookloop.publish.PublishForm form) {
        this.ownerId = ownerId;
        apply(form);
        this.lifecycleStatus = "ACTIVE"; this.adminBlocked = false;
        this.publishedAt = this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void apply(cn.edu.hust.bookloop.publish.PublishForm form) {
        this.title = form.getTitle().strip();
        this.category = form.getCategory();
        this.author = optional(form.getAuthor());
        this.publisher = optional(form.getPublisher());
        this.editionNote = optional(form.getEditionNote());
        this.courseName = optional(form.getCourseName());
        this.description = form.getDescription().strip();
        this.noteLevel = form.getNoteLevel();
        this.conditionCode = form.getConditionCode();
        this.transferType = form.getTransferType();
        this.price = "GIFT".equals(transferType) ? BigDecimal.ZERO : form.getPrice();
        touch();
    }

    public void reserve() {
        requireLifecycle("ACTIVE");
        lifecycleStatus = "RESERVED";
        touch();
    }

    public void cancelReservation() {
        requireLifecycle("RESERVED");
        lifecycleStatus = "ACTIVE";
        touch();
    }

    public void complete() {
        if (!"ACTIVE".equals(lifecycleStatus) && !"RESERVED".equals(lifecycleStatus)) {
            throw new IllegalStateException("只有可获取或已预订的资料可以完成流转。");
        }
        lifecycleStatus = "COMPLETED";
        completedAt = LocalDateTime.now(ZoneOffset.UTC);
        touch();
    }

    public void withdraw() {
        if (!"ACTIVE".equals(lifecycleStatus) && !"RESERVED".equals(lifecycleStatus)) {
            throw new IllegalStateException("只有可获取或已预订的资料可以下架。");
        }
        lifecycleStatus = "WITHDRAWN";
        touch();
    }

    public void relist() {
        requireLifecycle("WITHDRAWN");
        lifecycleStatus = "ACTIVE";
        touch();
    }

    public boolean isOwner(long userId) { return ownerId == userId; }
    public boolean isEditable() { return !adminBlocked && !"COMPLETED".equals(lifecycleStatus); }

    private void requireLifecycle(String expected) {
        if (!expected.equals(lifecycleStatus)) {
            throw new IllegalStateException("当前资料状态不允许执行此操作。");
        }
    }
    private void touch() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public Long getOwnerId() { return ownerId; }
    public String getPublisher() { return publisher; }
    public String getEditionNote() { return editionNote; }
    public String getDescription() { return description; }
    public String getNoteLevel() { return noteLevel; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public boolean isAdminBlocked() { return adminBlocked; }
    public String getAdminReason() { return adminReason; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public long getRowVersion() { return rowVersion; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getAuthor() { return author; }
    public String getCourseName() { return courseName; }
    public String getConditionCode() { return conditionCode; }
    public String getTransferType() { return transferType; }
    public BigDecimal getPrice() { return price; }
    public List<ListingScope> getScopes() { return scopes; }
    public List<BookTag> getTags() { return tags; }
}
