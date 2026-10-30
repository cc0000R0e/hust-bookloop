package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

/** Read-only market projection; publishing will supply a separate write model. */
@Entity
@Table(name = "listing")
@Immutable
public class Listing {
    @Id private Long id;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 16) private String category;
    @Column(length = 100) private String author;
    @Column(name = "course_name", length = 100) private String courseName;
    @Column(name = "condition_code", nullable = false, length = 16) private String conditionCode;
    @Column(name = "transfer_type", nullable = false, length = 16) private String transferType;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal price;
    @Column(name = "lifecycle_status", nullable = false, length = 16) private String lifecycleStatus;
    @Column(name = "admin_blocked", nullable = false) private boolean adminBlocked;
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
