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
 * The Class PrepareBusybox implements the Interface IPreparetion and
 * manipulates a Busybox Sourcetree so that it is compatible with KConfigreader
 * and codemodelextractors.
 * 
 * @author Kevin
 */
public class PrepareBusybox implements IPreparation {
    
    private static final @NonNull Logger LOGGER = Logger.get();
    

	/**
	 * The run method as it is requested by IPreparation. Just getting the Path from
	 * the Config file and calling run(String pathSource).
	 * 
	 * @see net.ssehub.kernel_haven.IPreparation#run(net.ssehub.kernel_haven.config.Configuration)
	 */
	@Override
	public void run(@NonNull Configuration config) throws SetUpException {
		File pathToSource = config.getValue(DefaultSettings.SOURCE_TREE);
		LOGGER.logInfo("Starting PrepareBusybox for " + pathToSource);
		run(pathToSource);

	}

	/**
	 * The real run method, managing the manipulation of Busybox Sourcetree.
	 *
	 * @param pathSource
	 *            the path to the sourcetree
	 */
	private void run(@NonNull File pathToSource) throws SetUpException {
		String logPrefix = "Busybox Preparation: ";
		
		LOGGER.logDebug(logPrefix + "Copy Source Tree");
		try {
            copyOriginal(pathToSource);
        } catch (IOException e) {
            throw new SetUpException("Couldn't copy source tree", e);
        }
		
		LOGGER.logDebug(logPrefix + "Execute make allyesconfig prepare");
		try {
            executeMakePrepareAllyesconfigPrepare(pathToSource);
        } catch (IOException e) {
            throw new SetUpException("Couldn't execute 'make prepare allyesconfig'", e);
        }
		
		LOGGER.logDebug(logPrefix + "Renaming Conig.in to Kconfig");
		try {
		    for (File file : findFilesByName(pathToSource, "Config.in")) {
		        replaceInFile(file, new File(file.getParentFile(), "Kconfig"), "Config.in", "Kconfig");
		    }
		} catch (IOException exc) {
            throw new SetUpException("Couldn't replace in Config.in files", exc);
        }
		
		LOGGER.logDebug(logPrefix + "Renaming obj- list");
        try {
    		for (File file : findFilesByName(pathToSource, "Kbuild")) {
    		    replaceInFile(file, file, "lib-", "obj-");
    		}
        } catch (IOException exc) {
            throw new SetUpException("Couldn't replace in Kbuild files", exc);
        }
		
		LOGGER.logDebug(logPrefix + "Making Makefile with dummy targets");
		try {
            makeDummyMakefile(pathToSource);
        } catch (IOException e) {
            throw new SetUpException("Couldn't write Makefile", e);
        }
		
		LOGGER.logDebug(logPrefix + "Normalizing sourcecode");
		try {
            normalizeDir(pathToSource);
        } catch (IOException e) {
            throw new SetUpException("Couldn't normalize file contents", e);
        }
		
		LOGGER.logDebug(logPrefix + "Done");
	}
	
	/**
     * Copy original source tree.
     *
     * @param pathToSource
     *            the path to source tree
     */
    private void copyOriginal(@NonNull File pathToSource) throws IOException {
        File cpDir = new File(pathToSource.getParentFile(), pathToSource.getName() + "UnchangedCopy");
        if (cpDir.exists()) {
            throw new IOException("Copy directory already exists");
        }
        cpDir.mkdir();
        Util.copyFolder(pathToSource, cpDir);
    }
    
    /**
     * Execute make prepare allyesconfig.
     *
     * @param pathToSource
     *            the path to the source tree
     */
    private void executeMakePrepareAllyesconfigPrepare(@NonNull File pathToSource) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("make", "allyesconfig", "prepare");
        processBuilder.directory(pathToSource);
        
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
     * Make dummy makefile creates the dummy makefile with fake targets.
     *
     * @param pathToSource
     *            the path to source
     */
    private void makeDummyMakefile(@NonNull File pathToSource) throws IOException {
        try (PrintWriter writer = new PrintWriter(new File(pathToSource, "Makefile"))) {
            writer.print("allyesconfig:\n\nprepare:\n");
        }
    }
    
