param(
    [Parameter(Mandatory=$true)][string]$RepositoryUrl
)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git não está instalado. Instale Git for Windows: https://git-scm.com/downloads/win' }
if ($RepositoryUrl -notmatch '^https://github\.com/[\w.-]+/[\w.-]+(?:\.git)?$') { throw 'Informe URL HTTPS do repositório GitHub, ex.: https://github.com/seuusuario/NEXIR-LAB.git' }
if (-not (Test-Path '.git')) { git init }
git branch -M main
git add .
git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    git commit -m 'NEXIR LAB - aplicativo Android e compilacao automatica'
    if ($LASTEXITCODE -ne 0) { throw 'Falha no commit. Configure git config --global user.name e git config --global user.email.' }
}
$remotes = @(git remote)
if ($remotes -contains 'origin') { git remote set-url origin $RepositoryUrl }
else { git remote add origin $RepositoryUrl }
Write-Host 'Enviando projeto para o GitHub. Você pode precisar autenticar no navegador.'
git push -u origin main
if ($LASTEXITCODE -ne 0) { throw 'Falha no git push. Confira acesso ao GitHub, autenticacao e se o repositorio esta vazio.' }
Write-Host 'Arquivos enviados. Abra GitHub > Actions e confira a compilacao.'
