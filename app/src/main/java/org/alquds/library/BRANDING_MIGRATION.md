# Al-Quds Library branding requirements

Package: `org.alquds.library`. Preserve the package and update-signing identity.

- Application identity: مكتبة القدس الجديدة. Do not introduce NEXVARY names, monograms, watermarks or decorative marks into runtime screens or assets.
- Use professionally prepared, colorful PNG/WebP icon assets for navigation and categories, with an identifiable subject and a consistent size/style. Do not use improvised Canvas pictograms, monochrome line symbols or text emoji as substitutes for the requested assets. Record their source and license.
- The current source has a local catalog screen and a vector launcher placeholder; it does not contain the home/category/favorites screens or the logo shown in the user's legacy screenshots. Their removal/replacement cannot be claimed for that legacy version until its source is available.
- Stage 11 replaces the launcher placeholder and adds bundled Microsoft Fluent 3D PNGs to the catalog, search, favorites and theme controls. Source commit, SHA-256 checksums and MIT license are in `third_party/fluent-emoji/`; the license is also packaged in the APK assets. Visual acceptance results are tracked in `docs/VISUAL_IDENTITY.md`. This changes the available catalog screen only.
- Keep controls labeled in Arabic, RTL, accessible touch targets, and meaningful content descriptions for actionable images. Decorative images should not duplicate spoken labels.
