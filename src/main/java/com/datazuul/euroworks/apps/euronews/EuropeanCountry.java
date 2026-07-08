package com.datazuul.euroworks.apps.euronews;

public enum EuropeanCountry {
    ALBANIA("al", "Albanien"),
    ANDORRA("ad", "Andorra"),
    AUSTRIA("at", "Österreich"),
    BELARUS("by", "Belarus"),
    BELGIUM("be", "Belgien"),
    BOSNIA("ba", "Bosnien und Herzegowina"),
    BULGARIA("bg", "Bulgarien"),
    CROATIA("hr", "Kroatien"),
    CYPRUS("cy", "Zypern"),
    CZECHIA("cz", "Tschechien"),
    DENMARK("dk", "Dänemark"),
    ESTONIA("ee", "Estland"),
    FINLAND("fi", "Finnland"),
    FRANCE("fr", "Frankreich"),
    GERMANY("de", "Deutschland"),
    GREECE("gr", "Griechenland"),
    HUNGARY("hu", "Ungarn"),
    ICELAND("is", "Island"),
    IRELAND("ie", "Irland"),
    ITALY("it", "Italien"),
    KOSOVO("xk", "Kosovo"),
    LATVIA("lv", "Lettland"),
    LIECHTENSTEIN("li", "Liechtenstein"),
    LITHUANIA("lt", "Litauen"),
    LUXEMBOURG("lu", "Luxemburg"),
    MALTA("mt", "Malta"),
    MOLDOVA("md", "Moldawien"),
    MONACO("mc", "Monaco"),
    MONTENEGRO("me", "Montenegro"),
    NETHERLANDS("nl", "Niederlande"),
    NORTH_MACEDONIA("mk", "Nordmazedonien"),
    NORWAY("no", "Norwegen"),
    POLAND("pl", "Polen"),
    PORTUGAL("pt", "Portugal"),
    ROMANIA("ro", "Rumänien"),
    RUSSIA("ru", "Russland"),
    SAN_MARINO("sm", "San Marino"),
    SERBIA("rs", "Serbien"),
    SLOVAKIA("sk", "Slowakei"),
    SLOVENIA("si", "Slowenien"),
    SPAIN("es", "Spanien"),
    SWEDEN("se", "Schweden"),
    SWITZERLAND("ch", "Schweiz"),
    TURKEY("tr", "Türkei"),
    UKRAINE("ua", "Ukraine"),
    UNITED_KINGDOM("gb", "Vereinigtes Königreich"),
    VATICAN("va", "Vatikanstadt");

    private final String code;
    private final String name;

    EuropeanCountry(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
