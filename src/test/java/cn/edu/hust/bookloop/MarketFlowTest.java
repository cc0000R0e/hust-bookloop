package cn.edu.hust.bookloop;

import cn.edu.hust.bookloop.market.MarketFilter;
import cn.edu.hust.bookloop.market.MarketService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("preview")
class MarketFlowTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MarketService market;
    @Value("${local.server.port}") int port;

    @BeforeEach
    void seed() {
        clear();
        jdbc.update("insert into major (id, name, enabled) values (11, '计算机科学与技术', true), (12, '物联网工程', true)");
        jdbc.update("insert into tag (id, name, enabled) values (21, '英语四级', true), (22, '旧版六级', false)");
        book(101, "数据结构", "TEXTBOOK", "SALE", 20, "ACTIVE", false);
        book(102, "英语练习", "EXAM", "GIFT", 0, "ACTIVE", false);
        book(103, "跨专业书", "TEXTBOOK", "SALE", 10, "ACTIVE", false);
        book(104, "未定义学期", "READING", "SALE", 5, "ACTIVE", false);
        book(105, "研究生资料", "EXAM", "SALE", 30, "ACTIVE", false);
        book(106, "大五资料", "TEXTBOOK", "SALE", 30, "ACTIVE", false);
        book(107, "已预订", "EXAM", "GIFT", 0, "RESERVED", false);
        book(108, "已完成", "TEXTBOOK", "SALE", 1, "COMPLETED", false);
        book(109, "已下架", "TEXTBOOK", "SALE", 1, "WITHDRAWN", false);
        book(110, "管理员限制", "TEXTBOOK", "SALE", 1, "ACTIVE", true);
        book(111, "100%_提升", "READING", "SALE", 15, "ACTIVE", false);
        book(112, "<script>alert(1)</script>", "READING", "SALE", 16, "ACTIVE", false);
        scope(1, 101, 11L, "Y1_S1");
        scope(2, 101, null, "Y1_S1");
        scope(3, 102, null, "Y1_S1");
        scope(4, 103, 11L, "Y2_S1");
        scope(5, 103, 12L, "Y1_S1");
        scope(6, 104, null, "UNDEFINED");
        scope(7, 105, null, "POSTGRAD");
        scope(8, 106, null, "Y5_S2");
        jdbc.update("insert into listing_tag (listing_id, tag_id) values (101, 21), (101, 22), (102, 21), (111, 22)");
    }

    @AfterEach
    void clear() {
        jdbc.update("delete from listing_tag where listing_id between 100 and 199");
        jdbc.update("delete from listing_scope where listing_id between 100 and 199");
        jdbc.update("delete from listing where id between 100 and 199");
        jdbc.update("delete from major where id in (11, 12)");
        jdbc.update("delete from tag where id in (21, 22)");
    }

    private void book(long id, String title, String category, String transfer, int price, String status, boolean blocked) {
        jdbc.update("""
                insert into listing (id, title, category, author, course_name, condition_code,
                transfer_type, price, lifecycle_status, admin_blocked, published_at)
                values (?, ?, ?, 'Example Author', '算法课程', 'LIGHT_USE', ?, ?, ?, ?, '2026-01-01 00:00:00')
                """, id, title, category, transfer, price, status, blocked);
    }

    private void scope(long id, long book, Long major, String semester) {
        jdbc.update("insert into listing_scope (id, listing_id, scope_type, major_id, semester_code) values (?, ?, ?, ?, ?)",
                id, book, major == null ? "ALL" : "SPECIFIC", major, semester);
    }

    private MarketService.Result search(Map<String, String> input, Long... tags) {
        return market.search(new MarketFilter(input.getOrDefault("q", ""),
                input.containsKey("major") ? Long.valueOf(input.get("major")) : null,
                input.getOrDefault("semester", ""), input.getOrDefault("category", ""), List.of(tags),
                input.getOrDefault("mode", "all"),
                input.containsKey("minPrice") ? new BigDecimal(input.get("minPrice")) : null,
                input.containsKey("maxPrice") ? new BigDecimal(input.get("maxPrice")) : null,
                input.getOrDefault("condition", ""), input.getOrDefault("sort", "new"),
                Integer.parseInt(input.getOrDefault("page", "0"))));
    }

    private List<Long> ids(MarketService.Result result) {
        return result.page().getContent().stream().map(MarketService.Card::id).toList();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void showsOnlyAvailableBooksAndEscapesContent() throws Exception {
        assertThat(ids(search(Map.of()))).containsExactly(112L, 111L, 106L, 105L, 104L, 103L, 102L, 101L);
        var response = get("/market");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("数据结构", "免费赠送", "&lt;script&gt;", "旧版六级")
                .doesNotContain("<script>alert(1)</script>", "data-id=\"107\"", "data-id=\"110\"",
                    "books.js", "demo-state.js", "password_hash", "contact_value");
        assertThat(get("/css/market.css").statusCode()).isEqualTo(200);
    }

    @Test
    void matchesMajorAndSemesterWithinSameScopeWithoutDuplicates() {
        assertThat(ids(search(Map.of("major", "11", "semester", "Y1_S1")))).containsExactly(102L, 101L);
        assertThat(ids(search(Map.of("major", "12", "semester", "Y1_S1")))).containsExactly(103L, 102L, 101L);
        assertThat(ids(search(Map.of("major", "0", "semester", "Y1_S1")))).containsExactly(102L, 101L);
        assertThat(ids(search(Map.of("semester", "UNDEFINED")))).containsExactly(104L);
        assertThat(ids(search(Map.of("semester", "POSTGRAD")))).containsExactly(105L);
        assertThat(ids(search(Map.of("semester", "Y5_S2")))).containsExactly(106L);
    }

    @Test
    void combinesAnyTagWithOtherFiltersAndSearchesLiteralKeywords() {
        assertThat(ids(search(Map.of(), 21L, 22L))).containsExactly(111L, 102L, 101L);
        assertThat(ids(search(Map.of("category", "EXAM", "major", "11", "semester", "Y1_S1", "mode", "free"), 21L, 22L)))
                .containsExactly(102L);
        assertThat(ids(search(Map.of("q", "旧版六级")))).containsExactly(111L, 101L);
        assertThat(ids(search(Map.of("q", "EXAMPLE AUTHOR")))).hasSize(8);
        assertThat(ids(search(Map.of("q", "算法课程")))).hasSize(8);
        assertThat(ids(search(Map.of("q", "%_")))).containsExactly(111L);
        assertThat(ids(search(Map.of("q", "' OR 1=1 --")))).isEmpty();
        assertThat(ids(search(Map.of("q", "数据结构")))).containsExactly(101L);
    }

    @Test
    void sortsAndFiltersPricesIncludingGifts() {
        assertThat(ids(search(Map.of("sort", "low")))).containsExactly(102L, 104L, 103L, 111L, 112L, 101L, 106L, 105L);
        assertThat(ids(search(Map.of("sort", "high", "minPrice", "10", "maxPrice", "20"))))
                .containsExactly(101L, 112L, 111L, 103L);
        assertThat(ids(search(Map.of("mode", "free", "minPrice", "1")))).isEmpty();
        assertThat(ids(search(Map.of("mode", "sale")))).doesNotContain(102L).hasSize(7);
        assertThat(ids(search(Map.of("condition", "LIKE_NEW")))).isEmpty();
    }

    @Test
    void paginatesStablyAndPreservesFiltersInLinks() throws Exception {
        for (long id = 120; id < 145; id++) {
            book(id, "分页测试", "EXAM", "GIFT", 0, "ACTIVE", false);
            scope(id, id, null, "UNDEFINED");
            jdbc.update("insert into listing_tag (listing_id, tag_id) values (?, 21), (?, 22)", id, id);
        }
        var first = search(Map.of("q", "分页测试", "sort", "low"), 21L, 22L);
        var second = search(Map.of("q", "分页测试", "sort", "low", "page", "1"), 21L, 22L);
        assertThat(first.page().getTotalElements()).isEqualTo(25);
        assertThat(ids(first)).hasSize(20);
        assertThat(ids(second)).containsExactly(124L, 123L, 122L, 121L, 120L).doesNotContainAnyElementsOf(ids(first));
        var response = get("/market?q=%E5%88%86%E9%A1%B5&major=0&semester=UNDEFINED&category=EXAM&tags=21&tags=22&mode=free&sort=low&minPrice=0&maxPrice=1&condition=LIGHT_USE");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("page=1", "tags=21", "tags=22", "major=0", "semester=UNDEFINED", "mode=free", "sort=low", "minPrice=0", "maxPrice=1", "condition=LIGHT_USE", "rel=\"next\"");
    }

    @Test
    void handlesEmptyResultsAndRejectsMalformedFilters() throws Exception {
        var empty = get("/market?q=nonexistent");
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body()).contains("暂时没有匹配的资料", "清除筛选");
        for (String query : List.of("major=-1", "major=999", "tags=999", "semester=invalid", "category=bad",
                "mode=bad", "sort=price;drop", "page=-1", "page=abc", "minPrice=20&maxPrice=1", "minPrice=0.001")) {
            assertThat(get("/market?" + query).statusCode()).as(query).isEqualTo(400);
        }
    }
}
