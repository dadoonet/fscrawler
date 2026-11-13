param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label,
    [string]$ClusterUrl='http://127.0.0.1:19203',
    [ValidateRange(3,30)][int]$Repeats=5,
    [ValidateSet('all','small-directories','large-directory','wide-directory')][string]$Workload='all',
    [string]$OutputDirectory='target/checkpoint-benchmark'
)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    $out=[IO.Path]::GetFullPath($OutputDirectory)
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $log=Join-Path $out "$Label.log"
    $source=Join-Path $repoRoot 'core/src/main/java/fr/pilato/elasticsearch/crawler/fs/FsParser.java'
    $revision=git rev-parse HEAD
    $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    $started=[DateTime]::UtcNow
    $selected=if($Workload -eq 'all'){'small-directories,large-directory,wide-directory'}else{$Workload}
    $mavenArgs=@('-B','-ntp','verify','-pl','integration-tests','-am','-Pdaily,sequential_tests',
        '-DskipUnitTests','-Dit.test=FsCrawlerCheckpointBenchmarkIT','-Dtests.checkpoint.benchmark=true',
        "-Dtests.checkpoint.repeats=$Repeats", "-Dtests.checkpoint.workloads=$selected",'-Dtests.seed=3939393939393939',
        "-Dtests.cluster.url=$ClusterUrl",'-Dtests.leaveTemporary=false','-Ddocker.skip=true')
    & mvn @mavenArgs *> $log
    if($LASTEXITCODE -ne 0) { throw "Benchmark failed; timings are invalid. See $log" }
    $pattern='(?m)^CHECKPOINT_BENCH workload=(\S+) iteration=(\d+) warmup=(true|false) elapsed_ms=([\d.]+) documents=(\d+)'
    $rows=@([regex]::Matches([IO.File]::ReadAllText($log),$pattern) | ForEach-Object {
        [pscustomobject]@{workload=$_.Groups[1].Value; iteration=[int]$_.Groups[2].Value;
            warmup=($_.Groups[3].Value -eq 'true');
            elapsed_ms=[double]::Parse($_.Groups[4].Value,[Globalization.CultureInfo]::InvariantCulture);
            documents=[int]$_.Groups[5].Value}
    })
    if($rows.Count -ne ($selected -split ",").Count*($Repeats+1)) { throw "Incomplete benchmark samples: $($rows.Count). See $log" }
    $runtimeIssues='Rate limited \(429\)|Server error on .*Retrying|Bulk item failure for|Error while crawling|Failed to save checkpoint'
    if([regex]::IsMatch([IO.File]::ReadAllText($log),$runtimeIssues)){throw "Benchmark encountered processing errors or retries; see $log"}
    $report=[ordered]@{label=$Label; workloads=($selected -split ','); revision=$revision; parserSha256=$hash;
        startedUtc=$started.ToString('o'); completedUtc=[DateTime]::UtcNow.ToString('o');
        repeats=$Repeats; samples=$rows}
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $out "$Label.json")
    $rows | Where-Object {-not $_.warmup} | Format-Table
    Write-Output "Saved verified samples to $(Join-Path $out "$Label.json")"
} finally { Pop-Location }
