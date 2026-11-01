package cn.edu.hust.bookloop;

import cn.edu.hust.bookloop.image.ImageStorage;
import cn.edu.hust.bookloop.publish.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:publish_integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("preview")
class PublishIntegrationTest {
    static final Path ROOT = tempDirectory();
    static Path tempDirectory() {
        try { return Files.createTempDirectory("bookloop-publish-test-"); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("bookloop.upload-dir", ROOT::toString);
    }
    @Value("${local.server.port}") int port;
    @Autowired PublishService publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean ImageStorage storage;

    @BeforeEach void seed() {
        String hash = encoder.encode("password123");
        for (int i = 0; i < 3; i++) jdbc.update("""
            insert into app_user (id, username, password_hash, role, created_at, updated_at)
            values (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, 9101+i, List.of("real_owner", "real_buyer", "real_admin").get(i), hash, i == 2 ? "ADMIN" : "USER");
        jdbc.update("insert into major (id,name,enabled) values (91,'测试专业',true)");
        jdbc.update("insert into tag (id,name,enabled) values (91,'测试标签',true)");
    }
    @AfterEach void clean() throws IOException {
        for (String table : List.of("listing_event", "listing_image", "listing_contact", "listing_tag", "listing_scope", "listing", "tag", "major", "app_user")) jdbc.update("delete from " + table);
        try (var files = Files.walk(ROOT)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) if (!path.equals(ROOT)) Files.delete(path);
        }
    }
    @AfterAll static void removeRoot() throws IOException { Files.delete(ROOT); }

    byte[] png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
    PublishForm form() throws IOException {
        var form = new PublishForm(); form.setTitle("真实发布教材"); form.setCategory("TEXTBOOK");
        form.setDescription("发布说明"); form.setConditionCode("LIKE_NEW"); form.setNoteLevel("NONE");
        form.setPrice(new java.math.BigDecimal("12.50")); form.setContactType("WECHAT"); form.setContactValue("private-contact");
        form.setTags(List.of(91L)); form.setPhotos(List.of(new MockMultipartFile("photos", "../../evil.html", "image/png", png())));
        return form;
    }
    long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    List<Path> files() throws IOException {
        try (var paths = Files.walk(ROOT)) { return paths.filter(Files::isRegularFile).toList(); }
    }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    void login(HttpClient client, String name) throws Exception {
        var fields = Map.of("username",name,"password","password123","_csrf",csrf(get(client,"/login").body()));
        String body = fields.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        var result = client.send(HttpRequest.newBuilder(uri("/login")).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(302);
    }
    String imageUrl(long book) {
        Long image = jdbc.queryForObject("select id from listing_image where listing_id=? order by sort_order limit 1",Long.class,book);
        return "/books/"+book+"/images/"+image;
    }
    @Test void publishesMultipartAndDisplaysImageWithBoundOwner() throws Exception {
        var client = client(); login(client,"real_owner");
        String token = csrf(get(client,"/books/new").body());
        var fields = Map.ofEntries(Map.entry("title","真实发布教材"),Map.entry("category","TEXTBOOK"),Map.entry("description","发布说明"),
                Map.entry("conditionCode","LIKE_NEW"),Map.entry("noteLevel","NONE"),Map.entry("transferType","SALE"),Map.entry("price","12.50"),
                Map.entry("contactType","WECHAT"),Map.entry("contactValue","private-contact"),Map.entry("scopes[0].majorId","0"),
                Map.entry("scopes[0].semester","UNDEFINED"),Map.entry("tags","91"),Map.entry("ownerId","9102"),Map.entry("lifecycleStatus","COMPLETED"));
        var bytes = new ByteArrayOutputStream(); String boundary="PublishBoundary";
        for (var entry:fields.entrySet()) bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(png()); bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var response=client.send(HttpRequest.newBuilder(uri("/books?_csrf="+enc(token))).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(302);
        long id=jdbc.queryForObject("select id from listing",Long.class);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/books/"+id);
        assertThat(jdbc.queryForObject("select owner_id from listing",Long.class)).isEqualTo(9101L);
        assertThat(jdbc.queryForObject("select lifecycle_status from listing",String.class)).isEqualTo("ACTIVE");
        for(String table:List.of("listing_scope","listing_tag","listing_contact","listing_image","listing_event")) assertThat(count(table)).as(table).isEqualTo(1);
        String snapshot=jdbc.queryForObject("select after_snapshot from listing_event",String.class);
        assertThat(snapshot).contains("PUBLISH".equals("ignored")?"":"ACTIVE", "12.50").doesNotContain("private-contact", "password");
        assertThat(get(client,"/market").body()).contains(imageUrl(id));
        assertThat(get(client,"/books/"+id).body()).contains(imageUrl(id),"private-contact").doesNotContain("封面占位图");
        var anonymous=client(); var image=anonymous.send(HttpRequest.newBuilder(uri(imageUrl(id))).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200); assertThat(ImageIO.read(new ByteArrayInputStream(image.body())).getWidth()).isEqualTo(3);
        assertThat(image.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(get(anonymous,"/books/"+id).body()).doesNotContain("private-contact");
        String key=jdbc.queryForObject("select storage_key from listing_image",String.class);
        assertThat(key).matches("listings/[0-9a-f-]{36}\\.png");
        assertThat(new ImageStorage(ROOT.toString()).read(key)).isEqualTo(image.body());
        assertThat(get(anonymous,"/uploads/"+key).statusCode()).isEqualTo(403);
    }
    @Test void giftAndMultipleImagesPersistInOrder() throws Exception {
        var f=form();f.setTransferType("GIFT");f.setPrice(new java.math.BigDecimal("900"));
        f.setPhotos(List.of(f.getPhotos().getFirst(),new MockMultipartFile("photos","second.png","image/png",png())));
        long id=publisher.publish(f,"real_owner");
        assertThat(jdbc.queryForObject("select price from listing where id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForList("select sort_order from listing_image order by sort_order",Integer.class)).containsExactly(1,2);
        assertThat(files()).hasSize(2);
        assertThat(get(client(),"/books/"+id).body()).contains("免费赠送","待赠送");
    }
    @Test void imageVisibilityMatchesBookAndRejectsWrongAssociationOrTraversal() throws Exception {
        long id=publisher.publish(form(),"real_owner"), other=publisher.publish(form(),"real_owner");
        String url=imageUrl(id);
        assertThat(get(client(),url.replace("/books/"+id+"/", "/books/"+other+"/")).statusCode()).isEqualTo(404);
        for(String state:List.of("RESERVED","COMPLETED","WITHDRAWN")) {
            jdbc.update("update listing set lifecycle_status=? where id=?",state,id);
            assertThat(get(client(),url).statusCode()).isEqualTo(state.equals("RESERVED")?200:410);
        }
        jdbc.update("update listing set lifecycle_status='ACTIVE',admin_blocked=true where id=?",id);
        for(String user:List.of("real_owner","real_admin","real_buyer")) {
            var c=client();login(c,user);assertThat(get(c,url).statusCode()).isEqualTo(user.equals("real_buyer")?410:200);
        }
        var owner=client();login(owner,"real_owner");
        jdbc.update("update listing_image set storage_key='../pom.xml' where listing_id=?",id);
        assertThat(get(owner,url).statusCode()).isEqualTo(404);
    }
    @Test void invalidImagesAndDisabledOptionsCreateNothing() throws Exception {
        for(var photo:List.of(new MockMultipartFile("photos","fake.png","image/png","not an image".getBytes()),
                new MockMultipartFile("photos","wrong.jpg","image/jpeg",png()),
                new MockMultipartFile("photos","empty.png","image/png",new byte[0]))) {
            var f=form();f.setPhotos(List.of(photo));
            assertThatThrownBy(()->publisher.publish(f,"real_owner")).isInstanceOf(PublishFailure.class);
            assertThat(count("listing")).isZero();assertThat(files()).isEmpty();
        }
        jdbc.update("update tag set enabled=false where id=91");
        assertThatThrownBy(()->publisher.publish(form(),"real_owner")).isInstanceOf(PublishFailure.class);
    }
    @Test void databaseFailureRollsBackAllRowsAndFiles() throws Exception {
        jdbc.execute("alter table listing_event add constraint reject_publish_test check (action_code <> 'PUBLISH')");
        try {
            assertThatThrownBy(()->publisher.publish(form(),"real_owner")).isInstanceOf(RuntimeException.class);
            for(String table:List.of("listing","listing_scope","listing_tag","listing_contact","listing_image","listing_event")) assertThat(count(table)).as(table).isZero();
            assertThat(files()).isEmpty();
        } finally {jdbc.execute("alter table listing_event drop constraint reject_publish_test");}
    }
    @Test void secondFileFailureCleansFirstFileAndDatabase() throws Exception {
        var calls=new AtomicInteger();
        doAnswer(invocation->{if(calls.incrementAndGet()==2)throw new PublishFailure("模拟磁盘失败");return invocation.callRealMethod();}).when(storage).save(any());
        var f=form();f.setPhotos(List.of(f.getPhotos().getFirst(),new MockMultipartFile("photos","second.png","image/png",png())));
        assertThatThrownBy(()->publisher.publish(f,"real_owner")).isInstanceOf(PublishFailure.class);
        assertThat(count("listing")).isZero();assertThat(count("listing_image")).isZero();assertThat(files()).isEmpty();
    }
    @Test void decodesRealWebpAndRemovesTrailingData() throws Exception {
        byte[] webp=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        var image=storage.prepare(new MockMultipartFile("photos","photo.webp","image/webp",webp));
        assertThat(image.mime()).isEqualTo("image/png");assertThat(ImageIO.read(new ByteArrayInputStream(image.bytes()))).isNotNull();
        var appended=new ByteArrayOutputStream();appended.write(png());appended.write("<script>payload</script>".getBytes(StandardCharsets.UTF_8));
        var cleaned=storage.prepare(new MockMultipartFile("photos","photo.png","image/png",appended.toByteArray()));
        assertThat(new String(cleaned.bytes(),StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
    }
}
