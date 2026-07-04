$wmp = New-Object -ComObject WMPlayer.OCX.7
if ($wmp.cdromCollection.count -gt 0) {
    $drive = $wmp.cdromCollection.item(0)
    $playlist = $drive.Playlist
    Write-Host "Tracks count: $($playlist.count)"
    
    $wmp.currentPlaylist = $playlist
    $wmp.controls.play()
    
    Write-Host "Playing CD..."
    Start-Sleep -Seconds 5
    
    $wmp.controls.stop()
    Write-Host "Stopped CD playback."
} else {
    Write-Host "No CD drive found"
}
