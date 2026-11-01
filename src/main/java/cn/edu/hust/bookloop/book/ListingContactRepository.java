package cn.edu.hust.bookloop.book;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingContactRepository extends JpaRepository<ListingContact, Long> {
    List<ListingContact> findByListingIdOrderByIdAsc(Long listingId);
}
