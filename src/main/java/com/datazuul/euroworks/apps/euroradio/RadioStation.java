package com.datazuul.euroworks.apps.euroradio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RadioStation {
    @JsonProperty("name")
    private String name;

    @JsonProperty("url")
    private String url;

    @JsonProperty("codec")
    private String codec;

    @JsonProperty("bitrate")
    private int bitrate;

    @JsonProperty("stationuuid")
    private String stationuuid;

    public RadioStation() {
    }

    public RadioStation(String name, String url) {
        this.name = name;
        this.url = url;
        this.codec = "MP3";
    }

    public RadioStation(String name, String url, String codec, int bitrate, String stationuuid) {
        this.name = name;
        this.url = url;
        this.codec = codec;
        this.bitrate = bitrate;
        this.stationuuid = stationuuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public String getStationuuid() {
        return stationuuid;
    }

    public void setStationuuid(String stationuuid) {
        this.stationuuid = stationuuid;
    }

    @Override
    public String toString() {
        return name;
    }
}
