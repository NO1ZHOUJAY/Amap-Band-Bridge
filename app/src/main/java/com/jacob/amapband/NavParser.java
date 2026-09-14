package com.jacob.amapband;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NavParser {
    private static final Pattern REMAINING = Pattern.compile("(?:剩余|还有|再坐|再过|途经|距离下车)\\s*(\\d{1,2})\\s*站|(?<!\\d)(\\d{1,2})\\s*站(?:后)?(?:下车|到达|出站)");
    // 高德 17.00 的 AJX 公交导航会实际输出“1站后后 沈杜公路换乘”这类描述。
    // 第二个“后”是高德无障碍文案自身的重复，不能按正常中文句式解析。
    private static final Pattern TRANSIT_STEP = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*站后+\\s*([^，。；;\\n]{2,40}?)(换乘|下车|出站|到达)(?=$|\\s|，|。|；|;|\\d|[A-Za-z（(])");
    private static final Pattern CURRENT = Pattern.compile("(?:当前(?:位于|所在|到达)?|已到(?:达)?|到达|驶入|经过)\\s*[:：]?\\s*([^，。；;\\n]{2,24}?)(?:站|，|。|；|;|$)");
    private static final Pattern NEXT = Pattern.compile("(?:下一站|下站)\\s*[:：]?\\s*([^，。；;\\n]{2,24}?)(?:站|，|。|；|;|$)");
    private static final Pattern BUS_ETA = Pattern.compile(
            "(?:第\\s*(\\d{1,2})\\s*辆\\s*)?(\\d{1,2})\\s*分钟\\s*[·・•.\\-]?\\s*(\\d{1,2})\\s*站");
    private static final Pattern BUS_IMMINENT = Pattern.compile(
            "(?:第\\s*(\\d{1,2})\\s*辆\\s*)?(?:公交(?:车)?\\s*)?即将进站");

    static Result parse(String raw) {
        String text = clean(raw);
        Matcher remainingMatcher = REMAINING.matcher(text);
        Matcher transitStepMatcher = TRANSIT_STEP.matcher(text);
        Matcher currentMatcher = CURRENT.matcher(text);
        Matcher nextMatcher = NEXT.matcher(text);
        Matcher busEtaMatcher = BUS_ETA.matcher(text);
        Matcher busImminentMatcher = BUS_IMMINENT.matcher(text);

        Integer remaining = null;
        String target = null;
        String action = null;
        if (remainingMatcher.find()) {
            String count = remainingMatcher.group(1) != null ? remainingMatcher.group(1) : remainingMatcher.group(2);
            remaining = Integer.valueOf(count);
        }
        if (transitStepMatcher.find()) {
            remaining = Integer.valueOf(transitStepMatcher.group(1));
            target = clean(transitStepMatcher.group(2))
                    .replaceFirst("^[·・•\\-—:：\\s]+", "")
                    .trim();
            action = transitStepMatcher.group(3);
        }
        String current = currentMatcher.find() ? normalizeStation(currentMatcher.group(1)) : null;
        String next = nextMatcher.find() ? normalizeStation(nextMatcher.group(1)) : null;
        Integer busNumber = null;
        Integer busMinutes = null;
        Integer busStops = null;
        boolean busImminent = false;
        if (busImminentMatcher.find()) {
            busImminent = true;
            if (busImminentMatcher.group(1) != null) {
                busNumber = Integer.valueOf(busImminentMatcher.group(1));
            }
        } else if (busEtaMatcher.find()) {
            if (busEtaMatcher.group(1) != null) {
                busNumber = Integer.valueOf(busEtaMatcher.group(1));
            }
            busMinutes = Integer.valueOf(busEtaMatcher.group(2));
            busStops = Integer.valueOf(busEtaMatcher.group(3));
        }
        boolean urgent = text.contains("准备下车") || text.contains("请下车")
                || text.contains("已到站") || text.matches(".*即将到达.{2,24}(?:站|终点).*?");
        boolean useful = remaining != null || current != null || next != null || urgent
                || busImminent || busMinutes != null;
        return new Result(text, current, next, remaining, target, action, urgent,
                busNumber, busMinutes, busStops, busImminent, useful);
    }

    static boolean startsNavigation(String raw) {
        String text = clean(raw);
        return text.contains("开始导航") || text.contains("已为您开启导航")
                || text.contains("下车提醒将在后台")
                || text.contains("持续为您提供公交语音导航服务");
    }

    static boolean endsNavigation(String raw) {
        String text = clean(raw);
        return text.contains("结束导航") || text.contains("退出导航")
                || text.contains("关闭下车提醒") || text.contains("已结束行程");
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
        final String target;
        final String action;
        final boolean urgent;
        final Integer busNumber;
        final Integer busMinutes;
        final Integer busStops;
        final boolean busImminent;
        final boolean useful;

        Result(String raw, String current, String next, Integer remaining, String target, String action,
               boolean urgent, Integer busNumber, Integer busMinutes, Integer busStops,
               boolean busImminent, boolean useful) {
            this.raw = raw;
            this.current = current;
            this.next = next;
            this.remaining = remaining;
            this.target = target;
            this.action = action;
            this.urgent = urgent;
            this.busNumber = busNumber;
            this.busMinutes = busMinutes;
            this.busStops = busStops;
            this.busImminent = busImminent;
            this.useful = useful;
        }
    }
}
