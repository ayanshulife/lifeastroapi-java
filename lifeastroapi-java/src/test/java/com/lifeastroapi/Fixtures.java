package com.lifeastroapi;

/** Shared sample inputs. The neutral sample person is Arjun Mehta, born 1990-01-15 10:30 in Mumbai. */
final class Fixtures {
    private Fixtures() {
    }

    static final BirthInput ARJUN = BirthInput.of("1990-01-15", "10:30", "Asia/Kolkata", 19.076, 72.8777);

    /** The second chart of the Node SDK's tests (Delhi). */
    static final BirthInput GIRL = BirthInput.of("1992-03-20", "08:15", "Asia/Kolkata", 28.6139, 77.209);

    static final Place MUMBAI = Place.of(19.076, 72.8777, "Asia/Kolkata");
}
