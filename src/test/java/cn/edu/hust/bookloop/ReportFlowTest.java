package cn.edu.hust.bookloop;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:report_flow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("preview")
class ReportFlowTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        clear();
        String hash = encoder.encode("testpass123");
        String[] usernames = {"report_owner", "report_user", "other_reporter", "report_admin"};
        for (int i = 0; i < usernames.length; i++) {
            jdbc.update("""
                    insert into app_user (id, username, password_hash, role, created_at, updated_at)
                    values (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, 9601 + i, usernames[i], hash, i == 3 ? "ADMIN" : "USER");
        }
        listing(501, "可举报资料", "ACTIVE", false, 9601);
        listing(502, "已预订资料", "RESERVED", false, 9601);
        listing(503, "已完成资料", "COMPLETED", false, 9601);
        listing(504, "管理员限制资料", "ACTIVE", true, 9601);
    }

    @AfterEach
    void clear() {
        jdbc.update("delete from report");
        jdbc.update("delete from listing where id between 501 and 504");
        jdbc.update("delete from app_user where id between 9601 and 9604");
    }

    private void listing(long id, String title, String status, boolean blocked, long ownerId) {
        jdbc.update("""
                insert into listing (id, owner_id, title, category, description, condition_code, note_level,
                    transfer_type, price, lifecycle_status, admin_blocked, admin_reason, completed_at, published_at)
                values (?, ?, ?, 'TEXTBOOK', '举报测试说明', 'LIGHT_USE', 'NONE', 'SALE', 20,
                    ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id, ownerId, title, status, blocked,
                blocked ? "管理员测试下架" : null,
                "COMPLETED".equals(status) ? java.time.LocalDateTime.now() : null);
    }

    private HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        URI target = path.startsWith("http://") || path.startsWith("https://") ? URI.create(path) : uri(path);
        return client.send(HttpRequest.newBuilder(target).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> post(HttpClient client, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(HttpClient client, String username) throws Exception {
        return post(client, "/login", Map.of(
                "username", username,
                "password", "testpass123",
                "_csrf", csrf(get(client, "/login").body())));
    }

    private String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private long countReports() {
        return jdbc.queryForObject("select count(*) from report", Long.class);
    }

    @Test
    void signedInUserCanReportAnotherListingAndSeeSuccessNextToReportForm() throws Exception {
        var client = client();
        login(client, "report_user");
        String returnTo = "/market?q=%E5%8F%AF%E4%B8%BE%E6%8A%A5";
        var detail = get(client, "/books/501?returnTo=" + encode(returnTo));
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("举报资料", "信息不准确", "疑似虚假发布", "提交举报");
        String token = csrf(detail.body());

        var response = post(client, "/books/501/reports?_csrf=" + encode(token), Map.of(
                "reasonCode", "INACCURATE",
                "description", "封面与实物不一致",
                "returnTo", returnTo));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(countReports()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select listing_id from report", Long.class)).isEqualTo(501L);
        assertThat(jdbc.queryForObject("select reporter_id from report", Long.class)).isEqualTo(9602L);
        assertThat(jdbc.queryForObject("select reason_code from report", String.class)).isEqualTo("INACCURATE");
        assertThat(jdbc.queryForObject("select status from report", String.class)).isEqualTo("PENDING");

        var followed = get(client, location(response));
        assertThat(followed.statusCode()).isEqualTo(200);
        assertThat(followed.body()).contains("举报已提交，管理员会尽快处理。", "举报资料");
    }

    @Test
    void sameUserCannotCreateSecondPendingReportButAnotherUserCan() throws Exception {
        var reporter = client();
        login(reporter, "report_user");
        String path = "/books/501/reports?_csrf=" + encode(csrf(get(reporter, "/books/501").body()));
        var first = post(reporter, path, Map.of("reasonCode", "OTHER", "description", "第一次举报"));
        assertThat(first.statusCode()).isEqualTo(302);
        assertThat(get(reporter, location(first)).body()).contains("举报已提交");

        String duplicateToken = csrf(get(reporter, "/books/501").body());
        var duplicate = post(reporter, "/books/501/reports?_csrf=" + encode(duplicateToken),
                Map.of("reasonCode", "OTHER", "description", "重复举报"));
        assertThat(countReports()).isEqualTo(1);
        assertThat(get(reporter, location(duplicate)).body())
                .contains("你已举报过该资料");

        var other = client();
        login(other, "other_reporter");
        String otherToken = csrf(get(other, "/books/501").body());
        post(other, "/books/501/reports?_csrf=" + encode(otherToken),
                Map.of("reasonCode", "INAPPROPRIATE", "description", "另一用户举报"));
        assertThat(countReports()).isEqualTo(2);
    }

    @Test
    void ownerAndUnavailableListingsCannotBeReported() throws Exception {
        var owner = client();
        login(owner, "report_owner");
        var ownerDetail = get(owner, "/books/501");
        assertThat(ownerDetail.body()).doesNotContain("提交举报");
        String ownerToken = csrf(get(owner, "/account").body());
        post(owner, "/books/501/reports?_csrf=" + encode(ownerToken),
                Map.of("reasonCode", "OTHER", "description", "自己的资料"));

        var reporter = client();
        login(reporter, "report_user");
        String reporterToken = csrf(get(reporter, "/books/501").body());
        post(reporter, "/books/503/reports?_csrf=" + encode(reporterToken),
                Map.of("reasonCode", "OTHER", "description", "已结束资料"));
        post(reporter, "/books/504/reports?_csrf=" + encode(reporterToken),
                Map.of("reasonCode", "OTHER", "description", "管理员限制资料"));
        assertThat(countReports()).isZero();
    }

    @Test
    void guestIsSentToLoginAndReturnedToReportSection() throws Exception {
        var client = client();
        String returnTo = "/market?q=report";
        String reportPath = "/books/501/report?returnTo=" + encode(returnTo);
        var anonymous = get(client, reportPath);
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(location(anonymous)).endsWith("/login");

        var signedIn = login(client, "report_user");
        assertThat(location(signedIn)).endsWith(reportPath);
        var redirect = get(client, location(signedIn));
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(location(redirect)).contains("/books/501", "returnTo=/market", "q%3Dreport", "#report");
        assertThat(get(client, location(redirect)).body()).contains("举报资料", "提交举报");
    }

    @Test
    void rejectsInvalidOrOverlongReportInput() throws Exception {
        var client = client();
        login(client, "report_user");
        String token = csrf(get(client, "/books/501").body());
        var invalid = post(client, "/books/501/reports?_csrf=" + encode(token),
                Map.of("reasonCode", "NOT_A_REASON", "description", "无效原因"));
        assertThat(countReports()).isZero();
        assertThat(get(client, location(invalid)).body()).contains("请选择举报原因。");

        var longResponse = post(client, "/books/501/reports?_csrf=" + encode(token),
                Map.of("reasonCode", "OTHER", "description", "长".repeat(501)));
        assertThat(countReports()).isZero();

        var result = get(client, location(longResponse));
        assertThat(result.body()).contains("补充说明不能超过500个字符。").doesNotContain("请选择举报原因。");
    }
}
