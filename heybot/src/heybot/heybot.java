package heybot;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import operation.CheckNew;
import operation.Cleanup;
import operation.CleanupSvn;
import operation.NextVersion;
import operation.Operation;
import operation.Upload;
import operation.Review;
import operation.SyncIssue;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.util.TextUtils;

/**
 * Main entry for the program.
 *
 * @author onur
 */
public class heybot
{

    private final static String VERSION = "1.9.6.6-beta";
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
	Options options = buildOptions();
	printLogo();
	printVersion();

	if (args.length == 0)
	{
	    printHelp(options);
	}
	else
	{
	    // parse command line arguments
	    CommandLine line = getParser(options, args);
	    if (line.hasOption("help"))
	    {
		printHelp(options);
	    }
	    else
	    {
		start(line);
	    }
	}
    }

    private static void start(CommandLine line)
    {
	String hbFile = line.getOptionValue("do");
	if (hbFile != null && hbFile.length() > 0)
	{
	    try
	    {
		hbFile = getFullPath(hbFile);
		tryExecute(hbFile);
	    }
	    catch (Exception ex)
	    {
		System.err.println("Ooops! An error occurred while executing [" + line.getOptionValue("do") + "]"
			+ NEWLINE + " " + ex.getMessage() + " ");
	    }
	}
	else
	{
	    printHelp(buildOptions());
	}
    }

    private static void tryExecute(String hbFile) throws Exception
    {
	Properties prop = new Properties();
	prop.load(new FileInputStream(hbFile));

	Operation operation = getOperation(prop);
	if (operation != null)
	{
	    System.out.println("== " + prop.getProperty("OPERATION") + " - " + DATE_FORMATTER.format(new Date()) + NEWLINE);
	    operation.execute(prop);
	    System.out.println(NEWLINE + "== [END]" + " - " + DATE_FORMATTER.format(new Date()));
	}
	prop.store(new FileOutputStream(hbFile), "Last runtime:");
    }

    private static Operation getOperation(Properties prop)
    {
	String opValue = prop.getProperty("OPERATION");
	if (TextUtils.isEmpty(opValue))
	{
	    System.err.println("Ooops! Parameter OPERATION not found in .hb file.");
	}
	else
	{
	    opValue = opValue.toLowerCase(new Locale("tr-TR"));
	    switch (opValue)
	    {
		case "upload":
		    return new Upload();
		case "cleanup":
		    return new Cleanup();
		case "review":
		    return new Review();
		case "check-new":
		    return new CheckNew();
		case "cleanup-svn":
		    return new CleanupSvn();
		case "sync-issue":
		    return new SyncIssue();
		case "next-version":
		    return new NextVersion();
		default:
		    System.err.println("Ooops! Unsupported operation. Please check version and manual.");
	    }
	}

	return null;
    }

    private static String getFullPath(String hbFile)
    {
	if (hbFile.contains("/"))
	{
	    return hbFile; // already full path is given
	}
	else
	{
	    try
	    {
		return new java.io.File(heybot.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent() + "/workspace/" + hbFile;
	    }
	    catch (URISyntaxException ex)
	    {
		System.err.println("Ooops! URISyntaxException caught." + ex.getMessage());
		return "";
	    }
	}
    }

    //<editor-fold defaultstate="collapsed" desc="help">
    private static final String HEADER = NEWLINE + "Designed to help developers on their day-to-day development activities. Works in subversion and redmine ecosystem." + NEWLINE + NEWLINE;

    private static final String FOOTER = NEWLINE + " * For more information and latest versions:" + NEWLINE + "https://github.com/csonuryilmaz/heybot/releases/latest" + NEWLINE + NEWLINE + NEWLINE;

    private static void printHelp(Options options)
    {
	HelpFormatter formatter = new HelpFormatter();
	formatter.setOptionComparator(new MyOptionComparator());
	formatter.printHelp("heybot", HEADER, options, FOOTER, true);
    }

//</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="syntax">
    private static Options buildOptions()
    {
	Options options = new Options();

	options.addOption("d", "do", true, "Heybot operation file with .hb extension." + NEWLINE + "something.hb");
	options.getOption("do").setRequired(true);

	options.addOption("h", "help", false, "Prints this help message.");

	return options;
    }

    private static void printLogo()
    {
	String logo = "  _           _       _    "
		+ NEWLINE + " | |_ ___ _ _| |_ ___| |_  "
		+ NEWLINE + " |   | -_| | | . | . |  _| "
		+ NEWLINE + " |_|_|___|_  |___|___|_|   "
		+ NEWLINE + "         |___|             "
		+ "";
	System.out.println(logo);
    }

    private static void printVersion()
    {
	System.out.println(NEWLINE + " " + VERSION + NEWLINE);
    }

    private static class MyOptionComparator<T extends Option> implements Comparator<T>
    {

	private static final String OPTS_ORDER = "oh"; // short option names

	@Override
	public int compare(T o1, T o2)
	{
	    int io1 = OPTS_ORDER.indexOf(o1.getOpt());
	    int io2 = OPTS_ORDER.indexOf(o2.getOpt());

	    if (io1 >= 0)
	    {
		return io1 - io2;
	    }

	    return 1; // no index defined, default tail!
	}
    }

    private static CommandLine getParser(Options options, String[] args)
    {
	// create the parser
	CommandLineParser parser = new DefaultParser();
	try
	{
	    // parse the command line arguments
	    return parser.parse(options, args);
	}
	catch (ParseException exp)
	{
	    // oops, something went wrong
	    System.err.println("Ooops! Parsing failed.  Reason: " + exp.getMessage());
	    System.exit(1);
	}
	return null;
    }
    //</editor-fold>
}
