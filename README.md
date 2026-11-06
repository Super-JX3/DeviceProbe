# 屏幕与设备检测 1.4.0

## 最新验证状态

- 用户已完成 1.4.0 真机测试并反馈无问题；未提供机型、系统版本或逐项测试记录，不据此宣称覆盖全部设备或完成 HDR/Atmos 播放认证。
- 交接阶段完整构建与 43 项主机测试通过，详见 `docs/PACKAGING_VERIFICATION.md`。
- 本次发布准备重新校验全部交接文件哈希，构建脚本语法检查与 26 项独立主机测试通过；未重新执行完整 Android 构建与另外 17 项报告测试。

下文“此环境未连接真机”等描述记录此前自动构建环境，真机测试状态以上述用户反馈为准。

Android 8.0+，原生 Java，无第三方运行库，无联网权限，无需 root。一个 APK 兼容 ARM32、ARM64、x86、x86_64，不包含本机库。本地开发签名，不是任何手机厂商的官方检测工具。

## 能检测什么

- 显示模式分辨率、长短边比例、最高枚举分辨率、全部刷新率模式。
- 当前窗口/内容区的 px 与 dp 尺寸、系统栏安全边距、最小宽度。
- 逻辑 densityDpi、dp→px 缩放倍率、系统字体倍率以及实际 sp→px 换算。
- 驱动申报的 xdpi/ydpi 与估计 PPI/英寸数；可以自行输入屏幕对角线计算 PPI。
- 当前模式和枚举模式上报的 HDR 类型：HDR10、HLG、HDR10+、Dolby Vision；亮度仅为申报值。
- 广色域显示能力、Android 广色域管理路径、本窗口实际广色域状态、系统首选广色域空间。
- E-AC-3 JOC/E-AC-3/AC-4/TrueHD/Dolby Vision 解码器声明，杜比厂商音效线索，Android Spatializer 状态及 5.1 PCM 空间化能力。
- 系统可见总 RAM、可用 RAM、内存警戒值、本应用堆上限。GiB/GB 分别展示。
- 手动 15 秒界面回调帧率采样、sRGB/P3 色块参考、100 dp 标尺。
- 复制、通过系统分享菜单发送报告、通过系统文件选择器导出 TXT。无存储权限。

## 必须知道的局限

1. Dolby 播放器格式能力与标准 Android Spatializer 分开显示。明确声明 audio/eac3-joc 时报告 Atmos 格式解码能力；audio/eac3 按 Dolby 官方播放器指南作为 DD+ / DD+ JOC 集成解码检测入口；AC-4 单独列出，不凭这个 MIME 保证 Atmos。厂商音效线索、格式解码、组件创建、实际 Atmos 渲染和授权认证互不等价。Spatializer 当前关闭不会否决 Dolby 解码能力。
2. 系统枚举最高分辨率不保证为面板原生分辨率；驱动 xdpi/ydpi 也可能不准确。没有准确的面板像素数和真实对角线，软件不能保证真实 PPI。
3. 界面缩放倍率以 Android 的 160 dpi 基准计算，并不是用户相对出厂“显示大小”滑块的倍率。字体缩放从 Android 14 开始可能是非线性的。
4. HDR/广色域/色彩管理为 API 能力与窗口状态报告，不是仪器实测。不测试流媒体 DRM、视频实际解码、真实峰值亮度、色准、面板位深或色域覆盖率。
5. 当前显示模式/刷新率不等于 LTPO 面板的真实瞬时扫描频率。手动帧率采样也仅代表应用回调率，会增加界面活动。
6. RAM 总量是内核交给系统管理的可见总量，不等于厂商标称容量；不把内存扩展当成物理 RAM。
7. 更改分辨率/显示大小、旋转、分屏或插拔耳机后点击“重新检测”。应用不修改这些系统设置。

## 隐私

Manifest 不声明任何权限，包括 INTERNET。无网络库、无广告、无统计。不会读取设备唯一 ID、IMEI、序列号、账户、网络地址。报告包含品牌/型号/系统补丁和能力信息，请仍自行确认后再分享。仅在应用私有偏好保存手工输入的屏幕对角线。导出位置由你通过系统文件选择器决定。

## 构建和验证

本源码交接包保留 1.4.0 的应用代码，只调整构建脚本的工具路径发现方式。需要 JDK 17、Android SDK Platform 35、SDK Build-Tools 35.0.0、Bash、zip 和 unzip；无需 Gradle、NDK/C 编译器或第三方 Java 运行库。SDK/JDK 由使用者自行安装，不包含在源码包中。

