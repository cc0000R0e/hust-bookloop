package cn.edu.hust.bookloop.market;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "major", uniqueConstraints = @UniqueConstraint(name = "uk_major_name", columnNames = "name"))
@Immutable
public class Major {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String name;
    @Column(nullable = false) private boolean enabled;
    protected Major() {}
    public Long getId() { return id; }
    public String getName() { return name; }
}
