import json
import os
import sys
import urllib.parse
import urllib.request


class CrowdinLookupError(RuntimeError):
    pass


def lookup_is_required() -> bool:
    return os.environ.get("CROWDIN_REQUIRE_RELEASE_LOOKUP", "").lower() == "true"


def unwrap_item(item: dict) -> dict:
    data = item.get("data")
    return data if isinstance(data, dict) else item


def extract_name(item: dict) -> str | None:
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

    return name


def is_translation_contributor(item: dict) -> bool:
    role_values: list[str] = []

    def collect_role(value: object) -> None:
        if isinstance(value, str) and value.strip():
            role_values.append(value.strip().lower())
        elif isinstance(value, dict):
            for key in ("name", "role", "type", "slug"):
                nested = value.get(key)
                if isinstance(nested, str) and nested.strip():
                    role_values.append(nested.strip().lower())

    for key in ("role", "roleName", "type"):
        collect_role(item.get(key))

    user = item.get("user")
    if isinstance(user, dict):
        for key in ("role", "roleName", "type"):
            collect_role(user.get(key))

    if not role_values:
        # If the API payload does not expose a role in this shape, preserve the
        # previous inclusive behavior rather than accidentally dropping a real
        # translator from release credits.
        return True

    translation_markers = (
        "translator",
        "proofreader",
        "language coordinator",
        "coordinator",
    )
    excluded_markers = (
        "owner",
        "manager",
    )

    has_translation_role = any(
        any(marker in role for marker in translation_markers) for role in role_values
    )
    has_excluded_role = any(
        any(marker in role for marker in excluded_markers) for role in role_values
    )

    if has_translation_role:
        return True

    return not has_excluded_role


def fetch_contributors() -> list[str]:
    project_id = os.environ.get("CROWDIN_PROJECT_ID")
    token = os.environ.get("CROWDIN_PERSONAL_TOKEN")

    if not project_id or not token:
        message = "Crowdin credentials are missing; skipping Crowdin contributor lookup."
        if lookup_is_required():
            raise CrowdinLookupError(message)
        print(message, file=sys.stderr)
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
        except Exception as exc:
            message = "Crowdin contributor lookup failed; skipping Crowdin contributor credits."
            if lookup_is_required():
                raise CrowdinLookupError(message) from exc
            print(message, file=sys.stderr)
            return []

        items = payload.get("data") or payload.get("items") or []
        if not items:
            break

        for raw_item in items:
            if not isinstance(raw_item, dict):
                continue

            item = unwrap_item(raw_item)
            if not is_translation_contributor(item):
                continue

            name = extract_name(item)

            if name and name not in names:
                names.append(name)

        if len(items) < limit:
            break

        offset += limit

    return sorted(names, key=str.lower)


def main() -> int:
    try:
        for name in fetch_contributors():
            print(f"- {name}")
        return 0
    except CrowdinLookupError as exc:
        print(str(exc), file=sys.stderr)
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
