package cn.edu.hust.bookloop.report;

import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.market.Listing;
import cn.edu.hust.bookloop.market.ListingRepository;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final Set<String> REPORTABLE_STATES = Set.of("ACTIVE", "RESERVED");

    private final UserRepository users;
    private final ListingRepository listings;
    private final ReportRepository reports;

    public ReportService(UserRepository users, ListingRepository listings, ReportRepository reports) {
        this.users = users;
        this.listings = listings;
        this.reports = reports;
    }

    @Transactional
    public void submit(long listingId, String reasonCode, String rawDescription, String username) {
        if (!ReportOptions.REASONS.containsKey(reasonCode)) {
            throw new ReportFailure(HttpStatus.BAD_REQUEST, "请选择举报原因。");
        }
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.codePointCount(0, description.length()) > 500) {
            throw new ReportFailure(HttpStatus.BAD_REQUEST, "补充说明不能超过500个字符。");
        }
        var reporter = users.findByUsername(username)
                .orElseThrow(() -> new ReportFailure(HttpStatus.UNAUTHORIZED, "登录账号不存在，请重新登录。"));
        var listing = listings.findById(listingId)
                .orElseThrow(() -> new ReportFailure(HttpStatus.NOT_FOUND, "资料不存在。"));
        if (listing.isOwner(reporter.getId())) {
            throw new ReportFailure(HttpStatus.FORBIDDEN, "不能举报自己发布的资料。");
        }
        if (listing.isAdminBlocked() || !REPORTABLE_STATES.contains(listing.getLifecycleStatus())) {
            throw new ReportFailure(HttpStatus.GONE, "该资料当前已不可获取，无法提交举报。");
        }
        if (reports.existsByListingIdAndReporterIdAndStatus(listingId, reporter.getId(), "PENDING")) {
            throw new ReportFailure(HttpStatus.CONFLICT, "你已举报过该资料，请等待管理员处理。");
        }
        try {
            reports.saveAndFlush(new Report(listingId, reporter.getId(), reasonCode,
                    description.isEmpty() ? null : description));
        } catch (DataIntegrityViolationException exception) {
            throw new ReportFailure(HttpStatus.CONFLICT, "你已举报过该资料，请等待管理员处理。");
        }
    }
}
