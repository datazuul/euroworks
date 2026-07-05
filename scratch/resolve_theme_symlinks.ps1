# resolve_theme_symlinks.ps1
# Recursively traverses a theme folder, finds git symlinks, and copies their actual target file content in place.

param (
    [string]$themeDir
)

if (-not (Test-Path $themeDir)) {
    Write-Error "Directory $themeDir does not exist."
    exit
}

Write-Host "Resolving symlinks in $themeDir..."

$files = Get-ChildItem -Path $themeDir -Recurse -File
$symlinkCount = 0

foreach ($file in $files) {
    if ($file.Length -gt 0 -and $file.Length -lt 400) {
        $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
        $isPng = ($bytes.Length -ge 4 -and $bytes[0] -eq 0x89 -and $bytes[1] -eq 0x50 -and $bytes[2] -eq 0x4E -and $bytes[3] -eq 0x47)
        
        $contentStr = ""
        try {
            $contentStr = [System.Text.Encoding]::UTF8.GetString($bytes).Trim()
        } catch {}
        $isSvg = ($contentStr.StartsWith("<svg") -or $contentStr.StartsWith("<?xml") -or $contentStr.Contains("<svg"))
        
        if (-not $isPng -and -not $isSvg) {
            # It's a symlink target!
            $target = $contentStr.Trim()
            $parentDir = $file.DirectoryName
            $targetPath = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($parentDir, $target))
            
            if (Test-Path $targetPath) {
                Write-Host "Resolving: '$($file.FullName)' -> '$targetPath'"
                
                # Resolve recursively if target is also a symlink (max depth 5)
                $maxDepth = 5
                $depth = 0
                $currentTarget = $targetPath
                
                while ($depth -lt $maxDepth) {
                    $targetBytes = [System.IO.File]::ReadAllBytes($currentTarget)
                    $tPng = ($targetBytes.Length -ge 4 -and $targetBytes[0] -eq 0x89 -and $targetBytes[1] -eq 0x50 -and $targetBytes[2] -eq 0x4E -and $targetBytes[3] -eq 0x47)
                    $tStr = ""
                    try {
                        $tStr = [System.Text.Encoding]::UTF8.GetString($targetBytes).Trim()
                    } catch {}
                    $tSvg = ($tStr.StartsWith("<svg") -or $tStr.StartsWith("<?xml") -or $tStr.Contains("<svg"))
                    
                    if ($tPng -or $tSvg -or $targetBytes.Length -ge 400) {
                        # Actual file found! Write bytes over the symlink file.
                        [System.IO.File]::WriteAllBytes($file.FullName, $targetBytes)
                        $symlinkCount++
                        break
                    }
                    
                    $tParent = [System.IO.Path]::GetDirectoryName($currentTarget)
                    $currentTarget = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($tParent, $tStr))
                    
                    if (-not (Test-Path $currentTarget)) {
                        Write-Warning "Nested target not found: $currentTarget"
                        break
                    }
                    $depth++
                }
            } else {
                Write-Warning "Target not found: $targetPath (for $($file.FullName))"
            }
        }
    }
}

Write-Host "Finished resolving symlinks. Resolved $symlinkCount files."
