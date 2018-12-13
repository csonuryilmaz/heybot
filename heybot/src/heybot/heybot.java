package heybot;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import utilities.Properties;
import operation.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.http.util.TextUtils;
import org.apache.commons.io.comparator.NameFileComparator;
import utilities.Copy;
import utilities.Editor;
import utilities.Open;

public class heybot
{

    private final static String VERSION = "1.29.2.2";
    private static final String NEWLINE = System.getProperty("line.separator");
    public static final String WORKSPACE = System.getProperty("user.home") + "/.heybot/workspace";

    public static void main(String[] args)
    {
	printLogo();
	printVersion();
	createWorkspaceIfNotExists();

	if (args.length > 0)
	{
	    processArgs(args);
	}
	else
	{
	    printHelp();
	}
    }

    private static void processArgs(String[] args)
    {
	CommandLine line = getParser(buildOptions(), args);
	if (line.hasOption("help"))
	{
	    printHelp();
	}
	else if (line.hasOption("version"))
	{
	    printVersionDetailed();
	}
	else if (line.hasOption("list"))
	{
	    listAllOperations();
	}
	else if (line.hasOption("list-prefix"))
	{
	    listOperationsStartsWith(line.getOptionValue("list-prefix"));
	}
	else if (line.hasOption("insert"))
	{
	    if (line.getArgs().length > 0)
	    {
		insertOperationParameters(line.getOptionValue("insert"), line.getArgs());
	    }
	    else
	    {
		System.err.println("Ooops! Please add parameters after operation.");
	    }
	}
	else if (line.hasOption("select"))
	{
	    selectOperationParameters(line.getOptionValue("select"), line.getArgs());
	}
	else if (line.hasOption("open"))
	{
	    openOperation(line.getOptionValue("open"));
	}
	else if (line.hasOption("remove"))
	{
	    removeOperation(line.getOptionValue("remove"));
	}
	else if (line.hasOption("editor"))
	{
	    setDefaultEditor(line.getOptionValue("editor"));
	}
	else if (line.hasOption("copy"))
	{
	    copyOperation(line);
	}
	else
	{
	    start(line);
	}
    }

    private static void listAllOperations()
    {
	listFiles(new File(WORKSPACE).listFiles((File dir, String name) -> name.toLowerCase().endsWith(".hb")));
    }

    private static void listOperationsStartsWith(String prefix)
    {
	listFiles(new File(WORKSPACE).listFiles((File dir, String name) -> name.toLowerCase().startsWith(prefix.toLowerCase()) && name.toLowerCase().endsWith(".hb")));
    }

    private static void listFiles(File[] files)
    {
	if (files != null)
	{
	    Arrays.sort(files, NameFileComparator.NAME_COMPARATOR);
	    for (File file : files)
	    {
		System.out.println(file.getName());
	    }

	    printTotalOperationsFound(files.length);
	}
	else
	{
	    printTotalOperationsFound(0);
	}
    }

    private static void printTotalOperationsFound(int count)
    {
	System.out.println();
	System.out.println("Total " + count + " operation(s) found.");
    }

    private static void printVersionDetailed()
    {
	System.out.println("Copyright (c) 2017 Onur Yılmaz");
	System.out.println("MIT License: <https://github.com/csonuryilmaz/heybot/blob/master/LICENSE.txt>");
	System.out.println("This is free software: you are free to change and redistribute it.");
	System.out.println("There is NO WARRANTY, to the extent permitted by law.");
    }

    private static void start(CommandLine line)
    {
	String[] args = line.getArgs();
	if (args.length == 1)
	{
	    processOperation(args[0]);
	}
	else
	{
	    String doOptionValue = line.getOptionValue("do");
	    if (!TextUtils.isEmpty(doOptionValue))
	    {
		processOperation(doOptionValue);
	    }
	    else
	    {
		System.err.println("Ooops! Unrecognized argument. Please refer to documentation.");
	    }
	}
    }

    private static void processOperation(String operation)
    {
	try
	{
	    tryExecute(getFullPath(getFileName(operation)));
	}
	catch (Exception ex)
	{
	    System.err.println("Ooops! An error occurred while executing [" + operation + "]"
		    + NEWLINE + " " + ex.getMessage() + " ");
	}
    }

    private static String getFileName(String arg)
    {
	return arg.endsWith(".hb") ? arg : arg + ".hb";
    }

    private static void tryExecute(String hbFile) throws Exception
    {
	Properties prop = new Properties();
	prop.load(hbFile);

	Operation operation = getOperation(prop);
	if (operation != null)
	{
	    operation.start(prop);
	}
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
		case "release":
		    return new Release();
		case "begin-issue":
		    return new BeginIssue();
		case "snapshot":
		    return new Snapshot();
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
	    return WORKSPACE + "/" + hbFile;
	}
    }

    private static void createWorkspaceIfNotExists()
    {
	File workspace = new File(WORKSPACE);
	if (!workspace.exists())
	{
	    if (!workspace.mkdirs())
	    {
		System.err.println("[e] Workspace folder not found and could not be created!");
		System.err.println("[e] " + workspace.getAbsolutePath());
		System.exit(-1);
	    }
	}
    }

    //<editor-fold defaultstate="collapsed" desc="help">
    private static final String HEADER = NEWLINE + "Designed to help developers on their day-to-day development activities. Works in subversion and redmine ecosystem." + NEWLINE + NEWLINE;

    private static final String FOOTER = NEWLINE
	    + "Report bugs to \"csonuryilmaz@gmail.com\" " + NEWLINE
	    + "  or drop an issue @ http://bit.ly/2S83xvy" + NEWLINE
	    + "Releases: http://bit.ly/2BukdrR" + NEWLINE
	    + "Document: http://bit.ly/2Sd8zqQ" + NEWLINE
	    + NEWLINE + "Happy coding!" + NEWLINE;

    private static void printHelp()
    {
	HelpFormatter formatter = new HelpFormatter();
	formatter.setOptionComparator(new MyOptionComparator());
	formatter.printHelp("heybot", HEADER, buildOptions(), FOOTER, true);
    }

