package com.loopers.domain.payment;

public record PgResult(String pgTransactionId, PgStatus status) {

    public static PgResult unavailable() {
        return new PgResult(null, PgStatus.UNAVAILABLE);
    }

    public boolean isAccepted() {
        return status == PgStatus.PROCESSING;
    }

    public boolean isUnavailable() {
        return status == PgStatus.UNAVAILABLE;
    }
}
