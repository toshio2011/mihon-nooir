param(
  [Parameter(Mandatory=$true)][string]$PackageRoot,
  [Parameter(Mandatory=$true)][string]$QaJar
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $PackageRoot).Path
$qa=(Resolve-Path -LiteralPath $QaJar).Path
$version=(Get-Content desktop/windows-version.txt -Raw).Trim()
if ($version -ne '0.1.0') { throw 'Synthetic upgrade fixture is explicitly scoped to initial 0.1.0 preview' }
$current=(Resolve-Path -LiteralPath (Join-Path $root "MihonNooir-Windows-$version-x64.msi")).Path
$installed=Join-Path $env:LOCALAPPDATA 'MihonNooir-Preview'
if (Test-Path -LiteralPath $installed) { throw 'Unexpected existing app install; refusing to modify it' }
$data=Join-Path $env:RUNNER_TEMP ('nooir-installed-data-' + [guid]::NewGuid().ToString())
$fixture=Join-Path $env:RUNNER_TEMP ('nooir-msi-predecessor-' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $data,$fixture | Out-Null
Set-Content -LiteralPath (Join-Path $data 'nooir-installed-qa.marker') -Value 'disposable installer acceptance' -Encoding ascii
$oldHome=$env:MIHONNOOIR_DATA_DIR
$activeMsi=$null
function Invoke-Msi([string]$action,[string]$file,[string]$label) {
  $log=Join-Path $env:RUNNER_TEMP "nooir-msi-$label.log"
  $p=Start-Process msiexec.exe -ArgumentList @($action,('"' + $file + '"'),'/qn','/norestart','/l*v',('"' + $log + '"')) -PassThru -Wait
  if($p.ExitCode -ne 0) { Get-Content -LiteralPath $log -Tail 70;throw "MSI $label failed ($($p.ExitCode))" }
}
function Read-MsiProperty([string]$path,[string]$key) {
  $installer=New-Object -ComObject WindowsInstaller.Installer
  $db=$installer.GetType().InvokeMember('OpenDatabase','InvokeMethod',$null,$installer,@($path,0))
  $sql="SELECT " + [char]96 + "Value" + [char]96 + " FROM " + [char]96 + "Property" + [char]96 + " WHERE " + [char]96 + "Property" + [char]96 + "='$key'"
  $view=$db.GetType().InvokeMember('OpenView','InvokeMethod',$null,$db,@($sql))
  try {
    $view.GetType().InvokeMember('Execute','InvokeMethod',$null,$view,$null) | Out-Null
    $record=$view.GetType().InvokeMember('Fetch','InvokeMethod',$null,$view,$null)
    if($null -eq $record) { throw "Missing MSI property $key" }
    [string]$record.GetType().InvokeMember('StringData','GetProperty',$null,$record,@(1))
  } finally {
    $view.GetType().InvokeMember('Close','InvokeMethod',$null,$view,$null) | Out-Null
    foreach($obj in @($record,$view,$db,$installer)) { if($null -ne $obj) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($obj) } }
  }
}
Add-Type -TypeDefinition @'
using System.Runtime.InteropServices;
public static class NooirMsiProbe {
 [DllImport("msi.dll", CharSet=CharSet.Unicode)]
 public static extern int MsiQueryProductState(string productCode);
}
'@
function Assert-Active([string]$code,[bool]$expected) {
  $state=[NooirMsiProbe]::MsiQueryProductState($code)
  if($expected -and $state -ne 5) { throw "Expected installed product $code; state=$state" }
  if(!$expected -and $state -eq 5) { throw "Product $code still installed" }
}
function Invoke-State([string]$mode) {
  $java=Join-Path $installed 'runtime/bin/java.exe'
  $jar=Join-Path $installed 'app/mihonnooir-desktop-preview.jar'
  if(!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath $jar)) { throw 'Missing installed runtime' }
  & $java '-Djava.awt.headless=true' '-cp' "$jar;$qa" 'dev.nooir.desktop.ui.DesktopInstalledDataTestKt' $mode
  if($LASTEXITCODE -ne 0) { throw "Installed state $mode failed ($LASTEXITCODE)" }
}
try {
  $env:MIHONNOOIR_DATA_DIR=$data
  # No prior public Windows version exists. Create a lower-version MSI from
  # the SAME verified app image, strictly as a disposable installer fixture.
  jpackage --type msi --name MihonNooir-Preview --app-version 0.0.9 --app-image "$root/output/MihonNooir-Preview" --dest $fixture --vendor MihonNooir --win-per-user-install --win-menu --win-shortcut --win-upgrade-uuid '8441c7aa-d895-4f1d-bd3b-c99ee8eb5872'
  if($LASTEXITCODE -ne 0) { throw "Synthetic predecessor creation failed ($LASTEXITCODE)" }
  $oldPackages=@(Get-ChildItem -LiteralPath $fixture -Filter *.msi)
  if($oldPackages.Count -ne 1) { throw 'Expected one synthetic predecessor MSI' }
  $previous=$oldPackages[0].FullName
  if((Read-MsiProperty $previous ProductVersion) -ne '0.0.9' -or (Read-MsiProperty $current ProductVersion) -ne $version) { throw 'MSI product versions are not distinct expected versions' }
  $oldCode=Read-MsiProperty $previous ProductCode
  $newCode=Read-MsiProperty $current ProductCode
  $oldUpgrade=Read-MsiProperty $previous UpgradeCode
  $newUpgrade=Read-MsiProperty $current UpgradeCode
  if($oldCode -eq $newCode -or $oldUpgrade -ne $newUpgrade) { throw 'Expected distinct ProductCodes and same UpgradeCode' }
  Assert-Active $oldCode $false; Assert-Active $newCode $false
  Invoke-Msi '/i' $previous 'synthetic-predecessor-install';$activeMsi=$previous
  Assert-Active $oldCode $true
  Invoke-State 'seed'
  & ./desktop/windows/verify-launcher.ps1 -ExePath (Join-Path $installed 'MihonNooir-Preview.exe') -ExistingQaDataDir $data
  # Fingerprint the application data, not just an unrelated sentinel.
  $statePaths=@('local-library-v1.txt','reader-bookmarks-v1.tsv','conversion-queue-v1.tsv','conversion-settings-v1.txt','web-projects-v1.json','exports/original-comic.xtc','original-comic/001.png','original-comic/002.png')
  $before=@{}
  foreach($relative in $statePaths) { $before[$relative]=(Get-FileHash -LiteralPath (Join-Path $data $relative) -Algorithm SHA256).Hash }
  Invoke-Msi '/i' $current 'direct-upgrade';$activeMsi=$current
  Assert-Active $oldCode $false; Assert-Active $newCode $true
  Invoke-State 'verify'
  & ./desktop/windows/verify-launcher.ps1 -ExePath (Join-Path $installed 'MihonNooir-Preview.exe') -ExistingQaDataDir $data
  Write-Host 'PASS: synthetic 0.0.9 -> 0.1.0 MSI in-place upgrade, prior product removed'
  Invoke-Msi '/x' $current 'uninstall';$activeMsi=$null
  Assert-Active $newCode $false
  if(Test-Path -LiteralPath (Join-Path $installed 'MihonNooir-Preview.exe')) { throw 'Uninstall left launcher installed' }
  foreach($relative in $statePaths) {
    if((Get-FileHash -LiteralPath (Join-Path $data $relative) -Algorithm SHA256).Hash -ne $before[$relative]) { throw "User state changed or disappeared: $relative" }
  }
  Write-Host 'PASS: actual library/bookmark/queue records and original/export files byte-identical after MSI upgrade/uninstall'
  # Reinstall and reopen the old state through production code in a fresh JVM.
  Invoke-Msi '/i' $current 'reinstall';$activeMsi=$current
  Assert-Active $newCode $true
  Invoke-State 'verify'
  Write-Host 'PASS: reinstalled Windows application reopened retained user state'
} finally {
  $env:MIHONNOOIR_DATA_DIR=$oldHome
  if($null -ne $activeMsi) { Invoke-Msi '/x' $activeMsi 'cleanup' }
  Remove-Item -LiteralPath $data,$fixture -Recurse -Force
}
