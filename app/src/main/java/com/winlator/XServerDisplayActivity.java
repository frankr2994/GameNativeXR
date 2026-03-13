package com.winlator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.box86_64.rc.RCFile;
import com.winlator.box86_64.rc.RCManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.contents.ContentsManager;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineRequestHandler;
import com.winlator.inputcontrols.ControllerManager;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.renderer.GLRenderer;
import com.winlator.widget.XServerView;
import com.winlator.winhandler.WinHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.ImageFs;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.BionicProgramLauncherComponent;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.xenvironment.components.NetworkInfoUpdateComponent;
import com.winlator.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.xenvironment.components.XServerComponent;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import app.gamenative.R;

public class XServerDisplayActivity extends AppCompatActivity {
    private XServerView xServerView;
    private XEnvironment environment;
    public Container container;
    private XServer xServer;
    private ImageFs imageFs;
    private Shortcut shortcut;
    private WineInfo wineInfo;
    private SharedPreferences preferences;
    private WinHandler winHandler;
    private WineRequestHandler wineRequestHandler;
    private ContentsManager contentsManager;

    private float lastFPS = 0;
    private long lastTime = 0;
    private int frameCount = 0;

    private String screenEffectProfile;

    boolean isMouseDisabled;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
        setContentView(R.layout.xserver_display_activity);

        ControllerManager.getInstance().init(this);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        // Initialize the WinHandler after context is set up
        winHandler = new WinHandler(getXServer(), getXServerView());
        // Inside the XServerDisplayActivity class
        ExternalController controller = winHandler.getCurrentController();

        if (isOpenWithAndroidBrowser || isShareAndroidClipboard)
            wineRequestHandler = new WineRequestHandler(this);

        if (controller != null) {
            int triggerType = preferences.getInt("trigger_type", ExternalController.TRIGGER_IS_AXIS); // Default to TRIGGER_IS_AXIS
            controller.setTriggerType((byte) triggerType); // Cast to byte if needed
        }

        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        Menu menu = ((NavigationView)findViewById(R.id.NavigationView)).getMenu();
        if (XrActivity.isEnabled(this)) {
            menu.findItem(R.id.main_menu_input_controls).setVisible(false);
            menu.findItem(R.id.main_menu_touchpad_help).setVisible(false);
        }
        menu.findItem(R.id.main_menu_toggle_fullscreen).setVisible(false);

