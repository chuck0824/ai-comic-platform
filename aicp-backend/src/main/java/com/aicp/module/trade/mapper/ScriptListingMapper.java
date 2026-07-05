package com.aicp.module.trade.mapper;

import com.aicp.module.trade.entity.ScriptListing;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ScriptListingMapper extends BaseMapper<ScriptListing> {

    /**
     * Conditionally reserve exclusive inventory.
     * Returns the number of affected rows (1 = success, 0 = already reserved).
     */
    @Update("UPDATE script_listings SET listing_status = 'EXCLUSIVE_RESERVED', "
            + "reserved_order_no = #{orderNo}, reservation_expires_at = #{expiresAt}, "
            + "row_version = row_version + 1 "
            + "WHERE id = #{listingId} AND listing_status = 'LISTED' "
            + "AND (reservation_expires_at IS NULL OR reservation_expires_at < NOW())")
    int reserveExclusive(@Param("listingId") Long listingId,
                         @Param("orderNo") String orderNo,
                         @Param("expiresAt") LocalDateTime expiresAt);

    /** Release reservation on expiry/cancellation. */
    @Update("UPDATE script_listings SET listing_status = 'LISTED', "
            + "reserved_order_no = NULL, reservation_expires_at = NULL, "
            + "row_version = row_version + 1 "
            + "WHERE reserved_order_no = #{orderNo} AND listing_status = 'EXCLUSIVE_RESERVED'")
    int releaseReservation(@Param("orderNo") String orderNo);

    /** Restore listing after exclusive refund. */
    @Update("UPDATE script_listings SET listing_status = 'LISTED', "
            + "exclusive_license_type = NULL, "
            + "reserved_order_no = NULL, reservation_expires_at = NULL, "
            + "row_version = row_version + 1 "
            + "WHERE reserved_order_no = #{orderNo}")
    int restoreListed(@Param("orderNo") String orderNo);
}
