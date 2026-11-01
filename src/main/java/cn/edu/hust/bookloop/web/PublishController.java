package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.market.MarketOptions;
import cn.edu.hust.bookloop.publish.PublishForm;
import cn.edu.hust.bookloop.publish.PublishOptions;
import jakarta.validation.Valid;
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
    public PublishController(UserRepository users, PublishOptions options) { this.users = users; this.options = options; }

    @InitBinder("form")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("title", "category", "author", "publisher", "editionNote", "courseName", "description",
                "conditionCode", "noteLevel", "transferType", "price", "contactType", "contactValue", "scopes[*].majorId",
                "scopes[*].semester", "tags", "photos");
        binder.setAutoGrowCollectionLimit(50);
    }

    @GetMapping("/books/new")
    public String form(Principal principal, Model model) {
        var form = new PublishForm();
        var user = users.findByUsername(principal.getName()).orElseThrow();
        form.setContactType(user.getDefaultContactType() == null ? "" : user.getDefaultContactType());
        form.setContactValue(user.getDefaultContactValue() == null ? "" : user.getDefaultContactValue());
        model.addAttribute("form", form);
        options(model);
        return "publish";
    }

    @PostMapping("/books/preview")
    public String preview(@Valid @ModelAttribute("form") PublishForm form, BindingResult errors, Model model) {
        var majors = options.majors();
        var tags = options.tags();
        if (!MarketOptions.CATEGORIES.containsKey(form.getCategory())) errors.rejectValue("category", "invalid", "请选择资料类别。");
        if (!MarketOptions.CONDITIONS.containsKey(form.getConditionCode())) errors.rejectValue("conditionCode", "invalid", "请选择资料成色。");
        if (!Set.of("NONE", "FEW", "MANY").contains(form.getNoteLevel())) errors.rejectValue("noteLevel", "invalid", "请选择笔记程度。");
        if ("GIFT".equals(form.getTransferType())) form.setPrice(BigDecimal.ZERO);
        else if (!"SALE".equals(form.getTransferType())) errors.rejectValue("transferType", "invalid", "请选择出售或免费赠送。");
        else if (form.getPrice() == null || form.getPrice().signum() <= 0
                || form.getPrice().compareTo(new BigDecimal("9999.99")) > 0 || form.getPrice().stripTrailingZeros().scale() > 2) {
            errors.rejectValue("price", "invalid", "出售价格须大于0、不超过9999.99元，最多两位小数。");
        }
        if (!Set.of("WECHAT", "QQ", "PHONE").contains(form.getContactType())) errors.rejectValue("contactType", "invalid", "请选择联系方式类型。");
        var seen = new HashSet<String>();
        for (var scope : form.getScopes()) {
            if (scope == null || scope.getMajorId() == null || scope.getSemester() == null
                    || !MarketOptions.SEMESTERS.containsKey(scope.getSemester())
                    || scope.getMajorId() != 0 && majors.stream().noneMatch(m -> m.id().equals(scope.getMajorId()))) {
                errors.rejectValue("scopes", "invalid", "请选择有效的专业和学期，已停用的专业不可新增。");
            } else if (!seen.add(scope.getMajorId() + ":" + scope.getSemester())) {
                errors.rejectValue("scopes", "duplicate", "适用范围不能重复。");
            }
        }
        if (!tags.stream().map(PublishOptions.Option::id).toList().containsAll(form.getTags())) {
            errors.rejectValue("tags", "invalid", "请选择有效标签，已停用的标签不可新增。");
        }
        var photos = form.getPhotos().stream().filter(p -> !p.isEmpty()).toList();
        if (photos.isEmpty() || photos.size() > 6) errors.rejectValue("photos", "count", "请选择1—6张实物照片。");
        for (var photo : photos) {
            if (photo.getSize() > 5 * 1024 * 1024 || photo.getContentType() == null
                    || !Set.of("image/jpeg", "image/png", "image/webp").contains(photo.getContentType())) {
                errors.rejectValue("photos", "invalid", "照片须为JPG、PNG或WebP，每张不超过5MB。");
                break;
            }
        }
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
