# Vibee - nền tảng bán vé sự kiện

## Chạy thử

```bash
export JWT_SECRET=$(openssl rand -base64 32)
export DB_PASSWORD=your_mysql_password
export ADMIN_EMAIL=admin@vibee.local
export ADMIN_PASSWORD=ChangeMe123
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html (bấm Authorize, dán access token để thử API có bảo vệ).

## Thử nhanh bằng curl

```bash
# Đăng ký
curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"a@test.com","password":"12345678","fullName":"Test User"}'

# Đăng nhập
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@test.com","password":"12345678"}'

# Gọi API cần đăng nhập
curl localhost:8080/api/v1/users/me -H "Authorization: Bearer <accessToken>"

# Đổi token
curl -X POST localhost:8080/api/v1/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# ATTENDEE gọi API admin -> 403
curl -X POST localhost:8080/api/v1/admin/users -H "Authorization: Bearer <accessToken>"
```

## Phân quyền

| Route | Quyền |
|---|---|
| `POST /auth/{register,login,refresh,logout}`, `GET /events/**` | Public |
| `/organizer/**` | ORGANIZER |
| `/staff/**` | STAFF, ADMIN |
| `/admin/**` | ADMIN |
| Còn lại | Đã đăng nhập |

Tự đăng ký chỉ được ATTENDEE hoặc ORGANIZER. STAFF/ADMIN do ADMIN tạo qua `POST /admin/users`.

## Thiết kế token

- **Access token**: JWT HS256, sống 15 phút, chứa `sub` (userId), `email`, `role`. Stateless, filter không truy vấn DB.
- **Refresh token**: chuỗi ngẫu nhiên 256-bit, sống 7 ngày, DB chỉ lưu SHA-256.
- **Rotation**: mỗi lần refresh thu hồi token cũ, cấp cặp mới.
- **Reuse detection**: token đã thu hồi mà bị dùng lại thì thu hồi toàn bộ phiên của user.

## Sự kiện và hạng vé

Vòng đời sự kiện:

```
DRAFT --submit--> PENDING --approve--> PUBLISHED
  ^                  |
  +-----reject-------+      (DRAFT/PENDING/PUBLISHED --cancel--> CANCELLED, nếu chưa bán vé nào)
```

Quy tắc: sự kiện và hạng vé chỉ sửa được khi DRAFT. Organizer chỉ thấy sự kiện của mình (của người khác trả 404).

| API | Quyền | Ghi chú |
|---|---|---|
| `GET /events?keyword=&venue=&from=&to=&page=&size=` | Public | Chỉ sự kiện PUBLISHED chưa kết thúc, sắp xếp theo giờ bắt đầu |
| `GET /events/{id}`, `GET /events/{id}/ticket-types` | Public | |
| `POST/GET /organizer/events`, `GET/PUT /organizer/events/{id}` | ORGANIZER | |
| `PATCH /organizer/events/{id}/submit` | ORGANIZER | DRAFT -> PENDING, cần >= 1 hạng vé |
| `PATCH /organizer/events/{id}/cancel` | ORGANIZER | |
| `POST /organizer/events/{id}/ticket-types` | ORGANIZER | |
| `PUT/DELETE /organizer/events/{id}/ticket-types/{typeId}` | ORGANIZER | |
| `GET /admin/events?status=PENDING`, `GET /admin/events/{id}` | ADMIN | Hàng đợi duyệt |
| `PATCH /admin/events/{id}/approve`, `/reject` | ADMIN | reject kèm `{"reason": "..."}` |

Ví dụ (đã đăng nhập bằng tài khoản ORGANIZER):

```bash
curl -X POST localhost:8080/api/v1/organizer/events -H "Authorization: Bearer $ORG_TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "title":"Vibee Live Concert","description":"Đêm nhạc cuối năm","venue":"Nhà hát Hòa Bình, TP.HCM",
    "startTime":"2026-12-20T12:00:00Z","endTime":"2026-12-20T15:00:00Z"}'

curl -X POST localhost:8080/api/v1/organizer/events/1/ticket-types -H "Authorization: Bearer $ORG_TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "name":"VIP","price":1500000,"totalQuantity":100,
    "saleStart":"2026-11-01T00:00:00Z","saleEnd":"2026-12-20T10:00:00Z","maxPerOrder":4}'

curl -X PATCH localhost:8080/api/v1/organizer/events/1/submit -H "Authorization: Bearer $ORG_TOKEN"
curl -X PATCH localhost:8080/api/v1/admin/events/1/approve   -H "Authorization: Bearer $ADMIN_TOKEN"
curl "localhost:8080/api/v1/events?keyword=vibee"
```

## Danh mục và sắp xếp (V3)

- Sự kiện có `category`: CONCERT, FESTIVAL, WORKSHOP, EXHIBITION, SPORT, OTHER (mặc định OTHER).
- `GET /events` thêm tham số `category` và `sort` (`SOONEST` = sắp diễn ra trước, `NEWEST` = mới đăng trước).
- Sắp xếp theo độ phổ biến sẽ làm sau khi có dữ liệu bán vé.

## Đặt vé, thanh toán, vé và soát vé

| API | Quyền | Ghi chú |
|---|---|---|
| `POST /orders` | Đã đăng nhập | Giữ vé 10 phút. Body: `{"items":[{"ticketTypeId":1,"quantity":2}]}` |
| `GET /orders/me`, `GET /orders/{orderCode}` | Chủ đơn | |
| `POST /orders/{orderCode}/cancel` | Chủ đơn | Chỉ đơn PENDING, hoàn vé về kho |
| `POST /payments/vnpay/create` | Chủ đơn | Trả `paymentUrl` để chuyển sang VNPay |
| `GET /payments/vnpay/return`, `/ipn` | Public | Callback VNPay (có kiểm tra chữ ký) |
| `POST /payments/mock/{orderCode}/confirm` | Chủ đơn | Giả lập thanh toán, cần `PAYMENT_MOCK_ENABLED=true` |
| `GET /tickets/me` | Đã đăng nhập | Vé của tôi (kèm `code` để tạo QR) |
| `POST /staff/check-in` | STAFF, ADMIN | `{"code":"...","eventId":1}`, quét lần hai bị từ chối |

Biến môi trường mới: `PAYMENT_MOCK_ENABLED`, `ORDER_HOLD_MINUTES`, `ORDER_EXPIRE_SCAN_MS`, `VNPAY_TMN_CODE`,
`VNPAY_HASH_SECRET`, `PUBLIC_BASE_URL`, `FRONTEND_URL`.

Vòng đời đơn: `PENDING -> PAID` (thanh toán) | `-> CANCELLED` (người dùng hủy) | `-> EXPIRED` (quá hạn, job quét mỗi 30 giây).
Mọi chuyển trạng thái đều là UPDATE có điều kiện (`... WHERE status = 'PENDING'`) nên các luồng tranh nhau chỉ một bên thắng.
