# SmartThingsCloud Binding

The SmartThingsCloud binding integrates SmartThings-connected devices into openHAB using the [SmartThings Cloud API](https://developer.smartthings.com/). 
Currently, it specifically supports **Samsung WindFree Air Conditioners**, but is designed to be extensible for other device types.

## Supported Things

This binding supports the following thing types:

| Thing Type UID | Description |
|---|---|
| `account` | **SmartThings Account Bridge**: Represents your SmartThings account connection. Requires OAuth 2.0 authorization. |
| `windfree-ac` | **Samsung WindFree AC**: Controls a Samsung WindFree Air Conditioner. |

## Discovery

Once the **SmartThings Account Bridge** is added and successfully connected (STATUS: ONLINE), the binding automatically scans your SmartThings account for supported devices.

- **WindFree ACs** will appear in the Inbox automatically.
- The scan is performed periodically or can be manually triggered.

### Binding Configuration

The binding utilizes the SmartThings CLI OAuth flow for a simplified setup. No manual Client ID or Client Secret is required by default.

#### Configuration Steps
1. Create a **SmartThings Account** bridge in openHAB.
2. Leave `Client ID` and `Client Secret` empty (unless you want to use your own).
3. Check the bridge status; it will provide an authorization URL.
4. Click the URL to authorize the binding.
5. The binding will automatically capture the authorization code on port 61973 and complete the setup.

> [!TIP]
> Ensure that port **61973** is available on the machine running openHAB during the authorization process.

### Bridge Configuration

| Parameter | Type | Required | Description |
|---|---|---|---|
| `clientId` | text | No | OAuth Client ID (optional, defaults to SmartThings CLI ID). |
| `clientSecret` | text | No | OAuth Client Secret (optional for SmartThings CLI flow). |
| `authCode` | text | No | Captured automatically. |

### Samsung WindFree AC (`windfree-ac`)

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `deviceId` | text | Yes | - | The specific Device ID (UUID) of the AC in SmartThings. |
| `refreshInterval` | integer | No | 60 | Interval in seconds to poll the device status (minimum 10s). |

> [!NOTE]
> The Thing status will update to **ONLINE** only after a successful API connection. If a polling request fails, the status changes to **OFFLINE** (Communication Error) and error logs are suppressed to avoid log spam.

## Channels

### Samsung WindFree AC (`windfree-ac`)

| Channel ID | Type | Description |
|---|---|---|
| `power` | Switch | Turn the AC On/Off. |
| `temperature` | Number:Temperature | Current room temperature (Read-only). |
| `setpoint` | Number:Temperature | Target cooling temperature setpoint. |
| `mode` | String | Operation mode. Options: `auto`, `cool`, `dry`, `heat`, `windfree`. |

## Full Example

### Things Configuration (`smartthings.things`)

```java
Bridge smartthingscloud:account:myaccount [ ] {
    Thing windfree-ac livingroom [ deviceId="YOUR_DEVICE_UUID", refreshInterval=30 ]
}
```

### Items Configuration (`smartthings.items`)

```java
Switch LivingRoom_AC_Power "Power" { channel="smartthingscloud:windfree-ac:myaccount:livingroom:power" }
Number:Temperature LivingRoom_AC_Temp "Temperature [%.1f %unit%]" { channel="smartthingscloud:windfree-ac:myaccount:livingroom:temperature" }
Number:Temperature LivingRoom_AC_Setpoint "Setpoint [%.1f %unit%]" { channel="smartthingscloud:windfree-ac:myaccount:livingroom:setpoint" }
String LivingRoom_AC_Mode "Mode" { channel="smartthingscloud:windfree-ac:myaccount:livingroom:mode" }
```

### Sitemap Configuration (`smartthings.sitemap`)

```perl
sitemap smartthings label="SmartThings" {
    Frame label="Living Room AC" {
        Switch item=LivingRoom_AC_Power
        Text item=LivingRoom_AC_Temp
        Setpoint item=LivingRoom_AC_Setpoint minValue=16 maxValue=30 step=1
        Selection item=LivingRoom_AC_Mode mappings=[auto="Auto", cool="Cool", dry="Dry", heat="Heat", windfree="WindFree"]
    }
}
```
