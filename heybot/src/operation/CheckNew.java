package operation;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.model.Attachment;
import com.github.seratch.jslack.api.model.Field;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author onuryilmaz
 */
public class CheckNew extends Operation
{

    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_LAST_ISSUE = "LAST_ISSUE";
    private final static String PARAMETER_PROJECT = "PROJECT";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_WEBHOOK_URL = "WEBHOOK_URL";
    // optional
    private final static String PARAMETER_ISSUE_STATUS = "ISSUE_STATUS";
//</editor-fold>
    private RedmineManager redmineManager;

    public CheckNew()
    {
	super(new String[]
	{
	    PARAMETER_LAST_ISSUE, PARAMETER_PROJECT, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_WEBHOOK_URL
	});
    }

    @Override
    public void execute(Properties prop)
    {
	String lastIssueId = getParameterString(prop, PARAMETER_LAST_ISSUE, false);
	String projectName = getParameterString(prop, PARAMETER_PROJECT, true);
	String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	String slackWebHookUrl = getParameterString(prop, PARAMETER_WEBHOOK_URL, false);
	// optional
	String issueStatus = getParameterString(prop, PARAMETER_ISSUE_STATUS, true);

	// default values
	int filterLastIssueId = 0;
	if (lastIssueId != null && lastIssueId.length() > 0)
	{
	    filterLastIssueId = Integer.parseInt(lastIssueId);
	}

	// connect redmine
	redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	List<Issue> issues = getIssues(issueStatus, projectName, filterLastIssueId);
	if (issues.size() > 0)
	{
	    for (int i = issues.size() - 1; i >= 0; i--)
	    {// notify issues ascending
		notifySlack(slackWebHookUrl, issues.get(i), redmineUrl, projectName);
	    }

	    prop.setProperty("LAST_ISSUE", Integer.toString(issues.get(0).getId()));
	}
	else
	{
	    System.out.println("There is no new issue is detected.");
	}
    }

    private List<Issue> getIssues(String issueStatus, String projectName, int filterLastIssueId)
    {
	int filterIssueStatusId = 0;
	if (issueStatus != null && issueStatus.length() > 0)
	{
	    filterIssueStatusId = tyrGetIssueStatusId(redmineManager, issueStatus);
	}
	int filterProjectId = tryGetProjectId(redmineManager, projectName);

	List<Issue> issues = getProjectIssues(redmineManager, filterProjectId, filterIssueStatusId, 0, 10, "id:desc");

	if (filterLastIssueId > 0)
	{// filter
	    return filterIssues(issues, filterLastIssueId);
	}

	return issues;
    }

    private List<Issue> filterIssues(List<Issue> issues, int filterIssueStatusId)
    {
	List<Issue> filteredIssues = new ArrayList<>();
	for (Issue issue : issues)
	{
	    if (issue.getId() > filterIssueStatusId)
	    {
		filteredIssues.add(issue);
	    }
	}

	return filteredIssues;
    }

    private void notifySlack(String slackWebHookUrl, Issue issue, String redmineUrl, String projectName)
    {
	Attachment attachment = Attachment.builder().text(issue.getSubject())
		.pretext("Heads up! ~" + projectName + "~" + " has a new issue.")
		.color("#36a64f")
		.fallback("New issue detected by check-new operation of heybot.")
		.title("#" + issue.getId())
		.titleLink(redmineUrl + "/issues/" + issue.getId())
		.footer(issue.getAuthorName())
		.fields(new ArrayList<Field>())
		.build();

	ArrayList<Attachment> attachments = new ArrayList<Attachment>()
	{
	};
	attachments.add(attachment);

	Payload payload = Payload.builder()
		.attachments(attachments)
		.build();

	WebhookResponse response;
	try
	{
	    System.out.print("#" + issue.getId() + ": ");
	    response = Slack.getInstance().send(slackWebHookUrl, payload);

	    System.out.println(response.toString());
	}
	catch (IOException ex)
	{
	    System.err.println("Ooops! Slack notification problem! (" + ex.getMessage() + ")");
	}

    }

}
