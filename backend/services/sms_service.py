from database import get_connection
from models import SmsListResponse, SmsMessageItem


def fetch_sms_messages(
    device_id: str | None = None,
    keyword: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> SmsListResponse:
    conditions: list[str] = []
    params: list[object] = []

    if device_id:
        conditions.append("s.device_id = ?")
        params.append(device_id)

    if keyword:
        conditions.append("(s.body LIKE ? OR s.sender LIKE ? OR s.verification_code LIKE ?)")
        like_value = f"%{keyword}%"
        params.extend([like_value, like_value, like_value])

    where_clause = f"WHERE {' AND '.join(conditions)}" if conditions else ""

    with get_connection() as conn:
        total = conn.execute(
            f"SELECT COUNT(*) AS cnt FROM sms_messages s {where_clause}",
            params,
        ).fetchone()["cnt"]

        rows = conn.execute(
            f"""
            SELECT
                s.id,
                s.device_id,
                d.device_name,
                s.sender,
                s.body,
                s.received_at,
                s.created_at,
                s.verification_code
            FROM sms_messages s
            LEFT JOIN devices d ON d.device_id = s.device_id
            {where_clause}
            ORDER BY s.received_at DESC
            LIMIT ? OFFSET ?
            """,
            [*params, limit, offset],
        ).fetchall()

    items = [
        SmsMessageItem(
            id=row["id"],
            device_id=row["device_id"],
            device_name=row["device_name"],
            sender=row["sender"],
            body=row["body"],
            received_at=row["received_at"],
            created_at=row["created_at"],
            verification_code=row["verification_code"],
        )
        for row in rows
    ]

    return SmsListResponse(total=total, items=items)
