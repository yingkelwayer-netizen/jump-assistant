param([switch]$TestOnly,[string]$ApiOrigin,[string]$PublicKeyPath,[switch]$CompileCheck,[switch]$Diagnostics,[string]$VersionName='1.3.3',[int]$VersionCode=10)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$jdkRoot = (Get-ChildItem -LiteralPath "$projectRoot\tools\jdk" -Directory | Select-Object -First 1).FullName
$buildTools = "$projectRoot\tools\build-tools\android-15"
$androidJar = "$projectRoot\tools\platform\android-35\android.jar"
if (!(Test-Path -LiteralPath "$jdkRoot\bin\javac.exe") -or !(Test-Path -LiteralPath $androidJar)) { throw 'Read tools/README and install the portable toolchain first.' }
$env:JAVA_HOME = $jdkRoot
$env:PATH = "$jdkRoot\bin;$env:PATH"
if ($VersionName -notmatch '^\d+\.\d+\.\d+$' -or $VersionCode -lt 3) { throw 'Invalid version.' }
$outDir = "$projectRoot\build\v$VersionName" + $(if($Diagnostics){'-diagnostics'}else{''})
if (Test-Path -LiteralPath $outDir) {
    $resolvedBuild=[IO.Path]::GetFullPath($outDir)
    $allowedBuild=[IO.Path]::GetFullPath("$projectRoot\build")+[IO.Path]::DirectorySeparatorChar
    if (!$resolvedBuild.StartsWith($allowedBuild,[StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup escaped workspace.' }
    Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
}
New-Item -ItemType Directory -Path "$outDir\classes","$outDir\generated","$outDir\dex","$outDir\tests","$projectRoot\dist" -Force | Out-Null
function Check-Exit([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed ($LASTEXITCODE)" } }
& "$jdkRoot\bin\javac.exe" -encoding UTF-8 -d "$outDir\tests" "$projectRoot\app\src\local\jump\offline\Vision.java" "$projectRoot\app\src\local\jump\offline\LandingTracker.java" "$projectRoot\tests\VisionTest.java" "$projectRoot\tests\LandingTrackerTest.java" "$projectRoot\app\src\local\jump\offline\TokenCrypto.java" "$projectRoot\app\src\local\jump\offline\LicenseClock.java" "$projectRoot\app\src\local\jump\offline\LicenseTerms.java" "$projectRoot\tests\LicenseSecurityTest.java"
Check-Exit 'Vision test compile'
if (Test-Path -LiteralPath "$projectRoot\tests\fixtures\game-136.png") {
& "$jdkRoot\bin\java.exe" -cp "$outDir\tests" VisionTest "$projectRoot\tests\fixtures"
Check-Exit 'Vision tests'
& "$jdkRoot\bin\java.exe" -cp "$outDir\tests" LandingTrackerTest "$projectRoot\tests\landing-fixtures"
Check-Exit 'Landing feedback tests'
} else { Write-Warning 'Real-device image fixtures are excluded from the public source. Vision and Landing image tests skipped; provide your own fixtures to run them.' }
& "$jdkRoot\bin\java.exe" -cp "$outDir\tests" local.jump.offline.LicenseSecurityTest
Check-Exit 'License signature and clock tests'
if ($TestOnly) { exit 0 }
if ($CompileCheck) { $ApiOrigin='https://unconfigured.invalid'; $publicKey='' } else {
    if (!$ApiOrigin -or !$PublicKeyPath) { throw 'Production builds require -ApiOrigin and -PublicKeyPath.' }
    $originUri = [Uri]$ApiOrigin
    if ($originUri.Scheme -ne 'https' -or $originUri.AbsolutePath -ne '/' -or $originUri.Query -or $originUri.Fragment -or $originUri.UserInfo) { throw 'ApiOrigin must be an HTTPS origin.' }
    $ApiOrigin=$originUri.GetLeftPart([UriPartial]::Authority)
    $publicKey=([IO.File]::ReadAllText($PublicKeyPath) -replace '-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s','')
    if ($publicKey -notmatch '^[A-Za-z0-9+/=]+$') { throw 'Invalid public key' }
}
$configDir="$outDir\generated\local\jump\offline"
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
[IO.File]::WriteAllText("$configDir\NetworkConfig.java",('package local.jump.offline; final class NetworkConfig { static final String ORIGIN="'+$ApiOrigin+'"; static final String PUBLIC_KEY="'+$publicKey+'"; static final String VERSION="'+$VersionName+'"; static final long VERSION_CODE='+$VersionCode+'L; static final boolean DIAGNOSTICS='+$Diagnostics.ToString().ToLower()+'; }'))
$manifestSource=[IO.File]::ReadAllText("$projectRoot\app\AndroidManifest.xml") -replace 'android:versionCode="\d+"',('android:versionCode="'+$VersionCode+'"') -replace 'android:versionName="[^"]+"',('android:versionName="'+$VersionName+'"')
[IO.File]::WriteAllText("$outDir\AndroidManifest.xml",$manifestSource)
& "$buildTools\aapt2.exe" compile --dir "$projectRoot\app\res" -o "$outDir\resources.zip"
Check-Exit 'Resource compile'
& "$buildTools\aapt2.exe" link -o "$outDir\unsigned.apk" -I $androidJar --manifest "$outDir\AndroidManifest.xml" --java "$outDir\generated" --auto-add-overlay "$outDir\resources.zip"
Check-Exit 'Resource link'
$javaSources = @((Get-ChildItem -LiteralPath "$projectRoot\app\src","$outDir\generated" -Recurse -Filter '*.java').FullName)
& "$jdkRoot\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d "$outDir\classes" @javaSources
Check-Exit 'Java compile'
$classFiles = @((Get-ChildItem -LiteralPath "$outDir\classes" -Recurse -Filter '*.class').FullName)
& "$buildTools\d8.bat" --lib $androidJar --min-api 30 --output "$outDir\dex" @classFiles
Check-Exit 'DEX compile'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkArchive = [System.IO.Compression.ZipFile]::Open("$outDir\unsigned.apk",[System.IO.Compression.ZipArchiveMode]::Update)
try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkArchive,"$outDir\dex\classes.dex",'classes.dex') | Out-Null } finally { $apkArchive.Dispose() }
& "$buildTools\zipalign.exe" -f -p 4 "$outDir\unsigned.apk" "$outDir\aligned.apk"
Check-Exit 'Zipalign'
$signingKey = $env:JUMP_KEYSTORE_PATH
if (!$signingKey -or !(Test-Path -LiteralPath $signingKey)) { throw 'Set JUMP_KEYSTORE_PATH to your controlled signing keystore.' }
if (!$env:JUMP_KEYSTORE_PASSWORD -or !$env:JUMP_KEY_PASSWORD -or !$env:JUMP_KEY_ALIAS) { throw 'Set JUMP_KEYSTORE_PASSWORD, JUMP_KEY_PASSWORD and JUMP_KEY_ALIAS before signing.' }
$apkPath = if ($CompileCheck) { "$outDir\compile-check.apk" } elseif($Diagnostics) { "$projectRoot\dist\JumpAssistant-v$VersionName-diagnostics.apk" } else { "$projectRoot\dist\JumpAssistant-v$VersionName.apk" }
& "$buildTools\apksigner.bat" sign --ks $signingKey --ks-key-alias $env:JUMP_KEY_ALIAS --ks-pass env:JUMP_KEYSTORE_PASSWORD --key-pass env:JUMP_KEY_PASSWORD --out $apkPath "$outDir\aligned.apk"
Check-Exit 'APK sign'
& "$buildTools\apksigner.bat" verify --verbose $apkPath
Check-Exit 'Signature verify'
$permissions = & "$buildTools\aapt2.exe" dump permissions $apkPath
Check-Exit 'Permission audit'
foreach ($line in $permissions) { if ($line -match "uses-permission: name='([^']+)'" -and $Matches[1] -notin @('android.permission.INTERNET','android.permission.REQUEST_INSTALL_PACKAGES')) { throw "Unexpected permission: $line" } }
Write-Output $permissions
$digest = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLower()
if (!$CompileCheck -and !$Diagnostics) { "$digest  JumpAssistant-v$VersionName.apk" | Set-Content -LiteralPath "$projectRoot\dist\JumpAssistant-v$VersionName.sha256" -Encoding ascii }
Write-Output "Built: $apkPath"
