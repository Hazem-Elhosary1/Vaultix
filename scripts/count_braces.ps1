$path = 'e:\Vaultix\app\src\main\java\com\vaultix\app\ui\screens\SettingsScreen.kt'
$s = Get-Content $path -Raw
$open = ($s.ToCharArray() | Where-Object { $_ -eq '{' }).Count
$close = ($s.ToCharArray() | Where-Object { $_ -eq '}' }).Count
Write-Output "OPEN:$open CLOSE:$close"
