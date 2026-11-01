package cn.edu.hust.bookloop;

import cn.edu.hust.bookloop.book.ListingContactRepository;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("preview")
class BookFlowTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean ListingContactRepository contacts;

    @BeforeEach
    void seed() {
        clear();
        String hash = encoder.encode("testpass123");
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                insert into app_user (id, username, password_hash, role, default_contact_type, default_contact_value, created_at, updated_at)
                values (?, ?, ?, ?, 'WECHAT', 'DEFAULT-MUST-NOT-APPEAR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 7001 + i, List.of("detail_owner", "detail_buyer", "detail_admin").get(i), hash, i == 2 ? "ADMIN" : "USER");
        }
        jdbc.update("insert into major (id, name, enabled) values (71, '计算机科学与技术', true)");
        jdbc.update("insert into tag (id, name, enabled) values (71, '标签一', true), (72, '历史标签', false)");
        for (int id = 301; id <= 308; id++) {
            String status = switch (id) { case 303, 308 -> "RESERVED"; case 304, 307 -> "COMPLETED"; case 305 -> "WITHDRAWN"; default -> "ACTIVE"; };
            boolean gift = id == 302 || id == 307 || id == 308;
            jdbc.update("""
                insert into listing (id, owner_id, title, category, author, publisher, edition_note, course_name,
                    description, note_level, condition_code, transfer_type, price, lifecycle_status, admin_blocked, published_at)
                values (?, 7001, ?, 'TEXTBOOK', ?, ?, ?, ?, ?, 'FEW', 'LIGHT_USE', ?, ?, ?, ?, '2026-01-01 00:30:00')
                """, id, "详情书籍" + id, id == 302 ? null : "作者", id == 302 ? null : "出版社",
                    id == 302 ? null : "第二版", id == 302 ? null : "课程", "独有说明" + id + "\n<script>alert(1)</script>",
                    gift ? "GIFT" : "SALE", gift ? 0 : 18.50, status, id == 306);
            jdbc.update("insert into listing_scope (id, listing_id, scope_type, major_id, semester_code) values (?, ?, 'ALL', null, 'UNDEFINED')", id, id);
            jdbc.update("insert into listing_contact (id, listing_id, contact_type, contact_value) values (?, ?, 'WECHAT', ?)", id, id, "book-contact-" + id + "<img src=x>");
        }
        jdbc.update("insert into listing_scope (id, listing_id, scope_type, major_id, semester_code) values (309, 301, 'SPECIFIC', 71, 'Y1_S1')");
        jdbc.update("insert into listing_tag (listing_id, tag_id) values (301, 71), (301, 72)");
        clearInvocations(contacts);
    }

    @AfterEach
    void clear() {
        jdbc.update("delete from listing_contact where listing_id between 301 and 308");
        jdbc.update("delete from listing_tag where listing_id between 301 and 308");
        jdbc.update("delete from listing_scope where listing_id between 301 and 308");
        jdbc.update("delete from listing where id between 301 and 308");
        jdbc.update("delete from major where id = 71");
        jdbc.update("delete from tag where id in (71, 72)");
        jdbc.update("delete from app_user where id between 7001 and 7003");
    }

    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
    private HttpResponse<String> post(HttpClient client, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private HttpResponse<String> login(HttpClient client, String username) throws Exception {
        return post(client, "/login", Map.of("username", username, "password", "testpass123", "_csrf", token(get(client, "/login").body())));
    }
    private String location(HttpResponse<String> response) { return response.headers().firstValue("Location").orElseThrow(); }

    @Test
    void rendersPublicDetailsWithoutQueryingContacts() throws Exception {
        var client = client();
        var detail = get(client, "/books/301");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("详情书籍301", "出版社", "第二版", "少量笔记", "detail_owner",
                "2026-01-01 08:30", "北京时间", "计算机科学与技术 · 大一上", "不限专业 · 未定义", "历史标签",
                "&lt;script&gt;alert(1)&lt;/script&gt;", "封面占位图", "登录后查看联系方式")
                .doesNotContain("<script>alert(1)</script>", "book-contact-", "DEFAULT-MUST-NOT-APPEAR", "password_hash", "demo-state.js");
        assertThat(get(client, "/books/302").body()).contains("免费赠送", "待赠送", "未填写");
        assertThat(get(client, "/books/303").body()).contains("已预订", "已有意向买家");
        assertThat(get(client, "/books/308").body()).contains("免费赠送", "已有意向买家");
        verifyNoInteractions(contacts);
    }

    @Test
    void enforcesVisibilityForGuestBuyerOwnerAndAdmin() throws Exception {
        for (String role : List.of("guest", "detail_buyer", "detail_owner", "detail_admin")) {
            var client = client();
            boolean authenticated = !role.equals("guest");
            if (authenticated) assertThat(login(client, role).statusCode()).isEqualTo(302);
            for (int id = 301; id <= 308; id++) {
                boolean visible = id <= 303 || id == 308 || role.equals("detail_owner") || role.equals("detail_admin");
                var response = get(client, "/books/" + id);
                assertThat(response.statusCode()).as(role + " " + id).isEqualTo(visible ? 200 : 410);
                if (visible) {
                    assertThat(response.body()).contains("独有说明" + id);
                    if (authenticated) assertThat(response.body()).contains("book-contact-" + id + "&lt;img src=x&gt;");
                    else assertThat(response.body()).doesNotContain("book-contact-");
                } else {
                    assertThat(response.body()).contains("资料已不可获取").doesNotContain("独有说明", "详情书籍", "book-contact-", "detail_owner", "封面占位");
                }
                assertThat(response.body()).doesNotContain("DEFAULT-MUST-NOT-APPEAR", "<img src=x>");
            }
            if (role.equals("detail_owner") || role.equals("detail_admin")) {
                assertThat(get(client, "/books/304").body()).contains("已售出", "仅所有者和管理员");
                assertThat(get(client, "/books/307").body()).contains("已送出");
                assertThat(get(client, "/books/306").body()).contains("管理员限制");
            }
        }
    }

    @Test
    void returnsToDetailAfterLoginAndPreservesMarketContext() throws Exception {
        var client = client();
        String returnTo = "/market?q=%E6%95%B0%E6%8D%AE&major=71&semester=Y1_S1&category=TEXTBOOK&tags=71&tags=72&mode=sale&condition=LIGHT_USE&minPrice=1&maxPrice=30&sort=low&page=2";
        String contactPath = "/books/301/contact?returnTo=" + encode(returnTo);
        assertThat(location(get(client, contactPath))).endsWith("/login");
        var badLogin = post(client, "/login", Map.of("username", "detail_buyer", "password", "wrong", "_csrf", token(get(client, "/login").body())));
        assertThat(location(badLogin)).endsWith("/login?error");
        assertThat(location(login(client, "detail_buyer"))).endsWith(contactPath);
        var redirected = get(client, contactPath);
        assertThat(redirected.statusCode()).isEqualTo(302);
        URI uri = URI.create(location(redirected));
        assertThat(uri.getPath()).isEqualTo("/books/301");
        assertThat(uri.getFragment()).isEqualTo("contacts");
        var detail = get(client, uri.getRawPath() + "?" + uri.getRawQuery());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("book-contact-301", "href=\"" + returnTo.replace("&", "&amp;") + "\"");
        var market = get(client, "/market?q=" + encode("详情书籍") + "&tags=71&tags=72&sort=low");
        assertThat(market.body()).contains("href=\"/books/301?returnTo=", "tags%3D71", "tags%3D72", "sort%3Dlow");
        assertThat(location(login(client(), "detail_buyer"))).endsWith("/account");
    }

    @Test
    void blocksContactsIfBookBecomesUnavailableDuringLogin() throws Exception {
        var client = client();
        get(client, "/books/301/contact");
        jdbc.update("update listing set lifecycle_status = 'WITHDRAWN' where id = 301");
        login(client, "detail_buyer");
        clearInvocations(contacts);
        var response = get(client, "/books/301/contact");
        assertThat(response.statusCode()).isEqualTo(410);
        assertThat(response.body()).doesNotContain("独有说明", "book-contact-");
        verifyNoInteractions(contacts);
    }

    @Test
    void handlesInvalidIdsAndUnsafeReturnUrls() throws Exception {
        var client = client();
        for (String id : List.of("0", "-1", "abc", "9999999999999999999999999", "99999")) {
            var response = get(client, "/books/" + id);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).contains("没有找到这份资料").doesNotContain("book-contact-", "独有说明");
        }
        for (String returnTo : List.of("https://example.com/market", "//example.com/market", "/account", "/market#bad",
                "/market/../account", "/market\\evil", "/market?x=%0d%0aLocation:evil")) {
            var response = get(client, "/books/301?returnTo=" + encode(returnTo));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("class=\"back-link\" href=\"/market\"").doesNotContain("example.com", "Location:evil");
        }
    }

    @Test
    void logoutRemovesContactAccessAndMissingContactDoesNotUseUserDefault() throws Exception {
        var client = client();
        login(client, "detail_buyer");
        jdbc.update("delete from listing_contact where listing_id = 302");
        assertThat(get(client, "/books/302").body()).contains("尚未提供此资料的联系方式").doesNotContain("DEFAULT-MUST-NOT-APPEAR");
        post(client, "/logout", Map.of("_csrf", token(get(client, "/account").body())));
        clearInvocations(contacts);
        assertThat(get(client, "/books/301").body()).doesNotContain("book-contact-");
        assertThat(location(get(client, "/books/301/contact"))).endsWith("/login");
        verifyNoInteractions(contacts);
    }
}
