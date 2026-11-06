package cn.litebot.deviceprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.Spatializer;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Probe {
    public static final class Section {
        public final String title;
        public final List<String[]> rows = new ArrayList<>();
        public Section(String title) { this.title = title; }
        public Section add(String key, String value) { rows.add(new String[]{key, value}); return this; }
    }
    public static final class Snapshot {
        public final List<Section> sections = new ArrayList<>();
        public String resolution = "检测中", refresh = "—", ram = "—";
        public String ratio = "待读取", pixelDensity = "无法确认", uiSize = "待读取", scale = "待读取";
        public String maxRefresh = "待读取", hdr = "无法确认", gamut = "无法确认", colorManagement = "无法确认";
        public String atmos = "读取中", availableRam = "待读取";
        public int maxWidth, maxHeight, currentWidth, currentHeight;
        public Section brief() {
            return new Section("一眼看懂")
                .add("屏幕比例", ratio).add("PPI", pixelDensity)
                .add("界面尺寸", uiSize).add("界面 / 字体缩放", scale)
                .add("刷新率（系统上报）", refresh + " · 最高 " + maxRefresh)
                .add("HDR（系统上报）", hdr)
                .add("广色域", gamut).add("色彩管理", colorManagement)
                .add("杜比全景声", atmos)
                .add("内存（系统可见）", ram + " · 可用 " + availableRam);
        }
        public String briefReport() {
            StringBuilder b = new StringBuilder("屏幕与设备检测 1.4.0 · 简报\n");
            b.append("分辨率（当前模式）: ").append(resolution).append('\n');
            for (String[] row : brief().rows) b.append(row[0]).append(": ").append(row[1]).append('\n');
            return b.append("系统声明/估算，非实测或认证；详情可在应用展开。\n").toString();
        }
        public String report() {
            StringBuilder b = new StringBuilder("屏幕与设备检测 1.4.0\n本地 API 报告；不是仪器实测或厂商认证。\n未读取账号、IMEI、序列号或网络地址。\n");
            for (Section s : sections) {
                b.append("\n【").append(s.title).append("】\n");
                for (String[] row : s.rows) b.append(row[0]).append(": ").append(row[1]).append('\n');
            }
            return b.toString();
        }
    }
    private static String f(double n) { return MetricMath.f(n, 2); }
    private static String yes(boolean b) { return b ? "是" : "否（仅代表此 API 当前上报）"; }
    private static String size(int w, int h) { return w + " × " + h + " px"; }
    private static String unknown(Exception e) { return "读取失败 / " + e.getClass().getSimpleName(); }
    public static Display display(Activity a) { return a.getWindowManager().getDefaultDisplay(); }
    public static Snapshot collect(Activity a, View content, AudioEvidence audioEvidence, double diagonalInches) {
        Snapshot out = new Snapshot();
        out.sections.add(new Section("设备")
            .add("品牌 / 型号", Build.MANUFACTURER + " / " + Build.MODEL)
            .add("系统", "Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
            .add("安全补丁", Build.VERSION.SECURITY_PATCH)
            .add("权限与隐私", "无网络权限；无账号、定位、电话或存储权限；不上传结果"));
        Display d = display(a);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        Configuration config = a.getResources().getConfiguration();
        try {
            Display.Mode mode = d.getMode();
            out.currentWidth = mode.getPhysicalWidth(); out.currentHeight = mode.getPhysicalHeight();
            out.maxWidth = out.currentWidth; out.maxHeight = out.currentHeight;
            Section screen = new Section("屏幕分辨率与比例");
            StringBuilder modes = new StringBuilder();
            float maxHz = 0;
            for (Display.Mode m : d.getSupportedModes()) {
                if ((long)m.getPhysicalWidth() * m.getPhysicalHeight() > (long)out.maxWidth * out.maxHeight) {
                    out.maxWidth = m.getPhysicalWidth(); out.maxHeight = m.getPhysicalHeight();
                }
                maxHz = Math.max(maxHz, m.getRefreshRate());
                if (modes.length() > 0) modes.append('\n');
                modes.append("模式 ").append(m.getModeId()).append(": ")
                    .append(size(m.getPhysicalWidth(), m.getPhysicalHeight())).append(" @ ").append(f(m.getRefreshRate())).append(" Hz");
                if (Build.VERSION.SDK_INT >= 34) modes.append(" / HDR: ").append(hdrNames(m.getSupportedHdrTypes()));
            }
            out.resolution = size(out.currentWidth, out.currentHeight);
            out.refresh = f(d.getRefreshRate()) + " Hz";
            out.ratio = MetricMath.ratio(out.currentWidth, out.currentHeight);
            out.maxRefresh = f(maxHz) + " Hz";
            screen.add("当前显示模式", out.resolution)
                .add("屏幕比例", MetricMath.ratio(out.currentWidth, out.currentHeight))
                .add("系统枚举最高分辨率", size(out.maxWidth, out.maxHeight))
                .add("当前上报刷新率", out.refresh)
                .add("模式名义刷新率", f(mode.getRefreshRate()) + " Hz")
                .add("枚举模式最高刷新率", f(maxHz) + " Hz")
                .add("全部上报模式", modes.toString())
                .add("解释", "模式可能是合成/渲染模式；最高分辨率不保证等于面板原生分辨率。LTPO 的瞬时扫描频率可能不向普通应用公开，不能凭这里断言最低 1 Hz。");
            out.sections.add(screen);
        } catch (Exception e) { out.sections.add(new Section("显示模式").add("状态", unknown(e))); }
        try {
            Section ui = new Section("界面尺寸与缩放");
            if (Build.VERSION.SDK_INT >= 30) {
                WindowMetrics wm = a.getWindowManager().getCurrentWindowMetrics();
                Rect r = wm.getBounds();
                android.graphics.Insets in = wm.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                ui.add("当前窗口（含系统区域）", size(r.width(), r.height()))
                    .add("窗口逻辑尺寸", f(r.width() / dm.density) + " × " + f(r.height() / dm.density) + " dp")
                    .add("安全边距", "左 " + in.left + " / 上 " + in.top + " / 右 " + in.right + " / 下 " + in.bottom + " px")
                    .add("最大窗口边界", a.getWindowManager().getMaximumWindowMetrics().getBounds().width() + " × " + a.getWindowManager().getMaximumWindowMetrics().getBounds().height() + " px（非面板像素证明）");
            } else {
                DisplayMetrics real = new DisplayMetrics(); d.getRealMetrics(real);
                ui.add("显示逻辑坐标范围", size(real.widthPixels, real.heightPixels));
            }
            ui.add("本应用内容区", content.getWidth() > 0 ? size(content.getWidth(), content.getHeight()) + " / " + f(content.getWidth() / dm.density) + " × " + f(content.getHeight() / dm.density) + " dp" : "布局完成后点击重新检测")
                .add("配置界面宽高", config.screenWidthDp + " × " + config.screenHeightDp + " dp")
                .add("最小宽度", config.smallestScreenWidthDp + " dp")
                .add("逻辑密度 densityDpi", dm.densityDpi + " dpi（不是物理 PPI）")
                .add("界面缩放 dp → px", f(dm.density) + "×，即 1 dp ≈ " + f(dm.density) + " px")
                .add("系统字体倍率", f(config.fontScale) + "×")
                .add("实际字体尺寸换算", "12 sp → " + f(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, dm)) + " px；24 sp → " + f(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, dm)) + " px")
                .add("解释", "缩放是相对 160 dpi 基准，不是相对出厂滑块的倍率。Android 14+ 字体可能非线性缩放；分屏、旋转、键盘和系统栏会改变界面尺寸。");
            out.sections.add(ui);
            out.uiSize = config.screenWidthDp + " × " + config.screenHeightDp + " dp";
            out.scale = f(dm.density) + "×（dp→px） / " + f(config.fontScale) + "×";
        } catch (Exception e) { out.sections.add(new Section("界面尺寸").add("状态", unknown(e))); }
        Section ppi = new Section("物理 PPI 与屏幕尺寸");
        DisplayMetrics physical = new DisplayMetrics();
        d.getRealMetrics(physical);
        double diag = MetricMath.diagonal(out.currentWidth, out.currentHeight, physical.xdpi, physical.ydpi);
        if (MetricMath.positive(diag)) out.pixelDensity = f(MetricMath.effectivePpi(out.currentWidth, out.currentHeight, physical.xdpi, physical.ydpi)) + "（驱动估算）";
        ppi.add("驱动上报 X / Y 密度", f(physical.xdpi) + " / " + f(physical.ydpi) + " dpi")
            .add("按驱动值推算的等效 PPI", MetricMath.positive(diag) ? f(MetricMath.effectivePpi(out.currentWidth, out.currentHeight, physical.xdpi, physical.ydpi)) + " ppi（估计值）" : "未知")
            .add("按驱动值推算的对角线", MetricMath.positive(diag) ? f(diag) + " 英寸（可能不准确）" : "未知")
            .add("可靠性说明", "Android 的 xdpi/ydpi 可能是占位值或随分辨率变化，无法保证真实物理 PPI；本应用不把逻辑密度当 PPI。");
        if (MetricMath.positive(diagonalInches)) {
            out.pixelDensity = f(MetricMath.ppi(out.currentWidth, out.currentHeight, diagonalInches)) + "（按输入英寸计算）";
            ppi.add("你输入的对角线", f(diagonalInches) + " 英寸")
                .add("当前模式 PPI（按输入计算）", f(MetricMath.ppi(out.currentWidth, out.currentHeight, diagonalInches)))
                .add("最高枚举模式 PPI（按输入计算）", f(MetricMath.ppi(out.maxWidth, out.maxHeight, diagonalInches)))
                .add("计算前提", "仅当所用像素尺寸就是面板原生尺寸，且对角线准确时，结果才是物理 PPI。");
        }
        out.sections.add(ppi);
        try {
            Display.HdrCapabilities hc = d.getHdrCapabilities();
            int[] types = Build.VERSION.SDK_INT >= 34 ? d.getMode().getSupportedHdrTypes() : (hc == null ? new int[0] : hc.getSupportedHdrTypes());
            out.hdr = hdrNames(types);
            Section hdr = new Section("HDR 显示能力")
                .add("显示器上报 HDR", yes(d.isHdr()))
                .add("当前模式 HDR 类型", hdrNames(types));
            if (hc != null) hdr.add("驱动申报峰值 / 平均峰值 / 最低亮度", luminance(hc.getDesiredMaxLuminance()) + " / " + luminance(hc.getDesiredMaxAverageLuminance()) + " / " + luminance(hc.getDesiredMinLuminance()));
            hdr.add("注意", "亮度是静态申报，非实测或实时亮度。HDR 显示支持不保证视频解码、DRM、应用授权或流媒体画质；本应用没有播放 HDR 测试片。");
            out.sections.add(hdr);
        } catch (Exception e) { out.sections.add(new Section("HDR").add("状态", unknown(e))); }
        try {
            out.gamut = d.isWideColorGamut() ? "支持（系统声明）" : "未声明，不能确认";
            out.colorManagement = config.isScreenWideColorGamut() ? "广色域路径可用" : "未声明广色域路径";
            Section color = new Section("广色域与色彩管理")
                .add("显示器广色域能力", yes(d.isWideColorGamut()))
                .add("Android 广色域管理路径", yes(config.isScreenWideColorGamut()))
                .add("HDR 配置能力", yes(config.isScreenHdr()))
                .add("本窗口请求", "wideColorGamut")
                .add("本窗口实际广色域状态", yes(a.getWindow().isWideColorGamut()));
            if (Build.VERSION.SDK_INT >= 29) {
                ColorSpace cs = d.getPreferredWideGamutColorSpace();
                color.add("系统首选广色域空间", cs == null ? "未上报" : cs.getName());
            }
            color.add("结论边界", "支持广色域管理路径不代表所有应用都正确管理色彩；不代表 P3/Adobe RGB 覆盖率、色准、位深或屏幕校准达标。下方色块只是视觉参考，不作自动判定。");
            out.sections.add(color);
        } catch (Exception e) { out.sections.add(new Section("色彩管理").add("状态", unknown(e))); }
        out.sections.add(audio(a, audioEvidence));
        out.atmos = audioEvidence.briefSummary();
        out.sections.add(spatial(a));
        try {
            ActivityManager am = (ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi);
            out.ram = MetricMath.f(mi.totalMem / 1073741824.0, 1) + " GiB";
            out.availableRam = MetricMath.f(mi.availMem / 1073741824.0, 1) + " GiB";
            out.sections.add(new Section("运行内存 RAM")
                .add("系统可见总 RAM", MetricMath.bytes(mi.totalMem))
                .add("当前可用 RAM", MetricMath.bytes(mi.availMem))
                .add("总量减可用", MetricMath.bytes(Math.max(0, mi.totalMem - mi.availMem)))
                .add("低内存警戒阈值", MetricMath.bytes(mi.threshold))
                .add("系统低内存状态", yes(mi.lowMemory))
                .add("本应用 Java 堆上限", MetricMath.bytes(Runtime.getRuntime().maxMemory()))
                .add("说明", "RAM 不是存储空间。系统可见总量扣除了部分硬件保留区，可能低于标称容量；不把内存扩展/存储交换空间计为物理 RAM。缓存可回收，不能用“总量减可用”判定内存泄漏。"));
        } catch (Exception e) { out.sections.add(new Section("RAM").add("状态", unknown(e))); }
        return out;
    }
    private static String luminance(float v) { return v >= 0 && !Float.isInfinite(v) && !Float.isNaN(v) ? f(v) + " nit（申报）" : "未知"; }
    private static String hdrNames(int[] types) {
        if (types == null || types.length == 0) return "无上报类型";
        StringBuilder s = new StringBuilder();
        for (int t : types) {
            if (s.length() > 0) s.append(" / ");
            switch(t) {
                case 1: s.append("Dolby Vision"); break;
                case 2: s.append("HDR10"); break;
                case 3: s.append("HLG"); break;
                case 4: s.append("HDR10+"); break;
                default: s.append("类型 ").append(t);
            }
        }
        return s.toString();
    }
    private static Section audio(Activity a, AudioEvidence evidence) {
        Section s = new Section("杜比 / 播放器格式能力");
        s.add("播放器能力判断", evidence.playerSummary())
            .add("判断依据", evidence.detail())
            .add("系统普通播放解码器", evidence.inventory)
            .add("整机认证与实际呈现", "本应用不作认证判断；未播放样片验证")
            .add("与 Apple Music 的关系", "按 Dolby 公开移动播放器方案查询集成解码器；没有复刻或调用 Apple Music 的私有检测逻辑，也不保证其账号/曲目/输出路径的结果相同。");
        appendEffects(s);
        return s.add("结论边界", "明确区分格式解码能力、组件是否能创建和实际 Atmos 呈现。Spatializer 为否不会覆盖这里的格式支持结论。Dolby Vision 是视频，不是 Atmos 音频。");
    }
    private static void appendEffects(Section s) {
        try {
            AudioEffect.Descriptor[] effects = AudioEffect.queryEffects();
            StringBuilder clues = new StringBuilder();
            if (effects != null) for (AudioEffect.Descriptor effect : effects) {
                String text = effect.name + " / " + effect.implementor;
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.contains("dolby") || lower.contains("dax") || lower.contains("atmos")) {
                    if (clues.length() > 0) clues.append('\n');
                    clues.append(text.replace('\n', ' ').replace('\r', ' '));
                }
            }
            s.add("杜比相关音效线索", clues.length() == 0 ? "未枚举到（不能据此断言不支持）" : clues.toString());
        } catch (Exception e) { s.add("音效线索", unknown(e)); }
    }
    private static Section spatial(Activity a) {
        Section s = new Section("Android 标准空间音频（独立状态）");
        s.add("注意", "以下只描述 Android Spatializer，不是 Dolby Atmos 支持开关；不可用/未启用不等于手机没有杜比。");
        try {
            AudioManager am = (AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= 32) {
                Spatializer sp = am.getSpatializer();
                if (sp != null) {
                    int level = sp.getImmersiveAudioLevel();
                    s.add("Android 空间音频能力", level == 1 ? "多声道空间化（级别 1，不是音质等级）" : level == 0 ? "此标准接口未声明空间化能力" : "级别 " + level)
                        .add("标准 Spatializer 当前可用", sp.isAvailable() ? "当前可用" : "当前不可用；不代表 Dolby Atmos 不支持")
                        .add("标准 Spatializer 当前开关", sp.isEnabled() ? "当前开启" : "当前关闭；不代表厂商杜比方案关闭");
                    AudioFormat fmt = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).setSampleRate(48000).build();
                    AudioAttributes at = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
                    s.add("当前输出可空间化 5.1 PCM", yes(sp.canBeSpatialized(at, fmt)));
                } else s.add("Android Spatializer", "系统未提供");
            } else s.add("Android Spatializer", "需 Android 12L / API 32 或以上；不能据此否定厂商杜比方案");
            StringBuilder routes = new StringBuilder();
            for (AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (routes.length() > 0) routes.append(" / ");
                routes.append(deviceType(dev.getType()));
            }
            s.add("可用输出设备类型", routes.length() == 0 ? "未上报" : routes.toString());
        } catch (Exception e) { s.add("空间音频 / 输出设备", unknown(e)); }
        return s.add("判读", "标准空间化状态与厂商 Dolby 实现分开看。可用设备列表不等于实际播放路由；插拔耳机后重新检测。");
    }
    private static String deviceType(int t) {
        switch(t) {
            case 1: return "听筒"; case 2: return "内置扬声器";
            case 3: case 4: return "有线耳机"; case 7: case 8: return "蓝牙音频";
            case 9: case 10: return "HDMI"; case 11: case 12: case 22: return "USB 音频";
            case 18: return "电话网络音频通路";
            case 26: case 27: case 30: return "蓝牙 LE 音频";
            default: return "类型 " + t;
        }
    }
}
