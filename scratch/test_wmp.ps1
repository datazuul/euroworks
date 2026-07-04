$wmp = New-Object -ComObject WMPlayer.OCX.7
$wmp | Get-Member | Out-String | Write-Host
