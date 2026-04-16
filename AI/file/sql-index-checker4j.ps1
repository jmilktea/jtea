[CmdletBinding()]
param(
    [Parameter(Mandatory=$true, HelpMessage="Branch name to check")]
    [string]$Branch,

    [Parameter(HelpMessage="Base branch to compare against (overrides config)")]
    [string]$BaseBranch = "",

    [Parameter(HelpMessage="Path to config file")]
    [string]$ConfigPath = "",

    [switch]$NoFetch,
    [switch]$SkipExplain
)

$ErrorActionPreference = "Continue"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }

# ============================================================
# Load Config
# ============================================================

if (-not $ConfigPath) { $ConfigPath = Join-Path $ScriptDir "sql-index-checker4j.json" }
if (-not (Test-Path $ConfigPath)) {
    Write-Host "[ERROR] Config file not found: $ConfigPath" -ForegroundColor Red
    Write-Host "Please create sql-index-checker4j.json with required settings." -ForegroundColor Red
    exit 1
}
$Config = Get-Content $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json

$RepoRoot       = $Config.repo_root
$MysqlJdbcJar   = $Config.mysql_jdbc_jar
$AllowedRepos   = @($Config.repos)
$FetchTimeout   = if ($Config.fetch_timeout_seconds) { $Config.fetch_timeout_seconds } else { 15 }

if (-not $BaseBranch) {
    $BaseBranch = if ($Config.base_branch) { $Config.base_branch } else { "master" }
}

if (-not (Test-Path $RepoRoot)) {
    Write-Host "[ERROR] repo_root does not exist: $RepoRoot" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $MysqlJdbcJar)) {
    Write-Host "[ERROR] mysql_jdbc_jar not found: $MysqlJdbcJar" -ForegroundColor Red
    Write-Host "Please set the correct path in sql-index-checker4j.json" -ForegroundColor Red
    if (-not $SkipExplain) { exit 1 }
}

# ============================================================
# Utility Functions
# ============================================================

function Write-ColorLine {
    param([string]$Text, [string]$Color = "White")
    Write-Host $Text -ForegroundColor $Color
}

function Write-Banner {
    param([string]$Text)
    $line = "=" * 60
    Write-Host ""
    Write-ColorLine $line "Cyan"
    Write-ColorLine "  $Text" "Cyan"
    Write-ColorLine $line "Cyan"
}

function Write-SectionHeader {
    param([string]$Text)
    Write-Host ""
    Write-ColorLine "--- $Text ---" "Yellow"
}

# ============================================================
# Repo Scanning
# ============================================================

function Find-ReposWithBranch {
    param([string]$RootDir, [string]$BranchName, [array]$RepoFilter, [int]$Timeout)

    $repos = @()
    $dirs = Get-ChildItem -Path $RootDir -Directory | Where-Object { $RepoFilter -contains $_.Name }

    foreach ($dir in $dirs) {
        $gitDir = Join-Path $dir.FullName ".git"
        if (-not (Test-Path $gitDir)) { continue }

        Push-Location $dir.FullName
        try {
            if (-not $NoFetch) {
                Write-Host "  Fetching $($dir.Name)..." -NoNewline -ForegroundColor Gray
                $fetchJob = Start-Job -ScriptBlock {
                    param($path)
                    Set-Location $path
                    git fetch origin 2>&1
                } -ArgumentList $dir.FullName
                $finished = Wait-Job $fetchJob -Timeout $Timeout
                if (-not $finished) {
                    Stop-Job $fetchJob
                    Write-Host " timeout (skipped)" -ForegroundColor Yellow
                } else {
                    Write-Host " done" -ForegroundColor Gray
                }
                Remove-Job $fetchJob -Force -ErrorAction SilentlyContinue
            }

            $branchExists = $false
            $remoteBranch = "origin/$BranchName"

            $remoteCheck = git branch -r --list $remoteBranch 2>$null
            if ($remoteCheck) { $branchExists = $true }

            if (-not $branchExists) {
                $localCheck = git branch --list $BranchName 2>$null
                if ($localCheck) { $branchExists = $true }
            }

            if ($branchExists) {
                $repos += @{
                    Name = $dir.Name
                    Path = $dir.FullName
                }
            }
        } finally {
            Pop-Location
        }
    }

    return $repos
}

# ============================================================
# Git Diff & Changed Files
# ============================================================

