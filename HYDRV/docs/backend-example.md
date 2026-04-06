# Custom Backend Guide

HYDRV can read your own catalogue JSON. The app only needs a public catalogue endpoint, and it will use the version and URL fields to show releases and start downloads.

## What HYDRV reads

The important fields are:

- `name`
- `versions`
- inside each version:
  - `version`
  - `url`

Helpful but optional fields:

- `packageName`
- `icon`
- `version_name`
- `changelog`
- timestamps such as `released_at` or `timestamp`

If `version_name` is missing, HYDRV now falls back to a safe label like `v326`.

## How to use your own backend

1. Host a public JSON file for the catalogue, usually `catalogue.json`.
2. Paste that JSON URL into HYDRV in the Backend setting.
3. Make sure the URL points to the raw JSON, not a webpage.
4. If you use signed downloads, keep the private catalogue or worker logic on your backend.

## Public Catalogue Example

`catalogue.json`

```json
[
  {
    "name": "Moonlight Android",
    "packageName": "com.example.moonlight",
    "icon": "https://example.com/icon.png",
    "versions": [
      {
        "version": 326,
        "version_name": "12.1",
        "url": "https://api.example.com/token/moonlight-android-12-1-326",
        "changelog": "Initial public release"
      },
      {
        "version": 325,
        "url": "https://api.example.com/token/moonlight-android-12-1-325",
        "changelog": "Older build with the same version name"
      }
    ]
  }
]
```

## Private Catalogue Example

`catalogue.private.json`

```json
[
  {
    "name": "Moonlight Android",
    "packageName": "com.example.moonlight",
    "icon": "https://example.com/icon.png",
    "versions": [
      {
        "version": 326,
        "version_name": "12.1",
        "downloadId": "moonlight-android-12-1-326",
        "path": "moonlight/moonlight-android-12.1.326.apk",
        "changelog": "Initial public release"
      },
      {
        "version": 325,
        "downloadId": "moonlight-android-12-1-325",
        "path": "moonlight/moonlight-android-12.1.325.apk",
        "changelog": "Older build with the same version name"
      }
    ]
  }
]
```

## Flow

1. HYDRV opens the public catalogue JSON.
2. The version rows point to your token endpoint or direct download link.
3. Your backend resolves the real file path or signed URL.
4. HYDRV downloads the APK from that response.
5. When the APK finishes, HYDRV stores the local download and version code.

## Cloudflare Settings

- R2 binding: `HYDRV_ASSETS`
- Secret: `DOWNLOAD_SIGNING_SECRET`
- Worker routes:
  - `/catalogue`
  - `/token/:downloadId`
  - `/download/:downloadId`

## Tips

- Keep `version` unique per release.
- Use `version_name` for the friendly label, but do not rely on it alone.
- If two releases share the same `version_name`, HYDRV will still separate them by `version`.
- If a version name is missing, HYDRV will show a fallback label automatically.
