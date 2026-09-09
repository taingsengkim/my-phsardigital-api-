package co.istad.projectpracticum.phsardigital.core.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The property that matters is containment: the box may be too big, because the exact
 * distance is checked afterwards, but a point inside the circle must never fall outside
 * the box or the shop simply disappears from the results.
 */
class GeoBoundingBoxTest {

    /** Phnom Penh, roughly the centre of what this marketplace serves. */
    private static final double LATITUDE = 11.5564;
    private static final double LONGITUDE = 104.9282;

    @Test
    @DisplayName("brackets the centre")
    void bracketsTheCentre() {
        GeoBoundingBox box = GeoBoundingBox.around(LATITUDE, LONGITUDE, 5);

        assertThat(box.minLatitude()).isLessThan(LATITUDE);
        assertThat(box.maxLatitude()).isGreaterThan(LATITUDE);
        assertThat(box.minLongitude()).isLessThan(LONGITUDE);
        assertThat(box.maxLongitude()).isGreaterThan(LONGITUDE);
        assertThat(box.wrapsAntimeridian()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("contains every point on the circle it was built around")
    @CsvSource({
            "11.5564, 104.9282, 1",
            "11.5564, 104.9282, 25",
            "11.5564, 104.9282, 100",
            "0.0, 0.0, 50",
            "-33.8688, 151.2093, 40",
            "71.0, 25.0, 100",
            "-71.0, -25.0, 100"
    })
    void containsTheCircle(double latitude, double longitude, double radiusKm) {
        GeoBoundingBox box = GeoBoundingBox.around(latitude, longitude, radiusKm);

        // Walk the circle a degree of bearing at a time and check each point landed
        // inside. One bearing in 360 is fine — the extremes of a circle on a sphere are
        // due north, south, east and west, all of which are hit exactly.
        for (int bearing = 0; bearing < 360; bearing++) {
            double[] point = destination(latitude, longitude, radiusKm, bearing);
            assertThat(contains(box, point[0], point[1]))
                    .as("bearing %d from %s,%s lands at %s,%s outside %s",
                            bearing, latitude, longitude, point[0], point[1], box)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("wraps across the 180th meridian rather than producing an empty range")
    void wrapsAcrossTheAntimeridian() {
        GeoBoundingBox box = GeoBoundingBox.around(-16.5, 179.9, 50);

        assertThat(box.wrapsAntimeridian()).isTrue();
        assertThat(box.minLongitude()).isPositive();
        assertThat(box.maxLongitude()).isNegative();
        // Points either side of the meridian are both inside, which is the whole point.
        assertThat(contains(box, -16.5, 179.95)).isTrue();
        assertThat(contains(box, -16.5, -179.95)).isTrue();
    }

    @Test
    @DisplayName("opens up to every meridian when the circle swallows a pole")
    void coversEveryMeridianOverAPole() {
        GeoBoundingBox box = GeoBoundingBox.around(89.9, 0, 100);

        assertThat(box.maxLatitude()).isEqualTo(90d);
        assertThat(box.minLongitude()).isEqualTo(-180d);
        assertThat(box.maxLongitude()).isEqualTo(180d);
        assertThat(box.wrapsAntimeridian()).isFalse();
        // The far side of the pole is a few kilometres away and must not be excluded.
        assertThat(contains(box, 89.95, 180)).isTrue();
    }

    @Test
    @DisplayName("stays inside the map at the south pole too")
    void clampsAtTheSouthPole() {
        GeoBoundingBox box = GeoBoundingBox.around(-89.95, 12, 100);

        assertThat(box.minLatitude()).isEqualTo(-90d);
        assertThat(box.minLongitude()).isEqualTo(-180d);
        assertThat(box.maxLongitude()).isEqualTo(180d);
    }

    @Test
    @DisplayName("collapses onto the point itself at zero radius")
    void zeroRadiusIsThePoint() {
        GeoBoundingBox box = GeoBoundingBox.around(LATITUDE, LONGITUDE, 0);

        // Not exactly the point: the margin is still there, and it is what stops the
        // shop standing on that very spot from rounding its way out of the box.
        assertThat(box.minLatitude()).isCloseTo(LATITUDE, within(1e-6));
        assertThat(box.maxLatitude()).isCloseTo(LATITUDE, within(1e-6));
        assertThat(box.minLongitude()).isCloseTo(LONGITUDE, within(1e-6));
        assertThat(box.maxLongitude()).isCloseTo(LONGITUDE, within(1e-6));
        assertThat(contains(box, LATITUDE, LONGITUDE)).isTrue();
    }

    /** The same reading the SQL predicate uses, including the wrapped case. */
    private static boolean contains(GeoBoundingBox box, double latitude, double longitude) {
        boolean withinLatitude = latitude >= box.minLatitude() && latitude <= box.maxLatitude();
        boolean withinLongitude = box.wrapsAntimeridian()
                ? longitude >= box.minLongitude() || longitude <= box.maxLongitude()
                : longitude >= box.minLongitude() && longitude <= box.maxLongitude();
        return withinLatitude && withinLongitude;
    }

    /** Where you end up travelling {@code distanceKm} along a bearing on a sphere. */
    private static double[] destination(double latitude, double longitude,
                                        double distanceKm, double bearingDegrees) {
        double angular = distanceKm / GeoBoundingBox.EARTH_RADIUS_KM;
        double bearing = Math.toRadians(bearingDegrees);
        double fromLatitude = Math.toRadians(latitude);
        double fromLongitude = Math.toRadians(longitude);

        double toLatitude = Math.asin(Math.sin(fromLatitude) * Math.cos(angular)
                + Math.cos(fromLatitude) * Math.sin(angular) * Math.cos(bearing));
        double toLongitude = fromLongitude + Math.atan2(
                Math.sin(bearing) * Math.sin(angular) * Math.cos(fromLatitude),
                Math.cos(angular) - Math.sin(fromLatitude) * Math.sin(toLatitude));

        double degrees = Math.toDegrees(toLongitude);
        return new double[]{Math.toDegrees(toLatitude), ((degrees + 540) % 360) - 180};
    }
}
