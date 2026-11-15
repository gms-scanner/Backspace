package dev.goldforge;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Regression checks for LaunchWrapper locations and release-candidate ordering. */
public final class UpdaterHarness {
    public static void main(String[] args) throws Exception {
        Path expected=Paths.get(System.getProperty("java.io.tmpdir"),"Backspace path with spaces","backspace.jar").toAbsolutePath().normalize();
        URL file=expected.toUri().toURL();
        if(!expected.equals(BackspaceUpdater.installedPath(file)))throw new AssertionError("file location");
        if(!expected.equals(BackspaceUpdater.installedPath(new URL("jar:"+file+"!/"))))throw new AssertionError("jar location");
        String[] ordered={"dev-4.2.6","dev-4.3.0-dev","dev-4.3.0-beta2","dev-4.3.0-rc1","dev-4.3.0-rc2","dev-4.3.0-rc3","dev-4.3.0","dev-4.3.1"};
        long previous=-1;
        for(String tag:ordered){long value=BackspaceUpdater.version(tag);if(value<=previous)throw new AssertionError("Version order: "+tag);previous=value;}
        if(BackspaceUpdater.version("broken")!=-1||BackspaceUpdater.version("dev-999999999999999999999.1")!=-1)throw new AssertionError("Invalid versions");
        System.out.println("Updater harness: PASS (file and jar locations, escaped spaces, release ordering)");
    }
}
