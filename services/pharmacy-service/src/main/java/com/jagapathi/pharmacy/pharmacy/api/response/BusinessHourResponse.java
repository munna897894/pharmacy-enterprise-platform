package com.jagapathi.pharmacy.pharmacy.api.response;

import com.jagapathi.pharmacy.pharmacy.domain.model.BusinessHour;
import java.time.LocalTime;

public record BusinessHourResponse(
        String id,
        Integer dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime,
        Boolean closed
) {
    public static BusinessHourResponse from(BusinessHour hour) {
        return new BusinessHourResponse(
                hour.getId(),
                hour.getDayOfWeek(),
                hour.getOpenTime(),
                hour.getCloseTime(),
                hour.getClosed()
        );
    }
}
