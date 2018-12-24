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
import com.taskadapter.redmineapi.bean.IssueRelation;
import com.taskadapter.redmineapi.bean.Version;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import model.EmailSender;
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
    private final static String PARAMETER_RELEASE_TYPE = "RELEASE_TYPE";

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
    private final static String PARAMETER_EMAIL_SUBJECT = "EMAIL_SUBJECT";
    private final static String PARAMETER_EMAIL_BODY_RELEASE_NOTES_TITLE = "EMAIL_BODY_RELEASE_NOTES_TITLE";
    private final static String PARAMETER_EMAIL_BODY_RELEASE_NOTES_SPLITTER = "EMAIL_BODY_RELEASE_NOTES_SPLITTER";
    private final static String PARAMETER_EMAIL_BODY_ISSUES_TITLE = "EMAIL_BODY_ISSUES_TITLE";
    private final static String PARAMETER_EMAIL_DB_MODIFICATIONS_NOTE = "EMAIL_DB_MODIFICATIONS_NOTE";
    private final static String PARAMETER_SMTP_REPLY_TO = "SMTP_REPLY_TO";
    private final static String PARAMETER_EMAIL_FOOTER_NOTE = "EMAIL_FOOTER_NOTE";
    private final static String PARAMETER_EMAIL_BODY_VERSION_DESCRIPTION = "EMAIL_BODY_VERSION_DESCRIPTION";
    private final static String PARAMETER_EMAIL_BODY_TEST_RELEASE_DESCRIPTION = "EMAIL_BODY_TEST_RELEASE_DESCRIPTION";
    private final static String PARAMETER_EMAIL_BODY_TEST_USERS_DESCRIPTION = "EMAIL_BODY_TEST_USERS_DESCRIPTION";
    private final static String PARAMETER_EMAIL_BODY_TEST_USERS = "EMAIL_BODY_TEST_USERS";
    private final static String PARAMETER_EMAIL_BODY_TEST_USERS_TITLE = "EMAIL_BODY_TEST_USERS_TITLE";
    private final static String PARAMETER_EMAIL_BODY_RELATED_ISSUES_FROM_PROJECTS = "EMAIL_BODY_RELATED_ISSUES_FROM_PROJECTS";
    
    private final static String PARAMETER_DRY_RUN="DRY_RUN";
    private final static String PARAMETER_DRY_RUN_NOTIFY_SLACK="DRY_RUN_NOTIFY_SLACK";
    private final static String PARAMETER_DRY_RUN_NOTIFY_EMAIL="DRY_RUN_NOTIFY_EMAIL";
    //</editor-fold>
    private RedmineManager redmineManager;
    private Properties prop;

    private HashSet<String> relateIssuesFromProjects;

    public Release()
    {
	super(new String[]
	{
	    PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_VERSION_ID, PARAMETER_ISSUE_DEPLOYED_STATUS, PARAMETER_RELEASE_TYPE
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
	    this.prop = prop;

	    relateIssuesFromProjects = getParameterStringHash(prop, PARAMETER_EMAIL_BODY_RELATED_ISSUES_FROM_PROJECTS, true);

	    int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);
	    System.out.println("[*] Getting version from redmine ... ");
	    System.out.println("[i] VersionId: " + versionId);
	    Version version = getVersion(redmineManager, versionId);
	    if (version != null) {
		release(version, redmineUrl);
	    }
	}
    }

    private void release(Version version, String redmineUrl) throws Exception
    {
	System.out.println("[✓] " + version.getName());
	Issue[] issues = getVersionIssues(getParameterString(prop, PARAMETER_REDMINE_URL, false),
		getParameterString(prop, PARAMETER_REDMINE_TOKEN, false), version);

	String releaseType = getParameterString(prop, PARAMETER_RELEASE_TYPE, true);
	boolean isDryRun = getParameterBoolean(prop, PARAMETER_DRY_RUN);

	if (!isDryRun) {
	    deployVersion(releaseType, version, issues);
	}
	else {
	    System.out.println("[i] Running in --dry-run mode. Be cool, there won't be real deployment. ¯\\_(ツ)_/¯");
	    for (Issue issue : issues) {
		System.out.println("[✓] " + "#" + issue.getId() + " [" + issue.getStatusName() + "] " + issue.getSubject());
	    }
	    System.out.println("[i] Total " + issues.length + " issue(s).");
	}

	String[] slackWebHookUrls = getParameterStringArray(prop, 
		isDryRun ? PARAMETER_DRY_RUN_NOTIFY_SLACK : PARAMETER_NOTIFY_SLACK, false);
	if (slackWebHookUrls.length > 0) {
	    notifySlack(version, issues, redmineUrl, slackWebHookUrls);
	}
	
	String[] recipients = getParameterStringArray(prop, 
		isDryRun ? PARAMETER_DRY_RUN_NOTIFY_EMAIL : PARAMETER_NOTIFY_EMAIL, true);
	if (recipients.length > 0) {
	    notifyEmail(releaseType, recipients, version, issues, redmineUrl);
	}
    }
    
    private void deployVersion(String releaseType, Version version, Issue[] issues) throws Exception
    {
	if (!isVersionDeployed(version)) {
	    if (isProductionRelease(releaseType)) {
		deployVersion(version, issues);
	    }
	}
	else {
	    System.out.println("[i] Version is already deployed! Version status is closed.");
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

    private void notifySlack(Version version, Issue[] issues, String redmineUrl, String[] slackWebHookUrls)
    {
	System.out.println("[*] Sending slack notification ... ");
	
	StringBuilder summary = new StringBuilder();
	
	String description = getParameterString(prop, PARAMETER_DESCRIPTION, false).trim();
	String splitter = getParameterString(prop, PARAMETER_EMAIL_BODY_RELEASE_NOTES_SPLITTER, false);
	if (!StringUtils.isBlank(splitter))
	{
	    description = description.replace(splitter, "\n :pushpin: ");
	}
	summary.append("\n :point_up: _Release Notes:_ \n");
	summary.append("-------------------");
	summary.append(description);
	summary.append("\n ------------------ \n");
	String dbModifications = getParameterString(prop, PARAMETER_DB_MODIFICATIONS, false);
	if (!isEmpty(dbModifications))
	{
	    summary.append("\n");
	    summary.append(":scroll: _Has database schema migrations:_ \n");
	    appendSlackSummaryMigrations(summary, getMigrations(dbModifications));
	    summary.append("\n ------------------ \n");
	}
	
	Attachment attachment = Attachment.builder().text(summary.toString())
		.pretext(":tada: Cheers! <" + redmineUrl + "/versions/" + version.getId() + "|" + version.getName() + "> is released.")
		.authorName(version.getProjectName())
		.color("#FF8000")
		.fallback(":tada: Cheers! " + version.getName() + " is released.")
		.title(issues.length + " issue(s) fixed.")
		.footer("onur.yilmaz@kitapyurdu.com" + " | " + dateTimeFormat.format(new Date()))
		.fields(new ArrayList<>())
		.build();

	attachment.getFields().add(Field.builder()
		.title(":fire: _Related Issues:_ ")
		.valueShortEnough(false).build());

	for (Issue issue : issues)
	{
	    attachment.getFields().add(Field.builder()
		    .title("#" + issue.getId() + " - " + issue.getTracker().getName().toLowerCase())
		    .value("<" + redmineUrl + "/issues/" + issue.getId() + "|:spider_web:> ["
			    + issue.getPriorityText().toLowerCase() + "] " + issue.getSubject())
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
            for (String slackWebHookUrl : slackWebHookUrls) {
                System.out.println("Sending notification to hook: ");
                System.out.println(slackWebHookUrl);
                response = Slack.getInstance().send(slackWebHookUrl, payload);
                System.out.println(response.toString());
            }
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

    private void notifyEmail(String releaseType, String[] recipients, Version version, Issue[] issues, String redmineUrl)
    {
	EmailSender emailSender;

	System.out.println("[*] Sending email notification ... ");
	try {
	    emailSender = new EmailSender(
		    getParameterString(prop, PARAMETER_SMTP_USERNAME, false),
		    getParameterString(prop, PARAMETER_SMTP_PASSWORD, false),
		    getParameterString(prop, PARAMETER_SMTP_HOST, false),
		    getParameterString(prop, PARAMETER_SMTP_PORT, false),
		    getParameterString(prop, PARAMETER_SMTP_TLS_ENABLED, false),
		    getParameterString(prop, PARAMETER_SMTP_REPLY_TO, false)
	    );

	    emailSender.send(recipients, getSubject(releaseType, version, issues), getBody(releaseType, version, issues, redmineUrl));
	    System.out.println("[✓] Done.");
	}
	catch (Exception ex) {
	    System.err.println("Ooops! Email sending could not be completed. (" + ex.getMessage() + ")");
	}
    }

    private String getSubject(String releaseType, Version version, Issue[] issues)
    {
	String subject = getParameterString(prop, PARAMETER_EMAIL_SUBJECT, false);
	String versionName = version.getName();
	if (!isProductionRelease(releaseType))
	{
	    versionName += " [" + releaseType + "]";
	}

	if (!isEmpty(subject))
	{
	    subject = subject.replace("<VERSION>", versionName);
	    subject = subject.replace("<ISSUE_COUNT>", String.valueOf(issues.length));
	}
	else
	{
	    subject = "Cheers! " + versionName + " is released. (" + String.valueOf(issues.length) + " issues fixed)";
	}

	return subject;
    }

    private String getBody(String releaseType, Version version, Issue[] issues, String redmineUrl)
    {
	StringBuilder sb = new StringBuilder();

	sb.append("<html><body>");
	sb.append(getVersionHtml(redmineUrl, version));
	String versionDescription = getParameterString(prop, PARAMETER_EMAIL_BODY_VERSION_DESCRIPTION, false);
	if (!isEmpty(versionDescription))
	{
	    sb.append("<br/>( ");
	    sb.append(versionDescription);
	    sb.append(" )");
	}
	sb.append("<br/><br/>");

	String testReleaseDescription = getParameterString(prop, PARAMETER_EMAIL_BODY_TEST_RELEASE_DESCRIPTION, false);
	if (!isEmpty(testReleaseDescription))
	{
	    sb.append("<p>");
	    sb.append(testReleaseDescription);
	    sb.append("</p>");
	}
	String testUsersDescription = getParameterString(prop, PARAMETER_EMAIL_BODY_TEST_USERS_DESCRIPTION, false);
	if (!isEmpty(testUsersDescription))
	{
	    sb.append("<p>");
	    sb.append(testUsersDescription);
	    sb.append("</p>");
	}
	String[] testUsers = getParameterStringArray(prop, PARAMETER_EMAIL_BODY_TEST_USERS, true);
	if (testUsers.length > 0)
	{
	    String testUsersTitle = getParameterString(prop, PARAMETER_EMAIL_BODY_TEST_USERS_TITLE, false);
	    if (!isEmpty(testUsersTitle))
	    {
		sb.append("<p>");
		sb.append(testUsersTitle.replace("<TEST_USERS_COUNT>", String.valueOf(testUsers.length)));
		sb.append("</p>");
	    }
	    sb.append("<p><ul>");
	    for (String testUser : testUsers)
	    {
		sb.append("<li>");
		sb.append(testUser);
		sb.append("</li>");
	    }
	    sb.append("</ul></p>");
	}

	sb.append("<p>");
	String releaseNotesTitle = getParameterString(prop, PARAMETER_EMAIL_BODY_RELEASE_NOTES_TITLE, false);
	if (isEmpty(releaseNotesTitle))
	{
	    releaseNotesTitle = "Release Notes";
	}
	if (!isProductionRelease(releaseType))
	{
	    releaseNotesTitle += " [" + releaseType + "]";
	}
	sb.append("<b>");
	sb.append(releaseNotesTitle);
	sb.append("</b>");
	sb.append("<br/>--------------------------------------------<br/>");
	String releaseNotesSplitter = getParameterString(prop, PARAMETER_EMAIL_BODY_RELEASE_NOTES_SPLITTER, false);
	if (isEmpty(releaseNotesSplitter))
	{
	    sb.append(getParameterString(prop, PARAMETER_DESCRIPTION, false));
	}
	else
	{
	    sb.append(getParameterString(prop, PARAMETER_DESCRIPTION, false).replace(releaseNotesSplitter, "<br/> * "));
	}
	sb.append("<br/><br/>");
	sb.append("</p>");

	sb.append("<p>");
	String issuesTitle = getParameterString(prop, PARAMETER_EMAIL_BODY_ISSUES_TITLE, false);
	if (isEmpty(issuesTitle))
	{
	    issuesTitle = "Issues Fixed:";
	}
	sb.append("<b>");
	sb.append(issuesTitle);
	sb.append("</b>");
	sb.append("<br/>--------------------------------------------<br/>");
	for (Issue issue : issues)
	{
	    sb.append(getIssueIdHtml(redmineUrl, issue));
	    sb.append(getIssueDetailsHtml(issue));
	    sb.append(" / ");
	    sb.append(getIssueSubjectHtml(issue));
	    sb.append(getRelatedIssuesHtml(redmineUrl, issue));
	    sb.append("<br/>");
	}
	sb.append("</p>");

	String dbModifications = getParameterString(prop, PARAMETER_DB_MODIFICATIONS, false);
	if (!isEmpty(dbModifications))
	{
	    sb.append("<p>");
	    sb.append("<br/>--------------------------------------------<br/>");
	    String dbModificationsNote = getParameterString(prop, PARAMETER_EMAIL_DB_MODIFICATIONS_NOTE, false);
	    if (isEmpty(dbModificationsNote))
	    {
		dbModificationsNote = "Note: Has database schema modifications.";
	    }
	    sb.append(dbModificationsNote);
	    sb.append("<br>");
	    appendHtmlSummaryMigrations(sb, getMigrations(dbModifications));
	    sb.append("</p>");
	}

	String footerNote = getParameterString(prop, PARAMETER_EMAIL_FOOTER_NOTE, false);
	if (isEmpty(footerNote))
	{
	    footerNote = "You can send us any feedback by replying this email.";
	}
	sb.append("<p style=\"font-size: 12px; color:gray;\">");
	sb.append(footerNote);
	sb.append("<br>");
	sb.append(dateTimeFormat.format(new Date()));
	sb.append("</p>");

	sb.append("</body></html>");

	return sb.toString();
    }

    private String getVersionHtml(String redmineUrl, Version version)
    {
	return "<b><a href=\"" + redmineUrl + "/versions/" + version.getId() + "\">" + version.getName() + "</a></b>";
    }

    private String getIssueIdHtml(String redmineUrl, Issue issue)
    {
	return "<a href=\"" + redmineUrl + "/issues/" + issue.getId() + "\">#" + issue.getId() + "</a>";
    }

    private String getIssueDetailsHtml(Issue issue)
    {
	return " - (" + issue.getTracker().getName() + ")";
    }

    private String getIssueSubjectHtml(Issue issue)
    {
	return issue.getSubject();
    }

    private boolean isProductionRelease(String releaseType)
    {
	return releaseType.equals("production");
    }

    private String getRelatedIssuesHtml(String redmineUrl, Issue issue)
    {
	if (relateIssuesFromProjects.size() > 0)
	{
	    Collection<IssueRelation> relations = getIssueRelations(redmineManager, issue.getId());
	    StringBuilder htmlString = new StringBuilder();

	    for (IssueRelation relation : relations)
	    {
		Issue relatedIssue = tryGetRelatedIssue(issue, relation);
		if (relatedIssue != null)
		{
		    htmlString.append("<br/>|-------> ");
		    htmlString.append(getIssueProjectNameHtml(relatedIssue));
		    htmlString.append(getIssueIdHtml(redmineUrl, relatedIssue));
		    htmlString.append(getIssueDetailsHtml(relatedIssue));
		    htmlString.append(" / ");
		    htmlString.append(getIssueSubjectHtml(relatedIssue));
		}
	    }

	    return htmlString.toString();
	}

	return "";
    }

    private Issue tryGetRelatedIssue(Issue issue, IssueRelation relation)
    {
	if (relation.getType().equals("relates"))
	{
	    int relatedIssueId;
	    if ((int) relation.getIssueToId() != issue.getId())
	    {
		relatedIssueId = relation.getIssueToId();
	    }
	    else
	    {
		relatedIssueId = relation.getIssueId();
	    }

	    Issue relatedIssue = getIssue(redmineManager, relatedIssueId);
	    if (isRelatedIssueFromProjects(relatedIssue))
	    {
		return relatedIssue;
	    }
	}

	return null;
    }

    private boolean isRelatedIssueFromProjects(Issue relatedIssue)
    {
	return relatedIssue != null && relateIssuesFromProjects.contains(relatedIssue.getProjectName().toLowerCase(trLocale));
    }

    private String getIssueProjectNameHtml(Issue issue)
    {
	return "<i><span style=\"color:green;\"> " + issue.getProjectName() + " </span></i>";
    }

    private String[] getMigrations(String dbModifications)
    {
	String[] migrations;
	if (!dbModifications.contains("*"))
	{
	    migrations = new String[]
	    {
		dbModifications
	    };
	}
	else
	{
	    migrations = dbModifications.split("\\*");
	}

	return migrations;
    }

    private void appendSlackSummaryMigrations(StringBuilder summary, String[] migrations)
    {
	for (String migration : migrations)
	{
	    migration = migration.trim();
	    if (!isEmpty(migration))
	    {
		summary.append("<");
		summary.append(migration);
		summary.append("|");
		summary.append(getMigrationText(migration));
		summary.append("> \n");
	    }
	}
    }

    private String getMigrationText(String migration)
    {
	int lastIndexOfSlash = migration.lastIndexOf("/");
	return lastIndexOfSlash >= 0 ? migration.substring(lastIndexOfSlash + 1) : migration;
    }

    private void appendHtmlSummaryMigrations(StringBuilder summary, String[] migrations)
    {
	for (String migration : migrations)
	{
	    migration = migration.trim();
	    if (!isEmpty(migration))
	    {
		summary.append("<a href=\"");
		summary.append(migration);
		summary.append("\">");
		summary.append(getMigrationText(migration));
		summary.append("</a> <br/>");
	    }
	}
    }
}
