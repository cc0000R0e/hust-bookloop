package cn.edu.hust.bookloop.web;

import cn.edu.hust.bookloop.book.BookAccessException;
import cn.edu.hust.bookloop.book.BookService;
import cn.edu.hust.bookloop.report.ReportOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class BookController {
    private final BookService books;
    public BookController(BookService books) { this.books = books; }

    @GetMapping("/books/{id}")
    public String detail(@PathVariable String id, @RequestParam(required = false) String returnTo,
                         Authentication authentication, Model model) {
        model.addAttribute("book", books.find(parseId(id), authentication));
        model.addAttribute("returnTo", MarketReturnUrl.safe(returnTo));
        model.addAttribute("reportReasons", ReportOptions.REASONS);
        return "book";
    }

    @GetMapping("/books/{id}/contact")
    public String contact(@PathVariable String id, @RequestParam(required = false) String returnTo,
                          Authentication authentication) {
        long bookId = parseId(id);
        books.find(bookId, authentication); // Recheck status after login; a book may have been withdrawn meanwhile.
        return "redirect:" + UriComponentsBuilder.fromPath("/books/" + bookId)
                .queryParam("returnTo", MarketReturnUrl.safe(returnTo)).fragment("contacts").build().encode().toUriString();
    }

    @ExceptionHandler(BookAccessException.class)
    public String unavailable(BookAccessException exception, HttpServletRequest request,
                              HttpServletResponse response, Model model) {
        response.setStatus(exception.status().value());
        model.addAttribute("missing", exception.status() == HttpStatus.NOT_FOUND);
        model.addAttribute("returnTo", MarketReturnUrl.safe(request.getParameter("returnTo")));
        return "book-unavailable";
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new BookAccessException(HttpStatus.NOT_FOUND);
    }
}
