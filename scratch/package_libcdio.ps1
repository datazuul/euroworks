$ErrorActionPreference = "Stop"

# Create directories
$destWin = "src/main/resources/win32-x86-64"
$destLinux = "src/main/resources/linux-x86-64"
$destMacIntel = "src/main/resources/darwin-x86-64"
$destMacArm = "src/main/resources/darwin-aarch64"

New-Item -ItemType Directory -Force -Path $destWin | Out-Null
New-Item -ItemType Directory -Force -Path $destLinux | Out-Null
New-Item -ItemType Directory -Force -Path $destMacIntel | Out-Null
New-Item -ItemType Directory -Force -Path $destMacArm | Out-Null

Write-Host "Downloading and packaging Windows libcdio..."
Invoke-WebRequest -Uri "https://repo.msys2.org/mingw/mingw64/mingw-w64-x86_64-libcdio-2.3.0-1-any.pkg.tar.zst" -OutFile "scratch/libcdio_win.tar.zst"
# Extract using bsdtar
tar -xf "scratch/libcdio_win.tar.zst" -C "scratch" "mingw64/bin/libcdio-19.dll"
Copy-Item "scratch/mingw64/bin/libcdio-19.dll" -Destination "$destWin/cdio.dll" -Force

Write-Host "Downloading and packaging Windows libiconv dependency..."
Invoke-WebRequest -Uri "https://repo.msys2.org/mingw/mingw64/mingw-w64-x86_64-libiconv-1.19-1-any.pkg.tar.zst" -OutFile "scratch/libiconv_win.tar.zst"
tar -xf "scratch/libiconv_win.tar.zst" -C "scratch" "mingw64/bin/libiconv-2.dll"
Copy-Item "scratch/mingw64/bin/libiconv-2.dll" -Destination "$destWin/libiconv-2.dll" -Force

Write-Host "Downloading and packaging Linux libcdio..."
Invoke-WebRequest -Uri "https://archive.archlinux.org/packages/l/libcdio/libcdio-2.3.0-1-x86_64.pkg.tar.zst" -OutFile "scratch/libcdio_linux.tar.zst"
tar -xf "scratch/libcdio_linux.tar.zst" -C "scratch" "usr/lib/libcdio.so.19.1.0"
Copy-Item "scratch/usr/lib/libcdio.so.19.1.0" -Destination "$destLinux/libcdio.so" -Force

# Get anonymous token for Homebrew core
Write-Host "Authenticating with ghcr.io for Homebrew bottles..."
$tokenJson = Invoke-RestMethod -Uri "https://ghcr.io/token?service=ghcr.io&scope=repository:homebrew/core/libcdio:pull"
$token = $tokenJson.token
$headers = @{ Authorization = "Bearer $token" }

Write-Host "Downloading and packaging macOS Intel libcdio..."
Invoke-WebRequest -Headers $headers -Uri "https://ghcr.io/v2/homebrew/core/libcdio/blobs/sha256:f5f5849a0fba0231d2ec81f8a0e14501d25fbd5c1085ac150163c3b88f4eae42" -OutFile "scratch/libcdio_mac_intel.tar.gz"
tar -xf "scratch/libcdio_mac_intel.tar.gz" -C "scratch" "libcdio/2.3.0/lib/libcdio.19.dylib"
Copy-Item "scratch/libcdio/2.3.0/lib/libcdio.19.dylib" -Destination "$destMacIntel/libcdio.dylib" -Force

Write-Host "Downloading and packaging macOS ARM64 libcdio..."
Invoke-WebRequest -Headers $headers -Uri "https://ghcr.io/v2/homebrew/core/libcdio/blobs/sha256:a5f3c809caf711932d2f63d9dd90b4279749819c8a27fe529e4a1d7d163e7fad" -OutFile "scratch/libcdio_mac_arm.tar.gz"
tar -xf "scratch/libcdio_mac_arm.tar.gz" -C "scratch" "libcdio/2.3.0/lib/libcdio.19.dylib"
Copy-Item "scratch/libcdio/2.3.0/lib/libcdio.19.dylib" -Destination "$destMacArm/libcdio.dylib" -Force

Write-Host "Cleaning up temporary files..."
Remove-Item -Path "scratch/mingw64" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "scratch/usr" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "scratch/libcdio" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "scratch/*.tar.zst" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "scratch/*.tar.gz" -Force -ErrorAction SilentlyContinue

Write-Host "All native libcdio binaries and dependencies packaged successfully!"
