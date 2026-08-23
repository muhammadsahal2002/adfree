#!/usr/bin/env python3
"""
Generate NetMirror session token – outputs decoded t_hash_t (with colons)
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

def get_session_token():
    session = requests.Session()
    
    # Step 1: Get initial page and addhash
    resp = session.get(f"{BASE_URL}/mobile/home?app=1", headers=HEADERS_HOME)
    
    addhash_encoded = session.cookies.get("addhash")
    if not addhash_encoded:
        match = re.search(r'data-addhash="([^"]+)"', resp.text)
        if match:
            addhash_encoded = match.group(1)
    
    if not addhash_encoded:
        raise Exception("Could not obtain addhash")
    
    # Step 2: Mobile device check
    try:
        session.get("https://mobiledetects.com/check.php", headers=HEADERS_MOBILE, timeout=10)
    except:
        pass
    
    # Step 3: Visit userver endpoint with mobile parameter
    ad_url = f"https://userver.net52.cc/?hee5={addhash_encoded}&a=y&t={random.random()}&m=1"
    session.get(ad_url, headers=HEADERS_HOME)
    
    # Step 4: Wait 20 seconds
    time.sleep(20)
    
    # Step 5: Verification
    addhash_decoded = urllib.parse.unquote(addhash_encoded)
    success = False
    
    for _ in range(5):
        verify_resp = session.post(
            f"{BASE_URL}/mobile/verify2.php",
            data={"verify": addhash_decoded},
            headers=HEADERS_POST
        )
        
        if verify_resp.text.strip() == "invalid id":
            time.sleep(2)
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
    
    # Step 6: Get fresh cookies with mobile parameter
    session.get(f"{BASE_URL}/mobile/home?app=1&m=1", headers=HEADERS_HOME)
    
    # Step 7: Extract and build token
    cookies = session.cookies.get_dict()
    
    # Get addhash
    addhash_final = cookies.get("addhash", addhash_encoded)
    
    # Try to get t_hash_t, or build from addhash
    t_hash_t_encoded = cookies.get("t_hash_t")
    if not t_hash_t_encoded:
        # Use addhash and ensure mobile suffix
        t_hash_t_encoded = addhash_final
        if not t_hash_t_encoded.endswith("%3A%3Am"):
            decoded = urllib.parse.unquote(t_hash_t_encoded)
            if not decoded.endswith("::m"):
                decoded += "::m"
            t_hash_t_encoded = urllib.parse.quote(decoded)
    
    # Decode token
    t_hash_t_raw = urllib.parse.unquote(t_hash_t_encoded)
    if not t_hash_t_raw.endswith("::m"):
        t_hash_t_raw += "::m"
        t_hash_t_encoded = urllib.parse.quote(t_hash_t_raw)
    
    # Return only the three required fields
    return {
        "token": t_hash_t_raw,
        "t_hash_t": t_hash_t_encoded,
        "addhash": addhash_final
    }

if __name__ == "__main__":
    try:
        token = get_session_token()
        print(json.dumps(token, indent=2))
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)