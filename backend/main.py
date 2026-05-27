from fastapi import FastAPI, Form, HTTPException, Query, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from auth import SMS_COOKIE_NAME, is_admin_authenticated, verify_admin
from config import ADMIN_TOKEN, BASE_DIR
from database import get_connection, init_db
from models import DeviceSummary
from routers import devices, sms
from services.device_service import fetch_devices
from services.sms_service import fetch_sms_messages

app = FastAPI(title="SMS Receiver", version="1.0.0")
templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))

app.mount("/sms/static", StaticFiles(directory=str(BASE_DIR / "static")), name="sms-static")
app.include_router(devices.router)
app.include_router(sms.router)

SMS_COOKIE_MAX_AGE = 60 * 60 * 24 * 7


@app.on_event("startup")
def on_startup() -> None:
    init_db()


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def redirect_to_login(request: Request, next_path: str | None = None) -> RedirectResponse:
    target = next_path or request.url.path
    if not target.startswith("/sms"):
        target = "/sms/"
    return RedirectResponse(url=f"/sms/login?next={target}", status_code=302)


@app.get("/")
def root_redirect() -> RedirectResponse:
    return RedirectResponse(url="/sms/", status_code=302)


@app.get("/device/{device_id}")
def legacy_device_redirect(device_id: str) -> RedirectResponse:
    return RedirectResponse(url=f"/sms/device/{device_id}", status_code=302)


@app.get("/sms/login", response_class=HTMLResponse)
def login_page(
    request: Request,
    next: str = Query(default="/sms/"),
    error: str | None = Query(default=None),
) -> HTMLResponse:
    if is_admin_authenticated(request):
        return RedirectResponse(url=next or "/sms/", status_code=302)

    return templates.TemplateResponse(
        request,
        "login.html",
        {"next": next or "/sms/", "error": error},
    )


@app.post("/sms/login")
def login_submit(
    request: Request,
    password: str = Form(...),
    next: str = Form(default="/sms/"),
) -> RedirectResponse:
    if password != ADMIN_TOKEN:
        return templates.TemplateResponse(
            request,
            "login.html",
            {"next": next or "/sms/", "error": "口令错误"},
            status_code=401,
        )

    safe_next = next if next.startswith("/sms") else "/sms/"
    response = RedirectResponse(url=safe_next, status_code=302)
    response.set_cookie(
        key=SMS_COOKIE_NAME,
        value=ADMIN_TOKEN,
        httponly=True,
        samesite="lax",
        max_age=SMS_COOKIE_MAX_AGE,
        path="/",
    )
    return response


@app.post("/sms/logout")
def logout() -> RedirectResponse:
    response = RedirectResponse(url="/sms/login", status_code=302)
    response.delete_cookie(key=SMS_COOKIE_NAME, path="/")
    return response


@app.get("/sms/", response_class=HTMLResponse)
def index(request: Request) -> HTMLResponse:
    if not is_admin_authenticated(request):
        return redirect_to_login(request)
    device_rows = fetch_devices()
    sms_rows = fetch_sms_messages(limit=50, offset=0)

    return templates.TemplateResponse(
        request,
        "index.html",
        {
            "devices": device_rows,
            "messages": sms_rows.items,
            "total_messages": sms_rows.total,
        },
    )


@app.get("/sms/device/{device_id}", response_class=HTMLResponse)
def device_page(
    request: Request,
    device_id: str,
    keyword: str | None = Query(default=None),
) -> HTMLResponse:
    if not is_admin_authenticated(request):
        return redirect_to_login(request, next_path=f"/sms/device/{device_id}")
    with get_connection() as conn:
        device_row = conn.execute(
            "SELECT * FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()

    if not device_row:
        raise HTTPException(status_code=404, detail="Device not found")

    sms_rows = fetch_sms_messages(device_id=device_id, keyword=keyword, limit=100, offset=0)

    return templates.TemplateResponse(
        request,
        "device.html",
        {
            "device": DeviceSummary(
                device_id=device_row["device_id"],
                device_name=device_row["device_name"],
                phone_number=device_row["phone_number"],
                last_seen_at=device_row["last_seen_at"],
            ),
            "messages": sms_rows.items,
            "total_messages": sms_rows.total,
            "keyword": keyword or "",
        },
    )
