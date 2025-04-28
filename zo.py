from flask import Flask
from apscheduler.schedulers.background import BackgroundScheduler
import subprocess
import requests
import time

app = Flask(__name__)

# Hàm chạy file z.py
def run_z_script():
    try:
        # Chạy file z.py
        subprocess.run(["python", "z.py"], check=True)
        print("File z.py đã được chạy thành công!")
    except subprocess.CalledProcessError as e:
        print(f"Error khi chạy file z.py: {e}")

# Hàm upload file all_proxies.txt
def upload_proxies():
    try:
        with open("all_proxies.txt", "rb") as file:
            response = requests.post("http://localhost/api/all", files={"file": file})
            if response.status_code == 200:
                print("Upload file all_proxies.txt thành công!")
            else:
                print(f"Lỗi khi upload: {response.status_code}")
    except Exception as e:
        print(f"Error khi upload file: {e}")

# Tạo một scheduler để chạy các task định kỳ
def start_scheduler():
    scheduler = BackgroundScheduler()
    # Thực thi run_z_script và upload_proxies mỗi 8 phút (480 giây)
    scheduler.add_job(run_z_script, 'interval', minutes=8)
    scheduler.add_job(upload_proxies, 'interval', minutes=8)
    scheduler.start()

# Khởi chạy scheduler khi bắt đầu ứng dụng Flask
@app.before_first_request
def initialize():
    start_scheduler()

@app.route('/')
def index():
    return "Flask server is running and tasks are scheduled!"

if __name__ == '__main__':
    app.run(debug=True, use_reloader=False)
