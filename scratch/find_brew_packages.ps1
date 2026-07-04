$json = Invoke-RestMethod -Uri "https://formulae.brew.sh/api/formula/libcdio.json"
Write-Host "Formula Name: $($json.name)"
Write-Host "Bottles Info:"
$json.bottle.stable.files.psobject.properties | ForEach-Object {
    Write-Host "$($_.Name): $($_.Value.url)"
}
