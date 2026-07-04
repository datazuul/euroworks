$html = Invoke-WebRequest -Uri "https://archive.archlinux.org/packages/l/libcdio/" -UseBasicParsing
$html.Links | Where-Object { $_.href -like "*.pkg.tar.zst" } | Select-Object -ExpandProperty href
