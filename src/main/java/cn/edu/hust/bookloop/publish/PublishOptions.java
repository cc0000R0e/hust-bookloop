package cn.edu.hust.bookloop.publish;

import cn.edu.hust.bookloop.market.BookTag;
import cn.edu.hust.bookloop.market.Major;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublishOptions {
    private final EntityManager entityManager;
    public PublishOptions(EntityManager entityManager) { this.entityManager = entityManager; }
    public record Option(Long id, String name, String colleges) {
        public Option(Long id, String name) { this(id, name, ""); }
    }
    public List<Option> majors() {
        return entityManager.createQuery("select m from Major m where m.enabled = true order by m.name, m.id", Major.class)
                .getResultList().stream().map(m -> new Option(m.getId(), m.getName(), m.getColleges())).toList();
    }
    public List<Option> tags() {
        return entityManager.createQuery("select t from BookTag t where t.enabled = true order by t.name, t.id", BookTag.class)
                .getResultList().stream().map(t -> new Option(t.getId(), t.getName())).toList();
    }
}
