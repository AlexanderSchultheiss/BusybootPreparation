package net.ssehub.kernel_haven.busyboot;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.ssehub.kernel_haven.IPreparation;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * A preparation for Busybox source trees. This modifies the source tree in a way that the normal Linux extractors
 * work for them.
 * 
 * @author Kevin
 * @author Adam
 */
public class PrepareBusybox implements IPreparation {
    
    private static final @NonNull Logger LOGGER = Logger.get();
    
    private @NonNull File sourceTree = new File(""); // will be initialized in run()

	@Override
	public void run(@NonNull Configuration config) throws SetUpException {
		this.sourceTree = config.getValue(DefaultSettings.SOURCE_TREE);
		LOGGER.logInfo("Starting PrepareBusybox for " + sourceTree);
		runImpl();
	}

	/**
	 * The real run method, managing the manipulation of Busybox source tree.
	 */
	private void runImpl() throws SetUpException {
		String logPrefix = "Busybox Preparation: ";
		
		LOGGER.logDebug(logPrefix + "Copy Source Tree");
		try {
            copyOriginal();
        } catch (IOException e) {
            throw new SetUpException("Couldn't copy source tree", e);
        }
		
		LOGGER.logDebug(logPrefix + "Execute make allyesconfig prepare");
		try {
            executeMakePrepareAllyesconfigPrepare();
        } catch (IOException e) {
            throw new SetUpException("Couldn't execute 'make allyesconfig prepare'", e);
        }
		
		LOGGER.logDebug(logPrefix + "Renaming Conig.in to Kconfig");
		try {
		    for (File file : findFilesByName(sourceTree, "Config.in")) {
		        replaceInFile(file, new File(file.getParentFile(), "Kconfig"), "Config.in", "Kconfig");
		    }
		} catch (IOException exc) {
            throw new SetUpException("Couldn't replace in Config.in files", exc);
        }
		
		LOGGER.logDebug(logPrefix + "Renaming obj- list");
        try {
    		for (File file : findFilesByName(sourceTree, "Kbuild")) {
    		    replaceInFile(file, file, "lib-", "obj-");
    		}
        } catch (IOException exc) {
            throw new SetUpException("Couldn't replace in Kbuild files", exc);
        }
		
		LOGGER.logDebug(logPrefix + "Making Makefile with dummy targets");
		try {
            makeDummyMakefile();
        } catch (IOException e) {
            throw new SetUpException("Couldn't write Makefile", e);
        }
		
		LOGGER.logDebug(logPrefix + "Normalizing sourcecode");
		try {
            normalizeDir(sourceTree);
        } catch (IOException e) {
            throw new SetUpException("Couldn't normalize file contents", e);
        }
		
		LOGGER.logDebug(logPrefix + "Done");
	}
	
	/**
     * Copies the source tree so that we keep an unmodified version.
     */
    private void copyOriginal() throws IOException {
        File cpDir = new File(sourceTree.getParentFile(), sourceTree.getName() + "UnchangedCopy");
        if (cpDir.exists()) {
            throw new IOException("Copy directory already exists");
        }
        cpDir.mkdir();
        Util.copyFolder(sourceTree, cpDir);
    }
    
