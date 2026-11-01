package cn.edu.hust.bookloop;

import java.io.ByteArrayOutputStream;
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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:publish_form_test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("preview")
class PublishFormTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setup() {
        clean();
        jdbc.update("""
            insert into app_user (id, username, password_hash, role, default_contact_type, default_contact_value, created_at, updated_at)
            values (8001, 'publish_user', ?, 'USER', 'WECHAT', 'default-contact', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, encoder.encode("password123"));
        jdbc.update("insert into major (id, name, enabled) values (81, '可用专业', true), (82, '已停用专业', false)");
        jdbc.update("insert into tag (id, name, enabled) values (81, '可用标签', true), (82, '已停用标签', false)");
    }
    @AfterEach void clean() {
        jdbc.update("delete from app_user where id = 8001");
        jdbc.update("delete from major where id in (81, 82)");
        jdbc.update("delete from tag where id in (81, 82)");
    }
    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> get(HttpClient c, String path) throws Exception {
        return c.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String body) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    private HttpResponse<String> login(HttpClient c) throws Exception {
        var fields = Map.of("_csrf", token(get(c, "/login").body()), "username", "publish_user", "password", "password123");
        var body = fields.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        return c.send(HttpRequest.newBuilder(uri("/login")).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Map<String, String> valid() {
        return new LinkedHashMap<>(Map.ofEntries(Map.entry("title", "测试资料"), Map.entry("category", "TEXTBOOK"),
            Map.entry("description", "资料说明"), Map.entry("conditionCode", "LIGHT_USE"), Map.entry("noteLevel", "NONE"),
            Map.entry("transferType", "SALE"), Map.entry("price", "12.50"), Map.entry("contactType", "QQ"),
            Map.entry("contactValue", "my-contact"), Map.entry("scopes[0].majorId", "0"),
            Map.entry("scopes[0].semester", "UNDEFINED"), Map.entry("tags", "81")));
    }
    private HttpResponse<String> multipart(HttpClient c, String token, Map<String, String> fields, int count, int size, String mime) throws Exception {
        var out = new ByteArrayOutputStream();
        String boundary = "BookloopBoundary";
        for (var entry : fields.entrySet()) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < count; i++) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"photo.png\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(new byte[size]);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        String path = "/books/preview" + (token == null ? "" : "?_csrf=" + enc(token));
        return c.send(HttpRequest.newBuilder(uri(path)).header("Accept", "text/html").header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void requiresLoginAndPrefillsOnlyCurrentUserDefaults() throws Exception {
        var c = client();
        assertThat(get(c, "/books/new").statusCode()).isEqualTo(302);
        assertThat(login(c).headers().firstValue("Location").orElseThrow()).endsWith("/books/new");
        var page = get(c, "/books/new");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("multipart/form-data", "default-contact", "可用专业", "可用标签", "大五下", "研究生", "未定义", "发布成功后可在教材市场查看")
            .doesNotContain("已停用专业", "已停用标签", "password_hash", "demo-state.js");
        assertThat(get(c, "/js/publish-form.js").statusCode()).isEqualTo(200);
    }
    @Test void validatesMultipartAndNormalizesGiftWithoutPublishing() throws Exception {
        var c = client(); login(c);
        String csrf = token(get(c, "/books/new").body());
        assertThat(multipart(c, csrf, valid(), 1, 8, "image/png").body()).contains("表单校验通过，尚未发布");
        var gift = valid(); gift.put("transferType", "GIFT"); gift.put("price", "999");
        gift.put("scopes[1].majorId", "81"); gift.put("scopes[1].semester", "Y5_S2");
        var response = multipart(c, csrf, gift, 6, 8, "image/webp");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("表单校验通过，尚未发布", "免费赠送", "value=\"0\"", "6 张照片");
        assertThat(jdbc.queryForObject("select count(*) from listing", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select default_contact_value from app_user where id=8001", String.class)).isEqualTo("default-contact");
    }
    @Test void rejectsDuplicateDisabledAndInvalidFieldsAndRetainsText() throws Exception {
        var c = client(); login(c);
        String csrf = token(get(c, "/books/new").body());
        var bad = valid(); bad.put("title", "<script>bad()</script>"); bad.put("price", "-1"); bad.put("tags", "82");
        bad.put("scopes[1].majorId", "0"); bad.put("scopes[1].semester", "UNDEFINED");
        var response = multipart(c, csrf, bad, 1, 8, "image/png");
        assertThat(response.body()).contains("适用范围不能重复", "已停用的标签不可新增", "出售价格须大于0", "&lt;script&gt;", "文字输入已保留")
            .doesNotContain("表单校验通过", "<script>bad()</script>");
        bad = valid(); bad.put("scopes[0].majorId", "82"); bad.put("contactValue", " ");
        assertThat(multipart(c, csrf, bad, 1, 8, "image/png").body()).contains("已停用的专业不可新增").doesNotContain("表单校验通过");
    }
    @Test void enforcesPhotoLimitsAndCsrf() throws Exception {
        var c = client(); login(c);
        String csrf = token(get(c, "/books/new").body());
        assertThat(multipart(c, null, valid(), 1, 8, "image/png").statusCode()).isEqualTo(403);
        for (int count : List.of(0, 7)) assertThat(multipart(c, csrf, valid(), count, 8, "image/png").body()).contains("请选择1—6张实物照片");
        assertThat(multipart(c, csrf, valid(), 1, 8, "text/plain").body()).contains("照片须为JPG");
        var tooLarge = multipart(c, csrf, valid(), 1, 5 * 1024 * 1024 + 1, "image/png");
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        assertThat(tooLarge.body()).contains("照片超过上传限制");
    }
}
