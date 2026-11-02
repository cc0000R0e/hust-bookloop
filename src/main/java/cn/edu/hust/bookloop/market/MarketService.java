package cn.edu.hust.bookloop.market;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MarketService {
    private final ListingRepository listings;
    private final cn.edu.hust.bookloop.image.ListingImageRepository images;
    private final EntityManager entityManager;

    public MarketService(ListingRepository listings, EntityManager entityManager, cn.edu.hust.bookloop.image.ListingImageRepository images) {
        this.listings = listings; this.images = images;
        this.entityManager = entityManager;
    }

    public record Option(Long id, String name, String colleges) {
        public Option(Long id, String name) { this(id, name, ""); }
    }
    public record Card(Long id, String title, String author, String course, String category,
                       String condition, BigDecimal price, boolean gift, List<String> scopes, List<String> tags, String coverUrl) {}
    public record Result(Page<Card> page, List<Option> majors, List<Option> tags) {}

    public Result search(MarketFilter filter) {
        // Disabled dictionary entries remain searchable for historical listings.
        var majors = entityManager.createQuery("select m from Major m order by m.name, m.id", Major.class)
                .getResultList().stream().map(m -> new Option(m.getId(), m.getName(), m.getColleges())).toList();
        var tags = entityManager.createQuery("select t from BookTag t order by t.name, t.id", BookTag.class)
                .getResultList().stream().map(t -> new Option(t.getId(), t.getName())).toList();
        if (filter.major() != null && filter.major() != 0 && majors.stream().noneMatch(m -> m.id().equals(filter.major()))
                || !tags.stream().map(Option::id).toList().containsAll(filter.tags())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "专业或标签不存在");
        }
        if (!filter.college().isEmpty() && majors.stream().noneMatch(m ->
                (filter.major() == null || filter.major() == 0 || m.id().equals(filter.major()))
                && CollegeNames.split(m.colleges()).contains(filter.college()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "学院不存在或与专业不匹配");
        }
        Map<Long, String> majorNames = majors.stream().collect(Collectors.toMap(Option::id, Option::name));
        Sort sort = switch (filter.sort()) {
            case "low" -> Sort.by("price").ascending();
            case "high" -> Sort.by("price").descending();
            default -> Sort.by("publishedAt").descending();
        };
        // Stable ordering prevents ties from shuffling between pages.
        sort = sort.and(Sort.by("id").descending());
        var entries = listings.findAll(specification(filter, majors), PageRequest.of(filter.page(), 20, sort));
        var covers = new java.util.HashMap<Long, String>();
        if (!entries.isEmpty()) images.findByListingIdInOrderBySortOrderAsc(entries.stream().map(Listing::getId).toList())
                .forEach(image -> covers.putIfAbsent(image.getListingId(), "/books/" + image.getListingId() + "/images/" + image.getId()));
        var page = entries.map(book -> new Card(book.getId(), displayTitle(book.getTitle()), book.getAuthor(), book.getCourseName(),
                        MarketOptions.label(MarketOptions.CATEGORIES, book.getCategory()),
                        MarketOptions.label(MarketOptions.CONDITIONS, book.getConditionCode()), book.getPrice(),
                        "GIFT".equals(book.getTransferType()),
                        book.getScopes().stream().map(scope ->
                            ("ALL".equals(scope.getScopeType()) ? "不限专业" : "COLLEGE".equals(scope.getScopeType()) ? scope.getCollege() + "（全院）" : majorNames.get(scope.getMajorId()))
                            + " · " + MarketOptions.SEMESTERS.get(scope.getSemesterCode()))
                            .distinct().sorted().toList(),
                        book.getTags().stream().map(BookTag::getName).sorted(Comparator.naturalOrder()).toList(), covers.get(book.getId())));
        return new Result(page, majors, tags);
    }

    private String displayTitle(String title) {
        return title == null || title.isBlank() ? "未命名资料" : title;
    }

    private Specification<Listing> specification(MarketFilter filter, List<Option> majors) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("lifecycleStatus"), "ACTIVE"));
            predicates.add(cb.isFalse(root.get("adminBlocked")));
            if (!filter.q().isEmpty()) {
                // Treat SQL LIKE wildcard characters as literal user input.
                String pattern = "%" + filter.q().toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                var tagQuery = query.subquery(Long.class);
                var tagged = tagQuery.from(Listing.class);
                var tag = tagged.join("tags");
                tagQuery.select(tagged.get("id")).where(cb.equal(tagged.get("id"), root.get("id")),
                        cb.like(cb.lower(tag.get("name")), pattern, '!'));
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '!'),
                        cb.like(cb.lower(root.get("author")), pattern, '!'),
                        cb.like(cb.lower(root.get("courseName")), pattern, '!'), cb.exists(tagQuery)));
            }
            if (filter.major() != null || !filter.college().isEmpty() || !filter.semester().isEmpty()) {
                var scopeQuery = query.subquery(Long.class);
                var scope = scopeQuery.from(ListingScope.class);
                var scopePredicates = new ArrayList<Predicate>();
                scopePredicates.add(cb.equal(scope.get("listing").get("id"), root.get("id")));
                var all = cb.equal(scope.get("scopeType"), "ALL");
                if (filter.major() != null && filter.major() > 0) {
                    var colleges = majors.stream().filter(m -> m.id().equals(filter.major()))
                            .flatMap(m -> CollegeNames.split(m.colleges()).stream())
                            .filter(c -> filter.college().isEmpty() || c.equals(filter.college())).toList();
                    var collegeScope = colleges.isEmpty() ? cb.disjunction()
                            : cb.and(cb.equal(scope.get("scopeType"), "COLLEGE"), scope.get("college").in(colleges));
                    scopePredicates.add(cb.or(all, cb.equal(scope.get("majorId"), filter.major()), collegeScope));
                } else if (!filter.college().isEmpty()) {
                    var majorIds = majors.stream().filter(m -> CollegeNames.split(m.colleges()).contains(filter.college()))
                            .map(Option::id).toList();
                    var specific = majorIds.isEmpty() ? cb.disjunction() : scope.get("majorId").in(majorIds);
                    scopePredicates.add(cb.or(all, specific, cb.and(cb.equal(scope.get("scopeType"), "COLLEGE"),
                            cb.equal(scope.get("college"), filter.college()))));
                } else if (filter.major() != null) {
                    scopePredicates.add(all);
                }
                if (!filter.semester().isEmpty()) {
                    scopePredicates.add(cb.equal(scope.get("semesterCode"), filter.semester()));
                }
                scopeQuery.select(scope.get("id")).where(scopePredicates.toArray(Predicate[]::new));
                predicates.add(cb.exists(scopeQuery));
            }
            if (!filter.tags().isEmpty()) {
                var tagQuery = query.subquery(Long.class);
                var tagged = tagQuery.from(Listing.class);
                tagQuery.select(tagged.get("id")).where(cb.equal(tagged.get("id"), root.get("id")),
                        tagged.join("tags").get("id").in(filter.tags()));
                predicates.add(cb.exists(tagQuery));
            }
            if (!filter.category().isEmpty()) predicates.add(cb.equal(root.get("category"), filter.category()));
            if (!filter.condition().isEmpty()) predicates.add(cb.equal(root.get("conditionCode"), filter.condition()));
            if (!filter.mode().equals("all")) {
                predicates.add(cb.equal(root.get("transferType"), filter.mode().equals("free") ? "GIFT" : "SALE"));
            }
            if (filter.minPrice() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
            if (filter.maxPrice() != null) predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
