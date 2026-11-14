param(
    [string]$HostName = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int]$Port = 9090,
    [long]$SensorId = 2,
    [ValidateSet("PM25", "PM10", "NOISE", "TEMP", "HUMIDITY", "WIND")]
    [string]$MetricType = "PM25",
    [decimal]$MetricValue = 88.5,
    [string]$Unit = "ug/m3"
)

$client = [System.Net.Sockets.TcpClient]::new()
try {
    $client.Connect($HostName, $Port)
    $stream = $client.GetStream()
    $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false), 1024, $true)
    $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $false, 1024, $true)
    $writer.AutoFlush = $true

    $writer.WriteLine((@{ role = "sensor" } | ConvertTo-Json -Compress))
    $payload = @{
        monitorPointId = $SensorId
        metricType = $MetricType
        metricValue = $MetricValue
        unit = $Unit
        collectTime = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    } | ConvertTo-Json -Compress
    $writer.WriteLine($payload)

    $ack = $reader.ReadLine() | ConvertFrom-Json
    if ($ack.type -ne "ack") {
        throw "后端未确认数据: $($ack | ConvertTo-Json -Compress)"
    }
    Write-Host "TCP 数据发送成功: $payload"
} finally {
    if ($null -ne $writer) { $writer.Dispose() }
    if ($null -ne $reader) { $reader.Dispose() }
    $client.Dispose()
}
