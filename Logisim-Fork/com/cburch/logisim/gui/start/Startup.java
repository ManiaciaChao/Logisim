/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.gui.start;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.UIManager;
import javax.swing.JOptionPane;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.Main;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Print;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.WindowManagers;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.util.ArgonXML;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.MacCompatibility;
import com.cburch.logisim.util.StringUtil;

public class Startup {
	private static Startup startupTemp = null;

	static void doOpen(File file) {
		if (startupTemp != null)
			startupTemp.doOpenFile(file);
	}

	static void doPrint(File file) {
		if (startupTemp != null)
			startupTemp.doPrintFile(file);
	}

	private void doOpenFile(File file) {
		if (initialized) {
			ProjectActions.doOpen(null, null, file);
		} else {
			filesToOpen.add(file);
		}
	}

	private void doPrintFile(File file) {
		if (initialized) {
			Project toPrint = ProjectActions.doOpen(null, null, file);
			Print.doPrint(toPrint);
			toPrint.getFrame().dispose();
		} else {
			filesToPrint.add(file);
		}
	}

	private static void registerHandler() {
		try {
			Class<?> needed1 = Class.forName("com.apple.eawt.Application");
			if (needed1 == null)
				return;
			Class<?> needed2 = Class.forName("com.apple.eawt.ApplicationAdapter");
			if (needed2 == null)
				return;
			MacOsAdapter.register();
			MacOsAdapter.addListeners(true);
		} catch (ClassNotFoundException e) {
			return;
		} catch (Throwable t) {
			try {
				MacOsAdapter.addListeners(false);
			} catch (Throwable t2) {
			}
		}
	}

	// based on command line
	boolean isTty;
	private File templFile = null;
	private boolean templEmpty = false;
	private boolean templPlain = false;
	private ArrayList<File> filesToOpen = new ArrayList<File>();
	private boolean showSplash;
	private File loadFile;
	private HashMap<File, File> substitutions = new HashMap<File, File>();
	private int ttyFormat = 0;

	// from other sources
	private boolean initialized = false;
	private SplashScreen monitor = null;
	private ArrayList<File> filesToPrint = new ArrayList<File>();

	public Startup(boolean isTty) {
		this.isTty = isTty;
		this.showSplash = !isTty;
	}

	List<File> getFilesToOpen() {
		return filesToOpen;
	}

	File getLoadFile() {
		return loadFile;
	}

	int getTtyFormat() {
		return ttyFormat;
	}

	Map<File, File> getSubstitutions() {
		return Collections.unmodifiableMap(substitutions);
	}

	public void run() {
		if (isTty) {
			try {
				TtyInterface.run(this);
				return;
			} catch (Throwable t) {
				t.printStackTrace();
				System.exit(-1);
				return;
			}
		}

		// kick off the progress monitor
		// (The values used for progress values are based on a single run where
		// I loaded a large file.)
		if (showSplash) {
			try {
				monitor = new SplashScreen();
				monitor.setVisible(true);
			} catch (Throwable t) {
				monitor = null;
				showSplash = false;
			}
		}

		// pre-load the two basic component libraries, just so that the time
		// taken is shown separately in the progress bar.
		if (showSplash)
			monitor.setProgress(SplashScreen.LIBRARIES);
		Loader templLoader = new Loader(monitor);
		int count = templLoader.getBuiltin().getLibrary("Base").getTools().size()
				+ templLoader.getBuiltin().getLibrary("Gates").getTools().size();
		if (count < 0) {
			// this will never happen, but the optimizer doesn't know that...
			System.err.println("FATAL ERROR - no components"); // OK
			System.exit(-1);
		}

		// load in template
		loadTemplate(templLoader, templFile, templEmpty);

		// now that the splash screen is almost gone, we do some last-minute
		// interface initialization
		if (showSplash)
			monitor.setProgress(SplashScreen.GUI_INIT);
		WindowManagers.initialize();
		if (MacCompatibility.isSwingUsingScreenMenuBar()) {
			MacCompatibility.setFramelessJMenuBar(new LogisimMenuBar(null, null));
		} else {
			new LogisimMenuBar(null, null);
			// most of the time occupied here will be in loading menus, which
			// will occur eventually anyway; we might as well do it when the
			// monitor says we are
		}

		// if user has double-clicked a file to open, we'll
		// use that as the file to open now.
		initialized = true;

		// load file
		if (filesToOpen.isEmpty()) {
			ProjectActions.doNew(monitor, true);
			if (showSplash)
				monitor.close();
		} else {
			boolean first = true;
			for (File fileToOpen : filesToOpen) {
				try {
					ProjectActions.doOpen(monitor, fileToOpen, substitutions);
				} catch (LoadFailedException ex) {
					System.err.println(fileToOpen.getName() + ": " + ex.getMessage()); // OK
					System.exit(-1);
				}
				if (first) {
					first = false;
					if (showSplash)
						monitor.close();
					monitor = null;
				}
			}
		}

		for (File fileToPrint : filesToPrint) {
			doPrintFile(fileToPrint);
		}
	}

