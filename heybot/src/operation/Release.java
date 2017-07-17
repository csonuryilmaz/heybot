package operation;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.model.Attachment;
import com.github.seratch.jslack.api.model.Field;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import utilities.Properties;
import static org.apache.http.util.TextUtils.isEmpty;

/**
 *
 * @author onur
 */
public class Release extends Operation
{
    //<editor-fold defaultstate="collapsed" desc="parameters">

    // mandatory
    private final static String PARAMETER_VERSION_ID = "VERSION_ID";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_ISSUE_DEPLOYED_STATUS = "ISSUE_DEPLOYED_STATUS";

    // optional
    private final static String PARAMETER_DEPLOY_CMD = "DEPLOY_CMD";
    private final static String PARAMETER_SMTP_USERNAME = "SMTP_USERNAME";
    private final static String PARAMETER_SMTP_PASSWORD = "SMTP_PASSWORD";
    private final static String PARAMETER_SMTP_HOST = "SMTP_HOST";
    private final static String PARAMETER_SMTP_PORT = "SMTP_PORT";
    private final static String PARAMETER_SMTP_TLS_ENABLED = "SMTP_TLS_ENABLED";
    private final static String PARAMETER_NOTIFY_EMAIL = "NOTIFY_EMAIL";
    private final static String PARAMETER_NOTIFY_SLACK = "NOTIFY_SLACK";
    private final static String PARAMETER_DESCRIPTION = "DESCRIPTION";
    private final static String PARAMETER_DB_MODIFICATIONS = "DB_MODIFICATIONS";
    //</editor-fold>
    private RedmineManager redmineManager;
    private Properties prop;

    public Release()
    {
	super(new String[]
	{
	    PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_VERSION_ID, PARAMETER_ISSUE_DEPLOYED_STATUS
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
	    int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);
	    this.prop = prop;

	    System.out.println("* Getting version from redmine: " + versionId);
	    Version version = getVersion(redmineManager, versionId);
	    if (version != null)
	    {
		release(version, redmineUrl);
	    }
	}
    }

    private void release(Version version, String redmineUrl) throws Exception
    {
	System.out.println("  [" + version.getName() + "]");
	Issue[] issues = getVersionIssues(redmineManager, version);

	if (!isVersionDeployed(version))
	{
	    deployVersion(version, issues);// inside managed exception on any error
	    notifySlack(version, issues, redmineUrl);
	}
	else
	{
	    System.out.println("- [info] Version is already deployed!");
	}
    }

    private boolean isVersionDeployed(Version version)
    {
	return version.getStatus().equals("closed");
    }

    private void deployVersion(Version version, Issue[] issues) throws Exception
    {
	doDeployment(version);
	updateAsDeployed(issues);
	updateAsDeployed(version);
    }

    private void doDeployment(Version version) throws Exception
    {
	System.out.println("* Executing deployment commands ... ");
	String[] commands = getParameterStringArray(prop, PARAMETER_DEPLOY_CMD, false);
	if (commands.length > 0)
	{
	    executeCommands(version, commands);
	}
	System.out.println("  Total " + commands.length + " command(s) has been executed.");
    }

    private void executeCommands(Version version, String[] commands) throws Exception
    {
	String versionTag = getVersionTag(version.getName());
	for (String command : commands)
	{
	    executeCommand(versionTag, command);
	}
    }

    private void executeCommand(String versionTag, String command) throws Exception
    {
	if (!isEmpty(versionTag))
	{
	    command = command.replace("{$VERSION_TAG}", versionTag);
	}
	System.out.println(command);
	System.out.println(StringUtils.leftPad("", 80, '-'));

	String[] output = execute(command);
	System.out.println(output[0]);

	if (output[1].length() > 0)
	{
	    throw new Exception(output[1]);
	}

	String[] lines = output[0].split(System.getProperty("line.separator"));
	if (lines.length == 0 || !lines[lines.length - 1].contains("SUCCESS"))
	{
	    throw new Exception("Command seems to be failed. There is no SUCCESS found at last line!");
	}
    }

    private void updateAsDeployed(Issue[] issues) throws Exception
    {
	System.out.println("* Updating related issues ... ");
	int statusId = getIssueDeployedStatusId();
	for (Issue issue : issues)
	{
	    updateAsDeployed(issue, statusId);
	    System.out.println("[✓] " + "#" + issue.getId() + " - " + issue.getSubject());
	}
	System.out.println("  Total " + issues.length + " issue(s) are updated as deployed.");
    }

