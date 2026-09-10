import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Checks installer policy against the fresh, actual C04 images before wrapping them. */
public class BuildWindowsInstallersTest {
    public static void main(String[] args) throws Exception {
        var build = new BuildImages(Path.of(""), new BuildImages.Target(true, "win"));
        int passed = 0;
        var xml = DocumentBuilderFactory.newInstance();
        xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var pom = xml.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        BuildImages.require(pom.getDocumentElement().getElementsByTagName("version").item(0).getTextContent().equals(BuildImages.VERSION), "Repository version mismatch");
        BuildImages.require(BuildImages.NATIVE_VERSION.matches("\\d+\\.\\d+\\.\\d+"), "Windows version must be numeric");
        passed++;
        BuildImages.require(new HashSet<>(BuildWindowsInstallers.UPGRADE_IDS.values()).size() == 2, "Upgrade families must differ");
        for (String id : BuildWindowsInstallers.UPGRADE_IDS.values()) UUID.fromString(id);
        passed++;
        for (String product : List.of("student", "operator")) {
            List<String> command = BuildWindowsInstallers.command(build, product);
            BuildImages.require(command.get(command.indexOf("--type") + 1).equals("exe"), "EXE required");
            BuildImages.require(command.contains("--app-image") && !command.contains("--input") && !command.contains("--runtime-image"), "Wrap C04 image only");
            BuildImages.require(command.contains("--win-menu") && !command.contains("--win-shortcut") && !command.contains("--win-per-user-install"), "Machine install, main menu only");
            BuildImages.require(BuildWindowsInstallers.artifactName(product).endsWith("-" + BuildImages.NATIVE_VERSION + "-windows-x64.exe"), "Artifact version/architecture");
            passed++;
            String name = product.equals("student") ? "ExaSecure Student" : "ExaSecure Operator";
            var state = xml.newDocumentBuilder().parse(build.output.resolve("images/" + name + "/app/.jpackage.xml").toFile());
            var additional = state.getElementsByTagName("add-launcher");
            BuildImages.require(state.getElementsByTagName("WIN_SHORTCUT_DESKTOP").getLength() == 0, "No desktop shortcuts");
            BuildImages.require(additional.getLength() == (product.equals("student") ? 0 : 2), "Product launcher inventory");
            for (int i = 0; i < additional.getLength(); i++) {
                var launcher = (org.w3c.dom.Element) additional.item(i);
                BuildImages.require(launcher.getElementsByTagName("WIN_SHORTCUT_START_MENU").getLength() == 0
                        && launcher.getElementsByTagName("WIN_SHORTCUT_DESKTOP").getLength() == 0, "Internal launcher shortcuts must be disabled in image metadata");
                for (String key : List.of("menu-hint", "shortcut-hint")) {
                    var hint = launcher.getElementsByTagName(key);
                    BuildImages.require(hint.getLength() == 1 && hint.item(0).getTextContent().equals("false"), "Missing explicit internal shortcut override");
                }
            }
            passed++;
        }
        System.out.println("Tests: " + passed + ", failures: 0, errors: 0, skipped: 0");
    }
}
