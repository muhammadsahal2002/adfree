#!/usr/bin/env python3
"""
Generate NetMirror session token – outputs decoded t_hash_t (with colons)
Mobile-optimized for Android environment
"""

import re
import json
import time
import urllib.parse
import sys
import requests
import random
import os
from datetime import datetime

# Mobile-optimized headers
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
    "User-Agent": "okhttp/4.9.2",  # Mobile app user agent
    "Accept": "application/json, text/plain, */*",
    "Cache-Control": "no-cache",
    "Pragma": "no-cache",
    "Expires": "0",
    "Host": "mobiledetects.com",
    "Connection": "Keep-Alive",
    "Accept-Encoding": "gzip",
}

BASE_URL = "https://net52.cc"
TOKEN_FILE = "token.json"
LOG_FILE = "token_update.log"

def log_message(msg):
    """Write log message with timestamp"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    log_entry = f"[{timestamp}] {msg}"
    print(log_entry, flush=True)
    try:
        with open(LOG_FILE, "a") as f:
            f.write(log_entry + "\n")
    except:
        pass

def get_session_token():
    session = requests.Session()
    
    # Detect if running in mobile environment
    is_mobile = os.getenv("MOBILE_APP") == "app.netmirror.nmv2" or True
    log_message(f"Running in {'mobile' if is_mobile else 'desktop'} mode")

    log_message("Starting token generation...")

    # Step 1: Get initial page with mobile parameter
    try:
        resp = session.get(f"{BASE_URL}/mobile/home?app=1&m=1", headers=HEADERS_HOME, timeout=30)
        log_message(f"Homepage loaded: {resp.status_code}")
    except Exception as e:
        log_message(f"Failed to load homepage: {e}")
        raise

    addhash_encoded = session.cookies.get("addhash")
    if not addhash_encoded:
        match = re.search(r'data-addhash="([^"]+)"', resp.text)
        if match:
            addhash_encoded = match.group(1)
            log_message("addhash found in HTML")
        else:
            log_message("addhash not found in HTML")

    if not addhash_encoded:
        raise Exception("Could not obtain addhash")

    log_message(f"Got addhash: {addhash_encoded[:30]}...")

    # Step 2: Mobile device check (Android)
    try:
        session.get("https://mobiledetects.com/check.php", headers=HEADERS_MOBILE, timeout=10)
        log_message("Mobile check completed (Android)")
    except Exception as e:
        log_message(f"Mobile check failed: {e}")

    # Step 3: Visit userver endpoint with mobile parameters
    try:
        ad_url = f"https://userver.net52.cc/?hee5={addhash_encoded}&a=y&t={random.random()}&m=1&platform=android"
        session.get(ad_url, headers=HEADERS_HOME, timeout=10)
        log_message("Userver request sent with mobile params")
    except Exception as e:
        log_message(f"Userver request failed: {e}")

    # Step 4: Wait 20 seconds (mobile apps often need more time)
    log_message("Waiting 20 seconds for mobile token generation...")
    time.sleep(20)

    # Step 5: Verification
    addhash_decoded = urllib.parse.unquote(addhash_encoded)
    success = False

    for attempt in range(5):
        try:
            log_message(f"Verification attempt {attempt+1}/5...")
            verify_resp = session.post(
                f"{BASE_URL}/mobile/verify2.php",
                data={"verify": addhash_decoded, "mobile": "1"},
                headers=HEADERS_POST,
                timeout=10
            )

            log_message(f"Response: {verify_resp.text[:100]}")

            if verify_resp.text.strip() == "invalid id":
                log_message(f"Attempt {attempt+1}: Invalid ID, waiting...")
                time.sleep(2)
                continue

            try:
                vdata = verify_resp.json()
                if vdata.get("c") == "y":
                    success = True
                    log_message(f"Attempt {attempt+1}: Verification successful!")
                    break
                else:
                    log_message(f"Unexpected response: {vdata}")
            except json.JSONDecodeError:
                if "success" in verify_resp.text.lower() or "ok" in verify_resp.text.lower():
                    success = True
                    log_message("Verification successful (non-JSON)!")
                    break
                else:
                    log_message(f"Unexpected response: {verify_resp.text[:100]}")
        except Exception as e:
            log_message(f"Verification attempt {attempt+1} failed: {e}")
            time.sleep(2)

    if not success:
        raise Exception("Verification failed after 5 attempts")

    # Step 6: Get fresh cookies with mobile parameter
    try:
        session.get(f"{BASE_URL}/mobile/home?app=1&m=1&mobile=1", headers=HEADERS_HOME, timeout=10)
        log_message("Fresh mobile cookies obtained")
    except Exception as e:
        log_message(f"Failed to get fresh cookies: {e}")

    # Step 7: Extract and build token
    cookies = session.cookies.get_dict()
    log_message(f"Cookies: {list(cookies.keys())}")

    # Get addhash
    addhash_final = cookies.get("addhash", addhash_encoded)

    # Try to get t_hash_t, or build from addhash
    t_hash_t_encoded = cookies.get("t_hash_t")
    if not t_hash_t_encoded:
        t_hash_t_encoded = addhash_final
        if not t_hash_t_encoded.endswith("%3A%3Am"):
            decoded = urllib.parse.unquote(t_hash_t_encoded)
            if not decoded.endswith("::m"):
                decoded += "::m"
            t_hash_t_encoded = urllib.parse.quote(decoded)
        log_message("t_hash_t created from addhash")

    # Decode token
    t_hash_t_raw = urllib.parse.unquote(t_hash_t_encoded)
    
    # Ensure mobile suffix
    if not t_hash_t_raw.endswith("::m"):
        t_hash_t_raw += "::m"
    
    # Force ::db instead of ::su (or any other suffix)
    import re
    t_hash_t_raw = re.sub(r'::[^:]+::m$', '::db::m', t_hash_t_raw)
    
    # Re-encode
    t_hash_t_encoded = urllib.parse.quote(t_hash_t_raw)
    
    log_message(f"Mobile token generated: {t_hash_t_raw[:50]}...")

    # Return only the three required fields
    return {
        "token": t_hash_t_raw,
        "t_hash_t": t_hash_t_encoded,
        "addhash": addhash_final
    }

if __name__ == "__main__":
    try:
        token = get_session_token()

        # Write to token.json
        with open(TOKEN_FILE, "w") as f:
            json.dump(token, f, indent=2)

        log_message(f"✅ Mobile token saved to {TOKEN_FILE}")
        log_message(f"Token: {token['token'][:50]}...")
        
        # Print the JSON for verification
        print("\nGenerated Mobile Token JSON:")
        print(json.dumps(token, indent=2))

    except Exception as e:
        error_msg = f"❌ Error: {e}"
        log_message(error_msg)
        print(error_msg, file=sys.stderr)
        sys.exit(1)