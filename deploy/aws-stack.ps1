<#
.SYNOPSIS
  workout-tracker AWS stack orchestrator - full $0 idle teardown / on-demand bring-up.

.DESCRIPTION
  up     : bring the whole demo stack online from a torn-down state.
             RDS start + Redis (re)create + ALB (re)create in parallel, then
             update SSM REDIS_HOST, attach ALB listener, point Cloudflare DNS at
             the new ALB, scale ECS back up, and verify target health.
  down   : tear the paid resources down to ~$0.
             ECS -> 0, delete ALB, delete Redis replication group, stop RDS.
             Kept (free): VPC, subnets, SGs, target group, Redis subnet/param
             group, ECR, IAM roles, SSM, S3. RDS keeps its data (stopped, not
             deleted) so DB_URL never changes.
  status : show current existence/state of every managed resource.

  Frontend link: the ALB DNS changes on every recreate, so a stable Cloudflare
  CNAME (BACKEND_FQDN) is repointed to the new ALB each 'up'. Vercel's
  EC2_API_URL stays fixed at http://<BACKEND_FQDN> and never needs a redeploy.

  Secrets (Cloudflare token) are read from deploy/.ops.local (gitignored).

.EXAMPLE
  ./aws-stack.ps1 up
  ./aws-stack.ps1 down
  ./aws-stack.ps1 status
  ./aws-stack.ps1 up -DesiredCount 1     # single task (half Fargate cost)
  ./aws-stack.ps1 up -SkipFrontend       # skip Cloudflare DNS step
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('up', 'down', 'status')]
    [string]$Action,

    [int]$DesiredCount = 2,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'

# ---- config (captured 2026-07-01, see aws-stack-snapshot.md) ------------
$Region            = 'ap-northeast-2'
$Subnets           = @('subnet-00ef62d78c2ea01fd', 'subnet-0267d72adf5e66c78')

$AlbName           = 'workout-tracker-alb'
$AlbSg             = 'sg-067e18a5fc64e5b5e'
$TgName            = 'workout-tracker-tg-fargate'
# HTTPS: ACM cert (workout-api.eeiu.net, persists across ALB delete) + SSL policy
$AlbCertArn        = 'arn:aws:acm:ap-northeast-2:610156626396:certificate/fd5e4f68-a531-4945-bd3a-1ccd863ae153'
$AlbSslPolicy      = 'ELBSecurityPolicy-2016-08'

$RedisId           = 'workout-tracker-redis'
$RedisNodeType     = 'cache.t4g.micro'
$RedisEngine       = 'valkey'
$RedisEngineVer    = '9.0'   # ElastiCache rejects patch-level '9.0.0' on create; use minor '9.0'
$RedisSubnetGroup  = 'workout-tracker-cache-subnets'
$RedisParamGroup   = 'default.valkey9'
$RedisSg           = 'sg-0fbd95ae3529081b0'
$RedisPort         = 6379

$Cluster           = 'workout-tracker-cluster'
$Service           = 'workout-tracker-backend-svc'
$RdsId             = 'workout-tracker-db'

$SsmRedisHost      = '/workout-tracker/REDIS_HOST'
# ------------------------------------------------------------------------

# Resolve aws.exe (PATH first, then default install location).
$Aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $Aws) { $Aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }
if (-not (Test-Path $Aws)) { throw "aws CLI not found. Install it or fix the path in this script." }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step($msg)  { Write-Host $msg -ForegroundColor Yellow }
function Write-Ok($msg)    { Write-Host "  $msg" -ForegroundColor Green }
function Write-Info($msg)  { Write-Host "  $msg" -ForegroundColor DarkGray }

# ---- ops secrets (Cloudflare) ------------------------------------------
function Get-OpsConfig {
    $path = Join-Path $ScriptDir '.ops.local'
    $cfg = @{}
    if (Test-Path $path) {
        foreach ($line in Get-Content $path) {
            $t = $line.Trim()
            if ($t -eq '' -or $t.StartsWith('#')) { continue }
            $kv = $t -split '=', 2
            if ($kv.Count -eq 2) { $cfg[$kv[0].Trim()] = $kv[1].Trim() }
        }
    }
    return $cfg
}

