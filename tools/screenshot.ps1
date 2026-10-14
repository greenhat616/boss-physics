# Captures only the autotest client window (not the desktop), even when other windows cover it.
#   pwsh tools/screenshot.ps1 [-Out path.png]
# Finds the java.exe started by tools/autotest.sh (its command line contains "build/autotest").
param([string]$Out = "$PSScriptRoot/../build/autotest/screenshot.png")

Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class Win {
    public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lParam);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdc, uint flags);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
'@
[Win]::SetProcessDPIAware() | Out-Null

$proc = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*build/autotest*' -or $_.CommandLine -like '*build\autotest*' } |
    Select-Object -First 1
if (-not $proc) { Write-Error "autotest client is not running"; exit 1 }

$script:hwnd = [IntPtr]::Zero
$best = 0
[Win]::EnumWindows({
    param($h, $l)
    $wpid = 0
    [Win]::GetWindowThreadProcessId($h, [ref]$wpid) | Out-Null
    if ($wpid -eq $proc.ProcessId -and [Win]::IsWindowVisible($h)) {
        $r = New-Object Win+RECT
        [Win]::GetClientRect($h, [ref]$r) | Out-Null
        $area = ($r.Right - $r.Left) * ($r.Bottom - $r.Top)
        if ($area -gt $script:best) { $script:best = $area; $script:hwnd = $h }
    }
    return $true
}, [IntPtr]::Zero) | Out-Null
if ($script:hwnd -eq [IntPtr]::Zero) { Write-Error "no visible window for pid $($proc.ProcessId)"; exit 1 }

$rect = New-Object Win+RECT
[Win]::GetClientRect($script:hwnd, [ref]$rect) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
# 1 = PW_CLIENTONLY, 2 = PW_RENDERFULLCONTENT (needed for OpenGL/DirectX content)
[Win]::PrintWindow($script:hwnd, $hdc, 3) | Out-Null
$g.ReleaseHdc($hdc)
$bmp.Save((New-Item -ItemType File -Force $Out).FullName)
"saved $($bmp.Width)x$($bmp.Height) to $Out"
