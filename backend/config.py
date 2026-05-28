import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
DATA_DIR.mkdir(exist_ok=True)

DATABASE_PATH = Path(os.getenv("SMS_DATABASE_PATH", str(DATA_DIR / "sms.db")))
ADMIN_TOKEN = os.getenv("SMS_ADMIN_TOKEN", "admin123")
SMS_PREFIX = os.getenv("SMS_PREFIX", "/sms")
HOST = os.getenv("SMS_HOST", "0.0.0.0")
PORT = int(os.getenv("SMS_PORT", "8080"))
