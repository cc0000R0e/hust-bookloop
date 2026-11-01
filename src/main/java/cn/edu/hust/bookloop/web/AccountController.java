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
    public AccountController(AccountService accounts, UserRepository users) {
        this.accounts = accounts; this.users = users;
    }
    @GetMapping("/login")
    public String login(Principal principal) { return principal == null ? "login" : "redirect:/account"; }
    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("username", "");
        return "register";
    }
    @PostMapping("/register")
    public String register(@RequestParam(defaultValue = "") String username,
                           @RequestParam(defaultValue = "") String password,
                           @RequestParam(defaultValue = "") String confirmation, Model model) {
        try {
            accounts.register(username, password, confirmation);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            // Concurrent registrations are also protected by the database unique key.
            model.addAttribute("error", "账号已被使用，请更换账号后重试。");
        }
        model.addAttribute("username", username);
        return "register"; // Never return passwords in the model or HTML.
    }
    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        var user = users.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("username", user.getUsername());
        model.addAttribute("role", user.getRole());
        model.addAttribute("contactType", user.getDefaultContactType());
        model.addAttribute("contactValue", user.getDefaultContactValue());
        return "account";
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
