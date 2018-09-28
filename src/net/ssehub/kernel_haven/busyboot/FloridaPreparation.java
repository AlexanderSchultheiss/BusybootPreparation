package net.ssehub.kernel_haven.busyboot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Writer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.ssehub.kernel_haven.IPreparation;
import net.ssehub.kernel_haven.PipelineConfigurator;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.config.Setting.Type;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;

/**
 * An {@link IPreparation} that replaces all <a href="https://dl.acm.org/citation.cfm?id=3023967">FLOrIDA</a>
 * conditions with equivalent C preprocessor ifdefs.
 * 
 * @author Adam
 */
public class FloridaPreparation implements IPreparation {
    
    public static final @NonNull Setting<@NonNull File> DESTINATION_DIR
        = new Setting<>("prepare_pure_variants.destination", Type.DIRECTORY, true, null, "The destination directory "
            + "where a temporary copy of the source tree with the FLOrIDA replacements should be placed. "
            + "All contents of this will be overwritten.");
    
    private static final @NonNull Logger LOGGER = Logger.get();
    
    private static final @NonNull Pattern BEGIN_PATTERN
            = Pattern.compile(Pattern.quote("begin[") + "(\\w+)" + Pattern.quote("]"));
    
    private static final @NonNull Pattern END_PATTERN
        = Pattern.compile(Pattern.quote("end[") + "(\\w+)" + Pattern.quote("]"));
    
    private static final @NonNull Pattern LINE_PATTERN
        = Pattern.compile(Pattern.quote("Line[") + "(\\w+)" + Pattern.quote("]"));
    
    private File currentFile;
    
    private int currentLineNumber;
    
    /**
     * The stack of features in //&begin[] and //&end[] blocks (//&Line[] is NOT considered).
     */
    private Deque<String> featureStack;
    
    /**
     * If a //&Line[] directive is found, this is set to the feature that needs to be closed.
     */
    private @Nullable String closeLine;
    
    @Override
    public void run(@NonNull Configuration config) throws SetUpException {
        config.registerSetting(DESTINATION_DIR);
        
        File copiedSourceTree = config.getValue(DESTINATION_DIR);
        File originalSourceTree = config.getValue(DefaultSettings.SOURCE_TREE);
        
        try {
            if (Util.isNestedInDirectory(originalSourceTree, copiedSourceTree)) {
                throw new SetUpException(DESTINATION_DIR.getKey() + " points to a location inside "
                        + "of " + DefaultSettings.SOURCE_TREE);
            }
        } catch (IOException e) {
            throw new SetUpException(e);
        }
        
        try {
            prepare(originalSourceTree, copiedSourceTree);
        } catch (IOException e) {
            throw new SetUpException(e);
        }
        
        config.setValue(DefaultSettings.SOURCE_TREE, copiedSourceTree);
        PipelineConfigurator.instance().getCmProvider().setConfig(config);
    }
    
    /**
     * <p>
     * Does the actual work of this preparation.
     * </p>
     * 
     * <p>
     * Package visibility for test cases.
     * </p>
     *  
     * @param originalSourceTree The original source tree to copy from.
     * @param copiedSourceTree The target source tree. If this doesn't exist, its created; if it exists, it's cleared.
     * 
     * @throws IOException If reading or writing files fails.
     * @throws SetUpException If clearing the copy location fails.
     */
    void prepare(File originalSourceTree, File copiedSourceTree)
            throws IOException, SetUpException {
        
        LOGGER.logDebug("Starting preperation...");
        
        // make sure that the destination is empty
        try {
            Util.clearFolder(copiedSourceTree);
        } catch (IOException e) {
            LOGGER.logException("Cannot clear/create " + copiedSourceTree.getName() + " in "
                    + copiedSourceTree.getParentFile().getAbsolutePath(), e);
            throw e;
        }
        
        this.featureStack = new LinkedList<>();
        
        // copy the source_tree to destination, while replacing all FLOrIDA conditions
        LOGGER.logDebug("Copying from " + originalSourceTree.getAbsolutePath() + " to "
                + copiedSourceTree.getAbsolutePath());
        copy(originalSourceTree, copiedSourceTree);
    }
    
    /**
     * Copies the given file. If the file is a .c or .h file, then replacements are done. If from is a directory
     * then this recursively copies the files inside it.
     * 
     * @param from The file to copy.
     * @param to The destination.
     * 
     * @throws IOException If copying the file fails.
     */
    private void copy(File from, File to) throws IOException {
        for (File f : from.listFiles()) {
            
            File newF = new File(to, f.getName());
            
            if (f.isDirectory()) {
                newF.mkdir();
                copy(f, newF);
            } else {
                if (f.getName().endsWith(".c") || f.getName().endsWith(".cpp") || f.getName().endsWith(".h")) {
                    copySourceFile(f, newF);
                } else {
                    Util.copyFile(f, newF);
                }
            }
        }
    }
    
    /**
     * Data structure to store the C preprocessor block structure of the file we are replacing in.
     */
    private static class CppBlock {

        private int lineStart;
        
        private int numOpeningFlorida;
        
        private int numClosingFlorida;

