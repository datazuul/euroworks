package com.datazuul.euroworks.apps.euronews;

public class NewsItem {
    private final String title;
    private final String description;
    private final String date;
    private final String link;
    private final String source;
    private final String imageUrl;

    public NewsItem(String title, String description, String date, String link, String source, String imageUrl) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.link = link;
        this.source = source;
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getDate() {
        return date;
    }

    public String getLink() {
        return link;
    }

    public String getSource() {
        return source;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public String toString() {
        return "NewsItem{" +
                "title='" + title + '\'' +
                ", source='" + source + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}
