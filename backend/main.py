from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from auth import verify_admin
from config import ADMIN_TOKEN, BASE_DIR
from database import get_connection, init_db
from models import DeviceSummary
from routers import devices, sms
from services.device_service import fetch_devices
from services.sms_service import fetch_sms_messages

app = FastAPI(title="SMS Receiver", version="1.0.0")
templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))

app.mount("/static", StaticFiles(directory=str(BASE_DIR / "static")), name="static")
app.include_router(devices.router)
app.include_router(sms.router)


@app.on_event("startup")
def on_startup() -> None:
    init_db()


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/", response_class=HTMLResponse)
def index(
    request: Request,
    _: None = Depends(verify_admin),
    admin_token: str | None = Query(default=None),
) -> HTMLResponse:

    device_rows = fetch_devices()
    sms_rows = fetch_sms_messages(limit=50, offset=0)

    return templates.TemplateResponse(
        request,
        "index.html",
        {
            "devices": device_rows,
            "messages": sms_rows.items,
            "total_messages": sms_rows.total,
            "admin_token": admin_token or ADMIN_TOKEN,
        },
    )


@app.get("/device/{device_id}", response_class=HTMLResponse)
def device_page(
    request: Request,
    device_id: str,
    keyword: str | None = Query(default=None),
    _: None = Depends(verify_admin),
    admin_token: str | None = Query(default=None),
) -> HTMLResponse:

    with get_connection() as conn:
        device_row = conn.execute(
            "SELECT * FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()

    if not device_row:
        raise HTTPException(status_code=404, detail="Device not found")

    sms_rows = fetch_sms_messages(device_id=device_id, keyword=keyword, limit=100, offset=0)
    token = admin_token or ADMIN_TOKEN

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
            "admin_token": token,
        },
    )
