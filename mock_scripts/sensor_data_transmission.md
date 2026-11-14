# 传感器模拟脚本

传感器实时链路已改为原生 TCP Socket，不再使用 HTTP。完整协议、后端配置、前端接收方式见 [`docs/传感器TCP数据传输.md`](../docs/传感器TCP数据传输.md)。

持续发送正弦波数据（`--sensor-id` 需与订阅的传感器匹配，见下文说明）：

```
py -3 mock_sensor_report.py --host 127.0.0.1 --port 9090 --sensor-id 19 --metric-type TEMP --min 20 --max 30 --unit "℃" --pattern sine --period 30 --interval 2


py -3 mock_sensor_report.py --host 127.0.0.1 --port 9090 --sensor-id 21 --metric-type TEMP --min 20 --max 30 --unit "℃" --pattern sine --period 30 --interval 2
```

需要模拟围绕中值波动的数据时，可改用 `--pattern normal --seed 42`；相同种子会生成相同序列。

同时模拟 5 个传感器（ID 从 10 自动递增到 14）：

```powershell
py -3 .\mock_scripts\mock_sensor_report.py --sensor-count 5 --sensor-id 10 --metric-type "PM25,PM10,TEMP,HUMIDITY,NOISE" --unit "ug/m3;ug/m3;℃;%;dB" --min "5,4,3,7,6" --max 120 --interval "1;2;1.5;3;2" --precision 2 --count 10 --connect-timeout 5
```

`--metric-type`、`--unit`、`--min`、`--max`、`--interval`、`--precision`、`--count`
和 `--connect-timeout` 都支持两种形式：只传 1 个值时全部传感器共用，传入与
`--sensor-count` 数量相同的列表时按顺序分别使用。列表可用中英文逗号或分号分隔，
分隔符两侧的空格会被忽略。`--min 5` 与 `--min=5` 都是有效写法。

`--sensor-id` 只给一个值时，该值是起始 ID；也可传入完整列表，例如
`--sensor-id "10,20,30,40,50"`。列表项数不是 1 且不等于传感器数量时，脚本会给出
明确错误，避免参数错位。

> 说明：脚本上报的 `monitorPointId` 即 `--sensor-id`。后端 WebSocket 订阅时按传感器匹配
> `monitorPointId`（优先传感器 parentId，未命中则回退到传感器 id），因此请将 `--sensor-id`
> 设为实际订阅的传感器的 parentId（或 id），并保证 `--metric-type` 与该传感器一致，否则推送的
> `value` 为空。

发送单条固定数据：

```powershell
.\mock_scripts\mock_sensor_report.ps1 -HostName 127.0.0.1 -Port 9090 -SensorId 2 -MetricType PM25 -MetricValue 88.5 -Unit "ug/m3"
```
