<#
.SYNOPSIS
  Dolos local infrastructure helper (Phase 0B+) — thin wrapper over `docker compose -f infra.yml`.

.DESCRIPTION
  Convenience actions for the local infra baseline (Postgres pgvector + Redpanda broker).
  Run from this folder:
    ./infra.ps1 up        # start detached
    ./infra.ps1 status    # ps + health
    ./infra.ps1 logs      # follow logs (Ctrl+C to stop following)
    ./infra.ps1 psql      # open an interactive psql shell inside the container
    ./infra.ps1 rpk       # open an rpk shell inside the Redpanda container
    ./infra.ps1 topics    # list Kafka topics
    ./infra.ps1 down      # stop, KEEP data
    ./infra.ps1 reset     # stop AND delete the data volumes (destructive)

  Redpanda Console UI: http://localhost:8088
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'status', 'logs', 'psql', 'rpk', 'topics', 'down', 'reset')]
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
    'rpk'    { & docker @compose exec redpanda rpk }
    'topics' { & docker @compose exec redpanda rpk topic list }
    'down'   { & docker @compose down }
    'reset'  { & docker @compose down -v }
}