function Get-ChangedFiles {
    param([string]$RepoPath, [string]$BranchName, [string]$Base)

    Push-Location $RepoPath
    try {
        $remoteBranch = "origin/$BranchName"
        $remoteBase = "origin/$Base"

        $baseRef = $remoteBase
        $branchRef = $remoteBranch

        $baseCheck = git rev-parse --verify $remoteBase 2>$null
        if (-not $baseCheck) { $baseRef = $Base }

        $branchCheck = git rev-parse --verify $remoteBranch 2>$null
        if (-not $branchCheck) { $branchRef = $BranchName }

        $diffOutput = git diff "$baseRef...$branchRef" --name-only --diff-filter=AM 2>$null
        if (-not $diffOutput) { return @() }

        $files = $diffOutput | Where-Object {
            $_ -match '\.(xml|java|sql)$'
        }

        return @($files)
    } finally {
        Pop-Location
    }
}

function Get-FileFromBranch {
    param([string]$RepoPath, [string]$Ref, [string]$FilePath)

    Push-Location $RepoPath
    try {
        $remoteRef = "origin/$Ref"
        $refToUse = $remoteRef
        $check = git rev-parse --verify $remoteRef 2>$null
        if (-not $check) { $refToUse = $Ref }

        $content = git show "${refToUse}:${FilePath}" 2>$null
        if ($LASTEXITCODE -ne 0) { return $null }
        return ($content -join "`n")
    } finally {
        Pop-Location
    }
}

# ============================================================
# MyBatis XML Parsing
# ============================================================

function Expand-XmlNode {
    param(
        [System.Xml.XmlNode]$Node,
        [hashtable]$SqlFragments
    )

    $result = ""

    foreach ($child in $Node.ChildNodes) {
        switch ($child.NodeType) {
            ([System.Xml.XmlNodeType]::Text) {
                $result += $child.Value
            }
            ([System.Xml.XmlNodeType]::CDATA) {
                $result += $child.Value
            }
            ([System.Xml.XmlNodeType]::Element) {
                switch ($child.LocalName) {
                    "include" {
                        $refid = $child.GetAttribute("refid")
                        if ($SqlFragments.ContainsKey($refid)) {
                            $result += Expand-XmlNode -Node $SqlFragments[$refid] -SqlFragments $SqlFragments
                        }
                    }
                    "if" {
                        $result += Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                    }
                    "when" {
                        $result += Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                    }
                    "otherwise" {
                        $result += Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                    }
                    "where" {
                        $innerSql = Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                        $innerSql = $innerSql -replace '(?i)^\s*(AND|OR)\s+', ''
                        if ($innerSql.Trim()) {
                            $result += " WHERE $innerSql"
                        }
                    }
                    "set" {
                        $innerSql = Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                        $innerSql = $innerSql.TrimEnd().TrimEnd(',')
                        if ($innerSql.Trim()) {
                            $result += " SET $innerSql"
                        }
                    }
                    "trim" {
                        $prefix = $child.GetAttribute("prefix")
                        $suffix = $child.GetAttribute("suffix")
                        $prefixOverrides = $child.GetAttribute("prefixOverrides")
                        $suffixOverrides = $child.GetAttribute("suffixOverrides")
                        $innerSql = Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                        if ($prefixOverrides -and $innerSql.Trim()) {
                            $overrides = $prefixOverrides -split '\|'
                            foreach ($o in $overrides) {
                                $o = $o.Trim()
                                if ($o -and $innerSql -match "(?i)^\s*$o") {
                                    $innerSql = $innerSql -replace "(?i)^\s*$o\s*", ""
                                    break
                                }
                            }
                        }
                        if ($suffixOverrides -and $innerSql.Trim()) {
                            $overrides = $suffixOverrides -split '\|'
                            foreach ($o in $overrides) {
                                $o = $o.Trim()
                                if ($o -and $innerSql -match "(?i)\s*${o}\s*$") {
                                    $innerSql = $innerSql -replace "(?i)\s*${o}\s*$", ""
                                    break
                                }
                            }
                        }
                        if ($innerSql.Trim()) {
                            $result += " $prefix $innerSql $suffix "
                        }
                    }
                    "foreach" {
                        $open = $child.GetAttribute("open")
                        $close = $child.GetAttribute("close")
                        $innerSql = Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                        $result += " ${open}${innerSql}${close} "
                    }
                    "choose" {
                        $firstWhen = $child.SelectSingleNode("when")
                        if ($firstWhen) {
                            $result += Expand-XmlNode -Node $firstWhen -SqlFragments $SqlFragments
                        } else {
                            $otherwiseNode = $child.SelectSingleNode("otherwise")
                            if ($otherwiseNode) {
                                $result += Expand-XmlNode -Node $otherwiseNode -SqlFragments $SqlFragments
                            }
                        }
                    }
                    "bind" { }
                    "selectKey" { }
                    default {
                        $result += Expand-XmlNode -Node $child -SqlFragments $SqlFragments
                    }
                }
            }
        }
    }

    return $result
}

