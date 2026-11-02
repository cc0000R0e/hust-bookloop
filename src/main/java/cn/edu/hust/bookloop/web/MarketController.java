package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.market.MarketFilter;
import cn.edu.hust.bookloop.market.MarketOptions;
import cn.edu.hust.bookloop.market.MarketService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class MarketController {
    private final MarketService market;
    private final cn.edu.hust.bookloop.account.UserRepository users;
    public MarketController(MarketService market, cn.edu.hust.bookloop.account.UserRepository users) { this.market = market; this.users = users; }

    @GetMapping({"/", "/market"})
    public String market(@RequestParam(defaultValue = "") String q,
                         @RequestParam(required = false) Long major,
                         @RequestParam(defaultValue = "") String college,
                         @RequestParam(defaultValue = "") String semester,
                         @RequestParam(defaultValue = "") String category,
                         @RequestParam(required = false) List<Long> tags,
                         @RequestParam(defaultValue = "all") String mode,
                         @RequestParam(required = false) BigDecimal minPrice,
                         @RequestParam(required = false) BigDecimal maxPrice,
                         @RequestParam(defaultValue = "") String condition,
                         @RequestParam(defaultValue = "new") String sort,
                         @RequestParam(defaultValue = "0") int page,
                         Principal principal, HttpServletRequest request, Model model) {
        var filter = new MarketFilter(q, major, semester, category, tags, mode, minPrice, maxPrice, condition, sort, page, college);
        var result = market.search(filter);
        String marketPath = "/".equals(request.getServletPath()) ? "/" : "/market";
        model.addAttribute("filter", filter);
        model.addAttribute("result", result.page());
        model.addAttribute("majors", result.majors());
        model.addAttribute("tags", result.tags());
        model.addAttribute("semesters", MarketOptions.SEMESTERS);
        model.addAttribute("categories", MarketOptions.CATEGORIES);
        model.addAttribute("conditions", MarketOptions.CONDITIONS);
        model.addAttribute("username", principal == null ? null : principal.getName());
        var personal = principal == null ? null : users.findByUsername(principal.getName()).orElse(null);
        Long personalMajor = personal == null ? null : personal.getMajorId();
        String personalCollege = personal == null ? "" : personal.getCollege();
        model.addAttribute("personalMajor", personalMajor);
        model.addAttribute("hasPersonalScope", personalMajor != null || !personalCollege.isEmpty());
        if (personalMajor != null || !personalCollege.isEmpty()) {
            var personalFilter = new MarketFilter(q, personalMajor, semester, category, tags, mode, minPrice, maxPrice, condition, sort, 0, personalCollege);
            model.addAttribute("personalMajorUrl", pageUrl(personalFilter, 0, marketPath));
        }
        model.addAttribute("marketPath", marketPath);
        model.addAttribute("marketUrl", pageUrl(filter, page, marketPath));
        model.addAttribute("previousUrl", pageUrl(filter, Math.max(0, page - 1), marketPath));
        model.addAttribute("nextUrl", pageUrl(filter, page + 1, marketPath));
        return "market";
    }

    private String pageUrl(MarketFilter filter, int page, String marketPath) {
        var url = UriComponentsBuilder.fromPath(marketPath)
                .queryParam("q", filter.q()).queryParam("semester", filter.semester())
                .queryParam("category", filter.category()).queryParam("mode", filter.mode())
                .queryParam("condition", filter.condition()).queryParam("sort", filter.sort()).queryParam("page", page);
        if (!filter.college().isEmpty()) url.queryParam("college", filter.college());
        if (filter.major() != null) url.queryParam("major", filter.major());
        if (filter.minPrice() != null) url.queryParam("minPrice", filter.minPrice());
        if (filter.maxPrice() != null) url.queryParam("maxPrice", filter.maxPrice());
        filter.tags().forEach(tag -> url.queryParam("tags", tag));
        return url.build().encode().toUriString();
    }
}
