package Models;

import java.util.Date;

public class AQIData {
    private int id;
    private long userId;
    private int aqiValue;
    private String status; // Good, Moderate, Unhealthy, etc.
    private Date recordedAt;
    private String serverId;
    private String city;
    private String state;
    private String country;

    // Constructor for new AQI data
    public AQIData(long userId, int aqiValue, String status, Date recordedAt) {
        this.userId = userId;
        this.aqiValue = aqiValue;
        this.status = status;
        this.recordedAt = recordedAt;
    }

    // Constructor for database retrieval
    public AQIData(int id, long userId, int aqiValue, String status, Date recordedAt, String serverId) {
        this.id = id;
        this.userId = userId;
        this.aqiValue = aqiValue;
        this.status = status;
        this.recordedAt = recordedAt;
        this.serverId = serverId;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public int getAqiValue() { return aqiValue; }
    public void setAqiValue(int aqiValue) { this.aqiValue = aqiValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Date recordedAt) { this.recordedAt = recordedAt; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getLocationKey() {
        return city + ", " + state + ", " + country;
    }
}
