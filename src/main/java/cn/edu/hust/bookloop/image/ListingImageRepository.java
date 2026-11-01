package cn.edu.hust.bookloop.image;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
    List<ListingImage> findByListingIdOrderBySortOrderAsc(Long listingId);
    List<ListingImage> findByListingIdInOrderBySortOrderAsc(Collection<Long> listingIds);
    Optional<ListingImage> findByIdAndListingId(Long id, Long listingId);
}
