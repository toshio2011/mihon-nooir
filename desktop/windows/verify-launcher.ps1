param([Parameter(Mandatory=$true)][string]$ExePath)
$ErrorActionPreference = 'Stop'
# Enumerate native top-level Swing windows by owning PID. The .NET
# MainWindowHandle heuristic can pick an unrelated hidden JVM window.
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class NooirWindowProbe {
  private delegate bool EnumProc(IntPtr hwnd, IntPtr data);
  [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc callback, IntPtr data);
  [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hwnd);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int length);
  [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
  [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr hwnd, uint msg, IntPtr w, IntPtr l);
  public static IntPtr Find(int[] owned, string title) {
    IntPtr result = IntPtr.Zero;
    var ids = new HashSet<int>(owned);
    EnumWindows((hwnd, data) => {
      uint pid; GetWindowThreadProcessId(hwnd, out pid);
      if (!ids.Contains((int)pid)) return true;
      var text = new StringBuilder(1024);
      GetWindowText(hwnd, text, text.Capacity);
      bool visible = IsWindowVisible(hwnd);
      Console.WriteLine("Owned window pid=" + pid + " visible=" + visible + " title=" + text);
      if (visible && text.ToString() == title) { result = hwnd; return false; }
      return true;
    }, IntPtr.Zero);
    return result;
  }
  public static bool Close(IntPtr hwnd) { return PostMessage(hwnd, 0x0010, IntPtr.Zero, IntPtr.Zero); }
}
'@
$exe = (Resolve-Path -LiteralPath $ExePath).Path
$qaHome = Join-Path $env:RUNNER_TEMP ('nooir-launcher-' + [guid]::NewGuid().ToString())
$oldHome = $env:MIHONNOOIR_DATA_DIR
$process = $null
try {
  $env:MIHONNOOIR_DATA_DIR = $qaHome
  $process = Start-Process -FilePath $exe -WorkingDirectory (Split-Path $exe) -PassThru
  $deadline = [DateTime]::UtcNow.AddSeconds(45)
  $handle = [IntPtr]::Zero
  while ([DateTime]::UtcNow -lt $deadline) {
    $process.Refresh()
    if ($process.HasExited) { throw "Launcher exited before its window appeared: $($process.ExitCode)" }
    # A native launcher can host the JVM itself or start a child.
    # Only inspect the freshly started process and its direct children.
    $ownedIds = @([int]$process.Id)
    $ownedIds += @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.Id)" | ForEach-Object { [int]$_.ProcessId })
    $handle = [NooirWindowProbe]::Find([int[]]$ownedIds, 'MihonNooir — Windows development')
    if ($handle -ne [IntPtr]::Zero) { break }
    Start-Sleep -Milliseconds 500
  }
  if ($handle -eq [IntPtr]::Zero) {
    Write-Host "Owned launcher PID: $($process.Id), .NET title: $($process.MainWindowTitle)"
    throw 'No visible expected native window owned by the packaged launcher was found'
  }
  Write-Host 'PASS: actual Windows launcher owns visible MihonNooir main window'
  if (![NooirWindowProbe]::Close($handle)) { throw 'WM_CLOSE to owned window failed' }
  if (!$process.WaitForExit(15000)) { throw 'Owned launcher failed to exit after normal close' }
  if ($process.ExitCode -ne 0) { throw "Launcher exited with $($process.ExitCode)" }
  Write-Host 'PASS: actual packaged Windows EXE launched and closed normally'
} finally {
  if ($null -ne $process) {
    $process.Refresh()
    if (!$process.HasExited) { $process.Kill($true); $process.WaitForExit(5000) | Out-Null }
    $process.Dispose()
  }
  $env:MIHONNOOIR_DATA_DIR = $oldHome
  if (Test-Path -LiteralPath $qaHome) { Remove-Item -LiteralPath $qaHome -Recurse -Force }
}
