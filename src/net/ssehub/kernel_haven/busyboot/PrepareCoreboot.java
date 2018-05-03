package net.ssehub.kernel_haven.busyboot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;

import net.ssehub.kernel_haven.IPreparation;
import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.util.Logger;
import net.ssehub.kernel_haven.util.Util;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * The Class PrepareCoreboot implements the Interface IPreparetion and
 * manipulates a Coreboot Sourcetree so that it is compatible with KConfigreader
 * and codemodelextractors.
 * 
 * @author Kevin
 */
public class PrepareCoreboot implements IPreparation {

	/**
	 * The run method as it is requested by IPreparation. Just getting the Path from
	 * the Config file and calling run(String pathSource).
	 * 
	 * @see net.ssehub.kernel_haven.IPreparation#run(net.ssehub.kernel_haven.config.Configuration)
	 */
	@Override
	public void run(@NonNull Configuration config) throws SetUpException {
		String pathToSource = config.getValue(DefaultSettings.SOURCE_TREE).getPath();
		Logger.get().logInfo("Starting PrepareCoreboot for " + pathToSource);
		run(pathToSource);
	}

	/**
	 * The real run method, managing the manipulation of Coreboot Sourcetree.
	 *
	 * @param pathSource
	 *            the path to the sourcetree
	 */
	private void run(String pathSource) {
		String pathToSource = pathSource;
		String logPrefix = "Coreboot Preparation: ";
		Logger.get().logDebug(logPrefix + "Copy Source Tree");
		copyOriginal(pathToSource);
		Logger.get().logDebug(logPrefix + "Exeute make allyesconfig");
		executeMakeAllyesconfig(pathToSource);
		Logger.get().logDebug(logPrefix + "Make Makefile with dummy targets");
		makeDummyMakefile(pathToSource);
		Logger.get().logDebug(logPrefix + "Rename Makefile.inc to Kbuild and rename lists");
		ArrayList<File> matchingFiles = findFilesByName(new File(pathToSource), "Makefile.inc");
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
	 * Copy original source tree.
	 *
	 * @param pathToSource
	 *            the path to source tree
	 */
	private void copyOriginal(String pathToSource) {
		File srcDir = new File(pathToSource);
		File cpDir = new File(pathToSource + "UnchangedCopy");
		try {
			FileUtils.copyDirectory(srcDir, cpDir);
		} catch (IOException exc) {
			Logger.get().logWarning(exc.getMessage());
			exc.printStackTrace();
		}
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
			FileUtils.copyDirectory(source0.toFile(), destination.toFile());
			FileUtils.copyDirectory(source1.toFile(), destination.toFile());
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
	 * Make dummy makefile creates the dummy makefile with fake targets.
	 *
	 * @param pathToSource
	 *            the path to source tree of coreboot
	 */
	private void makeDummyMakefile(String pathToSource) {
		PrintWriter writer;
		try {
			writer = new PrintWriter(pathToSource + File.separatorChar + "Makefile");
			writer.print("allyesconfig:\nprepare:");
			writer.close();
		} catch (FileNotFoundException exc) {
			Logger.get().logWarning(exc.getMessage());
			exc.printStackTrace();
		}
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
