param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [Parameter(Mandatory=$true)][ValidateSet('baseline','fixed')][string]$Variant,
    [Parameter(Mandatory=$true)][string]$ControlDirectory,
    [Parameter(Mandatory=$true)][string]$ClusterUrl,
    [int]$Warmups=2,
    [int]$Repeats=10,
    [int]$Files=50000,
    [int]$Directories=500
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $RepoRoot
$mavenArgs=@('-B','-ntp','verify','-pl','integration-tests','-am','-Pdaily,sequential_tests',
    '-DskipUnitTests','-Dit.test=FsCrawlerCheckpointPairedBenchmarkIT',
    "-Dtests.checkpoint.paired.variant=$Variant", "-Dtests.checkpoint.paired.control=$ControlDirectory",
    "-Dtests.checkpoint.paired.warmups=$Warmups", "-Dtests.checkpoint.paired.repeats=$Repeats",
    "-Dtests.checkpoint.paired.files=$Files", "-Dtests.checkpoint.paired.directories=$Directories",
    '-Dtests.seed=3939393939393939', "-Dtests.cluster.url=$ClusterUrl",
    '-Dtests.leaveTemporary=false','-Ddocker.skip=true')
if(-not $env:CHECKPOINT_BENCHMARK_API_KEY){throw 'Set CHECKPOINT_BENCHMARK_API_KEY to a dedicated shared test key'}
$mavenArgs += "-Dtests.cluster.apiKey=$env:CHECKPOINT_BENCHMARK_API_KEY"
& mvn @mavenArgs
exit $LASTEXITCODE