    /**
     * Executes 'make allyesconfig prepare' to prepare the busybox tree for analysis.
     */
    private void executeMakePrepareAllyesconfigPrepare() throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("make", "allyesconfig", "prepare");
        processBuilder.directory(sourceTree);
        
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        
        boolean success = Util.executeProcess(processBuilder, "make", stdout, stderr, 0);
        if (!success) {
            LOGGER.logError("Couldn't execute 'make allyesconfig prepare'", "stdout:", stdout.toString(),
                    "stderr:", stderr.toString());
            throw new IOException("make returned failure");
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
    private static void replaceInFile(@NonNull File source, @NonNull File target,
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
     * Creates a dummy Makefile with the targets 'allyesconfig' and 'prepare', so that extractors that call these
     * targets again will not fail.
     */
    private void makeDummyMakefile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new File(sourceTree, "Makefile"))) {
            writer.print("allyesconfig:\n\nprepare:\n");
        }
    }
    
    /**
     * Starting point for modifying the c preprocessor source files based on Manuel Zerpies Busyfix.
     *
     * @param dir The directory to normalize all source files in.
     */
    private static void normalizeDir(@NonNull File dir) throws IOException {

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    normalizeDir(file);
                } else if (file.getName().endsWith(".h") || file.getName().endsWith(".c")) {
                    normalizeFile(file);
                }
            }
        }
    }
    
    /**
     * Normalizes a single file in style of Busyfix.
     *
     * @param file The file to normalize.
     */
    private static void normalizeFile(@NonNull File file) throws IOException {
        File tempFile;
        FileOutputStream fos = null;
        if (file.getName().contains("unicode") || file.getName().contains(".fnt"))
            return;

        List<@NonNull String> inputFile = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file.getPath()), StandardCharsets.UTF_8))) {
            for (String line; (line = br.readLine()) != null;) {
                inputFile.add(line);
            }
            file.delete();
            tempFile = file;
            fos = new FileOutputStream(tempFile);
        }
        
        inputFile = substituteLineContinuation(inputFile);
        
        try (BufferedWriter bwr = new BufferedWriter(new OutputStreamWriter(fos))) {
            for (String line : inputFile) {
                bwr.write(normalizeLine(line));
                bwr.write('\n');
            }
        }
    }
    
    /**
     * Substitutes line continuation in Busybox for easier transformation.
     *
     * @param inputFile The input file as a list of lines.
     * 
     * @return The list of lines with substituted line continuation
     */
    private static @NonNull List<@NonNull String> substituteLineContinuation(@NonNull List<@NonNull String> inputFile) {
        int start = -1;
        int end = -1;
        List<@NonNull String> toReturn = new ArrayList<>();

        for (int i = 0; i < inputFile.size(); i++) {
            if (notNull(inputFile.get(i)).endsWith(" \\")) {
                if (start == -1) {
                    start = i;
                }
                end = i;
                continue;
            } else {
                end = i;
            }
            if (end == i && start != -1 && end >= start) {
                String toAdd = "";
                for (int j = start; j <= end; j++) {
                    toAdd += inputFile.get(j);
                }
                toAdd = notNull(toAdd.replace("\\", ""));
                toReturn.add(toAdd);
                start = -1;
                end = -1;
            } else {
                toReturn.add(inputFile.get(i));
                start = -1;
                end = -1;
            }
        }

        return toReturn;
    }

    /**
     * Normalizes a single line in style of Busyfix.
     *
     * @param line The line to normalize
     * 
     * @return The normalized line.
     */
    private static @NonNull String normalizeLine(@NonNull String line) {
        int index;
        String temp;
        if (line.length() == 0)
            return line;

        if (doNotNormalizeDefUndef(line))
            return line;

        // don't normalize comments
        if (line.contains("//")) {
            index = line.indexOf("//");
            return normalizeLine(notNull(line.substring(0, index))) + line.substring(index);
        }
        if (line.contains("/*") || line.contains("*/") || line.replace("\\t", " ").trim().startsWith("*")) {
            // lines that start with or are block comments
            if (line.replace("\\t", " ").trim().startsWith("/*") || line.replace("\\t", " ").trim().startsWith("*")) {
                // fully comment
                if (!line.contains("*/")) {
                    return line;

                } else {
                    return line.substring(0, line.indexOf("*/") + 2)
                            + normalizeLine(notNull(line.substring(line.indexOf("*/") + 2)));
                }

            } else if (line.contains("/*")) {
                return normalizeLine(notNull(line.substring(0, line.indexOf("/*"))))
                        + line.substring(line.indexOf("/*"));

            }
        }
        // malformed comments in scripts/basic/fixdep.c
        if (line.contains("if (!memcmp(p, \"IF_NOT\", 6)) goto conf7")
                || line.contains("/*if (!memcmp(p, \"IF_\", 3)) ...*/"))
            return line;
        temp = normalizeDefinedEnableMacro(line);
        temp = normalizeEnableMacro(temp);
        temp = normalizeEnableInline(temp);
        temp = normalizeIf(temp);
        return temp;
    }
    
    /**
     * Checks whether the given line is a #define or #undef line.
     *
     * @param line The line to check.
     * 
     * @return Whether the given line is a #define or #undef.
     */
    private static boolean doNotNormalizeDefUndef(@NonNull String line) {
        boolean toRet = false;
        if (line.contains("#undef") || line.contains("#define") || line.contains("# define")
                || line.contains("# undef"))
            toRet = true;
        return toRet;
    }

    /**
     * Normalize defined enable macro in Busyfix style.
     *
     * @param line The line to normalize.
     * 
     * @return The normalized line.
     */
    private static @NonNull String normalizeDefinedEnableMacro(@NonNull String line) {
        return notNull(line.replace("defined ENABLE_", "defined CONFIG_"));
    }

    /**
     * Normalizes enable macro in Busyfix style.
     *
     * @param temp The string to normalize.
     * 
     * @return The normalized string.
     */
    private static @NonNull String normalizeEnableMacro(@NonNull String temp) {
        if (temp.contains("if ENABLE_")) {
            temp = notNull(temp.replace("if ENABLE_", "if defined CONFIG_"));
        }
        if (temp.contains("if !ENABLE_")) {
            temp = notNull(temp.replace("if !ENABLE_", "if !defined CONFIG_"));
        }
        if (temp.contains("|| ENABLE_")) {
            temp = notNull(temp.replace("ENABLE_", "defined CONFIG_"));
        }
        if (temp.contains("&& ENABLE_")) {
            temp = notNull(temp.replace("ENABLE_", "'defined CONFIG_"));
        }
        if (temp.contains("|| !ENABLE_")) {
            temp = notNull(temp.replace("!ENABLE_", "!defined CONFIG_"));
        }
        if (temp.contains("&& !ENABLE_")) {
            temp = notNull(temp.replace("!ENABLE_", "'!defined CONFIG_"));
        }

        return temp;
    }

    /**
     * Normalizes enable inline in Busyfix style.
     *
     * @param line The line to normalize.
     * 
     * @return The normalized line.
     */
    private static @NonNull String normalizeEnableInline(@NonNull String line) {

        if (line.contains("_ENABLE_") || line.contains("#if"))
            return line;
        if (line.contains("ENABLE_")) {
            line = notNull(line.replace("ENABLE_", "\n#if defined CONFIG_"));
            StringBuilder strB = new StringBuilder(line);
            if (line.contains("if (\n#if defined CONFIG_")) {
                try {
                    strB.insert(line.indexOf(")", line.indexOf("defined CONFIG_") + 10), "\n1\n#else\n0\n#endif\n");
                } catch (StringIndexOutOfBoundsException exc) {
                    try {
                        strB.insert(line.indexOf(")", line.indexOf("defined CONFIG_") + 10), "\n1\n#else\n0\n#endif\n");
                    } catch (Exception exc2) {
                        strB.append("\n1\n#else\n0\n#endif\n");
                    }

                }
                line = notNull(strB.toString());
                return line;
            }

            // findOutWhat CONFIG_X is followed by and at which index of string
            int indexOfWhitespace = line.indexOf(" ", line.indexOf("defined CONFIG_") + 10);
            int indexOfComma = line.indexOf(",", line.indexOf("defined CONFIG_") + 10);
            int indexOfParenthesis = line.indexOf(")", line.indexOf("defined CONFIG_") + 10);
            int indexToInsert = indexOfWhitespace;
            if (indexOfComma != -1 && (indexOfComma < indexToInsert || indexToInsert == -1))
                indexToInsert = indexOfComma;
            if (indexOfParenthesis != -1 && (indexOfParenthesis < indexToInsert || indexToInsert == -1))
                indexToInsert = indexOfComma;
            if (indexToInsert != -1) {
                strB.insert(indexToInsert, "\n1\n#else\n0\n#endif\n");
            } else {
                strB.append("\n1\n#else\n0\n#endif\n");

            }
            line = notNull(strB.toString());
        }
        return line;
    }
    
    /**
     * Normalizes if structures in Busyfix style.
     *
     * @param line The line to do normalization in.
     * 
     * @return The normalized line.
     */
    private static @NonNull String normalizeIf(@NonNull String line) {
        if (!line.contains("IF_"))
            return line;
        String variable = "";
        String init = "";
        String toRet = "";
        
        if (line.contains("(") && line.contains(")")) {
            int indexOpening = line.indexOf("(", line.indexOf("IF_"));
            int indexClosing = line.length() - 1;

            int openingCount = 0;

            char[] chars = line.toCharArray();

            for (int i = indexOpening + 1; i < chars.length; i++) {
                if (chars[i] == '(') {
                    openingCount++;
                } else if (chars[i] == ')') {
                    if (openingCount == 0) {
                        indexClosing = i;
                        break;
                    }
                    openingCount--;
                }
            }

            variable = line.substring(indexOpening, indexClosing);
            init = "\n" + line.substring(indexClosing + 1);

            line = notNull(line.substring(0, indexOpening));
        }
        if (line.contains("IF_NOT_")) {
            line = notNull(line.replace("IF_NOT_", "\n#if !defined CONFIG_"));
        } else if (line.contains("IF_")) {
            line = notNull(line.replace("IF_", "\n#if defined CONFIG_"));
        }

        toRet = line + "\n";
        if (variable.length() != 0)
            toRet += variable.substring(1);
        toRet += "\n#endif" + init;
        return toRet;
    }

	/**
	 * Finds all files in the given directory (recursively) that have exactly the given filename.
	 *
	 * @param directory The directory to search in.
	 * @param filename The filename to search for.
	 * 
	 * @return A list of all files that have the given filename.
	 */
	private static @NonNull List<@NonNull File> findFilesByName(@NonNull File directory, @NonNull String filename) {
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
