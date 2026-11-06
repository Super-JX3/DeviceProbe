package cn.litebot.deviceprobe;

public final class AudioEvidenceTest {
    private static int assertions;
    private static void check(boolean v, String message) { assertions++; if (!v) throw new AssertionError(message); }
    public static void main(String[] args) {
        AudioEvidence e = new AudioEvidence();
        check(e.playerSummary().contains("正在读取"), "loading must not be unsupported");
        e.complete = true;
        check(e.playerSummary().contains("不等于"), "no codec is not universal no support");
        e.error = "SecurityException";
        check(e.playerSummary().contains("读取不完整"), "failed query is unknown");
        e.error = null; e.ac4 = true;
        check(e.playerSummary().contains("需进一步验证"), "AC4 alone cannot certify Atmos");
        e.eac3 = true;
        check(e.playerSummary().contains("Digital Plus"), "DD+ official player entry");
        check(e.detail().contains("未验证"), "declaration is not rendering proof");
        e.joc = true;
        check(e.playerSummary().contains("已声明 Dolby Atmos"), "explicit JOC positive");
        check(e.detail().contains("不要求 Android Spatializer"), "Spatializer must not veto vendor Atmos");
        e.error = "OtherCodecFailed";
        check(e.playerSummary().contains("已声明 Dolby Atmos"), "partial failure must preserve valid positive evidence");
        AudioEvidence visionOnly = new AudioEvidence(); visionOnly.complete = true;
        visionOnly.inventory = "video/dolby-vision: c2.dolby.vision.decoder";
        check(!visionOnly.playerSummary().contains("已声明 Dolby Atmos"), "Dolby Vision is not Atmos");
        AudioEvidence effectOnly = new AudioEvidence(); effectOnly.complete = true;
        effectOnly.inventory = "DAP / Dolby Laboratories";
        check(!effectOnly.playerSummary().contains("已声明 Dolby Atmos"), "effect name alone is not decoder evidence");
        System.out.println("PASS: " + assertions + " player audio evidence tests");
    }
}
