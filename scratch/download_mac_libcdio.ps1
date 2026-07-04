try {
    # Get anonymous token
    $tokenJson = Invoke-RestMethod -Uri "https://ghcr.io/token?service=ghcr.io&scope=repository:homebrew/core/libcdio:pull"
    $token = $tokenJson.token
    Write-Host "Got Token: $($token.Substring(0, 10))..."

    # Download Sonoma bottle (MacOS Intel/x86_64)
    # URL from Sonoma: https://ghcr.io/v2/homebrew/core/libcdio/blobs/sha256:f5f5849a0fba0231d2ec81f8a0e14501d25fbd5c1085ac150163c3b88f4eae42
    $headers = @{ Authorization = "Bearer $token" }
    Invoke-WebRequest -Headers $headers -Uri "https://ghcr.io/v2/homebrew/core/libcdio/blobs/sha256:f5f5849a0fba0231d2ec81f8a0e14501d25fbd5c1085ac150163c3b88f4eae42" -OutFile "scratch/libcdio_mac.tar.gz"
    Write-Host "MacOS bottle downloaded successfully."
} catch {
    Write-Error $_
}
