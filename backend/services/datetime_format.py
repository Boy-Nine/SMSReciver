from datetime import datetime
from zoneinfo import ZoneInfo

DISPLAY_TZ = ZoneInfo("Asia/Shanghai")


def format_display_datetime(value: str | None) -> str:
    if not value:
        return "-"

    text = value.strip()
    if not text:
        return "-"

    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=DISPLAY_TZ)
        else:
            parsed = parsed.astimezone(DISPLAY_TZ)
        return parsed.strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return text
