# EuroWorks Desktop

[![Build Status](https://img.shields.io/badge/build-success-brightgreen.svg)](#)
[![Java Version](https://img.shields.io/badge/Java-26-orange.svg)](#)
[![Spring Boot](https://img.shields.io/badge/Spring--Boot-4.1.0-blue.svg)](#)

**EuroWorks** is a retro-inspired, modern desktop environment built for Java systems. Heavily inspired by the classic **GeoWorks** (PC-GEOS/BreadBox Ensemble) and Windows 95 aesthetics, it recreates the vintage pixel look while utilizing modern multi-threaded execution, 3D raycasting, and hardware-accelerated 2D graphics.

---

![EuroWorks Desktop Shell Screenshot](assets/screenshot-20260704.png)

## 🌟 Key Features

### 🖥️ The Desktop Shell
* **Express Launcher**: A bottom-left pop-up menu providing access to all system applications, utilities, and shutdown commands.
* **3D Taskbar**: A silver taskbar with authentic 3D raised and sunken styles, utilizing checkered dither patterns to indicate active and inactive applications.
* **Taskbar Clock**: A sunken 24-hour digital clock widget updating in real-time.
* **Window Management**: Classic cascading and tiling options for organized multitasking.
* **Color Customization**: Instantly customize the desktop backdrop color (Retro Teal, Navy Blue, VGA Gray, Forest Green, Dark Red).

### 📁 Built-in Applications
* **EuroManager (File Manager)**: A vintage file tree navigator with lazy-loaded drive/directory structures and double-click navigation.
* **EuroMandelbrot (Fractal Explorer)**: An advanced multi-pass progressive refinement fractal renderer (16x16 down to 1x1 block passes) with interactive drag-zoom and retro color palettes (Rainbow, Fire, Ice-Blue, Original GEOS Rings).
* **EuroPreferences (Control Panel)**: Settings center to customize desktop backgrounds, toggle Speaker Emulation, outline window dragging, and configure screensavers.
* **Retro Mock Applications**: Authentic mocks of classic suites like `EuroWrite`, `EuroDraw`, `EuroCalc`, and `EuroFile`.

### 🌌 Retro Screensavers
* **EuroStarfield**: 3D fly-through space simulator. Simulates motion blur using projected speed-streak trails, adjustable star count (up to 5000 "Cosmic" stars), and spacecraft camera-roll rotation.
* **EuroMaze**: A first-person 3D maze walk-through powered by a custom DDA (Digital Differential Analysis) raycaster writing directly to raster buffers, featuring realistic brick textures.
* **EuroPipes**: 3D perspective pipes generator with 90-degree bends and color-cycling paths.
* **EuroBezier**: Bouncing, multi-point color-cycling Bezier curves inspired by classic screensaver loops.

---

## 🛠️ Architecture & Technology Stack

* **Core**: Java 26 & Swing / AWT.
* **Graphics**: Direct pixel manipulation on buffer arrays backed by `DataBufferInt` to bypass standard graphics pipelines for high-performance 3D rendering (raycasting and starfield loops).
* **Build Tool**: Maven.
* **Application Framework**: Spring Boot.

---

## 🚀 Getting Started

### Prerequisites
* **Java Development Kit (JDK) 26** or higher.
* Maven wrapper (`mvnw`) included in the project directory.

### Build and Package
To clean and package the project into a runnable JAR, execute:
```bash
./mvnw clean package
```

### Launch the Shell
Run the application using Spring Boot:
```bash
./mvnw spring-boot:run
```

---

## ⚙️ Settings & Inactivity Timer
By default, EuroWorks monitors user activity (mouse and keyboard input). If the system detects inactivity for the duration set in `EuroPreferences` (default: 10 seconds), the selected Screensaver automatically launches in a borderless, undecorated fullscreen mode. Move the mouse or press any key to instantly dismiss the screensaver and return to the desktop.
