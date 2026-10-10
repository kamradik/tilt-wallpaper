param([string]$Jdk = '', [string]$Sdk = '')
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (!$Jdk) { $Jdk = (Get-ChildItem -LiteralPath (Join-Path $root '.tools/jdk') -Directory | Select-Object -First 1).FullName }
if (!$Sdk) { $Sdk = Join-Path $root '.tools/sdk' }
# Build Tools by Pkg.Revision, not folder name: '9.0.0' sorts above '36.0.0' as text, and some SDK zips unpack as 'android-NN'.
$tools = @(Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory | ForEach-Object {
    $line = Get-Content -LiteralPath (Join-Path $_.FullName 'source.properties') -ErrorAction SilentlyContinue | Where-Object { $_ -match '^Pkg\.Revision=([0-9.]+)' } | Select-Object -First 1
    if ($line -match '=([0-9]+(\.[0-9]+)*)') { [pscustomobject]@{ Path = $_.FullName; Version = [version]($Matches[1] + $(if ($Matches[1] -notmatch '\.') { '.0' })) } }
} | Sort-Object Version -Descending)
if (!$tools) { throw "No Android Build Tools in $Sdk/build-tools" }
$bt = $tools[0].Path
if ($tools[0].Version.Major -lt 36) { Write-Warning "Build Tools $($tools[0].Version) found; 36.0.0 is the verified version" }
# Gradle and this script share one source of SDK and version numbers: app/build.gradle.
$gradle = [IO.File]::ReadAllText((Join-Path $root 'app/build.gradle'))
function GradleValue([string]$name) { if ($gradle -match "$name\s+'?([^';\s}]+)'?") { $Matches[1] } else { throw "app/build.gradle: $name not found" } }
$android = Join-Path $Sdk ('platforms/android-{0}/android.jar' -f (GradleValue 'compileSdk'))
$env:JAVA_HOME = $Jdk
$java = Join-Path $Jdk 'bin/java.exe'
$javac = Join-Path $Jdk 'bin/javac.exe'
function Check { if ($LASTEXITCODE -ne 0) { throw "Build command failed ($LASTEXITCODE)" } }
Push-Location $root
try {
    New-Item -ItemType Directory -Force build/classes,build/generated,build/dex,build/tests,dist,.tools | Out-Null
    & (Join-Path $bt 'aapt2.exe') compile --dir app/src/main/res -o build/resources.zip
    Check
    $manifest = [IO.File]::ReadAllText((Join-Path $root 'app/src/main/AndroidManifest.xml')).Replace('<manifest ', ('<manifest package="{0}" ' -f (GradleValue 'namespace')))
    [IO.File]::WriteAllText((Join-Path $root 'build/AndroidManifest.xml'),$manifest,[Text.UTF8Encoding]::new($false))
    & (Join-Path $bt 'aapt2.exe') link -o build/base.apk -I $android --manifest build/AndroidManifest.xml --java build/generated -A app/src/main/assets --min-sdk-version (GradleValue 'minSdk') --target-sdk-version (GradleValue 'targetSdk') --version-code (GradleValue 'versionCode') --version-name (GradleValue 'versionName') build/resources.zip
    Check
    $sources = @(Get-ChildItem app/src/main/java,build/generated -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Substring($root.Length + 1).Replace('\','/') + '"' })
    [IO.File]::WriteAllLines((Join-Path $root 'build/sources.txt'),$sources,[Text.UTF8Encoding]::new($false))
    & $javac -encoding UTF-8 -source 17 -target 17 -classpath $android -d build/classes '@build/sources.txt'
    Check
    & $javac -encoding UTF-8 -d build/tests app/src/main/java/dev/solotilt/personal/FoldMath.java tests/FoldMathTest.java
    Check
    & $java -cp build/tests FoldMathTest
    Check
    & (Join-Path $Jdk 'bin/jar.exe') --create --file build/classes.jar -C build/classes .
    Check
    & $java -cp (Join-Path $bt 'lib/d8.jar') com.android.tools.r8.D8 --lib $android --min-api (GradleValue 'minSdk') --output build/dex build/classes.jar
    Check
    Copy-Item build/base.apk build/unsigned.apk -Force
    & (Join-Path $Jdk 'bin/jar.exe') --update --file build/unsigned.apk -C build/dex classes.dex
    Check
    & (Join-Path $bt 'zipalign.exe') -f -p 4 build/unsigned.apk build/aligned.apk
    Check
    if (!(Test-Path -LiteralPath '.tools/personal-debug.jks')) {
        & (Join-Path $Jdk 'bin/keytool.exe') -genkeypair -keystore .tools/personal-debug.jks -storepass android -keypass android -alias personal -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Personal Tilt Development'
        Check
    }
    & $java -jar (Join-Path $bt 'lib/apksigner.jar') sign --ks .tools/personal-debug.jks --ks-key-alias personal --ks-pass pass:android --key-pass pass:android --out dist/Tilt-Personal.apk build/aligned.apk
    Check
    & $java -jar (Join-Path $bt 'lib/apksigner.jar') verify --verbose dist/Tilt-Personal.apk
    Check
    Get-FileHash dist/Tilt-Personal.apk -Algorithm SHA256 | Format-List
} finally { Pop-Location }
