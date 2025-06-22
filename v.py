import requests
import random

# Danh sách các URL chứa proxy a
urls = [
    "https://api.nminhniee.sbs/Conc.php",
    "http://36.50.134.20:3000/download/vn.txt"
]

# Tên file đầu ra
output_file = "vn.txt"

# Dùng set để tự động lọc trùng
unique_proxies = set()

for url in urls:
    try:
        print(f"▶ Đang tải từ: {url}")
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        proxies = response.text.strip().splitlines()
        
        for proxy in proxies:
            clean = proxy.strip()
            if clean:
                unique_proxies.add(clean)

    except Exception as e:
        print(f"❌ Lỗi khi tải từ {url}: {e}")

# Chuyển set thành list và xáo trộn ngẫu nhiên
proxy_list = list(unique_proxies)
random.shuffle(proxy_list)

# Ghi ra file sau khi đã lọc trùng và xáo trộn
with open(output_file, "w", encoding="utf-8") as f:
    for proxy in proxy_list:
        f.write(proxy + "\n")

print(f"✅ Đã lưu {len(proxy_list)} proxy vào {output_file} (đã lọc trùng và xáo trộn)")
