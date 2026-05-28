import hashlib
import secrets
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException

from auth import verify_admin
from database import get_connection, utc_now
from models import DeviceRegisterRequest, DeviceRegisterResponse, DeviceSummary, DeviceUpdateRequest
from services.device_service import fetch_devices, update_device

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


@router.patch("/{device_id}", response_model=DeviceSummary)
def patch_device(
    device_id: str,
    payload: DeviceUpdateRequest,
    _: None = Depends(verify_admin),
) -> DeviceSummary:
    updated = update_device(
        device_id=device_id,
        device_name=payload.device_name,
        phone_number=payload.phone_number,
    )
    if not updated:
        raise HTTPException(status_code=404, detail="Device not found")

    return updated


@router.delete("/{device_id}")
def delete_device(device_id: str, _: None = Depends(verify_admin)) -> dict[str, bool]:
    with get_connection() as conn:
        existing = conn.execute(
            "SELECT device_id FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="Device not found")

        conn.execute("DELETE FROM sms_messages WHERE device_id = ?", (device_id,))
        conn.execute("DELETE FROM devices WHERE device_id = ?", (device_id,))

    return {"ok": True}


def build_message_hash(device_id: str, sender: str, body: str, received_at: str | None = None) -> str:
    raw = f"{device_id}|{sender.strip()}|{body.strip()}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()
