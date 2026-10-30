package cn.edu.hust.bookloop.market;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MarketOptions {
    private MarketOptions() {}
    public static final Map<String, String> SEMESTERS = options(
        "Y1_S1", "大一上", "Y1_S2", "大一下", "Y2_S1", "大二上", "Y2_S2", "大二下",
        "Y3_S1", "大三上", "Y3_S2", "大三下", "Y4_S1", "大四上", "Y4_S2", "大四下",
        "Y5_S1", "大五上", "Y5_S2", "大五下", "POSTGRAD", "研究生", "UNDEFINED", "未定义");
    public static final Map<String, String> CATEGORIES = options(
        "TEXTBOOK", "教材", "READING", "推荐阅读书籍", "EXAM", "考试复习资料");
    public static final Map<String, String> CONDITIONS = options(
        "LIKE_NEW", "近全新", "LIGHT_USE", "少量使用痕迹", "HEAVY_USE", "明显使用痕迹");
    private static Map<String, String> options(String... pairs) {
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(result);
    }
}
