package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.admin.AdminFailure;
import cn.edu.hust.bookloop.admin.AdminService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admin/reports")
    public String reports(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("result", adminService.pendingReports(principal.getName(), page));
        model.addAttribute("previousUrl", "/admin/reports?page=" + Math.max(0, page - 1));
        model.addAttribute("nextUrl", "/admin/reports?page=" + (page + 1));
        return "admin-reports";
    }

    @PostMapping("/admin/reports/{id}/handle")
    public String handleReport(@PathVariable long id, @RequestParam String decision,
                               @RequestParam(defaultValue = "") String resolutionNote,
                               @RequestParam(defaultValue = "false") boolean blockListing,
                               @RequestParam(defaultValue = "") String adminReason,
                               @RequestParam(defaultValue = "-1") long expectedVersion,
                               Principal principal, RedirectAttributes redirect) {
        try {
            adminService.handleReport(id, decision, resolutionNote, blockListing, adminReason,
                    expectedVersion, principal.getName());
            redirect.addFlashAttribute("success", "举报已处理。");
        } catch (AdminFailure exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/reports";
    }

    @GetMapping("/admin/listings")
    public String listings(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("result", adminService.listings(principal.getName(), page));
        model.addAttribute("previousUrl", "/admin/listings?page=" + Math.max(0, page - 1));
        model.addAttribute("nextUrl", "/admin/listings?page=" + (page + 1));
        return "admin-listings";
    }

    @PostMapping("/admin/listings/{id}/block")
    public String block(@PathVariable long id, @RequestParam(defaultValue = "") String adminReason,
                        @RequestParam(defaultValue = "-1") long expectedVersion,
                        Principal principal, RedirectAttributes redirect) {
        try {
            adminService.blockListing(id, adminReason, expectedVersion, principal.getName());
            redirect.addFlashAttribute("success", "资料已被管理员下架。");
        } catch (AdminFailure exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/listings";
    }

    @PostMapping("/admin/listings/{id}/release")
    public String release(@PathVariable long id, @RequestParam(defaultValue = "-1") long expectedVersion,
                          Principal principal, RedirectAttributes redirect) {
        try {
            adminService.releaseListing(id, expectedVersion, principal.getName());
            redirect.addFlashAttribute("success", "管理员限制已解除。");
        } catch (AdminFailure exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/listings";
    }
}
