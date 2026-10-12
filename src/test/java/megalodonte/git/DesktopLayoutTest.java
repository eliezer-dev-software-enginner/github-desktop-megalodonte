package megalodonte.git;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import megalodonte.base.theme.ThemeManager;
import megalodonte.script.AppConfig;
import megalodonte.script.ScriptApplication;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DesktopLayoutTest {
    @Test void workspaceStaysInsideShortDesktopWindow() throws Exception {
        var ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        var task = new FutureTask<Void>(() -> {
            ThemeManager.setTheme(new GitDesktopTheme());
            try (var app = new ScriptApplication(AppConfig.load(Desktop.class.getClassLoader()), Desktop.class.getClassLoader())) {
                var host = (javafx.scene.layout.StackPane) app.render().getJavaFxNode();
                var root = (VBox) host.getChildren().getFirst();
                new Scene(host, 1366, 684);
                var description = (javafx.scene.control.TextArea) root.lookup(".text-area");
                assertNotNull(description);
                assertTrue(description.getStyle().contains("#444d56"), "TextArea border must use the app theme");
                assertTrue(description.getStylesheets().stream().anyMatch(path -> path.endsWith("text-area.css")));
                var diff = (javafx.scene.control.ListView<?>) root.lookup(".megalodonte-diff-view");
                assertNotNull(diff);
                assertTrue(diff.getStyle().contains("-fx-focus-color: transparent"), "Diff focus must not use Modena blue");
                app.current().states().set("pushTitle", "7 commits aguardando push");
                app.current().states().set("pushHint", "Para origin/main · comparado ao último fetch");
                app.current().states().set("pendingPush", true);
                var notice = app.current().component("pushNotice").getJavaFxNode();
                assertTrue(notice.isVisible(), "pending commits must reveal the push notice");
                for (int[] size : new int[][]{{1366, 684}, {1050, 560}}) {
                    host.resize(size[0], size[1]); host.applyCss(); host.layout();
                    var header = root.getChildren().getFirst();
                    var workspace = root.getChildren().get(3);
                    var footer = root.getChildren().getLast();
                    assertTrue(header.getBoundsInParent().getMinY() >= 0, "header above window");
                    assertTrue(footer.getBoundsInParent().getMaxY() <= size[1] + 1, "footer below window");
                    assertTrue(workspace.getBoundsInParent().getMaxY() <= size[1] + 1, "workspace below window");
                    assertTrue(((Region) workspace).getHeight() > 200, "workspace collapsed");
                    assertTrue(notice.getBoundsInParent().getMaxY() <= size[1], "push notice below window");
                    if (size[0] == 1366) {
                        var snapshot = host.snapshot(null, null);
                        var png = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
                            png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        var path = java.nio.file.Path.of("build/preview/git-desktop-layout.png");
                        java.nio.file.Files.createDirectories(path.getParent());
                        javax.imageio.ImageIO.write(png, "png", path.toFile());
                    }
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
