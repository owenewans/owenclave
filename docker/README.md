# Owenclave Docker (Mihomo-Compatible Server + Web UI)

Runs `owenclave-core` and external plugins (`naive`) as a headless gateway/router daemon with:

- **Mixed HTTP + CONNECT + SOCKS5 + UDP listener** (`mixed-port`, default `7890`)
- **Optional Linux TProxy listener** (`tproxy-port`, requires `CAP_NET_ADMIN`)
- **Mihomo / Clash.Meta compatible REST controller** (`external-controller`, default `0.0.0.0:9090`) with constant-time Bearer `secret` authentication (`GET /version`, `GET/PATCH /configs`, `GET/PUT /proxies`, `GET /proxies/{name}/delay`, `GET/PUT /providers/proxies`, `GET /rules`, `GET /logs`)
- **Built-in Web Console** at `http://<host>:9090/ui/` for switching proxy groups and nodes, adding HTTP/HTTPS proxies or share links, managing subscriptions (including Remnawave Mihomo YAML and `happ://` encrypted links), editing rules, and viewing live logs.

## Build & Run

```sh
cp docker/config.example.yaml docker/config/config.yaml
docker compose up -d --build
```

For fast local rebuilds when `desktop/core/dist` is already built on the host:

```sh
./gradlew :desktop:app:prepareServerDist
docker build --target prebuilt -t owenclave:latest .
```

## Replacing a Mihomo Container

Owenclave reads standard Mihomo fields from `/config/config.yaml` (`mixed-port`, `tproxy-port`, `allow-lan`, `bind-address`, `interface-name`, `mode`, `log-level`, `external-controller`, `secret`, `proxies`, `proxy-groups`, `rule-providers`, `rules`) and accepts overrides via environment variables:

| Variable | Description |
| --- | --- |
| `CONFIG_FILE` | Path to `config.yaml` (default `/config/config.yaml`) |
| `DATA_DIR` | Persistent state directory (default `/data`) |
| `MIXED_PORT` | Mixed HTTP + SOCKS5 + UDP port |
| `TPROXY_PORT` | Transparent proxy port (`0` disables) |
| `BIND_ADDRESS` | Listener IP (`0.0.0.0` or specific LAN interface IP) |
| `INTERFACE_NAME` | Outbound interface (`SO_BINDTODEVICE`, e.g. `enp7s0`) |
| `EXTERNAL_CONTROLLER` | REST API + `/ui/` address (`0.0.0.0:9090`) |
| `SECRET` | Controller Bearer secret |
| `SUBSCRIPTION_URL` | Subscription URL(s) to fetch and auto-refresh |
| `AUTO_UPDATE_MINUTES` | Subscription refresh interval in minutes (default `60`) |
| `FALLBACK_TO_DIRECT` | Route through `bypass` (`DIRECT`) when all proxy servers are unreachable (`true` / `false`) |
| `HEALTH_CHECK_INTERVAL` | Optional cap in seconds for automatic group health-check intervals |

