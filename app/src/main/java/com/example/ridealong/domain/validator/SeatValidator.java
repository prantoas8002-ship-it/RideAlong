package com.example.ridealong.domain.validator;

/** Seat-capacity rules shared by the post form, request flow and accept transaction. */
public final class SeatValidator {
    public static final int MIN_SEATS = 1;
    public static final int MAX_SEATS = 8;

    private SeatValidator() { }

    public static boolean isValidTotalSeats(int seats) {
        return seats >= MIN_SEATS && seats <= MAX_SEATS;
    }

    /** A request for {@code requested} seats may be sent / accepted. */
    public static boolean canReserve(int seatsAvailable, int requested) {
        return requested >= MIN_SEATS && requested <= seatsAvailable && seatsAvailable <= MAX_SEATS;
    }

    /** Seats after accepting; throws if it would overbook. */
    public static int seatsAfterAccept(int seatsAvailable, int requested) {
        if (!canReserve(seatsAvailable, requested)) throw new IllegalStateException("overbooking");
        return seatsAvailable - requested;
    }

    /** Seats after a participant leaves; never exceeds the ride's total. */
    public static int seatsAfterLeave(int seatsAvailable, int released, int totalSeats) {
        if (released < 1) throw new IllegalArgumentException("released must be >= 1");
        int s = seatsAvailable + released;
        if (s > totalSeats) throw new IllegalStateException("seat count would exceed total");
        return s;
    }
}
