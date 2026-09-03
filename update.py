import json
import requests
import re
import os

COUNTRIES = ["bd", "in", "us", "gb"]

def fetch_playlist(country):
    url = f"https://raw.githubusercontent.com/iptv-org/iptv/master/streams/{country}.m3u"
    try:
        r = requests.get(url, timeout=15)
        return r.text if r.status_code == 200 else ""
    except:
        return ""

def parse_m3u(content):
    channels = {}
    lines = content.splitlines()
    for i, line in enumerate(lines):
        if line.startswith("#EXTINF:"):
            name_match = re.search(r",(.+)$", line)
            if name_match:
                name = name_match.group(1).strip()
                if i + 1 < len(lines) and not lines[i+1].startswith("#"):
                    channels[name.lower()] = lines[i+1].strip()
    return channels

def get_my_channel_names(json_path):
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    names = []
    for cat in data.get("categories", []):
        for ch in cat.get("channels", []):
            if ch.get("name"):
                names.append(ch["name"])
    return names

def update_playlist(playlist_path, public_channels, my_names):
    with open(playlist_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    new_lines = []
    updated = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        new_lines.append(line)

        if line.startswith("#EXTINF:"):
            name_match = re.search(r",(.+)$", line)
            if name_match:
                current_name = name_match.group(1).strip()
                # Check if this channel is in our "wanted" list
                is_wanted = any(
                    n.lower() in current_name.lower() or current_name.lower() in n.lower()
                    for n in my_names
                )

                if is_wanted and i + 1 < len(lines) and not lines[i+1].startswith("#"):
                    old_url = lines[i+1].strip()
                    new_url = None

                    # Exact match
                    if current_name.lower() in public_channels:
                        new_url = public_channels[current_name.lower()]
                    else:
                        # Fuzzy partial match
                        for pub_name, pub_url in public_channels.items():
                            if current_name.lower() in pub_name or pub_name in current_name.lower():
                                if pub_url != old_url:
                                    new_url = pub_url
                                    break

                    if new_url and new_url != old_url:
                        print(f"✅ Updated '{current_name}'")
                        new_lines.append(new_url + "\n")
                        updated += 1
                        i += 1  # skip the old URL line
                    else:
                        new_lines.append(lines[i + 1])
                        i += 1
        i += 1

    if updated:
        with open(playlist_path, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print(f"🎉 Updated {updated} channels.")
    else:
        print("ℹ️ No updates found.")

def main():
    if not os.path.exists("channels.json"):
        print("❌ channels.json not found.")
        return

    if not os.path.exists("playlist.m3u"):
        print("⚠️ playlist.m3u not found. Generating from channels.json...")
        with open("channels.json", "r", encoding="utf-8") as f:
            data = json.load(f)
        with open("playlist.m3u", "w", encoding="utf-8") as f:
            f.write("#EXTM3U\n")
            for cat in data.get("categories", []):
                for ch in cat.get("channels", []):
                    if ch.get("url") and ch.get("name"):
                        f.write(f'#EXTINF:-1 tvg-logo="{ch.get("logo", "")}",{ch["name"]}\n')
                        f.write(f'{ch["url"]}\n')
        print("✅ playlist.m3u created.")

    my_names = get_my_channel_names("channels.json")
    print(f"🎯 Tracking {len(my_names)} channels.")

    all_public = {}
    for country in COUNTRIES:
        print(f"📡 Fetching {country}...")
        content = fetch_playlist(country)
        if content:
            all_public.update(parse_m3u(content))

    print(f"🌐 Found {len(all_public)} public channels.")
    update_playlist("playlist.m3u", all_public, my_names)

if __name__ == "__main__":
    main()