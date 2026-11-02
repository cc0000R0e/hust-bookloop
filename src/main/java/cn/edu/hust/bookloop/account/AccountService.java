package cn.edu.hust.bookloop.account;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public AccountService(UserRepository users, PasswordEncoder encoder, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.users = users; this.encoder = encoder;
    }
    public static String normalize(String username) { return username.strip().toLowerCase(Locale.ROOT); }
    @Transactional
    public void register(String username, String password, String confirmation) {
        register(username, password, confirmation, null);
    }

    @Transactional
    public void register(String username, String password, String confirmation, Long majorId) {
        username = normalize(username);
        if (!username.matches("[a-z0-9_]{4,20}")) throw new IllegalArgumentException("账号应为4—20位字母、数字或下划线。");
        validatePassword(password, confirmation);
        if (users.existsByUsername(username)) throw new IllegalArgumentException("此账号已被使用，请更换账号。");
        validateMajor(majorId);
        var user = new AppUser(username, encoder.encode(password));
        user.setMajorId(majorId);
        users.saveAndFlush(user);
    }

    private void validateMajor(Long majorId) {
        if (majorId == null) return;
        var enabled = jdbc.queryForList("select enabled from major where id = ? for update", Boolean.class, majorId);
        if (majorId <= 0 || enabled.isEmpty() || !Boolean.TRUE.equals(enabled.getFirst()))
            throw new IllegalArgumentException("请选择有效且启用的专业，或选择未设置。");
    }

    @Transactional
    public void updateMajor(String username, Long majorId) {
        var user = users.findByUsername(username).orElseThrow();
        if (java.util.Objects.equals(user.getMajorId(), majorId)) return;
        validateMajor(majorId);
        user.setMajorId(majorId);
    }

    private void validatePassword(String password, String confirmation) {
        if (password.isBlank() || password.length() < 6 || password.length() > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("密码应为6—64位，UTF-8编码长度不能超过72字节。");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("两次输入的密码不一致。");
    }

    @Transactional
    public void updateContact(String username, String type, String value) {
        type = type.strip();
        value = value.strip();
        if (type.isEmpty() && value.isEmpty()) {
            users.findByUsername(username).orElseThrow().updateContact(null, null);
            return;
        }
        if (!java.util.Set.of("WECHAT", "QQ", "PHONE").contains(type) || value.isEmpty()
                || value.codePointCount(0, value.length()) > 100) {
            throw new IllegalArgumentException("请选择联系方式类型，并填写1—100个字符的联系账号；清空时请同时清空两项。");
        }
        users.findByUsername(username).orElseThrow().updateContact(type, value);
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String password, String confirmation) {
        var user = users.findByUsername(username).orElseThrow();
        if (!encoder.matches(oldPassword, user.getPasswordHash()))
            throw new IllegalArgumentException("原密码错误。");
        validatePassword(password, confirmation);
        user.changePassword(encoder.encode(password));
    }
}
