---
name: sync-internal
description: >-
  Automate cherry-picking commits from origin development branches to internal remote
  across multiple modules in the workspace. Use when the user mentions
  "同步内部仓库", "sync internal", "cherry-pick to internal" or wants to sync branches to internal repo.
---

# 同步内部仓库自动化流程

将 origin 开发分支的 commit 通过 cherry-pick 同步到 internal 远端。

## 工作区信息

- 项目根目录 `{ROOT}`：取 `ai-assistant` 工作区文件夹的父目录（例如 `ai-assistant` 路径为 `D:\workspace\ai-assistant`，则 `{ROOT}` = `D:\workspace`）。后续所有 `{ROOT}` 占位符均替换为该实际路径。
- 远端 origin 主分支：`master`
- internal 基准分支：`main`
- internal 分支命名规则：`{origin分支名}_internal`

可选模块列表：从当前工作区中所有以 `module-` 开头的文件夹名自动获取（排除 `ai-assistant`）。

## 步骤 0：记录起始时间

```
powershell -Command "Get-Date -Format 'yyyy-MM-dd HH:mm:ss'"
```

记录该时间作为整个流程的起始时间，供步骤 4 计算总耗时。

## 步骤 1：收集参数

使用 AskQuestion 工具**一次调用、两个 question** 收集参数：

1. **分支名称**：提供两个选项 —— A. `master`、B. `输入分支名（如 feature_xxx）`。用户选择 A 直接使用 master，或选择 B 输入自定义分支名。
2. **选择模块**：将模块列表作为多选选项（`allow_multiple: true`），让用户勾选要处理的模块。

## 步骤 2：预览确认与选择起点

对每个选中的模块，用 Shell 工具执行（可并行 fetch 所有模块）：

```
git -C {ROOT}\{模块名} fetch origin
git -C {ROOT}\{模块名} fetch internal
```

然后对每个模块，**确定 commit 源引用 `{源引用}`**：

1. 先检查本地是否存在 `{分支名}` 分支：
   ```
   git -C {ROOT}\{模块名} rev-parse --verify {分支名}
   ```
2. 如果本地存在，`{源引用}` = `{分支名}`（本地分支，包含未推送的 commit）
3. 如果本地不存在，检查远端 `origin/{分支名}` 是否存在：
   ```
   git -C {ROOT}\{模块名} rev-parse --verify origin/{分支名}
   ```
4. 如果远端存在，`{源引用}` = `origin/{分支名}`
5. 如果本地和远端都不存在，明确告诉用户并跳过该模块

确定源引用后，列出 commit：

```
git -C {ROOT}\{模块名} log --format="%h %ai %an %s" origin/master..{源引用}
```

将所有模块的 commit 列表汇总展示给用户，并标注每个模块使用的源引用类型（本地分支 / 远端分支）。

### 2.1 逐模块选择 cherry-pick 模式

对每个有 commit 的模块，分两步询问：

**第一步：选择 cherry-pick 模式**

使用 AskQuestion 询问该模块的 cherry-pick 模式，选项如下：

- **A. 从头开始 cherry-pick**：从第一个 commit 开始，等同于全量 cherry-pick
- **B. 从某个 commit 开始**：从指定 commit（含）cherry-pick 到最新，适用于之前已 cherry-pick 过部分 commit 的场景
- **C. 指定 commit cherry-pick**：多选若干个 commit，只 cherry-pick 选中的（不要求连续）

提示文案示例："模块 {模块名} 共有 N 个 commit，请选择 cherry-pick 模式："

**第二步：根据模式选择具体 commit**

- 模式 A：无需进一步选择
- 模式 B：使用 AskQuestion 列出所有 commit（单选），用户选择起始 commit
- 模式 C：使用 AskQuestion 列出所有 commit（`allow_multiple: true` 多选），用户勾选要 cherry-pick 的 commit

commit 选项格式：`{short_hash} {datetime} {author} {commit_message}`，按时间倒序排列。

记录每个模块的选择结果（模式 + 对应的 commit hash 列表），供步骤 3 使用。

### 2.2 最终确认

汇总展示每个模块即将 cherry-pick 的计划，用 AskQuestion 确认：
- "以上是各模块的 cherry-pick 计划，确认继续？"

汇总格式示例：
- 模块 xxx：从头开始（N 个 commit）
- 模块 yyy：从 `abc1234 msg` 开始（N 个 commit）
- 模块 zzz：指定 commit（`abc1234 msg`、`def5678 msg`，共 N 个）

## 步骤 3：逐模块执行

对每个确认通过的模块，**按顺序逐个**执行以下 git 操作（不要并行，方便定位冲突）：

### 3.0 检查工作区状态

在操作该模块之前，先检查工作区是否有未提交的修改：

```
git -C {ROOT}\{模块名} status --porcelain --untracked-files=no
```

如果输出不为空（说明有已跟踪文件的未提交修改），使用 AskQuestion 提示用户选择处理方式：

- **A. 暂存修改（stash）**：执行 `git -C {ROOT}\{模块名} stash push -m "auto-stash before internal merge"` 暂存修改，流程结束后提醒用户恢复
- **B. 跳过该模块**：跳过当前模块，继续处理下一个
- **C. 终止流程**：停止整个流程，让用户自行处理

提示文案示例："模块 {模块名} 当前工作区有未提交的修改，直接切换分支可能导致修改丢失或冲突。请选择处理方式："

