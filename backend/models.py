from pydantic import BaseModel, Field


class DeviceRegisterRequest(BaseModel):
    device_name: str = Field(min_length=1, max_length=100)
    phone_number: str | None = None


class DeviceRegisterResponse(BaseModel):
    device_id: str
    api_key: str
    device_name: str
    phone_number: str | None = None


class SmsInboundRequest(BaseModel):
    sender: str = Field(min_length=1, max_length=50)
    body: str = Field(min_length=1)
    received_at: str
    phone_number: str | None = None


class SmsInboundResponse(BaseModel):
    id: int
    verification_code: str | None = None
    duplicate: bool = False


class DeviceSummary(BaseModel):
    device_id: str
    device_name: str
    phone_number: str | None = None
    last_seen_at: str | None = None
    latest_sms_preview: str | None = None
    latest_sms_at: str | None = None


class DeviceUpdateRequest(BaseModel):
    device_name: str = Field(min_length=1, max_length=100)
    phone_number: str | None = Field(default=None, max_length=100)


class SmsMessageItem(BaseModel):
    id: int
    device_id: str
    device_name: str | None = None
    sender: str
    body: str
    received_at: str
    created_at: str
    verification_code: str | None = None


class SmsListResponse(BaseModel):
    total: int
    items: list[SmsMessageItem]
