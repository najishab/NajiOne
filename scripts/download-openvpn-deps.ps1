# Fills third_party\openvpn3 with the pinned sources from source-lock.json.
# Run from the project root:  .\scripts\download-openvpn-deps.ps1
$ErrorActionPreference = 'Stop'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch {}

$deps = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\third_party\openvpn3"))

function Fetch([string]$label, [string]$url, [string]$sha256, [string]$folder) {
    if (Test-Path (Join-Path $deps $folder)) { Write-Host "OK    $folder (already present)"; return }
    $zip = Join-Path $deps "$folder.zip"
    if (-not (Test-Path $zip)) {
        Write-Host "GET   $label"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    } else {
        Write-Host "ZIP   $label (zip already downloaded)"
    }
    $hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $sha256) { throw "$label SHA256 mismatch!`n  expected: $sha256`n  got:      $hash" }
    Expand-Archive -LiteralPath $zip -DestinationPath $deps -Force
    Remove-Item -LiteralPath $zip
    if (-not (Test-Path (Join-Path $deps $folder))) { throw "$label extracted without the expected folder $folder" }
    Write-Host "OK    $folder"
}

Fetch 'OpenVPN 3 core (2986b581)' 'https://codeload.github.com/OpenVPN/openvpn3/zip/2986b58108726a7aff278a50caa0cff9c4cdf943' 'f6be52f0b74a57a701a4ce07a4cd0d7daace2f2994ded9e53c68c8a1a541b64b' 'openvpn3-2986b58108726a7aff278a50caa0cff9c4cdf943'
Fetch 'Mbed TLS 3.6.7' 'https://codeload.github.com/Mbed-TLS/mbedtls/zip/refs/tags/mbedtls-3.6.7' 'd359314abc9bced38ddf9f0f9423e8c1c5084113a00f26d6864e1ceea6bf1153' 'mbedtls-mbedtls-3.6.7'
Fetch 'Asio 1.30.2' 'https://codeload.github.com/chriskohlhoff/asio/zip/refs/tags/asio-1-30-2' '948373ed2e838ee1181937380c754e8fe6da8b18022eae922bcdc0cd38d6dbde' 'asio-asio-1-30-2'
Fetch 'LZ4 1.10.0' 'https://codeload.github.com/lz4/lz4/zip/refs/tags/v1.10.0' '3224b4c80f351f194984526ef396f6079bd6332dd9825c72ac0d7a37b3cdc565' 'lz4-1.10.0'
Fetch 'fmt 11.1.4' 'https://codeload.github.com/fmtlib/fmt/zip/refs/tags/11.1.4' '7e85cbf6125a76daa0f83cd9240eff863d988aca68cd5f66c01ff7b59fa886b6' 'fmt-11.1.4'

# Mbed TLS framework is a submodule upstream: its content goes INSIDE the mbedtls folder.
$fwZip = Join-Path $deps 'framework.zip'
$fwSrc = Join-Path $deps 'mbedtls-framework-dde0c4a0e448a0552f18817dcea633bb851fd288'
$fwDst = Join-Path $deps 'mbedtls-mbedtls-3.6.7\framework'
if (Test-Path $fwDst) {
    Write-Host "OK    mbedtls-mbedtls-3.6.7\framework (already present)"
} else {
    if (-not (Test-Path $fwZip)) {
        Write-Host 'GET   Mbed TLS framework (dde0c4a0)'
        Invoke-WebRequest -Uri 'https://codeload.github.com/Mbed-TLS/mbedtls-framework/zip/dde0c4a0e448a0552f18817dcea633bb851fd288' -OutFile $fwZip -UseBasicParsing
    }
    $hash = (Get-FileHash -LiteralPath $fwZip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne '2808e7a9b6fd87f294d1423bb29984057d0654b4f0d70b0d9cf085e887472337') { throw "Mbed TLS framework SHA256 mismatch!" }
    Expand-Archive -LiteralPath $fwZip -DestinationPath $deps -Force
    Remove-Item -LiteralPath $fwZip
    if (Test-Path $fwDst) { Remove-Item -Recurse -Force $fwDst }   # empty placeholder from the mbedtls zip
    Move-Item $fwSrc $fwDst
    Write-Host 'OK    mbedtls-mbedtls-3.6.7\framework'
}

Write-Host ''
Write-Host 'All pinned sources are in place. Now run:  .\scripts\build-openvpn.ps1'
