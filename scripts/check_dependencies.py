#!/usr/bin/env python3
import urllib.request
import json

DEPENDENCIES = {
    "create-electro-energetics": "Create: Electro Energetics",
    "power-grid": "Create: Power Grid",
    "create": "Create"
}

def get_latest_version(slug):
    url = f"https://api.modrinth.com/v2/project/{slug}/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22neoforge%22%5D"
    req = urllib.request.Request(url, headers={"User-Agent": "CreateWireRider-Checker/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode())
            if data and len(data) > 0:
                latest = data[0]
                return {
                    "version_number": latest.get("version_number"),
                    "name": latest.get("name"),
                    "date_published": latest.get("date_published", "")[:10]
                }
    except Exception as e:
        return {"error": str(e)}
    return None

def main():
    print("Checking dependency updates on Modrinth (Minecraft 1.21.1 / NeoForge)...")
    print("-" * 65)
    for slug, name in DEPENDENCIES.items():
        res = get_latest_version(slug)
        if not res:
            print(f"[{name}] No versions found for 1.21.1 NeoForge")
        elif "error" in res:
            print(f"[{name}] Failed to fetch: {res['error']}")
        else:
            print(f"[{name}] Latest: {res['version_number']} ({res['name']}) - {res['date_published']}")
    print("-" * 65)

if __name__ == "__main__":
    main()
