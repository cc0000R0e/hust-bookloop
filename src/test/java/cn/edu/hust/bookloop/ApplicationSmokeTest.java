package cn.edu.hust.bookloop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("preview")
class ApplicationSmokeTest {
    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rendersTemplateAndServesPrototypeAssets() throws Exception {
        var home = get("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("本地预览模式", "/prototype/index.html");
        assertThat(get("/prototype/index.html").statusCode()).isEqualTo(200);
        assertThat(get("/prototype/js/books.js").body()).contains("const books");
        assertThat(get("/prototype/css/style.css").statusCode()).isEqualTo(200);
    }

    @Test
    void doesNotExposeServerWriteEndpointsOrProjectSources() throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/prototype/index.html"))
                        .POST(HttpRequest.BodyPublishers.ofString("test")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(get("/database/schema.sql").statusCode()).isEqualTo(403);
    }
}
