import hashlib
import secrets
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException

from auth import verify_admin
from database import get_connection, utc_now
from models import DeviceRegisterRequest, DeviceRegisterResponse, DeviceSummary
from services.device_service import fetch_devices

router = APIRouter(prefix="/sms/api/devices", tags=["devices"])


def verify_device(device_id: str, api_key: str) -> dict:
    with get_connection() as conn:
        row = conn.execute(
            "SELECT * FROM devices WHERE device_id = ? AND api_key = ?",
            (device_id, api_key),
        ).fetchone()

    if not row:
        raise HTTPException(status_code=401, detail="Invalid device credentials")

    return dict(row)


@router.post("/register", response_model=DeviceRegisterResponse)
def register_device(payload: DeviceRegisterRequest) -> DeviceRegisterResponse:
    device_id = str(uuid.uuid4())
    api_key = secrets.token_urlsafe(24)
    now = utc_now()

    with get_connection() as conn:
        conn.execute(
            """
            INSERT INTO devices (device_id, device_name, phone_number, api_key, last_seen_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (device_id, payload.device_name, payload.phone_number, api_key, now, now),
        )

    return DeviceRegisterResponse(
        device_id=device_id,
        api_key=api_key,
        device_name=payload.device_name,
        phone_number=payload.phone_number,
    )


@router.get("", response_model=list[DeviceSummary])
def list_devices(_: None = Depends(verify_admin)) -> list[DeviceSummary]:
    return fetch_devices()


def build_message_hash(device_id: str, sender: str, body: str, received_at: str) -> str:
    raw = f"{device_id}|{sender}|{body}|{received_at}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()
