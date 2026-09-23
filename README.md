# 极弈 (JiYi) - 中国象棋桌面分析软件

一个免费开源的中国象棋对弈/分析/连线软件。支持 UCI/UCCI 协议引擎、YOLO 视觉识别自动连线、开局库、PGN 棋谱管理。

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-23-green)
![License](https://img.shields.io/badge/License-GPLv3-blue)

---

## ✨ 主要特性

- 🎮 **本地对弈** - 人人对弈、人机对弈
- 🤖 **引擎分析** - 自动识别 UCI/UCCI 协议引擎，MultiPV 多主变、立即出招、变招分析
- 📊 **评分趋势图** - 实时显示局面评分变化
- 🔗 **连线对弈** - YOLO 视觉识别棋子，自动走子（前台/后台两种点击方式）
- 🔁 **自动续盘** - 截图标定按钮模板，自动点击"再来一局"
- 📖 **开局库** - 本地库（兵河/象棋桥/鹏飞格式）+ 云库查询
- 📝 **棋谱管理** - PGN 格式完整支持、导航、变招
- 🖼️ **图片导出** - 导出/复制棋盘图片
- ⚙️ **完整设置** - 时间、连线、开局库设置

---

## 🚀 快速开始

### 系统要求

- **操作系统**：Windows 10+（连线/自动点击功能依赖 Windows API）
- **Java**：无需安装——发布包已内置运行时（源码构建需 JDK 21+）
- **内存**：2GB RAM（推荐 4GB）

### 运行程序

#### 方式一：使用发布包（推荐）

1. 从 [Releases](../../releases) 下载发布包并解压到任意目录
2. 双击 `极弈.exe` 即可运行，**无需安装任何环境**

#### 方式二：从源码运行

```bash
mvn javafx:run
```

#### 方式三：构建后运行

```bash
mvn clean package
mvn dependency:copy-dependencies -DoutputDirectory=target/lib
java -cp "target/ji-yi-1.0.0.jar;target/lib/*" com.jiyi.Main
```

---

## 🔧 配置引擎

本软件**不捆绑引擎**，请自行下载开源引擎（推荐 [皮卡鱼 Pikafish](https://github.com/official-pikafish/Pikafish)）。

1. 打开菜单 **引擎管理** → **添加**
2. 在弹出的文件选择窗口中**选择任意目录下的引擎 exe 文件**
3. 设置线程数、哈希等参数（引擎路径完全由你自由选择，无固定路径限制）

UCI（皮卡鱼等）与 UCCI 引擎均支持，程序会自动识别协议。

## 🧠 配置识别模型（连线功能）

- 发布包自带 `models/yolov11.onnx` 模型，开箱即用
- 如需更换模型：**连线设置** → **模型路径** → 点击选择按钮，可**自由选择任意目录下的 `.onnx` 文件**
- 模型路径保存在 `config.json` 中，支持相对路径（`./models/...`）或绝对路径

## 📂 目录说明

```
极弈/
├── 极弈.exe             主程序（双击运行）
├── models/              YOLO 识别模型（可自行更换/选择）
├── assets/              附加资源
├── app/                 程序与依赖库（打包生成，无需改动）
├── runtime/             内置 Java 运行时（无需安装 JDK）
├── autoclick/           自动续盘模板（首次标定后自动生成）
├── logs/                运行日志（程序启动后自动生成）
└── config.json          配置文件（首次运行自动生成）
```

---

## ⚠️ 免责声明

- 本软件**仅供学习交流使用**，请勿用于任何违反法律法规的场景
- 使用连线功能时，请遵守目标游戏平台的用户协议；因使用本软件产生的一切后果（包括但不限于账号封禁）**由使用者自行承担**
- 本软件按 GPL-3.0 协议"无任何担保"条款提供，作者不对使用后果承担任何责任

---

## 📄 开源协议

本项目基于 [GPL-3.0](LICENSE) 协议开源。

**衍生声明**：本项目的部分实现（窗口选择、屏幕截图、光标处理等）参考并改编自 [TCHESS](https://github.com/sojourners/public-Xiangqi)（GPLv3），棋子识别模型与光标素材来自该项目。感谢原作者的贡献。

---

## 🙏 致谢

- **[TCHESS](https://github.com/sojourners/public-Xiangqi)** - 部分实现参考与模型素材来源（GPLv3）
- **[Pikafish](https://github.com/official-pikafish/Pikafish)** - 开源象棋引擎
- **[Ultralytics YOLO](https://github.com/ultralytics/ultralytics)** - 视觉识别模型训练框架
- **JNativeHook** - 全局鼠标钩子
- **JNA / Guice / Jackson / SQLite** - 基础设施

---

## ❤️ 支持作者

本软件完全免费开源。如果它对你有帮助，欢迎请作者喝杯茶 ☕

（自愿打赏，与任何功能无关）

<p align="center">
  <img src="assets/donate-wechat.jpg" width="320" alt="微信赞赏码">
</p>

---

*极弈 - 让分析更专业，让对弈更智能*
