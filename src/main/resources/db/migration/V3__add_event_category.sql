-- Danh mục sự kiện để lọc ở trang khám phá. Sự kiện cũ mặc định là OTHER.
ALTER TABLE events ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'OTHER' AFTER venue;
CREATE INDEX idx_events_category ON events (category);
