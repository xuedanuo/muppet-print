package com.xuesinuo.muppet;

import java.awt.*;
import javax.imageio.ImageIO;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Timer;
import java.util.TimerTask;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.microsoft.playwright.Playwright;
import com.xuesinuo.muppet.vertx.WebVerticle;

@SpringBootApplication
public class UiStarter {

    public static volatile int appState = 0; // 0:停止 1:正在启动 2:运行 3:正在关闭
    public static volatile ApplicationContext springContext;

    public static final String port = "58080";

    private static final Frame frame = new Frame();
    private static final Label portTitelLabel = new Label("Web Port:");
    private static final Label portLabel = new Label("");
    private static final TextField portTextField = new TextField(4);
    private static final Label statusLabel = new Label("Status: Stopped");
    private static final Button runButton = new Button("Run");
    private static final Button stopButton = new Button("Stop");
    private static final Label messageLabel = new Label("");
    private static final Checkbox autoStartCheckbox = new Checkbox("auto start on boot");

    // 系统托盘相关
    private static TrayIcon trayIcon;
    private static SystemTray systemTray;

    public static void main(String[] args) {
        frame.setTitle("Muppet Printer");
        // 设置自定义图标，假设图标文件为 app.png 放在 resources 目录下
        java.awt.Image icon = null;
        try (java.io.InputStream iconStream = UiStarter.class.getResourceAsStream("/app.png")) {
            if (iconStream != null) {
                icon = ImageIO.read(iconStream);
                frame.setIconImage(icon);
            }
        } catch (Exception e) {
            e.printStackTrace();// 忽略图标加载失败
        }
        frame.setSize(0, 0);
        frame.setResizable(false);
        frame.setLayout(null);// NULL布局，绝对坐标值布局
        // 托盘支持
        if (SystemTray.isSupported()) {
            systemTray = SystemTray.getSystemTray();
            if (icon == null) {
                // fallback icon
                icon = Toolkit.getDefaultToolkit().createImage(new byte[0]);
            }
            PopupMenu popupMenu = new PopupMenu();
            MenuItem openItem = new MenuItem("Open");
            openItem.addActionListener(e -> {
                frame.setVisible(true);
                frame.setState(Frame.NORMAL);
                frame.toFront();
            });
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                System.exit(0);
            });
            popupMenu.add(openItem);
            popupMenu.addSeparator();
            popupMenu.add(exitItem);
            trayIcon = new TrayIcon(icon, "Muppet Printer", popupMenu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> {
                frame.setVisible(true);
                frame.setState(Frame.NORMAL);
                frame.toFront();
            });
            try {
                systemTray.add(trayIcon);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 关闭窗口时隐藏到托盘
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    frame.setVisible(false);
                }
            });
        } else {
            // 不支持托盘，直接关闭
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
        }
        frame.setVisible(true);
        Insets insets = frame.getInsets();
        int titleBarHeight = insets.top; // 标题栏高度
        frame.setVisible(false);
        Label label = new Label("Loading...");
        frame.add(label);
        Font defaultFont = label.getFont();
        int fs = defaultFont.getSize(); // 字体大小
        int hfs = fs / 2 + fs % 2;
        frame.setSize(70 * hfs, 40 * hfs);// 窗口大小

        /*
         * 布局计算方式：
         * 
         * hfs - half font size 半个字体大小，作为基本高度单元。
         * 
         * 每行内容占用空间5hfs：字符行高4hfs，上间距1hfs。
         * 
         * 标准行 y = 行号 * 5 + 1 (hfs)
         * 
         * 标准行 height = 4 (hfs)
         */

        // 端口
        portTitelLabel.setBounds(2 * hfs, 1 * hfs + titleBarHeight, 12 * hfs, 4 * hfs);
        frame.add(portTitelLabel);

        portLabel.setBounds(14 * hfs, 1 * hfs + titleBarHeight, 14 * hfs, 4 * hfs);
        portLabel.setVisible(false);
        frame.add(portLabel);

        portTextField.setBounds(14 * hfs, 1 * hfs + titleBarHeight, 14 * hfs, 4 * hfs);
        portTextField.setText(port);
        frame.add(portTextField);

        // 状态
        statusLabel.setBounds(2 * hfs, 6 * hfs + titleBarHeight, 46 * hfs, 4 * hfs);
        frame.add(statusLabel);

        // 运行/停止按钮
        runButton.setBounds(2 * hfs, 11 * hfs + titleBarHeight, 20 * hfs, 4 * hfs);
        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startSpringApplication();
            }
        });
        frame.add(runButton);

        stopButton.setBounds(24 * hfs, 11 * hfs + titleBarHeight, 20 * hfs, 4 * hfs);
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopSpringApplication();
            }
        });
        frame.add(stopButton);

        // 开机自启动复选框
        autoStartCheckbox.setBounds(2 * hfs, 16 * hfs + titleBarHeight, 25 * hfs, 4 * hfs);
        autoStartCheckbox.setState(isAutoStartEnabled());
        autoStartCheckbox.addItemListener(e -> {
            if (autoStartCheckbox.getState()) {
                enableAutoStart();
            } else {
                disableAutoStart();
            }
        });
        frame.add(autoStartCheckbox);

        // 消息标签
        messageLabel.setForeground(Color.RED);
        messageLabel.setBounds(2 * hfs, 21 * hfs + titleBarHeight, 60 * hfs, 4 * hfs);
        frame.add(messageLabel);

        frame.setVisible(true);

        // 检查Playwright插件
        messageLabel.setText("Loading update. Please wait...");
        new Thread(() -> {
            try (Playwright playwright = Playwright.create()) {
                messageLabel.setText("Update completed.");
                startSpringApplication();
                messageLabel.setText("");
            } catch (Exception e) {
                e.printStackTrace();
                messageLabel.setText("Setup failed!" + e.getMessage());
                return;
            }
        }).start();
    }

    /**
     * 判断当前是否已设置开机自启动
     */
    private static boolean isAutoStartEnabled() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows: 检查启动文件夹是否有快捷方式
            try {
                String startup = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup";
                String exePath = getNativeExePath();
                String exeName = new java.io.File(exePath).getName();
                java.io.File lnk = new java.io.File(startup, exeName + ".lnk");
                return lnk.exists();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else if (os.contains("mac")) {
            // macOS: 检查LaunchAgent
            try {
                String userHome = System.getProperty("user.home");
                String plistPath = userHome + "/Library/LaunchAgents/com.xuesinuo.muppet.plist";
                return new java.io.File(plistPath).exists();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    /**
     * 启用开机自启动
     */
    private static void enableAutoStart() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows: 复制exe的快捷方式到启动文件夹
            try {
                String startup = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup";
                String exePath = getNativeExePath();
                String exeName = new java.io.File(exePath).getName();
                String lnkPath = startup + "\\" + exeName + ".lnk";
                createWindowsShortcut(exePath, lnkPath);
            } catch (Exception e) {
                e.printStackTrace();
                error("自启动设置失败: " + e.getMessage());
            }
        } else if (os.contains("mac")) {
            // macOS: 写入LaunchAgent plist，使用 open -a 启动 .app，保证 Dock 图标正常
            try {
                String userHome = System.getProperty("user.home");
                String plistPath = userHome + "/Library/LaunchAgents/com.xuesinuo.muppet.plist";
                String appPath = getNativeAppPath();
                String plist = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                        "<plist version=\"1.0\">\n" +
                        "<dict>\n" +
                        "    <key>Label</key>\n" +
                        "    <string>com.xuesinuo.muppet</string>\n" +
                        "    <key>ProgramArguments</key>\n" +
                        "    <array>\n" +
                        "        <string>open</string>\n" +
                        "        <string>-a</string>\n" +
                        "        <string>" + appPath + "</string>\n" +
                        "    </array>\n" +
                        "    <key>RunAtLoad</key>\n" +
                        "    <true/>\n" +
                        "</dict>\n" +
                        "</plist>\n";
                java.nio.file.Files.write(java.nio.file.Paths.get(plistPath), plist.getBytes());
            } catch (Exception e) {
                e.printStackTrace();
                error("自启动设置失败: " + e.getMessage());
            }
        }
    }

    /**
     * 关闭开机自启动
     */
    private static void disableAutoStart() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            try {
                String startup = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup";
                String exePath = getNativeExePath();
                String exeName = new java.io.File(exePath).getName();
                java.io.File lnk = new java.io.File(startup, exeName + ".lnk");
                if (lnk.exists()) lnk.delete();
            } catch (Exception e) {
                e.printStackTrace();
                error("自启动取消失败: " + e.getMessage());
            }
        } else if (os.contains("mac")) {
            try {
                String userHome = System.getProperty("user.home");
                String plistPath = userHome + "/Library/LaunchAgents/com.xuesinuo.muppet.plist";
                java.io.File plist = new java.io.File(plistPath);
                if (plist.exists()) plist.delete();
            } catch (Exception e) {
                e.printStackTrace();
                error("自启动取消失败: " + e.getMessage());
            }
        }
    }

    // 获取原生exe路径（Windows打包后）
    private static String getNativeExePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String exePath = getAppExePath();
        if (os.contains("win")) {
            String programFiles = System.getenv("ProgramFiles");
            String exe = programFiles + "\\MuppetPrint\\MuppetPrint.exe";
            if (new java.io.File(exe).exists()) return exe;
        }
        return exePath;
    }

    // 获取原生app路径（macOS打包后）
    private static String getNativeAppPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            String appBundle = "/Applications/MuppetPrint.app";
            if (new java.io.File(appBundle).exists()) return appBundle;
        }
        // fallback: 返回 jar 路径
        return getAppJarPath();
    }

    // 获取当前应用exe路径（如果是exe/dmg打包）
    private static String getAppExePath() {
        try {
            return new java.io.File(UiStarter.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsolutePath();
        } catch (Exception e) {
            return System.getProperty("java.class.path");
        }
    }

    // 获取当前应用jar路径（macOS用）
    private static String getAppJarPath() {
        try {
            return new java.io.File(UiStarter.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsolutePath();
        } catch (Exception e) {
            return System.getProperty("java.class.path");
        }
    }

    // 创建Windows快捷方式（调用powershell）
    private static void createWindowsShortcut(String target, String lnkPath) throws Exception {
        String ps = "$WshShell = New-Object -ComObject WScript.Shell; " +
                "$Shortcut = $WshShell.CreateShortcut('" + lnkPath.replace("\\", "\\\\") + "'); " +
                "$Shortcut.TargetPath = '" + target.replace("\\", "\\\\") + "'; " +
                "$Shortcut.Save();";
        Process p = Runtime.getRuntime().exec(new String[] { "powershell", "-Command", ps });
        p.waitFor();
    }

    /**
     * UI上显示错误信息
     */
    public static void error(String msg) {
        messageLabel.setText(msg);
    }

    /**
     * 启动Spring服务
     */
    public static void startSpringApplication() {
        Integer port = null;
        String message = "";
        String portString = portTextField.getText();
        try {
            if (portString == null || portString.isBlank()) {
                throw new RuntimeException("Warning: Please enter a port");
            }
            port = Integer.parseInt(portString);
            if (port < 1024 || port > 65535) {
                throw new RuntimeException("Warning: Port must be an integer between 1024 and 65535");
            }
        } catch (NumberFormatException nfe) {
            message = "Warning: Port must be an integer between 1024 and 65535";
        } catch (RuntimeException re) {
            message = re.getMessage();
        }
        if (!message.isBlank()) {
            final String msg = message;
            messageLabel.setText(msg);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (msg.equals(messageLabel.getText())) {
                        messageLabel.setText("");
                    }
                }
            }, 5000);
            return;
        }
        if (port == null) {
            return;
        }

        synchronized (UiStarter.class) {
            if (appState != 0) {
                return;
            }
            appState = 1;
            statusLabel.setText("Status: Starting...");
            portTextField.setVisible(false);
            portLabel.setText("" + port);
            portLabel.setVisible(true);
        }
        WebVerticle.port = port;
        new Thread(() -> {
            try {
                springContext = SpringApplication.run(UiStarter.class, new String[0]);
                System.out.println("Startup complete: " + springContext);
                statusLabel.setText("Status: Ready !");
                appState = 2;
            } catch (Exception ex) {
                appState = 0;
                statusLabel.setText("Status: Stopped");
                portLabel.setVisible(false);
                portTextField.setVisible(true);
            }
        }).start();
    }

    /**
     * 停止Spring服务
     */
    public static void stopSpringApplication() {
        synchronized (UiStarter.class) {
            if (appState != 2) {
                return;
            }
            appState = 3;
            statusLabel.setText("Status: Stopping...");
        }
        new Thread(() -> {
            SpringApplication.exit(springContext);
            springContext = null;
            appState = 0;
            statusLabel.setText("Status: Stopped");
            portLabel.setVisible(false);
            portTextField.setVisible(true);
        }).start();
    }
}
