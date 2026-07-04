# Implementation Plan: PC/GEOS User Applications in Java Swing

This document outlines the design and implementation plan for recreating the core applications of **PC/GEOS** (Geoworks Ensemble) using Java Swing. 

---

## 1. Architectural Foundation

To recreate the look, feel, and functionality of PC/GEOS, we will employ the following Java Swing mechanisms:

*   **Multitasking Desktop Shell:** A main `JFrame` containing a `JDesktopPane` which manages multiple `JInternalFrame` windows. This directly mimics the windowed multitasking environment of PC/GEOS.
*   **Retro Look-and-Feel (L&F):** Custom border renderers, retro fonts (such as a monospaced or custom pixel font), and a strict EGA/VGA 16-color palette (including the signature teal background, dark grey borders, and bevels).
*   **Vector Engine & Graphic Rendering:** Extended `JPanel` components overriding `paintComponent` to render vintage UI widgets, icons, and canvas elements using `Graphics2D` with antialiasing disabled to preserve the pixelated retro feel.

---

## 2. PC/GEOS Core Applications

Below is the list of user applications we plan to implement, including their functional description, Swing design components, and visual mockups.

### 2.1 GeoManager (The Desktop Shell & File Manager)
*   **Description:** The system shell of PC/GEOS. It allows users to browse files, launch applications, and manage directories.
*   **Swing Design:**
    *   Left panel: `JTree` representing the folder structure.
    *   Right panel: A custom `JPanel` rendering pixelated folder and file icons, responding to double-clicks to open files/apps.
    *   Standard menu bar with "File", "View", and "Options".
*   **Mockup Screenshot:**
    ![GeoManager Desktop Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geomanager.png)

---

### 2.2 GeoWrite (Word Processor)
*   **Description:** A WYSIWYG word processor featuring styled text, page layouts, ruler bars, and font customization.
*   **Swing Design:**
    *   Main canvas: `JTextPane` wrapped in a `JScrollPane`.
    *   Ruler bar: Custom painted `JPanel` at the top showing margins.
    *   Toolbar: Buttons for Bold, Italic, Underline, and dropdown selectors for Fonts and Font Sizes.
*   **Mockup Screenshot:**
    ![GeoWrite Word Processor Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geowrite.png)

---

### 2.3 GeoDraw (Vector Graphic Editor)
*   **Description:** An illustration program for drawing lines, shapes, and polygons, supporting grouping and layering.
*   **Swing Design:**
    *   Drawing Canvas: Custom `JPanel` tracking mouse events (`MouseListener`, `MouseMotionListener`) to draw geometric paths using `Graphics2D`.
    *   Toolbox: Vertical bar with tools for selection, line, rectangle, ellipse, and polyline.
    *   Color Palette: `JPanel` displaying the 16-color VGA palette at the bottom to set foreground/background colors.
*   **Mockup Screenshot:**
    ![GeoDraw Graphics Editor Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geodraw.png)

---

### 2.4 GeoDex (Address Book / Rolodex)
*   **Description:** A cardfile-style digital Rolodex for managing contacts.
*   **Swing Design:**
    *   Main View: A layout displaying fields for Name, Address, Phone, and Notes.
    *   A-Z Tabs: A vertical or horizontal grid of `JToggleButton` representing alphabet letters to instantly jump to contacts starting with that letter.
    *   Navigation: Standard record-navigation buttons (First, Prev, Next, Last) at the bottom.
*   **Mockup Screenshot:**
    ![GeoDex Address Book Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geodex.png)

---

### 2.5 GeoPlanner (Calendar & Scheduler)
*   **Description:** A personal organizer showing daily, weekly, and monthly views with time slot scheduling.
*   **Swing Design:**
    *   Time Schedule: A custom `JTable` or list of panels representing hourly slots.
    *   Mini-Calendar: A simple 7x6 grid of buttons for quick day selection.
    *   Event Dialog: A pop-up dialog using standard Swing inputs (`JTextField`, `JComboBox`) to add or edit appointments.
*   **Mockup Screenshot:**
    ![GeoPlanner Calendar Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geoplanner.png)

---

### 2.6 GeoCalc (Spreadsheet)
*   **Description:** A spreadsheet application featuring cells, row/column management, and basic arithmetic formula execution.
*   **Swing Design:**
    *   Spreadsheet Grid: A customized `JTable` using cell renderers to draw classic cell borders.
    *   Formula Entry: A `JTextField` synced with the selected cell to display and edit formulas.
    *   Engine: A simple parser executing expressions like `=SUM(A1:A5)` or `=A1+B1`.
*   **Mockup Screenshot:**
    ![GeoCalc Spreadsheet Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geocalc.png)

---

### 2.7 GeoFile (Form-Based Database Manager)
*   **Description:** A database tool that allows users to design simple custom forms and query database records.
*   **Swing Design:**
    *   Form Canvas: A layout pane where users can position labels and data fields.
    *   Data Source: Backed by a standard model connecting to local storage (e.g. SQLite, JSON database files).
*   **Mockup Screenshot:**
    ![GeoFile Database Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/geofile.png)

---

### 2.8 Preferences (System Settings)
*   **Description:** The centralized control panel for adjusting system preferences, UI colors, font scales, and mock drivers.
*   **Swing Design:**
    *   Categories: A selection panel displaying icons for Display, Mouse, Sound, Keyboard, and Printers.
    *   Detail Views: A `CardLayout` changing options panels based on the selected category icon.
*   **Mockup Screenshot:**
    ![Preferences Control Panel Mockup](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/docs/images/preferences.png)

---

## 3. Recommended Implementation Phases

1.  **Phase 1: Shell & Window Manager:** Implement the desktop background (`JDesktopPane`) and the core system menu wrapper.
2.  **Phase 2: Custom Retro Look-and-Feel (L&F):** Set up custom components, borders, and colors to guarantee retro styling.
3.  **Phase 3: File Manager & Preferences:** Implement `GeoManager` (directory browsing) and `Preferences` (settings changes).
4.  **Phase 4: Productivity Utilities:** Progressively build out `GeoWrite`, `GeoDraw`, `GeoCalc`, and other suite components.
