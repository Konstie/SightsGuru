package com.sightsguru.app.data.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class Place {
    public String address;
    public String description;
    public double lat;
    @PropertyName("long") public double lng;
    public String title;
    @PropertyName("wiki_url") public String wikiUrl;
    @PropertyName("image_url") public String imageUrl;
    public int year;

    @Override
    public String toString() {
        return "Place{" +
                "address='" + address + '\'' +
                ", description='" + description + '\'' +
                ", lat=" + lat +
                ", lng=" + lng +
                ", title='" + title + '\'' +
                ", wikiUrl='" + wikiUrl + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", year=" + year +
                '}';
    }
}
