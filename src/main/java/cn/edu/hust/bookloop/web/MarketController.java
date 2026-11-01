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
    public MarketController(MarketService market) { this.market = market; }

    @GetMapping({"/", "/market"})
    public String market(@RequestParam(defaultValue = "") String q,
                         @RequestParam(required = false) Long major,
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
        var filter = new MarketFilter(q, major, semester, category, tags, mode, minPrice, maxPrice, condition, sort, page);
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
        if (filter.major() != null) url.queryParam("major", filter.major());
        if (filter.minPrice() != null) url.queryParam("minPrice", filter.minPrice());
        if (filter.maxPrice() != null) url.queryParam("maxPrice", filter.maxPrice());
        filter.tags().forEach(tag -> url.queryParam("tags", tag));
        return url.build().encode().toUriString();
    }
}
