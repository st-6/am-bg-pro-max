$ErrorActionPreference = 'Stop'
$root = 'F:\AMPP-LyricBackgrounds'
$project = Join-Path $root 'project-419'
$build = Join-Path $project 'build'
$jdk = Join-Path $root 'toolchain\jdk-17.0.21+9\bin'
$android = Join-Path $root 'toolchain\sdk\platforms\android-37.0\android.jar'
$bt = Join-Path $root 'toolchain\sdk\build-tools\37.0.0'
$apiRoot = Join-Path $project 'vendor\am-plus-plus-plugin-api\src\main\java'
$outName = if ($env:OUT_NAME) { $env:OUT_NAME } else { 'am-bg-pro-max-1.0.0.zip' }
New-Item -ItemType Directory -Force -Path "$build\sdk-classes", "$build\classes", "$build\dex", "$build\dist\assets", "$root\output" | Out-Null
if (Test-Path "$build\dex\classes.dex") { Remove-Item -LiteralPath "$build\dex\classes.dex" }
$apiSources = Get-ChildItem $apiRoot -Filter *.java -Recurse | ForEach-Object FullName
& "$jdk\javac.exe" -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options -d "$build\sdk-classes" -cp $android $apiSources
if ($LASTEXITCODE -ne 0) { throw 'plugin API javac failed' }
& "$jdk\jar.exe" cf "$build\ampp-plugin-api.jar" -C "$build\sdk-classes" .
$sources = Get-ChildItem "$project\src\main\java" -Filter *.java -Recurse | ForEach-Object FullName
& "$jdk\javac.exe" -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options -d "$build\classes" -cp "$android;$build\ampp-plugin-api.jar" $sources
if ($LASTEXITCODE -ne 0) { throw 'plugin javac failed' }
& "$jdk\jar.exe" cf "$build\plugin-classes.jar" -C "$build\classes" .
& "$jdk\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib $android --classpath "$build\ampp-plugin-api.jar" --output "$build\dex" "$build\plugin-classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'plugin dex failed' }
& "$jdk\jar.exe" cf "$build\dist\code.jar" -C "$build\dex" classes.dex
Copy-Item -LiteralPath "$project\plugin.json" -Destination "$build\dist\plugin.json" -Force
Copy-Item -LiteralPath "$project\THIRD-PARTY-LICENSES.txt" -Destination "$build\dist\assets\LICENSE.txt" -Force
$out = Join-Path $root "output\$outName"
Push-Location "$build\dist"
try { & "$jdk\jar.exe" cfM $out plugin.json code.jar assets\LICENSE.txt; if ($LASTEXITCODE -ne 0) { throw 'package failed' } }
finally { Pop-Location }
Write-Output $out
