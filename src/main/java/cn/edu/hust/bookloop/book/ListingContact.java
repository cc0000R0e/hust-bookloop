package cn.edu.hust.bookloop.book;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "listing_contact")
@Immutable
public class ListingContact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "listing_id", nullable = false) private Long listingId;
    @Column(name = "contact_type", nullable = false, length = 16) private String type;
    @Column(name = "contact_value", nullable = false, length = 100) private String value;
    protected ListingContact() {}
    public ListingContact(Long listingId, String type, String value) {
        this.listingId = listingId; this.type = type; this.value = value.strip();
    }
    public String getType() { return type; }
    public String getValue() { return value; }
}
