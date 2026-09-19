$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot)
try {
    $jdk=(Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
    if (!(Test-Path .tools/json-test.jar)) { Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar' -OutFile .tools/json-test.jar }
    if ((Get-FileHash .tools/json-test.jar).Hash -ne '3EA61B2A06E31EDF1C91134FE9106B0EBB16628BE169F3DB75BC7A2B06B45796') { throw 'Test JSON jar checksum mismatch' }
    & "$jdk/bin/javac.exe" -encoding UTF-8 -cp 'build/classes;.tools/sdk/platforms/android-36/android.jar;.tools/json-test.jar' -d build/tests tests/CoreChecks.java
    if ($LASTEXITCODE) { throw 'Compile failed; run build.ps1 first' }
    & "$jdk/bin/java.exe" -cp 'build/tests;build/classes;.tools/json-test.jar;.tools/sdk/platforms/android-36/android.jar' dev.solotilt.personal.CoreChecks
    if ($LASTEXITCODE) { throw 'Checks failed' }
} finally { Pop-Location }
