# E53_ST1真实GPS固件

## 目录

- `D13_gps_uart_test`：只读取并打印L80-R GPS串口数据，用于确认接线、波特率和定位状态。
- `D14_smart_logistics_gps_mqtt`：读取有效RMC定位数据，通过Wi-Fi和MQTT发布到云端；F1模拟异常开箱，F2模拟关箱并复位告警锁定。

## 配置

将配置模板复制为实际配置：

```powershell
Copy-Item .\iot\firmware\D14_smart_logistics_gps_mqtt\smart_logistics_config.example.h `
  .\iot\firmware\D14_smart_logistics_gps_mqtt\smart_logistics_config.h
```

然后在本机填写Wi-Fi和MQTT参数。实际配置已被本目录 `.gitignore` 排除。

## 集成到BearPi-HM Nano源码

1. 将D13/D14目录复制到 `applications/BearPi/BearPi-HM_Nano/sample/`。
2. 在 `applications/BearPi/BearPi-HM_Nano/sample/BUILD.gn` 的 `features` 中只启用一个目标：

```gn
"D13_gps_uart_test:gps_uart_test",
```

或：

```gn
"D14_smart_logistics_gps_mqtt:smart_logistics_gps_mqtt",
```

3. 按BearPi-HM Nano工程原有方式构建并烧录。
4. 串口出现有效GPS FIX后，D14将以 `sim_999` 发布到：

```text
iot/carla/vehicle/sim_999/gps
iot/carla/vehicle/sim_999/status
iot/carla/alert
iot/carla/alert/recovery
```

D14按键约定：

- F1（GPIO 11）：箱门由关闭变为打开，向 `iot/carla/alert` 发布一条 `source=device` 的“异常开箱”告警；
- 箱门保持打开时重复按F1不会重复上报；
- F2（GPIO 12）：模拟箱门关闭，向 `iot/carla/alert/recovery` 发布恢复事件；发布成功后清除本地锁定，之后再次按F1可产生下一条告警；
- F2恢复事件携带原F1的 `triggered_at` 和本次关箱的 `recovered_at`，供后端准确更新原告警；重复按F2不会重复上报；
- Wi-Fi连接成功后通过NTP获取UTC时间，因此开箱告警不依赖GPS已经定位；若NTP失败且尚无GPS时间，告警会保留为待发送。

## 为什么仓库不提供云端 `.bin`

Wi-Fi与MQTT配置会编译进固件。为了避免公开真实凭据，仓库只提交源码和配置模板；答辩现场使用本地构建的固件，不上传带真实凭据的二进制。
