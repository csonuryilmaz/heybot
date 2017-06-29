package operation;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.Tracker;
import java.util.HashMap;
import java.util.Map;
import model.MapUtil;
import org.apache.commons.lang3.ArrayUtils;
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
    private final static String PARAMETER_OTHER_USER_CUSTOM_FIELD = "OTHER_USER_CUSTOM_FIELD";
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
	    groupIssuesByAssignee(issues);

	    // todo
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
	System.out.println("Grouped by assignee:");
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

}
