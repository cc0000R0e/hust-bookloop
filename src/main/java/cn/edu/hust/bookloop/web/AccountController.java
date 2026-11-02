package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.account.AccountService;
import cn.edu.hust.bookloop.account.UserRepository;
import java.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccountController {
    private final AccountService accounts;
    private final UserRepository users;
    private final cn.edu.hust.bookloop.publish.PublishOptions options;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public AccountController(AccountService accounts, UserRepository users, cn.edu.hust.bookloop.publish.PublishOptions options, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.options = options; this.jdbc = jdbc;
        this.accounts = accounts; this.users = users;
    }
    @GetMapping("/login")
    public String login(Principal principal) { return principal == null ? "login" : "redirect:/account"; }
    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("username", "");
        model.addAttribute("majorId", null);
        model.addAttribute("college", "");
        model.addAttribute("majors", options.majors());
        return "register";
    }
    @PostMapping("/register")
    public String register(@RequestParam(defaultValue = "") String username,
                           @RequestParam(defaultValue = "") String password,
                           @RequestParam(defaultValue = "") String confirmation, @RequestParam(required = false) Long majorId, @RequestParam(defaultValue = "") String college, Model model) {
        try {
            accounts.register(username, password, confirmation, majorId, college);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            // Concurrent registrations are also protected by the database unique key.
            model.addAttribute("error", "账号已被使用，请更换账号后重试。");
        }
        model.addAttribute("username", username);
        model.addAttribute("majorId", majorId);
        model.addAttribute("college", college);
        model.addAttribute("majors", options.majors());
        return "register"; // Never return passwords in the model or HTML.
    }
    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        var user = users.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("username", user.getUsername());
        model.addAttribute("role", user.getRole());
        model.addAttribute("majorId", user.getMajorId());
        model.addAttribute("college", user.getCollege());
        model.addAttribute("majors", options.majors());
        var majorNames = user.getMajorId() == null ? java.util.List.<String>of()
                : jdbc.queryForList("select name from major where id = ?", String.class, user.getMajorId());
        model.addAttribute("currentMajorName", majorNames.isEmpty() ? "未设置" : majorNames.getFirst());
        model.addAttribute("majorDisabled", user.getMajorId() != null && options.majors().stream().noneMatch(m -> m.id().equals(user.getMajorId())));
        model.addAttribute("contactType", user.getDefaultContactType());
        model.addAttribute("contactValue", user.getDefaultContactValue());
        return "account";
    }

    @PostMapping("/account/major")
    public String major(Principal principal, @RequestParam(required = false) Long majorId, @RequestParam(defaultValue = "") String college, Model model,
                        org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try {
            accounts.updateMajor(principal.getName(), majorId, college);
            redirect.addFlashAttribute("success", "个人专业已保存，不影响已发布资料的适用范围。");
            return "redirect:/account";
        } catch (IllegalArgumentException exception) {
            account(principal, model);
            model.addAttribute("error", exception.getMessage());
            return "account";
        }
    }

    @PostMapping("/account/contact")
    public String contact(Principal principal, @RequestParam(defaultValue = "") String type,
                          @RequestParam(defaultValue = "") String value, Model model,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try {
            accounts.updateContact(principal.getName(), type, value);
            redirect.addFlashAttribute("success", "默认联系方式已保存。");
            return "redirect:/account";
        } catch (IllegalArgumentException exception) {
            account(principal, model);
            model.addAttribute("contactType", type);
            model.addAttribute("contactValue", value);
            model.addAttribute("error", exception.getMessage());
            return "account";
        }
    }

    @PostMapping("/account/password")
    public String password(Principal principal, @RequestParam(defaultValue = "") String oldPassword,
                           @RequestParam(defaultValue = "") String password,
                           @RequestParam(defaultValue = "") String confirmation, Model model,
                           jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response) {
        try {
            accounts.changePassword(principal.getName(), oldPassword, password, confirmation);
            new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                .logout(request, response,
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            return "redirect:/login?passwordChanged";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return account(principal, model);
        }
    }
}
