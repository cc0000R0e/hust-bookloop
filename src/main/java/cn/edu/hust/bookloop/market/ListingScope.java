package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "listing_scope")
@Immutable
public class ListingScope {
    @Id private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false) private Listing listing;
    @Column(name = "scope_type", nullable = false, length = 16) private String scopeType;
    @Column(name = "major_id") private Long majorId;
    @Column(name = "semester_code", nullable = false, length = 16) private String semesterCode;
    protected ListingScope() {}
    public String getScopeType() { return scopeType; }
    public Long getMajorId() { return majorId; }
    public String getSemesterCode() { return semesterCode; }
}
