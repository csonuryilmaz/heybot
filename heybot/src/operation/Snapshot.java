package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.Tracker;
import com.taskadapter.redmineapi.bean.User;
import java.util.HashMap;
import java.util.Map;
import model.MapUtil;
import org.apache.commons.lang3.ArrayUtils;
import static org.apache.http.util.TextUtils.isEmpty;
import utilities.Properties;

/**
 *
 * @author onur
 */
public class Snapshot extends Operation
{
    //<editor-fold defaultstate="collapsed" desc="parameters">

    // mandatory
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_PROJECT = "PROJECT";
    private final static String PARAMETER_STATUS = "STATUS";

    // optional
    private final static String PARAMETER_USER_CUSTOM_FIELD = "USER_CUSTOM_FIELD";
    private final static String PARAMETER_TRACKER = "TRACKER";

    //</editor-fold>
    private RedmineManager redmineManager;

    public Snapshot()
    {

	super(new String[]
	{
	    PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_PROJECT, PARAMETER_STATUS
	}
	);
    }

    @Override
    public void execute(Properties prop) throws Exception
    {
	if (areMandatoryParametersNotEmpty(prop))
	{

	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    Issue[] issues = getIssues(prop);

	    System.out.println("Total " + issues.length + " issue(s).");

	    System.out.println();
	    groupIssuesByAssignee(issues);
	    System.out.println();
	    groupIssuesByUserCustomFields(issues, getParameterStringArray(prop, PARAMETER_USER_CUSTOM_FIELD, false));
	    System.out.println();
	    listIssues(issues);
	}
    }

    private Issue[] getIssues(Properties prop)
    {
	Project[] projects = getProjects(redmineManager, getParameterStringArray(prop, PARAMETER_PROJECT, true));
	IssueStatus[] statuses = getStatuses(redmineManager, getParameterStringArray(prop, PARAMETER_STATUS, true));
	Tracker[] trackers = getTrackers(redmineManager, getParameterStringArray(prop, PARAMETER_TRACKER, true));

	Issue[] issues = new Issue[0];
	for (Project project : projects)
	{
	    System.out.println("=== Searching issues in project: " + project.getName());
	    issues = ArrayUtils.addAll(issues, getProjectIssues(redmineManager, project, statuses, trackers, true, "due_date:asc"));
	}

	return issues;
    }

    private void groupIssuesByAssignee(Issue[] issues)
    {
	System.out.println("Grouped by Assignee:");
	HashMap<String, Integer> statistics = new HashMap<>();

	Integer count;
	for (Issue issue : issues)
	{
	    if ((count = statistics.get(issue.getAssigneeName())) != null)
	    {
		statistics.put(issue.getAssigneeName(), count + 1);
	    }
	    else
	    {
		statistics.put(issue.getAssigneeName(), 1);
	    }
	}

	statistics = (HashMap<String, Integer>) MapUtil.sortByValueDesc(statistics);
	for (Map.Entry<String, Integer> statistic : statistics.entrySet())
	{
	    System.out.println(statistic.getKey() + " " + statistic.getValue());
	}
    }

    private void groupIssuesByUserCustomFields(Issue[] issues, String[] otherUserCustomFields)
    {
	for (String otherUserCustomField : otherUserCustomFields)
	{
	    groupIssuesByUserCustomField(issues, otherUserCustomField);
	}
    }

    private void groupIssuesByUserCustomField(Issue[] issues, String customField)
    {
	System.out.println("Grouped by " + customField + ":");
	HashMap<String, Integer> statistics = new HashMap<>();

	Integer count;
	for (Issue issue : issues)
	{
	    String customFieldValue = getIssueCustomFieldValue(issue, customField);

	    if ((count = statistics.get(customFieldValue)) != null)
	    {
		statistics.put(customFieldValue, count + 1);
	    }
	    else
	    {
		statistics.put(customFieldValue, 1);
	    }
	}

	statistics = (HashMap<String, Integer>) MapUtil.sortByValueDesc(statistics);
	for (Map.Entry<String, Integer> statistic : statistics.entrySet())
	{
	    System.out.println((statistic.getKey().equals("none") ? "none" : getUserFullName(statistic.getKey())) + " " + statistic.getValue());
	}
    }

    private String getIssueCustomFieldValue(Issue issue, String name)
    {
	CustomField customField = issue.getCustomFieldByName(name);

	if (customField != null && !isEmpty(customField.getValue()))
	{
	    return customField.getValue();
	}

	return "none";
    }

    private String getUserFullName(String userId)
    {
	try
	{
	    User user = redmineManager.getUserManager().getUserById(Integer.parseInt(userId));
	    if (user != null)
	    {
		return user.getFullName();
	    }
	}
	catch (RedmineException ex)
	{
	    // intentionally left blank
	}

	return "unknown";
    }

    private void listIssues(Issue[] issues)
    {
	System.out.println("List of Issues:");
	for (Issue issue : issues)
	{
	    System.out.print(format("#" + issue.getId(), 6, true));
	    System.out.print(format(dateTimeFormatOnlyDate.format(issue.getDueDate()), 12, true));
	    System.out.print(format("[" + issue.getTracker().getName() + "]", 15, true));
	    System.out.print(format((issue.getTargetVersion() != null ? issue.getTargetVersion().getName() : ""), 15, true));
	    System.out.print(format("(" + issue.getPriorityText() + ")", 10, true));
	    System.out.print(format(issue.getStatusName(), 15, true));
	    System.out.print(format(issue.getAssigneeName(), 10, true));
	    System.out.print(format(issue.getSubject(), 80, true));
	    System.out.println();
	}
    }

    private String format(String value, int length, boolean isRightPadding)
    {
	if (value.length() > length - 1)
	{
	    value = value.substring(0, length - 1) + ".";
	}

	String format = "%1$";
	if (isRightPadding)
	{
	    format += "-";
	}
	format += length + "s";

	return " " + String.format(format, value);
    }

}
