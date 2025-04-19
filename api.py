from flask import Flask, Response
import requests
import random
import threading
import time

app = Flask(__name__)

# Cập nhật mỗi 5 phút (300 giây)
UPDATE_INTERVAL = 60

# Danh sách URL proxy
text_urls_all = [
    "https://calce.space/proxy.txt",
    "https://api.nminhniee.sbs/Nm.php",
    "http://36.50.134.20:3000/download/http",
    "http://36.50.134.20:3000/download/free-proxy-list",
    "http://36.50.134.20:3000/download/proxy-list-download",
    "http://36.50.134.20:3000/download/geonode",
    "http://36.50.134.20:3000/download/spysme",
    "http://36.50.134.20:3000/download/proxyscrape",
    "http://36.50.134.20:3000/download/vn"
]

text_urls_vn = [
    "https://calce.space/proxy.txt",
    "https://api.nminhniee.sbs/Nm.php",
    "http://36.50.134.20:3000/download/vn"
]

# Cache proxy
proxy_cache_all = []
proxy_cache_vn = []

# Hàm tải proxy từ các URL text
def fetch_text_proxies(urls):
    all_lines = set()
    for url in urls:
        try:
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            lines = response.text.strip().splitlines()
            all_lines.update(line.strip() for line in lines if line.strip())
        except Exception as e:
            print(f"[!] Lỗi khi tải {url}: {e}")
    return list(all_lines)

# Ghi proxy ra file
def save_to_file(filename, data):
    try:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write("\n".join(data))
        print(f"[+] Đã ghi file: {filename} ({len(data)} proxy)")
    except Exception as e:
        print(f"[!] Lỗi ghi file {filename}: {e}")

# Cập nhật định kỳ
def update_proxies_periodically():
    global proxy_cache_all, proxy_cache_vn
    while True:
        print("[*] Đang cập nhật proxy...")
        proxy_cache_all = fetch_text_proxies(text_urls_all)
        proxy_cache_vn = fetch_text_proxies(text_urls_vn)

        # Ghi ra file
        save_to_file("all_proxies.txt", proxy_cache_all)
        save_to_file("vn_proxies.txt", proxy_cache_vn)

        print(f"[✓] Hoàn tất cập nhật: {len(proxy_cache_all)} (ALL), {len(proxy_cache_vn)} (VN)")
        time.sleep(UPDATE_INTERVAL)

# Endpoint lấy tất cả proxy
@app.route('/api/prx', methods=['GET'])
def get_proxies():
    shuffled = proxy_cache_all[:]
    random.shuffle(shuffled)
    return Response("\n".join(shuffled), mimetype="text/plain")

# Endpoint lấy proxy Việt Nam
@app.route('/api/vn', methods=['GET'])
def get_vn_proxies():
    shuffled = proxy_cache_vn[:]
    random.shuffle(shuffled)
    return Response("\n".join(shuffled), mimetype="text/plain")

# Endpoint xem thống kê
@app.route('/api/ip', methods=['GET'])
def get_stats():
    return {
        "total_all": len(proxy_cache_all),
        "total_vn": len(proxy_cache_vn),
        "update_interval_seconds": UPDATE_INTERVAL
    }

# Chạy luồng cập nhật định kỳ
threading.Thread(target=update_proxies_periodically, daemon=True).start()

# Chạy server
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=1110)
