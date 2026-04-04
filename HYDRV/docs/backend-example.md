# Backend Example

This example shows the expected catalogue shape for the current token-based download flow.

## Public Catalogue

`catalogue.json`

```json
[
{
    "name": "Sample App",
    "packageName": "com.example.sample",
    "icon": "https://example.com/icon.png",
    "versions": [
        {
            "version": 1,
            "version_name": "1.0.0",
            "url": "https://api.hydrv.app/token/sample-app-1-0-0",
            "changelog": "Initial release"
        }
    ]
}
]
```

## Private Catalogue

`catalogue.private.json`

```json
[
{
    "name": "Sample App",
    "packageName": "com.example.sample",
    "icon": "https://example.com/icon.png",
    "versions": [
        {
            "version": 1,
            "version_name": "1.0.0",
            "downloadId": "sample-app-1-0-0",
            "path": "sample/sample-app-1.0.0.zip",
            "changelog": "Initial release"
        }
    ]
}
]
```

## Worker Behavior

1. The app opens the public catalogue.
2. The public version URL points at `/token/<downloadId>`.
3. The Worker checks `catalogue.private.json`.
4. The Worker returns a short-lived `downloadUrl`.
5. The app downloads from that signed URL.

## Cloudflare Settings

- R2 binding: `HYDRV_ASSETS`
- Secret: `DOWNLOAD_SIGNING_SECRET`
- Worker routes:
  - `/catalogue`
  - `/token/:downloadId`
  - `/download/:downloadId`
