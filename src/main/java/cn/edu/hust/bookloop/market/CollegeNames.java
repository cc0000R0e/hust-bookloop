package cn.edu.hust.bookloop.market;

import java.util.Arrays;
import java.util.List;

public final class CollegeNames {
    private CollegeNames() {}
    public static String normalize(String value) { return value == null ? "" : value.strip(); }
    public static List<String> split(String value) {
        return Arrays.stream(normalize(value).split("\\|"))
                .map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
