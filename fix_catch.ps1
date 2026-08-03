$files = Get-ChildItem -Path d:\exasecure\src\com\examsystem\gui -Filter *.java -Recurse
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw
    $newContent = $content -replace 'catch\s*\(Exception ex\)\s*\{\}', 'catch(Exception ex) { javax.swing.JOptionPane.showMessageDialog(null, "Background error: " + ex.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE); }'
    Set-Content -Path $f.FullName -Value $newContent -NoNewline
}
