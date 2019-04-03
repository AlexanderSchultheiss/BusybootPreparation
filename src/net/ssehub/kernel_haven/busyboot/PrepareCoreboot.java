/*
 * Copyright 2018-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.busyboot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
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
        String logPrefix = "Coreboot Preparation: ";
        
        LOGGER.logDebug(logPrefix + "Copy Source Tree");
        try {
            copyOriginal();
        } catch (IOException e) {
            throw new SetUpException("Couldn't copy source tree", e);
        }
        
        LOGGER.logDebug(logPrefix + "Execute make allyesconfig");
        try {
            executeMakeAllyesconfig();
        } catch (IOException e) {
            throw new SetUpException("Couldn't execute 'make allyesconfig'", e);
        }
        
        LOGGER.logDebug(logPrefix + "Making Makefile with dummy targets");
        try {
            makeDummyMakefile();
        } catch (IOException e) {
            throw new SetUpException("Couldn't write Makefile", e);
        }
        
        LOGGER.logDebug(logPrefix + "Rename Makefile.inc to Kbuild and rename lists");
        List<File> matchingFiles = findFilesByName(getSourceTree(), "Makefile.inc");
        try {
            for (File source : matchingFiles) {
                File target = new File(source.getParentFile(), "Kbuild");
                
                String content;
                try (FileInputStream in = new FileInputStream(source)) {
                    content = Util.readStream(in);
                }
                source.delete();

                content = replaceStuff(content);
                
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new SetUpException("Couldn't replace in Makefiles", e);
        }
        
        LOGGER.logDebug(logPrefix + "Copying Kconfig information");
        try {
            collectKconfigInfos();
        } catch (IOException e) {
            throw new SetUpException("Couldn't copy Kconfig information", e);
        }
        
        LOGGER.logDebug(logPrefix + "initialize extern int");
        try {
            initializeExternInt();
        } catch (IOException e) {
            throw new SetUpException("Couldn't replace in variable initialization", e);
        }
    }
    
    /**
     * Executes 'make allyesconfig' to prepare the coreboot tree for analysis.
     * 
     * @throws IOException If execution of make fails.
     */
    private void executeMakeAllyesconfig() throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("make", "allyesconfig");
        processBuilder.directory(getSourceTree().getParentFile());
        
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        
        boolean success = Util.executeProcess(processBuilder, "make", stdout, stderr, 0);
        if (!success) {
            LOGGER.logError("Couldn't execute 'make allyesconfig'", "stdout:", stdout.toString(),
                    "stderr:", stderr.toString());
            throw new IOException("make returned failure");
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
     * Collects the kconfig infos from build/util/kconfig and util/kconfig and
     * pastes it at scripts/kconfig.
     *
     * @param pathToSource
     *            the path to source
     *            
     * @throws IOException If copying the folders fails.
     */
    private void collectKconfigInfos() throws IOException {
        File source0 = new File(getSourceTree().getParentFile(), "util/kconfig");
        File source1 = new File(getSourceTree().getParentFile(), "build/util/kconfig");

        File destination = new File(getSourceTree(), "scripts/kconfig");
        destination.mkdirs();
        
        Util.copyFolder(source0, destination);
        Util.copyFolder(source1, destination);
    }
    
    /**
     * <p>
     * Initializes the var extern int kconfig_warnings.
     * </p>
     * <p>
     * Package visibility for test cases.
     * </p>
     * <p>
     * TODO AK: Check if this is actually correct: Is this even needed?
     * </p>
     *
     * @param pathToSource
     *            the path to source
     * 
     * @throws IOException If writing the replacements fails.
     */
    void initializeExternInt() throws IOException {
        File lkcH = new File(getSourceTree(), "scripts/kconfig/lkc.h");
        
        replaceInFile(lkcH, lkcH, "extern int kconfig_warnings", "extern int kconfig_warnings = 0");
    }

}
