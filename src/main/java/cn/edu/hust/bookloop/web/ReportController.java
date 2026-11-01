package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.report.ReportFailure;
import cn.edu.hust.bookloop.report.ReportService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/books/{id}/report")
    public String reportForm(@PathVariable long id,
                             @RequestParam(required = false) String returnTo) {
        return "redirect:" + backToDetail(id, MarketReturnUrl.safe(returnTo));
    }

    @PostMapping("/books/{id}/reports")
    public String submit(@PathVariable long id, @RequestParam(defaultValue = "") String reasonCode,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam(required = false) String returnTo,
                         Principal principal, RedirectAttributes redirect) {
        try {
            reportService.submit(id, reasonCode, description, principal.getName());
            redirect.addFlashAttribute("reportSuccess", "举报已提交，管理员会尽快处理。");
        } catch (ReportFailure exception) {
            redirect.addFlashAttribute("reportError", exception.getMessage());
        }
        return "redirect:" + backToDetail(id, MarketReturnUrl.safe(returnTo));
    }

    private String backToDetail(long id, String returnTo) {
        return UriComponentsBuilder.fromPath("/books/" + id)
                .queryParam("returnTo", returnTo)
                .fragment("report")
                .build().encode().toUriString();
    }
}
