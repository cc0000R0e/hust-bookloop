package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "tag")
@Immutable
public class BookTag {
    @Id private Long id;
    @Column(nullable = false, length = 30) private String name;
    @Column(nullable = false) private boolean enabled;
    protected BookTag() {}
    public Long getId() { return id; }
    public String getName() { return name; }
}
