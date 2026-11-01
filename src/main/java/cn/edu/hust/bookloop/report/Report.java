package cn.edu.hust.bookloop.report;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "report")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "listing_id", nullable = false)
    private Long listingId;
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;
    @Column(name = "reason_code", nullable = false, length = 24)
    private String reasonCode;
    @Column(length = 500)
    private String description;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Report() {}

    public Report(Long listingId, Long reporterId, String reasonCode, String description) {
        this.listingId = listingId;
        this.reporterId = reporterId;
        this.reasonCode = reasonCode;
        this.description = description;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
