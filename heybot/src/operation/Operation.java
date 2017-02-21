package operation;

import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.Params;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueRelation;
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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Base class for all operations in operation package.
 *
 * @author onuryilmaz
 */
public abstract class Operation
{

    protected final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected final SimpleDateFormat dateTimeFormatOnlyDate = new SimpleDateFormat("yyyy-MM-dd");
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

    protected Issue[] getProjectIssues(RedmineManager redmineManager, Project[] filterProjects, Date filterUpdatedOnStart, Date filterUpdatedOnEnd, int offset, int limit, String sort)
    {

	com.taskadapter.redmineapi.Params params = new Params().add("set_filter", "1");

	params.add("f[]", "project_id");
	params.add("op[project_id]", "=");
	for (Project project : filterProjects)
	{
	    params.add("v[project_id][]", Integer.toString(project.getId()));
	}

	params.add("f[]", "updated_on");
	params.add("op[updated_on]", "><");
	params.add("v[updated_on][]", dateTimeFormatOnlyDate.format(filterUpdatedOnStart));
	params.add("v[updated_on][]", dateTimeFormatOnlyDate.format(filterUpdatedOnEnd));

	// default
	params.add("offset", Integer.toString(offset));
	params.add("limit", Integer.toString(limit));
	params.add("sort", sort);

	try
	{
	    List<Issue> issues = redmineManager.getIssueManager().getIssues(params).getResults();

	    return issues.toArray(new Issue[issues.size()]);
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Couldn't get issues.(" + ex.getMessage() + ")");
	}

	return new Issue[0];
    }

    protected Project[] getProjects(RedmineManager redmineManager, String[] projectNames)
    {
	List<Project> projects = new ArrayList<>();
	for (String projectName : projectNames)
	{
	    int projectId = tryGetProjectId(redmineManager, projectName);
	    if (projectId > 0)
	    {
		try
		{
		    projects.add(redmineManager.getProjectManager().getProjectById(projectId));
		}
		catch (RedmineException ex)
		{
		    System.err.println("Ooops! Project not found by id " + projectId + " (" + projectName + ")." + ex.getMessage());
		}
	    }
	}

	return projects.toArray(new Project[projects.size()]);
    }

    protected Collection<IssueRelation> getIssueRelations(RedmineManager redmineManager, int issueId)
    {
	Issue issue = getIssue(redmineManager, issueId, Include.relations);
	if (issue != null)
	{
	    return issue.getRelations();
	}

	return new ArrayList<>();
    }

    protected Issue getIssue(RedmineManager redmineManager, int issueId, Include... include)
    {
	try
	{
	    return redmineManager.getIssueManager().getIssueById(issueId, include);
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Relations could not be get for issue #" + issueId + ". (" + ex.getMessage() + ")");
	}

	return null;
    }

    protected boolean isIssueInProject(Issue issue, Project[] projects)
    {
	for (Project project : projects)
	{
	    if ((int) issue.getProjectId() == project.getId())
	    {
		return true;
	    }
	}

	return false;
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

    protected boolean getParameterBoolean(Properties props, String parameter)
    {
	String sValue = getParameterString(props, parameter, true);
	if (sValue != null && sValue.length() > 0)
	{
	    return Boolean.parseBoolean(sValue);
	}

	return false;
    }

    protected void setParameterDateTime(Properties props, String parameter, Date dValue)
    {
	props.setProperty(parameter, dateTimeFormat.format(dValue));
    }
//</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="DEBUG">
    protected void debugIssuesToString(Issue[] issues)
    {
	System.out.println("total issues: " + issues.length);
	for (int i = 0; i < issues.length; i++)
	{
	    System.out.println("issue[" + i + "]:\t #" + issues[i].getId() + " - " + issues[i].getSubject());
	}
    }

//</editor-fold>
}
