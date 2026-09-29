# Fonts

The UI uses the bundled `Xover-text.ttf` font. It is loaded from the classpath
as `fonts/Xover-text.ttf` by `XoverFontFamily` in
`src/main/kotlin/com/xover/music/ui/theme/Theme.kt` and applied through
`xoverTypography()`.

The same font file is registered for Normal, Medium, SemiBold, and Bold weights.
Keep this directory included in the main resources so the font is available
both during development and in packaged distributions.

I also recommend that you review the [NOTICE](NOTICE.md).
