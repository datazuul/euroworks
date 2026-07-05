package com.datazuul.euroworks.shell;

import java.awt.Font;

import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;

public class EuroRetroTheme extends DefaultMetalTheme {

    @Override
    public String getName() {
        return "EuroWorks Retro Theme";
    }

    // --- Active/Primary Colors (Steel Blue / Teal Accents) ---
    @Override
    protected ColorUIResource getPrimary1() {
        // Active border, focused title bar border
        return new ColorUIResource(0, 90, 110);
    }

    @Override
    protected ColorUIResource getPrimary2() {
        // Focused components, selected text highlights
        return new ColorUIResource(0, 120, 150);
    }

    @Override
    protected ColorUIResource getPrimary3() {
        // Selected background, focused title bar background
        return new ColorUIResource(200, 230, 240);
    }

    // --- Inactive/Secondary Colors (Classic Light Gray / Gray) ---
    @Override
    protected ColorUIResource getSecondary1() {
        // Inactive borders, shadows
        return new ColorUIResource(100, 100, 100);
    }

    @Override
    protected ColorUIResource getSecondary2() {
        // Inactive title bars, focused border highlights
        return new ColorUIResource(150, 150, 150);
    }

    @Override
    protected ColorUIResource getSecondary3() {
        // Main panel backgrounds, button backgrounds, control backgrounds (#D4D0C8)
        return new ColorUIResource(212, 208, 200);
    }

    // --- Retro Pixelated Fonts (Monospaced / Courier New) ---
    @Override
    public FontUIResource getControlTextFont() {
        return new FontUIResource("Courier New", Font.PLAIN, 12);
    }

    @Override
    public FontUIResource getSystemTextFont() {
        return new FontUIResource("Courier New", Font.PLAIN, 12);
    }

    @Override
    public FontUIResource getUserTextFont() {
        return new FontUIResource("Courier New", Font.PLAIN, 12);
    }

    @Override
    public FontUIResource getMenuTextFont() {
        return new FontUIResource("Courier New", Font.BOLD, 12);
    }

    @Override
    public FontUIResource getWindowTitleFont() {
        return new FontUIResource("Courier New", Font.BOLD, 12);
    }

    @Override
    public FontUIResource getSubTextFont() {
        return new FontUIResource("Courier New", Font.PLAIN, 10);
    }
}
