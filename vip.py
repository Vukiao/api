import requests
import re
import math

# ========== Cấu hình ==========
base_url = 'https://proxylist.geonode.com/api/proxy-list'
limit = 500  # Số proxy tối đa mỗi lần tải (có thể điều chỉnh)
output_file = 'all_proxies.txt'  # Tên file lưu proxy

# Danh sách các URL chứa proxy dạng text
urls = [
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
    "https://freeproxyupdate.com/files/txt/italy.txt"
]

# Hàm để lấy proxy từ URL text
def get_proxies_from_url(url):
    response = requests.get(url)
    proxies = []
    if response.status_code == 200:
        text = response.text
        # Dùng regex để tìm các dòng có định dạng IP:PORT
        pattern = re.compile(r'^(\d{1,3}\.){3}\d{1,3}:\d+$')
        for line in text.splitlines():
            line = line.strip()
            if pattern.match(line):
                proxies.append(line)
    else:
        print(f"Không thể tải từ URL: {url}")
    return proxies

# Hàm để lấy proxy từ API JSON
def get_proxies_from_api(page=1):
    params = {'limit': limit, 'page': page, 'sort_by': 'lastChecked', 'sort_type': 'desc'}
    response = requests.get(base_url, params=params)
    proxies = []
    if response.status_code == 200:
        data = response.json().get('data', [])
        for item in data:
            ip = item.get('ip')
            port = item.get('port')
            if ip and port:
                proxies.append(f"{ip}:{port}")
    else:
        print(f"Không thể tải từ API page {page}")
    return proxies

# Lấy số lượng proxy từ API
def get_total_proxy_count():
    try:
        response = requests.get(base_url, params={'limit': 1}, timeout=10)
        response.raise_for_status()
        total = response.json().get('total', 0)
        return total
    except Exception as e:
        print(f"Lỗi khi lấy tổng số proxy từ API: {e}")
        return 0

# Lấy proxy từ API theo số trang
def get_proxies_from_api_all():
    total = get_total_proxy_count()
    if total > 0:
        total_pages = math.ceil(total / limit)
        all_proxies = set()
        for page in range(1, total_pages + 1):
            print(f"Tải page {page}/{total_pages}...")
            proxies = get_proxies_from_api(page)
            all_proxies.update(proxies)
        return list(all_proxies)
    return []

# Kết hợp tất cả proxy từ cả API và URLs text
def get_all_proxies():
    all_proxies = set()

    # Lấy proxy từ API
    print("Đang lấy proxy từ API...")
    proxies_from_api = get_proxies_from_api_all()
    all_proxies.update(proxies_from_api)

    # Lấy proxy từ các URL
    print("Đang lấy proxy từ các URL text...")
    for url in urls:
        proxies_from_url = get_proxies_from_url(url)
        all_proxies.update(proxies_from_url)

    return list(all_proxies)

# Lưu proxy vào file
def save_proxies_to_file(proxies):
    with open(output_file, 'w') as file:
        for proxy in proxies:
            file.write(proxy + '\n')
    print(f"Đã lưu {len(proxies)} proxy vào {output_file}")

# Thực thi toàn bộ quá trình
all_proxies = get_all_proxies()
save_proxies_to_file(all_proxies)
