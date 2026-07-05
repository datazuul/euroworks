# create_desktops.ps1
# Generates .desktop files for all EuroWorks applications in resources

$targetDir = "src/main/resources/themes/euro/share/applications"
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir | Out-Null
}

$apps = @(
    @{ Name="EuroMines"; Exec="EuroMines"; Icon="mines" },
    @{ Name="EuroBreakout"; Exec="EuroBreakout"; Icon="breakout" },
    @{ Name="EuroInvaders"; Exec="EuroInvaders"; Icon="invaders" },
    @{ Name="EuroWrite"; Exec="EuroWrite"; Icon="document" },
    @{ Name="EuroDraw"; Exec="EuroDraw"; Icon="paint" },
    @{ Name="EuroCalc"; Exec="EuroCalc"; Icon="calc" },
    @{ Name="EuroFile"; Exec="EuroFile"; Icon="file" },
    @{ Name="EuroManager"; Exec="EuroManager"; Icon="folder" },
    @{ Name="EuroDex"; Exec="EuroDex"; Icon="dex" },
    @{ Name="EuroMandelbrot"; Exec="EuroMandelbrot"; Icon="mandelbrot" },
    @{ Name="CD Player"; Exec="EuroCDPlayer"; Icon="cdplayer" },
    @{ Name="EuroScan"; Exec="EuroScan"; Icon="scan" },
    @{ Name="EuroRadio"; Exec="EuroRadio"; Icon="radio" },
    @{ Name="EuroWeb"; Exec="EuroWeb"; Icon="web" },
    @{ Name="EuroPipes"; Exec="EuroPipes"; Icon="pipes" },
    @{ Name="EuroMaze"; Exec="EuroMaze"; Icon="maze" },
    @{ Name="EuroBezier"; Exec="EuroBezier"; Icon="bezier" },
    @{ Name="EuroStarfield"; Exec="EuroStarfield"; Icon="starfield" },
    @{ Name="EuroPreferences"; Exec="EuroPreferences"; Icon="preferences" },
    @{ Name="Shut Down..."; Exec="__EXIT__"; Icon="exit" }
)

foreach ($app in $apps) {
    $fn = $app.Name.Replace(" ", "") + ".desktop"
    if ($app.Name -eq "Shut Down...") {
        $fn = "ShutDown.desktop"
    }
    
    $content = @"
[Desktop Entry]
Type=Application
Name=$($app.Name)
Exec=$($app.Exec)
Icon=$($app.Icon)
"@
    
    $path = Join-Path $targetDir $fn
    Set-Content -Path $path -Value $content -Encoding utf8
    Write-Host "Created: $path"
}
