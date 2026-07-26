import java.awt.*;
import java.awt.event.*;

public class MenuExample {
    public static void main(String[] args) {
        // Create a frame
        Frame frame = new Frame("App");

        // Create a menu bar
        MenuBar mb = new MenuBar();
        frame.setMenuBar(mb);

        // Create a "File" menu
        Menu fileMenu = new Menu("File");
        fileMenu.add(new MenuItem("Open"));
        fileMenu.add(new MenuItem("Save"));
        fileMenu.addSeparator();

        // Add Exit item
        MenuItem exit = new MenuItem("Exit");
        fileMenu.add(exit);

        // Add File menu to menu bar
        mb.add(fileMenu);

        // Exit action
        exit.addActionListener(e -> System.exit(0));

        // Handle window close (important!)
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                System.exit(0);
            }
        });

        // Set frame size and make it visible
        frame.setSize(400, 300);
        frame.setVisible(true);
    }
}
