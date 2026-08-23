#!/usr/bin/env python3
"""
Generate NetMirror session token (cookies) without a content ID.
Outputs JSON with t_hash and t_hash_t.
"""

import re
import json
import time
import urllib.parse
import sys
import requests
import random

HEADERS_HOME = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 12; SM-M025F Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/151.0.7922.85 Mobile Safari/537.36 /OS.Gatu v3.1",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
    "Accept-Language": "en-GB,en-US;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-User": "?1",
    "Sec-Fetch-Dest": "document",
    "sec-ch-ua": '"Not=A?Brand";v="99", "Android WebView";v="151", "Chromium";v="151"',
    "sec-ch-ua-mobile": "?1",
    "sec-ch-ua-platform": '"Android"',
}

HEADERS_AJAX = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 12; SM-M025F Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/151.0.7922.85 Mobile Safari/537.36 /OS.Gatu v3.1",
    "Accept": "*/*",
    "Accept-Language": "en-GB,en-US;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "X-Requested-With": "app.netmirror.nmv2",
    "Referer": "https://net52.cc/mobile/home?app=1",
    "Sec-Fetch-Site": "same-origin",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Dest": "empty",
    "sec-ch-ua": '"Not=A?Brand";v="99", "Android WebView";v="151", "Chromium";v="151"',
    "sec-ch-ua-mobile": "?1",
    "sec-ch-ua-platform": '"Android"',
}

HEADERS_POST = HEADERS_AJAX.copy()
HEADERS_POST["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
HEADERS_POST["Origin"] = "https://net52.cc"

HEADERS_MOBILE = {
    "User-Agent": "okhttp/4.9.2",
    "Accept": "application/json, text/plain, */*",
    "Cache-Control": "no-cache",
    "Pragma": "no-cache",
    "Expires": "0",
    "Host": "mobiledetects.com",
    "Connection": "Keep-Alive",
    "Accept-Encoding": "gzip",
}

BASE_URL = "https://net52.cc"

def get_timestamp():
    return int(time.time())

def get_session_token():
    session = requests.Session()

    # 1. Get addhash
    resp = session.get(f"{BASE_URL}/mobile/home?app=1", headers=HEADERS_HOME)
    addhash_encoded = session.cookies.get("addhash")
    if not addhash_encoded:
        match = re.search(r'data-addhash="([^"]+)"', resp.text)
        if match:
            addhash_encoded = match.group(1)
    if not addhash_encoded:
        raise Exception("Could not obtain addhash")

    # 2. Device check (skip if fails)
    try:
        session.get("https://mobiledetects.com/check.php", headers=HEADERS_MOBILE, timeout=10)
    except:
        pass

    # 3. Visit ad page
    ad_url = f"https://userver.net52.cc/?hee5={addhash_encoded}&a=y&t={random.random()}"
    session.get(ad_url, headers=HEADERS_HOME)

    # 4. Wait 30 seconds
    time.sleep(30)

    # 5. Verify with decoded addhash
    addhash_decoded = urllib.parse.unquote(addhash_encoded)
    max_attempts = 5
    success = False
    for attempt in range(max_attempts):
        verify_resp = session.post(
            f"{BASE_URL}/mobile/verify2.php",
            data={"verify": addhash_decoded},
            headers=HEADERS_POST
        )
        if verify_resp.text.strip() == "invalid id":
            time.sleep(5)
            continue
        try:
            vdata = verify_resp.json()
            if vdata.get("c") == "y":
                success = True
                break
        except:
            pass
    if not success:
        raise Exception("Verification failed")

    # 6. Get t_hash_t by visiting home again
    session.get(f"{BASE_URL}/mobile/home?app=1", headers=HEADERS_HOME)

    # Extract cookies
    cookies = session.cookies.get_dict()
    t_hash = cookies.get("t_hash")
    t_hash_t = cookies.get("t_hash_t")
    if not t_hash_t:
        raise Exception("t_hash_t not set")

    # Return as dict
    return {
        "t_hash": t_hash,
        "t_hash_t": t_hash_t,
        "addhash": addhash_encoded
    }

if __name__ == "__main__":
    try:
        token = get_session_token()
        # Output as JSON
        print(json.dumps(token))
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)