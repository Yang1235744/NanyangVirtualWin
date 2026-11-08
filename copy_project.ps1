$src = "J:\文档\Trae-CN\temp_20261108_02"
$dst = "J:\NanyangVirtualWin"
if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
New-Item -ItemType Directory -Path $dst -Force | Out-Null
Get-ChildItem $src -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($src.Length).TrimStart("\")
    $dir = Join-Path $dst ($rel.Substring(0, $rel.LastIndexOf("\")))
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Copy-Item $_.FullName (Join-Path $dst $rel) -Force
}
echo "COPY_DONE"
