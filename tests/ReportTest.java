package cn.litebot.deviceprobe;

public final class ReportTest {
    public static void main(String[] args) {
        Probe.Snapshot result = new Probe.Snapshot();
        result.sections.add(new Probe.Section("屏幕").add("分辨率", "1440 × 3200 px"));
        result.sections.add(new Probe.Section("音频").add("Dolby Atmos", "无法确认"));
        String report = result.report();
        if (!report.contains("【屏幕】\n分辨率: 1440 × 3200 px")) throw new AssertionError("missing screen section");
        if (!report.contains("Dolby Atmos: 无法确认")) throw new AssertionError("unknown evidence lost");
        if (!report.contains("未读取账号、IMEI、序列号或网络地址")) throw new AssertionError("missing privacy notice");
        if (!report.contains("不是仪器实测或厂商认证")) throw new AssertionError("missing limits");
        if (!report.endsWith("\n")) throw new AssertionError("invalid report termination");
        result.resolution = "1440 × 3200 px";
        result.pixelDensity = "500（驱动估算）";
        String brief = result.briefReport();
        if (!brief.contains("分辨率（当前模式）: 1440 × 3200 px")) throw new AssertionError("missing current resolution");
        if (!brief.contains("PPI: 500（驱动估算）")) throw new AssertionError("estimate qualifier lost");
        if (brief.contains("Vivid")) throw new AssertionError("removed module in brief");
        if (report.contains("Vivid")) throw new AssertionError("removed module in full report");
        if (brief.contains("【屏幕】") || brief.contains("账号")) throw new AssertionError("verbose report leaked into brief");
        if (!brief.contains("刷新率（系统上报）") || !brief.contains("内存（系统可见）")) throw new AssertionError("missing key metrics");
        if (brief.length() > 750) throw new AssertionError("default brief too long");
        if (result.brief().rows.size() != 10) throw new AssertionError("unexpected overview size");
        AudioEvidence evidence = new AudioEvidence();
        if (!evidence.briefSummary().equals("读取中")) throw new AssertionError("premature audio conclusion");
        evidence.complete = true;
        if (!evidence.briefSummary().equals("无法确认")) throw new AssertionError("absence is not a veto");
        evidence.eac3 = true;
        if (!evidence.briefSummary().contains("Atmos 待验证")) throw new AssertionError("DD+ is not Atmos proof");
        evidence.joc = true;
        if (!evidence.briefSummary().contains("未验证呈现")) throw new AssertionError("JOC not rendering proof");
        System.out.println("PASS: 17 report and concise summary tests");
    }
}
