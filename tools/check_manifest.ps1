<#
  清单红线检查（阶段 0 起纳入构建前/CI 检查）。
  用法:
    pwsh -File tools/check_manifest.ps1 -ManifestPath app/src/main/AndroidManifest.xml
    pwsh -File tools/check_manifest.ps1 -ManifestPath app/build/intermediates/merged_manifest/debug/AndroidManifest.xml
  退出码: 0 = 通过; 1 = 命中红线; 2 = 清单不存在。
  说明: 带 tools:node="remove" 的 uses-permission 表示强制移除该传递权限，不计违规。
#>
param(
    [string]$ManifestPath = "app\src\main\AndroidManifest.xml"
)

$ErrorActionPreference = 'Stop'

$redlinePermissions = @(
    'android.permission.INTERNET',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.ACCESS_WIFI_STATE',
    'android.permission.CAMERA',
    'android.permission.RECORD_AUDIO',
    'android.permission.READ_SMS',
    'android.permission.READ_CONTACTS',
    'android.permission.READ_CALL_LOG',
    'android.permission.BIND_ACCESSIBILITY_SERVICE',
    'android.permission.READ_CLIPBOARD',
    'android.permission.WRITE_EXTERNAL_STORAGE'
)

if (-not (Test-Path -LiteralPath $ManifestPath)) {
    Write-Host "MANIFEST NOT FOUND: $ManifestPath"
    exit 2
}

$xml = Get-Content -LiteralPath $ManifestPath -Raw
$violations = @()

# 提取所有 uses-permission 标签块（自闭合或成对）
$permMatches = [regex]::Matches($xml, '<uses-permission\b[^>]*?/>|<uses-permission\b[^>]*?>.*?</uses-permission>', 'Singleline')
foreach ($m in $permMatches) {
    $block = $m.Value
    if ($block -match 'tools:node="remove"') { continue }
    foreach ($perm in $redlinePermissions) {
        if ($block -match [regex]::Escape($perm)) { $violations += $perm }
    }
}

if ($xml -match 'android:allowBackup="true"') {
    $violations += 'android:allowBackup="true"'
}
if ($xml -match 'android:fullBackupContent="true"') {
    $violations += 'android:fullBackupContent="true"'
}

if ($violations.Count -gt 0) {
    Write-Host "FAIL: 清单命中红线:"
    foreach ($v in $violations) { Write-Host "  - $v" }
    exit 1
}

Write-Host "PASS: 清单无红线权限，备份关闭"
exit 0
