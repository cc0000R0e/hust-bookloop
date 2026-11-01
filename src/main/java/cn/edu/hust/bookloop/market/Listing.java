package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

/** Immutable after insertion; publication creates a new aggregate in a transaction. */
@Entity
@Table(name = "listing")
@Immutable
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
    // DATETIME stores UTC wall time. Read directly, without java.sql.Timestamp's JVM timezone conversion.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "published_at", nullable = false) private LocalDateTime publishedAt;
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
        this.title = form.getTitle().strip(); this.category = form.getCategory();
        this.author = form.getAuthor(); this.publisher = form.getPublisher();
        this.editionNote = form.getEditionNote(); this.courseName = form.getCourseName();
        this.description = form.getDescription().strip(); this.noteLevel = form.getNoteLevel();
        this.conditionCode = form.getConditionCode(); this.transferType = form.getTransferType();
        this.price = "GIFT".equals(transferType) ? BigDecimal.ZERO : form.getPrice();
        this.lifecycleStatus = "ACTIVE"; this.adminBlocked = false;
        this.publishedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
    public Long getOwnerId() { return ownerId; }
    public String getPublisher() { return publisher; }
    public String getEditionNote() { return editionNote; }
    public String getDescription() { return description; }
    public String getNoteLevel() { return noteLevel; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public boolean isAdminBlocked() { return adminBlocked; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
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
