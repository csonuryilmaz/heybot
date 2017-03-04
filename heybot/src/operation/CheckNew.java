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
	    int filterLastIssueId = getParameterInt(prop, PARAMETER_LAST_ISSUE, 0);
	    String projectName = getParameterString(prop, PARAMETER_PROJECT, true);
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	    String slackWebHookUrl = getParameterString(prop, PARAMETER_WEBHOOK_URL, false);
	    // optional
	    String issueStatus = getParameterString(prop, PARAMETER_ISSUE_STATUS, true);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    Issue[] issues = getIssues(issueStatus, projectName, filterLastIssueId);
	    if (issues.length > 0)
	    {
		for (Issue issue : issues)
		{
		    notifySlack(slackWebHookUrl, issue, redmineUrl, projectName);
		}

		prop.setProperty(PARAMETER_LAST_ISSUE, Integer.toString(issues[issues.length - 1].getId()));
	    }
	    else
	    {
		System.out.println("There is no new issue is detected.");
	    }
	}
    }

    private Issue[] getIssues(String issueStatus, String projectName, int filterLastIssueId)
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

	    return getProjectIssues(redmineManager, filterProjectId, filterIssueStatusId, lastIssue.getCreatedOn(), lastIssue.getId(), "id:asc");
	}
	else
	{
	    return getProjectIssues(redmineManager, filterProjectId, filterIssueStatusId, 0, 1, "id:desc");
	}
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
