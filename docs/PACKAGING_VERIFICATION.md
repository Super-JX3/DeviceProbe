# 本次交接验证记录

## 后续发布准备

- 用户已选择 Apache-2.0，添加完整 `LICENSE` 并更新许可证说明；原交接记录中“未选择”描述为历史状态。

- 用户反馈已完成 1.4.0 真机测试，无问题；未提供机型、系统版本与逐项记录。
- 本次准备发布时，原交接清单所有文件 SHA-256 校验通过；`bash -n build.sh` 通过；macOS / Zulu JDK 17.0.7 重新执行 15 项数值测试与 11 项音频判定测试，均通过。
- 本次未重新完整构建 Android APK，也未重跑需 Android 工具链的 17 项报告测试。下面的 43 项完整测试记录属于原交接构建。

日期：2026-11-06（Asia/Shanghai）。目标：1.4.0 / versionCode 5。

## 实际完成

- `bash -n build.sh` 通过。
- Linux x86_64，Temurin JDK 17.0.20.1，Android Platform 35，Build-Tools 35.0.0。
- 显式 `DEVICE_PROBE_TOOLS` 旧布局入口完整构建成功。
- 清空 `DEVICE_PROBE_TOOLS`，分别设置 `JAVA_HOME`、`ANDROID_JAR`、`BUILD_TOOLS_DIR` 的独立工具链入口完整构建成功，不依赖相邻项目的默认路径。
- 两次构建均通过 15 项数值、11 项音频、17 项报告测试，共 43 项。
- APK v2/v3 签名验证通过，16 KiB 参数的 zipalign 对齐检查通过。
- Manifest：包名 `cn.litebot.deviceprobe`，min API 26，target API 35，版本 1.4.0 / 5；零 requested permissions。
- APK 无本机库、源码或签名密钥，无已移除的 Vivid/Vulkan 代码或服务。
- 原项目与交接副本的 `src/`、`res/`、`tests/` 和 `AndroidManifest.xml` 逐文件比较一致。
- 构建有 5 条 Android API 弃用警告，不是编译错误；本次未改应用代码处理这些警告。

第二次构建生成 APK 的 SHA-256：

`8152e32831bf09339b98b720a1b7311451a9eea324db75dd33ecbad96244b5f3`

该 APK 仅作本地验证，不在源码 ZIP 中；开发签名、签名时间和 ZIP 时间戳可能导致后续构建哈希不同，本包不宣称字节级可复现构建。

## 未完成、不能据此宣称完成

- 未连接手机或模拟器，未做 UI、生命周期、真实 HDR/Atmos 播放、授权或 DRM 测试。
- 未在 macOS、Windows/WSL 或全新下载的标准 SDK 目录上运行；默认标准 SDK 路径分支只做脚本检查，实际完整构建使用上列两种显式配置入口。
- 未创建 GitHub 仓库、CI、Release 或正式发行签名。
- 未替所有者选择开源许可证；此交接不等于法律或供应链审计。

根目录 `SHA256SUMS` 给出每个交付文件的 SHA-256；可在解压后的项目目录执行 `sha256sum -c SHA256SUMS` 校验。该清单不包含自身，也不是数字签名。
