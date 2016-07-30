package heybot;

import operation.Upload;
import operation.Cleanup;
import java.util.Comparator;
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

    /**
     * Ex: -o upload -h 10.10.10.108 -u root -p RSohat888 -t /var/www/html/kitapyurdu/ -s /Users/onuryilmaz/kitapyurdu_projects/web/kitapyurdu
     *
     * Ex: -o cleanup -ldr /Users/onuryilmaz/kitapyurdu_projects/web/branch -sdr https://kyrepo.sourcerepo.com/kyrepo/web/branch -rdt abab3a53c34f66b92da5cdcbb3bb95a3c78d691e -rdu https://kyrepo-apps.sourcerepo.com/redmine/kyrepo -lim 1
     */
    private final static String VERSION = "1.2.0.0";
    private final static String GITHUB = "https://github.com/csonuryilmaz";
    private final static String NOTES = "ATTENTION! In this version operation *revert* not working. ";

    public final static String ERROR_MISSING_PARAMETERS = "Ooops! Missing parameters. Please look at *help message* without using any parameter.";

    //<editor-fold defaultstate="collapsed" desc="INTERFACE">
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final String HEADER = "Some utilities to work with subversion and ftp, remote vs. local changes." + NEWLINE + NEWLINE;
    private static final String FOOTER = NEWLINE + "For additional information, see " + GITHUB + NEWLINE + "Version: " + VERSION + NEWLINE + NEWLINE + NOTES;

    private static Options buildOptions()
    {
	Options options = new Options();

	options.addOption("o", "operation", true, "Operation to run. Arguments can be either:" + NEWLINE
		+ "upload -> Uploads local changes to remote server." + NEWLINE
		+ "revert -> Reverts local changes from remote server." + NEWLINE
		+ "cleanup -> Cleanups *closed* issues from local working directory and subversion.");
	options.getOption("operation").setRequired(true);

	// OPERATION: upload
	options.addOption("h", "host", true, "remote server to connect");
	options.addOption("u", "username", true, "username to login remote servr");
	options.addOption("p", "password", true, "password to login remote server");
	options.addOption("t", "target-directory", true, "remote working directory to send changes");
	options.addOption("s", "source-directory", true, "local working directory to take changes");
	options.addOption("r", "revision", true, "specific revision to take changes");

	// OPERATION: cleanup
	// host, username, password are same as upload operation
	options.addOption("ldr", "loc-dir", true, "branch local working directory used as workspace");
	options.addOption("sdr", "svn-dir", true, "branch subversion directory where all branches kept");
	options.addOption("rdt", "rdm-token", true, "redmine access token (personal token taken from redmine account page)");
	options.addOption("rdu", "rdm-url", true, "redmine url (api uri, mostly root url)");
	options.addOption("lim", "limit", true, "maximum count to delete branches");

	return options;
    }

    private static class MyOptionComparator<T extends Option> implements Comparator<T>
    {

	private static final String OPTS_ORDER = "ohtupsr"; // short option names

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
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
	// build programming interface
	Options options = buildOptions();

	if (args.length == 0)
	{
	    // print usage message
	    HelpFormatter formatter = new HelpFormatter();
	    formatter.setOptionComparator(new MyOptionComparator());
	    formatter.printHelp("heybot", HEADER, options, FOOTER, true);
	}
	else
	{
	    // parse command line arguments
	    CommandLine line = getParser(options, args);

	    // try get operation argument
	    String operation = line.getOptionValue("operation");
	    if (operation == null || operation.equals("upload"))
	    {
		new Upload().execute(line);
	    }
	    else if (operation.equals("cleanup"))
	    {
		new Cleanup().execute(line);
	    }
	    else if (operation.equals("revert"))
	    {
		// @todo: implement operation
		System.err.println("Ooops! Not implemented yet. :( Please, be patient.");
	    }
	    else
	    {
		System.err.println("Ooops! Unknown operation argument.");
	    }
	}
    }

}
