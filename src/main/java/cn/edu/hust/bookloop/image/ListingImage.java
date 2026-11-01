package cn.edu.hust.bookloop.image;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "listing_image", uniqueConstraints = {
    @UniqueConstraint(name = "uk_image_storage", columnNames = "storage_key"),
    @UniqueConstraint(name = "uk_image_order", columnNames = {"listing_id", "sort_order"})})
@Immutable
public class ListingImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "listing_id", nullable = false) private Long listingId;
    @Column(name = "storage_key", nullable = false, length = 255) private String storageKey;
    @Column(name = "mime_type", nullable = false, length = 32) private String mimeType;
    @Column(name = "byte_size", nullable = false) private int byteSize;
    @Column(name = "sort_order", nullable = false) private byte sortOrder;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected ListingImage() {}
    public ListingImage(Long listingId, String key, String mime, int size, int order) {
        this.listingId = listingId; this.storageKey = key; this.mimeType = mime;
        this.byteSize = size; this.sortOrder = (byte) order; this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public int getByteSize() { return byteSize; }
    public byte getSortOrder() { return sortOrder; }
}
