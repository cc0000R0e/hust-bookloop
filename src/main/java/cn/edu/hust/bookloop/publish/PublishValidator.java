package cn.edu.hust.bookloop.publish;

import cn.edu.hust.bookloop.market.MarketOptions;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

@Service
public class PublishValidator {
    private final PublishOptions options;
    public PublishValidator(PublishOptions options) { this.options = options; }
    public void validate(PublishForm form, BindingResult errors) {
        validate(form, errors, true);
    }
    public void validateForEdit(PublishForm form, BindingResult errors) {
        validate(form, errors, false);
    }
    private void validate(PublishForm form, BindingResult errors, boolean photosRequired) {
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
            } else if (!scope.getCollege().isEmpty() && (scope.getCollege().length() > 100 || majors.stream().noneMatch(m ->
                    (scope.getMajorId() == 0 || m.id().equals(scope.getMajorId()))
                    && cn.edu.hust.bookloop.market.CollegeNames.split(m.colleges()).contains(scope.getCollege())))) {
                errors.rejectValue("scopes", "invalid", "请选择有效学院，专业必须属于所选学院。");
            } else if (!seen.add(scope.getMajorId() + ":" + (scope.getMajorId() == 0 ? scope.getCollege() : "") + ":" + scope.getSemester())) {
                errors.rejectValue("scopes", "duplicate", "适用范围不能重复。");
            }
        }
        if (!tags.stream().map(PublishOptions.Option::id).toList().containsAll(form.getTags())) {
            errors.rejectValue("tags", "invalid", "请选择有效标签，已停用的标签不可新增。");
        }
        var photos = form.getPhotos().stream().filter(p -> !p.isEmpty()).toList();
        if ((photosRequired && photos.isEmpty()) || photos.size() > 6) {
            errors.rejectValue("photos", "count", "请选择1—6张实物照片。");
        }
        for (var photo : photos) {
            if (photo.getSize() > 5 * 1024 * 1024 || photo.getContentType() == null
                    || !Set.of("image/jpeg", "image/png", "image/webp").contains(photo.getContentType())) {
                errors.rejectValue("photos", "invalid", "照片须为JPG、PNG或WebP，每张不超过5MB。");
                break;
            }
        }
    }

}
