package cn.edu.hust.bookloop.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByListingIdAndReporterIdAndStatus(Long listingId, Long reporterId, String status);
}
