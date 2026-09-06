# One checkout has one lifecycle owner. A failed/closed launcher releases the
# kernel mutex automatically; another checkout remains independent.
function Enter-ProjectLifecycleLock([string]$ProjectRoot) {
    $identity = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/').ToUpperInvariant()
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($identity))).Replace('-', '')
    } finally {
        $sha.Dispose()
    }
    $mutex = New-Object Threading.Mutex($false, "Local\SilverPilot-Lifecycle-$digest")
    try {
        $acquired = $false
        try { $acquired = $mutex.WaitOne(0) }
        catch [Threading.AbandonedMutexException] { $acquired = $true }
        if (-not $acquired) {
            throw 'Another startup, mode switch, or shutdown is already running for this checkout. Wait for it to finish, then retry.'
        }
        return $mutex
    } catch {
        $mutex.Dispose()
        throw
    }
}

function Exit-ProjectLifecycleLock([Threading.Mutex]$Mutex) {
    if ($null -eq $Mutex) { return }
    try { $Mutex.ReleaseMutex() } finally { $Mutex.Dispose() }
}
