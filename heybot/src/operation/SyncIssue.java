package operation;

import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueRelation;
import com.taskadapter.redmineapi.bean.Project;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Collection;
import java.util.HashMap;
import model.Diff;

/**
 *
 * @author onur
 */
public class SyncIssue extends Operation
{
    //<editor-fold defaultstate="collapsed" desc="parameters">

    // mandatory
    private final static String PARAMETER_SUPPORT_PROJECT = "SUPPORT_PROJECT";
    private final static String PARAMETER_SUPPORT_NEW_STATUS = "SUPPORT_NEW_STATUS";
    private final static String PARAMETER_SUPPORT_IN_PROGRESS_STATUS = "SUPPORT_IN_PROGRESS_STATUS";
    private final static String PARAMETER_SUPPORT_ON_HOLD_STATUS = "SUPPORT_ON_HOLD_STATUS";
    private final static String PARAMETER_SUPPORT_CLOSED_STATUS = "SUPPORT_CLOSED_STATUS";
    private final static String PARAMETER_INTERNAL_PROJECT = "INTERNAL_PROJECT";
    private final static String PARAMETER_INTERNAL_NEW_STATUS = "INTERNAL_NEW_STATUS";
    private final static String PARAMETER_INTERNAL_IN_PROGRESS_STATUS = "INTERNAL_IN_PROGRESS_STATUS";
    private final static String PARAMETER_INTERNAL_ON_HOLD_STATUS = "INTERNAL_ON_HOLD_STATUS";
    private final static String PARAMETER_INTERNAL_CLOSED_STATUS = "INTERNAL_CLOSED_STATUS";
    private final static String PARAMETER_SUPPORT_WATCHER = "SUPPORT_WATCHER";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // optional
    private final static String PARAMETER_SUPPORT_SYNC_START_DATE = "SUPPORT_SYNC_START_DATE";
    private final static String PARAMETER_SUPPORT_SYNC_DUE_DATE = "SUPPORT_SYNC_DUE_DATE";
    // internal
    private final static String PARAMETER_LAST_CHECK_TIME = "LAST_CHECK_TIME";

    //</editor-fold>
    private RedmineManager redmineManager;

