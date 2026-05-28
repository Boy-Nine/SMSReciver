import re

_FULLWIDTH_DIGITS = str.maketrans("０１２３４５６７８９", "0123456789")

_PATTERNS = (
    re.compile(r"验证码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"校验码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"动态码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"动态密码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"登录密码[是为：:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"code[:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(r"otp[:\s]*(\d{4,8})", re.IGNORECASE),
    re.compile(
        r"(\d{4,8})[，,。\s]*(?:为|是)?(?:您)?(?:的)?(?:登录|注册|校验|验证)?(?:码|密码)",
        re.IGNORECASE,
    ),
    re.compile(r"【(\d{4,8})】"),
    re.compile(r"(?:验证码|校验码|动态码|动态密码).*?(\d{4,8})", re.IGNORECASE),
    re.compile(r"(?<!\d)(\d{6})(?!\d)"),
)


def extract_verification_code(body: str) -> str | None:
    if not body:
        return None

    normalized = body.translate(_FULLWIDTH_DIGITS).strip()
    if not normalized:
        return None

    for pattern in _PATTERNS:
        match = pattern.search(normalized)
        if not match:
            continue
        code = match.group(1)
        if len(code) < 4:
            continue
        return code

    return None
