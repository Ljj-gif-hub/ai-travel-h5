param([string]$RepoRoot = '')

# ================================================================
# 本地打包部署包：把 travel-java / agent-service / trval-h5
# 干净地拷到 deploy/bundle
# 排除 node_modules / .venv / target / dist / .git 等大目录或无关内容
#
# 用法（在仓库根目录或 deploy 目录下运行）：
#   powershell -ExecutionPolicy Bypass -File deploy\prepare-bundle.ps1
#   或显式指定: powershell -ExecutionPolicy Bypass -File prepare-bundle.ps1 -RepoRoot "H:\...\ai-travel-project"
# ================================================================
$ErrorActionPreference = 'Stop'

# 定位仓库根目录
if (-not $RepoRoot) {
    $here = (Get-Location).Path
    if (Test-Path (Join-Path $here 'travel-java')) { $RepoRoot = $here }
    elseif (Test-Path (Join-Path $here '..\travel-java')) { $RepoRoot = Split-Path $here -Parent }
    else { throw "无法定位仓库根目录，请 cd 到仓库根或 deploy 目录后运行，或用 -RepoRoot 指定" }
}
if (-not (Test-Path (Join-Path $RepoRoot 'travel-java'))) { throw "仓库根目录无效: $RepoRoot" }

$bundle = Join-Path $RepoRoot 'deploy\bundle'
if (Test-Path $bundle) { Remove-Item -Recurse -Force $bundle }
New-Item -ItemType Directory -Path $bundle -Force | Out-Null

# robocopy 精确复制：/E 含子目录 /XD 排除目录 /XF 排除文件
function Copy-Clean {
    param($Rel, [string[]]$XD, [string[]]$XF)
    $src = Join-Path $RepoRoot $Rel
    $dst = Join-Path $bundle $Rel
    robocopy $src $dst /E /XD $XD /XF $XF /NFL /NDL /NJH /NJS | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy 复制失败: $Rel (code $LASTEXITCODE)" }
}

# uploads(运行期上传文件)/docs 无需部署；agent 的 .env(真密钥) 与日志不外带；
# trval-h5 保留 public/images/landmarks（App 在用），剔除 demos/showcase（README 素材）
Copy-Clean 'travel-java'   @('target','data','logs','uploads','docs','.git','.idea','.vscode','bundle') @('*.log')
Copy-Clean 'agent-service' @('.venv','data','.git','__pycache__','.idea','.vscode')   @('.env','*.log','*.log.err')
Copy-Clean 'trval-h5'      @('node_modules','dist','dev-dist','demos','showcase','.git','.idea','.vscode') @('*.log')

# 服务器端部署脚本放到包根目录
Copy-Item (Join-Path $RepoRoot 'deploy\deploy.sh') (Join-Path $bundle 'deploy.sh') -Force

Write-Host ""
Write-Host "OK 部署包已生成: $bundle"
Write-Host "内容:"
Get-ChildItem $bundle | Select-Object Name | Format-Table -AutoSize
Write-Host "下一步（拿到服务器后）:"
Write-Host "  1. 上传:  scp -r `"$bundle`" root@服务器IP:/opt/ai-travel"
Write-Host "  2. 部署:  ssh root@服务器IP 'sudo bash /opt/ai-travel/deploy.sh'"
