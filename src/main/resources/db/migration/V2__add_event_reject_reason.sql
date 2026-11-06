-- Lý do ADMIN từ chối duyệt sự kiện, để organizer biết cần sửa gì
ALTER TABLE events ADD COLUMN reject_reason VARCHAR(500) NULL AFTER status;