# ---- resource lookups --------------------------------------------------
# Lookups tolerate "resource absent": describe-* returns non-zero + stderr when a
# named resource is gone. Local SilentlyContinue keeps native stderr from turning
# into a terminating error under the script-level Stop preference; we gate on exit code.
function Get-TgArn {
    $ErrorActionPreference = 'SilentlyContinue'
    $v = & $Aws elbv2 describe-target-groups --names $TgName --region $Region `
        --query 'TargetGroups[0].TargetGroupArn' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }; return $v
}
function Get-AlbArn {
    $ErrorActionPreference = 'SilentlyContinue'
    $v = & $Aws elbv2 describe-load-balancers --names $AlbName --region $Region `
        --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }; return $v
}
function Get-AlbDns {
    $ErrorActionPreference = 'SilentlyContinue'
    $v = & $Aws elbv2 describe-load-balancers --names $AlbName --region $Region `
        --query 'LoadBalancers[0].DNSName' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }; return $v
}
function Test-RedisExists {
    $ErrorActionPreference = 'SilentlyContinue'
    $null = & $Aws elasticache describe-replication-groups --replication-group-id $RedisId `
        --region $Region --query 'ReplicationGroups[0].ReplicationGroupId' --output text 2>$null
    return ($LASTEXITCODE -eq 0)
}
function Get-RedisEndpoint {
    $ErrorActionPreference = 'SilentlyContinue'
    $v = & $Aws elasticache describe-replication-groups --replication-group-id $RedisId `
        --region $Region --query 'ReplicationGroups[0].NodeGroups[0].PrimaryEndpoint.Address' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }; return $v
}
function Get-RdsStatus {
    $ErrorActionPreference = 'SilentlyContinue'
    $v = & $Aws rds describe-db-instances --db-instance-identifier $RdsId --region $Region `
        --query 'DBInstances[0].DBInstanceStatus' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }; return $v
}
function Get-EcsCounts {
    $ErrorActionPreference = 'SilentlyContinue'
    $out = & $Aws ecs describe-services --cluster $Cluster --services $Service --region $Region `
        --query 'services[0].[desiredCount,runningCount]' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return @('?', '?') }
    return $out -split '\s+'
}

# ---- Cloudflare DNS ----------------------------------------------------
function Update-CloudflareDns($albDns) {
    if ($SkipFrontend) { Write-Info "SkipFrontend set - not touching Cloudflare"; return }
    $cfg = Get-OpsConfig
    $token = $cfg['CLOUDFLARE_API_TOKEN']
    $zone  = $cfg['CLOUDFLARE_ZONE']
    $fqdn  = $cfg['BACKEND_FQDN']
    if (-not $token -or -not $zone -or -not $fqdn) {
        Write-Warning "Cloudflare not configured in deploy/.ops.local - skipping DNS update."
        Write-Warning "Frontend will NOT reach the new ALB until DNS points to: $albDns"
        return
    }
    $headers = @{ Authorization = "Bearer $token"; 'Content-Type' = 'application/json' }
    $base = 'https://api.cloudflare.com/client/v4'

    Write-Step "Updating Cloudflare DNS ($fqdn -> $albDns) ..."
    $zoneId = (Invoke-RestMethod -Headers $headers -Uri "$base/zones?name=$zone").result[0].id
    if (-not $zoneId) { throw "Cloudflare zone '$zone' not found (token scope?)." }

    $rec = (Invoke-RestMethod -Headers $headers -Uri "$base/zones/$zoneId/dns_records?type=CNAME&name=$fqdn").result
    $body = @{ type = 'CNAME'; name = $fqdn; content = $albDns; ttl = 60; proxied = $false } | ConvertTo-Json

    if ($rec -and $rec.Count -ge 1) {
        $recId = $rec[0].id
        Invoke-RestMethod -Headers $headers -Method Put -Uri "$base/zones/$zoneId/dns_records/$recId" -Body $body | Out-Null
        Write-Ok "CNAME updated"
    }
    else {
        Invoke-RestMethod -Headers $headers -Method Post -Uri "$base/zones/$zoneId/dns_records" -Body $body | Out-Null
        Write-Ok "CNAME created"
    }
}

# ========================================================================
switch ($Action) {

    'status' {
        Write-Host ""
        Write-Host "=== workout-tracker AWS stack ===" -ForegroundColor Cyan
        $counts = Get-EcsCounts
        Write-Host ("  ECS   {0}: desired={1} running={2}" -f $Service, $counts[0], $counts[1])
        Write-Host ("  RDS   {0}: {1}" -f $RdsId, (Get-RdsStatus))
        $albArn = Get-AlbArn
        Write-Host ("  ALB   {0}: {1}" -f $AlbName, $(if ($albArn -and $albArn -ne 'None') { Get-AlbDns } else { 'ABSENT ($0)' }))
        Write-Host ("  Redis {0}: {1}" -f $RedisId, $(if (Test-RedisExists) { Get-RedisEndpoint } else { 'ABSENT ($0)' }))
        Write-Host ("  SSM REDIS_HOST: {0}" -f (& $Aws ssm get-parameter --name $SsmRedisHost --region $Region --query 'Parameter.Value' --output text 2>$null))
        Write-Host ""
    }

    'down' {
        Write-Step "Scaling ECS to 0 ..."
        & $Aws ecs update-service --cluster $Cluster --service $Service --desired-count 0 `
            --region $Region --query 'service.desiredCount' --output text | Out-Null
        Write-Ok "ECS desiredCount -> 0"

        $albArn = Get-AlbArn
        if ($albArn -and $albArn -ne 'None') {
            Write-Step "Deleting ALB (target group is kept) ..."
            & $Aws elbv2 delete-load-balancer --load-balancer-arn $albArn --region $Region
            & $Aws elbv2 wait load-balancers-deleted --load-balancer-arns $albArn --region $Region
            Write-Ok "ALB deleted"
        } else { Write-Info "ALB already absent" }

        if (Test-RedisExists) {
            Write-Step "Deleting Redis replication group (no final snapshot) ..."
            & $Aws elasticache delete-replication-group --replication-group-id $RedisId `
                --no-retain-primary-cluster --region $Region --output text | Out-Null
            Write-Info "waiting for Redis to fully delete ..."
            & $Aws elasticache wait replication-group-deleted --replication-group-id $RedisId --region $Region
            Write-Ok "Redis deleted"
        } else { Write-Info "Redis already absent" }

        if ((Get-RdsStatus) -eq 'available') {
            Write-Step "Stopping RDS (data preserved) ..."
            & $Aws rds stop-db-instance --db-instance-identifier $RdsId --region $Region `
                --query 'DBInstance.DBInstanceStatus' --output text | Out-Null
            Write-Ok "RDS stopping"
        } else { Write-Info "RDS not 'available' - skip stop" }

        Write-Host ""
        Write-Host "DOWN complete. Idle cost ~= `$0 (RDS storage pennies + ECR only)." -ForegroundColor Cyan
    }

    'up' {
        $tgArn = Get-TgArn
        if (-not $tgArn -or $tgArn -eq 'None') { throw "Target group '$TgName' not found - it must persist. Aborting." }

        # --- kick off the three slow creates in parallel ---
        $rdsStatus = Get-RdsStatus
        if ($rdsStatus -eq 'stopping') {
            # can't start while still stopping, and AWS CLI has no db-instance-stopped
            # waiter, so poll until it leaves the stopping state.
            Write-Step "RDS is stopping; waiting until fully stopped before start ..."
            do { Start-Sleep -Seconds 15; $rdsStatus = Get-RdsStatus } while ($rdsStatus -eq 'stopping')
            Write-Info ("RDS now: {0}" -f $rdsStatus)
        }
        if ($rdsStatus -eq 'stopped') {
            Write-Step "Starting RDS ..."
            & $Aws rds start-db-instance --db-instance-identifier $RdsId --region $Region --output text | Out-Null
        } else { Write-Info ("RDS status: {0} (not starting)" -f $rdsStatus) }

        if (-not (Test-RedisExists)) {
            Write-Step "Creating Redis replication group ..."
            & $Aws elasticache create-replication-group `
                --replication-group-id $RedisId `
                --replication-group-description "workout-tracker redis (recreated by aws-stack.ps1)" `
                --engine $RedisEngine --engine-version $RedisEngineVer `
                --cache-node-type $RedisNodeType --num-cache-clusters 1 `
                --cache-subnet-group-name $RedisSubnetGroup `
                --cache-parameter-group-name $RedisParamGroup `
                --security-group-ids $RedisSg --port $RedisPort `
                --transit-encryption-enabled --at-rest-encryption-enabled `
                --snapshot-retention-limit 0 `
                --region $Region --output text | Out-Null
            Write-Info "Redis creating (async)"
        } else { Write-Info "Redis already exists" }

        $albArn = Get-AlbArn
        if (-not $albArn -or $albArn -eq 'None') {
            Write-Step "Creating ALB ..."
            $albArn = & $Aws elbv2 create-load-balancer --name $AlbName --type application `
                --scheme internet-facing --ip-address-type ipv4 `
                --subnets $Subnets --security-groups $AlbSg `
                --region $Region --query 'LoadBalancers[0].LoadBalancerArn' --output text
            Write-Info "ALB creating (async)"
        } else { Write-Info "ALB already exists" }

        # --- wait for everything to become ready ---
        Write-Step "Waiting for RDS to become available ..."
        & $Aws rds wait db-instance-available --db-instance-identifier $RdsId --region $Region
        Write-Ok "RDS available"

        Write-Step "Waiting for Redis to become available (this is the slow one, ~5-8 min) ..."
        & $Aws elasticache wait replication-group-available --replication-group-id $RedisId --region $Region
        Write-Ok "Redis available"

        Write-Step "Waiting for ALB to become active ..."
        & $Aws elbv2 wait load-balancer-available --load-balancer-arns $albArn --region $Region
        Write-Ok "ALB active"

        # --- wire up: SSM redis host ---
        $redisEndpoint = Get-RedisEndpoint
        Write-Step "Updating SSM $SsmRedisHost -> $redisEndpoint ..."
        & $Aws ssm put-parameter --name $SsmRedisHost --value $redisEndpoint --type String `
            --overwrite --region $Region --output text | Out-Null
        Write-Ok "SSM updated"

        # --- ensure ALB has both listeners (HTTP:80 + HTTPS:443, both forward to TG) ---
        # SG rules (80,443) and the ACM cert persist across ALB delete, so only the
        # listeners themselves need recreating here.
        $portsRaw = & $Aws elbv2 describe-listeners --load-balancer-arn $albArn --region $Region `
            --query 'Listeners[].Port' --output text 2>$null
        $ports = @(); if ($portsRaw) { $ports = $portsRaw -split '\s+' }

        if ($ports -notcontains '80') {
            Write-Step "Creating listener HTTP:80 -> $TgName ..."
            & $Aws elbv2 create-listener --load-balancer-arn $albArn --protocol HTTP --port 80 `
                --default-actions "Type=forward,TargetGroupArn=$tgArn" `
                --region $Region --output text | Out-Null
            Write-Ok "HTTP:80 listener created"
        } else { Write-Info "HTTP:80 listener already present" }

        if ($ports -notcontains '443') {
            Write-Step "Creating listener HTTPS:443 (ACM cert) -> $TgName ..."
            & $Aws elbv2 create-listener --load-balancer-arn $albArn --protocol HTTPS --port 443 `
                --ssl-policy $AlbSslPolicy --certificates "CertificateArn=$AlbCertArn" `
                --default-actions "Type=forward,TargetGroupArn=$tgArn" `
                --region $Region --output text | Out-Null
            Write-Ok "HTTPS:443 listener created"
        } else { Write-Info "HTTPS:443 listener already present" }

        # --- point Cloudflare DNS at the new ALB ---
        Update-CloudflareDns (Get-AlbDns)

        # --- scale ECS back up (tasks read fresh SSM at start) ---
        Write-Step ("Scaling ECS to {0} ..." -f $DesiredCount)
        & $Aws ecs update-service --cluster $Cluster --service $Service --desired-count $DesiredCount `
            --region $Region --query 'service.desiredCount' --output text | Out-Null

        Write-Step "Waiting for ECS service to stabilize ..."
        & $Aws ecs wait services-stable --cluster $Cluster --services $Service --region $Region
        Write-Ok "ECS stable"

        # --- verify target health (poll: TG needs ~2 checks @30s after tasks register) ---
        Write-Step "Waiting for targets to pass ALB health checks ..."
        $healthy = 0
        foreach ($i in 1..12) {
            $healthy = & $Aws elbv2 describe-target-health --target-group-arn $tgArn --region $Region `
                --query "length(TargetHealthDescriptions[?TargetHealth.State=='healthy'])" --output text
            if ([int]$healthy -ge 1) { break }
            Start-Sleep -Seconds 15
        }
        Write-Host ""
        if ([int]$healthy -ge 1) {
            Write-Host ("UP complete. {0} healthy target(s) behind the ALB." -f $healthy) -ForegroundColor Cyan
        } else {
            Write-Warning "UP finished but no healthy targets after ~3 min - check ECS task logs."
        }

        # --- end-to-end verify via the stable HTTPS domain (non-fatal) ---
        $cfg = Get-OpsConfig
        $fqdn = $cfg['BACKEND_FQDN']
        if ($fqdn -and -not $SkipFrontend) {
            Write-Step "Verifying https://$fqdn/actuator/health (allowing for DNS propagation) ..."
            $ok = $false
            foreach ($i in 1..6) {
                try {
                    $resp = Invoke-WebRequest -Uri "https://$fqdn/actuator/health" -TimeoutSec 10 -UseBasicParsing
                    if ($resp.StatusCode -eq 200) { $ok = $true; break }
                } catch { Start-Sleep -Seconds 10 }
            }
            if ($ok) { Write-Host "  End-to-end OK: HTTPS 200 via stable domain." -ForegroundColor Green }
            else { Write-Warning "Could not confirm HTTPS via $fqdn yet - DNS may still be propagating. Check manually in a minute." }
        }
    }
}
