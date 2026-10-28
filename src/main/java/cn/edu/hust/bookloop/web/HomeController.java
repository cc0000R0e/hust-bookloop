package cn.edu.hust.bookloop.web;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final Environment environment;

    public HomeController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("databaseMode", environment.acceptsProfiles(Profiles.of("mysql"))
                ? "MySQL 连接模式" : "本地预览模式（H2临时数据库）");
        return "home";
    }
}
