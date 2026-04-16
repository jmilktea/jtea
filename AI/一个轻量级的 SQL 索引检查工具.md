# 一个轻量级的 SQL 索引检查工具

## 背景

慢 SQL 是后端服务的常见隐患。开发阶段数据量小，一条没走索引的查询可能只要几毫秒，看不出任何问题。然而一旦到了生产环境，面对百万甚至千万级数据，同一条 SQL 可能耗时数秒，直接拖垮接口响应甚至引发数据库连接池耗尽。

大多数团队依赖生产数据库的慢 SQL 监控来发现问题，但这属于**事后告警**——SQL 已经在线上跑了、用户已经受影响了，修复成本远高于开发阶段。

**能不能在发版前就自动发现这些潜在的慢 SQL？**

这个想法其实不新鲜，很多团队都想过。但要真正落地，需要处理的细节不少：多仓库扫描、MyBatis 动态 SQL 解析、参数占位替换、EXPLAIN 结果分析……以前纯靠手写，光是 MyBatis XML 中 `<if>`、`<where>`、`<foreach>` 的递归展开就够折腾一阵。投入产出比不高，往往不了了之。

现在有了 AI 辅助编程，情况完全不同。整个工具从方案设计到代码实现，**在 AI 的协助下 2 小时内完成**——我只需要描述需求和约束，AI 负责生成代码、处理边界情况、调试 PowerShell 的各种坑。以前觉得"性价比不够"的工具类需求，现在变得触手可及。

基于这个思路，我们做了一个轻量级工具：给定一个分支名称，自动扫描本次变更中的所有 SQL，连接测试库执行 EXPLAIN，检查是否走索引，有问题的直接告警。

## 实现思路

当前版本基于 **PowerShell + Java** 实现，面向 Windows 开发者开箱即用。整个工具的核心流程分为四步：

**1. 扫描仓库，定位变更**

遍历所有微服务仓库，通过 `git branch -a` 判断哪些仓库存在目标分支，再用 `git diff` 提取相对于基线分支的变更文件，只关注 `*Mapper.xml`、`*Mapper.java`、`*.sql` 三类文件。

**2. 解析 SQL 语句**

针对不同来源采用不同的解析策略：
- **MyBatis XML**：解析 `<select>`、`<update>`、`<delete>` 等标签，展开 `<if>`、`<where>`、`<foreach>` 等动态标签，解析 `<include>` 引用
- **Java 注解**：正则匹配 `@Select`、`@Update` 等注解中的 SQL
- **DDL 文件**：直接展示变更内容

同时对比基线分支的同一文件，只提取**新增或修改**的 SQL 块，排除未变更的部分。

**3. 执行 EXPLAIN 分析**

将 MyBatis 动态参数（`#{param}`）替换为占位值，连接测试数据库执行 `EXPLAIN`。连接层设置为只读模式，且只允许 `EXPLAIN` 开头的语句通过，确保对数据库零副作用。

数据库连接部分使用了一个轻量的 Java 辅助类（`SqlIndexChecker4j.java`）而非 Python 脚本，这也是工具命名为 `sql-index-checker4j`（4j = for Java）的原因。对于 Java 开发者来说，开发机上必然有 JDK，Maven 本地仓库中也已缓存了 MySQL JDBC 驱动，用 Java 连接数据库属于"就地取材"，零额外安装；而 Python 环境和 MySQL 客户端则不一定有，额外安装反而增加了推广成本。如果你的团队是 Python 或 Go 技术栈，同样的思路可以用对应语言替换 `SqlIndexChecker4j` 的实现。

**4. 分析告警**

根据 EXPLAIN 结果判断以下风险指标：

| 指标 | 级别 | 含义 |
|------|------|------|
| `type=ALL` | CRITICAL | 全表扫描 |
| `key=NULL` | CRITICAL | 未使用任何索引 |
| `type=index` 且 rows 较大 | WARNING | 全索引扫描 |
| `Using filesort` | WARNING | 文件排序 |
| `Using temporary` | WARNING | 使用临时表 |

## 使用方法

**推荐在提测前执行一次**，作为开发自检的最后一环。就像跑单元测试一样，花半分钟确认本次变更没有引入慢 SQL，远好过上线后被 DBA 找上门。

```powershell
# 基本用法：指定分支名
powershell -ExecutionPolicy Bypass -File "sql-index-checker4j.ps1" -Branch <分支名>

# 跳过 git fetch，加快速度
powershell -ExecutionPolicy Bypass -File "sql-index-checker4j.ps1" -Branch <分支名> -NoFetch

# 指定对比的基线分支（覆盖配置文件中的默认值）
powershell -ExecutionPolicy Bypass -File "sql-index-checker4j.ps1" -Branch <分支名> -BaseBranch <基线分支>

# 只提取 SQL，不连数据库
powershell -ExecutionPolicy Bypass -File "sql-index-checker4j.ps1" -Branch <分支名> -SkipExplain
```

### 文件清单

