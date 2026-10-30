package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "major")
@Immutable
public class Major {
    @Id private Long id;
    @Column(nullable = false, length = 40) private String name;
    @Column(nullable = false) private boolean enabled;
    protected Major() {}
    public Long getId() { return id; }
    public String getName() { return name; }
}
