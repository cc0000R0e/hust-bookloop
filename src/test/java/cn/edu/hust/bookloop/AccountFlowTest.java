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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("preview")
class AccountFlowTest {
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
    @Test
    void registersAuthenticatesAndLogsOutWithDatabaseCredentials() throws Exception {
        var client = client();
        assertThat(get(client,"/account").statusCode()).isEqualTo(302);
        var registrationPage = get(client,"/register").body();
        assertThat(registrationPage).doesNotContain("nickname", "昵称");
        var csrf = token(registrationPage);
        var registered = post(client,"/register",Map.of("_csrf",csrf,"username","Student_test","password","secure123","confirmation","secure123","role","ADMIN"));
        assertThat(registered.statusCode()).isEqualTo(302);
        var user = users.findByUsername("student_test").orElseThrow();
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getPasswordHash()).isNotEqualTo("secure123");
        assertThat(encoder.matches("secure123",user.getPasswordHash())).isTrue();
        csrf = token(get(client,"/login").body());
        var wrong = post(client,"/login",Map.of("_csrf",csrf,"username","student_test","password","wrong123"));
        assertThat(wrong.headers().firstValue("Location").orElseThrow()).endsWith("/login?error");
        assertThat(get(client,"/account").statusCode()).isEqualTo(302);
        csrf = token(get(client,"/login").body());
        var login = post(client,"/login",Map.of("_csrf",csrf,"username","STUDENT_TEST","password","secure123"));
        assertThat(login.headers().firstValue("Location").orElseThrow()).endsWith("/account");
        var account = get(client,"/account");
        assertThat(account.body()).contains("student_test").doesNotContain(user.getPasswordHash());
        assertThat(post(client,"/logout",Map.of("_csrf",token(account.body()))).statusCode()).isEqualTo(302);
        assertThat(get(client,"/account").statusCode()).isEqualTo(302);
    }
    @Test
    void rejectsMissingCsrfMismatchAndDuplicateAccountsWithoutEchoingPasswords() throws Exception {
        var client = client();
        assertThat(post(client,"/register",Map.of("username","test_missing")).statusCode()).isEqualTo(403);
        var registrationPage = get(client,"/register").body();
        assertThat(registrationPage).doesNotContain("nickname", "昵称");
        var csrf = token(registrationPage);
        var mismatch = post(client,"/register",Map.of("_csrf",csrf,"username","dup_test","password","never_echo_123","confirmation","other123"));
        assertThat(mismatch.body()).contains("两次输入的密码不一致").doesNotContain("never_echo_123");
        assertThat(users.findByUsername("dup_test")).isEmpty();
        var fields = Map.of("_csrf",csrf,"username","dup_test","password","secure123","confirmation","secure123");
        assertThat(post(client,"/register",fields).statusCode()).isEqualTo(302);
        assertThat(post(client,"/register",fields).body()).contains("账号已被使用");
    }
}
