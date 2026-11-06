package cn.litebot.deviceprobe;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;

public final class DolbyProbe {
    private DolbyProbe() {}
    private static final String[] FORMATS = {"audio/eac3-joc", "audio/eac3", "audio/ac4", "audio/true-hd", "video/dolby-vision"};
    public static AudioEvidence scan() {
        AudioEvidence evidence = new AudioEvidence();
        StringBuilder text = new StringBuilder();
        try {
            // Use decoders eligible for ordinary playback, not secure/tunneled-only entries.
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            for (String mime : FORMATS) {
                if (text.length() > 0) text.append('\n');
                text.append(mime).append(": "); boolean found = false;
                for (MediaCodecInfo codec : codecs) {
                    if (codec.isEncoder()) continue;
                    try {
                        if (!offers(codec, mime)) continue;
                        if (mime.equals("audio/eac3-joc")) evidence.joc = true;
                        if (mime.equals("audio/eac3")) evidence.eac3 = true;
                        if (mime.equals("audio/ac4")) evidence.ac4 = true;
                        if (found) text.append(", "); found = true;
                        text.append(codec.getName());
                        if (Build.VERSION.SDK_INT >= 29) text.append(codec.isHardwareAccelerated() ? " [硬件]" : codec.isSoftwareOnly() ? " [软件]" : " [未标注硬/软件]");
                    } catch (RuntimeException e) { evidence.error = e.getClass().getSimpleName(); }
                }
                if (!found) text.append("未声明");
            }
        } catch (Exception e) { evidence.error = e.getClass().getSimpleName(); }
        if (evidence.error != null) text.append("\n部分查询失败: ").append(evidence.error);
        evidence.complete = true;
        evidence.inventory = text.length() == 0 ? "未取得解码器列表" : text.toString();
        return evidence;
    }
    private static boolean offers(MediaCodecInfo codec, String wanted) {
        for (String type : codec.getSupportedTypes()) if (wanted.equalsIgnoreCase(type)) return true;
        return false;
    }
    /** Explicit user action only. No media data, configuration, playback, microphone or system changes. */
    public static String verifyCreation() {
        StringBuilder result = new StringBuilder();
        Set<String> tested = new HashSet<>();
        try {
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            // At most one candidate per format, so this is a short resource check, not a stress test.
            for (String mime : new String[]{"audio/eac3-joc", "audio/eac3", "audio/ac4"}) {
                MediaCodecInfo candidate = null;
                for (MediaCodecInfo info : codecs) {
                    if (info.isEncoder()) continue;
                    try { if (offers(info, mime)) { candidate = info; break; } } catch (RuntimeException ignored) {}
                }
                if (result.length() > 0) result.append('\n');
                result.append(mime).append(": ");
                if (candidate == null) { result.append("未找到普通播放候选解码器"); continue; }
                String name = candidate.getName(); result.append(name).append(" / ");
                if (!tested.add(name)) { result.append("同名组件已在上项验证，跳过重复创建"); continue; }
                MediaCodec instance = null;
                try {
                    instance = MediaCodec.createByCodecName(name);
                    result.append("创建成功");
                } catch (Exception e) { result.append("创建未成功（").append(e.getClass().getSimpleName()).append("，可能与资源或厂商实现有关）"); }
                finally {
                    if (instance != null) try { instance.release(); } catch (RuntimeException e) { result.append("；释放返回异常"); }
                }
            }
        } catch (Exception e) { result.append("\n查询异常: ").append(e.getClass().getSimpleName()); }
        return result.append("\n这里只创建并释放组件，没有配置/解码/播放音轨，不代表 Atmos 渲染验证；失败也不能直接判定永久不支持。").toString();
    }
}