| 文件 | 说明 |
|------|------|
| `sql-index-checker4j.ps1` | 主脚本（PowerShell） |
| `SqlIndexChecker4j.java` | Java 辅助类，负责连接数据库执行 EXPLAIN |
| `sql-index-checker4j.json` | 统一配置文件 |

### 关于 macOS / Linux

当前版本基于 PowerShell 编写，**主要面向 Windows 环境**。macOS / Linux 用户如需使用，可以安装 [PowerShell Core](https://github.com/PowerShell/PowerShell)（`brew install powershell`），然后通过 `pwsh` 运行：

```bash
pwsh sql-index-checker4j.ps1 -Branch <分支名>
```

配置文件中的路径需改为对应系统的格式（如 `repo_root` 改为 `/Users/you/risk`，`mysql_jdbc_jar` 改为 `/Users/you/.m2/...`），脚本逻辑本身无需改动。当然，也可以直接将 `.ps1` 脚本交给 AI 转换为原生的 Bash 版本，省去安装 PowerShell Core 的步骤。

### 配置文件

首次使用前，需在脚本目录下创建 `sql-index-checker4j.json` 配置文件。所有环境相关的变量都集中在这一个文件中，**换机器、换项目只需修改此文件即可**，脚本本身无需任何改动：

```json
{
  "repo_root": "D:\\your-project-root",
  "mysql_jdbc_jar": "C:\\Users\\you\\.m2\\repository\\...\\mysql-connector-j-x.x.x.jar",
  "base_branch": "master",
  "fetch_timeout_seconds": 15,
  "repos": [
    "service-a",
    "service-b",
    "service-c"
  ],
  "db_host": "your-db-host",
  "db_port": 3306,
  "db_name": "your_database",
  "db_user": "readonly_user",
  "db_password": "password"
}
```

| 配置项 | 说明 |
|--------|------|
| `repo_root` | 所有微服务仓库的父目录 |
| `mysql_jdbc_jar` | MySQL JDBC 驱动 JAR 路径（Maven 本地仓库中） |
| `base_branch` | 默认对比的基线分支 |
| `fetch_timeout_seconds` | git fetch 超时秒数，防止网络卡住 |
| `repos` | 需要扫描的仓库名称列表 |
| `db_*` | 测试数据库连接信息 |

## 运行效果示例

以下是一次实际检查的输出（已脱敏）：

```
============================================================
  SQL Index Checker4J
============================================================
Branch:     feature_xxx
Base:       master

--- Scanning repositories for branch: feature_xxx ---
Found 1 repo(s) with branch 'feature_xxx':
  - service-order

--- Processing: service-order ---
  Changed files (1):
    src/main/java/.../mapper/OrderMapper.xml
  OrderMapper.xml : 1 changed SQL block(s)

============================================================
  SQL Change Summary
============================================================
Found 1 DML change(s), 0 DDL file(s).

--- DML Changes ---
  [MOD] [service-order] OrderMapper.xml - queryByCondition (select)

--- Running EXPLAIN (1 statement(s)) ---

============================================================
  EXPLAIN Analysis Report
============================================================

[WARNING] [service-order] OrderMapper.xml - queryByCondition (MODIFIED)
  SQL: SELECT ... FROM t_order WHERE status = 1 AND type IN (1)
  EXPLAIN: type=ALL, key=NULL, rows=833226, Extra=Using where
  >> [CRITICAL] Full table scan (type=ALL), rows=833226
  >> [CRITICAL] No index used (key=NULL, possible_keys=NULL)

============================================================
  Summary: 1 analyzed, 0 OK, 1 WARNING, 0 ERROR
============================================================
```

工具成功识别出一条全表扫描的 SQL，预估扫描 83 万行，在发版前就能发现并修复。

## 优势

- **前置发现**：在开发阶段就能检测到潜在慢 SQL，而非等到生产告警
- **零侵入**：不修改任何项目代码，不依赖额外框架，独立于构建流程
- **安全可控**：数据库连接只读模式，只执行 EXPLAIN，对数据零副作用
- **多仓库支持**：自动扫描多个微服务仓库，适配多模块架构
- **增量检查**：只分析本次变更的 SQL，不浪费时间在已有代码上
- **可跨平台**：主要面向 Windows，macOS / Linux 用户可通过安装 PowerShell Core 运行，配置文件改路径即可
- **AI 加速落地**：借助 AI 辅助编程，从想法到可用工具只需要 2 小时，大幅降低了工具类需求的实现门槛
- **易于维护**：脚本本身也是 AI 生成的，后续遇到 bug、不适配或想加功能，直接让 AI 调整即可，不需要自己啃脚本代码

## 未来展望

- **接入企业微信通知**：检查完成后将报告推送到企业微信群，团队成员即时感知
- **集成 CI/CD 流水线**：作为发版前的卡点，SQL 索引检查不通过则阻断发布
- **支持更多 SQL 来源**：覆盖 JPA/Hibernate 的 HQL、MyBatis-Plus 的 QueryWrapper 等场景
- **历史趋势分析**：记录每次检查结果，追踪 SQL 质量变化趋势

## 文件
[sql-index-checker4j.ps1]()    
[sql-index-checker4j.java]()    