如果用户选择了 stash，在步骤 4 汇总报告中额外提醒用户哪些模块做了 stash，需要执行 `git stash pop` 恢复。

如果输出为空，直接继续后续步骤。

### 3.1 准备 internal 分支

根据步骤 2 中用户对该模块的选择，分情况处理：

**情况 A：用户选择"从头开始 cherry-pick"**

从 internal 基准分支创建（或重建）本地分支：

```
git -C {ROOT}\{模块名} checkout -b {分支名}_internal internal/{基准分支名}
```

如果分支已存在，提示用户选择：覆盖（删除后重建）、跳过该模块、或回退上一步重新选择 cherry-pick 模式。

**情况 B / C：用户选择"从某个 commit 开始"或"指定 commit cherry-pick"**

此时本地或远端应已有之前 cherry-pick 过的分支。按以下优先级处理：

1. 如果本地已有 `{分支名}_internal` 分支，checkout 后拉取最新代码：
   ```
   git -C {ROOT}\{模块名} checkout {分支名}_internal
   git -C {ROOT}\{模块名} pull internal {分支名}_internal
   ```
2. 如果本地没有但远端 `internal/{分支名}_internal` 存在，从远端检出：
   ```
   git -C {ROOT}\{模块名} checkout -b {分支名}_internal internal/{分支名}_internal
   ```
3. 如果本地和远端都不存在该分支，告知用户"未找到之前的 internal 分支，无法增量/指定 cherry-pick"，并询问是否改为从头开始 cherry-pick。

### 3.2 Cherry-pick

根据步骤 2 中的选择执行不同的 cherry-pick 方式：

**情况 A：从头开始 cherry-pick**

```
git -C {ROOT}\{模块名} cherry-pick origin/master..{源引用}
```

**情况 B：从指定 commit 开始（增量 cherry-pick）**

```
git -C {ROOT}\{模块名} cherry-pick {选定commit_hash}^..{源引用}
```

> 说明：`{选定commit_hash}^..{源引用}` 表示从选定 commit（含）到分支最新 commit 的范围。`{源引用}` 为步骤 2 中确定的本地分支或远端分支引用。

**情况 C：指定 commit cherry-pick**

按用户选中的 commit 的时间正序，逐个执行 cherry-pick：

```
git -C {ROOT}\{模块名} cherry-pick {commit_hash_1} {commit_hash_2} ...
```

> 说明：将用户多选的 commit hash 按原始时间顺序排列，一次性传给 `git cherry-pick`，git 会按给定顺序逐个应用。

**异常处理**：检查命令退出码。如果 cherry-pick 失败（退出码非 0），需要区分两种情况：

**判断方法**：执行 `git -C {ROOT}\{模块名} diff --cached --quiet`
- 退出码 0（暂存区无变更）→ 空 commit，走情况 1
- 退出码 1（暂存区有变更）→ 真实冲突，走情况 2

**情况 1：空 commit（commit 已被 cherry-pick 过）**

自动跳过并继续：

```
git -C {ROOT}\{模块名} cherry-pick --skip
```

告诉用户："模块 {模块名} 跳过了已 cherry-pick 过的空 commit"，记录跳过的 commit 数量供步骤 4 汇总。如果后续仍有 commit 失败，重复判断流程。

**情况 2：真实冲突**

1. 执行 `git -C {ROOT}\{模块名} status` 查看冲突文件列表
2. 告诉用户："模块 {模块名} 在 cherry-pick 过程中遇到冲突，冲突文件如下：{文件列表}。请手动解决冲突后告诉我'继续'。"
3. **停止当前流程**，等待用户回复
4. 用户回复"继续"后，执行 `git -C {ROOT}\{模块名} add .` 然后 `git -C {ROOT}\{模块名} cherry-pick --continue`
5. 如果仍有冲突或空 commit，重复上述判断流程

### 3.3 推送到 internal

```
git -C {ROOT}\{模块名} push internal {分支名}_internal
```

## 步骤 4：汇总报告

所有模块处理完毕后，记录结束时间：

```
powershell -Command "Get-Date -Format 'yyyy-MM-dd HH:mm:ss'"
```

计算总耗时（结束时间 - 起始时间），然后输出汇总表格：

| 模块 | 状态 | cherry-pick 数量 | 跳过（已存在） | 备注 |
|------|------|-----------------|---------------|------|
| module-a | ✅ 成功 | 3 | 0 | 已推送 |
| module-b | ⚠️ 有冲突已解决 | 5 | 2 | 已推送，跳过 2 个已 cherry-pick 的 commit |
| module-c | ❌ 跳过 | 0 | 0 | 分支不存在 |

> ⏱️ 总耗时：X 分 Y 秒（开始 HH:mm:ss → 结束 HH:mm:ss）

如果有模块在步骤 3.0 中执行了 stash，额外输出提醒：

> ⚠️ 以下模块在流程开始前暂存了未提交的修改，请记得恢复：
> - `git -C {ROOT}\{模块名} stash pop`

## 注意事项

- 始终使用 `git -C {目录}` 形式操作，避免 cd 切换目录导致状态混乱
- cherry-pick 用 `origin/master..{源引用}` 范围，`{源引用}` 优先使用本地分支（支持未推送的 commit），本地不存在时回退到 `origin/{分支名}`
- 每个模块处理完后立即报告该模块状态，不要等所有模块都完成
- 冲突时必须停止，绝不能自动解决冲突
