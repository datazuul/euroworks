package com.datazuul.euroworks.apps.euronews;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NewsService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) EuroWorks-EuroNews/1.0";
    
    // General European RSS feed for fallback/filtering
    private static final String EURONEWS_FEED_DE = "https://de.euronews.com/rss?level=theme&name=news";
    private static final String EURONEWS_FEED_EN = "https://www.euronews.com/rss?level=theme&name=news";

    static final Map<EuropeanCountry, String> SPECIFIC_FEEDS = new HashMap<>();

    static {
        SPECIFIC_FEEDS.put(EuropeanCountry.GERMANY, "https://www.tagesschau.de/xml/rss2/");
        SPECIFIC_FEEDS.put(EuropeanCountry.AUSTRIA, "https://rss.orf.at/news.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.SWITZERLAND, "https://www.srf.ch/news/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.UNITED_KINGDOM, "https://feeds.bbci.co.uk/news/world/rss.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.FRANCE, "https://www.france24.com/en/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.ITALY, "https://www.ansa.it/sito/ansait_rss.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.SPAIN, "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/internacional/portada");
        SPECIFIC_FEEDS.put(EuropeanCountry.NETHERLANDS, "https://feeds.nos.nl/nosnieuwsalgemeen");
        SPECIFIC_FEEDS.put(EuropeanCountry.BELGIUM, "https://www.vrt.be/vrtnws/nl.rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.SWEDEN, "https://www.svt.se/nyheter/rss.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.NORWAY, "https://www.nrk.no/nyheter/siste.rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.DENMARK, "https://www.dr.dk/nyheder/service/feeds/alleneuheder");
        SPECIFIC_FEEDS.put(EuropeanCountry.FINLAND, "https://yle.fi/uutiset/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.IRELAND, "https://www.rte.ie/news/rss/news-headlines.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.POLAND, "https://www.polskieradio.pl/rss/8.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.UKRAINE, "https://www.ukrinform.net/block/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.PORTUGAL, "https://www.rtp.pt/noticias/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.GREECE, "https://greekreporter.com/feed/");
        SPECIFIC_FEEDS.put(EuropeanCountry.CZECHIA, "https://english.radio.cz/rss/news");
        SPECIFIC_FEEDS.put(EuropeanCountry.ROMANIA, "https://www.hotnews.ro/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.BULGARIA, "https://www.dnevnik.bg/rss/");
        SPECIFIC_FEEDS.put(EuropeanCountry.HUNGARY, "https://telex.hu/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.CROATIA, "https://www.index.hr/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.SLOVAKIA, "https://rss.sme.sk/rss/sme-sekcia-domov");
        SPECIFIC_FEEDS.put(EuropeanCountry.SLOVENIA, "https://www.rtvslo.si/feeds/novice.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.ICELAND, "https://www.ruv.is/rss/frettir");
        SPECIFIC_FEEDS.put(EuropeanCountry.ESTONIA, "https://news.err.ee/rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.LATVIA, "https://eng.lsm.lv/rss/?lang=en");
        SPECIFIC_FEEDS.put(EuropeanCountry.LITHUANIA, "https://www.lrt.lt/en/news-in-english?rss");
        SPECIFIC_FEEDS.put(EuropeanCountry.BELARUS, "https://euroradio.fm/en/rss.xml");
        SPECIFIC_FEEDS.put(EuropeanCountry.TURKEY, "https://www.trtworld.com/rss/");
    }

    private final HttpClient httpClient;

    public NewsService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    /**
     * Fetches the top 3 news articles for a country.
     * Searches a specific feed if configured, and falls back to filtering general European feeds.
     */
    public List<NewsItem> getNewsForCountry(EuropeanCountry country) throws Exception {
        List<NewsItem> result = new ArrayList<>();
        String specificUrl = SPECIFIC_FEEDS.get(country);

        // 1. Try specific RSS feed if available
        if (specificUrl != null) {
            try {
                String xmlContent = fetchXml(specificUrl);
                List<NewsItem> items = parseRssXml(xmlContent, country.getName() + " News");
                if (items != null && !items.isEmpty()) {
                    result.addAll(items.subList(0, Math.min(items.size(), 5)));
                    return result;
                }
            } catch (Exception e) {
                System.err.println("EuroNews: Failed to load specific feed for " + country.getName() + ": " + e.getMessage());
            }
        }

        // 2. Fallback: Query and filter general European feeds (German first, then English)
        try {
            List<NewsItem> filtered = getFilteredNews(EURONEWS_FEED_DE, country, "EuroNews (DE)");
            if (filtered.size() < 5) {
                List<NewsItem> filteredEn = getFilteredNews(EURONEWS_FEED_EN, country, "EuroNews (EN)");
                for (NewsItem item : filteredEn) {
                    if (filtered.size() < 5 && filtered.stream().noneMatch(x -> x.getTitle().equalsIgnoreCase(item.getTitle()))) {
                        filtered.add(item);
                    }
                }
            }
            if (!filtered.isEmpty()) {
                result.addAll(filtered.subList(0, Math.min(filtered.size(), 5)));
                return result;
            }
        } catch (Exception e) {
            System.err.println("EuroNews: Fallback filtering failed for " + country.getName() + ": " + e.getMessage());
        }

        // If we found absolutely nothing, throw an exception so the UI shows an error/no-news message
        throw new Exception("Keine aktuellen Nachrichten für " + country.getName() + " erreichbar.");
    }

    private List<NewsItem> getFilteredNews(String feedUrl, EuropeanCountry country, String sourceName) {
        List<NewsItem> matches = new ArrayList<>();
        try {
            String xmlContent = fetchXml(feedUrl);
            List<NewsItem> allItems = parseRssXml(xmlContent, sourceName);

            // Keywords to match the country
            Set<String> keywords = new HashSet<>();
            keywords.add(country.getName().toLowerCase());
            keywords.add(country.getCode().toLowerCase());
            
            // Basic English/German variations
            String englishName = new Locale("", country.getCode()).getDisplayCountry(Locale.ENGLISH).toLowerCase();
            keywords.add(englishName);
            
            // Special mappings
            if (country == EuropeanCountry.AUSTRIA) {
                keywords.add("österreich");
                keywords.add("austria");
                keywords.add("wien");
            } else if (country == EuropeanCountry.SWITZERLAND) {
                keywords.add("schweiz");
                keywords.add("switzerland");
                keywords.add("swiss");
            } else if (country == EuropeanCountry.GERMANY) {
                keywords.add("deutschland");
                keywords.add("germany");
                keywords.add("berlin");
            } else if (country == EuropeanCountry.UNITED_KINGDOM) {
                keywords.add("london");
                keywords.add("großbritannien");
                keywords.add("grossbritannien");
                keywords.add("england");
                keywords.add("uk");
                keywords.add("united kingdom");
                keywords.add("britische");
                keywords.add("briten");
            } else if (country == EuropeanCountry.FRANCE) {
                keywords.add("frankreich");
                keywords.add("france");
                keywords.add("paris");
            }

            for (NewsItem item : allItems) {
                String titleLower = item.getTitle().toLowerCase();
                String descLower = item.getDescription().toLowerCase();

                boolean isMatch = false;
                for (String kw : keywords) {
                    if (titleLower.contains(kw) || descLower.contains(kw)) {
                        isMatch = true;
                        break;
                    }
                }

                if (isMatch) {
                    matches.add(item);
                }
            }
        } catch (Exception e) {
            System.err.println("EuroNews: Failed to search general feed: " + e.getMessage());
        }
        return matches;
    }

    private String fetchXml(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("HTTP status " + response.statusCode());
        }
        return response.body();
    }

    private List<NewsItem> parseRssXml(String xml, String sourceName) throws Exception {
        if (xml != null) {
            xml = xml.trim();
            // Sanitize raw ampersands that are not XML entities
            xml = xml.replaceAll("&(?!(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)", "&amp;");
            if (xml.startsWith("<!DOCTYPE html") || xml.startsWith("<html")) {
                throw new Exception("Server lieferte eine HTML-Seite statt eines RSS-Feeds.");
            }
        }
        List<NewsItem> list = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        NodeList items = doc.getElementsByTagName("item");
        if (items.getLength() == 0) {
            items = doc.getElementsByTagName("entry"); // Atom support
        }

        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                
                String title = getElementText(element, "title");
                
                String link = getElementText(element, "link");
                if (link.isEmpty()) {
                    NodeList linkList = element.getElementsByTagName("link");
                    if (linkList.getLength() > 0) {
                        Element lElem = (Element) linkList.item(0);
                        link = lElem.getAttribute("href");
                    }
                }
                
                String description = getElementText(element, "description");
                if (description.isEmpty()) {
                    description = getElementText(element, "summary");
                }
                if (description.isEmpty()) {
                    description = getElementText(element, "content");
                }
                
                // Strip HTML and clean whitespace
                description = description.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
                if (description.length() > 800) {
                    description = description.substring(0, 797) + "...";
                }
                
                String pubDate = getElementText(element, "pubDate");
                if (pubDate.isEmpty()) {
                    pubDate = getElementText(element, "published");
                }
                if (pubDate.isEmpty()) {
                    pubDate = getElementText(element, "updated");
                }

                // Clean and format pub date
                pubDate = cleanPubDate(pubDate);

                String imageUrl = null;
                // 1. Check enclosure
                NodeList enclosures = element.getElementsByTagName("enclosure");
                if (enclosures.getLength() > 0) {
                    for (int j = 0; j < enclosures.getLength(); j++) {
                        Element enc = (Element) enclosures.item(j);
                        String type = enc.getAttribute("type");
                        if (type != null && type.contains("image")) {
                            imageUrl = enc.getAttribute("url");
                            break;
                        }
                    }
                }

                // 2. Check media:content / content with url
                if (imageUrl == null || imageUrl.isEmpty()) {
                    NodeList mediaContents = element.getElementsByTagName("media:content");
                    if (mediaContents.getLength() == 0) {
                        mediaContents = element.getElementsByTagName("content");
                    }
                    for (int j = 0; j < mediaContents.getLength(); j++) {
                        Element med = (Element) mediaContents.item(j);
                        String urlAttr = med.getAttribute("url");
                        String medium = med.getAttribute("medium");
                        String type = med.getAttribute("type");
                        if (urlAttr != null && !urlAttr.isEmpty()) {
                            if ("image".equals(medium) || (type != null && type.contains("image")) || urlAttr.matches("(?i).*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?")) {
                                imageUrl = urlAttr;
                                break;
                            }
                        }
                    }
                }

                // 3. Check media:thumbnail
                if (imageUrl == null || imageUrl.isEmpty()) {
                    NodeList thumbnails = element.getElementsByTagName("media:thumbnail");
                    if (thumbnails.getLength() > 0) {
                        Element thumb = (Element) thumbnails.item(0);
                        imageUrl = thumb.getAttribute("url");
                    }
                }

                // 4. Check HTML img in content:encoded or raw description before it was stripped
                if (imageUrl == null || imageUrl.isEmpty()) {
                    String encodedContent = getElementText(element, "content:encoded");
                    if (encodedContent.isEmpty()) {
                        encodedContent = getElementText(element, "encoded");
                    }
                    imageUrl = extractImgSrc(encodedContent);
                }
                if (imageUrl == null || imageUrl.isEmpty()) {
                    String rawDesc = getElementText(element, "description");
                    imageUrl = extractImgSrc(rawDesc);
                }

                if (!title.isEmpty()) {
                    list.add(new NewsItem(title, description, pubDate, link, sourceName, imageUrl));
                }
            }
        }
        return list;
    }

    private String extractImgSrc(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() > 0) {
            Node n = nl.item(0);
            if (n != null) {
                return n.getTextContent().trim();
            }
        }
        return "";
    }

    private String cleanPubDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) {
            return "Heute";
        }
        // Basic date format cleaning, e.g. "Wed, 08 Jul 2026 19:00:00 GMT" -> "08.07.2026" or similar
        // Just extract date portion for simplicity and clean look
        if (rawDate.length() > 16) {
            // Check if it starts with weekday, e.g., "Wed, "
            if (rawDate.contains(",")) {
                int commaIdx = rawDate.indexOf(",");
                return rawDate.substring(commaIdx + 1, commaIdx + 12).trim();
            }
            // Check for ISO date "2026-07-08T19:00:00Z"
            if (rawDate.contains("T")) {
                int tIdx = rawDate.indexOf("T");
                return rawDate.substring(0, tIdx);
            }
            return rawDate.substring(0, 16);
        }
        return rawDate;
    }
}
