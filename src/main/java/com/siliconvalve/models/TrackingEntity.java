package com.siliconvalve.models;

public class TrackingEntity {
    private String PartitionKey;
    private String RowKey;
    private String DataValue;

    public String getPartitionKey() { return this.PartitionKey; }
    public void setPartitionKey(String key) { this.PartitionKey = key; }
    public String getRowKey() { return this.RowKey; }
    public void setRowKey(String key) { this.RowKey = key; }
    public String getLastReadItemDateTime() { return this.DataValue; }
    public void setLastReadItemDateTime(String dataValue) { this.DataValue = dataValue; }
}