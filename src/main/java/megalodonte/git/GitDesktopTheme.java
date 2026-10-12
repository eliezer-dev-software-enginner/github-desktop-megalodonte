package megalodonte.git;

import megalodonte.base.theme.ThemeColors;
import megalodonte.theme.DefaultTheme;

/** Dark palette sampled from the installed GitHub Desktop renderer stylesheet. */
public final class GitDesktopTheme extends DefaultTheme {
    @Override public ThemeColors colors() {
        return new ThemeColors(
                "#24292e", "#1d2125", "#2f363d", "#959da5",
                "#f6f8fa", "#959da5", "#444d56", "#959da5",
                "#444d56", "#535a61", "#2b3137",
                "#28a745", "#dbab09", "#d73a49");
    }
}
