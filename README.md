<p align="center">
  <img src="./icon.jpeg" alt="奶读 Icon" width="136" style="border-radius: 28px;" />
</p>

<h1 align="center">奶读 (NaiDu)</h1>

<p align="center">
  一款优雅流畅的 Android 原生英语学习阅读器（Kotlin + Jetpack Compose + Material 3）<br/>
  支持 <b>TXT / Markdown / EPUB</b> 小说式左右翻页阅读 · 双击查词与划词翻译 · 美/英真人原声 · SM-2 单词本与随身磨耳朵
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-2E5E4E?style=flat-square" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Language-Kotlin%201.9-7F52FF?style=flat-square" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?style=flat-square" alt="Compose"/>
  <img src="https://img.shields.io/badge/Offline%20Dict-41%2C106%20Words%20%2B%2033%2C867%20Lemmas-D9822B?style=flat-square" alt="Offline Dictionary"/>
</p>

---

## 核心特性

### 1. 多格式本地书籍导入与小说式分页阅读
- **支持格式**：**TXT**（`juniversalchardet` 自动检测 UTF-8 / UTF-16 / GBK / GB18030，智能识别中英文章节与大文件分段）、**Markdown (`.md`)**（标题、引用、粗斜体、代码块、表格、列表、图片渲染与目录生成）、**EPUB 2 & EPUB 3**（解析封面、元信息、真实章节目录 `nav.xhtml` / `toc.ncx` 与内嵌插图）。
- **丝滑 5 种左右翻页动画**：采用双层 GPU 合成横向多栏分页（禁止上下误滑），支持 **覆盖翻页 / 仿真翻页 / 平滑平移 / 淡入淡出 / 无动画** 实时切换，跨章自动无缝衔接。
- **贴心阅读操控**：
  - 单击左侧 30% 上一页、右侧 30% 下一页、中间 40% 呼出菜单；**双击单词查词**不误触翻页。
  - **硬件音量键翻页**（可自由开关，翻页时自动拦截系统音量弹窗）。
  - **自动翻页**（支持 2–60 秒自定义间隔，运行中自动保持屏幕常亮，查词或呼出菜单时自动暂停计时）。
  - **4 套阅读主题**（羊皮纸 / 纯净白 / 护眼绿 / 柔和夜）+ 实时调节字号、行距、边距、衬线/无衬线/等宽字体。
  - 支持直接从微信 / QQ / 文件管理器选择「用奶读打开」，并内置 3 篇示例文档（MD / EPUB / TXT）开箱体验。

### 2. 双击详尽查词 & 长按划词 / 划句翻译 + 真人原声
- **离线 + 在线全义项词典**：
  - 内置基于 MIT 协议 ECDICT 编译的 **41,106 条核心词条 + 33,867 条词形还原**离线 SQLite 词库，断网也能毫秒弹出音标、释义与考纲标签（中高考 / CET4 / CET6 / 考研 / IELTS / TOEFL / GRE / 牛津核心 / 柯林斯星级）。
  - 联网时自动后台无感补全 **有道 + 柯林斯英汉双解** 全部义项、分词性详解、词态变化、高频短语搭配、同根派生词、同近义词与双语例句，并持久化缓存到本地 SQLite（二次查看离线秒开）。
  - 变形词（如 `charged`、`studying`、`went`）自动联动拉取原型词完整义项。
- **长按划词 / 划句翻译**：长按拖选短语或长句后，系统菜单置顶 **「译 翻译 / ★ 记生词」**，同时底部弹出 **「划词翻译」** 快捷条；内置有道 / MyMemory / Google 自动容错，并支持在设置中选配 **DeepSeek / 通义千问 / OpenAI** 等兼容 AI 接口获取语境精讲。
- **美/英双真人发音**：默认播报纯正美式/英式真人 MP3 音频并自动缓存到本地（上限 50MB），单词、短语、例句与复习卡片均可即点即听；离线未缓存时自动优雅回退系统 TTS。

