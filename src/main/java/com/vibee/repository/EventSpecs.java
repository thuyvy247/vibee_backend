package com.vibee.repository;

import com.vibee.entity.Event;
import com.vibee.enums.EventCategory;
import com.vibee.enums.EventStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/** Các điều kiện lọc ghép được với nhau. Tham số null/rỗng thì bỏ qua điều kiện đó. */
public final class EventSpecs {

    private EventSpecs() {}

    public static Specification<Event> hasStatus(EventStatus status) {
        if (status == null) return null;
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Event> hasCategory(EventCategory category) {
        if (category == null) return null;
        return (root, q, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Event> organizerIs(Long organizerId) {
        if (organizerId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("organizer").get("id"), organizerId);
    }

    public static Specification<Event> endsAfter(Instant time) {
        if (time == null) return null;
        return (root, q, cb) -> cb.greaterThan(root.get("endTime"), time);
    }

    public static Specification<Event> startsFrom(Instant from) {
        if (from == null) return null;
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    public static Specification<Event> startsBefore(Instant to) {
        if (to == null) return null;
        return (root, q, cb) -> cb.lessThan(root.get("startTime"), to);
    }

    public static Specification<Event> titleContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String pattern = likePattern(keyword);
        return (root, q, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
    }

    public static Specification<Event> venueContains(String venue) {
        if (venue == null || venue.isBlank()) return null;
        String pattern = likePattern(venue);
        return (root, q, cb) -> cb.like(cb.lower(root.get("venue")), pattern, '\\');
    }

    /** Escape % _ \ để người dùng gõ ký tự đặc biệt không biến thành wildcard. */
    private static String likePattern(String input) {
        String escaped = input.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
