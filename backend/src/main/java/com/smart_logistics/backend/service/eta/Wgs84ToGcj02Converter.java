package com.smart_logistics.backend.service.eta;

final class Wgs84ToGcj02Converter {

    private static final double PI = Math.PI;
    private static final double SEMI_MAJOR_AXIS = 6378245.0;
    private static final double ECCENTRICITY_SQUARED = 0.00669342162296594323;

    private Wgs84ToGcj02Converter() {
    }

    static Coordinate convert(double longitude, double latitude) {
        if (outsideChina(longitude, latitude)) {
            return new Coordinate(longitude, latitude);
        }
        double latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0);
        double longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0);
        double radianLatitude = latitude / 180.0 * PI;
        double magic = Math.sin(radianLatitude);
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        latitudeOffset = latitudeOffset * 180.0
                / ((SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQUARED))
                / (magic * sqrtMagic) * PI);
        longitudeOffset = longitudeOffset * 180.0
                / (SEMI_MAJOR_AXIS / sqrtMagic * Math.cos(radianLatitude) * PI);
        return new Coordinate(longitude + longitudeOffset, latitude + latitudeOffset);
    }

    private static boolean outsideChina(double longitude, double latitude) {
        return longitude < 72.004 || longitude > 137.8347
                || latitude < 0.8293 || latitude > 55.8271;
    }

    private static double transformLatitude(double x, double y) {
        double result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * PI)
                + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(y * PI)
                + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        result += (160.0 * Math.sin(y / 12.0 * PI)
                + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return result;
    }

    private static double transformLongitude(double x, double y) {
        double result = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * PI)
                + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(x * PI)
                + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        result += (150.0 * Math.sin(x / 12.0 * PI)
                + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return result;
    }

    record Coordinate(double longitude, double latitude) {
    }
}
