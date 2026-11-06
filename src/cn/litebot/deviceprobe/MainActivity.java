package cn.litebot.deviceprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity implements DisplayManager.DisplayListener {
    private static final int BG = 0xff0b1220, CARD = 0xff152238, TEXT = 0xffeef4ff, SUB = 0xffa7b8d0, ACCENT = 0xff65e0c7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout shell, body;
    private ScrollView scroll;
    private TextView summary, live, fpsText;
    private View content;
    private Button fpsButton, decoderButton;
    private Probe.Snapshot snapshot;
    private AudioEvidence audioEvidence = new AudioEvidence();
    private boolean checkingDecoder;
    private String decoderResult = "未运行。点击下方按钮只创建并释放解码器，不播放声音。";
    private double diagonalInches;
    private DisplayManager manager;
    private boolean resumed, sampling, showDetails;
    private long firstFrame, sampleStart;
    private int frameCount;
    private String sampledFps = "尚未采样";
    private String pendingExport;
    private final Runnable rebuildTask = () -> rebuild();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                live.setText("系统上报 " + MetricMath.f(Probe.display(MainActivity.this).getRefreshRate(), 1) + " Hz  ·  可用 " + MetricMath.f(mi.availMem / 1073741824.0, 1) + " GiB");
            } catch (Exception ignored) { live.setText("实时状态暂不可读，点击重新检测。"); }
            handler.postDelayed(this, 2000);
        }
    };
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long time) {
            if (!sampling || !resumed) return;
            if (firstFrame == 0) firstFrame = time;
            else frameCount++;
            long elapsed = time - firstFrame;
            if (elapsed >= 1500000000L) {
                sampledFps = MetricMath.f(frameCount * 1e9 / elapsed, 1) + " fps（应用 Choreographer 回调率）";
                fpsText.setText(sampledFps + "\n不是面板硬件扫描频率，也不是 GPU 跑分。");
                firstFrame = time; frameCount = 0;
            }
            // The small indicator is deliberately animated only during an explicit sample.
            fpsText.setAlpha(((time / 250000000L) % 2 == 0) ? 1.0f : 0.65f);
            if (time - sampleStart > 15000000000L) stopSampling();
            else Choreographer.getInstance().postFrameCallback(this);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { pendingExport = state.getString("pendingExport"); showDetails = state.getBoolean("showDetails", false); }
        diagonalInches = Double.longBitsToDouble(getPreferences(MODE_PRIVATE).getLong("diagonal", Double.doubleToLongBits(0)));
        shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            shell.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
        }
        setContentView(shell);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = scroll;
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18), dp(24), dp(18), dp(28));
        scroll.addView(body);
        manager = (DisplayManager)getSystemService(DISPLAY_SERVICE);
        rebuild();
        shell.post(() -> rebuild());
        new Thread(() -> {
            final AudioEvidence result = DolbyProbe.scan();
            handler.post(() -> { if (!isFinishing() && !isDestroyed()) { audioEvidence = result; rebuild(); } });
        }, "codec-capability-read").start();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        manager.registerDisplayListener(this, handler);
        handler.removeCallbacks(ticker); handler.post(ticker);
        handler.removeCallbacks(rebuildTask); handler.postDelayed(rebuildTask, 250);
    }
    @Override protected void onPause() {
        resumed = false; stopSampling(); manager.unregisterDisplayListener(this);
        handler.removeCallbacks(ticker); handler.removeCallbacks(rebuildTask); super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle b) { b.putString("pendingExport", pendingExport); b.putBoolean("showDetails", showDetails); super.onSaveInstanceState(b); }
    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayRemoved(int id) {}
    @Override public void onDisplayChanged(int id) {
        // Don't rebuild when only refresh rate changes; that could create a render/refresh feedback loop.
        if (!resumed) return;
        live.setContentDescription("显示器状态已改变，可点击重新检测获取全部模式。");
    }
    private void rebuild() {
        if (isFinishing()) return;
        int y = scroll.getScrollY();
        stopSampling();
        try { snapshot = Probe.collect(this, content, audioEvidence, diagonalInches); }
        catch (Exception e) {
            snapshot = new Probe.Snapshot();
            snapshot.sections.add(new Probe.Section("检测异常").add("状态", e.getClass().getSimpleName() + "；可重新检测或重启应用"));
        }
        body.removeAllViews();
        fpsText = null; fpsButton = null; decoderButton = null;
        TextView kicker = text("DEVICE / DISPLAY LAB", 11, ACCENT); kicker.setLetterSpacing(0.16f); body.addView(kicker);
        TextView title = text("屏幕与设备检测", 29, TEXT); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, dp(8), 0, dp(6)); body.addView(title);
        body.addView(text("本地读取 · 无需 root · 无联网权限", 13, SUB));
        LinearLayout hero = card();
        summary = text(snapshot.resolution, 25, ACCENT); summary.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); hero.addView(summary);
        hero.addView(text("当前模式  /  总 RAM " + snapshot.ram, 13, SUB));
        live = text("读取实时状态……", 13, TEXT); live.setPadding(0, dp(12), 0, 0); hero.addView(live);
        rowButtons("重新检测", () -> { rebuild(); if (resumed) { handler.removeCallbacks(ticker); handler.post(ticker); } }, showDetails ? "复制详情" : "复制简报", () -> copyReport());
        rowButtons(showDetails ? "分享详情" : "分享简报", () -> shareReport(), "导出 TXT", () -> exportReport());
        LinearLayout brief = card();
        TextView briefTitle = text("一眼看懂", 18, ACCENT); briefTitle.setTypeface(null, Typeface.BOLD); brief.addView(briefTitle);
        for (String[] row : snapshot.brief().rows) {
            TextView label = text(row[0], 12, SUB); label.setPadding(0, dp(12), 0, dp(2)); brief.addView(label);
            brief.addView(text(row[1], 15, TEXT));
        }
        body.addView(text("以上为系统声明或估算，非实测；“待验证”不等于不支持。", 12, SUB));
        Button toggle = button(showDetails ? "收起详细信息" : "展开详细信息 / 测试工具", () -> {
            showDetails = !showDetails;
            scroll.scrollTo(0, 0); rebuild();
        }); body.addView(toggle);
        if (showDetails) {
        LinearLayout intro = card();
        intro.addView(text("检测边界", 17, ACCENT));
        intro.addView(text("这里区分“系统声明”“当前状态”“估算”和“无法确认”。不是硬件测量仪，不宣称测得真实峰值亮度、色准、色域覆盖率或杜比认证。切换分辨率、分屏、旋转或插拔耳机后，可重新检测。", 13, SUB));
        for (Probe.Section s : snapshot.sections) {
            LinearLayout box = card();
            TextView heading = text(s.title, 18, TEXT); heading.setTypeface(null, Typeface.BOLD); box.addView(heading);
            for (String[] row : s.rows) {
                TextView key = text(row[0], 12, ACCENT); key.setPadding(0, dp(14), 0, dp(3)); box.addView(key);
                TextView value = text(row[1], 14, TEXT); value.setTextIsSelectable(true); box.addView(value);
            }
            if (s.title.equals("物理 PPI 与屏幕尺寸")) {
                Button b = button("输入 / 清除屏幕英寸数", () -> askDiagonal()); box.addView(b);
            }
            if (s.title.equals("杜比 / 播放器格式能力")) {
                TextView result = text(decoderResult, 13, SUB); result.setPadding(0, dp(15), 0, dp(5)); box.addView(result);
                decoderButton = button(checkingDecoder ? "正在创建解码器……" : "验证解码器能否创建（不播放）", () -> checkDecoder());
                decoderButton.setEnabled(!checkingDecoder && audioEvidence.complete); box.addView(decoderButton);
            }
        }
        LinearLayout rate = card();
        rate.addView(text("界面帧率采样", 18, TEXT));
        fpsText = text(sampledFps + "\n仅测本应用的 vsync 回调，不等于面板最低刷新率。开启采样会增加界面活动，15 秒后自动停止。", 13, SUB); rate.addView(fpsText);
        fpsButton = button("开始 15 秒采样", () -> {
            if (sampling) stopSampling();
            else {
                sampling = true; firstFrame = 0; frameCount = 0; sampleStart = System.nanoTime();
                fpsButton.setText("停止采样"); fpsText.setText("采样中……请保持应用在前台");
                Choreographer.getInstance().postFrameCallback(frameCallback);
            }
        }); rate.addView(fpsButton);
        LinearLayout color = card();
        color.addView(text("色彩视觉参考", 18, TEXT));
        color.addView(text("上排 sRGB / 下排 Display P3。同样的 RGB 数值具有不同色度；能否看出差别受显示模式、色彩管理和视觉影响，不能单凭色块认证色域或色准。Android 8–9 仅显示 sRGB。", 13, SUB));
        color.addView(new ColorSamples(this), new LinearLayout.LayoutParams(-1, dp(150)));
        LinearLayout ruler = card();
        ruler.addView(text("界面尺寸参考尺", 18, TEXT));
        ruler.addView(text("下方标尺长度为 100 dp，分成 10 格；不是 100 mm，也不是物理屏幕校准。", 13, SUB));
        ruler.addView(new Ruler(this), new LinearLayout.LayoutParams(-1, dp(48)));
        }
        body.addView(text("v1.4.0 · Android 8.0+ · 本地检测，不上传结果", 12, SUB));
        if (resumed) { handler.removeCallbacks(ticker); handler.post(ticker); }
        scroll.post(() -> scroll.scrollTo(0, y));
    }
    private Probe.Snapshot current() { return Probe.collect(this, content, audioEvidence, diagonalInches); }
    private void checkDecoder() {
        if (checkingDecoder || !audioEvidence.complete) return;
        checkingDecoder = true; decoderResult = "正在验证组件能否创建；没有播放媒体。"; rebuild();
        new Thread(() -> {
            final String result = DolbyProbe.verifyCreation();
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                checkingDecoder = false; decoderResult = result; rebuild();
            });
        }, "dolby-decoder-create-check").start();
    }
    private void stopSampling() {
        sampling = false; Choreographer.getInstance().removeFrameCallback(frameCallback);
        if (fpsButton != null) fpsButton.setText("开始 15 秒采样");
        if (fpsText != null) fpsText.setAlpha(1.0f);
    }
    private String report() {
        Probe.Snapshot result;
        try { result = current(); } catch (Exception e) { result = snapshot; }
        if (!showDetails) return result.briefReport();
        return result.report() + "\n解码器创建验证:\n" + decoderResult + "\n界面帧率最近采样: " + sampledFps + "\n";
    }
    private void copyReport() {
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("设备检测报告", report()));
        toast("已复制检测报告");
    }
    private void shareReport() {
        Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, report());
        try { startActivity(Intent.createChooser(i, "分享设备检测报告")); } catch (Exception e) { toast("没有可用的分享应用，可以复制报告"); }
    }
    private void exportReport() {
        pendingExport = report();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "DeviceProbe-report.txt");
        try { startActivityForResult(i, 41); } catch (Exception e) { pendingExport = null; toast("系统未提供文件选择器，可以复制报告"); }
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != 41) return;
        String export = pendingExport; pendingExport = null;
        if (result != RESULT_OK || data == null || data.getData() == null || export == null) return;
        try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (stream == null) throw new java.io.IOException();
            stream.write(export.getBytes(StandardCharsets.UTF_8)); toast("报告已导出");
        } catch (Exception e) { toast("导出失败，可以使用复制报告"); }
    }
    private void askDiagonal() {
        EditText input = new EditText(this); input.setTextColor(TEXT); input.setHintTextColor(SUB);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("例如 6.79；留空清除"); if (MetricMath.positive(diagonalInches)) input.setText(Double.toString(diagonalInches));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("屏幕对角线（英寸）")
            .setMessage("按厂商规格或实测输入。最高枚举分辨率不一定是原生分辨率，因此计算结果仍需核对。")
            .setView(input).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String raw = input.getText().toString().trim();
            try {
                double inches = raw.isEmpty() ? 0 : Double.parseDouble(raw);
                if (!raw.isEmpty() && (!MetricMath.positive(inches) || inches < 1 || inches > 150)) throw new NumberFormatException();
                diagonalInches = inches;
                getPreferences(MODE_PRIVATE).edit().putLong("diagonal", Double.doubleToLongBits(inches)).apply();
                dialog.dismiss(); rebuild();
            } catch (NumberFormatException e) { input.setError("请输入 1–150 英寸，或清空"); }
        })); dialog.show();
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show(); }
    private TextView text(String t, int sp, int color) {
        TextView v = new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(color); v.setLineSpacing(dp(3), 1.08f); return v;
    }
    private LinearLayout card() {
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(dp(17), dp(18), dp(17), dp(18));
        GradientDrawable background = new GradientDrawable(); background.setColor(CARD); background.setCornerRadius(dp(18)); b.setBackground(background);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(16); body.addView(b, p); return b;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextColor(ACCENT); b.setTextSize(13); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void rowButtons(String a, Runnable ra, String b, Runnable rb) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(button(a, ra), p); row.addView(button(b, rb), new LinearLayout.LayoutParams(p)); body.addView(row);
    }
    private final class ColorSamples extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap p3;
        ColorSamples(Context c) { super(c); setContentDescription("sRGB 与 Display P3 的红绿蓝色块参考");
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    p3 = Bitmap.createBitmap(300, 60, Bitmap.Config.RGBA_F16, true, ColorSpace.get(ColorSpace.Named.DISPLAY_P3));
                    Canvas canvas = new Canvas(p3); ColorSpace cs = ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
                    for (int i = 0; i < 3; i++) { paint.setColor(Color.pack(i == 0 ? 1f : 0f, i == 1 ? 1f : 0f, i == 2 ? 1f : 0f, 1f, cs)); canvas.drawRect(i * 100, 0, (i + 1) * 100, 60, paint); }
                } catch (Exception ignored) { p3 = null; }
            }
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w = getWidth() / 3f;
            for (int i = 0; i < 3; i++) { paint.setColor(i == 0 ? Color.RED : i == 1 ? Color.GREEN : Color.BLUE); c.drawRect(i * w, dp(12), (i + 1) * w - dp(3), dp(60), paint); }
            if (p3 != null) { paint.setColor(Color.WHITE); c.drawBitmap(p3, null, new RectF(0, dp(72), getWidth() - dp(3), dp(120)), paint); }
            else { paint.setColor(SUB); paint.setTextSize(dp(12)); c.drawText("P3 参考需 Android 10+", 0, dp(100), paint); }
        }
    }
    private final class Ruler extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ruler(Context c) { super(c); setContentDescription("100 dp 标尺，每格 10 dp"); }
        @Override protected void onDraw(Canvas canvas) {
            p.setColor(ACCENT); p.setStrokeWidth(dp(1));
            canvas.drawLine(dp(3), dp(22), dp(103), dp(22), p);
            for (int i = 0; i <= 10; i++) canvas.drawLine(dp(3 + 10 * i), dp(i % 5 == 0 ? 10 : 16), dp(3 + 10 * i), dp(27), p);
            p.setTextSize(dp(12)); canvas.drawText("100 dp", dp(116), dp(26), p);
        }
    }
}
