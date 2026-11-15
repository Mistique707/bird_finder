# Serves the built Bird Finder app on http://localhost and opens it in the default browser.
# Browsers won't run the WebAssembly model or microphone from file://, hence this tiny server.
$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot 'app'
$types = @{
  '.html' = 'text/html; charset=utf-8'; '.js' = 'text/javascript'; '.css' = 'text/css'; '.json' = 'application/json'
  '.wasm' = 'application/wasm'; '.png' = 'image/png'; '.jpg' = 'image/jpeg'; '.svg' = 'image/svg+xml'
  '.woff2' = 'font/woff2'; '.txt' = 'text/plain; charset=utf-8'; '.tflite' = 'application/octet-stream'
}

$listener = $null
foreach ($port in 5173..5190) {
  try {
    $l = [System.Net.HttpListener]::new()
    $l.Prefixes.Add("http://localhost:$port/")
    $l.Start()
    $listener = $l
    break
  } catch { }
}
if (-not $listener) { Write-Host 'No free port between 5173 and 5190.'; Read-Host 'Press Enter to exit'; exit 1 }

$url = $listener.Prefixes | Select-Object -First 1
Write-Host ''
Write-Host "  Bird Finder is running at $url"
Write-Host '  Keep this window open while you use it. Close it to stop.'
Write-Host ''
Start-Process $url

while ($listener.IsListening) {
  $ctx = $listener.GetContext()
  $res = $ctx.Response
  try {
    $path = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath).TrimStart('/')
    if ($path -eq '') { $path = 'index.html' }
    $file = [System.IO.Path]::GetFullPath((Join-Path $root $path))
    if (-not $file.StartsWith($root) -or -not (Test-Path $file -PathType Leaf)) { $file = Join-Path $root 'index.html' }
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $ext = [System.IO.Path]::GetExtension($file).ToLower()
    $res.ContentType = if ($types.ContainsKey($ext)) { $types[$ext] } else { 'application/octet-stream' }
    $res.ContentLength64 = $bytes.Length
    $res.OutputStream.Write($bytes, 0, $bytes.Length)
  } catch {
    $res.StatusCode = 500
  } finally {
    $res.Close()
  }
}
