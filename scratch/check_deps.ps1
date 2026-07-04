$bytes = [System.IO.File]::ReadAllBytes("src/main/resources/win32-x86-64/cdio.dll")
$str = [System.Text.Encoding]::ASCII.GetString($bytes)
[regex]::Matches($str, "[a-zA-Z0-9_\-]+\.dll") | ForEach-Object { $_.Value } | Sort-Object -Unique