function Parse-MapperXml {
    param([string]$XmlContent)

    $XmlContent = $XmlContent -replace '<!DOCTYPE[^>]*>', ''

    $xml = New-Object System.Xml.XmlDocument
    $xml.PreserveWhitespace = $false

    try {
        $settings = New-Object System.Xml.XmlReaderSettings
        $settings.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
        $settings.XmlResolver = $null
        $reader = [System.Xml.XmlReader]::Create(
            (New-Object System.IO.StringReader($XmlContent)),
            $settings
        )
        $xml.Load($reader)
        $reader.Close()
    } catch {
        Write-Warning "  XML parse failed: $_"
        return @()
    }

    $sqlFragments = @{}
    $sqlNodes = $xml.SelectNodes("//sql")
    if ($sqlNodes) {
        foreach ($sqlNode in $sqlNodes) {
            $id = $sqlNode.GetAttribute("id")
            if ($id) { $sqlFragments[$id] = $sqlNode }
        }
    }

    $blocks = @()
    foreach ($tag in @("select", "update", "delete", "insert")) {
        $nodes = $xml.SelectNodes("//$tag")
        if (-not $nodes) { continue }
        foreach ($node in $nodes) {
            $id = $node.GetAttribute("id")
            $sqlText = Expand-XmlNode -Node $node -SqlFragments $sqlFragments
            $sqlText = $sqlText -replace '\s+', ' '
            $sqlText = $sqlText.Trim()
            if ($sqlText) {
                $blocks += @{
                    Id   = $id
                    Type = $tag
                    Sql  = $sqlText
                }
            }
        }
    }

    return $blocks
}

# ============================================================
# Java Annotation Parsing
# ============================================================

function Parse-JavaAnnotations {
    param([string]$JavaContent)

    $blocks = @()
    $patterns = @(
        @{ Tag = "select"; Pattern = '@Select\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)' },
        @{ Tag = "select"; Pattern = '@Select\s*\(\s*\{?\s*"((?:[^"\\]|\\.)*(?:"\s*\+\s*"(?:[^"\\]|\\.)*)*)"\s*\}?\s*\)' },
        @{ Tag = "update"; Pattern = '@Update\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)' },
        @{ Tag = "delete"; Pattern = '@Delete\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)' },
        @{ Tag = "insert"; Pattern = '@Insert\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)' }
    )

    foreach ($p in $patterns) {
        $matches = [regex]::Matches($JavaContent, $p.Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
        foreach ($m in $matches) {
            $sql = $m.Groups[1].Value
            $sql = $sql -replace '"\s*\+\s*"', ''
            $sql = $sql -replace '\\n', ' '
            $sql = $sql -replace '\\t', ' '
            $sql = $sql -replace '\\"', '"'
            $sql = $sql -replace '\s+', ' '
            $sql = $sql.Trim()

            $lineNum = ($JavaContent.Substring(0, $m.Index) -split "`n").Count
            if ($sql) {
                $blocks += @{
                    Id   = "annotation_line_$lineNum"
                    Type = $p.Tag
                    Sql  = $sql
                }
            }
        }
    }

    return $blocks
}

# ============================================================
# SQL Cleanup for EXPLAIN
# ============================================================

function Clean-SqlForExplain {
    param([string]$Sql)

    $Sql = $Sql -replace '#\{[^}]*\}', '1'
    $Sql = $Sql -replace '\$\{[^}]*\}', '1'
    $Sql = $Sql -replace '\s+', ' '
    $Sql = $Sql.Trim()
    $Sql = $Sql.TrimEnd(';')

    return $Sql
}

function Is-DmlForExplain {
    param([string]$Sql)
    $upper = $Sql.TrimStart().ToUpper()
    return ($upper.StartsWith("SELECT") -or $upper.StartsWith("UPDATE") -or $upper.StartsWith("DELETE"))
}

# ============================================================
# Compare SQL Blocks (branch vs base)
# ============================================================

function Compare-SqlBlocks {
    param(
        [array]$BranchBlocks,
        [array]$BaseBlocks
    )

    $baseMap = @{}
    foreach ($b in $BaseBlocks) {
        $baseMap[$b.Id] = $b.Sql
    }

    $changed = @()
    foreach ($b in $BranchBlocks) {
        if (-not $baseMap.ContainsKey($b.Id)) {
            $b["ChangeType"] = "NEW"
            $changed += $b
        } elseif ($baseMap[$b.Id] -ne $b.Sql) {
            $b["ChangeType"] = "MODIFIED"
            $changed += $b
        }
    }

    return $changed
}

# ============================================================
# EXPLAIN Execution via Java
# ============================================================

function Compile-SqlIndexChecker4j {
    $javaFile = Join-Path $ScriptDir "SqlIndexChecker4j.java"
    $classFile = Join-Path $ScriptDir "SqlIndexChecker4j.class"

    if (-not (Test-Path $javaFile)) {
        Write-ColorLine "  [ERROR] SqlIndexChecker4j.java not found at: $javaFile" "Red"
        return $false
    }

    if ((Test-Path $classFile) -and ((Get-Item $classFile).LastWriteTime -ge (Get-Item $javaFile).LastWriteTime)) {
        return $true
    }

    Write-ColorLine "  Compiling SqlIndexChecker4j.java..." "Gray"
    $result = javac -cp "$MysqlJdbcJar" -encoding UTF-8 $javaFile 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-ColorLine "  [ERROR] Failed to compile SqlIndexChecker4j.java: $result" "Red"
        return $false
    }
    return $true
}