    public SyncIssue()
    {
	super(new String[]
	{
	    PARAMETER_SUPPORT_PROJECT, PARAMETER_SUPPORT_NEW_STATUS, PARAMETER_SUPPORT_IN_PROGRESS_STATUS, PARAMETER_SUPPORT_ON_HOLD_STATUS, PARAMETER_SUPPORT_CLOSED_STATUS, PARAMETER_INTERNAL_NEW_STATUS, PARAMETER_INTERNAL_IN_PROGRESS_STATUS, PARAMETER_INTERNAL_ON_HOLD_STATUS, PARAMETER_INTERNAL_CLOSED_STATUS, PARAMETER_SUPPORT_WATCHER, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_INTERNAL_PROJECT
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String supportNewStatus = getParameterString(prop, PARAMETER_SUPPORT_NEW_STATUS, true);
	    String supportInProgressStatus = getParameterString(prop, PARAMETER_SUPPORT_IN_PROGRESS_STATUS, true);
	    String supportOnHoldSatus = getParameterString(prop, PARAMETER_SUPPORT_ON_HOLD_STATUS, true);
	    String supportClosedStatus = getParameterString(prop, PARAMETER_SUPPORT_CLOSED_STATUS, true);

	    HashSet<String> internalNewStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_NEW_STATUS, true);
	    HashSet<String> internalInProgressStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_IN_PROGRESS_STATUS, true);
	    HashSet<String> internalOnHoldStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_ON_HOLD_STATUS, true);
	    HashSet<String> internalClosedStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_CLOSED_STATUS, true);

	    HashSet<String> supportWatchers = getParameterStringHash(prop, PARAMETER_SUPPORT_WATCHER, true);

	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

	    Date lastCheckTime = getParameterDateTime(prop, PARAMETER_LAST_CHECK_TIME);
	    if (lastCheckTime == null)
	    {
		lastCheckTime = new Date();
	    }

	    // connect redmine
	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    Diff.initialize(redmineManager, internalNewStatuses, internalInProgressStatuses, internalOnHoldStatuses, internalClosedStatuses, supportNewStatus, supportInProgressStatus, supportOnHoldSatus, supportClosedStatus, supportWatchers);

	    Project[] internalProjects = getProjects(redmineManager, getParameterStringArray(prop, PARAMETER_INTERNAL_PROJECT, true));
	    Project[] supportProjects = getProjects(redmineManager, getParameterStringArray(prop, PARAMETER_SUPPORT_PROJECT, true));

	    System.out.println("Last check time=" + dateTimeFormat.format(lastCheckTime));

	    System.out.println("Getting last updated internal issues ...");
	    Issue[] internalIssues = getLastUpdatedInternalIssues(lastCheckTime, internalProjects);
	    //debugIssuesToString(internalIssues);
	    System.out.println("Total=" + internalIssues.length);

	    System.out.println("Getting their related support issues ...");
	    Issue[] supportIssues = getRelatedSupportIssues(internalIssues, supportProjects);
	    //debugIssuesToString(supportIssues);
	    System.out.println("Total=" + supportIssues.length);

	    boolean syncStartDateEnabled = getParameterBoolean(prop, PARAMETER_SUPPORT_SYNC_START_DATE);
	    boolean syncDueDateEnabled = getParameterBoolean(prop, PARAMETER_SUPPORT_SYNC_DUE_DATE);

	    checkSupportIssuesAgainstRelatedInternalIssues(supportIssues, internalProjects, syncStartDateEnabled, syncDueDateEnabled);

	    setParameterDateTime(prop, PARAMETER_LAST_CHECK_TIME, new Date());
	}
    }

    private Issue[] getLastUpdatedInternalIssues(Date lastCheckTime, Project[] internalProjects)
    {
	return getProjectIssues(redmineManager, internalProjects, lastCheckTime, new Date(), 0, Integer.MAX_VALUE, "id:desc");
    }

    private Issue[] getRelatedSupportIssues(Issue[] internalIssues, Project[] supportProjects)
    {
	HashMap<Integer, Issue> supportIssues = new HashMap<>();

	for (Issue internalIssue : internalIssues)
	{
	    Collection<IssueRelation> relations = getIssueRelations(redmineManager, internalIssue.getId());
	    for (IssueRelation relation : relations)
	    {
		if (relation.getType().equals("relates") && !supportIssues.containsKey(relation.getIssueToId()))
		{
		    Issue relatedIssue = getIssue(redmineManager, relation.getIssueToId(), Include.relations, Include.watchers);
		    if (relatedIssue != null && isIssueInProject(relatedIssue, supportProjects))
		    {
			supportIssues.put(relatedIssue.getId(), relatedIssue);
		    }
		}
	    }
	}

	return supportIssues.values().toArray(new Issue[supportIssues.values().size()]);
    }

    private void checkSupportIssuesAgainstRelatedInternalIssues(Issue[] supportIssues, Project[] internalProjects, boolean syncStartDateEnabled, boolean syncDueDateEnabled)
    {
	for (Issue supportIssue : supportIssues)
	{
	    Collection<IssueRelation> relations = supportIssue.getRelations();
	    Diff diff = new Diff(supportIssue);
	    for (IssueRelation relation : relations)
	    {
		checkSupportIssueRelation(supportIssue, relation, internalProjects, diff, syncStartDateEnabled, syncDueDateEnabled);
	    }
	    diff.apply(redmineManager);
	}
    }

    private void checkSupportIssueRelation(Issue sourceIssue, IssueRelation relation, Project[] internalProjects, Diff diff, boolean syncStartDateEnabled, boolean syncDueDateEnabled)
    {
	if (relation.getType().equals("relates"))
	{
	    Issue targetIssue;
	    if (relation.getIssueToId() != (int) sourceIssue.getId())
	    {
		targetIssue = getIssue(redmineManager, relation.getIssueToId(), Include.watchers, Include.journals);
	    }
	    else
	    {
		targetIssue = getIssue(redmineManager, relation.getIssueId(), Include.watchers, Include.journals);
	    }

	    if (targetIssue != null && isIssueInProject(targetIssue, internalProjects))
	    {
		System.out.println("Checking support #" + sourceIssue.getId() + " against internal #" + targetIssue.getId());
		diff.checkStatus(targetIssue.getStatusId(), targetIssue.getStatusName());
		diff.checkWatchers(targetIssue);
		if (syncStartDateEnabled)
		{
		    diff.checkStartDate(targetIssue);
		}
		if (syncDueDateEnabled)
		{
		    diff.checkDueDate(targetIssue);
		}
	    }
	}
    }

}
