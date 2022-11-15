package com.siliconvalve.models;

public class TrackingEntity {
    private String PartitionKey;
    private String RowKey;
    private String PubDate;
    private String Guid;

    public String getPartitionKey() { return this.PartitionKey; }
    public void setPartitionKey(String key) { this.PartitionKey = key; }
    public String getRowKey() { return this.RowKey; }
    public void setRowKey(String key) { this.RowKey = key; }
    public String getPubDate() { return this.PubDate; }
    public void setPubDate(String pubDate) { this.PubDate = pubDate; }
    public String getGuid() { return this.Guid; }
    public void setGuid(String guid) { this.Guid = guid; }
}