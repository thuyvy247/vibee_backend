package com.vibee.repository;

import com.vibee.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByEventIdOrderByPriceAscIdAsc(Long eventId);

    /** Lấy hạng vé của cả một trang sự kiện bằng 1 query (tránh N+1). */
    List<TicketType> findByEventIdIn(Collection<Long> eventIds);

    Optional<TicketType> findByIdAndEventId(Long id, Long eventId);

    @Query("select t from TicketType t join fetch t.event where t.id in :ids")
    List<TicketType> findAllWithEventByIdIn(@Param("ids") Collection<Long> ids);

    boolean existsByEventIdAndName(Long eventId, String name);

    boolean existsByEventIdAndNameAndIdNot(Long eventId, String name, Long id);

    /** Trừ tồn kho nguyên tử. Trả về 0 nghĩa là không đủ vé. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TicketType t
               SET t.availableQuantity = t.availableQuantity - :qty
             WHERE t.id = :id AND t.availableQuantity >= :qty
            """)
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** Hoàn tồn kho khi đơn hết hạn / bị hủy. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE TicketType t
               SET t.availableQuantity = t.availableQuantity + :qty
             WHERE t.id = :id AND t.availableQuantity + :qty <= t.totalQuantity
            """)
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}
