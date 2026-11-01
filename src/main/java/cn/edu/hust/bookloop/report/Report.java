package cn.edu.hust.bookloop.report;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.ColumnDefault;

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
    @Column(name = "handler_id")
    private Long handlerId;
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;
    @Column(name = "created_at", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;
    @Column(name = "handled_at")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime handledAt;
    @Version
    @ColumnDefault("0")
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected Report() {}

    public Report(Long listingId, Long reporterId, String reasonCode, String description) {
        this.listingId = listingId;
        this.reporterId = reporterId;
        this.reasonCode = reasonCode;
        this.description = description;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void handle(Long handlerId, String status, String resolutionNote) {
        if (!"PENDING".equals(this.status)) {
            throw new IllegalStateException("该举报已经处理。");
        }
        this.status = status;
        this.handlerId = handlerId;
        this.resolutionNote = resolutionNote;
        this.handledAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public Long getReporterId() { return reporterId; }
    public String getReasonCode() { return reasonCode; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Long getHandlerId() { return handlerId; }
    public String getResolutionNote() { return resolutionNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public long getRowVersion() { return rowVersion; }
}