        /**
         * Creates a new {@link CppBlock}.
         * 
         * @param lineStart The line where the #if starts.
         */
        public CppBlock(int lineStart) {
            this.lineStart = lineStart;
        }
        
    }
    
    /**
     * Pops the current block from the stack. Prints a warning if number of opening and closing FLOrIDA blocks
     * don't match.
     * 
     * @param blockStructure The block structure to pop from.
     */
    private void popBlock(Deque<CppBlock> blockStructure) {
        if (!blockStructure.isEmpty()) {
            
            CppBlock block = blockStructure.pop();
            
            if (block.numOpeningFlorida != block.numClosingFlorida) {
                LOGGER.logWarning("CppBlock in " + currentFile + " in line " + block.lineStart + " has "
                        + block.numOpeningFlorida + " opening and " + block.numClosingFlorida + " closing "
                                + "FLOrIDA statements");
            }
            
        }
    }
    
    /**
     * Called each time a FLOrIDA replacement is created. Checks if the nesting with the block structure is correct.
     * 
     * @param blockStructure The C preprocessor block structure.
     * @param floridaReplacement The FLOrIDA replacement line (i.e. C preprocessor).
     */
    private void onFloridaBlock(Deque<CppBlock> blockStructure, String floridaReplacement) {
        CppBlock block = blockStructure.peek();
        if (block != null) {
            if (floridaReplacement.startsWith("#if")) {
                block.numOpeningFlorida++;
            } else if (floridaReplacement.startsWith("#endif")) {
                block.numClosingFlorida++;
                
                if (block.numClosingFlorida > block.numOpeningFlorida) {
                    LOGGER.logWarning("CppBlock in " + currentFile + " in line " + block.lineStart
                            + " has a closing FLOrIDA statement without a prior opening one");
                }
            }
        }
    }
    
    /**
     * Copies a source file (.c or .h) while doing replacements.
     * 
     * @param from The file to copy.
     * @param to The destination.
     * 
     * @throws IOException If copying the file fails.
     */
    private void copySourceFile(File from, File to) throws IOException {
        currentFile = from;
        
        try (LineNumberReader in = new LineNumberReader(new FileReader(from))) {
            
            try (Writer out = new BufferedWriter(new FileWriter(to))) {
                
                Deque<CppBlock> blockStructure = new LinkedList<>();
                
                String line;
                while ((line = in.readLine()) != null) {
                    currentLineNumber = in.getLineNumber();
                    
                    String closeLineAfterThis = closeLine;
                    closeLine = null;
                    
                    String trimmed = line.trim();
                    
                    if (trimmed.startsWith("//&")) {
                        line = getReplacement(trimmed.substring("//&".length()));
                        onFloridaBlock(blockStructure, line);
                        
                    } else if (trimmed.startsWith("#")) {
                        
                        trimmed = trimmed.replace(" ", "");
                        if (trimmed.startsWith("#if")) {
                            // #if, #ifdef, #ifndef -> start new block
                            blockStructure.push(new CppBlock(currentLineNumber));
                            
                        } else if (trimmed.startsWith("#el")) {
                            // #elif, #else -> pop current and start new block
                            popBlock(blockStructure);
                            blockStructure.push(new CppBlock(currentLineNumber));
                            
                        } else if (trimmed.startsWith("#endif")) {
                            // #endif -> pop current block
                            popBlock(blockStructure);
                        }
                        
                    }
                    
                    out.write(line);
                    out.write("\n");
                    
                    if (closeLineAfterThis != null) {
                        String endLine = "#endif // " + closeLineAfterThis; 
                        onFloridaBlock(blockStructure, endLine);
                        out.write(endLine);
                        out.write("\n");
                    }
                }
                
            }
        } finally {
            currentFile = null;
            currentLineNumber = -1;
        }
    }
    
    /**
     * Creates a replacement CPP expression for the given FLOrIDA condition.
     * 
     * @param condition The condition (the part after the "//&").
     * 
     * @return A replacement line.
     */
    private String getReplacement(String condition) {
        String result = "// Error replacing FLOrIDA condition: //&" + condition;
        
        Matcher ifMatcher = BEGIN_PATTERN.matcher(condition);
        Matcher endMatcher = END_PATTERN.matcher(condition);
        Matcher lineMatcher = LINE_PATTERN.matcher(condition);
        
        if (endMatcher.matches()) {
            String feature = endMatcher.group(1);
            String expectedFeature = featureStack.pop();
            if (!feature.equals(expectedFeature)) {
                LOGGER.logWarning("begin[] and end[] block features don't match in " + currentFile
                        + " in line " + currentLineNumber,
                        "Got //&end[" + feature + "], expected //&end[" + expectedFeature + "]");
            }
            result = "#endif // " + feature;
            
        } else if (ifMatcher.matches()) {
            String feature = ifMatcher.group(1);
            featureStack.push(feature);
            result = "#if defined(" + feature + ")";
            
        } else if (lineMatcher.matches()) {
            String feature = lineMatcher.group(1);
            closeLine = feature;
            result = "#if defined(" + feature + ")";
            
        } else {
            LOGGER.logError("Unknown FLOrIDA condition in " + currentFile + " in line " + currentLineNumber + ":",
                    condition);
        }
        
        return result;
    }

}
