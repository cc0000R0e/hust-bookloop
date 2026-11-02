package cn.edu.hust.bookloop.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record MarketFilter(String q, Long major, String semester, String category, List<Long> tags,
                           String mode, BigDecimal minPrice, BigDecimal maxPrice, String condition,
                           String sort, int page, String college) {
    public MarketFilter(String q, Long major, String semester, String category, List<Long> tags,
                        String mode, BigDecimal minPrice, BigDecimal maxPrice, String condition, String sort, int page) {
        this(q, major, semester, category, tags, mode, minPrice, maxPrice, condition, sort, page, "");
    }
    public MarketFilter {
        college = CollegeNames.normalize(college);
        if (college.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "学院无效");
        q = q == null ? "" : q.strip();
        tags = tags == null ? List.of() : tags.stream().distinct().toList();
        if (q.length() > 100 || major != null && major < 0 || page < 0 || page > 100000
                || tags.size() > 50 || tags.stream().anyMatch(id -> id == null || id <= 0)
                || !validOption(semester, MarketOptions.SEMESTERS.keySet())
                || !validOption(category, MarketOptions.CATEGORIES.keySet())
                || !validOption(condition, MarketOptions.CONDITIONS.keySet())
                || !Set.of("all", "sale", "free").contains(mode)
                || !Set.of("new", "low", "high").contains(sort)
                || !validPrice(minPrice) || !validPrice(maxPrice)
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "筛选条件无效");
        }
    }
    private static boolean validOption(String value, Set<String> choices) {
        return value != null && (value.isEmpty() || choices.contains(value));
    }
    private static boolean validPrice(BigDecimal price) {
        return price == null || (price.signum() >= 0 && price.compareTo(new BigDecimal("9999.99")) <= 0
                && price.stripTrailingZeros().scale() <= 2);
    }
}
