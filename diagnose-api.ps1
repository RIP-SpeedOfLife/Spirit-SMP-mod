# Roda na pasta do projeto (depois de um ./gradlew build que falhou). Gera api-26_3.txt
$jar = Get-ChildItem -Recurse -Path "$HOME\.gradle","$PWD\.gradle" -Filter "*.jar" -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match "minecraft" -and $_.Length -gt 20MB } | Sort-Object Length -Descending | Select-Object -First 1
"JAR: $($jar.FullName)" | Out-File api-26_3.txt
foreach ($c in "net.minecraft.world.entity.Entity","net.minecraft.world.entity.LivingEntity","net.minecraft.server.level.ServerPlayer","net.minecraft.server.level.ServerLevel","net.minecraft.world.level.Level","net.minecraft.world.entity.EntityType") {
  "=== $c" | Out-File api-26_3.txt -Append
  javap -cp $jar.FullName -public $c | Select-String -Pattern "swing|Swing|tags|Tags|needsSync|hurtMarked|Clock|clock|DayTime|dayTime|isDarkOutside|weather|Weather|WARDEN|VEX|drop\(" | Out-File api-26_3.txt -Append
}
Write-Host "Pronto: api-26_3.txt"
