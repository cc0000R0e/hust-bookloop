package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.admin.AdminCatalogService;
import cn.edu.hust.bookloop.admin.AdminFailure;
import java.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminCatalogController {
    private final AdminCatalogService service;
    public AdminCatalogController(AdminCatalogService service) { this.service = service; }

    @ExceptionHandler(AdminFailure.class)
    public org.springframework.http.ResponseEntity<String> failure(AdminFailure exception) {
        return org.springframework.http.ResponseEntity.status(exception.status())
                .contentType(org.springframework.http.MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(exception.getMessage());
    }

    @GetMapping("/admin/options/{kind}")
    public String options(@PathVariable String kind, @RequestParam(defaultValue = "0") int page,
                          Principal principal, Model model) {
        var result = service.options(principal.getName(), kind, page);
        model.addAttribute("result", result);
        model.addAttribute("kind", kind);
        model.addAttribute("label", "majors".equals(kind) ? "专业" : "标签");
        model.addAttribute("maxLength", "majors".equals(kind) ? 40 : 30);
        return "admin-options";
    }

    @PostMapping("/admin/options/{kind}")
    public String save(@PathVariable String kind, @RequestParam(required = false) Long id,
                       @RequestParam(defaultValue = "") String name,
                       @RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam(defaultValue = "0") int page,
                       Principal principal, RedirectAttributes redirect) {
        try {
            service.save(principal.getName(), kind, id, name, enabled);
            redirect.addFlashAttribute("success", "选项已保存。");
        } catch (AdminFailure exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "名称已存在或不符合约束，请修改后重试。");
        }
        redirect.addAttribute("page", Math.max(0, Math.min(page, 100_000)));
        return "redirect:/admin/options/" + ("majors".equals(kind) ? "majors" : "tags");
    }

    @GetMapping("/admin/reports/history")
    public String history(@RequestParam(defaultValue = "all") String status,
                          @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("result", service.history(principal.getName(), status, page));
        model.addAttribute("status", status);
        return "admin-history";
    }

    @GetMapping("/admin/events")
    public String events(@RequestParam(required = false) Long listingId,
                         @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("result", service.events(principal.getName(), listingId, page));
        model.addAttribute("listingId", listingId);
        return "admin-events";
    }
}
