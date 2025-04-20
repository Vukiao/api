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
    "https://raw.githubusercontent.com/hookzof/socks5_list/refs/heads/master/proxy.txt",
   "https://freeproxyupdate.com/files/txt/afghanistan.txt",
    "https://freeproxyupdate.com/files/txt/albania.txt",
    "https://freeproxyupdate.com/files/txt/algeria.txt",
    "https://freeproxyupdate.com/files/txt/andorra.txt",
    "https://freeproxyupdate.com/files/txt/angola.txt",
    "https://freeproxyupdate.com/files/txt/argentina.txt",
    "https://freeproxyupdate.com/files/txt/armenia.txt",
    "https://freeproxyupdate.com/files/txt/australia.txt",
    "https://freeproxyupdate.com/files/txt/austria.txt",
    "https://freeproxyupdate.com/files/txt/azerbaijan.txt",
    "https://freeproxyupdate.com/files/txt/bahamas.txt",
    "https://freeproxyupdate.com/files/txt/bahrain.txt",
    "https://freeproxyupdate.com/files/txt/bangladesh.txt",
    "https://freeproxyupdate.com/files/txt/barbados.txt",
    "https://freeproxyupdate.com/files/txt/belarus.txt",
    "https://freeproxyupdate.com/files/txt/belgium.txt",
    "https://freeproxyupdate.com/files/txt/belize.txt",
    "https://freeproxyupdate.com/files/txt/benin.txt",
    "https://freeproxyupdate.com/files/txt/bhutan.txt",
    "https://freeproxyupdate.com/files/txt/bolivia.txt",
    "https://freeproxyupdate.com/files/txt/botswana.txt",
    "https://freeproxyupdate.com/files/txt/brazil.txt",
    "https://freeproxyupdate.com/files/txt/brunei.txt",
    "https://freeproxyupdate.com/files/txt/bulgaria.txt",
    "https://freeproxyupdate.com/files/txt/burkina faso.txt",
    "https://freeproxyupdate.com/files/txt/burundi.txt",
    "https://freeproxyupdate.com/files/txt/cabo verde.txt",
    "https://freeproxyupdate.com/files/txt/cambodia.txt",
    "https://freeproxyupdate.com/files/txt/cameroon.txt",
    "https://freeproxyupdate.com/files/txt/canada.txt",
    "https://freeproxyupdate.com/files/txt/chad.txt",
    "https://freeproxyupdate.com/files/txt/chile.txt",
    "https://freeproxyupdate.com/files/txt/china.txt",
    "https://freeproxyupdate.com/files/txt/colombia.txt",
    "https://freeproxyupdate.com/files/txt/comoros.txt",
    "https://freeproxyupdate.com/files/txt/congo.txt",
    "https://freeproxyupdate.com/files/txt/congo-dr.txt",
    "https://freeproxyupdate.com/files/txt/costa rica.txt",
    "https://freeproxyupdate.com/files/txt/croatia.txt",
    "https://freeproxyupdate.com/files/txt/cuba.txt",
    "https://freeproxyupdate.com/files/txt/cyprus.txt",
    "https://freeproxyupdate.com/files/txt/czech-republic.txt",
    "https://freeproxyupdate.com/files/txt/denmark.txt",
    "https://freeproxyupdate.com/files/txt/djibouti.txt",
    "https://freeproxyupdate.com/files/txt/dominica.txt",
    "https://freeproxyupdate.com/files/txt/dominican-republic.txt",
    "https://freeproxyupdate.com/files/txt/ecuador.txt",
    "https://freeproxyupdate.com/files/txt/egypt.txt",
    "https://freeproxyupdate.com/files/txt/el-salvador.txt",
    "https://freeproxyupdate.com/files/txt/equatorial-guinea.txt",
    "https://freeproxyupdate.com/files/txt/eritrea.txt",
    "https://freeproxyupdate.com/files/txt/estonia.txt",
    "https://freeproxyupdate.com/files/txt/eswatini.txt",
    "https://freeproxyupdate.com/files/txt/ethiopia.txt",
    "https://freeproxyupdate.com/files/txt/fiji.txt",
    "https://freeproxyupdate.com/files/txt/finland.txt",
    "https://freeproxyupdate.com/files/txt/france.txt",
    "https://freeproxyupdate.com/files/txt/gabon.txt",
    "https://freeproxyupdate.com/files/txt/gambia.txt",
    "https://freeproxyupdate.com/files/txt/georgia.txt",
    "https://freeproxyupdate.com/files/txt/germany.txt",
    "https://freeproxyupdate.com/files/txt/ghana.txt",
    "https://freeproxyupdate.com/files/txt/greece.txt",
    "https://freeproxyupdate.com/files/txt/grenada.txt",
    "https://freeproxyupdate.com/files/txt/guatemala.txt",
    "https://freeproxyupdate.com/files/txt/guinea.txt",
    "https://freeproxyupdate.com/files/txt/guinea-bissau.txt",
    "https://freeproxyupdate.com/files/txt/guyana.txt",
    "https://freeproxyupdate.com/files/txt/haiti.txt",
    "https://freeproxyupdate.com/files/txt/honduras.txt",
    "https://freeproxyupdate.com/files/txt/hungary.txt",
    "https://freeproxyupdate.com/files/txt/iceland.txt",
    "https://freeproxyupdate.com/files/txt/indonesia.txt",
    "https://freeproxyupdate.com/files/txt/iran.txt",
    "https://freeproxyupdate.com/files/txt/iraq.txt",
    "https://freeproxyupdate.com/files/txt/ireland.txt",
    "https://freeproxyupdate.com/files/txt/israel.txt",
    "https://freeproxyupdate.com/files/txt/italy.txt",
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
