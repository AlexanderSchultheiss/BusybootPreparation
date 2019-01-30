package net.ssehub.kernel_haven.busyboot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;

/**
 * The Class PrepareCoreboot implements the Interface IPreparetion and
 * manipulates a Coreboot Sourcetree so that it is compatible with KConfigreader
 * and codemodelextractors.
 * 
 * @author Kevin
 */
public class PrepareCoreboot extends AbstractBusybootPreparation {

    @Override
    protected void runImpl() throws SetUpException {
        String pathToSource = getSourceTree().getPath();
        String logPrefix = "Coreboot Preparation: ";
        
        LOGGER.logDebug(logPrefix + "Copy Source Tree");
        try {
            copyOriginal();
        } catch (IOException e) {
            throw new SetUpException("Couldn't copy source tree", e);
        }
        
        Logger.get().logDebug(logPrefix + "Exeute make allyesconfig");
        executeMakeAllyesconfig(pathToSource);
        
        LOGGER.logDebug(logPrefix + "Making Makefile with dummy targets");
        try {
            makeDummyMakefile();
        } catch (IOException e) {
            throw new SetUpException("Couldn't write Makefile", e);
        }
        
        Logger.get().logDebug(logPrefix + "Rename Makefile.inc to Kbuild and rename lists");
        List<File> matchingFiles = findFilesByName(new File(pathToSource), "Makefile.inc");
        for (File file : matchingFiles) {

            Path path = Paths.get(file.getPath());
            Charset charset = StandardCharsets.UTF_8;
            try {
                String content = new String(Files.readAllBytes(path), charset);
                content = replaceStuff(content);
                Files.write(path, content.getBytes(charset));
            } catch (IOException exc) {
                Logger.get().logWarning(exc.getMessage());
                exc.printStackTrace();
            }

            file.renameTo(new File(file.getAbsolutePath().replace("Makefile.inc", "Kbuild")));
        }
        Logger.get().logDebug(logPrefix + "Copying Kconfig information");
        collectKconfigInfos(pathToSource);
        Logger.get().logDebug(logPrefix + "initialize extern int");
        initializeExternInt(pathToSource);
    }

    /**
     * Initializes the var extern int kconfig_warnings.
     *
     * @param pathToSource
     *            the path to source
     */
    private void initializeExternInt(String pathToSource) {
        try {
            Charset charset = StandardCharsets.UTF_8;
            Path path = Paths.get(pathToSource + File.separatorChar + "scripts" + File.separatorChar + "kconfig"
                    + File.separatorChar + "lkc.h");
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replaceAll("extern int kconfig_warnings", "extern int kconfig_warnings = 0");
            Files.write(path, content.getBytes(charset));

        } catch (IOException exc) {
            Logger.get().logWarning(exc.getMessage());
            exc.printStackTrace();
        }
    }

    /**
     * Collects the kconfig infos from build/util/kconfig and util/kconfig and
     * pastes it at scripts/kconfig.
     *
     * @param pathToSource
     *            the path to source
     */
    private void collectKconfigInfos(String pathToSource) {
        try {
            Runtime rt = Runtime.getRuntime();
            rt.exec("mkdir " + pathToSource + File.separatorChar + "scripts");

        } catch (IOException exc) {
            Logger.get().logWarning(exc.getMessage());
            exc.printStackTrace();
        }

        Path source0 = Paths.get(pathToSource.substring(0, pathToSource.length() - 4) + File.separatorChar + "util"
                + File.separatorChar + "kconfig" + File.separatorChar);
        Path source1 = Paths.get(pathToSource.substring(0, pathToSource.length() - 4) + File.separatorChar + "build"
                + File.separatorChar + "util" + File.separatorChar + "kconfig");
        Path destination = Paths.get(
                pathToSource + File.separatorChar + "scripts" + File.separatorChar + "kconfig" + File.separatorChar);
        try {
            System.out.println(source0 + " = Source0");
            System.out.println(source1 + " = Source0");
            System.out.println(destination + " = dest");
            
            destination.toFile().mkdir();
            Util.copyFolder(source0.toFile(), destination.toFile());
            Util.copyFolder(source1.toFile(), destination.toFile());
        } catch (IOException exc) {
            Logger.get().logWarning(exc.getMessage());
            exc.printStackTrace();
        }
    }

    /**
     * Replaces the Name of the Makefile.inc and the list of files to be compiled.
     *
     * @param content
     *            the original string
     * @return the changed string
     */
    private String replaceStuff(String content) {
        content = content.replaceAll("Makefile.inc", "Kbuild");
        content = content.replaceAll("ramstage-", "obj-");
        content = content.replaceAll("romstage-", "obj-");
        content = content.replaceAll("bootblock-", "obj-");
        content = content.replaceAll("smm-", "obj-");
        content = content.replaceAll("smmstub-", "obj-");
        content = content.replaceAll("cpu_microcode-", "obj-");
        content = content.replaceAll("verstage-", "obj-");
        content = content.replaceAll("subdirs-y", "obj-y");
        return content;
    }

    /**
     * Execute make allyesconfig.
     *
     * @param pathToSource
     *            the path to source tree of coreboot
     */
    private void executeMakeAllyesconfig(String pathToSource) {
        ProcessBuilder processBuilder = new ProcessBuilder("make", "allyesconfig");
        processBuilder.directory(new File(pathToSource.replace("/src", "")));
        boolean ret = false;
        try {
            ret = Util.executeProcess(processBuilder, "make");
        } catch (IOException exc) {
            Logger.get().logWarning(exc.getMessage());
            exc.printStackTrace();
        } finally {
            System.out.println("success: " + ret);
        }
    }

}
