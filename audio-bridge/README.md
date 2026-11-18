# Lyricify 内录助手

该独立 APK 负责 Android 系统播放捕获；AM++ 插件本身不能请求 MediaProjection 授权或声明新的宿主权限。

## 安装和使用

1. 安装 `output/lyricify-audio-bridge-1.1.0.apk`。
2. 在 AM++ 导入 `output/lyricify-backgrounds-4.1.0.zip` 并启用插件。
3. Apple Music 播放音乐时，在插件设置点“打开内录助手并授权”。
4. 在助手中点“启动系统内录”，授予录音权限和 Android 系统录音授权。
5. 返回 Apple Music。通知会显示捕获状态；通知里的“停止”可结束采集。

助手只匹配 `com.apple.android.music` 的 `USAGE_MEDIA` 播放，并在本机提取低频脉冲，不保存或上传 PCM。停止或再次启动都需要新的系统授权。Android 10 以下不支持此路径。

Android 播放捕获遵守源应用的捕获策略。如果 Apple Music 或 DRM 禁止捕获，助手会显示“已授权但没有收到音频”，无法通过插件绕过。此时背景仍可使用插件里单独的节拍替代选项。

## 构建

源码在 `audio-bridge/src`，使用 F 盘下 `toolchain` 的 JDK 17 和 Android SDK 37 编译，最低 Android 10 / API 29，目标 API 35。签名密钥保存在 `output/lyricify-audio-bridge.keystore`，仅用于本地安装包。

