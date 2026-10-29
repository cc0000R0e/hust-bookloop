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
    public AccountService(UserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    public static String normalize(String username) { return username.strip().toLowerCase(Locale.ROOT); }
    @Transactional
    public void register(String username, String nickname, String password, String confirmation) {
        username = normalize(username);
        nickname = nickname.strip();
        if (!username.matches("[a-z0-9_]{4,20}")) throw new IllegalArgumentException("账号应为4—20位字母、数字或下划线。");
        if (nickname.isBlank() || nickname.codePointCount(0, nickname.length()) > 30)
            throw new IllegalArgumentException("昵称应为1—30个字符。");
        if (password.isBlank() || password.length() < 6 || password.length() > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("密码应为6—64位，UTF-8编码长度不能超过72字节。");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("两次输入的密码不一致。");
        if (users.existsByUsername(username)) throw new IllegalArgumentException("此账号已被使用，请更换账号。");
        users.saveAndFlush(new AppUser(username, encoder.encode(password), nickname));
    }
}