Linux/macOS 或 WSL 示例（路径改成自己的安装位置，SDK 的二进制必须与构建主机匹配）：

```bash
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_SDK_ROOT="/path/to/android-sdk"
bash build.sh
```

标准 SDK 目录应包含 `platforms/android-35/android.jar` 和 `build-tools/35.0.0/`。其他 build-tools 版本可通过 `ANDROID_BUILD_TOOLS_VERSION` 指定；也可分别设置 `ANDROID_JAR` 和 `BUILD_TOOLS_DIR`。仍兼容显式指定旧布局的 `DEVICE_PROBE_TOOLS`，但不再默认依赖别的项目文件夹。Windows 原生 PowerShell 不直接运行此 Bash 脚本；请用 WSL 并安装 Linux 版本工具链，或另行迁移构建系统。

`bash build.sh` 编译 Java、运行主机测试、D8 转换、打包及本地开发签名。每次在 build 下创建独立构建目录，避免旧类混入安装包。

单元测试覆盖比例、旋转、PPI、无效输入、GB/GiB 等计算；另外验证 APK 签名、对齐、Manifest 权限和最低系统版本。此环境没有连接手机或 Android 模拟器，未声称完成真机测试。

构建产物 `DeviceProbe-1.4.0.apk`。首次构建自动生成本机开发密钥，保存在 `build/signing/local.jks`；脚本里的 `android` 是通用开发口令，不是生产凭据。此源码包不包含旧密钥，新构建 APK 不能保证覆盖安装此前发出的版本。请勿为覆盖安装而公开旧密钥，也不要未经备份自动卸载现有应用。正式发布前由维护者确定并妥善保管发行签名方案。

## 发布前必读

本项目采用 Apache License 2.0，完整条款见根目录 `LICENSE`。Copyright 2026 Super-JX3。允许依照许可证使用、修改和分发，包括商用。发布 APK 时仍需确定并妥善保管发行签名。`docs/THIRD_PARTY_NOTICES.md` 说明外部工具和商标的边界。没有附带杜比专有 SDK、编解码库、授权测试视频或手机实测报告。

## 1.4.0 移除 Vivid 专用模块

- 按用户要求移除 Vivid 查询按钮、首页结果栏、报告字段和说明卡。
- 删除专用 Java 查询类、独立服务、本机 C/JNI 源码及对应测试；Manifest 不再声明查询服务，构建不再依赖 NDK，也不打包本机库。旧版 APK 和既有构建副本保留，未删除用户数据。
- 简洁首页为当前分辨率加 10 行结论；HDR10/HLG/HDR10+/Dolby Vision、杜比音频、PPI、缩放、刷新率、内存及测试工具保持原有逻辑。
- 43 项主机测试通过；构建另断言无本机库、DEX 中无 Vivid/Vulkan 字符串、Manifest 无服务，并校验签名、对齐和零权限。未声称完成本版真机测试。

以下为旧版本功能记录，不代表 1.4.0 仍包含相关模块。

## 1.3.0 非播放式 Vulkan HDR Vivid 查询

- 简洁首页新增“查询 HDR Vivid（不播放）”。用户点击后绑定不导出的 `:vivid_probe` 私有进程服务，后台加载系统 `libvulkan.so`；主界面不加载本机库。15 秒超时、连接失败、进程崩溃均回报“待验证”，结束后解绑。若驱动仍在阻塞，服务销毁会终止该私有进程；不终止应用主进程。
- 最多请求 Vulkan 1.1；旧驱动使用 1.0 + 已枚举的 `VK_KHR_get_physical_device_properties2`。先枚举物理设备的 `VK_HUAWEI_hdr_vivid`，只对确实声明该扩展的设备链接 `VkPhysicalDeviceHdrVividFeaturesHUAWEI` 并查询 `hdrVivid`。无扩展时绝不提交未知特性链。
- NDK r27c 尚无这项扩展的头文件定义，所以在原生头文件中按 Khronos 规范补充 ABI 结构和 sType 常量；不是按屏幕 HDR 类型数字猜测。
- 没有扩展不等于整机不支持。扩展存在但能力位为假时只描述该接口；有肯定能力位时显示“接口声明支持 · 未验证显示”。正面证据即使伴随其他设备的枚举异常也保留，异常数在详情单列。
- 不创建逻辑设备、交换链，不播放、不修改显示设置、不扫描机型私有属性、不上传 GPU 名称或唯一标识。结果仅包含计数、能力位和固定错误类别。
- 本机库四架构，ELF 16 KiB LOAD 对齐、栈不可执行，使用系统 libc/libdl，不强链接 Vulkan；库加载失败不拖垮主界面。APK 仍不声明任何权限。
- 89 项主机检查：15 数值计算 + 11 音频证据 + 17 报告 + 26 Vivid 判定 + 20 原生 Vulkan 模拟场景。原生测试启用 ASan/UBSan；沙箱不支持 LeakSanitizer 进程检查，故仅禁用其泄漏检查。真实设备 Vulkan 驱动、服务生命周期及 UI 尚需用户安装验证。
- 旋转/销毁活动时取消未完成查询，结果不持久保存；重进应用后可再次点查询。

