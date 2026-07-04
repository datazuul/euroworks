$wmp = New-Object -ComObject WMPlayer.OCX.7
if ($wmp.cdromCollection.count -gt 0) {
    $drive = $wmp.cdromCollection.item(0)
    Write-Host "Drive Specifier: $($drive.driveSpecifier)"
    Write-Host "Methods and Properties:"
    $drive | Get-Member | Out-String | Write-Host
} else {
    Write-Host "No CD drive found"
}
