import json
import os
import sys
import urllib.parse
import urllib.request


def fetch_contributors() -> list[str]:
    project_id = os.environ.get("CROWDIN_PROJECT_ID")
    token = os.environ.get("CROWDIN_PERSONAL_TOKEN")

    if not project_id or not token:
        return []

    names: list[str] = []
    offset = 0
    limit = 500

    while True:
        query = urllib.parse.urlencode({"limit": limit, "offset": offset})
        url = f"https://api.crowdin.com/api/v2/projects/{project_id}/members?{query}"
        request = urllib.request.Request(
            url,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/json",
            },
        )

        try:
            with urllib.request.urlopen(request) as response:
                payload = json.load(response)
        except Exception:
            break

        items = payload.get("data") or payload.get("items") or []
        if not items:
            break

        for item in items:
            if not isinstance(item, dict):
                continue

            name = item.get("name")
            user = item.get("user")
            if not name and isinstance(user, dict):
                name = (
                    user.get("name")
                    or user.get("username")
                    or user.get("login")
                    or user.get("fullName")
                )

            if not name:
                name = item.get("username") or item.get("login") or item.get("email")

            if name and name not in names:
                names.append(name)

        if len(items) < limit:
            break

        offset += limit

    return sorted(names, key=str.lower)


def main() -> int:
    for name in fetch_contributors():
        print(f"- {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