function Invoke-ExplainBatch {
    param(
        [array]$SqlStatements,
        [object]$DbConfig
    )

    $tempFile = Join-Path $env:TEMP "sql_explain_input_$(Get-Random).sql"
    $sqlContent = ($SqlStatements -join "`n---SQL_SEPARATOR---`n")
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tempFile, $sqlContent, $utf8NoBom)

    $jdbcUrl = "jdbc:mysql://$($DbConfig.db_host):$($DbConfig.db_port)/$($DbConfig.db_name)?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000&socketTimeout=30000"
    $classpath = "$MysqlJdbcJar;$ScriptDir"

    $output = java -cp $classpath SqlIndexChecker4j $jdbcUrl $DbConfig.db_user $DbConfig.db_password $tempFile 2>&1
    Remove-Item $tempFile -Force -ErrorAction SilentlyContinue

    return ($output -join "`n")
}

function Parse-ExplainOutput {
    param([string]$Output)

    $results = @()
    $blocks = $Output -split '===EXPLAIN_START==='

    foreach ($block in $blocks) {
        if (-not $block.Trim()) { continue }
        $block = $block -replace '===EXPLAIN_END===.*', ''

        $result = @{
            Sql     = ""
            Status  = ""
            Error   = ""
            Rows    = @()
            Headers = @()
        }

        $lines = $block -split "`n"
        foreach ($line in $lines) {
            $line = $line.Trim()
            if ($line.StartsWith("SQL:")) {
                $result.Sql = $line.Substring(4)
            } elseif ($line.StartsWith("HEADER:")) {
                $result.Headers = $line.Substring(7) -split "`t"
            } elseif ($line.StartsWith("ROW:")) {
                $result.Rows += ,($line.Substring(4) -split "`t")
            } elseif ($line.StartsWith("STATUS:OK")) {
                $result.Status = "OK"
            } elseif ($line.StartsWith("STATUS:ERROR:")) {
                $result.Status = "ERROR"
                $result.Error = $line.Substring(13)
            }
        }

        if ($result.Sql) {
            $results += $result
        }
    }

    return $results
}

# ============================================================
# EXPLAIN Result Analysis
# ============================================================

