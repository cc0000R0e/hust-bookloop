package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.market.MarketOptions;
import cn.edu.hust.bookloop.owner.ManageFailure;
import cn.edu.hust.bookloop.owner.OwnerListingService;
import cn.edu.hust.bookloop.publish.PublishForm;
import cn.edu.hust.bookloop.publish.PublishFailure;
import cn.edu.hust.bookloop.publish.PublishOptions;
import cn.edu.hust.bookloop.publish.PublishValidator;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OwnerListingController {
    private final OwnerListingService ownerListings;
    private final PublishOptions publishOptions;
    private final PublishValidator validator;

    public OwnerListingController(OwnerListingService ownerListings, PublishOptions publishOptions,
                                  PublishValidator validator) {
        this.ownerListings = ownerListings;
        this.publishOptions = publishOptions;
        this.validator = validator;
    }

    @GetMapping("/my/listings")
    public String listings(@RequestParam(defaultValue = "all") String status,
                           @RequestParam(defaultValue = "0") int page,
                           Principal principal, Model model) {
        model.addAttribute("result", ownerListings.page(principal.getName(), status, page));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", List.of(
                new StatusFilter("all", "全部"), new StatusFilter("active", "在售／待赠送"),
                new StatusFilter("reserved", "已预订"), new StatusFilter("completed", "已完成"),
                new StatusFilter("withdrawn", "已下架"), new StatusFilter("blocked", "管理员限制")));
        model.addAttribute("previousUrl", pageUrl(status, Math.max(0, page - 1)));
        model.addAttribute("nextUrl", pageUrl(status, page + 1));
        return "my-listings";
    }

    public record StatusFilter(String value, String label) {}

    @GetMapping("/books/{id}/edit")
    public String edit(@PathVariable long id, Principal principal, Model model) {
        try {
            var data = ownerListings.editData(id, principal.getName());
            model.addAttribute("form", data.form());
            model.addAttribute("currentPhotos", data.photos());
            model.addAttribute("expectedVersion", data.expectedVersion());
            model.addAttribute("editId", id);
            model.addAttribute("listingStatus", data.status());
            model.addAttribute("mode", "edit");
            options(model);
            return "publish";
        } catch (ManageFailure exception) {
            return accessError(exception);
        }
    }

    @PostMapping("/books/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") PublishForm form,
                         BindingResult errors, @RequestParam(defaultValue = "-1") long expectedVersion,
                         Principal principal, Model model, RedirectAttributes redirect) {
        validator.validateForEdit(form, errors);
        if (!errors.hasErrors()) {
            try {
                ownerListings.update(id, form, expectedVersion, principal.getName());
                redirect.addFlashAttribute("success", "资料已更新。");
                return "redirect:/books/" + id;
            } catch (ManageFailure exception) {
                if (exception.status().is4xxClientError() && exception.status().value() == 403) {
                    return accessError(exception);
                }
                errors.reject("update", exception.getMessage());
            } catch (PublishFailure exception) {
                errors.reject("photos", exception.getMessage());
            } catch (DataAccessException | jakarta.persistence.PersistenceException
                     | org.springframework.transaction.TransactionException exception) {
                errors.reject("update", "保存失败，本次没有修改资料，请重新选择照片后重试。");
            }
        }
        try {
            var data = ownerListings.editData(id, principal.getName());
            model.addAttribute("currentPhotos", data.photos());
            model.addAttribute("listingStatus", data.status());
            model.addAttribute("editId", id);
            model.addAttribute("mode", "edit");
            model.addAttribute("expectedVersion", expectedVersion);
            model.addAttribute("reselectPhotos", true);
            options(model);
            return "publish";
        } catch (ManageFailure exception) {
            return accessError(exception);
        }
    }

    @PostMapping("/books/{id}/status")
    public String status(@PathVariable long id, @RequestParam String action,
                         @RequestParam(defaultValue = "-1") long expectedVersion,
                         Principal principal, RedirectAttributes redirect) {
        try {
            ownerListings.changeStatus(id, action, expectedVersion, principal.getName());
            redirect.addFlashAttribute("success", switch (action) {
                case "reserve" -> "资料已标记为已预订。";
                case "cancel" -> "预订已取消，资料重新公开。";
                case "complete" -> "资料已标记为完成流转。";
                case "withdraw" -> "资料已下架。";
                case "relist" -> "资料已重新上架。";
                default -> "资料状态已更新。";
            });
        } catch (ManageFailure exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataAccessException | jakarta.persistence.PersistenceException
                 | org.springframework.transaction.TransactionException exception) {
            redirect.addFlashAttribute("error", "资料保存失败，请刷新页面后重试。");
        }
        return "redirect:/my/listings";
    }

    @InitBinder("form")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("title", "category", "author", "publisher", "editionNote", "courseName", "description",
                "conditionCode", "noteLevel", "transferType", "price", "contactType", "contactValue", "scopes[*].majorId",
                "scopes[*].semester", "tags", "photos");
        binder.setAutoGrowCollectionLimit(50);
    }

    @ExceptionHandler(ManageFailure.class)
    public String failed(ManageFailure exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.status().value());
        model.addAttribute("missing", exception.status().value() == 404);
        model.addAttribute("message", exception.getMessage());
        return "manage-error";
    }

    private String accessError(ManageFailure exception) {
        throw exception;
    }

    private void options(Model model) {
        model.addAttribute("majors", publishOptions.majors());
        model.addAttribute("tags", publishOptions.tags());
        model.addAttribute("semesters", MarketOptions.SEMESTERS);
        model.addAttribute("categories", MarketOptions.CATEGORIES);
        model.addAttribute("conditions", MarketOptions.CONDITIONS);
        model.addAttribute("notes", Map.of("NONE", "无笔记", "FEW", "少量笔记", "MANY", "较多笔记"));
    }

    private String pageUrl(String status, int page) {
        return "/my/listings?status=" + status + "&page=" + page;
    }
}