### 3. 单词本 2.0 & 多模式记忆复习系统
- **完整单词详情页 & 多原句溯源**：复用完整词典内容（美/英双音、柯林斯双解、搭配词族）；在多本书/多章节反复遇到同一生词时自动累计 **遇词次数** 并追加多条阅读原句，支持点击原句 **一键回跳原书对应章节**。
- **速览遮罩（红膜自测）& 高效管理**：列表一键切换「遮中文 / 遮英文」速刷自测；支持按状态（待复习 / 顽固词 / 星标 / 已掌握）、**来源书籍 / 自定义分组 / 考纲级别**多维筛选与 6 种排序；长按进入批量管理；支持 **粘贴/文件批量导入词表**（离线词库自动补全）及导出 **CSV（Excel / Anki）** 与 **Markdown**。
- **5 大主动复习模式**（支持按待复习 / 顽固词 / 星标 / 指定书籍 / 全部定制范围与词量）：
  1. **记忆卡片（SM-2 遗忘曲线）**：美/英双发音 + 可遮挡阅读原句 + 四档动态间隔评分。
  2. **释义四选一**：快速检验词义辨析。
  3. **拼写默写**：听真人发音 + 看释义/挖空原句拼写，支持提示字母与逐字红绿纠错比对。
  4. **听音辨义**：隐藏拼写盲听美/英发音选义，同步练听力反射。
  5. **原句填空（Cloze）**：直接拿读过的书里真实原句挖空目标词完形训练。
- **随身磨耳朵（单词电台）**：支持设置每词重复遍数、停顿秒数、英美发音交替与连读原句，适合通勤/睡前听词巩固。
- **学习统计与阅读器双向联动**：今日目标进度条与连续打卡天数（Streak）、掌握度分布条、**近 28 天学习热力图**、**未来 7 天 SM-2 复习负载预测**、顽固词榜；阅读正文中自动按掌握度**分级标亮**生词（待复习/顽固词醒目提示，已掌握词自动隐藏不打扰），目录抽屉内置「本书生词」实时联动。

---

## 项目结构

```text
app/src/main/
├── assets/
│   ├── dictionary.zdb              # 压缩后的 41,106 词条 + 33,867 词形还原离线 SQLite 词库
│   └── samples/                    # 内置示例文档（MD / TXT / EPUB）
├── java/com/lingoreader/app/
│   ├── data/
│   │   ├── db/                     # AppDatabaseHelper (SQLite v3: books, vocab, dict_cache, study_logs)
│   │   ├── model/                  # 数据模型与枚举定义
│   │   ├── prefs/                  # DataStore 设置持久化
│   │   ├── repository/             # BookRepository & VocabRepository
│   │   └── sm2/                    # Sm2Scheduler 遗忘曲线 & StudyStatsCalculator 学习统计
│   ├── parser/                     # TxtParser, MarkdownParser, EpubParser
│   ├── translation/                # OfflineDictionary, OnlineTranslator, TranslationService, TtsManager
│   └── ui/
│       ├── bookshelf/              # 书架页（导入、进度续读、本书生词统计）
│       ├── reader/                 # 分页 WebView 引擎、5 种翻页动画、双击查词弹窗、自动翻页
│       ├── vocab/                  # 单词本、单词详情页、统计热力图、5 模式复习、随身磨耳朵
│       ├── settings/               # 排版翻页、真人发音、在线与 AI 翻译配置
│       └── theme/                  # Material 3 暖纸色主题
└── res/                            # 奶读图标与资源文件
```

---

## 本地构建与运行

### 环境要求
- **JDK 17**
- **Android SDK**（`compileSdk 34` / `minSdk 26` / Android 8.0+）
- **Gradle 8.7**（或直接使用 Android Studio Koala 及以上版本打开）

### 快速构建步骤

1. 克隆仓库并在项目根目录创建 `local.properties`，配置本机 Android SDK 路径：
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```
2. 使用 Android Studio 打开项目点击 Run，或在命令行执行：
   ```bash
   # Linux / macOS
   gradle assembleDebug testDebugUnitTest

   # Windows (PowerShell)
   $env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
   $env:ANDROID_HOME = "C:\Users\a\Android"
   gradle assembleDebug testDebugUnitTest
   ```
3. 编译生成的安装包位于：
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```
4. 通过 ADB 安装到手机或模拟器：
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 开源致谢

- 离线基础英汉词库数据整理自开源项目 [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT)（MIT License）。
- TXT 字符集自动检测基于 [juniversalchardet](https://code.google.com/archive/p/juniversalchardet/)。
