package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.market.MarketOptions;
import cn.edu.hust.bookloop.publish.PublishForm;
import cn.edu.hust.bookloop.publish.PublishOptions;
import jakarta.validation.Valid;
import cn.edu.hust.bookloop.publish.PublishValidator;
import cn.edu.hust.bookloop.publish.PublishService;
import cn.edu.hust.bookloop.publish.PublishFailure;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
public class PublishController {
    private final UserRepository users;
    private final PublishOptions options;
    private final PublishValidator validator;
    private final PublishService publisher;
    public PublishController(UserRepository users, PublishOptions options, PublishValidator validator, PublishService publisher) {
        this.users = users; this.options = options; this.validator = validator; this.publisher = publisher;
    }

    @PostMapping("/books")
    public String publish(@Valid @ModelAttribute("form") PublishForm form, BindingResult errors,
                          Principal principal, Model model) {
        validator.validate(form, errors);
        if (!errors.hasErrors()) {
            try {
                long id = publisher.publish(form, principal.getName());
                return "redirect:/books/" + id;
            } catch (PublishFailure exception) {
                errors.reject("publish", exception.getMessage());
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | jakarta.persistence.PersistenceException exception) {
                errors.reject("publish", "保存失败，本次没有发布成功，请重新选择照片后重试。");
            }
        }
        options(model);
        model.addAttribute("reselectPhotos", true);
        return "publish";
    }

    @InitBinder("form")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("title", "category", "author", "publisher", "editionNote", "courseName", "description",
                "conditionCode", "noteLevel", "transferType", "price", "contactType", "contactValue", "scopes[*].majorId",
                "scopes[*].semester", "scopes[*].college", "tags", "photos");
        binder.setAutoGrowCollectionLimit(50);
    }

    @GetMapping("/books/new")
    public String form(Principal principal, Model model) {
        var form = new PublishForm();
        var user = users.findByUsername(principal.getName()).orElseThrow();
        form.setContactType(user.getDefaultContactType() == null ? "" : user.getDefaultContactType());
        form.setContactValue(user.getDefaultContactValue() == null ? "" : user.getDefaultContactValue());
        if (user.getMajorId() != null && options.majors().stream().anyMatch(m -> m.id().equals(user.getMajorId()))) {
            form.getScopes().getFirst().setMajorId(user.getMajorId());
        }
        if (!user.getCollege().isEmpty() && options.majors().stream().anyMatch(m ->
                (form.getScopes().getFirst().getMajorId() == 0 || m.id().equals(form.getScopes().getFirst().getMajorId()))
                && cn.edu.hust.bookloop.market.CollegeNames.split(m.colleges()).contains(user.getCollege()))) {
            form.getScopes().getFirst().setCollege(user.getCollege());
        }
        model.addAttribute("form", form);
        options(model);
        return "publish";
    }

    @PostMapping("/books/preview")
    public String preview(@Valid @ModelAttribute("form") PublishForm form, BindingResult errors, Model model) {
        validator.validate(form, errors);
        var photos = form.getPhotos().stream().filter(p -> !p.isEmpty()).toList();
        options(model);
        if (!errors.hasErrors()) {
            model.addAttribute("previewReady", true);
            model.addAttribute("photoCount", photos.size());
        }
        model.addAttribute("reselectPhotos", true);
        // This endpoint deliberately validates only; no files or business records are persisted.
        return "publish";
    }

    private void options(Model model) {
        model.addAttribute("majors", options.majors());
        model.addAttribute("tags", options.tags());
        model.addAttribute("semesters", MarketOptions.SEMESTERS);
        model.addAttribute("categories", MarketOptions.CATEGORIES);
        model.addAttribute("conditions", MarketOptions.CONDITIONS);
        model.addAttribute("notes", Map.of("NONE", "无笔记", "FEW", "少量笔记", "MANY", "较多笔记"));
    }
}
