package cn.edu.hust.bookloop.book;

import cn.edu.hust.bookloop.market.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookService {
    private final ListingRepository listings;
    private final ListingContactRepository contacts;
    private final EntityManager entityManager;
    private static final Map<String, String> NOTES = Map.of("NONE", "无笔记", "FEW", "少量笔记", "MANY", "较多笔记");
    private static final Map<String, String> CONTACT_TYPES = Map.of("WECHAT", "微信", "QQ", "QQ", "PHONE", "手机号");

    public BookService(ListingRepository listings, ListingContactRepository contacts, EntityManager entityManager) {
        this.listings = listings;
        this.contacts = contacts;
        this.entityManager = entityManager;
    }

    public record Contact(String type, String value) {}
    public record Detail(Long id, String title, String author, String publisher, String edition,
                         String category, String course, String condition, String notes, String description,
                         BigDecimal price, boolean gift, String status, boolean reserved, boolean restricted,
                         String seller, String publishedAt, List<String> scopes, List<String> tags,
                         boolean signedIn, List<Contact> contacts) {}

    public Detail find(long id, Authentication authentication) {
        var book = listings.findById(id).orElseThrow(() -> new BookAccessException(HttpStatus.NOT_FOUND));
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        String seller = entityManager.createQuery("select u.username from AppUser u where u.id = :id", String.class)
                .setParameter("id", book.getOwnerId()).getResultStream().findFirst().orElse("未填写");
        boolean owner = signedIn && seller.equals(authentication.getName());
        boolean admin = signedIn && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean publicBook = !book.isAdminBlocked()
                && (book.getLifecycleStatus().equals("ACTIVE") || book.getLifecycleStatus().equals("RESERVED"));
        if (!publicBook && !owner && !admin) throw new BookAccessException(HttpStatus.GONE);

        // Contact data is queried only after authentication and visibility checks.
        var visibleContacts = signedIn ? contacts.findByListingIdOrderByIdAsc(id).stream()
                .map(c -> new Contact(CONTACT_TYPES.get(c.getType()), c.getValue())).toList() : List.<Contact>of();
        var scopes = book.getScopes().stream().map(scope -> {
            String major = "ALL".equals(scope.getScopeType()) ? "不限专业" : entityManager
                    .createQuery("select m.name from Major m where m.id = :id", String.class)
                    .setParameter("id", scope.getMajorId()).getResultStream().findFirst().orElse("未填写");
            return major + " · " + MarketOptions.SEMESTERS.get(scope.getSemesterCode());
        }).distinct().sorted().toList();
        boolean gift = "GIFT".equals(book.getTransferType());
        String status = book.isAdminBlocked() ? "已下架（管理员限制）" : switch (book.getLifecycleStatus()) {
            case "ACTIVE" -> gift ? "待赠送" : "在售";
            case "RESERVED" -> "已预订";
            case "COMPLETED" -> gift ? "已送出" : "已售出";
            default -> "已下架";
        };
        String published = book.getPublishedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return new Detail(book.getId(), book.getTitle(), optional(book.getAuthor()), optional(book.getPublisher()),
                optional(book.getEditionNote()), MarketOptions.CATEGORIES.get(book.getCategory()), optional(book.getCourseName()),
                MarketOptions.CONDITIONS.get(book.getConditionCode()), NOTES.get(book.getNoteLevel()), book.getDescription(),
                book.getPrice(), gift, status, "RESERVED".equals(book.getLifecycleStatus()), !publicBook,
                seller, published, scopes, book.getTags().stream().map(BookTag::getName).sorted().toList(), signedIn, visibleContacts);
    }

    private String optional(String value) { return value == null || value.isBlank() ? "未填写" : value; }
}