//</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="syntax">
    private static Options buildOptions()
    {
	Options options = new Options();

	options.addOption("d", "do", true, "Heybot operation file with .hb extension." + NEWLINE + "something.hb");
	options.getOption("do").setRequired(true);

	options.addOption("h", "help", false, "Prints this help message.");
	options.addOption("v", "version", false, "Prints detailed version information.");
	options.addOption("l", "list", false, "Lists all operation files in workspace.");
	options.addOption("lp", "list-prefix", true, "Lists operation files in workspace which starts with given value.");
	options.addOption("i", "insert", true, "Inserts (by replacing if exists) given parameter values of an operation.");
	options.addOption("s", "select", true, "Selects all parameters with values of an operation");
	options.addOption("o", "open", true, "Opens operation file by default external editor in order to view or modify.");
	options.addOption("r", "remove", true, "Removes operation file from workspace.");

	Option editor = new Option("e", "editor", true, "Sets default external editor used when a file needs to be viewed or edited.");
	editor.setOptionalArg(true);
	options.addOption(editor);

	options.addOption(new Option("c", "copy", true, "Copies an operation file as a new operation file in workspace."));

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

    private static void insertOperationParameters(String operation, String[] parameters)
    {
	try
	{
	    String hbFile = WORKSPACE + "/" + getFileName(operation);

	    Properties prop = new Properties();
	    prop.load(hbFile);

	    for (String parameter : parameters)
	    {
		insertParameter(parameter, prop);
	    }
	}
	catch (ConfigurationException | FileNotFoundException ex)
	{
	    System.err.println("Ooops! Error occurred while handling operation file: " + ex.getMessage());
	}
    }

    private static void insertParameter(String parameter, Properties prop)
    {
	String[] tokens = parseParameter(parameter);
	if (tokens.length > 0)
	{
	    System.out.println("[*] " + tokens[0]);
	    insertParameter(tokens, prop);
	}
	else
	{
	    System.out.println("[x] " + "Unparsable operation parameter: ");
	    System.out.println(parameter);
	}
    }

    private static void insertParameter(String[] tokens, Properties prop)
    {
	String value = prop.getProperty(tokens[0]);
	if (value != null)
	{
	    System.out.println(" - " + value);
	    setParameter(tokens, prop);
	}
	else
	{
	    setParameter(tokens, prop);
	}
    }

    private static void setParameter(String[] tokens, Properties prop)
    {
	System.out.println(" + " + tokens[1]);
	prop.setProperty(tokens[0], tokens[1]);
	System.out.println("[✓] Inserted.");
    }

    private static String[] parseParameter(String parameter)
    {
	int i = parameter.indexOf("=");
	if (i > 0)
	{
	    String[] tokens = new String[2];
	    tokens[0] = parameter.substring(0, i);
	    tokens[1] = parameter.substring(i + 1);

	    return tokens;
	}

	return new String[0];
    }

    private static void selectOperationParameters(String operation, String[] parameters)
    {
	try
	{
	    String hbFile = WORKSPACE + "/" + getFileName(operation);

	    Properties prop = new Properties();
	    prop.load(hbFile);

	    if (parameters.length == 0)
	    {
		selectAllParameters(prop);
	    }
	    else
	    {
		selectParameters(prop, parameters);
	    }
	}
	catch (ConfigurationException | FileNotFoundException ex)
	{
	    System.err.println("Ooops! Error occurred while handling operation file: " + ex.getMessage());
	}
    }

    private static void selectAllParameters(Properties prop)
    {
	String[][] parameters = prop.getAllParameters();
	for (String[] parameter : parameters)
	{
	    System.out.println(parameter[0] + "=" + parameter[1]);
	}
    }

    private static void selectParameters(Properties prop, String[] parameters)
    {
	for (String parameter : parameters)
	{
	    System.out.println(parameter + "="
		    + (prop.getProperty(parameter) != null ? prop.getProperty(parameter) : ""));
	}
    }

    private static void openOperation(String operation)
    {
	try
	{
	    new Open(WORKSPACE, getFileName(operation)).run();
	}
	catch (Exception ex)
	{
	    System.err.println(ex.getMessage());
	}
    }

    private static void removeOperation(String operation)
    {
	File hbFile = new File(WORKSPACE + "/" + getFileName(operation));
	if (hbFile.exists())
	{
	    if (hbFile.isFile() && hbFile.delete())
	    {
		System.out.println("[✓] Removed.");
	    }
	    else
	    {
		System.out.println("[x] Could not be removed. Please check that's an operation file.");
	    }
	}
	else
	{
	    System.out.println("[!] Operation file could not be found in workspace.");
	}
    }

    private static void setDefaultEditor(String editor)
    {
	try
	{
	    new Editor(WORKSPACE, editor).run();
	}
	catch (Exception ex)
	{
	    System.err.println(ex.getMessage());
	}
    }

    private static void copyOperation(CommandLine line)
    {
	try
	{
	    new Copy(WORKSPACE, line).run();
	}
	catch (Exception ex)
	{
	    System.err.println(ex.getMessage());
	}
    }

}
