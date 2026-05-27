from fastapi import Header, HTTPException, Query

from config import ADMIN_TOKEN


def verify_admin(
    admin_token: str | None = Query(default=None),
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
) -> None:
    token = admin_token or x_admin_token
    if token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid admin token")
