import re

_PATTERNS = (
    re.compile(r"验证码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"校验码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"动态码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"code[:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"\b(\d{6})\b"),
)


def extract_verification_code(body: str) -> str | None:
    if not body:
        return None

    for pattern in _PATTERNS:
        match = pattern.search(body)
        if not match:
            continue
        code = match.group(1)
        if len(code) < 4:
            continue
        return code

    return None
