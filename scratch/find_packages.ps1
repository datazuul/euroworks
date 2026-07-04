$html = Invoke-WebRequest -Uri "https://repo.msys2.org/mingw/mingw64/" -UseBasicParsing
$html.Links | Where-Object { $_.href -like "*libcdio*" } | Select-Object -ExpandProperty href
