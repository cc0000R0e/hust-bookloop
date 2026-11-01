package cn.edu.hust.bookloop.book;

import cn.edu.hust.bookloop.market.Listing;
import cn.edu.hust.bookloop.market.ListingRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookVisibility {
    private final ListingRepository listings;
    private final EntityManager em;
    public BookVisibility(ListingRepository listings, EntityManager em) { this.listings = listings; this.em = em; }
    public boolean signedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
    public boolean isPublic(Listing book) {
        return !book.isAdminBlocked() && ("ACTIVE".equals(book.getLifecycleStatus()) || "RESERVED".equals(book.getLifecycleStatus()));
    }
    public Listing requireVisible(long id, Authentication auth) {
        var book = listings.findById(id).orElseThrow(() -> new BookAccessException(HttpStatus.NOT_FOUND));
        if (isPublic(book)) return book;
        if (signedIn(auth)) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return book;
            boolean owner = !em.createQuery("select u.id from AppUser u where u.id = :id and u.username = :name", Long.class)
                    .setParameter("id", book.getOwnerId()).setParameter("name", auth.getName()).getResultList().isEmpty();
            if (owner) return book;
        }
        throw new BookAccessException(HttpStatus.GONE);
    }
}
