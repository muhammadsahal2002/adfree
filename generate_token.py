#!/usr/bin/env python3
"""
NetMirror token generator – full flow with ad simulation
"""

import re
import json
import time
import urllib.parse
import sys
import requests
import random
import base64
from datetime import datetime

# ---------- Headers (matching your captured requests) ----------
HEADERS_HOME = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 17; SM-S928B Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.7922.139 Mobile Safari/537.36",
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
    "User-Agent": "Mozilla/5.0 (Linux; Android 17; SM-S928B Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.7922.139 Mobile Safari/537.36",
    "Accept": "*/*",
    "Accept-Language": "en-GB,en-US;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "X-Requested-With": "XMLHttpRequest",          # Important for verify
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

def log(msg):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] {msg}", flush=True)

def b64_decode(b64):
    """Decode base64 string (with possible padding issues)"""
    try:
        return base64.b64decode(b64 + "==").decode('utf-8')
    except:
        return None

def extract_addhash_from_html(html):
    """Try multiple patterns to find addhash in HTML"""
    patterns = [
        r'data-addhash="([^"]+)"',
        r'addhash\s*=\s*"([^"]+)"',
        r'"addhash":"([^"]+)"',
        r"var\s+addhash\s*=\s*'([^']+)'",
        r"addhash=([^&\s'\"]+)",
        # fallback: find a string like 32hex::32hex::digits::db
        r'([a-f0-9]{32}::[a-f0-9]{32}::[0-9]+::[a-z]+)'
    ]
    for pat in patterns:
        match = re.search(pat, html)
        if match:
            return match.group(1)
    return None

def get_session_token():
    session = requests.Session()

    # Step 1: Get home page and extract addhash
    log("Fetching home page...")
    resp = session.get(f"{BASE_URL}/mobile/home?app=1", headers=HEADERS_HOME, timeout=30)
    if resp.status_code != 200:
        raise Exception(f"Home page returned {resp.status_code}")

    # Try to get addhash from cookies first
    addhash = session.cookies.get("addhash")
    if addhash:
        log("✅ addhash found in cookies")
    else:
        # Extract from HTML
        addhash = extract_addhash_from_html(resp.text)
        if addhash:
            log("✅ addhash found in HTML")
        else:
            # Save HTML for debugging
            with open("debug_home.html", "w") as f:
                f.write(resp.text)
            raise Exception("Could not extract addhash from home page. See debug_home.html")

    # Step 2: Mobile detect – get ad URLs
    log("Checking mobile detect...")
    try:
        mob_resp = session.get("https://mobiledetects.com/check.php", headers=HEADERS_MOBILE, timeout=10)
        mob_data = mob_resp.json()
        stape_b64 = mob_data.get("stape", "")
        u_list_b64 = mob_data.get("u", [])
        stape_url = b64_decode(stape_b64) if stape_b64 else None
        ad_urls = [b64_decode(u) for u in u_list_b64 if b64_decode(u)]
        log(f"Stape URL: {stape_url}")
        log(f"Ad URLs: {ad_urls}")
    except Exception as e:
        log(f"Mobile detect failed: {e}")
        stape_url = None
        ad_urls = []

    # Step 3: Visit userver with addhash
    log("Visiting userver...")
    ad_url = f"https://userver.net52.cc/?hee5={addhash}&a=y&t={random.random()}&m=1"
    session.get(ad_url, headers=HEADERS_HOME, timeout=10)

    # Step 4: Wait 20 seconds
    log("Waiting 20 seconds...")
    time.sleep(20)

    # Step 5: Verification (with retry and ad clicks)
    success = False
    addhash_decoded = urllib.parse.unquote(addhash)
    for attempt in range(5):
        log(f"Verification attempt {attempt+1}/5...")
        verify_resp = session.post(
            f"{BASE_URL}/mobile/verify2.php",
            data={"verify": addhash_decoded},
            headers=HEADERS_POST,
            timeout=10
        )
        try:
            vdata = verify_resp.json()
            log(f"Verify response: {vdata}")
            if vdata.get("c") == "y":
                success = True
                log("✅ Verification successful!")
                break
            elif vdata.get("c") == "n" and "ads click" in vdata.get("statusup", ""):
                log("⚠️ Ad click required – visiting ad URLs...")
                # Visit the stape URL and ad URLs
                if stape_url:
                    session.get(stape_url, headers=HEADERS_HOME, timeout=10)
                    log(f"Visited stape: {stape_url}")
                for ad in ad_urls[:3]:  # visit first few ad URLs
                    try:
                        session.get(ad, headers=HEADERS_HOME, timeout=10)
                        log(f"Visited ad: {ad}")
                    except:
                        pass
                time.sleep(5)  # allow ads to register
            else:
                log("Unexpected verify response, retrying...")
        except Exception as e:
            log(f"Verify error: {e}")
        time.sleep(2)

    if not success:
        raise Exception("Verification failed after 5 attempts (ad click may have failed)")

    # Step 6: Get fresh cookies
    session.get(f"{BASE_URL}/mobile/home?app=1&m=1", headers=HEADERS_HOME, timeout=10)
    cookies = session.cookies.get_dict()
    log(f"Cookies: {list(cookies.keys())}")

    # Step 7: Build final token
    addhash_final = cookies.get("addhash", addhash)
    t_hash_t_encoded = cookies.get("t_hash_t")
    if not t_hash_t_encoded:
        t_hash_t_encoded = addhash_final
        if not t_hash_t_encoded.endswith("%3A%3Am"):
            decoded = urllib.parse.unquote(t_hash_t_encoded)
            if not decoded.endswith("::m"):
                decoded += "::m"
            t_hash_t_encoded = urllib.parse.quote(decoded)

    t_hash_t_raw = urllib.parse.unquote(t_hash_t_encoded)
    if not t_hash_t_raw.endswith("::m"):
        t_hash_t_raw += "::m"
    # Force ::db (or keep as is)
    t_hash_t_raw = re.sub(r'::[^:]+::m$', '::db::m', t_hash_t_raw)
    t_hash_t_encoded = urllib.parse.quote(t_hash_t_raw)

    return {
        "token": t_hash_t_raw,
        "t_hash_t": t_hash_t_encoded,
        "addhash": addhash_final
    }

if __name__ == "__main__":
    try:
        token = get_session_token()
        with open("token.json", "w") as f:
            json.dump(token, f, indent=2)
        print(json.dumps(token, indent=2))
    except Exception as e:
        print(f"❌ Error: {e}", file=sys.stderr)
        sys.exit(1)