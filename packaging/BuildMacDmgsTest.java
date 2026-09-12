import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** C06 checks run against physical images, before DMG generation. */
public class BuildMacDmgsTest {
    public static void main(String[] args) throws Exception {
        var build = new BuildImages(Path.of(""), BuildImages.Target.current());
        BuildImages.require(!build.target.windows(), "Run on Apple Silicon");
        Path temp = Files.createTempDirectory(build.output, "c06-tests-");
        BuildImagesTest.test("Mac JDK 25 support preserves Windows 25.0.1 pin", () -> {
            var win = new BuildImages.Target(true, "win");
            BuildImages.require(BuildImages.supportedJdk(build.target, Runtime.Version.parse("25"), "Oracle Corporation"), "Mac GA");
            BuildImages.require(BuildImages.supportedJdk(win, Runtime.Version.parse("25.0.1"), "Oracle Corporation"), "Windows pin");
            BuildImages.require(!BuildImages.supportedJdk(win, Runtime.Version.parse("25"), "Oracle Corporation"), "Windows pin changed");
            BuildImages.require(!BuildImages.supportedJdk(build.target, Runtime.Version.parse("26"), "Oracle Corporation")
                    && !BuildImages.supportedJdk(build.target, Runtime.Version.parse("25"), "Other vendor"), "Toolchain guard");
        });
        BuildImagesTest.test("only two DMGs wrap app-images without private content or paid signing", () -> {
            for (String product : BuildMacDmgs.PRODUCTS) {
                var command = BuildMacDmgs.command(build, product);
                BuildImages.require(command.get(command.indexOf("--type") + 1).equals("dmg")
                        && command.contains("--app-image") && !command.contains("--input")
                        && !command.contains("--mac-dmg-content") && !command.contains("--mac-sign"), "DMG recipe");
                BuildImages.require(BuildMacDmgs.artifactName(product).equals("ExaSecure-" + (product.equals("student") ? "Student" : "Operator")
                        + "-2.0.0-macos-aarch64.dmg"), "Artifact name");
            }
            BuildImagesTest.rejected(() -> BuildMacDmgs.artifactName("admin"));
            BuildImagesTest.rejected(() -> BuildMacDmgs.artifactName("../server"));
        });
        BuildImagesTest.test("ICNS chunks decode at all declared sizes and are deterministic", () -> {
            Path a = temp.resolve("a.icns"), b = temp.resolve("b.icns");
            Path source = build.root.resolve("exasecure-client-common/src/main/resources/icons/app_icon.png");
            BuildImages.icon(source, a, false); BuildImages.icon(source, b, false);
            BuildImages.require(Files.mismatch(a, b) == -1 && Files.mismatch(a, build.output.resolve("app.icns")) == -1, "Canonical icon reproducibility");
            ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(a));
            BuildImages.require(bytes.getInt() == 0x69636e73 && bytes.getInt() == bytes.capacity(), "ICNS envelope");
            String[] codes = {"ic07", "ic08", "ic09", "ic10"};
            for (int i = 0; i < codes.length; i++) {
                byte[] code = new byte[4]; bytes.get(code);
                BuildImages.require(new String(code, java.nio.charset.StandardCharsets.US_ASCII).equals(codes[i]), "Chunk type");
                byte[] png = new byte[bytes.getInt() - 8]; bytes.get(png);
                var image = ImageIO.read(new java.io.ByteArrayInputStream(png));
                BuildImages.require(image != null && image.getWidth() == 128 << i && image.getHeight() == 128 << i, "Chunk dimensions");
            }
            BuildImages.require(!bytes.hasRemaining(), "Trailing ICNS content");
            for (String product : BuildMacDmgs.PRODUCTS)
                BuildImages.require(Files.mismatch(a, BuildMacDmgs.image(build, product).resolve("Contents/Resources/" + BuildMacDmgs.role(product).name() + ".icns")) == -1, "Bundle icon provenance");
        });
        BuildImagesTest.test("bundle inventory contains links and detects escapes and file mutation", () -> {
            Path dir = Files.createDirectory(temp.resolve("bundle")), file = dir.resolve("file");
            Files.writeString(file, "before"); Files.createSymbolicLink(dir.resolve("internal"), Path.of("file"));
            var before = BuildMacDmgs.inventory(dir);
            Files.writeString(file, "after");
            BuildImages.require(!before.equals(BuildMacDmgs.inventory(dir)), "Mutation missed");
            Files.createSymbolicLink(dir.resolve("escape"), aFile(temp));
            BuildImagesTest.rejected(() -> BuildMacDmgs.inventory(dir));
        });
        for (String product : BuildMacDmgs.PRODUCTS)
            BuildImagesTest.test(product + " physical bundle, staging, classpaths and arm64 closure", () -> BuildMacDmgs.auditBundle(build, product));
        System.out.println("Tests: " + BuildImagesTest.passed + ", failures: 0, errors: 0, skipped: 0");
    }
    static Path aFile(Path dir) throws Exception { return Files.writeString(dir.resolve("outside"), "outside"); }
}
