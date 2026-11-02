package cn.edu.hust.bookloop.publish;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Form input only: never contains a client-selected owner, role or lifecycle status. */
public class PublishForm {
    @NotBlank @Size(max = 100) private String title = "";
    @NotBlank private String category = "";
    @Size(max = 100) private String author = "";
    @Size(max = 100) private String publisher = "";
    @Size(max = 100) private String editionNote = "";
    @Size(max = 100) private String courseName = "";
    @Size(max = 2000) private String description = "";
    @NotBlank private String conditionCode = "";
    @NotBlank private String noteLevel = "";
    @NotBlank private String transferType = "SALE";
    private BigDecimal price;
    @NotBlank private String contactType = "";
    @NotBlank @Size(max = 100) private String contactValue = "";
    @Valid @NotEmpty @Size(max = 50) private List<Scope> scopes = new ArrayList<>(List.of(new Scope()));
    @Size(max = 50) private List<Long> tags = new ArrayList<>();
    private List<MultipartFile> photos = new ArrayList<>();

    public static class Scope {
        @Size(max = 100) private String college = "";
        public String getCollege() { return college; }
        public void setCollege(String value) { college = cn.edu.hust.bookloop.market.CollegeNames.normalize(value); }
        @NotNull @Min(0) private Long majorId = 0L;
        @NotBlank private String semester = "UNDEFINED";
        public Long getMajorId() { return majorId; }
        public void setMajorId(Long value) { majorId = value; }
        public String getSemester() { return semester; }
        public void setSemester(String value) { semester = value; }
    }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public String getAuthor() { return author; }
    public void setAuthor(String value) { author = value; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String value) { publisher = value; }
    public String getEditionNote() { return editionNote; }
    public void setEditionNote(String value) { editionNote = value; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String value) { courseName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getConditionCode() { return conditionCode; }
    public void setConditionCode(String value) { conditionCode = value; }
    public String getNoteLevel() { return noteLevel; }
    public void setNoteLevel(String value) { noteLevel = value; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String value) { transferType = value; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal value) { price = value; }
    public String getContactType() { return contactType; }
    public void setContactType(String value) { contactType = value; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String value) { contactValue = value; }
    public List<Scope> getScopes() { return scopes; }
    public void setScopes(List<Scope> value) { scopes = value; }
    public List<Long> getTags() { return tags; }
    public void setTags(List<Long> value) { tags = value; }
    public List<MultipartFile> getPhotos() { return photos; }
    public void setPhotos(List<MultipartFile> value) { photos = value; }
}
