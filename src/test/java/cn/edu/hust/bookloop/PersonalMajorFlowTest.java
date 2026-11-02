package cn.edu.hust.bookloop;

import cn.edu.hust.bookloop.account.UserRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:personal_major;DB_CLOSE_DELAY=-1")
@ActiveProfiles("preview")
class PersonalMajorFlowTest {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("Rendered form has a CSRF token").isTrue();
        return matcher.group(1);
    }
    private HttpResponse<String> post(HttpClient client, String path, Map<String,String> fields) throws Exception {
        var body = fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)+"="+URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired cn.edu.hust.bookloop.account.AccountService accounts;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        for (String table : java.util.List.of("listing_scope", "listing", "app_user", "major")) jdbc.update("delete from " + table);
        jdbc.update("insert into major (id, name, enabled, colleges) values (501, '计算机科学与技术', true, '计算机学院'), (502, '软件工程', true, '软件学院'), (503, '停用专业', false, '历史学院')");
    }

    private HttpClient login(String username) throws Exception {
        var client = client();
        var response = post(client, "/login", Map.of("_csrf", token(get(client, "/login").body()), "username", username, "password", "secure123"));
        assertThat(response.statusCode()).isEqualTo(302);
        return client;
    }

    @Test
    void registrationCanSkipOrChooseMajorAndRejectsInvalidOptions() throws Exception {
        var client = client();
        String csrf = token(get(client, "/register").body());
        assertThat(get(client, "/register").body()).contains("data-major-picker", "计算机学院", "未设置").doesNotContain("停用专业");
        for (String id : java.util.List.of("0", "503", "99999", "-1")) {
            var invalid = post(client, "/register", Map.of("_csrf", csrf, "username", "invalid_major", "password", "secure123", "confirmation", "secure123", "majorId", id));
            assertThat(invalid.statusCode()).isEqualTo(200);
            assertThat(invalid.body()).contains("请选择有效且启用的专业").doesNotContain("secure123");
            assertThat(users.findByUsername("invalid_major")).isEmpty();
        }
        assertThat(post(client, "/register", Map.of("_csrf", csrf, "username", "major_student", "password", "secure123", "confirmation", "secure123", "majorId", "501")).statusCode()).isEqualTo(302);
        assertThat(users.findByUsername("major_student").orElseThrow().getMajorId()).isEqualTo(501L);
        assertThat(post(client, "/register", Map.of("_csrf", csrf, "username", "skip_student", "password", "secure123", "confirmation", "secure123", "majorId", "")).statusCode()).isEqualTo(302);
        assertThat(users.findByUsername("skip_student").orElseThrow().getMajorId()).isNull();
    }

    @Test
    void personalMajorChangesOnlyOwnProfileAndPreselectsNewListings() throws Exception {
        accounts.register("major_owner", "secure123", "secure123", 501L);
        accounts.register("major_other", "secure123", "secure123", 502L);
        var client = login("major_owner");
        String csrf = token(get(client, "/account").body());
        assertThat(get(client, "/books/new").body()).containsPattern("value=\"501\"[^>]*selected");
        assertThat(post(client, "/account/major", Map.of("majorId", "502")).statusCode()).isEqualTo(403);
        assertThat(post(client, "/account/major", Map.of("_csrf", csrf, "majorId", "503")).body()).contains("请选择有效且启用的专业");
        assertThat(post(client, "/account/major", Map.of("_csrf", csrf, "majorId", "502", "username", "major_other")).statusCode()).isEqualTo(302);
        assertThat(users.findByUsername("major_owner").orElseThrow().getMajorId()).isEqualTo(502L);
        assertThat(get(client, "/books/new").body()).containsPattern("value=\"502\"[^>]*selected");
        jdbc.update("update major set enabled = false where id = 502");
        assertThat(get(client, "/account").body()).contains("软件工程（已停用，可保留或更换）");
        assertThat(get(client, "/books/new").body()).containsPattern("value=\"0\"[^>]*selected");
        assertThat(post(client, "/account/major", Map.of("_csrf", csrf, "majorId", "")).statusCode()).isEqualTo(302);
        assertThat(users.findByUsername("major_owner").orElseThrow().getMajorId()).isNull();
        assertThat(users.findByUsername("major_other").orElseThrow().getMajorId()).isEqualTo(502L);
    }

    @Test
    void marketDefaultRemainsUnfilteredAndShortcutIncludesAllMajorBooks() throws Exception {
        accounts.register("market_major", "secure123", "secure123", 501L);
        long owner = users.findByUsername("market_major").orElseThrow().getId();
        for (int id = 601; id <= 603; id++) {
            jdbc.update("insert into listing (id, owner_id, title, category, condition_code, note_level, transfer_type, price, lifecycle_status, admin_blocked, published_at) values (?, ?, ?, 'TEXTBOOK', 'LIGHT_USE', 'NONE', 'SALE', 10, 'ACTIVE', false, CURRENT_TIMESTAMP)", id, owner, "专业测试书" + id);
            jdbc.update("insert into listing_scope (listing_id, scope_type, major_id, semester_code) values (?, ?, ?, 'UNDEFINED')", id, id == 603 ? "ALL" : "SPECIFIC", id == 603 ? null : id == 601 ? 501L : 502L);
        }
        var client = login("market_major");
        var market = get(client, "/market?q=专业测试书&sort=low");
        assertThat(market.body()).contains("专业测试书601", "专业测试书602", "专业测试书603", "适合我的专业");
        var matcher = Pattern.compile("href=\"([^\"]*major=501[^\"]*)\"[^>]*>适合我的专业").matcher(market.body());
        assertThat(matcher.find()).isTrue();
        String shortcut = matcher.group(1).replace("&amp;", "&");
        assertThat(shortcut).contains("sort=low", "page=0", "q=");
        var filtered = get(client, shortcut);
        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(filtered.body()).contains("专业测试书601", "专业测试书603").doesNotContain("专业测试书602");
        accounts.updateMajor("market_major", 502L);
        assertThat(jdbc.queryForObject("select major_id from listing_scope where listing_id = 601", Long.class)).isEqualTo(501L);
        assertThat(get(client(), "/market").body()).doesNotContain("适合我的专业");
    }

    @Test
    void collegeOnlyRegistrationProfileAndPublishDefaults() throws Exception {
        var guest = client();
        var registered = post(guest, "/register", Map.of("_csrf", token(get(guest, "/register").body()),
                "username", "college_student", "password", "secure123", "confirmation", "secure123",
                "college", "计算机学院", "majorId", ""));
        assertThat(registered.statusCode()).isEqualTo(302);
        var user = users.findByUsername("college_student").orElseThrow();
        assertThat(user.getMajorId()).isNull();
        assertThat(user.getCollege()).isEqualTo("计算机学院");
        var owner = login("college_student");
        assertThat(get(owner, "/account").body()).contains("value=\"计算机学院\"");
        assertThat(get(owner, "/books/new").body()).contains("value=\"计算机学院\"");
        String csrf = token(get(owner, "/account").body());
        assertThat(post(owner, "/account/major", Map.of("_csrf", csrf, "college", "软件学院", "majorId", "501")).body())
                .contains("专业必须属于所选学院");
        assertThat(users.findByUsername("college_student").orElseThrow().getCollege()).isEqualTo("计算机学院");
        assertThat(post(owner, "/account/major", Map.of("_csrf", csrf, "college", "软件学院")).statusCode()).isEqualTo(302);
        assertThat(users.findByUsername("college_student").orElseThrow().getMajorId()).isNull();
        assertThat(users.findByUsername("college_student").orElseThrow().getCollege()).isEqualTo("软件学院");
        assertThat(post(owner, "/account/major", Map.of("_csrf", csrf, "college", "不存在的学院")).body()).contains("请选择有效学院");
        assertThat(post(owner, "/account/major", Map.of("_csrf", csrf, "college", "")).statusCode()).isEqualTo(302);
        assertThat(users.findByUsername("college_student").orElseThrow().getCollege()).isEmpty();
    }

    @Test
    void collegeFiltersIncludeSpecificWholeCollegeAndUnrestrictedScopes() throws Exception {
        accounts.register("college_market", "secure123", "secure123", null, "计算机学院");
        long owner = users.findByUsername("college_market").orElseThrow().getId();
        for (int id = 701; id <= 704; id++) {
            jdbc.update("insert into listing (id, owner_id, title, category, condition_code, note_level, transfer_type, price, lifecycle_status, admin_blocked, published_at) values (?, ?, ?, 'TEXTBOOK', 'LIGHT_USE', 'NONE', 'SALE', 10, 'ACTIVE', false, CURRENT_TIMESTAMP)", id, owner, "学院测试书" + id);
        }
        jdbc.update("insert into listing_scope (listing_id, scope_type, major_id, college, semester_code) values (701,'SPECIFIC',501,null,'Y1_S1'), (702,'COLLEGE',null,'计算机学院','Y1_S1'), (703,'ALL',null,null,'Y1_S1'), (704,'COLLEGE',null,'软件学院','Y1_S1')");
        var client = login("college_market");
        String collegeQuery = URLEncoder.encode("计算机学院", StandardCharsets.UTF_8);
        var result = get(client, "/market?college=" + collegeQuery);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("学院测试书701", "学院测试书702", "学院测试书703").doesNotContain("学院测试书704");
        assertThat(get(client, "/market?major=501").body()).contains("学院测试书702");
        assertThat(get(client, "/market?college=" + collegeQuery + "&semester=Y2_S1").body()).doesNotContain("学院测试书702");
        assertThat(get(client, "/books/702").body()).contains("计算机学院（全院）");
        assertThat(get(client, "/market?college=" + collegeQuery + "&major=502").statusCode()).isEqualTo(400);
        var page = get(client, "/market?q=test&sort=low").body();
        assertThat(page).contains("适合我的学院", "college=", "sort=low");
    }
}
