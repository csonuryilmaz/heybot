package heybot;

import java.io.FileInputStream;
import java.io.IOException;
import operation.Upload;
import operation.Cleanup;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Main entry for the program.
 *
 * @author onur
 */
public class heybot
{

    private final static String VERSION = "1.3.1.0";
    private static final String NEWLINE = System.getProperty("line.separator");

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
	// build programming interface
	Options options = buildOptions();

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
	if (hbFile == null)
	{
	    System.err.println("Ooops! Unknown operation argument.");
	}
	else
	{
	    try
	    {
		tryReadHbFile(hbFile);
	    }
	    catch (IOException ex)
	    {
		System.err.println("Ooops! Hb file not found or format incompatible. (" + ex.getMessage() + ")");
	    }
	}
    }

    private static void tryReadHbFile(String hbFile) throws IOException
    {
	Properties prop = new Properties();
	prop.load(new FileInputStream(hbFile));

	String operation = prop.getProperty("OPERATION");
	if (operation == null)
	{
	    System.err.println("Ooops! Parameter OPERATION not found in .hb file.");
	}
	else
	{
	    tryDoOperation(prop, operation);
	}
    }

    private static void tryDoOperation(Properties prop, String operation)
    {
	operation = operation.toLowerCase(new Locale("tr-TR"));

	switch (operation)
	{
	    case "upload":
		//new Upload().execute(prop);
		System.out.println("Upload is executed");
		break;
	    case "cleanup":
		//new Cleanup().execute(prop);
		System.out.println("Cleanup is executed");
		break;
	    default:
		System.err.println("Ooops! Unsupported operation. Please check version and manual.");
		break;
	}
    }

    //<editor-fold defaultstate="collapsed" desc="help">
    private static final String HEADER = NEWLINE + "Designed to help developers on their day-to-day development activities. Works in subversion and redmine ecosystem." + NEWLINE + NEWLINE;

    private static final String FOOTER = NEWLINE + "For additional information, see " + "https://github.com/csonuryilmaz/utilities/tree/master/heybot" + NEWLINE + NEWLINE + "Version: " + VERSION + NEWLINE + NEWLINE;

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
