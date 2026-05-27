from fastapi import HTTPException, Request

from config import ADMIN_TOKEN

SMS_COOKIE_NAME = "sms"
SMS_HEADER_NAME = "X-Sms-Token"


def read_sms_token(request: Request) -> str | None:
    return request.cookies.get(SMS_COOKIE_NAME) or request.headers.get(SMS_HEADER_NAME)


def is_admin_authenticated(request: Request) -> bool:
    token = read_sms_token(request)
    if not token:
        return False
    return token == ADMIN_TOKEN


def verify_admin(request: Request) -> None:
    if not is_admin_authenticated(request):
        raise HTTPException(status_code=401, detail="Unauthorized")
