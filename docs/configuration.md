# configuration

## subscription links

`owenclave://` opens the Groups screen with the subscription dialog filled in:

```text
owenclave://add-subscription?url=<encoded URL>&hwid=1
```

`hwid` is optional. It accepts `1`/`true`/`yes`/`on` or
`0`/`false`/`no`/`off`.

## HWID

Send HWID can be enabled globally or per subscription. A subscription cannot
turn off the global setting. HWID is not included in exported share links.

## naiveproxy YAML

owenclave accepts NaiveProxy entries in Clash/Mihomo YAML subscriptions:

```yaml
proxies:
  - name: My Naive
    type: naive
    server: example.com
    port: 443
    proto: https
    username: user
    password: pass
    sni: example.com
    extra-headers: "X-Header: value"
    insecure-concurrency: 1
    no-post-quantum: false
    tunnel-timeout: 30
    idle-timeout: 60
```

`proto` selects `https` or `quic`. This is an owenclave parser extension;
other clients may not accept it.
