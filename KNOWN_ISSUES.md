# 已知问题与未验证范围 / Known issues

以下项目是风险与待办记录，不代表都已复现为产品缺陷。当前发布依据见 [发布基准](docs/RELEASE-POLICY.md)。

## Android Beta

- Targeted for Android 14+ ARM64 tablets, currently the Xiaomi Pad 5 Pro. Other device layouts and vendors are not part of this beta's acceptance scope.
- Office documents can be viewed only through supported project/PDF flows; in-process Word/PPT editing is deferred.
- Physical stylus latency and hardware-specific side-button behavior, real DocumentsUI grant recovery, and a complete OpenAI-compatible service workflow remain unverified end to end.
- The published APK is signed with the local Android debug key for sideload testing. Future updates must use the same signing key; if it is unavailable, uninstall the beta before installing another signed build.

This beta does not claim full parity with the Windows or Mac applications.

## 通用风险与待办

| 项目 | 状态与后续处理 |
|---|---|
| 历史长测约 54 分钟时窗口关闭 | 原因未明；重开后已保存内容恢复。历史中断不记作一小时通过，保留后续排查。 |
| Mac 全流程与环境覆盖 | 贡献者报告部分实机测试，维护者未独立复验全部 Mac 环境；继续测试版。 |
| 雨课堂账号与实时课堂 | 桌面/Android 协议回归通过；Android 14 平板 AVD 已打开应用内登录并显示雨课堂返回的二维码。真实账号确认、历史课件导入、Windows 运行和真实课堂逐页采集尚未验证；Mac arm64 冷启动待解锁后进行。Windows 目录包构建提示 `mcp-dist/mcp-node.exe` 缺失。真实课堂测试需用户明确确认可能产生的签到记录。 |
| 多种 Office、DPI、双屏和低配置设备 | 未穷尽环境；报告具体条件后逐项处理。 |
| Windows 签名与安全软件 | 当前未签名。早期 NSIS 安装包曾被火绒查杀，未取得厂商复核结论；不将其宣称为误报。新文件以实际检测为准。 |
| Mac 签名、公证和首次打开 | 当前构建未签名、公证；系统可能拦截，需后续完善分发体验。 |
| OCR 运行库与识别质量 | 部分 Windows 环境可能缺少 VC++ 运行库；OCR 不是无损识别，图表仍需结合原页图查看。 |
| 旧 2.0 测试版升级至 1.0 | 有意重置首个正式版本编号；完全退出旧版，手动安装或完整解压新 ZIP。若系统拒绝覆盖，可卸载应用后安装；先备份项目，保留用户数据。 |
| AI、Word/PPT 外部依赖 | 不内置大模型；AI 客户端、服务额度和本机 Office 授权需用户自行准备。 |

遇到 Bug 请[提交 Issue](https://github.com/obroccolio/pagenote/issues/new)，附版本、系统、操作步骤及脱敏截图；不要上传私人课件或连接密钥。

Windows stable is founder-approved; macOS remains experimental and the Android app is a beta. Pending work includes the unexplained interrupted soak, wider environment coverage, signing/notarization, antivirus review, OCR prerequisites, and unverified Android device coverage. These are tracked risks under the early-stage release policy, not claims that every environment has passed.
