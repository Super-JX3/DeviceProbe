package cn.litebot.deviceprobe;

import java.util.Locale;

/** Pure calculations: no Android or device state, so these can be unit-tested. */
public final class MetricMath {
    private MetricMath() {}
    public static boolean positive(double v) { return !Double.isNaN(v) && !Double.isInfinite(v) && v > 0; }
    public static String f(double v, int places) {
        return String.format(Locale.US, "%." + places + "f", v);
    }
    public static int gcd(int a, int b) {
        a = Math.abs(a); b = Math.abs(b);
        while (b != 0) { int t = a % b; a = b; b = t; }
        return a;
    }
    public static String ratio(int w, int h) {
        if (w <= 0 || h <= 0) return "未知";
        int a = Math.max(w, h), b = Math.min(w, h), g = gcd(a, b);
        return (a / g) + ":" + (b / g) + "（约 " + f(9.0 * a / b, 2) + ":9，长边:短边）";
    }
    public static double ppi(int w, int h, double inches) {
        return w > 0 && h > 0 && positive(inches) ? Math.hypot(w, h) / inches : Double.NaN;
    }
    public static double diagonal(int w, int h, double xdpi, double ydpi) {
        return w > 0 && h > 0 && positive(xdpi) && positive(ydpi) ? Math.hypot(w / xdpi, h / ydpi) : Double.NaN;
    }
    public static double effectivePpi(int w, int h, double xdpi, double ydpi) {
        return ppi(w, h, diagonal(w, h, xdpi, ydpi));
    }
    public static String bytes(long value) {
        return value >= 0 ? f(value / 1073741824.0, 2) + " GiB / " + f(value / 1e9, 2) + " GB" : "未知";
    }
}