function Analyze-ExplainResult {
    param([hashtable]$ExplainResult)

    $issues = @()
    $typeIdx = -1
    $keyIdx = -1
    $rowsIdx = -1
    $extraIdx = -1
    $possibleKeysIdx = -1

    for ($i = 0; $i -lt $ExplainResult.Headers.Count; $i++) {
        switch ($ExplainResult.Headers[$i].ToLower()) {
            "type"          { $typeIdx = $i }
            "key"           { $keyIdx = $i }
            "rows"          { $rowsIdx = $i }
            "extra"         { $extraIdx = $i }
            "possible_keys" { $possibleKeysIdx = $i }
        }
    }

    foreach ($row in $ExplainResult.Rows) {
        $type = if ($typeIdx -ge 0 -and $typeIdx -lt $row.Count) { $row[$typeIdx] } else { "" }
        $key = if ($keyIdx -ge 0 -and $keyIdx -lt $row.Count) { $row[$keyIdx] } else { "" }
        $rows = if ($rowsIdx -ge 0 -and $rowsIdx -lt $row.Count) { $row[$rowsIdx] } else { "0" }
        $extra = if ($extraIdx -ge 0 -and $extraIdx -lt $row.Count) { $row[$extraIdx] } else { "" }
        $possibleKeys = if ($possibleKeysIdx -ge 0 -and $possibleKeysIdx -lt $row.Count) { $row[$possibleKeysIdx] } else { "" }

        if ($type -eq "ALL") {
            $issues += "[CRITICAL] Full table scan (type=ALL), key=$key, rows=$rows"
        }
        if ($key -eq "NULL" -and $possibleKeys -eq "NULL") {
            $issues += "[CRITICAL] No index used (key=NULL, possible_keys=NULL), rows=$rows"
        }
        if ($type -eq "index" -and [int]$rows -gt 1000) {
            $issues += "[WARNING] Full index scan (type=index), rows=$rows"
        }
        if ($extra -match "Using filesort") {
            $issues += "[WARNING] Using filesort detected, rows=$rows"
        }
        if ($extra -match "Using temporary") {
            $issues += "[WARNING] Using temporary table detected, rows=$rows"
        }
    }

    return $issues
}

# ============================================================
# Main Flow
# ============================================================

Write-Banner "SQL Index Checker4J"
Write-ColorLine "Branch:     $Branch" "White"
Write-ColorLine "Base:       $BaseBranch" "White"
Write-ColorLine "Repo root:  $RepoRoot" "White"
Write-ColorLine "Config:     $ConfigPath" "Gray"
Write-Host ""

# Step 1: DB config
$dbConfig = $null
if (-not $SkipExplain) {
    $dbConfig = $Config
    Write-ColorLine "DB: $($dbConfig.db_host):$($dbConfig.db_port)/$($dbConfig.db_name)" "Gray"
}

# Step 2: Scan repos
Write-SectionHeader "Scanning repositories for branch: $Branch"
$repos = @(Find-ReposWithBranch -RootDir $RepoRoot -BranchName $Branch -RepoFilter $AllowedRepos -Timeout $FetchTimeout)

if ($repos.Count -eq 0) {
    Write-ColorLine "No repository found with branch '$Branch'." "Yellow"
    exit 0
}

Write-ColorLine "Found $($repos.Count) repo(s) with branch '$Branch':" "Green"
foreach ($r in $repos) {
    Write-ColorLine "  - $($r.Name)" "Green"
}

# Step 3: Extract changed SQL
$allChangedSql = @()
$ddlChanges = @()

