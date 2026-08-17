# XLedger

一个本地优先的个人记账 Android 应用，从 Expo/React Native 版（x-ledger）迁移重构而来，使用 **Kotlin + Jetpack Compose + Room** 实现。

## 功能

- 按月查看收支流水，支持支出 / 收入 / 未分类筛选
- 月度支出、收入、结余汇总
- 编辑 / 删除单条交易，编辑时可设置分类或新建分类
- 分类管理，删除分类时自动解除相关交易关联
- 资产账户管理（新增 / 编辑账户和余额）
- 分类统计（按月查看支出 / 收入分类占比）
- 截图导入：多图选择 → 压缩 → LLM 识别 → 勾选确认后入库（自动重复检测）
- 应用内 LLM 配置（Base URL / API Key / Model）

## 技术栈

- Kotlin 1.9 / Jetpack Compose（Material 3）/ Navigation Compose
- Room 2.6（KSP）
- OkHttp + kotlinx.serialization（OpenAI Responses API）
- [icons-lucide-android](https://github.com/Composables/lucide-android) 图标库

## 构建

```bash
./gradlew.bat :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 工程结构

```text
app/src/main/java/com/xdl/xledger/
├── MainActivity.kt            单 Activity 入口
├── XLedgerApplication.kt      全局依赖容器（手动 DI）
├── data/
│   ├── db/                    Room：实体 / DAO / AppDatabase
│   ├── repository/            业务仓储（去重、月份查询、配置 upsert 等）
│   └── remote/                LLM 服务（Responses API + 容错解析）
├── ui/
│   ├── navigation/            路由定义 + 嵌套导航图（Tab + 全屏子页）
│   ├── theme/                 语义色板（Light/Dark）+ Shapes + Typography
│   ├── home/                  账本主页 + 编辑弹窗
│   ├── assets/                资产页
│   ├── categories/            分类管理页
│   ├── settings/              设置页 + LLM 配置页
│   ├── stats/                 分类统计页
│   ├── import/                截图导入页
│   └── common/                共享子页面骨架
└── util/                      时间 / 金额格式化、图片压缩与缩略图加载
```

## 数据模型

| 表 | 说明 |
|---|---|
| `transactions` | 收支记录，`transaction_type` 0=支出 1=收入，`dedupe_key` 唯一去重 |
| `assets` | 资产账户及余额 |
| `categories` | 交易分类 |
| `configs` | 本地键值配置（含 LLM 设置） |

## LLM 配置

在 **设置 → LLM 配置** 中填写 Base URL（默认 `https://api.openai.com/v1`）、API Key 与模型（默认 `gpt-4.1-mini`），截图导入时调用 `{Base URL}/responses` 接口识别交易。

## 说明

- 时间存储为 UTC ISO-8601（`.000Z`），展示按本地时区转换，与旧版行为一致。
- 金额以两位小数字符串存储，去重键为 `occurred_at_amount_transaction_type`。
- 网络访问自建 LLM 服务时允许明文 HTTP（`usesCleartextTraffic`），正式发布可改为 network security config 白名单。
