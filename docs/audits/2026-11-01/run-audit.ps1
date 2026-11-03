$ErrorActionPreference = 'Stop'
$auditRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$auditBackend = Join-Path $auditRepo 'backend'
$auditFrontend = Join-Path $auditRepo 'frontend'
$auditJavaClass = Join-Path $auditBackend 'target/test-classes/com/tradeez/audit/BackendAuditTest.class'
$auditCopies = @(
    @{ Source = 'frontend-audit.test.tsx'; Target = (Join-Path $auditFrontend 'src/test/codex-audit.test.tsx') },
    @{ Source = 'frontend-pages-audit.test.tsx'; Target = (Join-Path $auditFrontend 'src/test/codex-pages-audit.test.tsx') }
)
$auditCreatedCopies = @()

foreach ($auditCopy in $auditCopies) {
    if (Test-Path -LiteralPath $auditCopy.Target) { throw "Temporary audit target already exists: $($auditCopy.Target)" }
}

try {
    Push-Location -LiteralPath $auditBackend
    try {
        & .\mvnw.cmd -q test '-Dtest=IdentitiesTest' *> target/audit-prepare.log
        if ($LASTEXITCODE -ne 0) { throw 'Preparation failed; see backend/target/audit-prepare.log' }
        [xml]$auditReport = Get-Content -Raw -LiteralPath 'target/surefire-reports/TEST-com.tradeez.identity.IdentitiesTest.xml'
        $auditClasspath = ($auditReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
        if (-not $auditClasspath) { throw 'The test report did not contain its classpath' }
        & javac -cp $auditClasspath -d target/test-classes (Join-Path $PSScriptRoot 'BackendAuditTest.java')
        if ($LASTEXITCODE -ne 0) { throw 'Audit probe compilation failed' }
        & .\mvnw.cmd -q surefire:test '-Dtest=com.tradeez.audit.BackendAuditTest' *> target/audit-backend.log
        if ($LASTEXITCODE -ne 0) { throw 'Backend probes failed; see backend/target/audit-backend.log' }
        Get-Content -LiteralPath 'target/surefire-reports/com.tradeez.audit.BackendAuditTest.txt'
    } finally {
        Pop-Location
    }

    foreach ($auditCopy in $auditCopies) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $auditCopy.Source) -Destination $auditCopy.Target
        $auditCreatedCopies += $auditCopy.Target
    }
    Push-Location -LiteralPath $auditFrontend
    try {
        & npm run test:run -- src/test/codex-audit.test.tsx src/test/codex-pages-audit.test.tsx *> ../backend/target/audit-frontend.log
        if ($LASTEXITCODE -ne 0) { throw 'Frontend probes failed; see backend/target/audit-frontend.log' }
        Get-Content -LiteralPath '../backend/target/audit-frontend.log' -Tail 10
    } finally {
        Pop-Location
    }
} finally {
    foreach ($auditCreatedCopy in $auditCreatedCopies) {
        if (Test-Path -LiteralPath $auditCreatedCopy) { Remove-Item -LiteralPath $auditCreatedCopy }
    }
    if (Test-Path -LiteralPath $auditJavaClass) { Remove-Item -LiteralPath $auditJavaClass }
}
