package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(name = "uk_tag_name", columnNames = "name"))
@Immutable
public class BookTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String name;
    @Column(nullable = false) private boolean enabled;
    protected BookTag() {}
    public Long getId() { return id; }
    public String getName() { return name; }
}
