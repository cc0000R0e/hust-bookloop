package cn.edu.hust.bookloop.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReportOptions {
    public static final Map<String, String> REASONS;

    static {
        var reasons = new LinkedHashMap<String, String>();
        reasons.put("INACCURATE", "信息不准确");
        reasons.put("FALSE_LISTING", "疑似虚假发布");
        reasons.put("INAPPROPRIATE", "内容不当");
        reasons.put("OTHER", "其他问题");
        REASONS = Collections.unmodifiableMap(reasons);
    }

    private ReportOptions() {}
}
