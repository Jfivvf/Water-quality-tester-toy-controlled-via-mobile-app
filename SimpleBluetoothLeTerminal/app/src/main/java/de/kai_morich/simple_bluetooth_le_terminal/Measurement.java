package de.kai_morich.simple_bluetooth_le_terminal;

public class Measurement {
    private String created_at;
    private double temperature;
    private double salinity;
    private String location;
    private Double latitude;
    private Double longitude;

    public Measurement(String created_at, double temperature, double salinity, String location, Double latitude, Double longitude) {
        this.created_at = created_at;
        this.temperature = temperature;
        this.salinity = salinity;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Measurement(String created_at, double temperature, double salinity) {
        this(created_at, temperature, salinity, null, null, null);
    }

    public String getCreatedAt() {
        return created_at;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getSalinity() {
        return salinity;
    }

    public String getLocation() {
        return location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
