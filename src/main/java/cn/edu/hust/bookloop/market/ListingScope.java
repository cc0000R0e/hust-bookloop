package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "listing_scope")
@Immutable
public class ListingScope {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false) private Listing listing;
    @Column(name = "scope_type", nullable = false, length = 16) private String scopeType;
    @Column(name = "major_id") private Long majorId;
    @Column(length = 100) private String college;
    public String getCollege() { return college == null ? "" : college; }
    @Column(name = "semester_code", nullable = false, length = 16) private String semesterCode;
    protected ListingScope() {}
    public ListingScope(Listing listing, Long majorId, String semester) {
        this(listing, majorId, semester, "");
    }
    public ListingScope(Listing listing, Long majorId, String semester, String college) {
        this.listing = listing;
        this.majorId = majorId == 0 ? null : majorId;
        this.college = majorId == 0 && !CollegeNames.normalize(college).isEmpty() ? college : null;
        this.scopeType = majorId != 0 ? "SPECIFIC" : this.college == null ? "ALL" : "COLLEGE";
        this.semesterCode = semester;
    }
    public String getScopeType() { return scopeType; }
    public Long getMajorId() { return majorId; }
    public String getSemesterCode() { return semesterCode; }
}