    /**
     * Starting point for modifying the c preprocessor sourcefiles based on Manuel
     * Zerpies Busyfix.
     *
     * @param dir
     *            the dir to normalize
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
     * @param file
     *            the file to normalize
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
     * @param inputFile
     *            the input file as a list of lines
     * @return the list of lines with substituted line continuation
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
     * @param line
     *            the line to normalize
     * 
     * @return the normalized line
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
     * Do not normalize def undef checks that def undefs are not getting normalized
     * Busyfix style
     *
     * @param line
     *            the line
     * @return true, if successful
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
     * @param line
     *            the line to normalize
     * @return the normalized line
     */
    private static @NonNull String normalizeDefinedEnableMacro(@NonNull String line) {
        return notNull(line.replace("defined ENABLE_", "defined CONFIG_"));
    }

    /**
     * Normalizes enable macro in Busyfix style.
     *
     * @param temp
     *            the string to normalize
     * @return the normalized string
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
     * @param temp
     *            the string to normalize
     * @return the normalized string
     */
    private static @NonNull String normalizeEnableInline(@NonNull String temp) {

        if (temp.contains("_ENABLE_") || temp.contains("#if"))
            return temp;
        if (temp.contains("ENABLE_")) {
            temp = notNull(temp.replace("ENABLE_", "\n#if defined CONFIG_"));
            StringBuilder strB = new StringBuilder(temp);
            if (temp.contains("if (\n#if defined CONFIG_")) {
                try {
                    strB.insert(temp.indexOf(")", temp.indexOf("defined CONFIG_") + 10), "\n1\n#else\n0\n#endif\n");
                } catch (StringIndexOutOfBoundsException exc) {
                    try {
                        strB.insert(temp.indexOf(")", temp.indexOf("defined CONFIG_") + 10), "\n1\n#else\n0\n#endif\n");
                    } catch (Exception exc2) {
                        strB.append("\n1\n#else\n0\n#endif\n");
                    }

                }
                temp = notNull(strB.toString());
                return temp;
            }

            // findOutWhat CONFIG_X is followed by and at which index of string
            int indexOfWhitespace = temp.indexOf(" ", temp.indexOf("defined CONFIG_") + 10);
            int indexOfComma = temp.indexOf(",", temp.indexOf("defined CONFIG_") + 10);
            int indexOfParenthesis = temp.indexOf(")", temp.indexOf("defined CONFIG_") + 10);
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
            temp = notNull(strB.toString());
        }
        return temp;
    }
    
    /**
     * Normalizes if structures in Busyfix style.
     *
     * @param temp
     *            the temp
     * 
     * @return the string
     */
    private static @NonNull String normalizeIf(@NonNull String temp) {
        if (!temp.contains("IF_"))
            return temp;
        String variable = "";
        String init = "";
        String toRet = "";
        
        if (temp.contains("(") && temp.contains(")")) {
            int indexOpening = temp.indexOf("(", temp.indexOf("IF_"));
            int indexClosing = temp.length() - 1;

            int openingCount = 0;

            char[] chars = temp.toCharArray();

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

            variable = temp.substring(indexOpening, indexClosing);
            init = "\n" + temp.substring(indexClosing + 1);

            temp = notNull(temp.substring(0, indexOpening));
        }
        if (temp.contains("IF_NOT_")) {
            temp = notNull(temp.replace("IF_NOT_", "\n#if !defined CONFIG_"));
        } else if (temp.contains("IF_")) {
            temp = notNull(temp.replace("IF_", "\n#if defined CONFIG_"));
        }

        toRet = temp + "\n";
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
