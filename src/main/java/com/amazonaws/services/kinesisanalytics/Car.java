package com.amazonaws.services.kinesisanalytics;

import java.sql.Timestamp;

/**
 * Car POJO class
 */
public class Car 
{
    // use this to avoid any serialization deserialization used within Flink
    public static final long serialVersionUID = 42L;
    public Car() {

    }
    public Car(String vehicleId, Timestamp timestamp, Boolean hasMoonRoof, Double speed) {
        this.vehicleId = vehicleId;
        this.moonRoof = hasMoonRoof;
        this.timestamp = timestamp;
        this.speed = speed;

    }
    private String vehicleId;
    private Boolean moonRoof;
    private Double speed;
    private Timestamp timestamp;

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
    public Timestamp getTimestamp() {
        return this.timestamp;
    }
    public void setMoonRoof(boolean m) {
        this.moonRoof = m;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }
    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
    public String getVehicleId() {
        return this.vehicleId ;
    }

    public Boolean getMoonRoof() {
        return moonRoof;
    }

    public Double getSpeed() {
        return speed;
    }

    public String toString() {
        return "VEHICLE: " + vehicleId + " TIMESTAMP: " + timestamp + " MOONROOF: " + moonRoof + " Speed: " + speed;
    }


}