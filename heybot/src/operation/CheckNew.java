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
import java.util.Date;
import java.util.Properties;

/**
 *
 * @author onuryilmaz
 */
public class CheckNew extends Operation
{

    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_PROJECT = "PROJECT";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_WEBHOOK_URL = "WEBHOOK_URL";
    // optional
    private final static String PARAMETER_ISSUE_STATUS = "ISSUE_STATUS";
    // internal
    private final static String PARAMETER_LAST_ISSUE = "LAST_ISSUE";
    private final static String PARAMETER_LAST_CREATED_ON = "LAST_CREATED_ON";
//</editor-fold>
    private RedmineManager redmineManager;

    public CheckNew()
    {
	super(new String[]
	{
	    PARAMETER_PROJECT, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_WEBHOOK_URL
	});
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String projectName = getParameterString(prop, PARAMETER_PROJECT, true);
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	    String slackWebHookUrl = getParameterString(prop, PARAMETER_WEBHOOK_URL, false);
	    // optional
	    String issueStatus = getParameterString(prop, PARAMETER_ISSUE_STATUS, true);
	    // internal
	    int filterLastIssueId = getParameterInt(prop, PARAMETER_LAST_ISSUE, 0);
	    Date filterCreatedOn = getParameterDateTime(prop, PARAMETER_LAST_CREATED_ON);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    Issue[] issues = getIssues(issueStatus, projectName, filterLastIssueId, filterCreatedOn);
	    if (issues.length > 0)
	    {
		for (Issue issue : issues)
		{
		    notifySlack(slackWebHookUrl, issue, redmineUrl, projectName);
		}

		Issue lastIssue = issues[issues.length - 1];
		prop.setProperty(PARAMETER_LAST_ISSUE, Integer.toString(lastIssue.getId()));
		prop.setProperty(PARAMETER_LAST_CREATED_ON, dateTimeFormat.format(lastIssue.getCreatedOn()));
	    }
	    else
	    {
		System.out.println("There is no new issue is detected.");
	    }
	}
    }

    private Issue[] getIssues(String issueStatus, String projectName, int filterLastIssueId, Date filterCreatedOn)
    {
	int filterIssueStatusId = 0;
	if (issueStatus != null && issueStatus.length() > 0)
	{
	    filterIssueStatusId = tryGetIssueStatusId(redmineManager, issueStatus);
	}
	int filterProjectId = tryGetProjectId(redmineManager, projectName);

	if (filterLastIssueId > 0)
	{
	    Issue lastIssue = getIssue(redmineManager, filterLastIssueId);
	    if (lastIssue == null)
	    {
		System.out.println("Last issue #" + filterLastIssueId + " is not found!");
	    }
	    else
	    {
		System.out.println("Filter issue newer than: #" + lastIssue.getId());
		filterCreatedOn = lastIssue.getCreatedOn();
	    }
	    System.out.println("Filter issue created on: " + dateTimeFormat.format(filterCreatedOn));

	    return getProjectIssues(redmineManager, filterProjectId, filterIssueStatusId, filterCreatedOn, filterLastIssueId, "id:asc");
	}
	else
	{
	    return getProjectIssues(redmineManager, filterProjectId, filterIssueStatusId, 0, 1, "id:desc");
	}
    }

    private void notifySlack(String slackWebHookUrl, Issue issue, String redmineUrl, String projectName)
    {
	Field priority = Field.builder()
		.title(issue.getPriorityText())
		.value("")
		.valueShortEnough(false).build();

	Attachment attachment = Attachment.builder().text(issue.getSubject())
		.pretext("Heads up! We have a new issue.")
		.authorName(projectName)
		.color("#36a64f")
		.fallback("Heads up! We have a new issue.")
		.title("[" + issue.getTracker().getName() + "] #" + issue.getId())
		.titleLink(redmineUrl + "/issues/" + issue.getId())
		.footer(issue.getAuthorName() + " | " + dateTimeFormat.format(issue.getCreatedOn()))
		.fields(new ArrayList<Field>())
		.build();

	attachment.getFields().add(priority);

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
