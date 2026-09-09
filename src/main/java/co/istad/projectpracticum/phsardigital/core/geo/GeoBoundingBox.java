package co.istad.projectpracticum.phsardigital.core.geo;

/**
 * The latitude/longitude rectangle that contains every point within {@code radiusKm} of
 * a centre.
 *
 * <p>Exists so "what is near me" can throw away most of the table with plain range
 * comparisons — which an index on the coordinate columns can serve — before the database
 * spends trigonometry on the rows that survive. The box is deliberately generous: every
 * point inside the circle is inside the box, but the box's corners are outside the
 * circle, so the exact distance still has to be checked on what comes back.
 *
 * @param minLongitude when greater than {@code maxLongitude} the box straddles the 180th
 *                     meridian, and "inside" means at or east of {@code minLongitude}
 *                     <em>or</em> at or west of {@code maxLongitude} — not between the
 *                     two. {@link #wrapsAntimeridian()} says which reading applies.
 */
public record GeoBoundingBox(double minLatitude, double maxLatitude,
                             double minLongitude, double maxLongitude) {

    /** Mean Earth radius in kilometres, the same figure the distance query is scaled by. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * A tenth of a millimetre, added to both spans. The box is derived through a chain of
     * trigonometry and the circle touches its edges exactly, so without a margin a shop
     * sitting on the boundary can be rounded a fraction outside and vanish from the
     * results. Being fractionally too generous costs nothing: the exact distance is
     * checked afterwards regardless.
     */
    private static final double MARGIN_DEGREES = 1e-9;

    private static final double SOUTH_POLE = -90d;
    private static final double NORTH_POLE = 90d;
    private static final double WEST_EDGE = -180d;
    private static final double EAST_EDGE = 180d;

    /**
     * @param radiusKm how far from the centre the box has to reach; a radius that
     *                 swallows a pole or more than half the globe widens to every
     *                 meridian rather than producing a rectangle that excludes points
     *                 on the far side of it
     */
    public static GeoBoundingBox around(double latitude, double longitude, double radiusKm) {
        // The radius as an angle at the centre of the Earth, which is what both spans
        // below are measured in before being converted back to degrees.
        double angularRadius = radiusKm / EARTH_RADIUS_KM;
        double latitudeSpan = Math.toDegrees(angularRadius) + MARGIN_DEGREES;

        double minLatitude = latitude - latitudeSpan;
        double maxLatitude = latitude + latitudeSpan;

        // A circle reaching over a pole covers every meridian at once: there is no
        // longitude range left to narrow by.
        if (minLatitude <= SOUTH_POLE || maxLatitude >= NORTH_POLE) {
            return new GeoBoundingBox(Math.max(minLatitude, SOUTH_POLE),
                    Math.min(maxLatitude, NORTH_POLE), WEST_EDGE, EAST_EDGE);
        }

        // Meridians converge towards the poles, so the same kilometre buys more degrees
        // of longitude the further from the equator it is measured. Taken at whichever
        // edge of the latitude band sits nearer a pole, so the box is wide enough along
        // the whole of it rather than only across its middle.
        double nearestToPole = Math.max(Math.abs(minLatitude), Math.abs(maxLatitude));
        double longitudeSpan = Math.toDegrees(
                Math.asin(Math.sin(angularRadius) / Math.cos(Math.toRadians(nearestToPole))))
                + MARGIN_DEGREES;

        if (!Double.isFinite(longitudeSpan) || longitudeSpan >= EAST_EDGE) {
            return new GeoBoundingBox(minLatitude, maxLatitude, WEST_EDGE, EAST_EDGE);
        }

        return new GeoBoundingBox(minLatitude, maxLatitude,
                wrap(longitude - longitudeSpan), wrap(longitude + longitudeSpan));
    }

    /** True when the box straddles the 180th meridian, leaving its west edge east of its east edge. */
    public boolean wrapsAntimeridian() {
        return minLongitude > maxLongitude;
    }

    /** Folds a longitude that ran off one side of the map back onto the other. */
    private static double wrap(double longitude) {
        double shifted = (longitude + EAST_EDGE) % 360d;
        if (shifted < 0) {
            shifted += 360d;
        }
        return shifted + WEST_EDGE;
    }
}
