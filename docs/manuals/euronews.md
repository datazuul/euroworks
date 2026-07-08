# EuroNews – User Manual

EuroNews is a graphical news aggregator application for the EuroWorks desktop suite. It aggregates and displays the top 5 real-time news stories for European countries, styled with classic pixel aesthetics and integrated with the EuroWorks desktop environment.

---

## Features

### 1. Alphabetical Country List
- Displays all European countries alongside their corresponding high-resolution SVG flags loaded from the classpath (`images/flags/`).
- **Dynamic Sorting:** The list is sorted alphabetically at runtime using their localized German names (e.g., *Ungarn* is sorted under *U*, *Tschechien* under *T*, and *Zypern* under *Z*).
- **Search & Filter:** A filter bar (`Land filtern...`) at the top of the list allows searching for countries by typing. The list updates instantly, keeping the correct alphabetical order.

### 2. Multi-Card News Display
- Displays the top **5** news stories for the selected country.
- Shows up to **10 lines of text** (up to 800 characters) per news card for readable and detailed previews.
- **Asynchronous Image Loading:** Automatically extracts images from standard RSS elements (`enclosure`, `media:content`, `media:thumbnail`) or falls back to parsing embedded HTML `<img>` tags inside `<content:encoded>` CDATA blocks (such as those in the *Tagesschau* feed). These are loaded in a background thread and displayed on the left side of the news cards.
- **Micro-animations:** News cards feature smooth hover animations (background color highlights and hand cursors) to make the interface feel alive.
- **Copy Link:** A "Kopieren" button copies the article's source URL to the clipboard.
- **Read More:** A "Mehr lesen..." button opens the link.

### 3. Integrated Web Browsing
When clicking "Mehr lesen...", EuroNews interacts with the EuroWorks desktop pane:
- It checks if the built-in **EuroWeb** browser is already open.
- If open, it brings EuroWeb to the front and navigates directly to the article URL.
- If closed, it automatically launches a new instance of **EuroWeb** preloaded with the article.
- If EuroWeb is not available or encounters issues, it falls back to the default system browser.

### 4. Translation Option ("Übersetzen")
- For non-German speaking countries, news cards show an **"Übersetzen"** button.
- Clicking it translates the title and description into German using a background thread (so the UI never freezes).
- Once translated, the button toggles to **"Original"**, letting you switch back to the original text.
- Translated texts are cached in memory to avoid redundant API requests.

### 5. Preferences & Settings (Einstellungen)
- A **"⚙ Einstellungen"** button in the header opens a preferences dialog.
- **Font Size Configuration:** You can customize the font size of the news items. Choosing a font size automatically rescales the title, description, meta info, and action buttons. The updates apply immediately to currently displayed news items.
- **Configurable RSS Feeds:** A scrollable and editable table (`JTable`) displays all 47 European countries and their RSS feed URLs. You can edit any URL directly in the table. If a feed is left blank, the app will fall back to filtering general European news.
- **Persistent Storage:** Settings (including base font size and the customized RSS feed URLs) are saved to `~/.euroworks/euronews/settings.json` and are loaded automatically whenever EuroNews starts.

---

## Technical Architecture

### Component Classes
- **[EuropeanCountry](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/src/main/java/com/datazuul/euroworks/apps/euronews/EuropeanCountry.java):** Enum holding the country definitions, codes, and localized German names.
- **[NewsItem](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/src/main/java/com/datazuul/euroworks/apps/euronews/NewsItem.java):** Data model representing a news story.
- **[NewsService](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/src/main/java/com/datazuul/euroworks/apps/euronews/NewsService.java):** Manages RSS feed mappings and fetches feed XML using Java's `HttpClient`. It sanitizes invalid XML characters (like unescaped `&` ampersands) and handles HTML error-page detection before sending content to the XML DOM parser.
- **[TranslationService](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/src/main/java/com/datazuul/euroworks/apps/euronews/TranslationService.java):** Connects to the translation API and parses the JSON response using Jackson's `ObjectMapper`.
- **[EuroNews](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/src/main/java/com/datazuul/euroworks/apps/euronews/EuroNews.java):** The main swing user interface extending `EuroAppFrame`.

---

## Future Improvements (Verbesserungsmöglichkeiten)

### Local Offline Translation with Argos Translate

Currently, EuroNews relies on the online Google Translate GTX API to perform translations, which requires an active internet connection and communicates with external Google servers. 

A high-priority future improvement is to replace Google Translate with **Argos Translate**, an open-source, offline translation library running locally in Java.

#### 1. Maven Dependency
Add the following dependency to `pom.xml`:

```xml
<dependency>
    <groupId>com.github.argosopentech</groupId>
    <artifactId>argos-translate-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 2. Usage Implementation Example
In `TranslationService.java`, initialize Argos Translate and load language models (e.g. English-German, Hungarian-German) package-embedded in resources:

```java
import org.argosopentech.translate.Translate;

public class Demo {
    public static void main(String[] args) throws Exception {
        Translate translate = new Translate();

        // Load model (file from resources)
        translate.loadModel("models/de-en.argosmodel");

        // Translate text locally offline
        String result = translate.translate("Hallo Welt", "de", "en");
        System.out.println(result);
    }
}
```

#### Advantages
*   **100% Offline Capability:** Works without internet access.
*   **Privacy:** No user data or news content is transmitted to external translation APIs.
*   **Reliability:** Free from network timeouts or API rate-limiting blocks.
