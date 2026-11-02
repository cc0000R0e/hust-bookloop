package cn.edu.hust.bookloop.admin;

import cn.edu.hust.bookloop.account.AppUser;
import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.market.Listing;
import cn.edu.hust.bookloop.market.ListingRepository;
import cn.edu.hust.bookloop.market.MarketOptions;
import cn.edu.hust.bookloop.publish.ListingEvent;
import cn.edu.hust.bookloop.report.Report;
import cn.edu.hust.bookloop.report.ReportOptions;
import cn.edu.hust.bookloop.report.ReportRepository;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> DECISIONS = Set.of("RESOLVED", "REJECTED");

    private final UserRepository users;
    private final ListingRepository listings;
    private final ReportRepository reports;
    private final EntityManager em;

    public AdminService(UserRepository users, ListingRepository listings, ReportRepository reports, EntityManager em) {
        this.users = users;
        this.listings = listings;
        this.reports = reports;
        this.em = em;
    }

    public record ReportCard(long id, long listingId, String reporter, String reason, String description,
                             String createdAt, String listingTitle, String listingOwner, String listingStatus,
                             boolean listingBlocked, String listingAdminReason, long version) {}

    public record ListingCard(long id, String title, String owner, String status, String transfer,
                              String adminReason, long version, boolean blocked) {}

    @Transactional(readOnly = true)
    public Page<ReportCard> pendingReports(String username, int requestedPage) {
        admin(username);
        int page = pageNumber(requestedPage);
        var result = reports.findByStatus("PENDING", PageRequest.of(page, 20,
                Sort.by("createdAt").ascending().and(Sort.by("id").ascending())));
        var listingMap = new HashMap<Long, Listing>();
        if (!result.isEmpty()) {
            listings.findAllById(result.stream().map(Report::getListingId).toList())
                    .forEach(listing -> listingMap.put(listing.getId(), listing));
        }
        var userIds = new HashSet<Long>();
        result.forEach(report -> userIds.add(report.getReporterId()));
        listingMap.values().forEach(listing -> userIds.add(listing.getOwnerId()));
        var names = new HashMap<Long, String>();
        users.findAllById(userIds).forEach(user -> names.put(user.getId(), user.getUsername()));
        return result.map(report -> {
            var listing = listingMap.get(report.getListingId());
            return new ReportCard(report.getId(), report.getListingId(),
                    names.getOrDefault(report.getReporterId(), "未知用户"),
                    ReportOptions.REASONS.getOrDefault(report.getReasonCode(), report.getReasonCode()),
                    optional(report.getDescription()), displayTime(report.getCreatedAt()),
                    listing == null ? "资料不存在" : displayTitle(listing.getTitle()),
                    listing == null ? "未知用户" : names.getOrDefault(listing.getOwnerId(), "未知用户"),
                    listing == null ? "不可用" : displayStatus(listing),
                    listing != null && listing.isAdminBlocked(),
                    listing == null ? null : listing.getAdminReason(),
                    report.getRowVersion());
        });
    }

    @Transactional(readOnly = true)
    public Page<ListingCard> listings(String username, int requestedPage) {
        admin(username);
        int page = pageNumber(requestedPage);
        var result = listings.findAll(PageRequest.of(page, 20,
                Sort.by("publishedAt").descending().and(Sort.by("id").descending())));
        var ownerIds = result.stream().map(Listing::getOwnerId).distinct().toList();
        var names = new HashMap<Long, String>();
        users.findAllById(ownerIds).forEach(user -> names.put(user.getId(), user.getUsername()));
        return result.map(listing -> new ListingCard(listing.getId(), listing.getTitle(),
                names.getOrDefault(listing.getOwnerId(), "未知用户"), displayStatus(listing),
                "GIFT".equals(listing.getTransferType()) ? "免费赠送"
                        : "¥" + listing.getPrice().setScale(2).toPlainString(),
                listing.getAdminReason(), listing.getRowVersion(), listing.isAdminBlocked()));
    }

    @Transactional
    public void handleReport(long reportId, String decision, String rawReason,
                             long expectedReportVersion, String username) {
        var admin = admin(username);
        if (!DECISIONS.contains(decision)) throw new AdminFailure(HttpStatus.BAD_REQUEST, "请选择处理或驳回。");
        String reason = requiredText(rawReason, "请填写处理理由。", 500);
        var report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new AdminFailure(HttpStatus.NOT_FOUND, "举报不存在。"));
        if (!"PENDING".equals(report.getStatus())) throw new AdminFailure("该举报已经处理。");
        if (report.getRowVersion() != expectedReportVersion) {
            throw new AdminFailure("举报已被其他管理员处理，请刷新页面。");
        }
        var listing = listings.findByIdForUpdate(report.getListingId())
                .orElseThrow(() -> new AdminFailure(HttpStatus.NOT_FOUND, "关联资料不存在。"));
        if ("RESOLVED".equals(decision) && !listing.isAdminBlocked()) {
            try {
                String before = snapshot(listing);
                listing.blockByAdmin(reason);
                em.flush();
                em.persist(new ListingEvent(listing.getId(), admin.getId(), admin.getRole(), "ADMIN_BLOCK",
                        before, snapshot(listing), reason));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new AdminFailure(exception.getMessage());
            }
        }
        report.handle(admin.getId(), decision, reason);
        reports.flush();
    }

    @Transactional
    public void blockListing(long listingId, String rawReason, long expectedVersion, String username) {
        var admin = admin(username);
        String reason = requiredText(rawReason, "请填写管理员下架原因。", 500);
        var listing = listings.findByIdForUpdate(listingId)
                .orElseThrow(() -> new AdminFailure(HttpStatus.NOT_FOUND, "资料不存在。"));
        requireVersion(listing, expectedVersion);
        try {
            String before = snapshot(listing);
            listing.blockByAdmin(reason);
            em.flush();
            em.persist(new ListingEvent(listing.getId(), admin.getId(), admin.getRole(), "ADMIN_BLOCK",
                    before, snapshot(listing), reason));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new AdminFailure(exception.getMessage());
        }
    }

    @Transactional
    public void releaseListing(long listingId, long expectedVersion, String username) {
        var admin = admin(username);
        var listing = listings.findByIdForUpdate(listingId)
                .orElseThrow(() -> new AdminFailure(HttpStatus.NOT_FOUND, "资料不存在。"));
        requireVersion(listing, expectedVersion);
        try {
            String before = snapshot(listing);
            listing.releaseByAdmin();
            em.flush();
            em.persist(new ListingEvent(listing.getId(), admin.getId(), admin.getRole(), "ADMIN_RELEASE",
                    before, snapshot(listing), null));
        } catch (IllegalStateException exception) {
            throw new AdminFailure(exception.getMessage());
        }
    }

    private AppUser admin(String username) {
        var user = users.findByUsername(username)
                .orElseThrow(() -> new AdminFailure(HttpStatus.UNAUTHORIZED, "登录账号不存在。"));
        if (!"ADMIN".equals(user.getRole())) {
            throw new AdminFailure(HttpStatus.FORBIDDEN, "只有管理员可以执行此操作。");
        }
        return user;
    }

    private void requireVersion(Listing listing, long expectedVersion) {
        if (listing.getRowVersion() != expectedVersion) {
            throw new AdminFailure("资料已发生变化，请刷新页面后重试。");
        }
    }

    private int pageNumber(int requested) {
        return Math.max(0, Math.min(requested, 100_000));
    }

    private String requiredText(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new AdminFailure(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String displayTitle(String title) {
        return title == null || title.isBlank() ? "未命名资料" : title;
    }

    private String displayStatus(Listing listing) {
        if (listing.isAdminBlocked()) return "已下架（管理员限制）";
        boolean gift = "GIFT".equals(listing.getTransferType());
        return switch (listing.getLifecycleStatus()) {
            case "ACTIVE" -> gift ? "待赠送" : "在售";
            case "RESERVED" -> "已预订";
            case "COMPLETED" -> gift ? "已送出" : "已售出";
            default -> "已下架";
        };
    }

    private String displayTime(java.time.LocalDateTime time) {
        return time.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).format(DISPLAY_TIME);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    private String snapshot(Listing listing) {
        String completed = listing.getCompletedAt() == null ? "null" : "\"" + listing.getCompletedAt() + "\"";
        return "{\"lifecycleStatus\":\"" + listing.getLifecycleStatus()
                + "\",\"transferType\":\"" + listing.getTransferType()
                + "\",\"price\":\"" + listing.getPrice().setScale(2).toPlainString()
                + "\",\"adminBlocked\":" + listing.isAdminBlocked()
                + ",\"completedAt\":" + completed
                + ",\"rowVersion\":" + listing.getRowVersion() + "}";
    }
}
