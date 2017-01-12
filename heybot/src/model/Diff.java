package model;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Watcher;
import com.taskadapter.redmineapi.bean.WatcherFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class Diff
{

    private static final Locale trLocale = new Locale("tr-TR");

    private final Issue issue;

    private final HashMap<Integer, Watcher> watchers = new HashMap<>();

    private final HashMap<String, Integer> statusTable = new HashMap<String, Integer>()
    {
	{
	    put("total", 0);
	    put("new", 0);
	    put("in_progress", 0);
	    put("on_hold", 0);
	    put("closed", 0);
	}
    };

    private static HashSet<String> newStatuses;
    private static HashSet<String> inProgressStatuses;
    private static HashSet<String> onHoldStatuses;
    private static HashSet<String> closedStatuses;

    private static int newStatusId;
    private static int inProgressStatusId;
    private static int onHoldStatusId;
    private static int closedStatusId;

    private static HashSet<String> supportWatchers;

    public static void initialize(RedmineManager redmineManager, HashSet<String> newStatuses, HashSet<String> inProgressStatuses, HashSet<String> onHoldStatuses, HashSet<String> closedStatuses, String newStatus, String inProgressStatus, String onHoldStatus, String closedStatus, HashSet<String> supportWatchers)
    {
	initializeStatus(newStatuses, inProgressStatuses, onHoldStatuses, closedStatuses);

	initializeStatusId(redmineManager, newStatus, inProgressStatus, onHoldStatus, closedStatus);

	Diff.supportWatchers = supportWatchers;
    }

    private static void initializeStatus(HashSet<String> newStatuses1, HashSet<String> inProgressStatuses1, HashSet<String> onHoldStatuses1, HashSet<String> closedStatuses1)
    {
	Diff.newStatuses = newStatuses1;
	Diff.inProgressStatuses = inProgressStatuses1;
	Diff.onHoldStatuses = onHoldStatuses1;
	Diff.closedStatuses = closedStatuses1;
    }

    private static void initializeStatusId(RedmineManager redmineManager, String newStatus, String inProgressStatus, String onHoldStatus, String closedStatus)
    {
	try
	{
	    List<IssueStatus> statuses = redmineManager.getIssueManager().getStatuses();
	    for (IssueStatus status : statuses)
	    {
		String statusName = status.getName().toLowerCase(trLocale);
		if (statusName.equals(newStatus))
		{
		    Diff.newStatusId = status.getId();
		}
		else if (statusName.equals(inProgressStatus))
		{
		    Diff.inProgressStatusId = status.getId();
		}
		else if (statusName.equals(onHoldStatus))
		{
		    Diff.onHoldStatusId = status.getId();
		}
		else if (statusName.equals(closedStatus))
		{
		    Diff.closedStatusId = status.getId();
		}
	    }
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Couldn't get statuses.(" + ex.getMessage() + ")");
	    System.exit(-1);
	}
    }

    public Diff(Issue issue)
    {
	this.issue = issue;

	Collection<Watcher> issueWatchers = issue.getWatchers();
	for (Watcher watcher : issueWatchers)
	{
	    this.watchers.put(watcher.getId(), watcher);
	}
    }

    public void checkStatus(Integer statusId, String statusName)
    {
	statusName = statusName.toLowerCase(trLocale);

	if (isStatusNew(statusName))
	{
	    incrementStatusTable("new");
	    incrementStatusTable("total");
	}
	else if (isStatusInProgress(statusName))
	{
	    incrementStatusTable("in_progress");
	    incrementStatusTable("total");
	}
	else if (isStatusOnHold(statusName))
	{
	    incrementStatusTable("on_hold");
	    incrementStatusTable("total");
	}
	else if (isStatusClosed(statusName))
	{
	    incrementStatusTable("closed");
	    incrementStatusTable("total");
	}
    }

    public void checkWatchers(Issue otherIssue)
    {
	tryAddWatcher(otherIssue.getAuthorId());
	if (otherIssue.getAssigneeId() != null)
	{
	    tryAddWatcher(otherIssue.getAssigneeId());
	}

	Collection<CustomField> customFields = otherIssue.getCustomFields();
	for (CustomField customField : customFields)
	{
	    if (Diff.supportWatchers.contains(customField.getName().toLowerCase(trLocale))
		    && customField.getValue() != null && customField.getValue().length() > 0)
	    {
		tryAddWatcher(Integer.parseInt(customField.getValue()));
	    }
	}
    }

    private void tryAddWatcher(int userId)
    {
	if (!watchers.containsKey(userId))
	{
	    System.out.println("Issue watcher add: " + userId);
	    watchers.put(userId, WatcherFactory.create(userId));

	}
    }

    public void apply(RedmineManager redmineManager)
    {
	applyStatus(redmineManager);
	applyWatchers(redmineManager);
    }

    private void applyStatus(RedmineManager redmineManager)
    {
	int statusId = 0;

	int total = statusTable.get("total");
	if (total > 0)
	{
	    if (total == statusTable.get("new"))
	    {
		statusId = Diff.newStatusId;
	    }
	    else if (total == statusTable.get("closed"))
	    {
		statusId = Diff.closedStatusId;
	    }
	    else if (total == statusTable.get("on_hold"))
	    {
		statusId = Diff.onHoldStatusId;
	    }
	    else if (statusTable.get("in_progress") > 0 || statusTable.get("closed") > 0)
	    {
		statusId = Diff.inProgressStatusId;
	    }
	}

	if (statusId > 0 && statusId != issue.getStatusId())
	{
	    issue.setStatusId(statusId);
	    System.out.println("Issue status update: " + statusId);
	    updateIssue(redmineManager);
	}
    }

    private void incrementStatusTable(String key)
    {
	int count = statusTable.get(key);
	statusTable.put(key, ++count);
    }

    private boolean isStatusClosed(String statusName)
    {
	return Diff.closedStatuses.contains(statusName);
    }

    private boolean isStatusOnHold(String statusName)
    {
	return Diff.onHoldStatuses.contains(statusName);
    }

    private boolean isStatusInProgress(String statusName)
    {
	return Diff.inProgressStatuses.contains(statusName);
    }

    private boolean isStatusNew(String statusName)
    {
	return Diff.newStatuses.contains(statusName);
    }

    private void updateIssue(RedmineManager redmineManager)
    {
	try
	{
	    redmineManager.getIssueManager().update(issue);
	    System.out.println("[✓]");
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Issue update error. (" + ex.getMessage() + ")");
	}
    }

    private void applyWatchers(RedmineManager redmineManager)
    {
	if (watchers.size() > issue.getWatchers().size())
	{
	    for (Watcher watcher : watchers.values())
	    {
		try
		{
		    redmineManager.getIssueManager().addWatcherToIssue(watcher, issue);
		}
		catch (RedmineException ex)
		{
		    System.err.println("Ooops! Watcher couldn't be added to issue #" + issue.getId() + ".(" + ex.getMessage() + ")");
		}
	    }
	}

    }

}
