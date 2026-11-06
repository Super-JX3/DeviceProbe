package cn.litebot.deviceprobe;

/** Player-facing format capability evidence. Never uses Spatializer as an Atmos veto. */
public final class AudioEvidence {
    public boolean complete, joc, eac3, ac4;
    public String error;
    public String inventory = "正在后台读取……";
    public String briefSummary() {
        if (!complete) return "读取中";
        if (joc) return "声明 Atmos 解码，未验证呈现";
        if (eac3) return "有 DD+ 解码，Atmos 待验证";
        if (ac4) return "有 AC-4 解码，Atmos 待验证";
        return "无法确认";
    }
    public String playerSummary() {
        if (!complete) return "正在读取播放器解码能力……";
        if (joc) return "已声明 Dolby Atmos（DD+ JOC）格式解码能力";
        if (eac3) return "已声明 Dolby Digital Plus 解码能力（杜比官方播放器检测入口）";
        if (ac4) return "已声明 Dolby AC-4 解码能力；Atmos 内容兼容性需进一步验证";
        if (error != null) return "读取不完整，无法判断；不能判定为不支持";
        return "未枚举到系统 Dolby 解码器；不等于所有播放器均不支持";
    }
    public String detail() {
        if (!complete) return "读取完成后显示。";
        if (joc) return "系统明确提供 audio/eac3-joc，播放器可以尝试选择 DD+ Atmos 音轨。此结论不要求 Android Spatializer 当前为开启状态。";
        if (eac3) return "Dolby 官方 Android 移动播放器指南以 audio/eac3 检查集成 DD+ / DD+ JOC 解码能力。这里只确认系统声明，尚未验证具体文件、Atmos 元数据或输出呈现。";
        if (ac4) return "AC-4 可承载多种内容；仅枚举此 MIME 不证明具体音轨为 Atmos。";
        return "播放器也可能自带授权解码库；本应用不会因此断言整机不支持。";
    }
}
