# 极弈资源文件说明

## 目录结构

```
resources/
├── css/                    # 样式表文件
│   └── app.css            # 主应用样式
├── fxml/                   # JavaFX界面定义文件
│   ├── main.fxml          # 主窗口界面
│   └── engine_manager.fxml # 引擎管理界面
├── images/                 # 图像资源
│   └── README.md          # 图像资源说明
├── sound/                  # 音频资源
│   └── README.md          # 音频资源说明
├── data/                   # 数据文件
│   └── startpos.txt       # 起始局面
├── logback.xml            # 日志配置
├── application.properties  # 应用属性配置
└── config-template.json   # 配置模板文件
```

## 文件说明

### 核心配置文件

1. **logback.xml** - 日志系统配置
   - 配置了控制台、文件、引擎、检测等多个日志输出
   - 支持日志滚动和大小限制
   - 错误日志单独记录

2. **application.properties** - 应用基础属性
   - 应用信息、版本
   - 编码设置
   - 基础配置参数

3. **config-template.json** - 运行时配置模板
   - 窗口大小和UI设置
   - 引擎配置
   - 检测配置
   - 开局库配置

### 界面文件

1. **css/app.css** - 完整的JavaFX样式定义
   - 全局样式、菜单栏、工具栏
   - 按钮、表格、列表等控件样式
   - 棋盘和引擎输出区域样式
   - 对话框和状态栏样式

2. **fxml/main.fxml** - 主窗口布局
   - 菜单栏（文件、局面、引擎、棋谱）
   - 工具栏（快捷操作按钮）
   - 棋盘显示区域
   - 引擎输出区域
   - 库招和棋谱标签页
   - 状态栏

3. **fxml/engine_manager.fxml** - 引擎管理对话框
   - 引擎列表表格
   - 添加、删除、设为默认按钮
   - 状态显示

### 资源文件

1. **images/** - 图标和图像资源目录
   - 应用图标
   - 棋子图像（可选，当前使用Canvas绘制）

2. **sound/** - 音频资源目录
   - move.wav - 移动棋子音效
   - capture.wav - 吃子音效

3. **data/** - 数据文件目录
   - 起始局面定义
   - 其他数据文件

## 日志输出

应用运行时会在项目根目录下创建 `logs/` 文件夹，包含：

- `jiyi.log` - 主日志文件
- `engine.log` - 引擎通信日志
- `detection.log` - 检测模块日志
- `error.log` - 错误日志

## 用户配置

首次运行时，应用会在用户目录下创建配置文件：
- Windows: `%USERPROFILE%/.jiyi/config.json`
- Linux/Mac: `~/.jiyi/config.json`

## 开发说明

- 修改样式：编辑 `css/app.css`
- 修改界面：编辑对应的 `.fxml` 文件
- 修改日志级别：编辑 `logback.xml`
- 添加新的FXML页面：在 `fxml/` 目录下创建新文件

## 资源加载方式

在Java代码中使用以下方式加载资源：

```java
// 加载FXML
getClass().getResource("/fxml/main.fxml")

// 加载CSS
getClass().getResource("/css/app.css")

// 加载图像
getClass().getResource("/images/icon.png")

// 加载音频
getClass().getResource("/sound/move.wav")
```
