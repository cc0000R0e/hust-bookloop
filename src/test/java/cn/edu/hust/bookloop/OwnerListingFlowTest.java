package cn.edu.hust.bookloop;

import cn.edu.hust.bookloop.publish.PublishForm;
import cn.edu.hust.bookloop.publish.PublishService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:owner_listing_flow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("preview")
class OwnerListingFlowTest {
    static final Path UPLOAD_ROOT = temporaryDirectory();

    static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("bookloop-owner-test-");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bookloop.upload-dir", UPLOAD_ROOT::toString);
    }

    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PublishService publisher;

    @BeforeEach
    void seed() throws Exception {
        clear();
        String hash = encoder.encode("testpass123");
        String[] usernames = {"owner_user", "other_user", "admin_user"};
        for (int i = 0; i < usernames.length; i++) {
            jdbc.update("""
                    insert into app_user (id, username, password_hash, role, created_at, updated_at)
                    values (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, 9501 + i, usernames[i], hash, i == 2 ? "ADMIN" : "USER");
        }
        jdbc.update("insert into major (id, name, enabled) values (95, '测试专业', true)");
        jdbc.update("insert into tag (id, name, enabled) values (95, '测试标签', true)");
    }

    @AfterEach
    void clear() throws IOException {
        for (String table : List.of("listing_event", "listing_image", "listing_contact", "listing_tag",
                "listing_scope", "listing", "tag", "major", "app_user")) {
            jdbc.update("delete from " + table);
        }
        if (Files.exists(UPLOAD_ROOT)) {
            try (var files = Files.walk(UPLOAD_ROOT)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.equals(UPLOAD_ROOT)) Files.delete(path);
                }
            }
        }
    }

    @AfterAll
    static void removeRoot() throws IOException {
        Files.deleteIfExists(UPLOAD_ROOT);
    }

    private long publish() throws Exception {
        var form = new PublishForm();
        form.setTitle("待管理教材");
        form.setCategory("TEXTBOOK");
        form.setDescription("用于验证发布后的编辑和状态操作");
        form.setConditionCode("LIGHT_USE");
        form.setNoteLevel("FEW");
        form.setPrice(new BigDecimal("20.00"));
        form.setContactType("WECHAT");
        form.setContactValue("original-contact");
        form.setTags(List.of(95L));
        form.setPhotos(List.of(new MockMultipartFile("photos", "cover.png", "image/png", png())));
        return publisher.publish(form, "owner_user");
    }

    private byte[] png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
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
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
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

    private HttpResponse<String> multipartPost(HttpClient client, String path, Map<String, String> fields,
                                               byte[] photo) throws Exception {
        String boundary = "OwnerEditBoundary";
        var body = new ByteArrayOutputStream();
        for (var entry : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                    + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"replacement.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(photo);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void login(HttpClient client, String username) throws Exception {
        var response = post(client, "/login", Map.of(
                "username", username,
                "password", "testpass123",
                "_csrf", csrf(get(client, "/login").body())));
        assertThat(response.statusCode()).isEqualTo(302);
    }

    private long version(long listingId) {
        return jdbc.queryForObject("select row_version from listing where id = ?", Long.class, listingId);
    }

    @Test
    void ownerCanReserveCancelCompleteAndCannotRelistCompletedListing() throws Exception {
        long id = publish();
        var client = client();
        login(client, "owner_user");

        var myListings = get(client, "/my/listings");
        assertThat(myListings.statusCode()).isEqualTo(200);
        assertThat(myListings.body()).contains("待管理教材", "在售", "标记已预订", "编辑");

        String token = csrf(get(client, "/my/listings").body());
        var reserveResponse = post(client, "/books/" + id + "/status?_csrf=" + encode(token),
                Map.of("action", "reserve", "expectedVersion", "0"));
        assertThat(reserveResponse.statusCode()).isEqualTo(302);
        assertThat(reserveResponse.headers().firstValue("Location").orElseThrow()).endsWith("/my/listings");
        var reserveResultPage = get(client, "/my/listings");
        assertThat(reserveResultPage.body()).contains("资料已标记为已预订。", "owner-inline-flash success");
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = ?", String.class, id))
                .isEqualTo("RESERVED");
        assertThat(version(id)).isEqualTo(1);
        assertThat(get(client(), "/market").body()).doesNotContain("data-id=\"" + id + "\"");
        assertThat(jdbc.queryForObject("select action_code from listing_event where action_code = 'RESERVE'",
                String.class)).isEqualTo("RESERVE");

        long reservedVersion = version(id);
        post(client, "/books/" + id + "/status?_csrf=" + encode(token),
                Map.of("action", "cancel", "expectedVersion", "0"));
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = ?", String.class, id))
                .isEqualTo("RESERVED");

        post(client, "/books/" + id + "/status?_csrf=" + encode(token),
                Map.of("action", "cancel", "expectedVersion", Long.toString(reservedVersion)));
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = ?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(get(client(), "/market").body()).contains("data-id=\"" + id + "\"");

        post(client, "/books/" + id + "/status?_csrf=" + encode(token),
                Map.of("action", "complete", "expectedVersion", Long.toString(version(id))));
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = ?", String.class, id))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select completed_at from listing where id = ?", Object.class, id)).isNotNull();

        long completedVersion = version(id);
        post(client, "/books/" + id + "/status?_csrf=" + encode(token),
                Map.of("action", "relist", "expectedVersion", Long.toString(completedVersion)));
        assertThat(jdbc.queryForObject("select lifecycle_status from listing where id = ?", String.class, id))
                .isEqualTo("COMPLETED");
    }

    @Test
    void editReplacesTextOptionsAndContactButRetainsPhotosWhenNoneAreSelected() throws Exception {
        long id = publish();
        String originalKey = jdbc.queryForObject("select storage_key from listing_image where listing_id = ?",
                String.class, id);
        var owner = client();
        login(owner, "owner_user");

        var editPage = get(owner, "/books/" + id + "/edit");
        assertThat(editPage.statusCode()).isEqualTo(200);
        assertThat(editPage.body()).contains("保存修改", "待管理教材", "original-contact", "当前照片");
        String token = csrf(editPage.body());
        var fields = Map.ofEntries(
                Map.entry("title", "更新后的教材"),
                Map.entry("category", "READING"),
                Map.entry("description", "更新后的资料说明"),
                Map.entry("conditionCode", "LIKE_NEW"),
                Map.entry("noteLevel", "NONE"),
                Map.entry("transferType", "SALE"),
                Map.entry("price", "26.50"),
                Map.entry("contactType", "QQ"),
                Map.entry("contactValue", "updated-contact"),
                Map.entry("scopes[0].majorId", "0"),
                Map.entry("scopes[0].semester", "UNDEFINED"),
                Map.entry("tags", "95"),
                Map.entry("expectedVersion", "0"));
        var response = post(owner, "/books/" + id + "?_csrf=" + encode(token), fields);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/books/" + id);

        assertThat(jdbc.queryForObject("select title from listing where id = ?", String.class, id))
                .isEqualTo("更新后的教材");
        assertThat(jdbc.queryForObject("select price from listing where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("26.50");
        assertThat(jdbc.queryForObject("select contact_type from listing_contact where listing_id = ?",
                String.class, id)).isEqualTo("QQ");
        assertThat(jdbc.queryForObject("select contact_value from listing_contact where listing_id = ?",
                String.class, id)).isEqualTo("updated-contact");
        assertThat(jdbc.queryForObject("select storage_key from listing_image where listing_id = ?",
                String.class, id)).isEqualTo(originalKey);
        assertThat(version(id)).isEqualTo(1);

        var other = client();
        login(other, "other_user");
        assertThat(get(other, "/books/" + id + "/edit").statusCode()).isEqualTo(403);
        assertThat(get(other, "/my/listings").body()).doesNotContain("更新后的教材");
    }

    @Test
    void guestIsRedirectedAndStaleEditCannotOverwriteNewerData() throws Exception {
        long id = publish();
        var guest = client();
        assertThat(get(guest, "/my/listings").statusCode()).isEqualTo(302);
        assertThat(get(guest, "/books/" + id + "/edit").statusCode()).isEqualTo(302);

        var owner = client();
        login(owner, "owner_user");
        String token = csrf(get(owner, "/books/" + id + "/edit").body());
        var stale = Map.ofEntries(
                Map.entry("title", "过期页面覆盖"),
                Map.entry("category", "TEXTBOOK"),
                Map.entry("description", "不应保存"),
                Map.entry("conditionCode", "LIGHT_USE"),
                Map.entry("noteLevel", "FEW"),
                Map.entry("transferType", "SALE"),
                Map.entry("price", "20.00"),
                Map.entry("contactType", "WECHAT"),
                Map.entry("contactValue", "original-contact"),
                Map.entry("scopes[0].majorId", "0"),
                Map.entry("scopes[0].semester", "UNDEFINED"),
                Map.entry("tags", "95"),
                Map.entry("expectedVersion", "0"));
        assertThat(post(owner, "/books/" + id + "/status?_csrf=" + encode(token),
                Map.of("action", "reserve", "expectedVersion", "0")).statusCode()).isEqualTo(302);
        var response = post(owner, "/books/" + id + "?_csrf=" + encode(token), stale);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("资料已发生变化，请刷新页面后重新操作。");
        assertThat(jdbc.queryForObject("select title from listing where id = ?", String.class, id))
                .isEqualTo("待管理教材");
    }

    @Test
    void editCanReplacePhotosAndChangeTransferTypeWithAnEvent() throws Exception {
        long id = publish();
        String oldKey = jdbc.queryForObject("select storage_key from listing_image where listing_id = ?",
                String.class, id);
        var owner = client();
        login(owner, "owner_user");
        String token = csrf(get(owner, "/books/" + id + "/edit").body());
        var fields = Map.ofEntries(
                Map.entry("title", "改为赠送的教材"),
                Map.entry("category", "TEXTBOOK"),
                Map.entry("description", "已更换照片"),
                Map.entry("conditionCode", "LIGHT_USE"),
                Map.entry("noteLevel", "FEW"),
                Map.entry("transferType", "GIFT"),
                Map.entry("price", "999"),
                Map.entry("contactType", "WECHAT"),
                Map.entry("contactValue", "original-contact"),
                Map.entry("scopes[0].majorId", "0"),
                Map.entry("scopes[0].semester", "UNDEFINED"),
                Map.entry("tags", "95"),
                Map.entry("expectedVersion", "0"));
        var response = multipartPost(owner, "/books/" + id + "?_csrf=" + encode(token), fields, png());
        assertThat(response.statusCode()).isEqualTo(302);

        String newKey = jdbc.queryForObject("select storage_key from listing_image where listing_id = ?",
                String.class, id);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(Files.exists(UPLOAD_ROOT.resolve(newKey))).isTrue();
        assertThat(Files.exists(UPLOAD_ROOT.resolve(oldKey))).isFalse();
        assertThat(jdbc.queryForObject("select transfer_type from listing where id = ?", String.class, id))
                .isEqualTo("GIFT");
        assertThat(jdbc.queryForObject("select price from listing where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                "select count(*) from listing_event where listing_id = ? and action_code = 'CHANGE_TRANSFER'",
                Long.class, id)).isEqualTo(1);
    }

    @Test
    void listingDescriptionCanBeLeftEmpty() throws Exception {
        var form = new PublishForm();
        form.setTitle("无说明资料");
        form.setCategory("TEXTBOOK");
        form.setConditionCode("LIGHT_USE");
        form.setNoteLevel("NONE");
        form.setTransferType("GIFT");
        form.setContactType("WECHAT");
        form.setContactValue("optional-fields-contact");
        form.setPhotos(List.of(new MockMultipartFile("photos", "cover.png", "image/png", png())));
        long id = publisher.publish(form, "owner_user");

        assertThat(jdbc.queryForObject("select description from listing where id = ?", String.class, id)).isNull();

        var detail = get(client(), "/books/" + id);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("无说明资料", "未填写");
        assertThat(get(client(), "/market").body()).contains("data-id=\"" + id + "\"", "无说明资料");

        var owner = client();
        login(owner, "owner_user");
        assertThat(get(owner, "/my/listings").body()).contains("无说明资料");
    }
}