foreach ($repo in $repos) {
    Write-SectionHeader "Processing: $($repo.Name)"

    $changedFiles = Get-ChangedFiles -RepoPath $repo.Path -BranchName $Branch -Base $BaseBranch

    if ($changedFiles.Count -eq 0) {
        Write-ColorLine "  No changed XML/Java/SQL files." "Gray"
        continue
    }

    Write-ColorLine "  Changed files ($($changedFiles.Count)):" "White"
    foreach ($f in $changedFiles) {
        Write-ColorLine "    $f" "Gray"
    }

    foreach ($file in $changedFiles) {
        $ext = [System.IO.Path]::GetExtension($file).ToLower()
        $fileName = [System.IO.Path]::GetFileName($file)

        if ($ext -eq ".xml" -and $file -match "[Mm]apper\.xml$") {
            $branchContent = Get-FileFromBranch -RepoPath $repo.Path -Ref $Branch -FilePath $file
            $baseContent = Get-FileFromBranch -RepoPath $repo.Path -Ref $BaseBranch -FilePath $file

            if (-not $branchContent) {
                Write-ColorLine "  [SKIP] Cannot read $fileName from branch" "Yellow"
                continue
            }

            $branchBlocks = @(Parse-MapperXml -XmlContent $branchContent)
            $baseBlocks = @()
            if ($baseContent) {
                $baseBlocks = @(Parse-MapperXml -XmlContent $baseContent)
            }

            $changed = @(Compare-SqlBlocks -BranchBlocks $branchBlocks -BaseBlocks $baseBlocks)

            foreach ($c in $changed) {
                $c["File"] = $fileName
                $c["Repo"] = $repo.Name
                $c["FullPath"] = $file
                $allChangedSql += $c
            }

            if ($changed.Count -gt 0) {
                Write-ColorLine "  $fileName : $($changed.Count) changed SQL block(s)" "White"
            }
        }
        elseif ($ext -eq ".java" -and $file -match "[Mm]apper\.java$") {
            $branchContent = Get-FileFromBranch -RepoPath $repo.Path -Ref $Branch -FilePath $file
            $baseContent = Get-FileFromBranch -RepoPath $repo.Path -Ref $BaseBranch -FilePath $file

            if (-not $branchContent) { continue }

            $branchBlocks = @(Parse-JavaAnnotations -JavaContent $branchContent)
            $baseBlocks = @()
            if ($baseContent) {
                $baseBlocks = @(Parse-JavaAnnotations -JavaContent $baseContent)
            }

            $changed = @(Compare-SqlBlocks -BranchBlocks $branchBlocks -BaseBlocks $baseBlocks)

            foreach ($c in $changed) {
                $c["File"] = $fileName
                $c["Repo"] = $repo.Name
                $c["FullPath"] = $file
                $allChangedSql += $c
            }

            if ($changed.Count -gt 0) {
                Write-ColorLine "  $fileName : $($changed.Count) changed SQL annotation(s)" "White"
            }
        }
        elseif ($ext -eq ".sql") {
            $branchContent = Get-FileFromBranch -RepoPath $repo.Path -Ref $Branch -FilePath $file
            if ($branchContent) {
                $ddlChanges += @{
                    File = $fileName
                    Repo = $repo.Name
                    Content = $branchContent
                }
                Write-ColorLine "  $fileName : DDL change detected" "White"
            }
        }
    }
}

# Step 4: Report
Write-Banner "SQL Change Summary"

if ($allChangedSql.Count -eq 0 -and $ddlChanges.Count -eq 0) {
    Write-ColorLine "No SQL changes detected in branch '$Branch' vs '$BaseBranch'." "Green"
    exit 0
}

Write-ColorLine "Found $($allChangedSql.Count) DML change(s), $($ddlChanges.Count) DDL file(s)." "White"
Write-Host ""

if ($ddlChanges.Count -gt 0) {
    Write-SectionHeader "DDL Changes"
    foreach ($ddl in $ddlChanges) {
        Write-ColorLine "  [$($ddl.Repo)] $($ddl.File)" "Magenta"
    }
}

if ($allChangedSql.Count -gt 0) {
    Write-SectionHeader "DML Changes"
    foreach ($s in $allChangedSql) {
        $changeLabel = if ($s.ChangeType -eq "NEW") { "NEW" } else { "MOD" }
        Write-ColorLine "  [$changeLabel] [$($s.Repo)] $($s.File) - $($s.Id) ($($s.Type))" "White"
        $shortSql = $s.Sql
        if ($shortSql.Length -gt 120) { $shortSql = $shortSql.Substring(0, 120) + "..." }
        Write-ColorLine "        $shortSql" "Gray"
    }
}

# Step 5: EXPLAIN
if ($SkipExplain) {
    Write-Host ""
    Write-ColorLine "EXPLAIN skipped (use without -SkipExplain to run index analysis)." "Yellow"
    exit 0
}

$dmlForExplain = @()
$skippedSql = @()

foreach ($s in $allChangedSql) {
    $cleanSql = Clean-SqlForExplain -Sql $s.Sql
    if (Is-DmlForExplain -Sql $cleanSql) {
        $dmlForExplain += @{
            OriginalInfo = $s
            CleanSql     = $cleanSql
        }
    } else {
        $skippedSql += $s
    }
}

if ($dmlForExplain.Count -eq 0) {
    Write-Host ""
    Write-ColorLine "No SELECT/UPDATE/DELETE statements to EXPLAIN (only INSERT changes)." "Yellow"
    exit 0
}

$skippedSql = @($skippedSql)
Write-SectionHeader "Running EXPLAIN ($($dmlForExplain.Count) statement(s))"

$compiled = Compile-SqlIndexChecker4j
if (-not $compiled) {
    Write-ColorLine "[ERROR] Cannot compile Java helper. Aborting EXPLAIN." "Red"
    exit 1
}