	private static void setLocale(String lang) {
		Locale[] opts = Strings.getLocaleOptions();
		for (int i = 0; i < opts.length; i++) {
			if (lang.equals(opts[i].toString())) {
				LocaleManager.setLocale(opts[i]);
				return;
			}
		}
		System.err.println(Strings.get("invalidLocaleError")); // OK
		System.err.println(Strings.get("invalidLocaleOptionsHeader")); // OK
		for (int i = 0; i < opts.length; i++) {
			System.err.println("   " + opts[i].toString()); // OK
		}
		System.exit(-1);
	}

	private void loadTemplate(Loader loader, File templFile, boolean templEmpty) {
		if (showSplash)
			monitor.setProgress(SplashScreen.TEMPLATE_OPEN);
		if (templFile != null) {
			AppPreferences.setTemplateFile(templFile);
			AppPreferences.setTemplateType(AppPreferences.TEMPLATE_CUSTOM);
		} else if (templEmpty) {
			AppPreferences.setTemplateType(AppPreferences.TEMPLATE_EMPTY);
		} else if (templPlain) {
			AppPreferences.setTemplateType(AppPreferences.TEMPLATE_PLAIN);
		}
	}

	public static Startup parseArgs(String[] args) {
		// see whether we'll be using any graphics
		boolean isTty = false;
		boolean isClearPreferences = false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-tty")) {
				isTty = true;
			} else if (args[i].equals("-clearprefs") || args[i].equals("-clearprops")) {
				isClearPreferences = true;
			}
		}

		if (!isTty) {
			// we're using the GUI: Set up the Look&Feel to match the platform
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Logisim");
			System.setProperty("apple.laf.useScreenMenuBar", "true");

			LocaleManager.setReplaceAccents(false);

			// Initialize graphics acceleration if appropriate
			AppPreferences.handleGraphicsAcceleration();
		}

		Startup ret = new Startup(isTty);
		startupTemp = ret;
		if (!isTty) {
			registerHandler();
		}

		if (isClearPreferences) {
			AppPreferences.clear();
		}

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ex) {
		}

		// parse arguments
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("-tty")) {
				if (i + 1 < args.length) {
					i++;
					String[] fmts = args[i].split(",");
					if (fmts.length == 0) {
						System.err.println(Strings.get("ttyFormatError")); // OK
					}
					for (int j = 0; j < fmts.length; j++) {
						String fmt = fmts[j].trim();
						if (fmt.equals("table")) {
							ret.ttyFormat |= TtyInterface.FORMAT_TABLE;
						} else if (fmt.equals("speed")) {
							ret.ttyFormat |= TtyInterface.FORMAT_SPEED;
						} else if (fmt.equals("tty")) {
							ret.ttyFormat |= TtyInterface.FORMAT_TTY;
						} else if (fmt.equals("halt")) {
							ret.ttyFormat |= TtyInterface.FORMAT_HALT;
						} else if (fmt.equals("stats")) {
							ret.ttyFormat |= TtyInterface.FORMAT_STATISTICS;
						} else {
							System.err.println(Strings.get("ttyFormatError")); // OK
						}
					}
				} else {
					System.err.println(Strings.get("ttyFormatError")); // OK
					return null;
				}
			} else if (arg.equals("-sub")) {
				if (i + 2 < args.length) {
					File a = new File(args[i + 1]);
					File b = new File(args[i + 2]);
					if (ret.substitutions.containsKey(a)) {
						System.err.println(Strings.get("argDuplicateSubstitutionError")); // OK
						return null;
					} else {
						ret.substitutions.put(a, b);
						i += 2;
					}
				} else {
					System.err.println(Strings.get("argTwoSubstitutionError")); // OK
					return null;
				}
			} else if (arg.equals("-load")) {
				if (i + 1 < args.length) {
					i++;
					if (ret.loadFile != null) {
						System.err.println(Strings.get("loadMultipleError")); // OK
					}
					File f = new File(args[i]);
					ret.loadFile = f;
				} else {
					System.err.println(Strings.get("loadNeedsFileError")); // OK
					return null;
				}
			} else if (arg.equals("-empty")) {
				if (ret.templFile != null || ret.templEmpty || ret.templPlain) {
					System.err.println(Strings.get("argOneTemplateError")); // OK
					return null;
				}
				ret.templEmpty = true;
			} else if (arg.equals("-plain")) {
				if (ret.templFile != null || ret.templEmpty || ret.templPlain) {
					System.err.println(Strings.get("argOneTemplateError")); // OK
					return null;
				}
				ret.templPlain = true;
			} else if (arg.equals("-version")) {
				System.out.println(Main.VERSION_NAME); // OK
				return null;
			} else if (arg.equals("-gates")) {
				i++;
				if (i >= args.length)
					printUsage();
				String a = args[i];
				if (a.equals("shaped")) {
					AppPreferences.GATE_SHAPE.set(AppPreferences.SHAPE_SHAPED);
				} else if (a.equals("rectangular")) {
					AppPreferences.GATE_SHAPE.set(AppPreferences.SHAPE_RECTANGULAR);
				} else {
					System.err.println(Strings.get("argGatesOptionError")); // OK
					System.exit(-1);
				}
			} else if (arg.equals("-locale")) {
				i++;
				if (i >= args.length)
					printUsage();
				setLocale(args[i]);
			} else if (arg.equals("-accents")) {
				i++;
				if (i >= args.length)
					printUsage();
				String a = args[i];
				if (a.equals("yes")) {
					AppPreferences.ACCENTS_REPLACE.setBoolean(false);
				} else if (a.equals("no")) {
					AppPreferences.ACCENTS_REPLACE.setBoolean(true);
				} else {
					System.err.println(Strings.get("argAccentsOptionError")); // OK
					System.exit(-1);
				}
			} else if (arg.equals("-template")) {
				if (ret.templFile != null || ret.templEmpty || ret.templPlain) {
					System.err.println(Strings.get("argOneTemplateError")); // OK
					return null;
				}
				i++;
				if (i >= args.length)
					printUsage();
				ret.templFile = new File(args[i]);
				if (!ret.templFile.exists()) {
					System.err.println(StringUtil.format( // OK
							Strings.get("templateMissingError"), args[i]));
				} else if (!ret.templFile.canRead()) {
					System.err.println(StringUtil.format( // OK
							Strings.get("templateCannotReadError"), args[i]));
				}
			} else if (arg.equals("-nosplash")) {
				ret.showSplash = false;
			} else if (arg.equals("-clearprefs")) {
				// already handled above
			} else if (arg.equals("-analyze")) {
				Main.ANALYZE = true;
			} else if (arg.equals("-noupdates")) {
				Main.UPDATE = false;
			} else if (arg.charAt(0) == '-') {
				printUsage();
				return null;
			} else {
				ret.filesToOpen.add(new File(arg));
			}
		}
		if (ret.isTty && ret.filesToOpen.isEmpty()) {
			System.err.println(Strings.get("ttyNeedsFileError")); // OK
			return null;
		}
		if (ret.loadFile != null && !ret.isTty) {
			System.err.println(Strings.get("loadNeedsTtyError")); // OK
			return null;
		}
		return ret;
	}

	private static void printUsage() {
		System.err.println(StringUtil.format(Strings.get("argUsage"), Startup.class.getName())); // OK
		System.err.println(); // OK
		System.err.println(Strings.get("argOptionHeader")); // OK
		System.err.println("   " + Strings.get("argAccentsOption")); // OK
		System.err.println("   " + Strings.get("argClearOption")); // OK
		System.err.println("   " + Strings.get("argEmptyOption")); // OK
		System.err.println("   " + Strings.get("argGatesOption")); // OK
		System.err.println("   " + Strings.get("argHelpOption")); // OK
		System.err.println("   " + Strings.get("argLoadOption")); // OK
		System.err.println("   " + Strings.get("argLocaleOption")); // OK
		System.err.println("   " + Strings.get("argNoSplashOption")); // OK
		System.err.println("   " + Strings.get("argPlainOption")); // OK
		System.err.println("   " + Strings.get("argSubOption")); // OK
		System.err.println("   " + Strings.get("argTemplateOption")); // OK
		System.err.println("   " + Strings.get("argTtyOption")); // OK
		System.err.println("   " + Strings.get("argVersionOption")); // OK
		System.exit(-1);
	}

	/**
	 * Auto-update Logisim if a new version is available
	 * 
	 * Original idea taken from logisim-evolution:
	 * https://github.com/reds-heig/logisim-evolution
	 * 
	 * @return true if the code has been updated, and therefore the execution
	 *         has to be stopped, false otherwise
	 */
	public boolean autoUpdate(boolean FromMenu) { // from "check version" = 1
		if (!Main.UPDATE || !networkConnectionAvailable()) {
			// Auto-update disabled from command line, or network connection not
			// available
			return (false);
		}

		// Get the remote XML file containing the current version
		URL xmlURL;
		URLConnection conn;
		InputStream in;
		try {
			xmlURL = new URL(Main.UPDATE_URL);
			conn = xmlURL.openConnection();
			in = conn.getInputStream();
		} catch (MalformedURLException e) {
			System.err.println("The URL of the XML file for the auto-updater is malformed.");
			System.err.println("Please report this error to the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		} catch (IOException e) {
			System.err.println(
					"Although an Internet connection should be available, the system couldn't connect to the URL requested by the auto-updater");
			System.err.println("If the error persist, please contact the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}
		ArgonXML logisimData = new ArgonXML(in, "logisim");

		// Get the appropriate remote version number
		LogisimVersion remoteVersion = LogisimVersion.parse(Main.VERSION.isJar()
				? logisimData.child("jar_version").content() : logisimData.child("exe_version").content());

		// If the remote version is newer, perform the update
		if (remoteVersion.compareTo(Main.VERSION) > 0) {

			String changelog = logisimData.child("changelog").content();
			int answer = JOptionPane.showConfirmDialog(null,
					StringUtil.format(Strings.get("UpdateMessage"), remoteVersion.toString(), changelog),
					Strings.get("Update"), JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

			if (answer == 1) {
				// User refused to update -- we just hope he gets sufficiently
				// annoyed by the message that he finally updates!
				return (false);
			}

			// Obtain the base directory of the archive
			CodeSource codeSource = Startup.class.getProtectionDomain().getCodeSource();
			File jarFile = null;
			try {
				jarFile = new File(codeSource.getLocation().toURI().getPath());
			} catch (URISyntaxException e) {
				System.err.println("Error in the syntax of the URI for the path of the executed Logisim file!");
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"An error occurred while updating to the new Logisim version.\nPlease check the console for log information.",
						"Update failed", JOptionPane.ERROR_MESSAGE);
				return (false);
			}

			// Get the appropriate remote filename to download
			String remoteJar = Main.VERSION.isJar() ? logisimData.child("jar_file").content()
					: logisimData.child("exe_file").content();

			boolean updateOk = downloadInstallUpdatedVersion(remoteJar, jarFile.getAbsolutePath());

			if (updateOk) {
				JOptionPane.showMessageDialog(null,
						StringUtil.format(Strings.get("UpdateSucceededMessage"), remoteVersion.toString()),
						Strings.get("UpdateSucceeded"), JOptionPane.INFORMATION_MESSAGE);
				return (true);
			} else {
				JOptionPane.showMessageDialog(null, Strings.get("UpdateFailedMessage"), Strings.get("UpdateFailed"),
						JOptionPane.ERROR_MESSAGE);
				return (false);
			}
		} else if (FromMenu) {
			JOptionPane.showMessageDialog(null, Strings.get("NoUpdates"), Strings.get("Update"),
					JOptionPane.INFORMATION_MESSAGE);
		}
		return (false);
	}

	/**
	 * Download a new version of Logisim, according to the instructions received
	 * from autoUpdate(), and install it at the specified location
	 * 
	 * Original idea taken from:
	 * http://baptiste-wicht.developpez.com/tutoriels/java/update/ by Baptiste
	 * Wicht
	 *
	 * @param filePath
	 *            remote file URL
	 * @param destination
	 *            local destination for the updated Jar file
	 * @return true if the new version has been downloaded and installed, false
	 *         otherwise
	 * @throws IOException
	 */
	private boolean downloadInstallUpdatedVersion(String filePath, String destination) {
		URL fileURL;
		try {
			fileURL = new URL(filePath);
		} catch (MalformedURLException e) {
			System.err.println("The URL of the requested update file is malformed.");
			System.err.println("Please report this error to the software maintainer.");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}
		URLConnection conn;
		try {
			conn = fileURL.openConnection();
		} catch (IOException e) {
			System.err.println(
					"Although an Internet connection should be available, the system couldn't connect to the URL of the updated file requested by the auto-updater.");
			System.err.println("If the error persist, please contact the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}

		// Get remote file size
		int length = conn.getContentLength();
		if (length == -1) {
			System.err.println("Cannot retrieve the file containing the updated version.");
			System.err.println("If the error persist, please contact the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}

		// Get remote file stream
		InputStream is;
		try {
			is = new BufferedInputStream(conn.getInputStream());
		} catch (IOException e) {
			System.err.println("Cannot get remote file stream.");
			System.err.println("If the error persist, please contact the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}

		// Local file buffer
		byte[] data = new byte[length];

		// Helper variables for marking the current position in the downloaded
		// file
		int currentBit = 0;
		int deplacement = 0;

		// Download remote content
		try {
			while (deplacement < length) {
				currentBit = is.read(data, deplacement, data.length - deplacement);

				if (currentBit == -1) {
					// Reached EOF
					break;
				}
				deplacement += currentBit;
			}
		} catch (IOException e) {
			System.err.println("An error occured while retrieving remote file (remote peer hung up).");
			System.err.println("If the error persist, please contact the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}
		// Close remote stream
		try {
			is.close();
		} catch (IOException e) {
			System.err.println("Error encountered while closing the remote stream!");
			e.printStackTrace();
		}

		// If not all the bytes have been retrieved, abort update
		if (deplacement != length) {
			System.err.println(
					"An error occured while retrieving remote file (local size != remote size), download corrupted.");
			System.err.println("If the error persist, please contact the software maintainer");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}

		// Open stream for local Jar and write data
		FileOutputStream destinationFile;
		try {
			destinationFile = new FileOutputStream(destination);
		} catch (FileNotFoundException e) {
			System.err.println("An error occured while opening the local Jar file.");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			return (false);
		}
		try {
			destinationFile.write(data);
			destinationFile.flush();
		} catch (IOException e) {
			System.err.println("An error occured while writing to the local Jar file.");
			System.err.println("-- AUTO-UPDATE ABORTED --");
			System.err.println(
					"The local file might be corrupted. If this is the case, please download a new copy of Logisim.");
		} finally {
			try {
				destinationFile.close();
			} catch (IOException e) {
				System.err.println("Error encountered while closing the local destination file!");
				System.err.println(
						"The local file might be corrupted. If this is the case, please download a new copy of Logisim.");
				return (false);
			}
		}

		return (true);
	}

	/**
	 * Check if network connection is available.
	 * 
	 * This function tries to connect to google in order to test the
	 * availability of a network connection. This step is needed before
	 * attempting to perform an auto-update. It assumes that google is
	 * accessible -- usually this is the case, and it should also provide a
	 * quick reply to the connection attempt, reducing the lag.
	 * 
	 * @return true if the connection is available, false otherwise
	 */
	private boolean networkConnectionAvailable() {
		try {
			URL url = new URL("http://www.google.com");
			URLConnection uC = url.openConnection();
			uC.connect();
			return (true);
		} catch (MalformedURLException e) {
			System.err.println("The URL used to check the connectivity is malformed -- no Google?");
			e.printStackTrace();
		} catch (IOException e) {
			// If we get here, the connection somehow failed
			return (false);
		}
		return (false);
	}
}