from database import get_connection
from models import DeviceSummary


def fetch_devices() -> list[DeviceSummary]:
    with get_connection() as conn:
        rows = conn.execute(
            """
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
            ORDER BY d.created_at DESC
            """
        ).fetchall()

    return [
        DeviceSummary(
            device_id=row["device_id"],
            device_name=row["device_name"],
            phone_number=row["phone_number"],
            last_seen_at=row["last_seen_at"],
            latest_sms_preview=row["latest_sms_preview"],
            latest_sms_at=row["latest_sms_at"],
        )
        for row in rows
    ]
