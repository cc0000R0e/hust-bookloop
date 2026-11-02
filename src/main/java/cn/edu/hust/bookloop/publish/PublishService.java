package cn.edu.hust.bookloop.publish;

import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.book.ListingContact;
import cn.edu.hust.bookloop.image.ImageStorage;
import cn.edu.hust.bookloop.image.ListingImage;
import cn.edu.hust.bookloop.market.Listing;
import cn.edu.hust.bookloop.market.ListingScope;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.BeanPropertyBindingResult;

@Service
public class PublishService {
    private final EntityManager em;
    private final UserRepository users;
    private final ImageStorage storage;
    private final PublishValidator validator;
    private final Validator beanValidator;
    private final JdbcTemplate jdbc;
    public PublishService(EntityManager em, UserRepository users, ImageStorage storage,
                          PublishValidator validator, Validator beanValidator, JdbcTemplate jdbc) {
        this.em = em; this.users = users; this.storage = storage; this.validator = validator;
        this.beanValidator = beanValidator; this.jdbc = jdbc;
    }

    @Transactional
    public long publish(PublishForm form, String username) {
        if (!beanValidator.validate(form).isEmpty()) throw new PublishFailure("请完整填写必填信息，并检查字数限制。");
        var errors = new BeanPropertyBindingResult(form, "form");
        validator.validate(form, errors);
        if (errors.hasErrors()) throw new PublishFailure(errors.getAllErrors().getFirst().getDefaultMessage());
        var user = users.findByUsername(username).orElseThrow(() -> new PublishFailure("登录账号不存在，请重新登录。"));
        // Deterministic lock order prevents an option being disabled between validation and commit.
        form.getScopes().stream().map(PublishForm.Scope::getMajorId).filter(id -> id > 0).distinct().sorted()
                .forEach(id -> lockEnabled("major", id));
        form.getTags().stream().distinct().sorted().forEach(id -> lockEnabled("tag", id));
        var prepared = form.getPhotos().stream().filter(p -> !p.isEmpty()).map(storage::prepare).toList();
        var keys = new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) keys.forEach(storage::discard);
                // An unknown commit outcome must retain files: the database may have committed.
            }
        });
        var book = new Listing(user.getId(), form);
        em.persist(book);
        for (var scope : form.getScopes()) em.persist(new ListingScope(book, scope.getMajorId(), scope.getSemester(), scope.getCollege()));
        for (Long tag : form.getTags().stream().distinct().toList()) {
            em.createNativeQuery("insert into listing_tag (listing_id, tag_id) values (:listing, :tag)")
                    .setParameter("listing", book.getId()).setParameter("tag", tag).executeUpdate();
        }
        em.persist(new ListingContact(book.getId(), form.getContactType(), form.getContactValue()));
        for (int i = 0; i < prepared.size(); i++) {
            var image = prepared.get(i);
            String key = storage.save(image);
            keys.add(key);
            em.persist(new ListingImage(book.getId(), key, image.mime(), image.bytes().length, i + 1));
        }
        String snapshot = "{\"lifecycleStatus\":\"ACTIVE\",\"transferType\":\"" + book.getTransferType()
                + "\",\"price\":\"" + book.getPrice().setScale(2).toPlainString()
                + "\",\"adminBlocked\":false,\"completedAt\":null,\"rowVersion\":0}";
        em.persist(new ListingEvent(book.getId(), user.getId(), user.getRole(), snapshot));
        em.flush();
        return book.getId();
    }

    private void lockEnabled(String table, Long id) {
        // table is chosen only by the two fixed calls above; values are always bound parameters.
        var rows = jdbc.queryForList("select enabled from " + table + " where id = ? for update", Boolean.class, id);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst())) throw new PublishFailure("所选专业或标签已停用，请重新选择。");
    }
}
