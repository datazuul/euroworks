# download_icons.ps1
# Fetches classic Windows 95-style SVG and PNG icons from the grassmunk/Chicago95 repository.
# Uses temporary files and byte-level inspection to safely resolve git symlinks without false positives on small binaries.

$targetDir = "src/main/resources/themes/win95/icons"
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir
}

# Mapping icon name to candidate GitHub paths
$icons = [ordered]@{
    "computer"    = @("devices/16/computer.png", "devices/scalable/computer.svg", "places/16/user-desktop.png")
    "folder"      = @("places/16/folder.png", "places/scalable/folder.svg")
    "document"    = @("apps/16/accessories-text-editor.png", "mimetypes/16/text-x-generic.png")
    "preferences" = @("apps/16/preferences-system.png")
    "cdplayer"    = @("apps/16/audio-player.png", "apps/16/cdplayer.png")
    "radio"       = @("apps/16/applications-multimedia.png", "apps/16/audio-player.png")
    "web"         = @("apps/scalable/internet-web-browser.svg", "apps/16/internet-web-browser.png")
    "mines"       = @("apps/scalable/gnome-mines.svg", "apps/16/gnome-mines.png")
    "calc"        = @("apps/16/calc.png")
    "paint"       = @("apps/16/gimp.png", "apps/16/paint.png")
    "generic_app" = @("apps/16/application-default-icon.png", "apps/16/exec.png")
}

$baseUrl = "https://raw.githubusercontent.com/grassmunk/Chicago95/master/Icons/Chicago95"

function Get-ActualFile {
    param (
        [string]$baseUrl,
        [string]$relativePath,
        [string]$finalDestination
    )
    
    $currentPath = $relativePath
    $maxDepth = 5
    $depth = 0
    $tempFile = [System.IO.Path]::GetTempFileName()
    
    try {
        while ($depth -lt $maxDepth) {
            $url = "$baseUrl/$currentPath"
            Write-Host "Downloading $url (depth=$depth)..."
            
            # Download file directly to the temp file
            Invoke-WebRequest -Uri $url -OutFile $tempFile -UseBasicParsing -ErrorAction Stop
            
            # Read file as raw binary bytes
            $bytes = [System.IO.File]::ReadAllBytes($tempFile)
            $fileSize = $bytes.Length
            
            $isPng = ($fileSize -ge 4 -and $bytes[0] -eq 0x89 -and $bytes[1] -eq 0x50 -and $bytes[2] -eq 0x4E -and $bytes[3] -eq 0x47)
            
            # Try to decode as UTF8 to check for SVG XML tags
            $contentStr = ""
            try {
                $contentStr = [System.Text.Encoding]::UTF8.GetString($bytes).Trim()
            } catch {}
            $isSvg = ($contentStr.StartsWith("<svg") -or $contentStr.StartsWith("<?xml") -or $contentStr.Contains("<svg"))
            
            $isSymlink = $false
            if ($fileSize -gt 0 -and $fileSize -lt 400 -and -not $isPng -and -not $isSvg) {
                # It is small and doesn't match standard PNG/SVG binary signatures: it's a symlink
                $isSymlink = $true
            }
            
            if (-not $isSymlink) {
                # Success! Copy temp file to final destination
                Copy-Item -Path $tempFile -Destination $finalDestination -Force
                return $currentPath
            }
            
            # Resolve symlink relative to currentPath's directory
            $target = $contentStr.Trim()
            Write-Host "-> Symlink detected: '$target'"
            
            $parts = $currentPath.Split('/')
            if ($parts.Length -gt 1) {
                $dir = $parts[0..($parts.Length - 2)] -join '/'
                
                # Check relative paths
                if ($target.StartsWith("../")) {
                    $dirParts = $dir.Split('/')
                    $targetParts = $target.Split('/')
                    $upCount = 0
                    while ($targetParts[$upCount] -eq "..") {
                        $upCount++
                    }
                    $newDir = $dirParts[0..($dirParts.Length - 1 - $upCount)] -join '/'
                    $newFile = $targetParts[$upCount..($targetParts.Length - 1)] -join '/'
                    if ($newDir) {
                        $currentPath = "$newDir/$newFile"
                    } else {
                        $currentPath = $newFile
                    }
                } else {
                    $currentPath = "$dir/$target"
                }
            } else {
                $currentPath = $target
            }
            
            $depth++
        }
        throw "Exceeded max symlink depth of $maxDepth"
    } finally {
        if (Test-Path $tempFile) {
            Remove-Item -Path $tempFile -Force
        }
    }
}

foreach ($name in $icons.Keys) {
    $paths = $icons[$name]
    $downloaded = $false
    
    foreach ($path in $paths) {
        try {
            $tempDest = "$targetDir/temp_$name"
            $resolvedPath = Get-ActualFile -baseUrl $baseUrl -relativePath $path -finalDestination $tempDest
            
            $ext = [System.IO.Path]::GetExtension($resolvedPath)
            $finalOutFile = "$targetDir/$name$ext"
            
            if (Test-Path $finalOutFile) {
                Remove-Item $finalOutFile -Force
            }
            Move-Item -Path $tempDest -Destination $finalOutFile -Force
            
            $downloaded = $true
            Write-Host "Successfully downloaded and resolved $name$ext"
            break
        } catch {
            Write-Warning "Could not retrieve or resolve $($path): $($_.Exception.Message)"
            if (Test-Path "$targetDir/temp_$name") {
                Remove-Item "$targetDir/temp_$name" -Force
            }
        }
    }
    
    if (-not $downloaded) {
        Write-Error "Could not download any version of icon: $name"
    }
}

Get-ChildItem -Path $targetDir -Filter "temp_*" | Remove-Item -Force

Write-Host "All icons processed!"
