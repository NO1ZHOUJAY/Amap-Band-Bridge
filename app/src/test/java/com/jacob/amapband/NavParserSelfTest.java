package com.jacob.amapband;

public final class NavParserSelfTest {
    public static void main(String[] args) {
        NavParser.Result transfer = NavParser.parse("3站后 · 耀华路换乘 7号线");
        check(transfer.remaining != null && transfer.remaining == 3, "transfer remaining");
        check("耀华路".equals(transfer.target), "transfer target");
        check("换乘".equals(transfer.action), "transfer action");

        NavParser.Result eta = NavParser.parse("第1辆 3分钟·2站,");
        check(eta.busNumber != null && eta.busNumber == 1, "bus number");
        check(eta.busMinutes != null && eta.busMinutes == 3, "bus minutes");
        check(eta.busStops != null && eta.busStops == 2, "bus stops");

        NavParser.Result imminent = NavParser.parse("第 1 辆即将进站,较拥挤");
        check(imminent.busImminent, "bus imminent");
        check(imminent.busNumber != null && imminent.busNumber == 1, "imminent bus number");

        check(NavParser.startsNavigation("高德地图持续为您提供公交语音导航服务"),
                "navigation start");
        check(NavParser.endsNavigation("已结束行程"), "navigation end");
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
