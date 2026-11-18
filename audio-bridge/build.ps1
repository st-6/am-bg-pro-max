$ErrorActionPreference = 'Stop'

$project = $PSScriptRoot
$repo = Split-Path $project -Parent
$build = Join-Path $project 'build'

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is not set; a JDK 17 install is required.' }
if (-not $env:ANDROID_HOME) { throw 'ANDROID_HOME is not set; Android SDK with Platform 37 and Build Tools 37.0.0 is required.' }

$jdkHome = $env:JAVA_HOME
$bin = Join-Path $jdkHome 'bin'
$android = Join-Path $env:ANDROID_HOME 'platforms\android-37.0\android.jar'
$bt = Join-Path $env:ANDROID_HOME 'build-tools\37.0.0'
$manifest = Join-Path $project 'AndroidManifest.xml'
$src = Get-ChildItem (Join-Path $project 'src') -Filter *.java -Recurse | ForEach-Object FullName
$outDir = Join-Path $repo 'output'
$out = Join-Path $outDir 'audio-capture-for-am-bg-pro-max-1.0.0.apk'

# Signing material is never committed. Provide it through the environment:
#   AMBG_KEYSTORE      path to the .jks/.keystore
#   AMBG_KEY_ALIAS     key alias
#   AMBG_KEYSTORE_PASS keystore password
#   AMBG_KEY_PASS      key password (optional, defaults to the keystore password)
$ks = $env:AMBG_KEYSTORE
$alias = $env:AMBG_KEY_ALIAS
$ksPass = $env:AMBG_KEYSTORE_PASS
$keyPass = if ($env:AMBG_KEY_PASS) { $env:AMBG_KEY_PASS } else { $ksPass }
if (-not $ks -or -not $alias -or -not $ksPass) {
    throw 'Set AMBG_KEYSTORE, AMBG_KEY_ALIAS and AMBG_KEYSTORE_PASS before building the APK.'
}

New-Item -ItemType Directory -Force -Path "$build\classes", "$build\dex", $outDir | Out-Null
foreach ($file in @('base.apk', 'aligned.apk', 'classes.dex', 'classes.jar')) {
    $target = if ($file -eq 'classes.dex') { Join-Path $build 'dex\classes.dex' } else { Join-Path $build $file }
    if (Test-Path $target) { Remove-Item -LiteralPath $target }
}

& "$bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp $android -d "$build\classes" $src
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& "$bin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

& "$bin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 29 --lib $android --output "$build\dex" "$build\classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

& "$bt\aapt2.exe" link --manifest $manifest -I $android -o "$build\base.apk"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }
& "$bin\jar.exe" uf "$build\base.apk" -C "$build\dex" classes.dex
if ($LASTEXITCODE -ne 0) { throw 'dex insertion failed' }

& "$bt\zipalign.exe" -f 4 "$build\base.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$env:JAVA_HOME = $jdkHome
$env:PATH = "$bin;$env:PATH"
& "$bt\apksigner.bat" sign --ks $ks --ks-key-alias $alias --ks-pass "pass:$ksPass" --key-pass "pass:$keyPass" --out $out "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& "$bt\apksigner.bat" verify --verbose $out
if ($LASTEXITCODE -ne 0) { throw 'APK signature check failed' }
Write-Output $out
