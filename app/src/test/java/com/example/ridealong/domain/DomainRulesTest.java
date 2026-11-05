package com.example.ridealong.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ridealong.domain.calculator.FareCalculator;
import com.example.ridealong.domain.config.FareConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.domain.model.RideStateMachine;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.domain.model.TransportType;
import com.example.ridealong.domain.validator.ProfileValidator;
import com.example.ridealong.domain.validator.RideInputValidator;
import com.example.ridealong.domain.validator.SeatValidator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/** Fare estimation, seat capacity, input validation and state transitions. */
public class DomainRulesTest {

    // ---------- FareCalculator ----------
    private final FareCalculator fare = new FareCalculator(new FareConfig());

    @Test
    public void cngTripFareAndSplit() {
        assertEquals(220, fare.estimateTripFare(TransportType.CNG, 10), 1e-9);    // 40 + 18*10
        assertEquals(75, fare.estimatePerPerson(TransportType.CNG, 10, 3), 1e-9); // 73.3 -> 75
    }

    @Test
    public void minimumFareApplies() {
        assertEquals(60, fare.estimateTripFare(TransportType.CNG, 0.5), 1e-9);
        assertEquals(150, fare.estimateTripFare(TransportType.CAR, 1), 1e-9);
    }

    @Test
    public void busIsPerSeatNotSplit() {
        assertEquals(25, fare.estimatePerPerson(TransportType.BUS, 10, 4), 1e-9);  // 24.5 -> 25
        assertEquals(10, fare.estimatePerPerson(TransportType.BUS, 1, 4), 1e-9);   // minimum ticket
    }

    @Test
    public void splitClampedToVehicleCapacity() {
        // Rickshaw seats 2: splitting by 5 would be unrealistic.
        assertEquals(fare.estimatePerPerson(TransportType.RICKSHAW, 3, 2),
                fare.estimatePerPerson(TransportType.RICKSHAW, 3, 5), 1e-9);
    }

    @Test
    public void passengerShareIsProportional() {
        double full = fare.estimatePerPerson(TransportType.CAR, 20, 4);
        double half = fare.estimatePassengerShare(TransportType.CAR, 20, 10, 4);
        assertTrue(half < full);
        assertEquals(full / 2, half, 5);
    }

