<#
.SYNOPSIS
  workout-tracker AWS cost toggle (Tier 1: stop/start only, no address changes).

.DESCRIPTION
  down  : scale ECS service to 0 tasks + stop the RDS instance.
  up    : start RDS (wait until available) + scale ECS service back to desired count.
  status: show current ECS desired/running count and RDS status.

  Tier 1 keeps all endpoints stable:
    - RDS keeps the same endpoint across stop/start (SSM DB_URL stays valid).
    - ECS only changes desiredCount; ALB DNS / target group are untouched.
  So nothing needs to be reconnected.

  NOTE: A stopped RDS instance is auto-started by AWS after 7 days.
        If idle longer, just run "down" again.

.EXAMPLE
  ./cost-toggle.ps1 down
  ./cost-toggle.ps1 up
  ./cost-toggle.ps1 status
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('up', 'down', 'status')]
    [string]$Action,

    # Desired ECS task count when bringing the service up.
    # 2 = current setting (HA). Set 1 to halve Fargate cost while developing.
    [int]$DesiredCount = 2
)

$ErrorActionPreference = 'Stop'

# ---- config ------------------------------------------------------------
$Region  = 'ap-northeast-2'
$Cluster = 'workout-tracker-cluster'
$Service = 'workout-tracker-backend-svc'
$RdsId   = 'workout-tracker-db'
# ------------------------------------------------------------------------

# Resolve aws.exe (PATH first, then default install location).
$Aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $Aws) { $Aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }
if (-not (Test-Path $Aws)) { throw "aws CLI not found. Install it or fix the path in this script." }

function Get-EcsCounts {
    $out = & $Aws ecs describe-services --cluster $Cluster --services $Service --region $Region `
        --query 'services[0].[desiredCount,runningCount]' --output text
    return $out -split '\s+'
}

function Get-RdsStatus {
    return (& $Aws rds describe-db-instances --db-instance-identifier $RdsId --region $Region `
        --query 'DBInstances[0].DBInstanceStatus' --output text)
}

function Show-Status {
    $counts = Get-EcsCounts
    $rds    = Get-RdsStatus
    Write-Host ""
    Write-Host "=== workout-tracker status ===" -ForegroundColor Cyan
    Write-Host ("  ECS {0}: desired={1}, running={2}" -f $Service, $counts[0], $counts[1])
    Write-Host ("  RDS {0}: {1}" -f $RdsId, $rds)
    Write-Host ""
}

switch ($Action) {

    'status' {
        Show-Status
    }

    'down' {
        Write-Host "Scaling ECS service to 0 ..." -ForegroundColor Yellow
        & $Aws ecs update-service --cluster $Cluster --service $Service `
            --desired-count 0 --region $Region --query 'service.desiredCount' --output text | Out-Null
        Write-Host "  ECS desiredCount -> 0" -ForegroundColor Green

        $rds = Get-RdsStatus
        if ($rds -eq 'available') {
            Write-Host "Stopping RDS ..." -ForegroundColor Yellow
            & $Aws rds stop-db-instance --db-instance-identifier $RdsId --region $Region `
                --query 'DBInstance.DBInstanceStatus' --output text | Out-Null
            Write-Host "  RDS stopping (compute billing stops once fully stopped)" -ForegroundColor Green
        }
        else {
            Write-Host ("  RDS not 'available' (currently '{0}') - skip stop" -f $rds) -ForegroundColor DarkGray
        }

        Write-Host ""
        Write-Host "DOWN complete. Fargate + RDS compute charges will stop shortly." -ForegroundColor Cyan
        Write-Host "Reminder: a stopped RDS auto-starts after 7 days; re-run 'down' if still idle." -ForegroundColor DarkGray
    }

    'up' {
        $rds = Get-RdsStatus
        if ($rds -eq 'stopped') {
            Write-Host "Starting RDS ..." -ForegroundColor Yellow
            & $Aws rds start-db-instance --db-instance-identifier $RdsId --region $Region --output text | Out-Null
        }
        elseif ($rds -eq 'available') {
            Write-Host "  RDS already available" -ForegroundColor DarkGray
        }
        else {
            Write-Host ("  RDS currently '{0}' - will wait for it to become available" -f $rds) -ForegroundColor DarkGray
        }

        if ((Get-RdsStatus) -ne 'available') {
            Write-Host "Waiting for RDS to become available (a few minutes) ..." -ForegroundColor Yellow
            & $Aws rds wait db-instance-available --db-instance-identifier $RdsId --region $Region
            Write-Host "  RDS available" -ForegroundColor Green
        }

        Write-Host ("Scaling ECS service to {0} ..." -f $DesiredCount) -ForegroundColor Yellow
        & $Aws ecs update-service --cluster $Cluster --service $Service `
            --desired-count $DesiredCount --region $Region --query 'service.desiredCount' --output text | Out-Null
        Write-Host ("  ECS desiredCount -> {0}" -f $DesiredCount) -ForegroundColor Green

        Write-Host ""
        Write-Host "UP complete. Tasks are starting; give the ALB a minute for health checks to pass." -ForegroundColor Cyan
    }
}
