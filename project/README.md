# AM背景Pro Max（AM++ 插件）

把 Apple Music 安卓版歌词页的动态背景替换为流体材质：封面重度模糊后经过三层旋转与网格形变，可选接入音频低频驱动。

这是一个独立的 AM++ 插件，由本仓库自行维护。

## 视觉渲染

- OpenGL ES 2 GPU 绘制，封面最多缩放至 512 像素边长；可见时按屏幕同步目标刷新，隐藏后停止请求帧。
- 三层封面旋转、平移和缩放；两次分离高斯模糊；透明边缘归一化。
- 网格控制点、Catmull–Clark 细分与横屏形变预设。
- 低频脉冲按 `1 + 0.33 × pulse²` 缩放旋转封面层，再进入模糊与网格渲染。
- 背景透明度跟随歌词抽屉的开合位置，收起时让位给原生背景。

## 安装

1. 安装 `output/` 下的「音频捕获for AM背景Pro Max」APK（可选，仅真实音频律动需要）。
2. 在 AM++ 中导入 `output/am-bg-pro-max-*.zip`，启用并重启 Apple Music。
3. 音频律动默认开启；首次使用时在弹出的音频捕获应用中完成授权。

内录应用以 Android `AudioPlaybackCapture` API 按 Apple Music UID 和 `USAGE_MEDIA` 过滤音频，只在本机计算低频包络并传给插件，不保存或上传 PCM。Android 10 以下不可用；停止或再次启动时需重新确认系统授权。

系统遵守 Apple Music 的音频捕获策略。如果应用策略或 DRM 使系统返回静音，插件无法绕过；应用和插件诊断会显示“已授权但没有收到音频”，此时可使用节拍替代律动。

插件当前支持 Apple Music 7.0.0 系列；已验证版本码为 1606、1607、1609、1613、1614、1622、1624、1626。

## 构建

插件源码位于 `project/src`，应用源码位于 `audio-bridge/src`。构建使用 `toolchain` 下的 JDK 17 与 Android SDK 37，应用最低 API 29。运行 `project/build.ps1` 构建插件、`audio-bridge/build.ps1` 构建应用；AM++ Plugin API 源码保存在 `project/vendor`。产物在 `output`。

## 来源与致谢

本插件（AM背景Pro Max）**不是** Lyricify 项目的插件，也不代表 Lyricify 或其开发者发布；两者之间没有隶属、授权或背书关系。

视觉管线（网格控制点、Catmull–Clark 细分、高斯模糊核、色彩处理参数与三层旋转结构）移植自开源项目 [WXRIW/Lyricify-Backgrounds](https://github.com/WXRIW/Lyricify-Backgrounds)，该项目以 Apache License 2.0 发布。相关实现出现在：

- `AppleMusicIosMesh.cs` → `render/MeshPresets.java`、`render/MeshWarp.java`
- `AppleMusicIosBackground.hlsl` → `render/FluidGlRenderer.java`、`render/BlurKernel.java`

上游许可证文本随插件 ZIP 提供，位于 `assets/LICENSE.txt`。感谢 WXRIW 及 Lyricify-Backgrounds 的作者公开这些实现。

AM++ Plugin API 仅作为编译接口使用，不随插件分发。
