package com.datazuul.euroworks.apps.eurotv;

public class EuroTvStream {
    private String name;
    private String url;

    public EuroTvStream() {
    }

    public EuroTvStream(String name, String url) {
        this.name = name;
        this.url = url;
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

    @Override
    public String toString() {
        return name + " (" + url + ")";
    }
}