    private void updateAsDeployed(Version version) throws RedmineException
    {
	System.out.println("* Updating related version ... ");
	setDateDeployedOn(version);
	setDescription(version);
	System.out.println("  Version is updated as deployed.");
    }

    private void notifySlack(Version version, Issue[] issues, String redmineUrl)
    {
	System.out.println("* Sending slack notification ... ");
	String slackWebHookUrl = getParameterString(prop, PARAMETER_NOTIFY_SLACK, false);

	StringBuilder summary = new StringBuilder(getParameterString(prop, PARAMETER_DESCRIPTION, false).trim());
	summary.append("\n");
	String dbModifications = getParameterString(prop, PARAMETER_DB_MODIFICATIONS, false);
	if (!isEmpty(dbModifications))
	{
	    summary.append("Has database schema <");
	    summary.append(dbModifications);
	    summary.append("|modifications.>");
	}

	Attachment attachment = Attachment.builder().text(summary.toString())
		.pretext("Cheers! <" + redmineUrl + "/versions/" + version.getId() + "|" + version.getName() + "> is released.")
		.authorName(version.getProjectName())
		.color("#FF8000")
		.fallback("Cheers! " + version.getName() + " is released.")
		.title(issues.length + " issues(s) fixed.")
		.footer("onur.yilmaz@kitapyurdu.com" + " | " + dateTimeFormat.format(new Date()))
		.fields(new ArrayList<Field>())
		.build();

	// empty line
	attachment.getFields().add(Field.builder()
		.title("")
		.valueShortEnough(false).build());

	for (Issue issue : issues)
	{
	    attachment.getFields().add(Field.builder()
		    .title("#" + issue.getId() + " - " + issue.getTracker().getName() + " (" + issue.getPriorityText() + ")")
		    .value("<" + redmineUrl + "/issues/" + issue.getId() + "|:link:> " + issue.getSubject())
		    .valueShortEnough(false).build());
	}

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
	    response = Slack.getInstance().send(slackWebHookUrl, payload);
	    System.out.println(response.toString());
	}
	catch (IOException ex)
	{
	    System.err.println("Ooops! Slack notification problem! (" + ex.getMessage() + ")");
	}
    }

    private int getIssueDeployedStatusId() throws Exception
    {
	String issueDeployedStatus = getParameterString(prop, PARAMETER_ISSUE_DEPLOYED_STATUS, true);
	int issueDeployedStatusId = tryGetIssueStatusId(redmineManager, issueDeployedStatus);
	if (issueDeployedStatusId == 0)
	{
	    throw new Exception("ISSUE_DEPLOYED_STATUS is unrecognized! Check redmine configuration or hb file.");
	}

	return issueDeployedStatusId;
    }

    private void updateAsDeployed(Issue issue, int statusId) throws Exception
    {
	if (issue.getStatusId() != statusId)
	{
	    issue.setStatusId(statusId);
	    redmineManager.getIssueManager().update(issue);
	    if (!isIssueUpdatedAsDeployed(issue.getId(), statusId))
	    {
		throw new Exception("Could not update issue status! Please check your redmine workflow or configuration!");
	    }
	}
    }

    private boolean isIssueUpdatedAsDeployed(int issueId, int statusId) throws RedmineException
    {
	Issue issue = redmineManager.getIssueManager().getIssueById(issueId);
	return issue.getStatusId() == statusId;
    }

    private void setDateDeployedOn(Version version) throws RedmineException
    {
	CustomField field = tryGetCustomField(version, "deployed on");
	if (field != null)
	{
	    field.setValue(dateTimeFormatOnlyDate.format(new Date()));
	    redmineManager.getProjectManager().update(version);
	}
    }

    private void setDescription(Version version) throws RedmineException
    {
	StringBuilder sb = new StringBuilder();

	String summary = getParameterString(prop, PARAMETER_DESCRIPTION, false).trim();
	if (summary.length() > 0)
	{
	    sb.append("[Summary: ");
	    sb.append(summary);
	    sb.append("]");
	    sb.append(" | ");
	}
	if (!isEmpty(getParameterString(prop, PARAMETER_DB_MODIFICATIONS, false)))
	{
	    sb.append("[Has database schema modifications]");
	    sb.append(" | ");
	}
	sb.append("[Timestamp: ");
	sb.append(dateTimeFormat.format(new Date()));
	sb.append("]");

	version.setDescription(sb.toString());
	redmineManager.getProjectManager().update(version);
    }

}
