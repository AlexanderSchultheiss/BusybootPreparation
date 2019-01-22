package net.ssehub.kernel_haven.busyboot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

	/**
	 * The run method as it is requested by IPreparation. Just getting the Path from
	 * the Config file and calling run(String pathSource).
	 * 
	 * @see net.ssehub.kernel_haven.IPreparation#run(net.ssehub.kernel_haven.config.Configuration)
	 */
	@Override
	public void run(@NonNull Configuration config) throws SetUpException {
		File pathToSource = config.getValue(DefaultSettings.SOURCE_TREE);
		Logger.get().logInfo("Starting PrepareBusybox for " + pathToSource);
		run(pathToSource);

	}

	/**
	 * The real run method, managing the manipulation of Busybox Sourcetree.
	 *
	 * @param pathSource
	 *            the path to the sourcetree
	 */
	private void run(File pathToSource) {
		String logPrefix = "Busybox Preparation: ";
		Logger.get().logDebug(logPrefix + "Copy Source Tree");
		copyOriginal(pathToSource);
		Logger.get().logDebug(logPrefix + "Execute make allyescongif prepare");
		executeMakePrepareAllyesconfigPrepare(pathToSource);
		Logger.get().logDebug(logPrefix + "Renaming Conig.in to Kconfig");
		ArrayList<File> matchingFiles = findFilesByName(pathToSource, "Config.in");
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
			file.renameTo(new File(file.getAbsolutePath().replace("Config.in", "Kconfig")));
		}
		Logger.get().logDebug(logPrefix + "Renaming obj- list");
		matchingFiles = findFilesByName(pathToSource, "Kbuild");
		for (File file : matchingFiles) {
			Path path = Paths.get(file.getPath());
			Charset charset = StandardCharsets.UTF_8;
			try {
				String content = new String(Files.readAllBytes(path), charset);
				content = content.replace("lib-", "obj-");
				Files.write(path, content.getBytes(charset));
			} catch (IOException exc) {
				Logger.get().logWarning(exc.getMessage());
				exc.printStackTrace();
			}
		}
		Logger.get().logDebug(logPrefix + "Making Makefile with dummy targets");
		makeDummyMakefile(pathToSource);
		Logger.get().logDebug(logPrefix + "Normalizing sourcecode");
		normalizeDir(pathToSource);
		Logger.get().logDebug(logPrefix + "Done.");
	}

	/**
	 * Make dummy makefile creates the dummy makefile with fake targets.
	 *
	 * @param pathToSource
	 *            the path to source
	 */
	private void makeDummyMakefile(File pathToSource) {
		PrintWriter writer;
		try {
			writer = new PrintWriter(new File(pathToSource, "Makefile"));
			writer.print("allyesconfig:\nprepare:");
			writer.close();
		} catch (FileNotFoundException exc) {
			Logger.get().logWarning(exc.getMessage());

			exc.printStackTrace();
		}
	}

	/**
	 * Execute make prepare allyesconfig.
	 *
	 * @param pathToSource
	 *            the path to the source tree
	 */
	private void executeMakePrepareAllyesconfigPrepare(File pathToSource) {
		ProcessBuilder processBuilder = new ProcessBuilder("make", "allyesconfig", "prepare");
		processBuilder.directory(pathToSource);
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

	/**
	 * Copy original source tree.
	 *
	 * @param pathToSource
	 *            the path to source tree
	 */
	private void copyOriginal(File pathToSource) {
		File cpDir = new File(pathToSource.getParentFile(), pathToSource.getName() + "UnchangedCopy");
		try {
		    cpDir.mkdir();
		    Util.copyFolder(pathToSource, cpDir);
		} catch (IOException exc) {
			Logger.get().logWarning(exc.getMessage());
			exc.printStackTrace();
		}
	}

	/**
	 * Renames Config.in into Kconfig.
	 *
	 * @param content
	 *            the content
	 * @return the string
	 */
	private String replaceStuff(String content) {
		content = content.replaceAll("Config.in", "Kconfig");
		return content;
	}

	/**
	 * Starting point for modifying the c preprocessor sourcefiles based on Manuel
	 * Zerpies Busyfix.
	 *
	 * @param dir
	 *            the dir to normalize
	 */
	private static void normalizeDir(File dir) {

		File[] files = dir.listFiles();
		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					normalizeDir(files[i]);
				} else if (files[i].getName().endsWith(".h") || files[i].getName().endsWith(".c")) {
					normalizeFile(files[i]);
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
	private static void normalizeFile(File file) {
		File tempFile;
		FileOutputStream fos = null;
		if (file.getName().contains("unicode") || file.getName().contains(".fnt"))
			return;

		List<String> inputFile = new ArrayList<String>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file.getPath()), "utf-8"))) {
			for (String line; (line = br.readLine()) != null;) {
				inputFile.add(line);
			}
			file.delete();
			tempFile = file;
			fos = new FileOutputStream(tempFile);
		} catch (IOException exc) {
			Logger.get().logWarning(exc.getMessage());
			exc.printStackTrace();
		}
		BufferedWriter bwr = new BufferedWriter(new OutputStreamWriter(fos));
		inputFile = substituteLineContinuation(inputFile);

		for (String line : inputFile) {
			addToTemp(bwr, normalizeLine(line));
		}

		try {
			bwr.close();
		} catch (IOException eexc) {
			Logger.get().logWarning(eexc.getMessage());
			eexc.printStackTrace();
		}
	}

	/**
	 * Writes the input string in the bufferedwriter and adds a new line.
	 *
	 * @param bw
	 *            the buffered writer
	 * @param normalizedLine
	 *            the line to write
	 */
	private static void addToTemp(BufferedWriter bw, String normalizedLine) {

		try {

			bw.write(normalizedLine);
			bw.newLine();
		} catch (IOException exc) {
			Logger.get().logWarning(exc.getMessage());
			exc.printStackTrace();
		}
	}

	/**
	 * Normalizes a single line in style of Busyfix.
	 *
	 * @param line
	 *            the line to normalize
	 * 
	 * @return the normalized line
	 */
	private static String normalizeLine(String line) {
		int index;
		String temp;
		if (line.length() == 0)
			return line;

		if (doNotNormalizeDefUndef(line))
			return line;

		// don't normalize comments
		if (line.contains("//")) {
			index = line.indexOf("//");
			return normalizeLine(line.substring(0, index)) + line.substring(index);
		}
		if (line.contains("/*") || line.contains("*/") || line.replace("\\t", " ").trim().startsWith("*")) {
			// lines that start with or are block comments
			if (line.replace("\\t", " ").trim().startsWith("/*") || line.replace("\\t", " ").trim().startsWith("*")) {
				// fully comment
				if (!line.contains("*/")) {
					return line;

				} else {
					return line.substring(0, line.indexOf("*/") + 2)
							+ normalizeLine(line.substring(line.indexOf("*/") + 2));
				}

			} else if (line.contains("/*")) {
				return normalizeLine(line.substring(0, line.indexOf("/*")))
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
	 * Normalizes if structures in Busyfix style.
	 *
	 * @param temp
	 *            the temp
	 * 
	 * @return the string
	 */
	private static String normalizeIf(String temp) {
		if (!temp.contains("IF_"))
			return temp;
		String variable = "";
		String init = "";
		String toRet = "";
		try {
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

				temp = temp.substring(0, indexOpening);
			}
			if (temp.contains("IF_NOT_")) {
				temp = temp.replace("IF_NOT_", "\n#if !defined CONFIG_");
			} else if (temp.contains("IF_")) {
				temp = temp.replace("IF_", "\n#if defined CONFIG_");
			}

		}

		catch (Exception exc) {
			exc.printStackTrace();
			Logger.get().logWarning(exc.getMessage());
		}
		toRet = temp + "\n";
		if (variable.length() != 0)
			toRet += variable.substring(1);
		toRet += "\n#endif" + init;
		return toRet;
	}

	/**
	 * Normalizes enable inline in Busyfix style.
	 *
	 * @param temp
	 *            the string to normalize
	 * @return the normalized string
	 */
	private static String normalizeEnableInline(String temp) {

		if (temp.contains("_ENABLE_") || temp.contains("#if"))
			return temp;
		if (temp.contains("ENABLE_")) {
			temp = temp.replace("ENABLE_", "\n#if defined CONFIG_");
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
				temp = strB.toString();
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
			temp = strB.toString();
		}
		return temp;
	}

	/**
	 * Normalizes enable macro in Busyfix style.
	 *
	 * @param temp
	 *            the string to normalize
	 * @return the normalized string
	 */
	private static String normalizeEnableMacro(String temp) {
		if (temp.contains("if ENABLE_")) {
			temp = temp.replace("if ENABLE_", "if defined CONFIG_");
		}
		if (temp.contains("if !ENABLE_")) {
			temp = temp.replace("if !ENABLE_", "if !defined CONFIG_");
		}
		if (temp.contains("|| ENABLE_")) {
			temp = temp.replace("ENABLE_", "defined CONFIG_");
		}
		if (temp.contains("&& ENABLE_")) {
			temp = temp.replace("ENABLE_", "'defined CONFIG_");
		}
		if (temp.contains("|| !ENABLE_")) {
			temp = temp.replace("!ENABLE_", "!defined CONFIG_");
		}
		if (temp.contains("&& !ENABLE_")) {
			temp = temp.replace("!ENABLE_", "'!defined CONFIG_");
		}

		return temp;
	}

	/**
	 * Normalize defined enable macro in Busyfix style.
	 *
	 * @param line
	 *            the line to normalize
	 * @return the normalized line
	 */
	private static String normalizeDefinedEnableMacro(String line) {
		return line.replace("defined ENABLE_", "defined CONFIG_");
	}

	/**
	 * Do not normalize def undef checks that def undefs are not getting normalized
	 * Busyfix style
	 *
	 * @param line
	 *            the line
	 * @return true, if successful
	 */
	private static boolean doNotNormalizeDefUndef(String line) {
		boolean toRet = false;
		if (line.contains("#undef") || line.contains("#define") || line.contains("# define")
				|| line.contains("# undef"))
			toRet = true;
		return toRet;
	}

	/**
	 * Substitutes line continuation in Busybox for easier transformation.
	 *
	 * @param inputFile
	 *            the input file as a list of lines
	 * @return the list of lines with substituted line continuation
	 */
	private static List<String> substituteLineContinuation(List<String> inputFile) {
		int start = -1;
		int end = -1;
		List<String> toReturn = new ArrayList<>();

		for (int i = 0; i < inputFile.size(); i++) {
			if (inputFile.get(i).endsWith(" \\")) {
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
				toAdd = toAdd.replace("\\", "");
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
	 * Find files by name recursively full depth.
	 *
	 * @param directory
	 *            the directory
	 * @param filename
	 *            the filename
	 * @return the array list of found files
	 */
	private ArrayList<File> findFilesByName(File directory, String filename) {
		ArrayList<File> matchingFiles = new ArrayList<File>();
		findFilesHelper(directory, matchingFiles, filename);
		return matchingFiles;
	}

	/**
	 * Find files helpermethod for recursion.
	 *
	 * @param directory
	 *            the directory
	 * @param files
	 *            the files
	 * @param filename
	 *            the filename
	 */
	private void findFilesHelper(File directory, ArrayList<File> files, String filename) {
		// get all the files from a directory
		File[] fList = directory.listFiles();
		for (File file : fList) {
			if (file.isFile() && file.getName().endsWith(filename)) {
				files.add(file);
			} else if (file.isDirectory()) {
				findFilesHelper(file, files, filename);
			}
		}
	}

}
