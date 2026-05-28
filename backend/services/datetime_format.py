from datetime import datetime


def format_display_datetime(value: str | None) -> str:
    if not value:
        return "-"

    text = value.strip()
    if not text:
        return "-"

    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        return parsed.strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return text
