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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.ssehub.kernel_haven.IPreparation;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;

/**
 * Superclass with helper methods for Busybox and Coreboot preparation.
 * 
 * @author Adam
 */
abstract class AbstractBusybootPreparation implements IPreparation {
    private static final @NonNull Setting<Boolean> PATH_TO_SOURCE_REPO
            = new Setting<>("analysis.busybox.normalize", Setting.Type.BOOLEAN, true, null, "" +
            "Whether the BusyBox sources should be normalized.");

    protected static final @NonNull Logger LOGGER = Logger.get();
    protected boolean normalizeSources = false;

    private @NonNull File sourceTree = new File(""); // will be initialized in run()

    @Override
    public void run(@NonNull Configuration config) throws SetUpException {
        this.sourceTree = config.getValue(DefaultSettings.SOURCE_TREE);
        config.registerSetting(PATH_TO_SOURCE_REPO);
        this.normalizeSources = config.getValue(PATH_TO_SOURCE_REPO);
        
        LOGGER.logInfo("Starting " + getClass().getSimpleName() + " for " + sourceTree);
        runImpl();
    }
    
    /**
     * The main preparation method.
     * 
     * @throws SetUpException If the preparation fails.
     */
    protected abstract void runImpl() throws SetUpException;
    
    /**
     * Changes the sourceTree attribute.
     * 
     * @param sourceTree The new source tree.
     */
    protected void setSourceTree(@NonNull File sourceTree) {
        this.sourceTree = sourceTree;
    }
    
    /**
     * Returns the source tree to do the preparation for.
     * 
     * @return The source tree location.
     */
    protected @NonNull File getSourceTree() {
        return sourceTree;
    }
    
    /**
     * Copies the source tree so that we keep an unmodified version.
     * 
     * @throws IOException If copying the directory fails.
     */
    protected void copyOriginal() throws IOException {
        File cpDir = new File(getSourceTree().getParentFile(), getSourceTree().getName() + "UnchangedCopy");
        if (cpDir.exists()) {
            throw new IOException("Copy directory already exists");
        }
        cpDir.mkdir();
        Util.copyFolder(getSourceTree(), cpDir);
    }
    
    /**
     * Creates a dummy Makefile with the targets 'allyesconfig' and 'prepare', so that extractors that call these
     * targets again will not fail.
     * 
     * @throws IOException If writing the file fails.
     */
    protected void makeDummyMakefile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new File(getSourceTree(), "Makefile"))) {
            writer.print("allyesconfig:\n\nprepare:\n");
        }
    }
    
    /**
     * Reads the contents of source, does string-based replacements, and writes the result as target.
     * 
     * @param source The source file to read the content from. This file will be delete after reading.
     * @param target The target file to write the replaced content to. This may be the same as source.
     * @param from The string to replace in the content.
     * @param to The string to replace occurrences of <code>from</code> with.
     * 
     * @throws IOException If reading or writing the file(s) fails.
     */
    protected static void replaceInFile(@NonNull File source, @NonNull File target,
            @NonNull String from, @NonNull String to) throws IOException {
        
        String content;
        try (FileInputStream in = new FileInputStream(source)) {
            content = Util.readStream(in);
        }
        source.delete();

        content = content.replace(from, to);
        
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Finds all files in the given directory (recursively) that have exactly the given filename.
     *
     * @param directory The directory to search in.
     * @param filename The filename to search for.
     * 
     * @return A list of all files that have the given filename.
     */
    protected static @NonNull List<@NonNull File> findFilesByName(@NonNull File directory, @NonNull String filename) {
        List<@NonNull File> matchingFiles = new ArrayList<>();
        if (directory.isDirectory()) {
            findFilesHelper(directory, filename, matchingFiles);
        }
        return matchingFiles;
    }

    /**
     * Helper method for {@link #findFilesByName(File, String)}. Recursively walks through the given directory and
     * all sub-directories and finds all files that have exactly the given filename.
     *
     * @param directory The directory to search in.
     * @param filename The filename to search for.
     * @param result The list to add the matching files to.
     */
    private static void findFilesHelper(@NonNull File directory, @NonNull String filename,
            @NonNull List<@NonNull File> result) {
        
        // get all the files from a directory
        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile() && file.getName().equals(filename)) {
                result.add(file);
            } else if (file.isDirectory()) {
                findFilesHelper(file, filename, result);
            }
        }
    }
    
}
