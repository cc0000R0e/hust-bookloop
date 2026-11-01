package cn.edu.hust.bookloop.admin;

import cn.edu.hust.bookloop.account.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCatalogService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public AdminCatalogService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public record Option(long id, String name, boolean enabled) {}
    public record History(long id, long listingId, String title, String reporter, String reason,
                          String description, String status, String handler, String resolution,
                          String createdAt, String handledAt) {}
    public record Event(long id, long listingId, String title, String actor, String role,
                        String action, String reason, String before, String after, String createdAt) {}

    private void admin(String username) {
        var user = users.findByUsername(username)
                .orElseThrow(() -> new AdminFailure(HttpStatus.UNAUTHORIZED, "请重新登录。"));
        if (!"ADMIN".equals(user.getRole())) throw new AdminFailure(HttpStatus.FORBIDDEN, "只有管理员可以执行此操作。");
    }

    private String table(String kind) {
        return switch (kind) {
            case "majors" -> "major";
            case "tags" -> "tag";
            default -> throw new AdminFailure(HttpStatus.BAD_REQUEST, "基础选项类型无效。");
        };
    }

    public Page<Option> options(String username, String kind, int page) {
        admin(username);
        String table = table(kind);
        var pageable = PageRequest.of(Math.max(0, Math.min(page, 100_000)), 20);
        var rows = jdbc.query("select id, name, enabled from " + table + " order by id desc limit ? offset ?",
                (rs, n) -> new Option(rs.getLong("id"), rs.getString("name"), rs.getBoolean("enabled")),
                pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(rows, pageable, jdbc.queryForObject("select count(*) from " + table, Long.class));
    }

    @Transactional
    public void save(String username, String kind, Long id, String rawName, boolean enabled) {
        admin(username);
        String table = table(kind);
        String name = rawName == null ? "" : rawName.strip();
        int max = "major".equals(table) ? 40 : 30;
        if (name.isBlank() || name.codePointCount(0, name.length()) > max)
            throw new AdminFailure(HttpStatus.BAD_REQUEST, "名称不能为空，且不能超过" + max + "字。");
        if ("major".equals(table) && "不限专业".equals(name))
            throw new AdminFailure(HttpStatus.BAD_REQUEST, "不限专业是固定选项，不参与管理。");
        if (id == null) {
            jdbc.update("insert into " + table + " (name, enabled) values (?, ?)", name, enabled);
        } else {
            if (id <= 0 || jdbc.update("update " + table + " set name = ?, enabled = ? where id = ?", name, enabled, id) == 0)
                throw new AdminFailure(HttpStatus.NOT_FOUND, "选项不存在，请刷新页面。");
        }
    }

    public Page<History> history(String username, String status, int page) {
        admin(username);
        if (!List.of("all", "RESOLVED", "REJECTED").contains(status))
            throw new AdminFailure(HttpStatus.BAD_REQUEST, "处理状态无效。");
        String where = "all".equals(status) ? "r.status in ('RESOLVED', 'REJECTED')" : "r.status = ?";
        Object[] filter = "all".equals(status) ? new Object[0] : new Object[]{status};
        var pageable = PageRequest.of(Math.max(0, Math.min(page, 100_000)), 20);
        var args = new java.util.ArrayList<Object>(List.of(filter));
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        var rows = jdbc.query("""
                select r.*, l.title, u.username reporter, h.username handler
                from report r left join listing l on l.id = r.listing_id
                left join app_user u on u.id = r.reporter_id left join app_user h on h.id = r.handler_id
                """ + " where " + where + " order by r.handled_at desc, r.id desc limit ? offset ?",
                (rs, n) -> new History(rs.getLong("id"), rs.getLong("listing_id"), rs.getString("title"),
                        rs.getString("reporter"), cn.edu.hust.bookloop.report.ReportOptions.REASONS.getOrDefault(rs.getString("reason_code"), rs.getString("reason_code")),
                        rs.getString("description"), "RESOLVED".equals(rs.getString("status")) ? "已处理" : "已驳回",
                        rs.getString("handler"), rs.getString("resolution_note"),
                        time(rs.getObject("created_at", LocalDateTime.class)), time(rs.getObject("handled_at", LocalDateTime.class))), args.toArray());
        return new PageImpl<>(rows, pageable, jdbc.queryForObject("select count(*) from report r where " + where, Long.class, filter));
    }

    public Page<Event> events(String username, Long listingId, int page) {
        admin(username);
        if (listingId != null && listingId <= 0) throw new AdminFailure(HttpStatus.BAD_REQUEST, "资料编号应为正整数。");
        String where = listingId == null ? "1=1" : "e.listing_id = ?";
        Object[] filter = listingId == null ? new Object[0] : new Object[]{listingId};
        var pageable = PageRequest.of(Math.max(0, Math.min(page, 100_000)), 20);
        var args = new java.util.ArrayList<Object>(List.of(filter));
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        var rows = jdbc.query("""
                select e.*, l.title, u.username actor from listing_event e
                left join listing l on l.id = e.listing_id left join app_user u on u.id = e.actor_id
                """ + " where " + where + " order by e.created_at desc, e.id desc limit ? offset ?",
                (rs, n) -> new Event(rs.getLong("id"), rs.getLong("listing_id"), rs.getString("title"),
                        rs.getString("actor"), rs.getString("actor_role"), action(rs.getString("action_code")),
                        rs.getString("reason"), rs.getString("before_snapshot"), rs.getString("after_snapshot"),
                        time(rs.getObject("created_at", LocalDateTime.class))), args.toArray());
        return new PageImpl<>(rows, pageable, jdbc.queryForObject("select count(*) from listing_event e where " + where, Long.class, filter));
    }

    private String time(LocalDateTime time) {
        return time == null ? "未填写" : time.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String action(String code) {
        return switch (code) {
            case "PUBLISH" -> "发布资料";
            case "RESERVE" -> "标记预订";
            case "CANCEL_RESERVATION" -> "取消预订";
            case "COMPLETE" -> "完成交易";
            case "WITHDRAW" -> "发布者下架";
            case "RELIST" -> "重新上架";
            case "CHANGE_TRANSFER" -> "变更出售／赠送方式";
            case "ADMIN_BLOCK" -> "管理员下架";
            case "ADMIN_RELEASE" -> "解除管理员限制";
            default -> code;
        };
    }
}
