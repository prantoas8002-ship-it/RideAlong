package com.example.ridealong.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * THE single definition of allowed ride status transitions. firestore.rules mirrors the manual
 * (owner-initiated) subset in {@code manualTransition()}; keep them in sync.
 *
 * <pre>
 *   OPEN ──(seats hit 0)──▶ FULL ──(participant leaves)──▶ OPEN       [system: seat-driven]
 *   OPEN / FULL ──start──▶ IN_PROGRESS ──complete──▶ COMPLETED          [owner]
 *   OPEN / FULL / IN_PROGRESS ──cancel──▶ CANCELLED                      [owner]
 * </pre>
 */
public final class RideStateMachine {
    private static final Map<RideStatus, Set<RideStatus>> MANUAL = new EnumMap<>(RideStatus.class);
    private static final Map<RideStatus, Set<RideStatus>> SYSTEM = new EnumMap<>(RideStatus.class);

    static {
        MANUAL.put(RideStatus.OPEN, EnumSet.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED));
        MANUAL.put(RideStatus.FULL, EnumSet.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED));
        MANUAL.put(RideStatus.IN_PROGRESS, EnumSet.of(RideStatus.COMPLETED, RideStatus.CANCELLED));
        MANUAL.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        MANUAL.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));

        SYSTEM.put(RideStatus.OPEN, EnumSet.of(RideStatus.FULL));
        SYSTEM.put(RideStatus.FULL, EnumSet.of(RideStatus.OPEN));
        SYSTEM.put(RideStatus.IN_PROGRESS, EnumSet.noneOf(RideStatus.class));
        SYSTEM.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        SYSTEM.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));
    }

    private RideStateMachine() { }

    /** Owner-initiated transition (start, complete, cancel). */
    public static boolean canManuallyTransition(RideStatus from, RideStatus to) {
        return MANUAL.get(from).contains(to);
    }

    /** Seat-driven transition performed inside accept/leave transactions. */
    public static boolean canSystemTransition(RideStatus from, RideStatus to) {
        return SYSTEM.get(from).contains(to);
    }

    public static boolean canTransition(RideStatus from, RideStatus to) {
        return canManuallyTransition(from, to) || canSystemTransition(from, to);
    }

    public static Set<RideStatus> manualTargets(RideStatus from) {
        return Collections.unmodifiableSet(MANUAL.get(from));
    }

    /** Status after a seat count change, for an OPEN/FULL ride. */
    public static RideStatus statusForSeats(RideStatus current, int seatsAvailable) {
        if (current != RideStatus.OPEN && current != RideStatus.FULL) return current;
        return seatsAvailable <= 0 ? RideStatus.FULL : RideStatus.OPEN;
    }
}
