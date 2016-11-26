package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Project;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for all operations in operation package.
 *
 * @author onuryilmaz
 */
public abstract class Operation
{

    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Locale trLocale = new Locale("tr-TR");

    protected Operation(String[] mandatoryParameters)
    {
	this.mandatoryParameters = mandatoryParameters;
	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));
    }

    protected Operation()
    {
	this.mandatoryParameters = null;
	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));
    }

    //<editor-fold defaultstate="collapsed" desc="execute shell command">
    protected String tryExecute(String command)
    {
	String[] output = execute(command);

	if (output == null)
	{// :(
	    System.err.println("Ooops! Command execution (" + command + ") with no result.");
	}
	else if (output[1].length() > 0)
	{// :(
	    System.err.println("Ooops! Command execution (" + command + ") failed with message: ");
	    System.err.println(output[1]);
	}
	else
	{
	    return output[0];
	}

	return "";
    }

    protected boolean tryExecute(String[] command)
    {
	String[] output = execute(command);

	if (output[1].length() > 0)
	{// :(
	    System.err.println("Ooops! Command execution (" + command + ") failed with message: ");
	    System.err.println(output[1]);

	    return false;
	}

	if (output != null)
	{
	    System.out.println(output[0]);
	}
	return true;
    }

    private String[] execute(String[] command)
    {
	Process process;
	try
	{
	    process = new ProcessBuilder(command).start(); // array of command and arguments

	    return execute(process);
	}
	catch (NumberFormatException ex)
	{
	    System.err.println(ex);
	}
	catch (IOException | InterruptedException ex)
	{
	    System.err.println(ex);
	}

	return null;
    }

    private String[] execute(String command)
    {
	Process process;
	try
	{
	    process = Runtime.getRuntime().exec(command);

	    return execute(process);
	}
	catch (NumberFormatException ex)
	{
	    System.err.println(ex);
	}
	catch (IOException | InterruptedException ex)
	{
	    System.err.println(ex);
	}

	return null;
    }

    private String[] execute(Process process) throws InterruptedException, IOException
    {
	int r = process.waitFor();

	InputStream ns = process.getInputStream();// normal output stream
	InputStream es = process.getErrorStream();// error output stream

	String[] output = new String[2];
	output[0] = read(ns);
	output[1] = read(es);

	ns.close();
	es.close();

	return output;
    }

    private String read(InputStream is)
    {
	try
	{
	    InputStreamReader isr = new InputStreamReader(is);
	    BufferedReader br = new BufferedReader(isr);

	    StringBuilder output = new StringBuilder();
	    String line;
	    while ((line = br.readLine()) != null)
	    {
		output.append("\n");
		output.append(line);
	    }

	    br.close();
	    isr.close();

	    if (output.length() > 0)
	    {
		return output.substring(1);// remove head new line
	    }
	}
	catch (IOException ex)
	{
	    System.err.println(ex);
	}

	return "";
    }
    //</editor-fold>

    public abstract void execute(Properties prop);

    protected String tryGetRepoRootDir(String svnCommand, String sftpSourceDir)
    {
	String output = tryExecute(svnCommand + " info " + sftpSourceDir);

	if (output.length() > 0)
	{// :) no error message
	    String rUrl = tryGetRowValue(output, "Relative URL:");
	    if (rUrl == null)
	    {// try get Relative URL by URL - Repository Root
		String url = tryGetRowValue(output, "URL:");
		String rRoot = tryGetRowValue(output, "Repository Root:");
		if (url != null && rRoot != null)
		{
		    return url.replace(rRoot, "^");
		}
	    }
	    return rUrl;
	}

	return "";
    }

    private String tryGetRowValue(String row, String key)
    {
	int index = row.indexOf(key);

	if (index > 0)
	{
	    row = row.substring(index + key.length());
	    index = row.indexOf("\n");

	    return row.substring(0, index).trim();
	}

	return null;
    }

    //<editor-fold defaultstate="collapsed" desc="REDMINE">
    protected int tyrGetIssueStatusId(RedmineManager redmineManager, String issueStatus)
    {
	Locale trLocale = new Locale("tr-TR");

	try
	{
	    List<IssueStatus> list = redmineManager.getIssueManager().getStatuses();
	    for (IssueStatus status : list)
	    {
		if (status.getName().toLowerCase(trLocale).equals(issueStatus.toLowerCase(trLocale)))
		{
		    return status.getId();
		}
	    }
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Couldn't get issue statuses.(" + ex.getMessage() + ")");
	}

	return 0; // not found
    }

    protected int tryGetProjectId(RedmineManager redmineManager, String projectName)
    {
	Locale trLocale = new Locale("tr-TR");

	try
	{
	    List<Project> projects = redmineManager.getProjectManager().getProjects();
	    for (Project project : projects)
	    {
		if (project.getName().toLowerCase(trLocale).equals(projectName.toLowerCase(trLocale)))
		{
		    return project.getId();
		}
	    }
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Couldn't get projects.(" + ex.getMessage() + ")");
	}

	return 0; // not found
    }

    protected List<Issue> getProjectIssues(RedmineManager redmineManager, int filterProjectId, int filterIssueStatusId, int offset, int limit, String sort)
    {

	HashMap<String, String> params = new HashMap<>();
	params.put("project_id", Integer.toString(filterProjectId));
	params.put("status_id", Integer.toString(filterIssueStatusId));

	// default
	params.put("offset", Integer.toString(offset));
	params.put("limit", Integer.toString(limit));
	params.put("sort", sort);

	try
	{
	    return redmineManager.getIssueManager().getIssues(params).getResults();
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Couldn't get issues.(" + ex.getMessage() + ")");
	}

	return new ArrayList<>();
    }

//</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="PARAMETERS">
    protected final String[] mandatoryParameters;

    protected boolean areMandatoryParametersNotEmpty(Properties props)
    {
	if (mandatoryParameters != null)
	{
	    String value;
	    for (String parameter : mandatoryParameters)
	    {
		value = props.getProperty(parameter);
		if (value == null || value.trim().length() == 0)
		{
		    System.err.println("Ooops! Missing required parameter.(" + parameter + ")");
		    return false;
		}
	    }
	}

	return true;
    }

    protected String getParameterString(Properties props, String parameter, boolean isLowerCased)
    {
	String sValue = props.getProperty(parameter);
	if (sValue != null)
	{
	    sValue = sValue.trim();
	    if (isLowerCased)
	    {
		sValue = sValue.toLowerCase(trLocale);
	    }
	}

	return sValue;
    }

    protected String[] getParameterStringArray(Properties props, String parameter, boolean isLowerCased)
    {
	return getParameterString(props, parameter, isLowerCased).split(",");
    }

    protected HashSet<String> getParameterStringHash(Properties props, String parameter, boolean isLowerCased)
    {
	return new HashSet<>(Arrays.asList(getParameterStringArray(props, parameter, isLowerCased)));
    }

    protected Date getParameterDateTime(Properties props, String parameter)
    {
	String sValue = getParameterString(props, parameter, false);
	if (sValue != null && sValue.length() > 0)
	{
	    try
	    {
		return dateTimeFormat.parse(sValue);
	    }
	    catch (ParseException ex)
	    {
		System.err.println("Ooops! Date time value could not be parsed. (" + sValue + ") (" + ex.getMessage() + ")");
	    }
	}

	return null;
    }

    protected void setParameterDateTime(Properties props, String parameter, Date dValue)
    {
	props.setProperty(parameter, dateTimeFormat.format(dValue));
    }
//</editor-fold>
}
