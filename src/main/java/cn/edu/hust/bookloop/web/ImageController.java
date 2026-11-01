package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.book.BookAccessException;
import cn.edu.hust.bookloop.book.BookVisibility;
import cn.edu.hust.bookloop.image.ImageStorage;
import cn.edu.hust.bookloop.image.ListingImageRepository;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ImageController {
    private final BookVisibility visibility;
    private final ListingImageRepository images;
    private final ImageStorage storage;
    public ImageController(BookVisibility visibility, ListingImageRepository images, ImageStorage storage) {
        this.visibility = visibility; this.images = images; this.storage = storage;
    }
    @GetMapping("/books/{bookId}/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable long bookId, @PathVariable long imageId, Authentication authentication) {
        visibility.requireVisible(bookId, authentication);
        var image = images.findByIdAndListingId(imageId, bookId)
                .orElseThrow(() -> new BookAccessException(HttpStatus.NOT_FOUND));
        try {
            var body = storage.read(image.getStorageKey());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(image.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentLength(body.length).body(body);
        } catch (IOException exception) {
            throw new BookAccessException(HttpStatus.NOT_FOUND);
        }
    }
    @ExceptionHandler(BookAccessException.class)
    public ResponseEntity<Void> unavailable(BookAccessException exception) {
        return ResponseEntity.status(exception.status()).cacheControl(CacheControl.noStore()).build();
    }
}
