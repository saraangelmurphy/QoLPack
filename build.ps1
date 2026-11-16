[CmdletBinding()]
param(
    [string]$StarsectorPath = $env:STARSECTOR_HOME,
    [string]$JdkPath = $env:JAVA_HOME,
    [switch]$Test,
    [switch]$Package
)
$ErrorActionPreference = 'Stop'
if (-not $StarsectorPath) {
    $StarsectorPath = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'Starsector'
}
if (-not (Test-Path (Join-Path $StarsectorPath 'starsector-core'))) {
    throw 'Set STARSECTOR_HOME or pass -StarsectorPath to a Starsector installation.'
}
$info = Get-Content (Join-Path $PSScriptRoot 'mod_info.json') -Raw | ConvertFrom-Json
$tracking = Get-Content (Join-Path $PSScriptRoot 'pt_qolpack.version') -Raw | ConvertFrom-Json
$trackedVersion = "$($tracking.modVersion.major).$($tracking.modVersion.minor).$($tracking.modVersion.patch)"
if ($info.version -ne $trackedVersion) { throw 'Mod and Version Checker versions differ.' }
$registered = @(Get-Content (Join-Path $PSScriptRoot 'data\config\version\version_files.csv') |
    Select-Object -Skip 1 | Where-Object { $_.Trim() })
if ($registered.Count -ne 1 -or $registered[0].Trim() -ne 'pt_qolpack.version') { throw 'Version Checker registration is incorrect.' }
if ((Get-Content (Join-Path $PSScriptRoot 'changelog.txt') -First 1) -ne "Version $trackedVersion") {
    throw 'Changelog does not begin with the current release version.'
}
if (-not $JdkPath) {
    $portable = Get-ChildItem (Join-Path (Split-Path $PSScriptRoot) 'tools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    $JdkPath = if ($portable) { $portable.FullName } else { Join-Path $StarsectorPath 'jre' }
}
$javac = Join-Path $JdkPath 'bin\javac.exe'
$java = Join-Path $JdkPath 'bin\java.exe'
$jar = Join-Path $JdkPath 'bin\jar.exe'
foreach ($tool in @($javac, $java, $jar)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing JDK tool: $tool" }
}
$core = Join-Path $StarsectorPath 'starsector-core'
$modFolders = Get-ChildItem (Join-Path $StarsectorPath 'mods') -Directory
$dependencies = @((Get-ChildItem $core -Filter '*.jar').FullName)
foreach ($modId in @('lw_lazylib', 'lunalib')) {
    $dependencyFolders = @($modFolders | Where-Object {
        $info = Join-Path $_.FullName 'mod_info.json'
        (Test-Path $info) -and ([regex]::Match((Get-Content $info -Raw), '"id"\s*:\s*"([^"]+)"').Groups[1].Value -eq $modId)
    })
    if ($dependencyFolders.Count -ne 1) { throw "Expected one installed $modId dependency, found $($dependencyFolders.Count)." }
    $dependencies += (Get-ChildItem $dependencyFolders[0].FullName -Recurse -Filter '*.jar').FullName
}
$classpath = $dependencies -join ';'
# A fresh output directory avoids packaging classes deleted since the previous build.
$classes = Join-Path $PSScriptRoot ('build\classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = @((Get-ChildItem (Join-Path $PSScriptRoot 'jars\src') -Recurse -Filter '*.java').FullName)
& $javac --release 17 -encoding UTF-8 -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
if ($Test) {
    $testClasses = Join-Path $classes 'test-classes'
    New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
    $testSources = @((Get-ChildItem (Join-Path $PSScriptRoot 'tests') -Recurse -Filter '*.java').FullName)
    & $javac --release 17 -encoding UTF-8 -cp "$classes;$classpath" -d $testClasses $testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & $java '-Djava.awt.headless=true' -cp "$testClasses;$classes;$classpath" data.plugins.CampaignRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed.' }
}
$jarPath = Join-Path $PSScriptRoot 'jars\QoLPack.jar'
# Package production classes only; test classes stay outside the distributable.
$builtJar = Join-Path $classes 'QoLPack.jar'
& $jar "-J-Djava.io.tmpdir=$classes" --create --file $builtJar -C $classes data/plugins -C $classes data/utils
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Copy-Item -LiteralPath $builtJar -Destination $jarPath -Force
if ($Package) {
    $dist = Join-Path $PSScriptRoot 'dist'
    $stage = Join-Path $dist ('stage-' + [guid]::NewGuid().ToString('N'))
    $mod = Join-Path $stage 'QoLPack'
    New-Item -ItemType Directory -Path (Join-Path $mod 'jars') -Force | Out-Null
    foreach ($item in @('data', 'graphics', 'mod_info.json', 'QoLPack.ini', 'changelog.txt', 'README.md', 'CREDITS.txt', 'pt_qolpack.version')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $item) -Destination $mod -Recurse
    }
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $mod 'jars\QoLPack.jar')
    $zip = Join-Path $dist 'QoLPack.zip'
    Compress-Archive -Path $mod -DestinationPath $zip -Force
    $hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  QoLPack.zip" | Set-Content ($zip + '.sha256') -Encoding ascii
    Write-Host "Package: $zip"
}
Write-Host "Built Java 17 mod: $jarPath"
