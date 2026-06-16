<#
.SYNOPSIS
  Dolos local infrastructure helper (Phase 0B) — thin wrapper over `docker compose -f infra.yml`.

.DESCRIPTION
  Convenience actions for the local Postgres (pgvector) baseline. Run from this folder:
    ./infra.ps1 up        # start detached
    ./infra.ps1 status    # ps + health
    ./infra.ps1 logs      # follow logs (Ctrl+C to stop following)
    ./infra.ps1 psql      # open an interactive psql shell inside the container
    ./infra.ps1 down      # stop, KEEP data
    ./infra.ps1 reset     # stop AND delete the data volume (destructive)
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'status', 'logs', 'psql', 'down', 'reset')]
    [string]$Action = 'status'
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$compose = @('compose', '-f', 'infra.yml')
$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'dolos' }
$db   = if ($env:POSTGRES_DB)   { $env:POSTGRES_DB }   else { 'dolos' }

switch ($Action) {
    'up'     { & docker @compose up -d; & docker @compose ps }
    'status' { & docker @compose ps }
    'logs'   { & docker @compose logs -f }
    'psql'   { & docker @compose exec postgres psql -U $user -d $db }
    'down'   { & docker @compose down }
    'reset'  { & docker @compose down -v }
}
