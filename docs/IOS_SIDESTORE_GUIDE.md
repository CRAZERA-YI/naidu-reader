# 奶读 (NaiDu) iOS 版：免费 Apple ID 自签 + 全自动续签完整指南

无需 MacBook、无需 99 美元苹果开发者账号，只需要一台 **Windows / Linux 电脑（仅首次配对用 5 分钟）** + **iPhone（iOS 16.0 及以上）**，即可使用免费个人 Apple ID 安装「奶读 iOS 版」并实现**手机端连 Wi-Fi 每日自动静默续签**。

---

## 一、整体原理

1. **云端免费 Mac 编译**：本项目通过 GitHub Actions (`macos-latest`) 自动调用 Xcode 编译生成未签名安装包 **`NaiDu-iOS-v1.1.0.ipa`**，并托管官方 SideStore 软件源 [`sidestore-source.json`](../sidestore-source.json)。
2. **手机端离线重签（SideStore + StosVPN）**：首次在电脑上安装好 **SideStore** 后，SideStore 能在手机端直接通过本地回环 VPN（StosVPN）向苹果免费续期个人证书，**后续永远不再需要连接电脑**。
3. **双重自动续签保障**：
   - **系统级静默自动续签**：通过 iOS 自带「快捷指令自动化」，每天定时在后台自动连上 StosVPN 并执行 SideStore 的「刷新所有应用」，每天自动重置回满血 7 天。
   - **奶读内置续签守护中心**：「奶读」App 会直接解析包内 `embedded.mobileprovision` 证书的真实到期时间，在书架顶部与「设置与续签」页实时显示剩余天数/小时，并在剩余 **48h / 24h / 6h** 时发送本地推送通知提醒，支持一键跳转 `sidestore://` 刷新。

---

## 二、首次电脑端准备工作（仅需做一次，约 5 分钟）

1. 在电脑上前往 SideStore 官网（`https://sidestore.io`）下载：
   - **Windows**：安装苹果官方版 iTunes 与 iCloud（非微软商店版），下载 `AltServer` / `SideServer` 与 `JitterbugPair`。
   - **Linux**：使用 `idevicepair` + `AltServer-Linux` 生成配对文件。
2. 用数据线将 iPhone 连接电脑，在手机上点击「信任此电脑」，运行 `JitterbugPair` 生成 `.mobiledevicepairing` 配对文件并发送到手机「文件」App。
3. 使用电脑端安装器将 `SideStore.ipa` 装入手机，在 iPhone「设置 → 通用 → VPN 与设备管理」中信任你的 Apple ID 开发者证书，并在「隐私与安全性」底部开启**开发者模式**。
4. 在 App Store 免费下载安装 **StosVPN**（或 WireGuard 导入 SideStore 配置），打开 SideStore 导入刚才生成的配对文件并登录你的免费 Apple ID（建议使用小号更安心）。

> 完成以上步骤后，拔掉数据线，**以后再也不需要用电脑重签了**！

---

## 三、在 SideStore 中一键安装「奶读」并开启更新源

### 方法 A：添加「奶读」官方自动续签源（推荐，后续新版本自动提示更新）
1. 确保手机已开启 **StosVPN** 且连接在任意 Wi-Fi 下。
2. 打开 **SideStore** → 底部切换至 **Sources（软件源）** → 点击右上角 **`+`**，粘贴以下源地址添加：
   ```text
   https://raw.githubusercontent.com/CRAZERA-YI/naidu-reader/main/sidestore-source.json
   ```
3. 点进「奶读 (NaiDu) 官方自动续签源」，点击 **奶读** 右侧的 **`FREE` / 安装** 即可直接签名安装到桌面！
   *(注：安装好打开奶读后，也可以在「设置与续签」页点击「一键将奶读源加入 SideStore」)*

### 方法 B：直接导入 `.ipa` 文件安装
1. 前往仓库 [GitHub Releases](https://github.com/CRAZERA-YI/naidu-reader/releases) 下载最新的 **`NaiDu-iOS-v1.1.0.ipa`** 到手机「文件」App。
2. 开启 **StosVPN** → 打开 **SideStore** → **My Apps** → 左上角 **`+`** → 选择刚才下载的 `NaiDu-iOS-v1.1.0.ipa` 即可完成自签安装。

---

## 四、配置「每日连 Wi-Fi 全自动静默续签」（一劳永逸关键步骤！）

在 iPhone 上设置一条 30 秒搞定的系统自动化，让手机每天自动帮你续满 7 天：

1. 打开 iPhone 自带的 **「快捷指令 (Shortcuts)」** App → 点击底部 **「自动化」** → 右上角 **`+`**。
2. 选择 **「特定时间」**：比如设为每天晚上 `22:30` 或凌晨 `04:00`（选你通常连着家里/宿舍 Wi-Fi 的时间），重复选 **「每天」**，底部务必勾选 **「立即运行」**（关闭“运行时询问”），点击下一步。
3. 选择 **「新建空白自动化」**，依次添加以下 3 个动作：
   - **动作 1**：搜索 `VPN` → 选择 **「设置 VPN」** → 设为 **「连接」** `StosVPN`
   - **动作 2**：搜索 `等待` → 选择 **「等待」** → 设为 `3 秒`
   - **动作 3**：搜索 `SideStore` → 选择 **「Refresh All Apps（刷新所有应用）」**
4. 点击右上角 **「完成」**！

从此以后，每天到点只要你的 iPhone 连着 Wi-Fi，系统就会在后台无感把「奶读」和「SideStore」本身的签名全部重置回 **7 天满有效期**；即使偶尔出差几天没连 Wi-Fi，「奶读」内置的 **48h / 24h / 6h 到期守护通知** 也会提前提醒你一键点击刷新！
