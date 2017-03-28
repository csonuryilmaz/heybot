package model;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueStatus;
import com.taskadapter.redmineapi.bean.Journal;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.Watcher;
import com.taskadapter.redmineapi.bean.WatcherFactory;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
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

    private Date supportStartDate = null;
    private Date supportDueDate = null;

    private static HashSet<String> newStatuses;
    private static HashSet<String> inProgressStatuses;
    private static HashSet<String> onHoldStatuses;
    private static HashSet<String> closedStatuses;

    private static int newStatusId;
    private static int inProgressStatusId;
    private static int onHoldStatusId;
    private static int closedStatusId;

    private static HashSet<String> supportWatchers;

    private final static HashMap<Integer, IssueStatus> statuses = new HashMap<>();

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
		Diff.statuses.put(status.getId(), status);
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
	if (issue.getStatusId() != Diff.newStatusId)
	{
	    applyStartDate(redmineManager);
	}
	if (issue.getStatusId() == Diff.closedStatusId)
	{
	    applyDueDate(redmineManager);
	}
	else if (issue.getDueDate() != null)
	{
	    resetDueDate(redmineManager);
	}
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

    public void checkStartDate(Issue targetIssue)
    {
	Date internalStartDate = getStartDateFromJournals(targetIssue);
	if (internalStartDate != null)
	{
	    internalStartDate = trimTime(internalStartDate);
	    if (supportStartDate == null || internalStartDate.before(supportStartDate))
	    {
		supportStartDate = internalStartDate;
	    }
	}
    }

    public void checkDueDate(Issue targetIssue)
    {
	Date internalDueDate = getDueDateFromJournals(targetIssue);
	if (internalDueDate != null)
	{
	    internalDueDate = trimTime(internalDueDate);
	    if (supportDueDate == null || internalDueDate.after(supportDueDate))
	    {
		supportDueDate = internalDueDate;
	    }
	}
    }

    private boolean isJournalDetailNewToInProgress(JournalDetail journalDetail)
    {
	if (journalDetail.getName().equals("status_id"))
	{
	    String oldStatus = getStatusById(journalDetail.getOldValue());
	    String newStatus = getStatusById(journalDetail.getNewValue());

	    return isStatusNew(oldStatus) && (isStatusInProgress(newStatus) || isStatusOnHold(newStatus) || isStatusClosed(newStatus));
	}
	return false;
    }

    private String getStatusById(String statusId)
    {
	return Diff.statuses.get(Integer.parseInt(statusId)).getName().toLowerCase(trLocale);
    }

    private Date getStartDateFromJournals(Issue targetIssue)
    {
	for (Journal journal : targetIssue.getJournals())
	{
	    for (JournalDetail journalDetail : journal.getDetails())
	    {
		if (isJournalDetailNewToInProgress(journalDetail))
		{
		    return journal.getCreatedOn();
		}
	    }
	}

	return null;
    }

    private Date getDueDateFromJournals(Issue targetIssue)
    {
	for (Journal journal : targetIssue.getJournals())
	{
	    for (JournalDetail journalDetail : journal.getDetails())
	    {
		if (isJournalDetailInProgressToClosed(journalDetail))
		{
		    return journal.getCreatedOn();
		}
	    }
	}

	return null;
    }

    private boolean isJournalDetailInProgressToClosed(JournalDetail journalDetail)
    {
	if (journalDetail.getName().equals("status_id"))
	{
	    String oldStatus = getStatusById(journalDetail.getOldValue());
	    String newStatus = getStatusById(journalDetail.getNewValue());

	    return isStatusClosed(newStatus) && (isStatusInProgress(oldStatus) || isStatusOnHold(oldStatus) || isStatusNew(oldStatus));
	}
	return false;
    }

    private void applyStartDate(RedmineManager redmineManager)
    {
	if (supportStartDate != null && (issue.getStartDate() == null || supportStartDate.before(issue.getStartDate())))
	{
	    issue.setStartDate(supportStartDate);
	    System.out.println("Issue start date update: " + supportStartDate);
	    updateIssue(redmineManager);
	}
    }

    private void applyDueDate(RedmineManager redmineManager)
    {
	if (supportDueDate != null && (issue.getDueDate() == null || supportDueDate.after(issue.getDueDate())))
	{
	    issue.setDueDate(supportDueDate);
	    System.out.println("Issue due date update: " + supportDueDate);
	    updateIssue(redmineManager);
	}
    }

    private void resetDueDate(RedmineManager redmineManager)
    {
	issue.setDueDate(null);
	System.out.println("Issue due date is cleared.");
	updateIssue(redmineManager);
    }

    private Date trimTime(Date date)
    {
	Calendar cal = Calendar.getInstance();
	cal.setTime(date);
	cal.set(Calendar.HOUR_OF_DAY, 0);
	cal.set(Calendar.MINUTE, 0);
	cal.set(Calendar.SECOND, 0);
	cal.set(Calendar.MILLISECOND, 0);
	return cal.getTime();
    }

}