接口依据：

- https://docs.vulkan.org/refpages/latest/refpages/source/VK_HUAWEI_hdr_vivid.html
- https://docs.vulkan.org/refpages/latest/refpages/source/VkPhysicalDeviceHdrVividFeaturesHUAWEI.html
- https://docs.vulkan.org/refpages/latest/refpages/source/vkEnumerateDeviceExtensionProperties.html

## 1.2.0 简洁视图与 HDR Vivid 边界

以下描述 1.2.0 的历史行为；1.4.0 已移除专用 Vivid 字段和查询。

- 默认仅展示当前分辨率和 11 行简要结论，原始模式列表、解释和测试工具收进“展开详细信息”；展开状态在本次活动重建时保留。
- 复制/分享/导出跟随当前视图：默认简报，展开后完整详情。主界面动态状态仍每 2 秒更新，其他结论点“重新检测”更新。
- PPI 保留“驱动估算/按输入英寸计算”限定，不把 densityDpi 当物理 PPI。
- HDR Vivid 一律显示“待验证 · 本版无法自动确认”。没有接入厂商 Vivid SDK/Vulkan 扩展，没有播放样片，不靠机型名、HEVC Main10、HDR10 或未知 HDR 类型编号判支持与否。
- 核实须结合确切机型/固件的官方规格，以及支持 Vivid 的播放器对已知 Vivid 动态元数据样片的处理路径证据。普通 HDR 图标/变亮/能够播放可能只是兼容回退，不是 Vivid 证明。
- 通用 Display HDR 类型枚举不提供 Vivid 支持的通用判据。较新 MediaQualityContract 的 HDR_VIVID 常量描述流状态，不是一个可直接套用于所有旧款手机的能力开关。部分厂商有专用能力，如 Vulkan VK_HUAWEI_hdr_vivid，但渲染扩展能力与整机显示路径、认证不可混为一谈。本版未调用这些接口。
- 共 43 项主机测试，另验证签名、对齐、版本及零权限；没有连接真机，不声称完成 UI/播放真机测试。

Vivid 参考：

- https://developer.android.com/reference/android/view/Display.HdrCapabilities
- https://developer.android.com/reference/android/media/quality/MediaQualityContract#STREAM_STATUS_HDR_VIVID
- https://docs.vulkan.org/spec/latest/chapters/features.html#features-hdrVivid

## 1.1.0 播放器能力改进

- 采用 Dolby 公开 Android 移动播放器文档的 MIME 检测思路；苹果没有在所查公开资料中披露 Apple Music 内部算法，本应用没有复刻/调用其私有代码。
- 改用 REGULAR_CODECS 枚举普通播放候选，避免把安全专用/隧道专用组件作为通用播放承诺。
- 添加显式“验证解码器能否创建（不播放）”按钮。最多选取 JOC/E-AC-3/AC-4 各一个候选，同名去重，创建后释放。不自动配置或播放，不干扰系统开关。创建失败可能是资源占用或实现问题，不改判为永久不支持。
- 不内置杜比样片，未进行格式配置、实际解码、渲染或扬声器听感测试。苹果图标也不由本应用读取。
- 补充电话网络音频通路（设备类型 18）的文字说明。
- 新增 11 项音频判定测试，总计 31 项主机测试；仍没有真机执行环境。

Dolby 依据：https://professionalsupport.dolby.com/s/article/Enabling-Dolby-Atmos-in-Android-Mobile-media-apps

Apple 公开支持文档：https://support.apple.com/zh-cn/109354

## API 依据

- https://developer.android.com/reference/android/view/Display
- https://developer.android.com/reference/android/view/Display.Mode
- https://developer.android.com/reference/android/util/DisplayMetrics
- https://developer.android.com/reference/android/view/WindowMetrics
- https://developer.android.com/training/wide-color-gamut
- https://developer.android.com/reference/android/media/Spatializer
- https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
