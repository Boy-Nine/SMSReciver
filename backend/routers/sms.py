from fastapi import APIRouter, Depends, Header, Query

from auth import verify_admin
from database import get_connection, utc_now
from models import SmsInboundRequest, SmsInboundResponse, SmsListResponse
from routers.devices import build_message_hash, verify_device
from services.code_extractor import extract_verification_code
from services.sms_service import fetch_sms_messages

router = APIRouter(prefix="/sms/api/sms", tags=["sms"])


@router.post("/inbound", response_model=SmsInboundResponse)
def inbound_sms(
    payload: SmsInboundRequest,
    x_device_id: str = Header(alias="X-Device-Id"),
    x_api_key: str = Header(alias="X-Api-Key"),
) -> SmsInboundResponse:
    verify_device(x_device_id, x_api_key)

    message_hash = build_message_hash(
        x_device_id,
        payload.sender,
        payload.body,
        payload.received_at,
    )
    verification_code = extract_verification_code(payload.body)
    now = utc_now()

    with get_connection() as conn:
        existing = conn.execute(
            "SELECT id, verification_code FROM sms_messages WHERE message_hash = ?",
            (message_hash,),
        ).fetchone()
        if existing:
            conn.execute(
                "UPDATE devices SET last_seen_at = ?, phone_number = COALESCE(?, phone_number) WHERE device_id = ?",
                (now, payload.phone_number, x_device_id),
            )
            return SmsInboundResponse(
                id=existing["id"],
                verification_code=existing["verification_code"],
                duplicate=True,
            )

        cursor = conn.execute(
            """
            INSERT INTO sms_messages (
                device_id, sender, body, received_at, created_at, verification_code, message_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                x_device_id,
                payload.sender,
                payload.body,
                payload.received_at,
                now,
                verification_code,
                message_hash,
            ),
        )
        conn.execute(
            "UPDATE devices SET last_seen_at = ?, phone_number = COALESCE(?, phone_number) WHERE device_id = ?",
            (now, payload.phone_number, x_device_id),
        )

    return SmsInboundResponse(
        id=cursor.lastrowid,
        verification_code=verification_code,
        duplicate=False,
    )


@router.get("", response_model=SmsListResponse)
def list_sms(
    device_id: str | None = Query(default=None),
    keyword: str | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    _: None = Depends(verify_admin),
) -> SmsListResponse:
    return fetch_sms_messages(
        device_id=device_id,
        keyword=keyword,
        limit=limit,
        offset=offset,
    )
