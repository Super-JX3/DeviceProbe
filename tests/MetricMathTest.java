package cn.litebot.deviceprobe;

public class MetricMathTest {
    private static int checks;
    private static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(MetricMath.ratio(1080, 1920).startsWith("16:9"), "16:9");
        check(MetricMath.ratio(1920, 1080).equals(MetricMath.ratio(1080, 1920)), "rotation normalization");
        check(MetricMath.ratio(1440, 3200).startsWith("20:9"), "20:9");
        check(MetricMath.ratio(1000, 1000).startsWith("1:1"), "square");
        check(MetricMath.ratio(0, 100).equals("未知"), "zero size");
        check(MetricMath.gcd(1440, 3200) == 160, "gcd");
        check(Math.abs(MetricMath.ppi(1080, 1920, 5) - 440.58) < .02, "ppi formula");
        check(Math.abs(MetricMath.effectivePpi(1080, 1920, 400, 400) - 400) < .0001, "physical density");
        check(Double.isNaN(MetricMath.ppi(1080, 1920, 0)), "zero diagonal");
        check(Double.isNaN(MetricMath.ppi(0, 1920, 6)), "invalid pixels");
        check(Double.isNaN(MetricMath.diagonal(1080, 1920, 0, 400)), "invalid driver density");
        check(!MetricMath.positive(Double.NaN) && !MetricMath.positive(Double.POSITIVE_INFINITY), "nonfinite");
        check(MetricMath.bytes(8589934592L).equals("8.00 GiB / 8.59 GB"), "GB vs GiB");
        check(MetricMath.bytes(-1).equals("未知"), "negative bytes");
        check(MetricMath.f(1.5, 2).equals("1.50"), "locale-independent");
        System.out.println("PASS: " + checks + " metric calculation tests");
    }
}