        imageFs = ImageFs.find(this);

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        ContainerManager containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getStringExtra("container_id"));

        // Log shortcut_path
        String shortcutPath = getIntent().getStringExtra("shortcut_path");
        Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);


        // Determine container ID
        String containerId = getIntent().getStringExtra("container_id");
        Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);
        if (containerId == null) {
            Log.d("XServerDisplayActivity", "Container ID is null");
        }

        // Retrieve the container and check if it's null
        container = containerManager.getContainerById(containerId);

        if (container == null) {
            Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
            finish();  // Gracefully exit the activity to avoid crashing
            return;
        }

        containerManager.activateContainer(container);

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            shortcut = new Shortcut(container, new File(shortcutPath));
        }

        String wineVersion = container.getWineVersion();
        wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);

        imageFs.setWinePath(wineInfo.path);

        ProcessHelper.removeAllDebugCallbacks();

        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        String dxwrapper = container.getDXWrapper();
        String dxwrapperConfig = container.getDXWrapperConfig();
        screenSize = container.getScreenSize();

        // Log the entire intent to verify the extras
        Intent intent = getIntent();
        Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());

        if (shortcut != null) {
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
            dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            screenSize = shortcut.getExtra("screenSize", container.getScreenSize());
            String xinputDisabledString = shortcut.getExtra("disableXinput", "false");
            boolean xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);
            // Pass the value to WinHandler
            String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");
            if (!sharpnessEffect.equals("None")) {
                double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));
                double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));
                String vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness=" + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100 + ";" + "dlsDenoise=" + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";
            }
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);
        }

        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);

        boolean[] winStarted = {false};

        // Add the OnWindowModificationListener for dynamic workarounds
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    xServerView.getRenderer().setCursorVisible(true);
                    winStarted[0] = true;
                }
                frameCount++;
            }

            private void setProcessAffinity(Window window, int processAffinity) {

                int processId = window.getProcessId();

                if (processId > 0) {
                    winHandler.setProcessAffinity(processId, processAffinity);
                } else if (!window.getClassName().isEmpty()) {
                    winHandler.setProcessAffinity(window.getClassName(), processAffinity);
                }
            }

            @Override
            public void onMapWindow(Window window) {

                String cpuList = container.getCPUList(true);

                // If a shortcut exists, let its setting override the container's default.
                if (shortcut != null) {
                    cpuList = shortcut.getExtra("cpuList", container.getCPUList(true));
                }

                // Calculate the final mask from the determined CPU list.
                short taskAffinityMask = (short) ProcessHelper.getAffinityMask(cpuList);

                // Apply the affinity mask.
                if (taskAffinityMask > 0) {
                    setProcessAffinity(window, taskAffinityMask);
                }
            }
        });

        Runnable runnable = () -> {
            setupUI();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    setupXEnvironment();
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }

            });
        };

        runnable.run();
    }

    private boolean parseBoolean(String value) {
        // Return true for "true", "1", "yes" (case-insensitive)
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        // Return false for any other value, including "false", "0", "no"
        return false;
    }


    public float getLastFPS() {
        if (lastTime == 0) lastTime = SystemClock.elapsedRealtime();
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            lastTime = time;
            frameCount = 0;
        }
        return lastFPS;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        ProcessHelper.resumeAllWineProcesses();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Check if we are entering Picture-in-Picture mode
        if (!isInPictureInPictureMode()) {
            // Only pause environment and xServerView if not in PiP mode
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }
        }

        ProcessHelper.pauseAllWineProcesses();
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        // Clear any temporary directory
        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());

        // Create the appropriate launcher based on the container type
        GuestProgramLauncherComponent guestProgramLauncherComponent;

        // Reference to BionicProgramLauncherComponent
        BionicProgramLauncherComponent bionicLauncher = new BionicProgramLauncherComponent(
                contentsManager,
                contentsManager.getProfileByEntryName(container.getWineVersion())
        );
        guestProgramLauncherComponent = bionicLauncher;

        // Additional container checks and environment configuration
        if (container != null) {
            bionicLauncher.setContainer(this.container);
            bionicLauncher.setWineInfo(this.wineInfo);
            boolean wow64Mode = container.isWoW64Mode();
            // Construct the guest executable command
            String guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();
            // (Alternatively: "wine wineboot -u" or anything else you want)

            // Set up the guest program parameters
            guestProgramLauncherComponent.setWoW64Mode(wow64Mode);
            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            // Bind any drive paths the container defines
            ArrayList<String> bindingPaths = new ArrayList<>();
            for (String[] drive : container.drivesIterator()) {
                bindingPaths.add(drive[1]);
            }
            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            // Box86/64 presets from container or shortcut
            guestProgramLauncherComponent.setBox64Preset(
                    shortcut != null
                            ? shortcut.getExtra("box64Preset", container.getBox64Preset())
                            : container.getBox64Preset()
            );
        }

        // Create our overall XEnvironment with various components
        environment = new XEnvironment(this, imageFs);
        environment.addComponent(
                new SysVSharedMemoryComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)
                )
        );
        environment.addComponent(
                new XServerComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)
                )
        );


        environment.addComponent(new NetworkInfoUpdateComponent());

        // RC (box86_64rc) file handling
        RCManager manager = new RCManager(this);
        manager.loadRCFiles();
        int rcfileId = shortcut == null
                ? container.getRCFileId()
                : Integer.parseInt(shortcut.getExtra("rcfileId", String.valueOf(container.getRCFileId())));
        RCFile rcfile = manager.getRcfile(rcfileId);

        File file = new File(container.getRootDir(), ".box64rc");
        String str = rcfile == null ? "" : rcfile.generateBox86_64rc();
        FileUtils.writeString(file, str);

        // Pass final envVars to the launcher
        guestProgramLauncherComponent.setTerminationCallback((status) -> finish());

        // Add the launcher to our environment
        environment.addComponent(guestProgramLauncherComponent);

        // Start all environment components (XServer, Audio, etc.)
        environment.startEnvironmentComponents();

        // Start the WinHandler
        winHandler.start();

        // Properly initialize the WineRequestHandler with all necessary context before starting it
        if (wineRequestHandler != null) {
            wineRequestHandler.setContainer(this.container);
            wineRequestHandler.setShortcut(this.shortcut);
            wineRequestHandler.setWineInfo(this.wineInfo);
            wineRequestHandler.start();
        }
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        final GLRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);

        if (shortcut != null) {
            if (shortcut.getExtra("forceFullscreen", "0").equals("1")) renderer.setForceFullscreenWMClass(shortcut.wmClass);
            renderer.setUnviewableWMClasses("explorer.exe");
        }

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        isMouseDisabled = preferences.getBoolean("touchscreen_mouse_disabled", false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (!winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||
                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }

    private String getWineStartCommand() {
        // Define default arguments
        String args = "";

        if (shortcut != null) {
            String execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " " + execArgs : "";

            if (shortcut.path.endsWith(".lnk")) {
                args += "\"" + shortcut.path + "\"" + execArgs;
            } else {
                String exeDir = FileUtils.getDirname(shortcut.path);
                String filename = FileUtils.getName(shortcut.path);

                int dotIndex = filename.lastIndexOf(".");
                int spaceIndex = (dotIndex != -1) ? filename.indexOf(" ", dotIndex) : -1;

                if (spaceIndex != -1) {
                    execArgs = filename.substring(spaceIndex + 1) + execArgs;
                    filename = filename.substring(0, spaceIndex);
                }

                args += "/dir " + StringUtils.escapeDOSPath(exeDir) + " \"" + filename + "\"" + execArgs;
            }
        } else {
            args += "\"wfm.exe\"";
        }
        // Construct the final command
        return "winhandler.exe " + args;
    }

    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public String getScreenSize() {
        if (shortcut != null) {
            return shortcut.getExtra("screenSize", container.getScreenSize());
        }
        return getContainer().getScreenSize();
    }

    public String getScreenEffectProfile() {
        return screenEffectProfile;
    }

    public void setScreenEffectProfile(String screenEffectProfile) {
        this.screenEffectProfile = screenEffectProfile;
    }
}
