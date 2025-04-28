from flask import Flask
from apscheduler.schedulers.background import BackgroundScheduler
import subprocess
import requests
import os

app = Flask(__name__)

# Hàm chạy file z.py
def run_z_script():
    try:
        # Kiểm tra xem file z.py có tồn tại không
        if os.path.exists("z.py"):
            subprocess.run(["python3", "z.py"], check=True)
            print("File z.py đã được chạy thành công!")
        else:
            print("Không tìm thấy file z.py")
    except subprocess.CalledProcessError as e:
        print(f"Error khi chạy file z.py: {e}")

# Hàm upload file all_proxies.txt
def upload_proxies():
    try:
        if os.path.exists("all_proxies.txt"):
            with open("all_proxies.txt", "rb") as file:
                response = requests.post("http://localhost:1110/api/all", files={"file": file})
                if response.status_code == 200:
                    print("Upload file all_proxies.txt thành công!")
                else:
                    print(f"Lỗi khi upload: {response.status_code}")
        else:
            print("Không tìm thấy file all_proxies.txt")
    except Exception as e:
        print(f"Error khi upload file: {e}")

# Tạo một scheduler để chạy các task định kỳ
def start_scheduler():
    scheduler = BackgroundScheduler()
    # Thực thi run_z_script và upload_proxies mỗi 8 phút (480 giây)
    scheduler.add_job(run_z_script, 'interval', minutes=8)
    scheduler.add_job(upload_proxies, 'interval', minutes=8)
    scheduler.start()

# Khởi chạy scheduler và Flask app
if __name__ == '__main__':
    start_scheduler()  # Bắt đầu scheduler trước khi chạy Flask app
    app.run(debug=True, port=1110, use_reloader=False)  # Chạy trên port 5000
