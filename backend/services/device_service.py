from database import get_connection
from models import DeviceSummary
from services.datetime_format import format_display_datetime

_DEVICE_SUMMARY_SQL = """
    SELECT
        d.device_id,
        d.device_name,
        d.phone_number,
        d.last_seen_at,
        s.body AS latest_sms_preview,
        s.received_at AS latest_sms_at
    FROM devices d
    LEFT JOIN sms_messages s ON s.id = (
        SELECT id FROM sms_messages
        WHERE device_id = d.device_id
        ORDER BY received_at DESC
        LIMIT 1
    )
"""


def _row_to_device_summary(row) -> DeviceSummary:
    return DeviceSummary(
        device_id=row["device_id"],
        device_name=row["device_name"],
        phone_number=row["phone_number"],
        last_seen_at=format_display_datetime(row["last_seen_at"])
        if row["last_seen_at"]
        else None,
        latest_sms_preview=row["latest_sms_preview"],
        latest_sms_at=format_display_datetime(row["latest_sms_at"])
        if row["latest_sms_at"]
        else None,
    )


def fetch_devices() -> list[DeviceSummary]:
    with get_connection() as conn:
        rows = conn.execute(
            f"""
            {_DEVICE_SUMMARY_SQL}
            ORDER BY d.created_at DESC
            """
        ).fetchall()

    return [_row_to_device_summary(row) for row in rows]


def fetch_device(device_id: str) -> DeviceSummary | None:
    with get_connection() as conn:
        row = conn.execute(
            f"""
            {_DEVICE_SUMMARY_SQL}
            WHERE d.device_id = ?
            """,
            (device_id,),
        ).fetchone()

    if not row:
        return None

    return _row_to_device_summary(row)


def update_device(
    device_id: str,
    device_name: str,
    phone_number: str | None,
) -> DeviceSummary | None:
    normalized_name = device_name.strip()
    if not normalized_name:
        return None

    normalized_phone = phone_number.strip() if phone_number else None
    if normalized_phone == "":
        normalized_phone = None

    with get_connection() as conn:
        existing = conn.execute(
            "SELECT device_id FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()
        if not existing:
            return None

        conn.execute(
            """
            UPDATE devices
            SET device_name = ?, phone_number = ?
            WHERE device_id = ?
            """,
            (normalized_name, normalized_phone, device_id),
        )

    return fetch_device(device_id)