$dmlForExplain = @($dmlForExplain)
$sqlList = @($dmlForExplain | ForEach-Object { $_.CleanSql })
$explainOutput = Invoke-ExplainBatch -SqlStatements $sqlList -DbConfig $dbConfig
$explainResults = @(Parse-ExplainOutput -Output $explainOutput)

# Step 6: Analyze & Final Report
Write-Banner "EXPLAIN Analysis Report"

$warningCount = 0
$okCount = 0
$errorCount = 0

for ($i = 0; $i -lt $explainResults.Count; $i++) {
    $er = $explainResults[$i]
    $info = if ($i -lt $dmlForExplain.Count) { $dmlForExplain[$i].OriginalInfo } else { @{} }
    $repoName = if ($info.Repo) { $info.Repo } else { "?" }
    $fileName = if ($info.File) { $info.File } else { "?" }
    $blockId = if ($info.Id) { $info.Id } else { "?" }
    $changeType = if ($info.ChangeType) { $info.ChangeType } else { "?" }

    Write-Host ""
    if ($er.Status -eq "ERROR") {
        $errorCount++
        Write-ColorLine "[ERROR] [$repoName] $fileName - $blockId ($changeType)" "Red"
        Write-ColorLine "  SQL: $($er.Sql)" "Gray"
        Write-ColorLine "  Error: $($er.Error)" "Red"
        Write-ColorLine "  >> Manual review required" "Yellow"
        continue
    }

    $issues = Analyze-ExplainResult -ExplainResult $er

    if ($issues.Count -gt 0) {
        $warningCount++
        Write-ColorLine "[WARNING] [$repoName] $fileName - $blockId ($changeType)" "Red"
        Write-ColorLine "  SQL: $($er.Sql)" "Gray"

        foreach ($row in $er.Rows) {
            $typeIdx = [Array]::IndexOf($er.Headers, "type")
            $keyIdx = [Array]::IndexOf($er.Headers, "key")
            $rowsIdx = [Array]::IndexOf($er.Headers, "rows")
            $extraIdx = [Array]::IndexOf($er.Headers, "Extra")

            $t = if ($typeIdx -ge 0) { $row[$typeIdx] } else { "-" }
            $k = if ($keyIdx -ge 0) { $row[$keyIdx] } else { "-" }
            $r = if ($rowsIdx -ge 0) { $row[$rowsIdx] } else { "-" }
            $e = if ($extraIdx -ge 0) { $row[$extraIdx] } else { "-" }
            Write-ColorLine "  EXPLAIN: type=$t, key=$k, rows=$r, Extra=$e" "Yellow"
        }

        foreach ($issue in $issues) {
            Write-ColorLine "  >> $issue" "Red"
        }
    } else {
        $okCount++
        Write-ColorLine "[OK] [$repoName] $fileName - $blockId ($changeType)" "Green"

        foreach ($row in $er.Rows) {
            $typeIdx = [Array]::IndexOf($er.Headers, "type")
            $keyIdx = [Array]::IndexOf($er.Headers, "key")
            $rowsIdx = [Array]::IndexOf($er.Headers, "rows")

            $t = if ($typeIdx -ge 0) { $row[$typeIdx] } else { "-" }
            $k = if ($keyIdx -ge 0) { $row[$keyIdx] } else { "-" }
            $r = if ($rowsIdx -ge 0) { $row[$rowsIdx] } else { "-" }
            Write-ColorLine "  EXPLAIN: type=$t, key=$k, rows=$r" "Gray"
        }
    }
}

# Summary
Write-Host ""
Write-ColorLine ("=" * 60) "Cyan"
Write-ColorLine "  Summary: $($explainResults.Count) analyzed, $okCount OK, $warningCount WARNING, $errorCount ERROR" "Cyan"
if ($skippedSql.Count -gt 0) {
    Write-ColorLine "  Skipped: $($skippedSql.Count) INSERT statement(s)" "Gray"
}
Write-ColorLine ("=" * 60) "Cyan"
Write-Host ""

if ($warningCount -gt 0) {
    Write-ColorLine "!! $warningCount SQL statement(s) may not use indexes properly. Please review above." "Red"
}
if ($errorCount -gt 0) {
    Write-ColorLine "!! $errorCount SQL statement(s) failed EXPLAIN (likely dynamic table/param issues). Manual review required." "Yellow"
}
