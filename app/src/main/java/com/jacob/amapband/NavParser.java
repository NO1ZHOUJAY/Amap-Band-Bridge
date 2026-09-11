package com.jacob.amapband;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NavParser {
    private static final Pattern REMAINING = Pattern.compile("(?:剩余|还有|再坐|再过|途经|距离下车)\\s*(\\d{1,2})\\s*站|(?<!\\d)(\\d{1,2})\\s*站(?:后)?(?:下车|到达)");
    private static final Pattern CURRENT = Pattern.compile("(?:当前(?:位于|所在|到达)?|已到(?:达)?|到达|驶入|经过)\\s*[:：]?\\s*([^，。；;\\n]{2,24}?)(?:站|，|。|；|;|$)");
    private static final Pattern NEXT = Pattern.compile("(?:下一站|下站)\\s*[:：]?\\s*([^，。；;\\n]{2,24}?)(?:站|，|。|；|;|$)");

    static Result parse(String raw) {
        String text = clean(raw);
        Matcher remainingMatcher = REMAINING.matcher(text);
        Matcher currentMatcher = CURRENT.matcher(text);
        Matcher nextMatcher = NEXT.matcher(text);

        Integer remaining = null;
        if (remainingMatcher.find()) {
            String count = remainingMatcher.group(1) != null ? remainingMatcher.group(1) : remainingMatcher.group(2);
            remaining = Integer.valueOf(count);
        }
        String current = currentMatcher.find() ? normalizeStation(currentMatcher.group(1)) : null;
        String next = nextMatcher.find() ? normalizeStation(nextMatcher.group(1)) : null;
        boolean urgent = text.contains("准备下车") || text.contains("请下车")
                || text.contains("已到站") || text.matches(".*即将到达.{2,24}(?:站|终点).*?");
        boolean useful = remaining != null || current != null || next != null || urgent;
        return new Result(text, current, next, remaining, urgent, useful);
    }

    static boolean isCandidate(String value) {
        if (value == null) return false;
        String text = clean(value);
        if (text.length() < 2 || text.length() > 240) return false;
        return text.contains("站") || text.contains("下车") || text.contains("上车")
                || text.contains("公交") || text.contains("地铁") || text.contains("轨道交通")
                || text.contains("换乘") || text.contains("剩余") || text.contains("还有")
                || text.contains("到达") || text.contains("方向");
    }

    static String clean(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeStation(String value) {
        String station = clean(value).replaceFirst("^(?:了|至|在)", "").trim();
        return station.endsWith("站") ? station : station + "站";
    }

    static final class Result {
        final String raw;
        final String current;
        final String next;
        final Integer remaining;
        final boolean urgent;
        final boolean useful;

        Result(String raw, String current, String next, Integer remaining, boolean urgent, boolean useful) {
            this.raw = raw;
            this.current = current;
            this.next = next;
            this.remaining = remaining;
            this.urgent = urgent;
            this.useful = useful;
        }
    }
}
