package cn.edu.hust.bookloop.web;

import java.net.URI;
import java.util.Locale;

/** Only a local market URL can be used for back navigation. */
public final class MarketReturnUrl {
    private MarketReturnUrl() {}
    public static String safe(String value) {
        if (value == null || value.length() > 8192 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(c -> c < 32 || c == 127)) return "/market";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("%0d") || lower.contains("%0a") || lower.contains("%5c")) return "/market";
        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                    || !"/market".equals(uri.getRawPath())) return "/market";
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return "/market";
        }
    }
}
