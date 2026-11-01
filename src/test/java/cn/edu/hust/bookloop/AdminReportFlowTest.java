package cn.edu.hust.bookloop;

import cn.edu.hust.bookloop.admin.AdminService;
import cn.edu.hust.bookloop.report.ReportService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
        properties = "spring.datasource.url=jdbc:h2:mem:admin_report_flow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("preview")
class AdminReportFlowTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ReportService reportService;
    @Autowired AdminService adminService;

    @BeforeEach
    void seed() {
        clear();
        String hash = encoder.encode("testpass123");
        String[] usernames = {"admin_owner", "admin_reporter", "admin_normal", "the_admin"};
        for (int i = 0; i < usernames.length; i++) {
            jdbc.update("""
                    insert into app_user (id, username, password_hash, role, created_at, updated_at)
                    values (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, 9701 + i, usernames[i], hash, i == 3 ? "ADMIN" : "USER");
        }
        listing(601, "待审核资料", "ACTIVE", 9701, null, null);
        listing(602, "已完成资料", "COMPLETED", 9701, null, LocalDateTime.now());
        listing(603, "驳回测试资料", "ACTIVE", 9701, null, null);
    }

    @AfterEach
    void clear() {
        jdbc.update("delete from report");
        jdbc.update("delete from listing_event");
        jdbc.update("delete from listing where id between 601 and 603");
        jdbc.update("delete from app_user where id between 9701 and 9704");
    }

    private void listing(long id, String title, String lifecycle, long ownerId,
                         String adminReason, LocalDateTime completedAt) {
        jdbc.update("""
                insert into listing (id, owner_id, title, category, description, condition_code, note_level,
                    transfer_type, price, lifecycle_status, admin_blocked, admin_reason, completed_at, published_at)
                values (?, ?, ?, 'TEXTBOOK', '管理员流程测试', 'LIGHT_USE', 'NONE', 'SALE', 20,
                    ?, FALSE, ?, ?, CURRENT_TIMESTAMP)
                """, id, ownerId, title, lifecycle, adminReason, completedAt);
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
        URI target = path.startsWith("http://") ? URI.create(path) : uri(path);
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

    private void login(HttpClient client, String username) throws Exception {
        var response = post(client, "/login", Map.of(
                "username", username,
                "password", "testpass123",
                "_csrf", csrf(get(client, "/login").body())));
        assertThat(response.statusCode()).isEqualTo(302);
    }

    private String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private long reportId() {
        return jdbc.queryForObject("select id from report order by id desc limit 1", Long.class);
    }

    private long listingVersion(long id) {
        return jdbc.queryForObject("select row_version from listing where id = ?", Long.class, id);
    }

    @Test
    void adminsSeeQueueAndUsersCannotAccessOrCallAdminActions() throws Exception {
        reportService.submit(601, "INACCURATE", "描述与实物不符", "admin_reporter");

        var guest = client();
        assertThat(get(guest, "/admin/reports").statusCode()).isEqualTo(302);

        var normal = client();
        login(normal, "admin_normal");
        assertThat(get(normal, "/admin/reports").statusCode()).isEqualTo(403);
        assertThat(post(normal, "/admin/reports/" + reportId() + "/handle",
                Map.of("decision", "RESOLVED", "reason", "越权处理")).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select status from report", String.class)).isEqualTo("PENDING");

        var admin = client();
        login(admin, "the_admin");
        var queue = get(admin, "/admin/reports");
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(queue.body()).contains("待审核资料", "admin_reporter", "描述与实物不符",
                "处理举报", "驳回举报", "处理理由", "都会由管理员下架")
                .doesNotContain("同时下架关联资料", "管理员下架原因");
        assertThat(get(admin, "/admin/listings").statusCode()).isEqualTo(200);
    }

    @Test
    void handlingCanBlockAndOwnerCannotRecoverUntilAdminRelease() throws Exception {
        reportService.submit(601, "FALSE_LISTING", "疑似虚假发布", "admin_reporter");
        var admin = client();
        login(admin, "the_admin");
        String adminToken = csrf(get(admin, "/admin/reports").body());
        var handled = post(admin, "/admin/reports/" + reportId() + "/handle?_csrf=" + encode(adminToken), Map.of(
                "decision", "RESOLVED",
                "reason", "信息与实物严重不符",
                "expectedVersion", "0"));
        assertThat(handled.statusCode()).isEqualTo(302);

        assertThat(jdbc.queryForObject("select status from report", String.class)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("select handler_id from report", Long.class)).isEqualTo(9704L);
        assertThat(jdbc.queryForObject("select resolution_note from report", String.class)).isEqualTo("信息与实物严重不符");
        assertThat(jdbc.queryForObject("select handled_at from report", Object.class)).isNotNull();
        assertThat(jdbc.queryForObject("select admin_blocked from listing where id = 601", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select admin_reason from listing where id = 601", String.class))
                .isEqualTo("信息与实物严重不符");
        assertThat(jdbc.queryForObject("select count(*) from listing_event where listing_id = 601 "
                + "and action_code = 'ADMIN_BLOCK'", Long.class)).isEqualTo(1);

        var owner = client();
        login(owner, "admin_owner");
        assertThat(get(owner, "/books/601/edit").statusCode()).isEqualTo(409);
        String ownerToken = csrf(get(owner, "/account").body());
        post(owner, "/books/601/status?_csrf=" + encode(ownerToken),
                Map.of("action", "relist", "expectedVersion", Long.toString(listingVersion(601))));
        assertThat(jdbc.queryForObject("select admin_blocked from listing where id = 601", Boolean.class)).isTrue();

        var blockedPage = get(admin, "/admin/listings");
        assertThat(blockedPage.body()).contains("信息与实物严重不符", "解除限制");
        String releaseToken = csrf(blockedPage.body());
        post(admin, "/admin/listings/601/release?_csrf=" + encode(releaseToken),
                Map.of("expectedVersion", Long.toString(listingVersion(601))));
        assertThat(jdbc.queryForObject("select admin_blocked from listing where id = 601", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = 601", String.class))
                .isEqualTo("WITHDRAWN");

        String ownerTokenAfterRelease = csrf(get(owner, "/my/listings").body());
        post(owner, "/books/601/status?_csrf=" + encode(ownerTokenAfterRelease),
                Map.of("action", "relist", "expectedVersion", Long.toString(listingVersion(601))));
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = 601", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    void rejectingAlsoBlocksListingAndRequiresOneReason() throws Exception {
        reportService.submit(603, "OTHER", "需要管理员核实", "admin_reporter");
        var admin = client();
        login(admin, "the_admin");
        String token = csrf(get(admin, "/admin/reports").body());

        var invalid = post(admin, "/admin/reports/" + reportId() + "/handle?_csrf=" + encode(token),
                Map.of("decision", "REJECTED", "expectedVersion", "0"));
        assertThat(invalid.statusCode()).isEqualTo(302);
        assertThat(jdbc.queryForObject("select status from report", String.class)).isEqualTo("PENDING");

        post(admin, "/admin/reports/" + reportId() + "/handle?_csrf=" + encode(token), Map.of(
                "decision", "REJECTED",
                "reason", "举报内容不成立",
                "expectedVersion", "0"));
        assertThat(jdbc.queryForObject("select status from report", String.class)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select admin_blocked from listing where id = 603", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select admin_reason from listing where id = 603", String.class))
                .isEqualTo("举报内容不成立");
        assertThat(jdbc.queryForObject("select count(*) from listing_event where listing_id = 603 "
                + "and action_code = 'ADMIN_BLOCK' and reason = '举报内容不成立'", Long.class)).isEqualTo(1);
    }

    @Test
    void releasingCompletedListingKeepsItCompleted() {
        adminService.blockListing(602, "管理员临时限制", 0, "the_admin");
        assertThat(jdbc.queryForObject("select admin_blocked from listing where id = 602", Boolean.class)).isTrue();

        adminService.releaseListing(602, listingVersion(602), "the_admin");
        assertThat(jdbc.queryForObject("select admin_blocked from listing where id = 602", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = 602", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select completed_at from listing where id = 602", Object.class)).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from listing_event where listing_id = 602 "
                + "and action_code in ('ADMIN_BLOCK','ADMIN_RELEASE')", Long.class)).isEqualTo(2);
    }
}
