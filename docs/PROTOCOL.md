# Loopback Protocol

The bridge implements the subset of FluxDown's remote-server contract needed by
the browser extension. It listens on:

```text
http://127.0.0.1:17800
```

## Common behavior

- Request and response bodies use UTF-8 JSON.
- `X-FluxDown-Token` is required for every route except `OPTIONS` and `/ping`.
- `Origin` is mirrored in `Access-Control-Allow-Origin`. Requests without an
  `Origin` header receive `*`.
- The server closes the connection after each response and sends
  `Cache-Control: no-store`.
- Header blocks are limited to 32 KiB.
- `Content-Length` is required for request bodies and may not exceed 2 MiB.
- Supported item URL schemes are `http`, `https`, `ftp`, `magnet`, and `ed2k`.

## `OPTIONS` — CORS preflight

```http
OPTIONS /download HTTP/1.1
Origin: chrome-extension://example
```

Returns `204` with CORS headers.

## `GET /ping` — liveness

Authentication is not required.

```http
GET /ping HTTP/1.1
```

```json
{
  "success": true,
  "message": "pong",
  "app": "FluxDown Android Bridge",
  "version": "1.7.0"
}
```

The version is read from the installed APK's build metadata.

## `GET /api/v1/info` — authenticated readiness

```http
GET /api/v1/info HTTP/1.1
X-FluxDown-Token: REDACTED
```

Success:

```json
{
  "success": true,
  "message": "ready"
}
```

An invalid or missing token returns `401`.

## `POST /download` — single item

Request:

```http
POST /download HTTP/1.1
Content-Type: application/json
X-FluxDown-Token: REDACTED
```

```json
{
  "url": "https://example.com/archive.zip",
  "filename": "archive.zip",
  "cookies": "session=REDACTED",
  "referrer": "https://example.com/",
  "headers": {
    "User-Agent": "Example browser"
  }
}
```

Only `url` is required. `filename`, `cookies`, `referrer`, and `headers` are
optional.

Success:

```json
{
  "success": true,
  "message": "sent to FluxDown"
}
```

`audioUrl` is accepted as a field by the parser but rejected when non-empty,
because the custom-protocol handoff cannot merge separate audio and video
tracks.

## `POST /download/batch` — multiple items

```json
{
  "items": [
    {
      "url": "https://example.com/one.zip",
      "filename": "one.zip"
    },
    {
      "url": "magnet:?xt=urn:btih:EXAMPLE",
      "filename": "example"
    }
  ]
}
```

The server launches items in order with a 250 ms delay between launches.

Success:

```json
{
  "success": true,
  "message": "2 item(s) sent to FluxDown"
}
```

All items are validated before the first launch. Invalid input returns `400`
with `sent: 0`. During actual dispatch, the operation stops at the first Root
launch failure; responses include `sent`, `total`, and a one-based `failedIndex`
on failure. The `message` also includes these counts because the current
extension displays only `success` and `message`.

`sent` counts successful activity launch commands, not completed downloads or
confirmed task creation. A partial failure cannot roll back prior launches;
retrying the whole batch may duplicate those items.

The 250 ms interval remains for FluxDown 0.5.9's asynchronous intake. Its browser
extension uses a 15-second request timeout, so large batches can still time out
(the fixed waits alone total 24.75 seconds for 100 items). There is no background
queue or early success acknowledgment in this release.

## Error responses

Errors use this JSON shape:

```json
{
  "success": false,
  "message": "remote_auth_failed"
}
```

| Status | Meaning |
| --- | --- |
| `400` | Invalid JSON, request size, URL scheme, missing URL, or failed Root launch |
| `401` | Missing or incorrect `X-FluxDown-Token` |
| `404` | Unknown route |

Common error messages include:

- `remote_auth_failed`
- `missing url`
- `unsupported URL scheme: <scheme>`
- `separate audioUrl is not supported by protocol handoff`
- `items array required`
- `root launch failed: <sanitized process output>`

## Trust boundary

This protocol is intended only for a browser and FluxDown instance on the same
Android device. Do not expose it through port forwarding, `adb reverse`, a
reverse proxy, or a LAN-accessible tunnel. The token protects local endpoints;
it does not turn the service into a safe remote API.