    @Test
    public void moreRidersMeansCheaperPerPerson() {
        assertTrue(fare.estimatePerPerson(TransportType.CAR, 15, 4) < fare.estimatePerPerson(TransportType.CAR, 15, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeDistanceRejected() {
        fare.estimateTripFare(TransportType.CNG, -1);
    }

    @Test
    public void fareRatesAreConfigurable() {
        FareConfig cfg = new FareConfig();
        cfg.rate(TransportType.CNG).perKm = 30;
        assertEquals(340, new FareCalculator(cfg).estimateTripFare(TransportType.CNG, 10), 1e-9);
    }

    // ---------- Seats ----------
    @Test
    public void seatCapacityRules() {
        assertTrue(SeatValidator.isValidTotalSeats(1));
        assertTrue(SeatValidator.isValidTotalSeats(8));
        assertFalse(SeatValidator.isValidTotalSeats(0));
        assertFalse(SeatValidator.isValidTotalSeats(9));
        assertTrue(SeatValidator.canReserve(3, 3));
        assertFalse(SeatValidator.canReserve(2, 3));
        assertFalse(SeatValidator.canReserve(2, 0));
        assertEquals(0, SeatValidator.seatsAfterAccept(2, 2));
        assertEquals(3, SeatValidator.seatsAfterLeave(1, 2, 3));
    }

    @Test(expected = IllegalStateException.class)
    public void overbookingThrows() {
        SeatValidator.seatsAfterAccept(1, 2);
    }

    @Test(expected = IllegalStateException.class)
    public void leaveCannotExceedTotal() {
        SeatValidator.seatsAfterLeave(3, 1, 3);
    }

    // ---------- Ride input ----------
    private static final long NOW = 1_800_000_000_000L;

    private RideInputValidator.Input validInput() {
        RideInputValidator.Input in = new RideInputValidator.Input();
        in.pickup = new GeoCoord(23.8069, 90.3687);
        in.drop = new GeoCoord(23.7389, 90.3950);
        in.departureMillis = NOW + 3600_000L;
        in.seats = 3;
        in.costPerPerson = 80.0;
        in.maxDetourKm = 2.0;
        in.comment = "";
        return in;
    }

    @Test
    public void validRideInputPasses() {
        assertTrue(RideInputValidator.validate(validInput(), NOW).isEmpty());
    }

    @Test
    public void rideInputErrors() {
        RideInputValidator.Input in = validInput();
        in.pickup = null;
        in.departureMillis = NOW - 1;
        in.seats = 9;
        in.costPerPerson = -5.0;
        in.maxDetourKm = 50.0;
        Set<RideInputValidator.Error> e = RideInputValidator.validate(in, NOW);
        assertTrue(e.contains(RideInputValidator.Error.PICKUP_REQUIRED));
        assertTrue(e.contains(RideInputValidator.Error.TIME_IN_PAST));
        assertTrue(e.contains(RideInputValidator.Error.SEATS_OUT_OF_RANGE));
        assertTrue(e.contains(RideInputValidator.Error.COST_NEGATIVE));
        assertTrue(e.contains(RideInputValidator.Error.DETOUR_OUT_OF_RANGE));
    }

    @Test
    public void sameLocationRejected() {
        RideInputValidator.Input in = validInput();
        in.drop = new GeoCoord(in.pickup.lat + 0.0001, in.pickup.lng);
        assertTrue(RideInputValidator.validate(in, NOW).contains(RideInputValidator.Error.SAME_LOCATION));
    }

    @Test
    public void zeroCostAllowedButMissingCostNot() {
        RideInputValidator.Input in = validInput();
        in.costPerPerson = 0.0;
        assertTrue(RideInputValidator.validate(in, NOW).isEmpty());
        in.costPerPerson = null;
        assertTrue(RideInputValidator.validate(in, NOW).contains(RideInputValidator.Error.COST_REQUIRED));
    }

    @Test
    public void tooFarInFutureRejected() {
        RideInputValidator.Input in = validInput();
        in.departureMillis = NOW + RideInputValidator.MAX_ADVANCE_MILLIS + 1;
        assertTrue(RideInputValidator.validate(in, NOW).contains(RideInputValidator.Error.TIME_TOO_FAR));
    }

    // ---------- Profile ----------
    @Test
    public void emailValidation() {
        assertTrue(ProfileValidator.isValidEmail("rafi@du.ac.bd"));
        assertFalse(ProfileValidator.isValidEmail("rafi@"));
        assertFalse(ProfileValidator.isValidEmail(null));
    }

    @Test
    public void passwordValidation() {
        assertTrue(ProfileValidator.isValidPassword("secret123"));
        assertFalse(ProfileValidator.isValidPassword("short1"));
        assertFalse(ProfileValidator.isValidPassword("onlyletters"));
    }

    @Test
    public void bangladeshPhoneValidation() {
        assertTrue(ProfileValidator.isValidPhone("01712345678"));
        assertTrue(ProfileValidator.isValidPhone("+8801712345678"));
        assertTrue(ProfileValidator.isValidPhone("017-1234-5678"));
        assertFalse(ProfileValidator.isValidPhone("01212345678")); // 012 is not a mobile prefix
        assertFalse(ProfileValidator.isValidPhone("12345"));
        assertTrue(ProfileValidator.isValidPhone("+447911123456")); // international fallback
    }

    @Test
    public void emergencyContactsValidation() {
        assertFalse(ProfileValidator.isValidEmergencyContacts(Collections.emptyList()));
        assertTrue(ProfileValidator.isValidEmergencyContacts(
                Collections.singletonList(new String[]{"Ammu", "01811111111"})));
        assertFalse(ProfileValidator.isValidEmergencyContacts(
                Collections.singletonList(new String[]{"Ammu", "999"})));
        String[] c = {"Abbu", "01911111111"};
        assertFalse(ProfileValidator.isValidEmergencyContacts(Arrays.asList(c, c, c, c, c, c)));
    }

    // ---------- State transitions ----------
    @Test
    public void rideStateTransitions() {
        assertTrue(RideStateMachine.canManuallyTransition(RideStatus.OPEN, RideStatus.IN_PROGRESS));
        assertTrue(RideStateMachine.canManuallyTransition(RideStatus.FULL, RideStatus.CANCELLED));
        assertTrue(RideStateMachine.canManuallyTransition(RideStatus.IN_PROGRESS, RideStatus.COMPLETED));
        assertFalse(RideStateMachine.canManuallyTransition(RideStatus.OPEN, RideStatus.COMPLETED));
        assertFalse(RideStateMachine.canManuallyTransition(RideStatus.COMPLETED, RideStatus.OPEN));
        assertFalse(RideStateMachine.canManuallyTransition(RideStatus.CANCELLED, RideStatus.IN_PROGRESS));
        assertFalse(RideStateMachine.canManuallyTransition(RideStatus.OPEN, RideStatus.FULL)); // seat-driven only
        assertTrue(RideStateMachine.canSystemTransition(RideStatus.OPEN, RideStatus.FULL));
        assertTrue(RideStateMachine.canSystemTransition(RideStatus.FULL, RideStatus.OPEN));
        assertFalse(RideStateMachine.canTransition(RideStatus.IN_PROGRESS, RideStatus.OPEN));
    }

    @Test
    public void statusForSeats() {
        assertEquals(RideStatus.FULL, RideStateMachine.statusForSeats(RideStatus.OPEN, 0));
        assertEquals(RideStatus.OPEN, RideStateMachine.statusForSeats(RideStatus.FULL, 1));
        assertEquals(RideStatus.IN_PROGRESS, RideStateMachine.statusForSeats(RideStatus.IN_PROGRESS, 0));
    }

    @Test
    public void terminalStates() {
        assertTrue(RideStatus.COMPLETED.isTerminal());
        assertTrue(RideStatus.CANCELLED.isTerminal());
        assertFalse(RideStatus.FULL.isTerminal());
        assertTrue(RideStatus.OPEN.isJoinable());
        assertFalse(RideStatus.FULL.isJoinable());
    }

    @Test
    public void requestTransitions() {
        assertTrue(RequestStatus.PENDING.canTransitionTo(RequestStatus.ACCEPTED));
        assertTrue(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.LEFT));
        assertTrue(RequestStatus.CANCELLED.canTransitionTo(RequestStatus.PENDING));
        assertFalse(RequestStatus.REJECTED.canTransitionTo(RequestStatus.PENDING));
        assertFalse(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.PENDING));
    }
}
