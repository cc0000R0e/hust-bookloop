package cn.edu.hust.bookloop.owner;

import cn.edu.hust.bookloop.account.AppUser;
import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.book.ListingContact;
import cn.edu.hust.bookloop.book.ListingContactRepository;
import cn.edu.hust.bookloop.image.ImageStorage;
import cn.edu.hust.bookloop.image.ListingImage;
import cn.edu.hust.bookloop.image.ListingImageRepository;
import cn.edu.hust.bookloop.market.Listing;
import cn.edu.hust.bookloop.market.ListingRepository;
import cn.edu.hust.bookloop.market.ListingScope;
import cn.edu.hust.bookloop.market.MarketOptions;
import cn.edu.hust.bookloop.publish.ListingEvent;
import cn.edu.hust.bookloop.publish.PublishForm;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OwnerListingService {
    private static final Set<String> FILTERS = Set.of("all", "active", "reserved", "completed", "withdrawn", "blocked");
    private static final Set<String> ACTIONS = Set.of("reserve", "cancel", "complete", "withdraw", "relist");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository users;
    private final ListingRepository listings;
    private final ListingContactRepository contacts;
    private final ListingImageRepository images;
    private final ImageStorage storage;
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    public OwnerListingService(UserRepository users, ListingRepository listings, ListingContactRepository contacts,
                               ListingImageRepository images, ImageStorage storage, EntityManager em, JdbcTemplate jdbc) {
        this.users = users;
        this.listings = listings;
        this.contacts = contacts;
        this.images = images;
        this.storage = storage;
        this.em = em;
        this.jdbc = jdbc;
    }

    public record Photo(Long id, String url) {}
    public record Card(long id, String title, String category, String status, String price,
                       String publishedAt, String coverUrl, long version, boolean restricted,
                       boolean editable, boolean canReserve, boolean canCancel, boolean canComplete,
                       boolean canWithdraw, boolean canRelist) {}
    public record EditData(PublishForm form, List<Photo> photos, long expectedVersion, String status) {}

    @Transactional(readOnly = true)
    public Page<Card> page(String username, String requestedStatus, int requestedPage) {
        String filter = requestedStatus == null || requestedStatus.isBlank() ? "all" : requestedStatus;
        if (!FILTERS.contains(filter)) throw new ManageFailure(HttpStatus.BAD_REQUEST, "发布状态筛选无效。");
        int page = Math.max(0, Math.min(requestedPage, 100_000));
        var user = user(username);
        Specification<Listing> spec = (root, query, cb) -> {
            var predicate = cb.equal(root.get("ownerId"), user.getId());
            if ("blocked".equals(filter)) {
                return cb.and(predicate, cb.isTrue(root.get("adminBlocked")));
            }
            predicate = cb.and(predicate, cb.isFalse(root.get("adminBlocked")));
            return "all".equals(filter) ? predicate : cb.and(predicate, cb.equal(root.get("lifecycleStatus"), switch (filter) {
                case "active" -> "ACTIVE";
                case "reserved" -> "RESERVED";
                case "completed" -> "COMPLETED";
                default -> "WITHDRAWN";
            }));
        };
        var pageable = PageRequest.of(page, 20,
                Sort.by("publishedAt").descending().and(Sort.by("id").descending()));
        var result = listings.findAll(spec, pageable);
        var covers = new HashMap<Long, String>();
        if (!result.isEmpty()) {
            images.findByListingIdInOrderBySortOrderAsc(result.stream().map(Listing::getId).toList())
                    .forEach(image -> covers.putIfAbsent(image.getListingId(),
                            "/books/" + image.getListingId() + "/images/" + image.getId()));
        }
        return result.map(listing -> card(listing, covers.get(listing.getId())));
    }

    @Transactional(readOnly = true)
    public EditData editData(long id, String username) {
        var user = user(username);
        var listing = listings.findById(id)
                .orElseThrow(() -> new ManageFailure(HttpStatus.NOT_FOUND, "资料不存在。"));
        requireOwner(listing, user);
        requireEditable(listing);
        var form = new PublishForm();
        form.setTitle(listing.getTitle());
        form.setCategory(listing.getCategory());
        form.setAuthor(nullToEmpty(listing.getAuthor()));
        form.setPublisher(nullToEmpty(listing.getPublisher()));
        form.setEditionNote(nullToEmpty(listing.getEditionNote()));
        form.setCourseName(nullToEmpty(listing.getCourseName()));
        form.setDescription(listing.getDescription());
        form.setConditionCode(listing.getConditionCode());
        form.setNoteLevel(listing.getNoteLevel());
        form.setTransferType(listing.getTransferType());
        form.setPrice(listing.getPrice());
        form.setScopes(new ArrayList<>(listing.getScopes().stream()
                .map(scope -> {
                    var value = new PublishForm.Scope();
                    value.setMajorId(scope.getMajorId() == null ? 0L : scope.getMajorId());
                    value.setSemester(scope.getSemesterCode());
                    return value;
                }).toList()));
        form.setTags(new ArrayList<>(listing.getTags().stream().map(tag -> tag.getId()).distinct().toList()));
        contacts.findByListingIdOrderByIdAsc(id).stream().findFirst().ifPresent(contact -> {
            form.setContactType(contact.getType());
            form.setContactValue(contact.getValue());
        });
        var photos = images.findByListingIdOrderBySortOrderAsc(id).stream()
                .map(image -> new Photo(image.getId(), "/books/" + id + "/images/" + image.getId()))
                .toList();
        return new EditData(form, photos, listing.getRowVersion(), displayStatus(listing));
    }

    @Transactional
    public void update(long id, PublishForm form, long expectedVersion, String username) {
        var user = user(username);
        var listing = locked(id);
        requireOwner(listing, user);
        requireEditable(listing);
        requireVersion(listing, expectedVersion);
        String before = snapshot(listing);
        boolean transferChanged = !listing.getTransferType().equals(form.getTransferType());
        if ("RESERVED".equals(listing.getLifecycleStatus()) && transferChanged) {
            throw new ManageFailure("已预订资料不能修改出售或赠送方式，请先取消预订。");
        }
        lockEnabledOptions(form);
        listing.apply(form);
        replaceScopes(listing, form);
        replaceTags(listing, form);
        replaceContact(listing, form);
        replaceImages(listing, form);
        em.flush();
        if (transferChanged) {
            em.persist(new ListingEvent(id, user.getId(), user.getRole(), "CHANGE_TRANSFER",
                    before, snapshot(listing), null));
        }
    }

    @Transactional
    public void changeStatus(long id, String action, long expectedVersion, String username) {
        if (!ACTIONS.contains(action)) throw new ManageFailure(HttpStatus.BAD_REQUEST, "资料操作无效。");
        var user = user(username);
        var listing = locked(id);
        requireOwner(listing, user);
        requireVersion(listing, expectedVersion);
        if (listing.isAdminBlocked()) {
            throw new ManageFailure("管理员下架的资料不能由发布者恢复或变更。");
        }
        String before = snapshot(listing);
        String eventAction;
        try {
            eventAction = switch (action) {
                case "reserve" -> {
                    listing.reserve();
                    yield "RESERVE";
                }
                case "cancel" -> {
                    listing.cancelReservation();
                    yield "CANCEL_RESERVATION";
                }
                case "complete" -> {
                    listing.complete();
                    yield "COMPLETE";
                }
                case "withdraw" -> {
                    listing.withdraw();
                    yield "WITHDRAW";
                }
                case "relist" -> {
                    listing.relist();
                    yield "RELIST";
                }
                default -> throw new IllegalStateException("Unreachable action");
            };
        } catch (IllegalStateException exception) {
            throw new ManageFailure(exception.getMessage());
        }
        em.flush();
        em.persist(new ListingEvent(id, user.getId(), user.getRole(), eventAction,
                before, snapshot(listing), null));
    }

    private Card card(Listing listing, String cover) {
        boolean active = "ACTIVE".equals(listing.getLifecycleStatus());
        boolean reserved = "RESERVED".equals(listing.getLifecycleStatus());
        boolean withdrawn = "WITHDRAWN".equals(listing.getLifecycleStatus());
        boolean gift = "GIFT".equals(listing.getTransferType());
        String price = gift ? "免费赠送" : "¥" + listing.getPrice().setScale(2).toPlainString();
        return new Card(listing.getId(),
                listing.getTitle() == null || listing.getTitle().isBlank() ? "未命名资料" : listing.getTitle(),
                MarketOptions.label(MarketOptions.CATEGORIES, listing.getCategory()),
                displayStatus(listing), price,
                listing.getPublishedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                        .format(DISPLAY_TIME),
                cover, listing.getRowVersion(), listing.isAdminBlocked(), listing.isEditable(),
                active && !listing.isAdminBlocked(), reserved && !listing.isAdminBlocked(),
                (active || reserved) && !listing.isAdminBlocked(),
                (active || reserved) && !listing.isAdminBlocked(),
                withdrawn && !listing.isAdminBlocked());
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

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ManageFailure(HttpStatus.UNAUTHORIZED, "登录账号不存在，请重新登录。"));
    }

    private Listing locked(long id) {
        return listings.findByIdForUpdate(id)
                .orElseThrow(() -> new ManageFailure(HttpStatus.NOT_FOUND, "资料不存在。"));
    }

    private void requireOwner(Listing listing, AppUser user) {
        if (!listing.isOwner(user.getId())) {
            throw new ManageFailure(HttpStatus.FORBIDDEN, "只能管理自己发布的资料。");
        }
    }

    private void requireEditable(Listing listing) {
        if (!listing.isEditable()) {
            throw new ManageFailure("已售出、已送出或管理员限制的资料不能编辑。");
        }
    }

    private void requireVersion(Listing listing, long expectedVersion) {
        if (listing.getRowVersion() != expectedVersion) {
            throw new ManageFailure("资料已发生变化，请刷新页面后重新操作。");
        }
    }

    private void lockEnabledOptions(PublishForm form) {
        form.getScopes().stream().map(PublishForm.Scope::getMajorId).filter(id -> id > 0).distinct().sorted()
                .forEach(id -> lockEnabled("major", id));
        form.getTags().stream().distinct().sorted().forEach(id -> lockEnabled("tag", id));
    }

    private void lockEnabled(String table, Long id) {
        var rows = jdbc.queryForList("select enabled from " + table + " where id = ? for update", Boolean.class, id);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst())) {
            throw new ManageFailure("所选专业或标签已停用，请重新选择。");
        }
    }

    private void replaceScopes(Listing listing, PublishForm form) {
        em.createNativeQuery("delete from listing_scope where listing_id = :id")
                .setParameter("id", listing.getId()).executeUpdate();
        form.getScopes().stream().distinct().forEach(scope ->
                em.persist(new ListingScope(listing, scope.getMajorId(), scope.getSemester())));
    }

    private void replaceTags(Listing listing, PublishForm form) {
        em.createNativeQuery("delete from listing_tag where listing_id = :id")
                .setParameter("id", listing.getId()).executeUpdate();
        form.getTags().stream().distinct().forEach(tag -> em.createNativeQuery(
                        "insert into listing_tag (listing_id, tag_id) values (:listing, :tag)")
                .setParameter("listing", listing.getId()).setParameter("tag", tag).executeUpdate());
    }

    private void replaceContact(Listing listing, PublishForm form) {
        em.createNativeQuery("delete from listing_contact where listing_id = :id")
                .setParameter("id", listing.getId()).executeUpdate();
        em.persist(new ListingContact(listing.getId(), form.getContactType(), form.getContactValue()));
    }

    private void replaceImages(Listing listing, PublishForm form) {
        var selected = form.getPhotos().stream().filter(photo -> !photo.isEmpty()).toList();
        if (selected.isEmpty()) return;
        var prepared = selected.stream().map(storage::prepare).toList();
        var oldKeys = images.findByListingIdOrderBySortOrderAsc(listing.getId()).stream()
                .map(ListingImage::getStorageKey).toList();
        var newKeys = new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    newKeys.forEach(storage::discard);
                } else if (status == STATUS_COMMITTED) {
                    oldKeys.forEach(storage::discard);
                }
            }
        });
        em.createNativeQuery("delete from listing_image where listing_id = :id")
                .setParameter("id", listing.getId()).executeUpdate();
        for (int i = 0; i < prepared.size(); i++) {
            var image = prepared.get(i);
            String key = storage.save(image);
            newKeys.add(key);
            em.persist(new ListingImage(listing.getId(), key, image.mime(), image.bytes().length, i + 1));
        }
    }

    private String snapshot(Listing listing) {
        String completed = listing.getCompletedAt() == null
                ? "null" : "\"" + listing.getCompletedAt() + "\"";
        return "{\"lifecycleStatus\":\"" + listing.getLifecycleStatus()
                + "\",\"transferType\":\"" + listing.getTransferType()
                + "\",\"price\":\"" + listing.getPrice().setScale(2).toPlainString()
                + "\",\"adminBlocked\":" + listing.isAdminBlocked()
                + ",\"completedAt\":" + completed
                + ",\"rowVersion\":" + listing.getRowVersion() + "}";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
